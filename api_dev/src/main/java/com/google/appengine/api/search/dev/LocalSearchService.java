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

import com.google.appengine.api.search.RequestStatusUtil;
import com.google.appengine.api.search.SearchQueryException;
import com.google.appengine.api.search.checkers.DocumentChecker;
import com.google.appengine.api.search.checkers.FacetQueryChecker;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteSchemaRequest;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteSchemaResponse;
import com.google.appengine.api.search.proto.SearchServicePb.IndexSpec;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.InvalidProtocolBufferException;
// <internal>
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

/**
 * A search service implementation when running appengine on a local machine.
 */
@AutoService(LocalRpcService.class)
public class LocalSearchService extends AbstractLocalRpcService {

  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "search";

  public static final String USE_RAM_DIRECTORY = "LocalSearchService.useRamDirectory";
  public static final String USE_DIRECTORY = "LocalSearchService.useDirectory";

  static final Logger LOG = Logger.getLogger(LocalSearchService.class.getCanonicalName());
  
  /** Hash function for adding query fingerprints to cursors. */
  private static final HashFunction CURSOR_HASH = Hashing.murmur3_32(1729);

  /** Prefix based on a query fingerprint, to prevent reuse of cursors across queries. */
  private static String getCursorPrefix(SearchServicePb.SearchParams params) {
    return CURSOR_HASH.hashString(params.getQuery(), StandardCharsets.UTF_8) + "-";
  }

  private static String encodeCursor(SearchServicePb.SearchParams params, int offset) {
    return getCursorPrefix(params) + offset;
  }

  /** Decodes cursor. On error, logs problem, returns -1. */
  private static int decodeCursor(SearchServicePb.SearchParams params, String cursorStr) {
    String expectedPrefix = getCursorPrefix(params);
    if (!cursorStr.startsWith(expectedPrefix)) {
      LOG.severe("Cursor is incompatible with query: " + cursorStr);
      return -1;
    }
    try {
      return Integer.parseInt(cursorStr.substring(expectedPrefix.length()));
    } catch (NumberFormatException nfe) {
      LOG.log(Level.SEVERE, "Invalid cursor value: " + cursorStr);
      return -1;
    }
  }

  /**
   * Init property that specifies the {@link Level} at which we log mail
   * messages.  Value must be a string representation of a {@link Level}
   * (calling {@link Level#parse(String)} with the value as the arg should
   * return a valid instance).
   */
  public static final String SEARCH_LOG_LEVEL_PROPERTY = "LocalSearchService.LogLevel";

  // The default logging level, if one is not specified in properties.
  private static final Level DEFAULT_LOG_LEVEL = Level.INFO;

  // Increment this number every time we make an incompatible change to how the document map is
  // persisted. Changing this number will invalidate all persisted indexes.
  private static final int PERSIST_VERSION = 0;

  // The maximum length of a field accepted by the local server.
  private static final MaxFieldLength MAX_FIELD_LENGTH = MaxFieldLength.LIMITED;

  private LuceneDirectoryMap dirMap;
  private final Analyzer analyzer;

  /**
   * The in-memory repository of Documents.
   * <p>
   * DEPRECATED: This map is deprecated and we should not add/delete anything from it. We keep it
   * to maintain backward compatibilty with persisted documents on disk. We are now storing full
   * documents in Lucene.
   */
  private Map<String, DocumentPb.Document> documentsById;

  private String documentsFile;

  public LocalSearchService() {
    analyzer = new WordSeparatorAnalyzer();
    LOG.info("Local search service created");
  }

  // --- Local RPC service ---

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    String logLevelStr = properties.get(SEARCH_LOG_LEVEL_PROPERTY);
    if (logLevelStr != null) {
      LOG.setLevel(Level.parse(logLevelStr));
    } else {
      LOG.setLevel(DEFAULT_LOG_LEVEL);
    }

    documentsById = new LinkedHashMap<>();

    if ("true".equals(properties.get(USE_RAM_DIRECTORY))) {
      LOG.warning("Using RAM directory; results are not preserved");
      dirMap = new LuceneDirectoryMap.RamBased();
      documentsFile = null;
    } else {
      String dirName = properties.get(USE_DIRECTORY);
      File dir;
      if (dirName == null) {
        dir = GenerationDirectory.getGenerationDirectory(
            context.getLocalServerEnvironment().getAppDir());
      } else {
        dir = new File(dirName);
      }
      File indexDirectory = null;
      dir.mkdirs();

      if (dir.exists()) {
        indexDirectory = new File(dir.getAbsolutePath(), "indexes");

        documentsFile = dir.getAbsolutePath() + File.separator + "local_search.bin";
        File documentsFileHandle = new File(documentsFile);
        if (documentsFileHandle.exists()) {
          // Load document map from disk for backward compatibility. The map will never change.
          loadDocumentMap(indexDirectory, documentsFileHandle);
        }

        // Initialize directory map after to prevent index files from being opened in case we need
        // to delete indexes.
        dirMap = new LuceneDirectoryMap.FileBased(indexDirectory);
      } else {
        if (LOG.isLoggable(Level.WARNING)) {
          String message = String.format(
              "Failed to create data directory %s, using RAM directory instead;"
              + " results are not preserved", dir.getAbsolutePath());
          LOG.warning(message);
        }
        dirMap = new LuceneDirectoryMap.RamBased();
        documentsFile = null;
      }
    }
    LOG.info(getPackage() + " initialized");
  }

  // <internal>
  private void loadDocumentMap(File indexDirectory, File documentsFile) {
    String path = documentsFile.getAbsolutePath();

    try {
      ObjectInputStream objectIn = new ObjectInputStream(
          new BufferedInputStream(new FileInputStream(path)));

      int readVersion = objectIn.readInt();
      if (readVersion != PERSIST_VERSION) {
        clearIndexes(indexDirectory);
      } else {
        @SuppressWarnings("unchecked")
        Map<String, DocumentPb.Document> documentsOnDisk =
            (Map<String, DocumentPb.Document>) objectIn.readObject();
        documentsById = documentsOnDisk;
      }

      objectIn.close();
    } catch (FileNotFoundException e) {
      // Should never happen, because we just checked for it
      LOG.severe("Failed to find search document storage, " + path);
    } catch (IOException e) {
      LOG.log(Level.INFO, "Failed to load from search document storage, " + path, e);
      clearIndexes(indexDirectory);
    } catch (ClassNotFoundException e) {
      LOG.log(Level.INFO, "Failed to load from search document storage, " + path, e);
      clearIndexes(indexDirectory);
    }
  }

  @Override
  public void start() {
    LOG.info(getPackage() + " started");
  }

  private void closeIndexWriters() {
    for (IndexWriter writer : indexWriters.values()) {
      try {
        writer.close();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to close index writer", e);
      }
    }
    if (dirMap != null) {
      try {
        dirMap.close();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to close local directory", e);
      }
    }
  }

  @Override
  public void stop() {
    closeIndexWriters();
    LOG.info(getPackage() + " stopped");
  }

  // --- Stubby interface ---

  public SearchServicePb.IndexDocumentResponse indexDocument(Status status,
      SearchServicePb.IndexDocumentRequest req) {
    return indexDocumentForApp(getAppId(), req.getParams().getIndexSpec(),
        req.getParams().getDocumentList());
  }

  public SearchServicePb.IndexDocumentResponse indexDocumentForApp(String appId, String indexId,
      DocumentPb.Document doc) {
    return indexDocumentForApp(appId, IndexSpec.newBuilder().setName(indexId).build(),
        Lists.newArrayList(doc));
  }

  private SearchServicePb.IndexDocumentResponse indexDocumentForApp(String appId,
      IndexSpec indexSpec, List<DocumentPb.Document> docList) {
    SearchServicePb.IndexDocumentResponse.Builder respBuilder =
        SearchServicePb.IndexDocumentResponse.newBuilder();
    int docsToIndex = docList.size();
    if (dirMap == null) {
      LOG.severe("Index documents called before local search service was initialized");
      return respBuilder.addAllStatus(newRepeatedStatus(docsToIndex,
          SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST)).build();
    }
    IndexWriter indexWriter;
    try {
      indexWriter = getIndexWriter(dirMap.getDirectory(appId, indexSpec), true);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Unable to access index", e);
      return respBuilder.addAllStatus(newRepeatedStatus(docsToIndex,
          SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR)).build();
    }
    for (DocumentPb.Document d : docList) {
      try {
        DocumentChecker.checkValid(d);
      } catch (IllegalArgumentException e) {
        respBuilder.addStatus(RequestStatusUtil.newStatus(
            SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, e.getMessage()));
        continue;
      }

      String id = d.getId();
      if (Strings.isNullOrEmpty(d.getId())) {
        id = UUID.randomUUID().toString();
        d = d.toBuilder().setId(id).build();
        respBuilder.addDocId(id);
      } else {
        respBuilder.addDocId(d.getId());
      }
      Document doc = LuceneUtils.toLuceneDocument(id, d);
      try {
        indexWriter.updateDocument(new Term(LuceneUtils.DOCID_FIELD_NAME, id), doc);
      } catch (IOException e) {
        respBuilder.addStatus(RequestStatusUtil.newStatus(
            SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR));
        continue;
      }

      respBuilder.addStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.OK));
    }
    if (LOG.isLoggable(Level.FINE)) {
      try {
        LOG.fine(String.format("Added %d documents. Index %s holds %d documents",
            docList.size(), indexSpec.getName(), indexWriter.numDocs()));
      } catch (IOException e) {
        // Ignored; this is an exception in a debug level log message.
      }
    }
    commitChangesToIndexWriter(indexWriter);
    return respBuilder.build();
  }

  public SearchServicePb.DeleteDocumentResponse deleteDocument(Status status,
      SearchServicePb.DeleteDocumentRequest req) {
    return deleteDocumentForApp(getAppId(), req.getParams().getIndexSpec(),
        req.getParams().getDocIdList());
  }

  public SearchServicePb.DeleteDocumentResponse deleteDocumentForApp(String appId, String indexId,
      String docId) {
    return deleteDocumentForApp(appId, IndexSpec.newBuilder().setName(indexId).build(),
        Lists.newArrayList(docId));
  }

  private SearchServicePb.DeleteDocumentResponse deleteDocumentForApp(String appId,
      IndexSpec indexSpec, List<String> docIdList) {
    SearchServicePb.DeleteDocumentResponse.Builder respBuilder =
        SearchServicePb.DeleteDocumentResponse.newBuilder();
    int docsToDelete = docIdList.size();
    if (dirMap == null) {
      LOG.severe("Delete documents called before local search service was initialized");
      return respBuilder.addAllStatus(newRepeatedStatus(docsToDelete,
          SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST)).build();
    }
    if (docsToDelete <= 0) {
      LOG.info("Request to delete 0 documents; ignoring");
      return respBuilder.build();
    }
    IndexWriter indexWriter = null;
    try {
      indexWriter = getIndexWriter(dirMap.getDirectory(appId, indexSpec), false);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to access index directory", e);
      return respBuilder.addAllStatus(newRepeatedStatus(docsToDelete,
          SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR)).build();
    }
    if (indexWriter == null) {
      LOG.info("Request to delete documents from non-existing index; ignoring");
      return respBuilder.addAllStatus(newRepeatedStatus(docsToDelete,
          SearchServicePb.SearchServiceError.ErrorCode.OK, "Not found")).build();
    }
    Term[] deleteTerms = new Term[docsToDelete];
    List<SearchServicePb.RequestStatus> docStatusList = new ArrayList<>();
    for (int i = 0; i < docsToDelete; i++) {
      String docId = docIdList.get(i);
      deleteTerms[i] = LuceneUtils.newDeleteTerm(docId);
      boolean docExists;
      try {
        docExists = getDocuments(appId, indexSpec, docId, true, 1).iterator().hasNext();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to check existance of document " + docId, e);
        docExists = false;
      }
      docStatusList.add(RequestStatusUtil.newStatus(SearchServicePb.SearchServiceError.ErrorCode.OK,
          docExists ? null : "Not found"));
    }
    try {
      indexWriter.deleteDocuments(deleteTerms);
      return respBuilder.addAllStatus(docStatusList).build();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to delete documents", e);
      return respBuilder.addAllStatus(newRepeatedStatus(docsToDelete,
          SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR)).build();
    } finally {
      commitChangesToIndexWriter(indexWriter);
    }
  }

  public SearchServicePb.ListIndexesResponse listIndexes(Status status,
      SearchServicePb.ListIndexesRequest req) throws IOException {
    return listIndexesForApp(getAppId(), req);
  }

  public SearchServicePb.ListIndexesResponse listIndexesForApp(String appId,
      SearchServicePb.ListIndexesRequest req) throws IOException {
    SearchServicePb.ListIndexesResponse.Builder respBuilder =
        SearchServicePb.ListIndexesResponse.newBuilder();
    SearchServicePb.RequestStatus requestStatus =
        RequestStatusUtil.newStatus(SearchServicePb.SearchServiceError.ErrorCode.OK);
    if (dirMap == null) {
      LOG.severe("List indexes called before local search service was initialized");
      return respBuilder.setStatus(
          RequestStatusUtil.newStatus(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST))
          .build();
    }
    List<SearchServicePb.IndexMetadata.Builder> indexMetadatas =
        dirMap.listIndexes(appId, req.getParams());

    // Arbitrarily imagine the limit to be 1GB.
    // TODO: make this configurable: b/12370828
    final long MAX_STORAGE = 1024L * 1024L * 1024L;
    for (SearchServicePb.IndexMetadata.Builder builder : indexMetadatas) {
      // Check if an index exists.
      if (!IndexReader.indexExists(dirMap.getDirectory(appId, builder.getIndexSpec()))) {
        continue;
      }
      if (req.getParams().getFetchSchema()) {
        Map<String, Set<DocumentPb.FieldValue.ContentType>> schema =
            getFieldTypes(appId, builder.getIndexSpec());
        for (String fieldName : schema.keySet()) {
          builder.addField(DocumentPb.FieldTypes.newBuilder()
              .setName(fieldName).addAllType(schema.get(fieldName)));
        }
      }
      try {
        long amountUsed = addUpStorageUsed(appId, builder.getIndexSpec());
        builder.setStorage(
            SearchServicePb.IndexMetadata.Storage.newBuilder()
            .setAmountUsed(amountUsed).setLimit(MAX_STORAGE));
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to list indexes", e);
        requestStatus = RequestStatusUtil.newStatus(
            SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR, e.getMessage());
      }
      respBuilder.addIndexMetadata(builder);
    }
    return respBuilder.setStatus(requestStatus).build();
  }

  private long addUpStorageUsed(String appId, IndexSpec indexSpec) throws IOException {
    long amount = 0;
    for (Document luceneDoc : getDocuments(appId, indexSpec, "", true,
        Integer.MAX_VALUE)) {
      // NB. this must match the computations performed by
      // java/com/google/apphosting/dexter/analytics/DocSizeCalculator.java
      // and apphosting/api/search/search_service_quotas.cc
      DocumentPb.Document doc = getFullDoc(luceneDoc);
      for (DocumentPb.Field field : doc.getFieldList()) {
        amount += field.getSerializedSize();
      }
      amount += LuceneUtils.toAppengineDocumentId(luceneDoc).getId()
          .getBytes(StandardCharsets.UTF_8).length;
    }
    return amount;
  }

  private ImmutableList<Document> getDocuments(
      String appId, IndexSpec indexSpec, String start, boolean includeStart, int limit)
      throws IOException {
    Directory directory = dirMap.getDirectory(appId, indexSpec);
    if (!IndexReader.indexExists(directory)) {
      return ImmutableList.of();
    }
    final IndexSearcher indexSearcher = new IndexSearcher(directory, true);

    List<Document> docs = new ArrayList<>();
    try {
      TopDocs topDocs = indexSearcher.search(
          new TermRangeQuery(
              LuceneUtils.DOCID_FIELD_NAME,
              start,
              Character.toString((char) 0x7F), // upper limit
              includeStart, true),
          null,
          limit,
          new Sort(new SortField(LuceneUtils.DOCID_FIELD_NAME, SortField.STRING_VAL)));

      final ScoreDoc[] scoreDocs = topDocs.scoreDocs;
      for (ScoreDoc scoreDoc : scoreDocs) {
        try {
          docs.add(indexSearcher.doc(scoreDoc.doc));
        } catch (IOException e) {
          LOG.log(Level.SEVERE, e.getMessage(), e);
          throw new SearchException(e.toString());
        }
      }
    } finally {
      closeIndexSearcher(indexSearcher);
    }
    return ImmutableList.copyOf(docs);
  }

  private DocumentPb.Document getFullDoc(Document luceneDoc) throws InvalidProtocolBufferException {
    DocumentPb.Document gaeDoc;
    gaeDoc = LuceneUtils.toAppengineDocument(luceneDoc);
    if (gaeDoc == null) {
      String docId = LuceneUtils.toAppengineDocumentId(luceneDoc).getId();
      gaeDoc = documentsById.get(docId);
    }
    return gaeDoc;
  }

  public SearchServicePb.ListDocumentsResponse listDocuments(Status status,
      SearchServicePb.ListDocumentsRequest req) {
    return listDocumentsForApp(getAppId(), req);
  }

  public SearchServicePb.ListDocumentsResponse listDocumentsForApp(String appId,
      SearchServicePb.ListDocumentsRequest req) {
    SearchServicePb.ListDocumentsResponse.Builder respBuilder =
        SearchServicePb.ListDocumentsResponse.newBuilder();
    if (dirMap == null) {
      LOG.severe("listDocuments called before local search service was initialized");
      return respBuilder.setStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST)).build();
    }
    SearchServicePb.ListDocumentsParams params = req.getParams();

    try {
      Iterable<Document> docs =
          getDocuments(appId, params.getIndexSpec(), params.getStartDocId(),
              params.getIncludeStartDoc(), params.getLimit());

      for (Document doc : docs) {
        if (params.getKeysOnly()) {
          respBuilder.addDocument(LuceneUtils.toAppengineDocumentId(doc));
        } else {
          respBuilder.addDocument(getFullDoc(doc));
        }
      }
      respBuilder.setStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.OK));
      return respBuilder.build();
    } catch (FileNotFoundException e) {
      // This is thrown if the index is undefined. We should return an empty list response in this
      // case.
      LOG.info("List request for empty or non-existing index; ignoring");
      return respBuilder.setStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.OK)).build();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to list documents", e);
      return respBuilder.setStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR)).build();
    }
  }

  public SearchServicePb.SearchResponse search(Status status, SearchServicePb.SearchRequest req) {
    return searchForApp(getAppId(), req);
  }

  public SearchServicePb.SearchResponse searchForApp(String appId,
      SearchServicePb.SearchRequest req) {
    SearchServicePb.SearchResponse.Builder respBuilder =
        SearchServicePb.SearchResponse.newBuilder();
    try {
      FacetQueryChecker.checkValid(req.getParams());
    } catch (IllegalArgumentException ex) {
      return replyWith(
          SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST,
          ex.getMessage(),
          respBuilder);
    }
    if (dirMap == null) {
      LOG.severe("Search called before local search service was initialized");
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, respBuilder);
    }
    SearchServicePb.SearchParams searchParams = req.getParams();
    IndexSearcher indexSearcher = null;
    Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes = null;
    try {
      Directory directory = dirMap.getDirectory(appId, searchParams.getIndexSpec());
      if (IndexReader.indexExists(directory)) {
        fieldTypes = getFieldTypes(appId, searchParams.getIndexSpec());
        indexSearcher = new IndexSearcher(directory, true);
        indexSearcher.setDefaultFieldSortScoring(true, false);
      }
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to access index", e);
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR, respBuilder);
    }
    if (indexSearcher == null) {
      LOG.info("Search on an empty or non-existing index; ignoring");
      String message = String.format("Index '%s' in namespace '%s' does not exist",
          searchParams.getIndexSpec().getName(),
          searchParams.getIndexSpec().getNamespace());
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.OK, message, respBuilder);
    }
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine(String.format("Index %s holds %d documents", searchParams.getIndexSpec()
          .getName(), indexSearcher.getIndexReader().numDocs()));
    }
    try {
      Query q = new LuceneQueryBuilder(fieldTypes).parse(searchParams);
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("Query " + searchParams.getQuery() + " translated to " + q);
      }
      int offset = getOffset(searchParams);
      if (offset == -1) {
        // An error message has already been logged.
        return replyWith(
            SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST,
            "Failed to execute search request \"" + searchParams.getQuery() + "\"",
            respBuilder);
      }
      List<FieldGenerator> fieldGenerators = createFieldGenerators(searchParams, fieldTypes);
      Scorer scorer = Scorer.newInstance(searchParams, fieldTypes);
      Set<String> fieldFilter = createFilter(searchParams);
      int limit = searchParams.getLimit();

      Scorer.SearchResults results = scorer.search(indexSearcher, q, offset, limit);

      FieldValue defaultExpressionValue = Expression.makeValue(ContentType.HTML, "");

      int docIndex = offset;
      for (Scorer.Result result : results.results) {
        SearchServicePb.SearchResult.Builder resultBuilder =
            SearchServicePb.SearchResult.newBuilder();
        DocumentPb.Document fullDoc = getFullDoc(result.doc);
        for (FieldGenerator fieldGenerator : fieldGenerators) {
          FieldValue fieldValue = defaultExpressionValue;
          try {
            fieldValue = fieldGenerator.getExpression().eval(result.doc);
          } catch (EvaluationException e) {
            // ignore
          }
          resultBuilder.addExpression(
              DocumentPb.Field.newBuilder()
              .setName(fieldGenerator.getName())
              .setValue(fieldValue));
        }

        if (req.getParams().hasScorerSpec()) {
          result.addScores(resultBuilder);
        }
        resultBuilder.setDocument(filterDocument(fullDoc, searchParams.getKeysOnly(), fieldFilter));

        if (SearchServicePb.SearchParams.CursorType.PER_RESULT.equals(
            searchParams.getCursorType())) {
          resultBuilder.setCursor(encodeCursor(searchParams, docIndex + 1));
        }
        respBuilder.addResult(resultBuilder);
        docIndex++;
      }
      respBuilder.setStatus(RequestStatusUtil.newStatus(
          SearchServicePb.SearchServiceError.ErrorCode.OK)).setMatchedCount(results.totalHits);
      if (SearchServicePb.SearchParams.CursorType.SINGLE.equals(searchParams.getCursorType())) {
        if (results.totalHits - offset > limit) {
          respBuilder.setCursor(encodeCursor(searchParams, offset + limit));
        }
      }
      respBuilder.addAllFacetResult(Arrays.asList(results.facetResults));
      return respBuilder.build();
    } catch (SearchException e) {
      LOG.log(Level.SEVERE, "Failed to execute search", e);
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST,
          e.getMessage(), respBuilder);
    } catch (SearchQueryException e) {
      LOG.log(Level.SEVERE, "Failed to parse query", e);
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST,
          String.format("%s in query '%s'", e.getMessage(), searchParams.getQuery()), respBuilder);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to execute search", e);
      return replyWith(SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR, respBuilder);
    } finally {
      closeIndexSearcher(indexSearcher);
    }
  }

  /**
   * @param code the error code to be reported in the response
   * @param respBuilder the builder to be used to build a response
   * @return a search response with 0 documents match and a given error code
   */
  private SearchServicePb.SearchResponse replyWith(
      SearchServicePb.SearchServiceError.ErrorCode code,
      SearchServicePb.SearchResponse.Builder respBuilder) {
    return respBuilder
        .setStatus(RequestStatusUtil.newStatus(code))
        .setMatchedCount(0).build();
  }

  /**
   * @param code the error code to be reported in the response
   * @param message the message to be reported in the response
   * @param respBuilder the builder to be used to build a response
   * @return a search response with 0 documents match and a given error code
   */
  private SearchServicePb.SearchResponse replyWith(
      SearchServicePb.SearchServiceError.ErrorCode code,
      String message, SearchServicePb.SearchResponse.Builder respBuilder) {
    return respBuilder
        .setStatus(RequestStatusUtil.newStatus(code, message))
        .setMatchedCount(0).build();
  }

  // --- Helper methods ---

  /**
   * Closes, if necessary, the index searcher reporting any problems in logs.
   *
   * @param indexSearcher the index searcher to close
   */
  private static void closeIndexSearcher(IndexSearcher indexSearcher) {
    if (indexSearcher != null) {
      try {
        indexSearcher.close();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to close index searcher", e);
      }
    }
  }

  /**
   * Returns map of field names to a set of content types for each field.
   */
  private Map<String, Set<DocumentPb.FieldValue.ContentType>> getFieldTypes(
      String appId, SearchServicePb.IndexSpec indexSpec) {
    Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes =
        new TreeMap<String, Set<DocumentPb.FieldValue.ContentType>>();
    SearchServicePb.ListDocumentsRequest.Builder req =
        SearchServicePb.ListDocumentsRequest.newBuilder();
    req.getParamsBuilder().setIndexSpec(indexSpec);
    SearchServicePb.ListDocumentsResponse resp = listDocumentsForApp(appId, req.build());
    String lastDoc = addFieldTypesToMap(fieldTypes, resp.getDocumentList());
    while (resp.getDocumentCount() == req.getParams().getLimit()) {
      req.getParamsBuilder().setStartDocId(lastDoc).setIncludeStartDoc(false);
      resp = listDocumentsForApp(appId, req.build());
      lastDoc = addFieldTypesToMap(fieldTypes, resp.getDocumentList());
    }
    return fieldTypes;
  }
  
  /**
   * Adds updates the type with all the fields from each doc in the given doc list.
   * 
   * @param fieldTypes a mapping of field names to field types.
   * @param docList list of documents to update the typemap with.
   * @return docId of the last document in the docList
   */
  private String addFieldTypesToMap(Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes,
      List<DocumentPb.Document> docList) {
    String lastDoc = "";
    for (DocumentPb.Document document : docList) {
      for (DocumentPb.Field field : document.getFieldList()) {
        Set<DocumentPb.FieldValue.ContentType> types = fieldTypes.get(field.getName());
        if (types == null) {
          types = new LinkedHashSet<DocumentPb.FieldValue.ContentType>();
          fieldTypes.put(field.getName(), types);
        }
        types.add(field.getValue().getType());
      }
      lastDoc = document.getId();
    }
    return lastDoc;
  }

  private static List<SearchServicePb.RequestStatus> newRepeatedStatus(int count,
      SearchServicePb.SearchServiceError.ErrorCode errorCode) {
    List<SearchServicePb.RequestStatus> statusList = new ArrayList<>();
    for (int i = 0; i < count; ++i) {
      statusList.add(RequestStatusUtil.newStatus(errorCode));
    }
    return statusList;
  }

  private static List<SearchServicePb.RequestStatus> newRepeatedStatus(int count,
      SearchServicePb.SearchServiceError.ErrorCode errorCode, String errorDetail) {
    List<SearchServicePb.RequestStatus> statusList = new ArrayList<>();
    for (int i = 0; i < count; ++i) {
      statusList.add(RequestStatusUtil.newStatus(errorCode, errorDetail));
    }
    return statusList;
  }

  private static Map<Directory, IndexWriter> indexWriters = new HashMap<Directory, IndexWriter>();

  private IndexWriter getIndexWriter(Directory directory, boolean createIfNotPresent)
      throws IOException {
    synchronized (indexWriters) {
      IndexWriter writer = indexWriters.get(directory);
      if (writer != null) {
        return writer;
      }

      if (IndexReader.indexExists(directory)) {
        writer = new IndexWriter(directory, analyzer, false, MAX_FIELD_LENGTH);
      } else if (!createIfNotPresent) {
        return null;
      } else {
        writer = new IndexWriter(directory, analyzer, true, MAX_FIELD_LENGTH);
      }
      indexWriters.put(directory, writer);
      return writer;
    }
  }

  private void recursiveDelete(File file) throws IOException {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    if (!file.delete()) {
      throw new IOException("Failed to delete file " + file);
    }
  }

  private void clearIndexes(final File indexDirectory) {
    if (indexDirectory == null) {
      dirMap = new LuceneDirectoryMap.RamBased();
    } else {
      closeIndexWriters();
      try {
        AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws IOException {
            if (indexDirectory.exists()) {
              recursiveDelete(indexDirectory);
            }
            indexDirectory.mkdirs();
            return null;
          }
        });
      } catch (PrivilegedActionException e) {
        throw new RuntimeException(e);
      }
      dirMap = new LuceneDirectoryMap.FileBased(indexDirectory);
    }
  }

  /**
   * Commits change to index reporting any problems in logs.
   *
   * @param indexWriter the index writer to close
   */
  private void commitChangesToIndexWriter(IndexWriter indexWriter) {
    if (indexWriter != null) {
      try {
        indexWriter.commit();
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "Failed to commit changes to an index", e);
      }
    }
  }

  /**
   * @return offset computed from cursor parameter, or -1 on error, in which case we
   * guarantee an error message has been logged.
   */
  private static int getOffset(SearchServicePb.SearchParams searchParams) {
    if (searchParams.hasOffset() && searchParams.hasCursor()) {
      LOG.severe("Both offset and cursor are set");
      return -1;
    }
    if (searchParams.hasOffset()) {
      return searchParams.getOffset();
    }
    if (!searchParams.hasCursor()) {
      return 0;
    }
    return decodeCursor(searchParams, searchParams.getCursor());
  }

  private static Set<String> createFilter(SearchServicePb.SearchParams searchParams) {
    // TODO: check the priority in backend
    if (searchParams.getKeysOnly()) {
      return new HashSet<String>();
    }
    if (searchParams.getFieldSpec().getNameList().isEmpty()) {
      // If no fields are specified, all fields should be returned.
      return null;
    }
    return new HashSet<String>(searchParams.getFieldSpec().getNameList());
  }

  /**
   * Filter fields in appengine document.
   *
   * @param fullDoc an appengine document with all fields.
   * @param keysOnly whether the request specified that only keys should be returned
   * @param fieldFilter a potentially null set of field filters
   * @return an appengine document with fields specified by filter
   */
  private static DocumentPb.Document filterDocument(
      DocumentPb.Document fullDoc, boolean keysOnly, Set<String> fieldFilter) {
    DocumentPb.Document.Builder docBuilder = DocumentPb.Document.newBuilder();
    docBuilder.setId(fullDoc.getId());
    if (!keysOnly) {
      for (DocumentPb.Field field : fullDoc.getFieldList()) {
        if (fieldFilter == null || fieldFilter.contains(field.getName())) {
          docBuilder.addField(field);
        }
      }
      docBuilder.setOrderId(fullDoc.getOrderId());
      if (fullDoc.hasLanguage()) {
        docBuilder.setLanguage(fullDoc.getLanguage());
      }
    }
    return docBuilder.build();
  }

  private static List<FieldGenerator> createFieldGenerators(
      SearchServicePb.SearchParams searchParams, Map<String,
      Set<DocumentPb.FieldValue.ContentType>> fieldTypes) {
    ExpressionBuilder exprBuilder = new ExpressionBuilder(fieldTypes);
    List<FieldGenerator> fieldGenerators = new ArrayList<FieldGenerator>();
    for (SearchServicePb.FieldSpec.Expression exprSpec :
         searchParams.getFieldSpec().getExpressionList()) {
      Expression expr = null;
      try {
        expr = exprBuilder.parse(exprSpec.getExpression());
      } catch (IllegalArgumentException e) {
          String errorMessage = String.format("Failed to parse field \'%s\': %s",
              exprSpec.getExpression(), e.getMessage());
          throw new SearchException(errorMessage);
      }
      fieldGenerators.add(new FieldGenerator(exprSpec.getName(), expr));
    }
    return fieldGenerators;
  }

  private static String getAppId() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      LOG.severe("Unable to retrieve information about the calling application. Aborting!");
      throw new ApiProxy.ApiProxyException("Failed to access application environment");
    }
    String appId = environment.getAppId();
    if (appId == null) {
      LOG.severe("Unable to read application ID. Aborting!");
      throw new ApiProxy.ApplicationException(
          SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR_VALUE,
          "Failed to retrieve application ID");
    }
    return appId;
  }

  public DeleteSchemaResponse deleteSchema(Object object, DeleteSchemaRequest request) {
    // We don't have a schema to delete because the local search service calculates the
    // schema based on the documents that are alive in the index. We thus return success
    // to keep things simple.
    SearchServicePb.DeleteSchemaParams params = request.getParams();
    SearchServicePb.DeleteSchemaResponse.Builder builder =
        SearchServicePb.DeleteSchemaResponse.newBuilder();
    for (int i = 0; i < params.getIndexSpecCount(); i++) {
      builder.addStatus(RequestStatusUtil.newStatus(ErrorCode.OK));
    }
    return builder.build();
  }
}
