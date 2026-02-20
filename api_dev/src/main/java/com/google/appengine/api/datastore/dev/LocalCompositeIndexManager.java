/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.api.datastore.dev;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.CompositeIndexManager;
import com.google.appengine.api.datastore.CompositeIndexUtils;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Error.ErrorCode;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.apphosting.utils.config.IndexYamlReader;
import com.google.apphosting.utils.config.IndexesXml;
import com.google.apphosting.utils.config.IndexesXmlReader;
import com.google.apphosting.utils.config.XmlUtils;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.flogger.GoogleLogger;
import com.google.common.io.Closeables;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index.Property.Direction;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index.Property.Mode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;

// CAUTION: this is one of several files that implement parsing and
// validation of the index definition schema; they all must be kept in
// sync.  Please refer to java/com/google/appengine/tools/development/datastore-indexes.xsd
// for the list of these files.
/**
 * Class responsible for managing composite indexes in the dev appserver.
 *
 */
class LocalCompositeIndexManager extends CompositeIndexManager {

  /** Index configuration modes. */
  // NOTE: These enum names are used as property values and should
  // not be changed.
  static enum IndexConfigurationFormat {
    /** See {@link XmlIndexFileManager}. */
    XML,

    /** See {@link YamlIndexFileManager}. */
    YAML;

    static final IndexConfigurationFormat DEFAULT = XML;
  }

  private static final Direction DEFAULT_DIRECTION = Direction.ASCENDING;

  private static final ImmutableBiMap<String, Direction> DIRECTION_MAP =
      ImmutableBiMap.of(
          IndexesXml.DIRECTION_VALUE_ASC, Direction.ASCENDING,
          IndexesXml.DIRECTION_VALUE_DESC, Direction.DESCENDING);

  private static final ImmutableBiMap<String, Mode> MODE_MAP =
      ImmutableBiMap.of(IndexesXml.MODE_VALUE_GEOSPATIAL, Mode.GEOSPATIAL);

  private static Mode toMode(String configMode) {
    Mode mode = MODE_MAP.get(configMode);
    if (mode == null) {
      throw new IllegalArgumentException("Unrecognized mode: " + configMode);
    }
    return mode;
  }

  private static Direction toDirection(String configDirection) {
    Direction direction = DIRECTION_MAP.get(configDirection);
    if (direction == null) {
      throw new IllegalArgumentException("Unrecognized direction: " + configDirection);
    }
    return direction;
  }

  /** A container for composite indexes, both manual and auto. */
  // @VisibleForTesting
  static class CompositeIndexes {
    private final boolean autoGenerationDisabledInFile;
    private final List<Index> manualIndexes = new ArrayList<>();
    private final List<Index> generatedIndexes = new ArrayList<>();

    CompositeIndexes(boolean autoGenerationDisabledInFile) {
      this.autoGenerationDisabledInFile = autoGenerationDisabledInFile;
    }

    public void addManualIndex(Index index) {
      manualIndexes.add(index);
    }

    public void addGeneratedIndex(Index index) {
      generatedIndexes.add(index);
    }

    public boolean isAutoGenerationDisabledInFile() {
      return autoGenerationDisabledInFile;
    }

    public ImmutableList<Index> getAllIndexes() {
      // Defensive copy.
      return ImmutableList.copyOf(Iterables.concat(manualIndexes, generatedIndexes));
    }

    public ImmutableList<Index> getManualIndexes() {
      // Defensive copy.
      return ImmutableList.copyOf(manualIndexes);
    }

    public ImmutableList<Index> getGeneratedIndexes() {
      // Defensive copy.
      return ImmutableList.copyOf(generatedIndexes);
    }

    public int size() {
      return manualIndexes.size() + generatedIndexes.size();
    }
  }

  /**
   * Manages files related to indexes. Implementations must be threadsafe, but they may also assume
   * that no more than one instance exists per application directory.
   */
  static interface IndexFileManager {
    /**
     * Returns a {@link CompositeIndexes} object generated by reading the indexes file(s). Returns
     * {@code null} if the manual index file does not exist.
     */
    // @Nullable
    CompositeIndexes read();

    /**
     * Writes the generated indexes file. {@code generatedIndexMap} is a map of generated indexes to
     * the number of times they have been used.
     */
    void write(Map<Index, Integer> generatedIndexMap) throws IOException;

    /**
     * Returns an error message for a missing composite index. Includes {@code minimumIndex} if it
     * is not null.
     */
    String getMissingCompositeIndexMessage(
        IndexComponentsOnlyQuery query, @Nullable Index minimumIndex);

    /** Returns the name of the generated indexes file. */
    String getGeneratedIndexFilename();

    // NOTE: The injection hooks below this point are required
    // because the LocalCompositeIndexManager is accessed as a singleton
    // instance rather than being explicitly constructed.

    /** Sets the app directory. */
    void setAppDir(File appDir);

    /** Sets the clock. */
    void setClock(Clock clock);
  }

  /** Base class for {@link IndexFileManager} implementations. */
  private abstract static class BaseIndexFileManager implements IndexFileManager {
    protected File appDir;
    protected Clock clock = Clock.DEFAULT;

    @Override
    public void setAppDir(File appDir) {
      this.appDir = appDir;
    }

    @Override
    public void setClock(Clock clock) {
      this.clock = clock;
    }

    static String trim(@Nullable String attribute) {
      return attribute == null ? null : attribute.trim();
    }
  }

  /**
   * An {@link IndexFileManager} that uses XML files: datastore-indexes.xml for manual indexes and
   * datastore-indexes-auto.xml for generated indexes.
   */
  static class XmlIndexFileManager extends BaseIndexFileManager {
    /**
     * The format of the top level datastore-indexes element. autoGenerate defaults to true because
     * we only write this document when autoGenerate is true.
     */
    private static final String DATASTORE_INDEXES_ELEMENT_FORMAT =
        "<datastore-indexes autoGenerate=\"true\"%s>\n\n";

    /** An empty datastore-indexes document. */
    private static final String DATASTORE_INDEXES_ELEMENT_EMPTY =
        String.format(DATASTORE_INDEXES_ELEMENT_FORMAT, "/");

    /** The opening tag for a non-empty datastore-indexes document. */
    private static final String DATASTORE_INDEXES_ELEMENT_NOT_EMPTY =
        String.format(DATASTORE_INDEXES_ELEMENT_FORMAT, "");

    /** The closing tag for a non-empty datastore-indexes document. */
    private static final String DATASTORE_INDEXES_ELEMENT_CLOSE = "</datastore-indexes>\n";

    /** The format of a comment indicating how many times an index was used. */
    private static final String FREQUENCY_XML_COMMENT_FORMAT =
        "    <!-- Used %d time%s in query history -->\n";

    /** The format of a comment indicating the time at which indexes were written. */
    private static final String TIMESTAMP_XML_COMMENT_FORMAT = "<!-- Indices written at %s -->\n\n";

    @Override
    // @Nullable
    public synchronized CompositeIndexes read() {
      // If the manual index file doesn't exist, return right away.
      InputStream indexFileInputStream = getIndexFileInputStream();
      if (indexFileInputStream == null) {
        // Auto generation was not explicitly disabled.
        return new CompositeIndexes(false);
      }

      // Start with the manual index file.
      CompositeIndexes compositeIndexes;
      Element datastoreIndexesElement =
          XmlUtils.parseXml(indexFileInputStream, getIndexFile().getPath()).getDocumentElement();
      compositeIndexes = new CompositeIndexes(!isAutoGenerateIndexes(datastoreIndexesElement));
      addIndexes(datastoreIndexesElement, compositeIndexes);

      // Add indexes from generated index file if it exists.
      InputStream generatedIndexFileInputStream = getGeneratedIndexFileInputStream();
      if (generatedIndexFileInputStream != null) {
        Element generatedDatastoreIndexesElement =
            XmlUtils.parseXml(generatedIndexFileInputStream, getGeneratedIndexFilename())
                .getDocumentElement();
        addIndexes(generatedDatastoreIndexesElement, compositeIndexes);
      }

      return compositeIndexes;
    }

    @Override
    public synchronized void write(Map<Index, Integer> generatedIndexMap) throws IOException {
      // We allocate a new SimpleDateFormat every time because instances
      // of this class are not threadsafe.
      SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);

      Writer fw = newGeneratedIndexFileWriter();
      try (BufferedWriter out = new BufferedWriter(fw)) {
        out.append(
            String.format(
                TIMESTAMP_XML_COMMENT_FORMAT, format.format(new Date(clock.getCurrentTime()))));
        if (generatedIndexMap.isEmpty()) {
          out.append(DATASTORE_INDEXES_ELEMENT_EMPTY);
        } else {
          out.append(DATASTORE_INDEXES_ELEMENT_NOT_EMPTY);
          for (Map.Entry<Index, Integer> entry : generatedIndexMap.entrySet()) {
            int count = entry.getValue();
            // Add a comment saying how many times the index has been used
            out.append(String.format(FREQUENCY_XML_COMMENT_FORMAT, count, count == 1 ? "" : "s"));
            String xml = CompositeIndexUtils.generateXmlForIndex(entry.getKey(), IndexSource.auto);
            out.append(xml);
          }
          out.append(DATASTORE_INDEXES_ELEMENT_CLOSE);
        }
      }
    }

    @Override
    public String getMissingCompositeIndexMessage(
        IndexComponentsOnlyQuery query, @Nullable Index minimumIndex) {
      String message =
          "This query requires a composite index that is not "
              + "defined. You must update "
              + getIndexFile().getPath()
              + " or enable "
              + "autoGenerate to have it automatically added.";
      if (minimumIndex != null) {
        message +=
            "\n\nThe minimum required index is:\n"
                + CompositeIndexUtils.generateXmlForIndex(minimumIndex, IndexSource.manual);
      }
      return message;
    }

    @Override
    public String getGeneratedIndexFilename() {
      return getGeneratedIndexFile().getPath();
    }

    /** Returns the generated indexes file. */
    private File getGeneratedIndexFile() {
      File dir = GenerationDirectory.getGenerationDirectory(appDir);
      return new File(dir, IndexesXmlReader.GENERATED_INDEX_FILENAME);
    }

    /**
     * Returns an input stream for the generated indexes file or {@code null} if it doesn't exist.
     */
    // @VisibleForTesting
    @Nullable InputStream getGeneratedIndexFileInputStream() {
      try {
        return new FileInputStream(getGeneratedIndexFile());
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    /** Returns a writer for the generated indexes file. */
    // @VisibleForTesting
    Writer newGeneratedIndexFileWriter() throws IOException {
      File output = getGeneratedIndexFile();
      output.getParentFile().mkdirs();
      return new FileWriter(output);
    }

    /** Returns an input stream for the manual indexes file or {@code null} if it doesn't exist. */
    // @Nullable
    // @VisibleForTesting
    InputStream getIndexFileInputStream() {
      try {
        return new FileInputStream(getIndexFile());
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    /** Returns the manual indexes file. */
    private File getIndexFile() {
      return new File(new File(appDir, "WEB-INF"), IndexesXmlReader.INDEX_FILENAME);
    }

    private static void addIndexes(
        Element datastoreIndexesElement, CompositeIndexes compositeIndexes) {
      for (Element datastoreIndex :
          XmlUtils.getChildren(datastoreIndexesElement, "datastore-index")) {
        if (isManual(datastoreIndex)) {
          compositeIndexes.addManualIndex(toIndex(datastoreIndex));
        } else {
          compositeIndexes.addGeneratedIndex(toIndex(datastoreIndex));
        }
      }
    }

    private static Index toIndex(Element datastoreIndexElement) {
      Index.Builder index = Index.newBuilder();
      // TODO: Should we be doing more validation here?
      // TODO: surely we should instead be reusing the parse
      // validation that we're already doing in IndexesXml,
      // including XML Schema validation against datastore-indexes.xsd.
      index.setEntityType(trim(datastoreIndexElement.getAttribute(IndexesXmlReader.KIND_PROP)));
      String ancestorValue =
          XmlUtils.getAttributeOrNull(datastoreIndexElement, IndexesXmlReader.ANCESTOR_PROP);
      boolean ancestor = ancestorValue == null ? false : Boolean.parseBoolean(trim(ancestorValue));
      index.setAncestor(ancestor);

      for (Element propertyElement :
          XmlUtils.getChildren(datastoreIndexElement, IndexesXmlReader.PROPERTY_TAG)) {
        Property.Builder prop =
            index
                .addPropertyBuilder()
                .setName(trim(propertyElement.getAttribute(IndexesXmlReader.NAME_PROP)));
        String directionValue =
            XmlUtils.getAttributeOrNull(propertyElement, IndexesXmlReader.DIRECTION_PROP);
        if (directionValue != null) {
          prop.setDirection(toDirection(trim(directionValue)));
        }
        String modeValue = XmlUtils.getAttributeOrNull(propertyElement, IndexesXmlReader.MODE_PROP);
        if (modeValue != null) {
          prop.setMode(toMode(trim(modeValue)));
        }
      }
      return index.build();
    }

    private static boolean isAutoGenerateIndexes(Element datastoreIndexesElement) {
      String autoGenerate = datastoreIndexesElement.getAttribute(IndexesXmlReader.AUTOINDEX_TAG);
      if (!"true".equals(autoGenerate) && !"false".equals(autoGenerate)) {
        throw new AppEngineConfigException(
            "autoGenerate=true|false is required in datastore-indexes.xml");
      }
      return Boolean.parseBoolean(autoGenerate);
    }

    // NOTE: The question of whether an index is manual is based entirely on the
    // "source" attribute on the datastore-index node, not the file in which the node originally
    // appeared. This is weird but non-trivial to change at this point.
    private static boolean isManual(Element datastoreIndexElement) {
      String sourceValue = XmlUtils.getAttributeOrNull(datastoreIndexElement, "source");
      // Index auto-gen always creates the source attribute, so if the source
      // attribute is null we assume it is a manually created index.
      return sourceValue == null || IndexSource.valueOf(trim(sourceValue)) == IndexSource.manual;
    }
  }

  /**
   * A converter between {@link Index} and {@link IndexesXml} instances. Methods refer to {@link
   * IndexesXml} and friends as "config" classes to distinguish them from the proto definitions.
   */
  private static class IndexesXmlConverter {
    public IndexesXml toConfigIndexes(List<Index> indexes) {
      IndexesXml configIndexes = new IndexesXml();
      for (Index index : indexes) {
        configIndexes.addNewIndex(toConfigIndex(index));
      }
      return configIndexes;
    }

    public List<Index> toIndexes(IndexesXml configIndexes) {
      List<Index> indexes = Lists.newArrayListWithCapacity(configIndexes.size());
      for (IndexesXml.Index configIndex : configIndexes) {
        indexes.add(toIndex(configIndex));
      }
      return indexes;
    }

    private IndexesXml.Index toConfigIndex(Index index) {
      IndexesXml.Index configIndex =
          new IndexesXml.Index(index.getEntityType(), index.getAncestor());
      for (Property property : index.getPropertyList()) {
        configIndex.addNewProperty(
            property.getName(),
            toConfigDirection(property.getDirection()),
            toConfigMode(property.getMode()));
      }
      return configIndex;
    }

    private Index toIndex(IndexesXml.Index configIndex) {
      Index.Builder index =
          Index.newBuilder()
              .setEntityType(configIndex.getKind())
              .setAncestor(configIndex.doIndexAncestors());
      for (IndexesXml.PropertySort propertySort : configIndex.getProperties()) {
        Property.Builder property =
            index.addPropertyBuilder().setName(propertySort.getPropertyName());
        // Always set direction.
        property.setDirection(toDirection(propertySort.getDirection()));
        // Only set mode if present.
        if (propertySort.getMode() != null) {
          property.setMode(toMode(propertySort.getMode()));
        }
      }
      return index.build();
    }

    // @Nullable
    private String toConfigDirection(Direction direction) {
      if (direction == DEFAULT_DIRECTION) {
        return null;
      }
      String configDirection = DIRECTION_MAP.inverse().get(direction);
      if (configDirection == null) {
        throw new IllegalArgumentException("Unrecognized direction: " + direction);
      }
      return configDirection;
    }

    private Direction toDirection(@Nullable String configDirection) {
      if (configDirection == null) {
        return DEFAULT_DIRECTION;
      }
      return LocalCompositeIndexManager.toDirection(configDirection);
    }

    // @Nullable
    private String toConfigMode(Mode mode) {
      if (mode == Mode.MODE_UNSPECIFIED) {
        return null;
      }
      String configMode = MODE_MAP.inverse().get(mode);
      if (configMode == null) {
        throw new IllegalArgumentException("Unrecognized mode: " + mode);
      }
      return configMode;
    }

    private Mode toMode(String configMode) {
      return LocalCompositeIndexManager.toMode(configMode);
    }
  }

  /**
   * An {@link IndexFileManager} that uses a single YAML file for both manual and generated indexes.
   */
  static class YamlIndexFileManager extends BaseIndexFileManager {
    private static final IndexesXmlConverter converter = new IndexesXmlConverter();

    private static final String AUTOGENERATED = "# AUTOGENERATED";

    private static final String AUTOGENERATED_COMMENT =
        "# This index.yaml is automatically updated whenever the Cloud Datastore\n"
            + "# emulator detects that a new type of query is run. If you want to manage the\n"
            + "# index.yaml file manually, remove the \"# AUTOGENERATED\" marker line above.\n"
            + "# If you want to manage some indexes manually, move them above the marker line.";

    private static final String INDEXES_TAG = "indexes:";

    @Override
    public synchronized CompositeIndexes read() {
      InputStream indexFileInputStream = getIndexFileInputStream();
      if (indexFileInputStream == null) {
        // Auto generation was not explicitly disabled.
        return new CompositeIndexes(false);
      }

      // Break the YAML file into two YAML strings.
      StringBuilder manualYaml = new StringBuilder();
      // The generated YAML fragment will not be valid on its own, so add a
      // top-level indexes tag to help the parser.
      StringBuilder generatedYaml = new StringBuilder(INDEXES_TAG + "\n");
      boolean sawAutoGenerateLine;
      try {
        sawAutoGenerateLine = splitYamlFile(indexFileInputStream, manualYaml, generatedYaml);
      } catch (IOException e) {
        String message = "Received IOException parsing the input stream.";
        throw new AppEngineConfigException(message, e);
      }

      // Parse the YAML strings and compute the indexes.
      CompositeIndexes compositeIndexes = new CompositeIndexes(!sawAutoGenerateLine);
      for (Index index : converter.toIndexes(IndexYamlReader.parse(manualYaml.toString()))) {
        compositeIndexes.addManualIndex(index);
      }
      if (sawAutoGenerateLine) {
        for (Index index : converter.toIndexes(IndexYamlReader.parse(generatedYaml.toString()))) {
          compositeIndexes.addGeneratedIndex(index);
        }
      }
      return compositeIndexes;
    }

    @Override
    public synchronized void write(Map<Index, Integer> generatedIndexMap) throws IOException {
      StringBuilder manualYaml = new StringBuilder();
      InputStream indexFileInputStream = getIndexFileInputStream();
      if (indexFileInputStream == null) {
        // The file doesn't already exist. We won't get the indexes tag from
        // the manual part of the YAML file, so add it ourselves.
        manualYaml.append(INDEXES_TAG + "\n");
        manualYaml.append("\n");
      } else {
        try {
          splitYamlFile(indexFileInputStream, manualYaml, new StringBuilder());
        } catch (IOException e) {
          String message = "Received IOException parsing the input stream.";
          throw new AppEngineConfigException(message, e);
        }
      }

      List<Index> indexes = Lists.newArrayList(generatedIndexMap.keySet());
      Writer fw = newIndexFileWriter();
      try (BufferedWriter out = new BufferedWriter(fw)) {

        // Manual YAML first.
        out.append(manualYaml);
        out.append(AUTOGENERATED).append("\n");
        out.append("\n");
        out.append(AUTOGENERATED_COMMENT).append("\n");
        out.append("\n");

        // Generated yaml.
        String generatedYaml = stripIndexesLine(converter.toConfigIndexes(indexes).toYaml());
        out.append(generatedYaml);
      }
    }

    @Override
    public String getMissingCompositeIndexMessage(
        IndexComponentsOnlyQuery query, @Nullable Index minimumIndex) {
      String message =
          "This query requires a composite index that is not "
              + "defined. You must update "
              + getIndexFile().getPath()
              + " or add "
              + "\"# AUTOGENERATED\" to have it automatically added.";
      if (minimumIndex != null) {
        message +=
            "\n\nThe minimum required index is:\n" + converter.toConfigIndex(minimumIndex).toYaml();
      }
      return message;
    }

    @Override
    public String getGeneratedIndexFilename() {
      return getIndexFile().getPath();
    }

    /** Returns an input stream for the indexes file or {@code null} if it doesn't exist. */
    // @VisibleForTesting
    // @Nullable
    InputStream getIndexFileInputStream() {
      try {
        return new FileInputStream(getIndexFile());
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    // @VisibleForTesting
    Writer newIndexFileWriter() throws IOException {
      File output = getIndexFile();
      output.getParentFile().mkdirs();
      return new FileWriter(output);
    }

    private static String stripIndexesLine(String yaml) {
      StringBuilder out = new StringBuilder();
      String[] yamlLines = yaml.split("\n");
      if (yamlLines.length < 1 || !INDEXES_TAG.equals(yamlLines[0])) {
        throw new IllegalStateException(
            "Failed to find " + INDEXES_TAG + " at beginning out yaml.");
      }
      for (String line : Arrays.asList(yamlLines).subList(1, yamlLines.length)) {
        out.append(line).append("\n");
      }
      return out.toString();
    }

    private File getIndexFile() {
      return new File(appDir, IndexesXmlReader.INDEX_YAML_FILENAME);
    }

    private boolean splitYamlFile(
        InputStream indexFileInputStream, StringBuilder manualYaml, StringBuilder generatedYaml)
        throws IOException {
      // Break the YAML file into two YAML strings.
      BufferedReader in = new BufferedReader(new InputStreamReader(indexFileInputStream, UTF_8));
      boolean sawAutoGenerateLine = false;
      try {
        String line;
        while ((line = in.readLine()) != null) {
          if (AUTOGENERATED.equals(trim(line))) {
            sawAutoGenerateLine = true;
          }
          if (sawAutoGenerateLine) {
            generatedYaml.append(line).append("\n");
          } else {
            manualYaml.append(line).append("\n");
          }
        }
      } finally {
        Closeables.closeQuietly(in);
      }
      return sawAutoGenerateLine;
    }
  }

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // These fields are only set once.
  private static IndexConfigurationFormat indexConfigurationFormat;
  private static LocalCompositeIndexManager instance;

  private final IndexFileManager fileManager;

  // @VisibleForTesting
  LocalCompositeIndexManager(IndexFileManager fileManager) {
    this.fileManager = fileManager;
  }

  /**
   * Initialize the singleton instance. Can be called multiple times but only if the arguments are
   * the same.
   */
  static synchronized void init(IndexConfigurationFormat indexConfigurationFormat) {
    if (LocalCompositeIndexManager.indexConfigurationFormat == null) {
      LocalCompositeIndexManager.indexConfigurationFormat = indexConfigurationFormat;
    } else {
      checkState(
          LocalCompositeIndexManager.indexConfigurationFormat == indexConfigurationFormat,
          "Cannot change index configuration format from %s to %s",
          LocalCompositeIndexManager.indexConfigurationFormat,
          indexConfigurationFormat);
    }
  }

  /** Returns the singleton instance. */
  public static synchronized LocalCompositeIndexManager getInstance() {
    if (instance == null) {
      if (indexConfigurationFormat == null) {
        indexConfigurationFormat = IndexConfigurationFormat.DEFAULT;
      }
      switch (indexConfigurationFormat) {
        case XML:
          instance = new LocalCompositeIndexManager(new XmlIndexFileManager());
          break;
        case YAML:
          instance = new LocalCompositeIndexManager(new YamlIndexFileManager());
          break;
        default:
          throw new IllegalArgumentException(
              "Unrecognized index configuration format: " + indexConfigurationFormat);
      }
    }
    return instance;
  }

  /**
   * The query history, maintained in a {@link Map} where the key is the query and the value is the
   * number of times that query has been executed.
   *
   * <p>This map is synchronized because multiple threads will be reading from and writing to it
   * concurrently. The value is Atomic because multiple threads will be incrementing it
   * concurrently.
   *
   * <p>We use a {@link LinkedHashMap} as the implementation so that we get consistent ordering in
   * our tests. This will also minimize file churn for users.
   *
   * <p>Serialization of access to the index files is managed by the {@link IndexFileManager}.
   */
  private final Map<IndexComponentsOnlyQuery, AtomicInteger> queryHistory =
      Collections.synchronizedMap(Maps.<IndexComponentsOnlyQuery, AtomicInteger>newLinkedHashMap());

  /**
   * In-memory cache of the indexes present in the index file. Used to quickly verify if an index
   * exists for a given query. We only use this cache when auto-generation of indexes is disabled.
   */
  private final IndexCache indexCache = new IndexCache();

  /** If {@code false} the index file will not be auto-generated. */
  private boolean storeIndexConfiguration = true;

  /**
   * Process a query: Update the query history and then write out the index file if necessary.
   *
   * @param query The query to process.
   * @throws com.google.appengine.api.datastore.DatastoreNeedIndexException If index file auto
   *     generation is disabled and the index required to fulfill this query is not present in the
   *     index file.
   */
  public void processQuery(DatastoreV3Pb.Query.Builder query) {
    IndexComponentsOnlyQuery indexOnlyQuery = new IndexComponentsOnlyQuery(query);
    boolean isNewQuery = updateQueryHistory(indexOnlyQuery);
    if (isNewQuery) {
      maybeUpdateIndexFile(indexOnlyQuery);
    }
  }

  /**
   * Update the query history.
   *
   * @param query The query
   * @return {@code true} if this is a query that we haven't seen before, {@code false} otherwise.
   */
  private boolean updateQueryHistory(IndexComponentsOnlyQuery query) {
    boolean newQuery = false;
    AtomicInteger count = queryHistory.get(query);
    if (count == null) {
      // First time this query has been run.
      count = newAtomicInteger(0);
      AtomicInteger overwrittenCount = queryHistory.put(query, count);
      // If put returns a non-null value, that means another thread executed
      // this same query and updated its count in between the time we
      // executed our get and now.  We need to add the count
      // that we just overwrote to our current value.
      if (overwrittenCount != null) {
        count.addAndGet(overwrittenCount.intValue());
      } else {
        // We really were the first to execute.
        newQuery = true;
      }
    }
    count.incrementAndGet();
    return newQuery;
  }

  void clearQueryHistory() {
    queryHistory.clear();
  }

  // Method just exists so that we can write a test that pauses at this point.
  // @VisibleForTesting
  AtomicInteger newAtomicInteger(int i) {
    return new AtomicInteger(i);
  }

  // @VisibleForTesting
  Map<IndexComponentsOnlyQuery, AtomicInteger> getQueryHistory() {
    return queryHistory;
  }

  private void maybeUpdateIndexFile(IndexComponentsOnlyQuery query) {
    CompositeIndexes compositeIndexes = fileManager.read();
    if (compositeIndexes.isAutoGenerationDisabledInFile()) {
      // TODO check file mod timestamp so that we can pick up
      // manual indexes that were added while the server was running.
      indexCache.verifyIndexExistsForQuery(query, compositeIndexes);
      logger.atFine().log("Skipping index file update because auto generation is disabled.");
      return;
    }
    // User might have explicitly disabled storing the index configuration.
    if (storeIndexConfiguration) {
      updateIndexFile(compositeIndexes);
    }
  }

  Set<Index> getIndexes() {
    List<Index> manualIndexes;
    Set<Index> generatedIndexes;
    CompositeIndexes compositeIndexes = fileManager.read();
    if (compositeIndexes.isAutoGenerationDisabledInFile()) {
      manualIndexes = compositeIndexes.getAllIndexes();
      generatedIndexes = Collections.emptySet();
    } else {
      manualIndexes = compositeIndexes.getManualIndexes();
      generatedIndexes = buildIndexMapFromQueryHistory().keySet();
    }
    Set<Index> combined =
        Sets.newLinkedHashSetWithExpectedSize(manualIndexes.size() + generatedIndexes.size());
    combined.addAll(generatedIndexes);
    combined.addAll(manualIndexes);
    return combined;
  }

  Collection<Index> getIndexesForKind(String kind) {
    Set<Index> indexes = Sets.newLinkedHashSet();
    for (Index index : getIndexes()) {
      if (index.getEntityType().equals(kind)) {
        indexes.add(index);
      }
    }
    return indexes;
  }

  /**
   * Updates the index file with all indexes needed to fulfill all the queries in the query history.
   */
  private void updateIndexFile(CompositeIndexes compositeIndexes) {
    Map<Index, Integer> indexMap = buildIndexMapFromQueryHistory();
    // Spin through the manually added indexes.  If any of them show up in
    // the map we built from the query history, make sure we only write the
    // manual version.
    indexMap.keySet().removeAll(compositeIndexes.getManualIndexes());

    try {
      // now we need to write it out
      fileManager.write(indexMap);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Unable to write %s", fileManager.getGeneratedIndexFilename());
    }
  }

  /**
   * Simple synchronized cache of the indexes defined in the index file. The cache loads the
   * contents of the file the first time it is accessed. We don't worry about reloading its contents
   * because we only use this cache when auto-generation of indexes is disabled and we don't support
   * changing the auto-generation flag at runtime.
   */
  private final class IndexCache {
    /**
     * We initialize to null so we can distinguish between a cache that has been loaded and is empty
     * and a cache that has not been loaded. All access to this member must be synchronized.
     */
    @SuppressWarnings("hiding")
    private Set<Index> indexCache = null;

    private synchronized void verifyIndexExistsForQuery(
        IndexComponentsOnlyQuery query, CompositeIndexes compositeIndexes) {
      // null check is safe because the method is synchronized
      if (indexCache == null) {
        // not using a synchronized collection because all access to this member is
        // inside this method, which is synchronized
        indexCache = Sets.newHashSet(compositeIndexes.getAllIndexes());
      }

      // returns null if no index needed for query
      Index index = compositeIndexForQuery(query);
      if (index != null && !indexCache.contains(index)) {
        // See if other indexes in the cache can satisfy the query.
        Index minimumIndex = minimumCompositeIndexForQuery(query, indexCache);

        if (minimumIndex != null) {
          // NOTE: The SDK will add index to the exception, so we are only adding the
          // minimum index if it is different.
          Index minimumIndexForMessage = minimumIndex.equals(index) ? null : minimumIndex;
          String message =
              fileManager.getMissingCompositeIndexMessage(query, minimumIndexForMessage);
          throw new ApiProxy.ApplicationException(ErrorCode.NEED_INDEX.getNumber(), message);
        }
      }
    }
  }

  // @VisibleForTesting
  Map<Index, Integer> buildIndexMapFromQueryHistory() {
    // LinkedHashMap gives us repeatable results in our tests and
    // minimizes file churn.
    Map<Index, Integer> indexMap = Maps.newLinkedHashMap();
    synchronized (queryHistory) {
      for (Map.Entry<IndexComponentsOnlyQuery, AtomicInteger> entry : queryHistory.entrySet()) {
        Index index = compositeIndexForQuery(entry.getKey());
        if (index == null) {
          // not interested in queries that don't need an index
          continue;
        }
        Integer count = indexMap.get(index);
        if (count == null) {
          count = 0;
        }
        count += entry.getValue().intValue();
        indexMap.put(index, count);
      }
    }
    return indexMap;
  }

  /** Get the single composite index used by this query, if any, as a list. */
  public List<Index> queryIndexList(DatastoreV3Pb.Query.Builder query) {
    IndexComponentsOnlyQuery indexOnlyQuery = new IndexComponentsOnlyQuery(query);
    Index index = compositeIndexForQuery(indexOnlyQuery);
    List<Index> indexList;
    if (index != null) {
      indexList = Collections.singletonList(index);
    } else {
      indexList = Collections.emptyList();
    }
    return indexList;
  }

  /** Stores the application directory, for locating the index configuration files. */
  public void setAppDir(File appDir) {
    fileManager.setAppDir(appDir);
  }

  public void setClock(Clock clock) {
    fileManager.setClock(clock);
  }

  public void setStoreIndexConfiguration(boolean storeIndexConfiguration) {
    this.storeIndexConfiguration = storeIndexConfiguration;
  }

  /** Aliasing to make the method available in the package. */
  protected Index compositeIndexForQuery(IndexComponentsOnlyQuery indexOnlyQuery) {
    return super.compositeIndexForQuery(indexOnlyQuery);
  }

  /** Aliasing to make the method available in the package. */
  protected Index minimumCompositeIndexForQuery(
      IndexComponentsOnlyQuery indexOnlyQuery, Collection<Index> indexes) {
    return super.minimumCompositeIndexForQuery(indexOnlyQuery, indexes);
  }

  /** Aliasing to make the class available in the package. */
  protected static class ValidatedQuery extends CompositeIndexManager.ValidatedQuery {
    protected ValidatedQuery(DatastoreV3Pb.Query.Builder query) {
      super(query);
    }

    public DatastoreV3Pb.Query.Builder getV3Query() {
      return super.getQuery();
    }
  }

  /** Aliasing to make the class available in the package. */
  protected static class KeyTranslator extends CompositeIndexManager.KeyTranslator {
    private KeyTranslator() {}
  }

  /** Aliasing to make the class available in the package. */
  protected static class IndexComponentsOnlyQuery
      extends CompositeIndexManager.IndexComponentsOnlyQuery {
    protected IndexComponentsOnlyQuery(DatastoreV3Pb.Query.Builder query) {
      super(query);
    }

    public DatastoreV3Pb.Query.Builder getV3Query() {
      return super.getQuery();
    }
  }
}
