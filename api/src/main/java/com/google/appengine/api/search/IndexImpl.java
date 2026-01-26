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

package com.google.appengine.api.search;

import static com.google.appengine.api.search.FutureHelper.quietGet;

import com.google.appengine.api.search.checkers.DocumentChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The default implementation of {@link Index}. This class uses a
 * {@link SearchApiHelper} to forward all requests to an appserver.
 */
class IndexImpl implements Index {

  private final SearchApiHelper apiHelper;
  private final IndexSpec spec;
  private final Schema schema;
  private final SearchServiceConfig config;
  private final Long storageUsage;
  private final Long storageLimit;

  /**
   * Creates new index specification.
   *
   * @param apiHelper the helper used to forward all calls
   * @param config a {@link SearchServiceConfig} instance that describes the
   * index implementation.
   * @param indexSpec the index specification
   */
  IndexImpl(SearchApiHelper apiHelper, SearchServiceConfig config, IndexSpec indexSpec) {
    this(apiHelper, config, indexSpec, null, null, null);
  }

  /**
   * Creates new index specification.
   *
   * @param apiHelper the helper used to forward all calls
   * @param config a {@link SearchServiceConfig} instance that describes the
   * index implementation.
   * @param indexSpec the index specification
   * @param schema the {@link Schema} defining the names and types of fields
   * supported
   */
  IndexImpl(SearchApiHelper apiHelper, SearchServiceConfig config, IndexSpec indexSpec,
      Schema schema, Long amount, Long limit) {
    this.apiHelper = Preconditions.checkNotNull(apiHelper, "Internal error");
    Preconditions.checkNotNull(config.getNamespace(), "Internal error");
    this.spec = Preconditions.checkNotNull(indexSpec, "Internal error");
    this.schema = schema;
    this.config = config;
    this.storageUsage = amount;
    this.storageLimit = limit;
  }

  @Override
  public String getName() {
    return spec.getName();
  }

  @Override
  public String getNamespace() {
    return config.getNamespace();
  }

  @Override
  public Schema getSchema() {
    return schema;
  }

  private RuntimeException noStorageInfo() {
    return new UnsupportedOperationException("Storage information is not available");
  }

  @Override
  public long getStorageUsage() {
    if (storageUsage == null) {
      throw noStorageInfo();
    }
    return storageUsage;
  }

  @Override
  public long getStorageLimit() {
    if (storageLimit == null) {
      throw noStorageInfo();
    }
    return storageLimit;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + spec.hashCode();
    result = prime * result + ((schema == null) ? 0 : schema.hashCode());
    result = prime * result + ((storageUsage == null) ? 0 : storageUsage.hashCode());
    result = prime * result + ((storageLimit == null) ? 0 : storageLimit.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IndexImpl other)) {
      return false;
    }
    return Util.equalObjects(spec, other.spec)
        && Util.equalObjects(schema, other.schema)
        && Util.equalObjects(storageUsage, other.storageUsage)
        && Util.equalObjects(storageLimit, other.storageLimit);
  }

  @Override
  public String toString() {
    String storageInfo =
        (storageUsage == null || storageLimit == null)
        ? "(no storage data)"
        : " (%d/%d)".formatted(storageUsage.longValue(), storageLimit.longValue());
    return "IndexImpl{namespace: %s, %s, %s%s}".formatted(config.getNamespace(), spec,
        (schema == null ? "(null schema)" : schema), storageInfo);
  }

  @Override
  public Future<Void> deleteSchemaAsync() {
    SearchServicePb.DeleteSchemaParams.Builder builder =
        SearchServicePb.DeleteSchemaParams.newBuilder().addIndexSpec(
            spec.copyToProtocolBuffer(config.getNamespace()));

    Future<SearchServicePb.DeleteSchemaResponse.Builder> future =
        apiHelper.makeAsyncDeleteSchemaCall(builder, config.getDeadline());
    return new FutureWrapper<SearchServicePb.DeleteSchemaResponse.Builder,
           Void>(future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new DeleteException(result);
      }

      @Override
      protected Void wrap(SearchServicePb.DeleteSchemaResponse.Builder key)
          throws Exception {
        SearchServicePb.DeleteSchemaResponse response = key.build();
        ArrayList<OperationResult> results = new ArrayList<>(
            response.getStatusCount());
        for (SearchServicePb.RequestStatus status : response.getStatusList()) {
          results.add(new OperationResult(status));
        }
        if (response.getStatusList().size() != 1) {
          throw new DeleteException(
              new OperationResult(
                  StatusCode.INTERNAL_ERROR,
                  "Expected 1 removed schema, but got %d".formatted(
                                response.getStatusList().size())),
              results);
        }
        for (OperationResult result : results) {
          if (result.getCode() != StatusCode.OK) {
            throw new DeleteException(result, results);
          }
        }
        return null;
      }
    };
  }

  @Override
  public Future<Void> deleteAsync(String... documentIds) {
    return deleteAsync(Arrays.asList(documentIds));
  }

  @Override
  public Future<Void> deleteAsync(final Iterable<String> documentIds) {
    Preconditions.checkArgument(documentIds != null,
        "Delete documents given null collection of document ids");
    SearchServicePb.DeleteDocumentParams.Builder builder =
        SearchServicePb.DeleteDocumentParams.newBuilder().setIndexSpec(
            spec.copyToProtocolBuffer(config.getNamespace()));
    int size = 0;
    for (String documentId : documentIds) {
      size++;
      builder.addDocId(DocumentChecker.checkDocumentId(documentId));
    }
    if (size > SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST) {
      throw new IllegalArgumentException(
          "number of doc ids, %s, exceeds maximum %s".formatted(size,
                        SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST));
    }
    final int documentIdsSize = size;
    Future<SearchServicePb.DeleteDocumentResponse.Builder> future =
        apiHelper.makeAsyncDeleteDocumentCall(builder, config.getDeadline());
    return new FutureWrapper<SearchServicePb.DeleteDocumentResponse.Builder,
           Void>(future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new DeleteException(result);
      }

      @Override
      protected Void wrap(SearchServicePb.DeleteDocumentResponse.Builder key)
          throws Exception {
        SearchServicePb.DeleteDocumentResponse response = key.build();
        ArrayList<OperationResult> results = new ArrayList<>(
            response.getStatusCount());
        for (SearchServicePb.RequestStatus status : response.getStatusList()) {
          results.add(new OperationResult(status));
        }
        if (documentIdsSize != response.getStatusList().size()) {
          throw new DeleteException(
              new OperationResult(
                  StatusCode.INTERNAL_ERROR,
                  "Expected %d removed documents, but got %d".formatted(documentIdsSize,
                                response.getStatusList().size())),
              results);
        }
        for (OperationResult result : results) {
          if (result.getCode() != StatusCode.OK) {
            throw new DeleteException(result, results);
          }
        }
        return null;
      }
    };
  }

  @Override
  public Future<PutResponse> putAsync(Document... documents) {
    return putAsync(Arrays.asList(documents));
  }

  @Override
  public Future<PutResponse> putAsync(Document.Builder... builders) {
    List<Document> documents = new ArrayList<>();
    for (int i = 0; i < builders.length; i++) {
      documents.add(builders[i].build());
    }
    return putAsync(documents);
  }

  @Override
  public Future<PutResponse> putAsync(final Iterable<Document> documents) {
    Preconditions.checkNotNull(documents, "document list cannot be null");
    if (Iterables.isEmpty(documents)) {
      return new FutureHelper.FakeFuture<>(
          new PutResponse(Collections.<OperationResult>emptyList(),
                                     Collections.<String>emptyList()));
    }
    SearchServicePb.IndexDocumentParams.Builder builder =
        SearchServicePb.IndexDocumentParams.newBuilder()
            .setIndexSpec(spec.copyToProtocolBuffer(config.getNamespace()));
    Map<String, Document> docMap = new HashMap<>();
    int size = 0;
    for (Document document : documents) {
      Document other = null;
      if (document.getId() != null) {
        other = docMap.put(document.getId(), document);
      }
      if (other != null) {
        // If the other document is identical to the current one, do not
        // add the current one.
        if (!document.isIdenticalTo(other)) {
          throw new IllegalArgumentException(
              "Put request with documents with the same ID \"%s\" but different content".formatted(
                  document.getId()));
        }
      }
      if (other == null) {
        size++;
        builder.addDocument(Preconditions.checkNotNull(document, "document cannot be null")
            .copyToProtocolBuffer());
      }
    }
    if (size > SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST) {
      throw new IllegalArgumentException(
          "number of documents, %s, exceeds maximum %s".formatted(size,
                        SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST));
    }
    final int documentsSize = size;
    Future<SearchServicePb.IndexDocumentResponse.Builder> future =
        apiHelper.makeAsyncIndexDocumentCall(builder, config.getDeadline());
    return new FutureWrapper<SearchServicePb.IndexDocumentResponse.Builder,
           PutResponse>(future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new PutException(result);
      }

      @Override
      protected PutResponse wrap(SearchServicePb.IndexDocumentResponse.Builder key)
          throws Exception {
        SearchServicePb.IndexDocumentResponse response = key.build();
        List<OperationResult> results = newOperationResultList(response);
        if (documentsSize != response.getStatusList().size()) {
          throw new PutException(
              new OperationResult(
                  StatusCode.INTERNAL_ERROR,
                  "Expected %d indexed documents, but got %d".formatted(documentsSize,
                      response.getStatusList().size())), results, response.getDocIdList());
        }
        for (OperationResult result : results) {
          if (result.getCode() != StatusCode.OK) {
            throw new PutException(result, results, response.getDocIdList());
          }
        }
        return new PutResponse(results, response.getDocIdList());
      }

      /**
       * Constructs a list of OperationResult from an index document response.
       *
       * @param response the index document response to extract operation
       * results from
       * @return a list of OperationResult
       */
      private List<OperationResult> newOperationResultList(
          SearchServicePb.IndexDocumentResponse response) {
        ArrayList<OperationResult> results = new ArrayList<>(
            response.getStatusCount());
        for (SearchServicePb.RequestStatus status : response.getStatusList()) {
          results.add(new OperationResult(status));
        }
        return results;
      }
    };
  }

  private Future<Results<ScoredDocument>> executeSearchForResults(
      SearchServicePb.SearchParams.Builder params) {
    Future<SearchServicePb.SearchResponse.Builder> future =
        apiHelper.makeAsyncSearchCall(params, config.getDeadline());
    return new FutureWrapper<SearchServicePb.SearchResponse.Builder,
           Results<ScoredDocument>>(future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new SearchException(result);
      }

      @Override
      protected Results<ScoredDocument> wrap(SearchServicePb.SearchResponse.Builder key)
      throws Exception {
        SearchServicePb.SearchResponse response = key.build();
        SearchServicePb.RequestStatus status = response.getStatus();
        if (status.getCode() != SearchServicePb.SearchServiceError.ErrorCode.OK) {
          throw new SearchException(new OperationResult(status));
        }
        List<ScoredDocument> scoredDocs = new ArrayList<>();
        for (SearchServicePb.SearchResult result : response.getResultList()) {
          List<Field> expressions = new ArrayList<>();
          for (DocumentPb.Field expression : result.getExpressionList()) {
            expressions.add(Field.newBuilder(expression).build());
          }
          ScoredDocument.Builder scoredDocBuilder = ScoredDocument.newBuilder(result.getDocument());
          for (Double score : result.getScoreList()) {
            scoredDocBuilder.addScore(score);
          }
          for (Field expression : expressions) {
            scoredDocBuilder.addExpression(expression);
          }
          if (result.hasCursor()) {
            scoredDocBuilder.setCursor(
                Cursor.newBuilder().build("true:" + result.getCursor()));
          }
          scoredDocs.add(scoredDocBuilder.build());
        }
        List<FacetResult> facetResults = new ArrayList<>();
        for (SearchServicePb.FacetResult result : response.getFacetResultList()) {
          facetResults.add(FacetResult.newBuilder(result).build());
        }
        Results<ScoredDocument> scoredResults = new Results<>(
            new OperationResult(status),
            scoredDocs, response.getMatchedCount(), response.getResultCount(),
            (response.hasCursor() ? Cursor.newBuilder().build("false:" + response.getCursor())
                : null), facetResults);
        return scoredResults;
      }
    };
  }

  @Override
  public Future<Results<ScoredDocument>> searchAsync(String query) {
    return searchAsync(Query.newBuilder().build(
        Preconditions.checkNotNull(query, "query cannot be null")));
  }

  @Override
  public Future<Results<ScoredDocument>> searchAsync(Query query) {
    Preconditions.checkNotNull(query, "query cannot be null");
    return executeSearchForResults(
        query.copyToProtocolBuffer().setIndexSpec(spec.copyToProtocolBuffer(
            config.getNamespace())));
  }

  @Override
  public Future<GetResponse<Document>> getRangeAsync(GetRequest.Builder builder) {
    return getRangeAsync(builder.build());
  }

  @Override
  public Future<GetResponse<Document>> getRangeAsync(GetRequest request) {
    Preconditions.checkNotNull(request, "list documents request cannot be null");

    SearchServicePb.ListDocumentsParams.Builder params =
        request.copyToProtocolBuffer().setIndexSpec(spec.copyToProtocolBuffer(
        config.getNamespace()));

    Future<SearchServicePb.ListDocumentsResponse.Builder> future =
        apiHelper.makeAsyncListDocumentsCall(params, config.getDeadline());
    return new FutureWrapper<SearchServicePb.ListDocumentsResponse.Builder,
        GetResponse<Document>>(future) {
      @Override
      protected Throwable convertException(Throwable cause) {
        OperationResult result = OperationResult.convertToOperationResult(cause);
        return (result == null) ? cause : new GetException(result);
      }

      @Override
      protected GetResponse<Document> wrap(
          SearchServicePb.ListDocumentsResponse.Builder key) throws Exception {
        SearchServicePb.ListDocumentsResponse response = key.build();
        SearchServicePb.RequestStatus status = response.getStatus();

        if (status.getCode() != ErrorCode.OK) {
          throw new GetException(new OperationResult(status));
        }

        List<Document> results = new ArrayList<>();
        for (DocumentPb.Document document : response.getDocumentList()) {
          results.add(Document.newBuilder(document).build());
        }
        return new GetResponse<>(results);
      }
    };
  }

  @Override
  public Document get(String documentId) {
    Preconditions.checkNotNull(documentId, "documentId must not be null");
    GetResponse<Document> response =
        getRange(GetRequest.newBuilder().setStartId(documentId).setLimit(1));
    for (Document document : response) {
      if (documentId.equals(document.getId())) {
        return document;
      }
    }
    return null;
  }

  @Override
  public void deleteSchema() {
    quietGet(deleteSchemaAsync());
  }

  @Override
  public void delete(String... documentIds) {
    quietGet(deleteAsync(documentIds));
  }

  @Override
  public void delete(Iterable<String> documentIds) {
    quietGet(deleteAsync(documentIds));
  }

  @Override
  public PutResponse put(Document... documents) {
    return quietGet(putAsync(documents));
  }

  @Override
  public PutResponse put(Document.Builder... builders) {
    return quietGet(putAsync(builders));
  }

  @Override
  public PutResponse put(Iterable<Document> documents) {
    return quietGet(putAsync(documents));
  }

  @Override
  public Results<ScoredDocument> search(String query) {
    return quietGet(searchAsync(query));
  }

  @Override
  public Results<ScoredDocument> search(Query query) {
    return quietGet(searchAsync(query));
  }

  @Override
  public GetResponse<Document> getRange(GetRequest request) {
    return quietGet(getRangeAsync(request));
  }

  @Override
  public GetResponse<Document> getRange(GetRequest.Builder builder) {
    return getRange(builder.build());
  }
}
