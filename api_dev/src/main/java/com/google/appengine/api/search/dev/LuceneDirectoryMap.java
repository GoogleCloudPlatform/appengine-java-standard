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

package com.google.appengine.api.search.dev;

import static com.google.common.io.BaseEncoding.base64Url;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.IndexSpec.Consistency;
import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSDirectory;

/** Maintains a map from app ID and index name, to a directory. */
abstract class LuceneDirectoryMap {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final GoogleLogger LOG = logger;
  private static final char APP_ID_NAMESPACE_DELIMETER = '/';

  private static SearchServicePb.IndexSpec normalize(SearchServicePb.IndexSpec indexSpec) {
    SearchServicePb.IndexSpec defaultInstance = SearchServicePb.IndexSpec.getDefaultInstance();
    SearchServicePb.IndexSpec.Builder builder = indexSpec.toBuilder();
    if (indexSpec.getConsistency() == defaultInstance.getConsistency()) {
      builder.clearConsistency();
    }
    if (!indexSpec.hasNamespace()) {
      builder.setNamespace(defaultInstance.getNamespace());
    }
    return builder.build();
  }

  private static class LuceneIndexSpec {
    public final Directory directory;
    public final SearchServicePb.IndexSpec indexSpec;

    public LuceneIndexSpec(Directory directory, SearchServicePb.IndexSpec indexSpec) {
      this.directory = directory;
      this.indexSpec = normalize(indexSpec);
    }
  }

  private static String getAppNamespaceKey(String appId, String namespace) {
    return appId + APP_ID_NAMESPACE_DELIMETER + namespace;
  }

  /**
   * A directory map that produces RAM based directories.
   */
  public static class RamBased extends LuceneDirectoryMap {
    @Override
    protected LuceneIndexSpec newDirectory(String appId, SearchServicePb.IndexSpec indexSpec) {
      return new LuceneIndexSpec(new RAMDirectory(), indexSpec);
    }
  }

  /**
   * A directory map that produces file system based directories.
   */
  public static final class FileBased extends LuceneDirectoryMap {

    private final File rootDir;

    private static class DecodingException extends Exception {
    }

    public FileBased(File rootDir) {
      this.rootDir = rootDir;

      File[] appDirs = rootDir.listFiles();
      if (appDirs == null) {
        LOG.atSevere().log("File base storage: root directory doesn't exist");
        return;
      }

      for (File appDir : appDirs) {
        String appId;
        try {
          appId = decode(appDir.getName());
        } catch (DecodingException e) {
          LOG.atSevere().withCause(e).log(
              "File base storage: ignoring app dir: %s", appDir.getName());
          continue;
        }

        try {
          File[] indexDirs = appDir.listFiles();
          if (indexDirs == null) {
            LOG.atSevere().log("File base storage: failed to read app dir: %s", appDir.getName());
            continue;
          }
          for (File indexDir : indexDirs) {
            SearchServicePb.IndexSpec indexSpec;

            try {
              indexSpec = decodeIndexSpec(indexDir.getName());
            } catch (DecodingException e) {
              LOG.atSevere().withCause(e).log(
                  "File base storage: ignoring index dir: %s", indexDir.getName());
              continue;
            }

            String appNamespaceKey = getAppNamespaceKey(appId, indexSpec.getNamespace());
            ConcurrentNavigableMap<String, LuceneIndexSpec> indexMap = appMap.get(appNamespaceKey);
            if (indexMap == null) {
              indexMap = new ConcurrentSkipListMap<>();
              appMap.put(appNamespaceKey, indexMap);
            }

            indexMap.put(indexSpec.getName(),
                new LuceneIndexSpec(new SimpleFSDirectory(indexDir), indexSpec));
          }
        } catch (IOException e) {
          LOG.atSevere().withCause(e).log(
              "File base storage: failed to initialize storage for appId: %s", appId);
        }
      }
    }

    @Override
    protected LuceneIndexSpec newDirectory(String appId, SearchServicePb.IndexSpec indexSpec)
        throws IOException {
      File appDir = new File(rootDir, encode(appId));
      File indexDir = new File(appDir, encodeIndexSpec(indexSpec));
      indexDir.mkdirs();
      // TODO: persist index params; otherwise user can alter them in
      // subsequent local server runs.
      LOG.atFine().log(
          "For %s.%s returning FS directory %s", appId, indexSpec.getName(), indexDir.getPath());
      return new LuceneIndexSpec(new SimpleFSDirectory(indexDir), indexSpec);
    }

    private static String encodeIndexSpec(SearchServicePb.IndexSpec indexSpec) {
      return encode(indexSpec.getName())
          + "." + encode(indexSpec.getNamespace())
          + "." + (indexSpec.getConsistency() == Consistency.GLOBAL ? "G" : "P");
    }

    private static SearchServicePb.IndexSpec decodeIndexSpec(
        String filename) throws DecodingException {
      String[] parts = filename.split("\\.");
      if (parts.length != 3) {
        throw new DecodingException();
      }
      return SearchServicePb.IndexSpec.newBuilder()
          .setName(decode(parts[0]))
          .setNamespace(decode(parts[1]))
          .setConsistency(parts[2].charAt(0) == 'G'
                          ? Consistency.GLOBAL : Consistency.PER_DOCUMENT)
          .build();
    }

    private static String encode(String name) {
      try {
        return base64Url().omitPadding().encode(name.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("should never happen", e);
      }
    }

    private static String decode(String name) throws DecodingException {
      try {
        return new String(base64Url().decode(CharMatcher.whitespace().removeFrom(name)), "UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("should never happen", e);
      } catch (IllegalArgumentException e) {
        throw new DecodingException();
      }
    }
  }

  protected final ConcurrentMap<String, ConcurrentNavigableMap<String, LuceneIndexSpec>> appMap;

  protected LuceneDirectoryMap() {
    appMap = new ConcurrentHashMap<>();
  }

  public Directory getDirectory(String appId, SearchServicePb.IndexSpec indexSpec)
      throws IOException {
    indexSpec = normalize(indexSpec);
    String appNamespaceKey = getAppNamespaceKey(appId, indexSpec.getNamespace());
    ConcurrentNavigableMap<String, LuceneIndexSpec> indexMap = appMap.get(appNamespaceKey);
    if (indexMap == null) {
      indexMap = new ConcurrentSkipListMap<>();
      appMap.put(appNamespaceKey, indexMap);
    }
    LuceneIndexSpec luceneIndexSpec = indexMap.get(indexSpec.getName());
    if (luceneIndexSpec == null) {
      luceneIndexSpec = newDirectory(appId, indexSpec);
      indexMap.put(indexSpec.getName(), luceneIndexSpec);
    } else {
      if (!luceneIndexSpec.indexSpec.equals(indexSpec)) {
        String message = String.format("Changed index specification for %s (%s vs. %s)",
            indexSpec.getName(), TextFormat.shortDebugString(indexSpec),
            TextFormat.shortDebugString(luceneIndexSpec.indexSpec));
        throw new IOException(message);
      }
    }
    return luceneIndexSpec.directory;
  }

  @AutoValue
  abstract static class AppIdAndNamespace {
    public abstract String appId();

    public abstract String namespace();

    public static AppIdAndNamespace of(String appId, String namespace) {
      return new AutoValue_LuceneDirectoryMap_AppIdAndNamespace(appId, namespace);
    }

    public static AppIdAndNamespace fromAppNamespaceKey(String appNamespaceKey) {
      List<String> parts = Splitter.on(APP_ID_NAMESPACE_DELIMETER).splitToList(appNamespaceKey);
      return of(parts.get(0), parts.get(1));
    }
  }

  @AutoValue
  abstract static class NamespaceAndIndexName implements Comparable<NamespaceAndIndexName> {
    public abstract String namespace();

    public abstract String indexName();

    public static NamespaceAndIndexName of(String namespace, String indexName) {
      return new AutoValue_LuceneDirectoryMap_NamespaceAndIndexName(namespace, indexName);
    }

    @Override
    public int compareTo(NamespaceAndIndexName other) {
      return ComparisonChain.start()
          .compare(namespace(), other.namespace())
          .compare(indexName(), other.indexName())
          .result();
    }
  }

  public List<SearchServicePb.IndexMetadata.Builder> listIndexes(
      String appId, SearchServicePb.ListIndexesParams params) {
    String namespace = params.getNamespace();
    String appNamespaceKey = getAppNamespaceKey(appId, namespace);

    List<SearchServicePb.IndexMetadata.Builder> indexMetadatas = new ArrayList<>();
    SortedMap<NamespaceAndIndexName, LuceneIndexSpec> appIndexes = new TreeMap<>();
    if (params.getAllNamespaces()) {
      // Grab all indexes belonging to this app.
      for (Map.Entry<String, ConcurrentNavigableMap<String, LuceneIndexSpec>> indexes :
          appMap.entrySet()) {
        AppIdAndNamespace appIdAndNamespace =
            AppIdAndNamespace.fromAppNamespaceKey(indexes.getKey());
        if (appId.equals(appIdAndNamespace.appId())) {
          for (Map.Entry<String, LuceneIndexSpec> index : indexes.getValue().entrySet()) {
            appIndexes.put(
                NamespaceAndIndexName.of(appIdAndNamespace.namespace(), index.getKey()),
                index.getValue());
          }
        }
      }
    } else {
      // Grab only the indexes in this namespace.
      Map<String, LuceneIndexSpec> appNamespaceIndexes = appMap.get(appNamespaceKey);
      if (appNamespaceIndexes == null) {
        return indexMetadatas;
      }
      for (Map.Entry<String, LuceneIndexSpec> index : appNamespaceIndexes.entrySet()) {
        appIndexes.put(
            NamespaceAndIndexName.of(params.getNamespace(), index.getKey()), index.getValue());
      }
    }

    int startPos = 0;
    String indexNamePrefix = params.getIndexNamePrefix();
    String startIndexName = params.getStartIndexName();
    NamespaceAndIndexName startNamespaceAndIndexName =
        NamespaceAndIndexName.of(namespace, startIndexName);

    NamespaceAndIndexName start = startNamespaceAndIndexName;
    if (start.indexName().length() == 0) {
      start = NamespaceAndIndexName.of(namespace, indexNamePrefix);
    }

    appIndexes = appIndexes.tailMap(start);
    if (appIndexes.isEmpty()) {
      return indexMetadatas;
    }
    if (appIndexes.firstKey().equals(startNamespaceAndIndexName)
        && !params.getIncludeStartIndex()) {
      startPos++;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Map.Entry<NamespaceAndIndexName, LuceneIndexSpec>[] indexes =
        appIndexes.entrySet().toArray(new Map.Entry[0]);
    startPos += params.getOffset();
    int endPos = Math.min(startPos + params.getLimit(), indexes.length);

    for (int i = startPos; i < endPos; i++) {
      Map.Entry<NamespaceAndIndexName, LuceneIndexSpec> dirEntry = indexes[i];
      String indexName = dirEntry.getKey().indexName();

      if (!indexName.startsWith(indexNamePrefix)) {
        break;
      }
      SearchServicePb.IndexMetadata.Builder metadataBuilder =
          SearchServicePb.IndexMetadata.newBuilder()
          .setIndexSpec(dirEntry.getValue().indexSpec);
      indexMetadatas.add(metadataBuilder);
    }
    return indexMetadatas;
  }

  public void close() throws IOException {
    for (Map.Entry<String, ConcurrentNavigableMap<String, LuceneIndexSpec>> entry :
        appMap.entrySet()) {
      for (Map.Entry<String, LuceneIndexSpec> dirEntry : entry.getValue().entrySet()) {
        dirEntry.getValue().directory.close();
      }
    }
  }

  protected abstract LuceneIndexSpec newDirectory(String appId,
      SearchServicePb.IndexSpec indexSpec) throws IOException;
}
