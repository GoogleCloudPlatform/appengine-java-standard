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

package com.google.apphosting.utils.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Element;

// CAUTION: this is one of several files that implement parsing and
// validation of the index definition schema; they all must be kept in
// sync.  Please refer to java/com/google/appengine/tools/development/datastore-indexes.xsd
// for the list of these files.
/**
 * Creates an {@link IndexesXml} instance from
 * WEB-INF/datastore-indexes.xml.  If you want to read the
 * configuration from a different file, subclass and override
 * {@link #getFilename()}.  If you want to read the configuration from
 * something that isn't a file, subclass and override
 * {@link #getInputStream()}.
 *
 * This class performs some validation on the XML, but it does rely on
 * the fact that (by the time our readIndexesXml() method is called)
 * the XML has already been partially validated by the XML Schema:
 * <pre>
 *     java/com/google/appengine/tools/development/datastore-indexes.xsd
 * </pre>
 */
public class IndexesXmlReader extends AbstractConfigXmlReader<IndexesXml> {

  // N.B.(schwardo): this class is not currently used in, and
  // therefore has not been tested in, the runtime.  Before adding a
  // dependency on this code from the runtime please ensure that there
  // is no possibility for external entity references or other
  // dependencies that may cause it to fail when running under the
  // restricted environment

  /**
   * Relative-to-{@code GenerationDirectory.GENERATED_DIR_PROPERTY} file for
   * generated index.
   */
  public static final String GENERATED_INDEX_FILENAME = "datastore-indexes-auto.xml";
  public static final String INDEX_FILENAME = "datastore-indexes.xml";
  public static final String INDEX_YAML_FILENAME = "WEB-INF/index.yaml";

  /** Name of the XML tag in {@code datastore-indexes.xml} for autoindexing */
  // TODO: Use this to decide whether to read the auto-generated file;
  // (also, replace repeated mentions of this string value hard-coded elsewhere)
  public static final String AUTOINDEX_TAG = "autoGenerate";

  // Relative location of the config file
  private static final String FILENAME = "WEB-INF/datastore-indexes.xml";

  // XML Constants
  private static final String INDEX_TAG = "datastore-index";

  public static final String KIND_PROP = "kind";
  public static final String ANCESTOR_PROP = "ancestor";
  public static final String PROPERTY_TAG = "property";
  public static final String NAME_PROP = "name";
  public static final String DIRECTION_PROP = "direction";
  public static final String MODE_PROP = "mode";

  private static final Logger logger = Logger.getLogger(IndexesXmlReader.class.getName());

  private IndexesXml indexesXml;

  /**
   * Constructs a reader for the {@code indexes.xml} configuration of a given app.
   * @param appDir root directory of the application
   */
  public IndexesXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Reads the configuration file.
   * @return an {@link IndexesXml} representing the parsed configuration.
   */
  public IndexesXml readIndexesXml() {
    return readConfigXml();
  }

  /**
   * Reads the index configuration.  If neither the user-written nor the
   * auto-generated config file exists, returns a {@code null}.  Otherwise,
   * reads both files (if available) and returns the union of both sets of
   * indexes.
   *
   * @throws AppEngineConfigException If the file cannot be parsed properly
   */
  @Override
  protected IndexesXml readConfigXml() {
    String filename = null;

    indexesXml = new IndexesXml();
    try {
      if (fileExists()) {
        filename = getFilename();
        try (InputStream is = getInputStream()) {
          processXml(is);
        }
        logger.info("Successfully processed " + filename);
      }
      if (yamlFileExists()) {
        filename = getYamlFilename();
        IndexYamlReader.parse(getYamlReader(), indexesXml);
        logger.info("Successfully processed " + filename);
      }
      if (generatedFileExists()) {
        filename = getAutoFilename();
        try (InputStream is = getGeneratedStream()) {
          processXml(is);
        }
        logger.info("Successfully processed " + filename);
      }
    } catch (Exception e) {
      String msg = "Received exception processing " + filename;
      logger.log(Level.SEVERE, msg, e);
      // Guarantee that the only exceptions thrown from this method are of
      // type AppEngineConfigException.
      if (e instanceof AppEngineConfigException) {
        throw (AppEngineConfigException) e;
      }
      throw new AppEngineConfigException(msg, e);
    }
    return indexesXml;
  }

  @Override
  protected IndexesXml processXml(InputStream is) {
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    for (Element child : XmlUtils.getChildren(root)) {
      if (child.getTagName().equals(INDEX_TAG)) {
        parseIndex(child);
      } else {
        throw new AppEngineConfigException(getFilename() + " contains <"
            + child.getTagName() + "> instead of <" + INDEX_TAG + "/>");
      }
    }
    return indexesXml;
  }

  /**
   * Assembles index definitions during XML parsing, and
   * performs additional validation that was not already done by
   * the XML Schema.
   * 
   * <p>There are two types of index definition: "ordered" and
   * "geo-spatial".  For an ordered index, an "ancestor"
   * specification is optional, and all "property" elements may
   * optionally specify a "direction", but may not specify a
   * "mode".  In a geo-spatial index, "ancestor" is irrelevant
   * (and therefore disallowed), and property elements may
   * specify a mode, but may not specify a direction.
   */
  private void parseIndex(Element indexElement) {
    String kind = indexElement.getAttribute(KIND_PROP);
    Boolean ancestorProp = null;
    String anc = XmlUtils.getAttributeOrNull(indexElement, ANCESTOR_PROP);

    IndexesXml.Type indexType;
    if (anc == null) {
      // We can't tell until we see some "property" attributes, below.
      indexType = null;
    } else {
      indexType = IndexesXml.Type.ORDERED;
      // Note that a case-insensitive comparison is not needed,
      // because the XML Schema validation already doesn't
      // accept "True", "TRUE", etc.
      // http://www.w3.org/TR/xmlschema-2/#boolean
      ancestorProp = anc.equals("true") || anc.equals("1");
    }
    IndexesXml.Index index = indexesXml.addNewIndex(kind, ancestorProp);

    for (Element propertyElement : XmlUtils.getChildren(indexElement)) {
      String name = propertyElement.getAttribute(NAME_PROP);
      String direction = XmlUtils.getAttributeOrNull(propertyElement, DIRECTION_PROP);
      String mode = XmlUtils.getAttributeOrNull(propertyElement, MODE_PROP);
      if (direction != null) {
        if (mode != null || indexType == IndexesXml.Type.GEO_SPATIAL) {
          throw new AppEngineConfigException(
              "The 'direction' attribute may not be specified in a 'geospatial' index.");
        }
        indexType = IndexesXml.Type.ORDERED;
      } else if (mode != null) {
        if (indexType == IndexesXml.Type.ORDERED) {
          throw new AppEngineConfigException(
              "The 'mode' attribute may not be specified with 'direction' or 'ancestor'.");
        }
        indexType = IndexesXml.Type.GEO_SPATIAL;
      }
      // If neither direction nor mode is specified, this property
      // element could be compatible with either index type, so we
      // can't use it to infer index type.

      index.addNewProperty(name, direction, mode);
    }
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

  protected File getGeneratedFile() {
    File genFile = new File(GenerationDirectory.getGenerationDirectory(new File(appDir)),
                    GENERATED_INDEX_FILENAME);
    return genFile;
  }

  public String getAutoFilename() {
    return getGeneratedFile().getPath();
  }

  protected boolean generatedFileExists() {
    return getGeneratedFile().exists();
  }

  protected InputStream getGeneratedStream() throws Exception {
    return new FileInputStream(getGeneratedFile());
  }

  protected String getYamlFilename() {
    return appDir + INDEX_YAML_FILENAME;
  }

  protected boolean yamlFileExists() {
    return new File(getYamlFilename()).exists();
  }

  protected Reader getYamlReader() {
    try {
      return new FileReader(getYamlFilename());
    } catch (FileNotFoundException ex) {
      throw new AppEngineConfigException("Cannot find file" + getYamlFilename());
    }
  }
}
