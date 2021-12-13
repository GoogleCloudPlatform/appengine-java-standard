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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteDocumentResponse;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteSchemaResponse;
import com.google.appengine.api.search.proto.SearchServicePb.IndexDocumentResponse;
import com.google.appengine.api.search.proto.SearchServicePb.RequestStatus;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.search.DocumentPb;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Index} the user land API for the search service.
 *
 */
@RunWith(JUnit4.class)
public class IndexImplTest {

  private static final String INDEX_NAME = "index";
  public static final Double NO_DEADLINE = null;

  private SearchApiHelper apiHelper;
  private Index index;
  private static final OperationResult OK = new OperationResult(ErrorCode.OK, null);
  private static final OperationResult TRANSIENT_ERROR =
      new OperationResult(ErrorCode.TRANSIENT_ERROR, null);
  private static final OperationResult INTERNAL_ERROR =
      new OperationResult(ErrorCode.INTERNAL_ERROR, null);
  private RequestStatus statusOK;
  private RequestStatus statusInvalidRequest;
  private RequestStatus statusTransientError;
  private RequestStatus statusInternalError;

  @Before
  public void setUp() throws Exception {
    ApiProxy.Environment environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);
    apiHelper = mock(SearchApiHelper.class);
    SearchService searchService = SearchServiceFactoryImpl.getSearchService(apiHelper);
    index = searchService.getIndex(IndexSpec.newBuilder().setName(INDEX_NAME).build());
    statusOK = RequestStatus.newBuilder().setCode(ErrorCode.OK).build();
    statusInvalidRequest = RequestStatus.newBuilder().setCode(ErrorCode.INVALID_REQUEST).build();
    statusTransientError = RequestStatus.newBuilder().setCode(ErrorCode.TRANSIENT_ERROR).build();
    statusInternalError = RequestStatus.newBuilder().setCode(ErrorCode.INTERNAL_ERROR).build();
  }

  @After
  public void tearDown() {
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  private Document createDocument(String documentId, String name, String value) {
    return createDocumentBuilder(documentId, name, value).build();
  }

  private Document.Builder createDocumentBuilder(String documentId, String name, String value) {
    Document.Builder builder = Document.newBuilder();
    if (documentId != null) {
      builder.setId(documentId);
    }
    return builder.addField(Field.newBuilder().setName(name).setText(value).build());
  }

  private Future<DeleteDocumentResponse.Builder> createDeleteFuture(RequestStatus... statuses) {
    DeleteDocumentResponse.Builder responseBuilder = DeleteDocumentResponse.newBuilder();
    for (RequestStatus status : statuses) {
      responseBuilder.addStatus(status);
    }
    return new FutureHelper.FakeFuture<>(responseBuilder);
  }

  private Future<DeleteSchemaResponse.Builder> createDeleteSchemaFuture(RequestStatus... statuses) {
    DeleteSchemaResponse.Builder responseBuilder = DeleteSchemaResponse.newBuilder();
    for (RequestStatus status : statuses) {
      responseBuilder.addStatus(status);
    }
    return new FutureHelper.FakeFuture<>(responseBuilder);
  }

  private void expectDeleteDocumentResponse(RequestStatus... statuses) {
    when(apiHelper.makeAsyncDeleteDocumentCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(createDeleteFuture(statuses));
  }

  private void expectDeleteSchemaResponse(RequestStatus... statuses) {
    when(apiHelper.makeAsyncDeleteSchemaCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(createDeleteSchemaFuture(statuses));
  }

  private Future<IndexDocumentResponse.Builder> createIndexFuture(RequestStatus... statuses) {
    IndexDocumentResponse.Builder responseBuilder = IndexDocumentResponse.newBuilder();
    for (RequestStatus status : statuses) {
      responseBuilder.addStatus(status);
    }
    return new FutureHelper.FakeFuture<>(responseBuilder);
  }

  private void expectIndexDocumentResponse(RequestStatus... statuses) {
    when(apiHelper.makeAsyncIndexDocumentCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(createIndexFuture(statuses));
  }

  private void expectSearchResponse(SearchServicePb.SearchResponse.Builder responseBuilder) {
    Future<SearchServicePb.SearchResponse.Builder> future =
        new FutureHelper.FakeFuture<>(responseBuilder);
    when(apiHelper.makeAsyncSearchCall(notNull(), same(NO_DEADLINE))).thenReturn(future);
  }

  private void expectListDocumentsResponse(
      SearchServicePb.ListDocumentsResponse.Builder responseBuilder) {
    Future<SearchServicePb.ListDocumentsResponse.Builder> future =
        new FutureHelper.FakeFuture<>(responseBuilder);
    when(apiHelper.makeAsyncListDocumentsCall(notNull(), same(NO_DEADLINE))).thenReturn(future);
  }

  @Test
  public void testGivenNamespace() {
    String ns = "user-given-namespace";
    SearchService searchService = SearchServiceFactory.getSearchService(ns);
    Index index = searchService.getIndex(IndexSpec.newBuilder().setName("index-name").build());
    assertThat(index.getNamespace()).isEqualTo(ns);
  }

  @Test
  public void testGlobalNamespace() {
    String ns = "some-global-namespace";
    NamespaceManager.set(ns);
    SearchService searchService = SearchServiceFactory.getSearchService();
    Index index = searchService.getIndex(IndexSpec.newBuilder().setName("blah").build());
    assertThat(index.getNamespace()).isEqualTo(ns);
  }

  @Test
  public void testDeleteAsync_nullDocumentId() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> index.deleteAsync((String) null));
  }

  @Test
  public void testDeleteAsync_emptyDocumentId() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> index.deleteAsync(""));
  }

  @Test
  public void testDeleteAsync_documentIdTooLong() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            index.deleteAsync(Strings.repeat("a", SearchApiLimits.MAXIMUM_DOCUMENT_ID_LENGTH + 1)));
  }

  @Test
  public void testDeleteAsync_documentIdInvalid() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> index.deleteAsync("!"));
  }

  @Test
  public void testDeleteSchemaAsync_ok() throws Exception {
    expectDeleteSchemaResponse(statusOK);
    index.deleteSchemaAsync().get();
    verify(apiHelper).makeAsyncDeleteSchemaCall(any(), any());
  }

  @Test
  public void testDeleteAsync_ok() throws Exception {
    expectDeleteDocumentResponse(statusOK);
    index.deleteAsync("documentId").get();
    verify(apiHelper).makeAsyncDeleteDocumentCall(any(), any());
  }

  @Test
  public void testDeleteSchema() throws Exception {
    expectDeleteSchemaResponse(statusOK);
    index.deleteSchema();
    // No exceptions equals success.
    verify(apiHelper).makeAsyncDeleteSchemaCall(any(), any());
  }

  @Test
  public void testDelete() throws Exception {
    expectDeleteDocumentResponse(statusOK);
    index.delete("documentId");
    // No exceptions equals success.
    verify(apiHelper).makeAsyncDeleteDocumentCall(any(), any());
  }

  @Test
  public void testDeleteSchemaAsync_failure() throws Exception {
    expectDeleteSchemaResponse(statusTransientError);
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.deleteSchemaAsync().get());
    DeleteException deleteException = (DeleteException) e.getCause();
    assertThat(deleteException.getOperationResult().getCode())
        .isEqualTo(StatusCode.TRANSIENT_ERROR);
  }

  @Test
  public void testDeleteAsync_failure() throws Exception {
    expectDeleteDocumentResponse(statusTransientError);
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.deleteAsync("documentId").get());
    DeleteException deleteException = (DeleteException) e.getCause();
    assertThat(deleteException.getOperationResult().getCode())
        .isEqualTo(StatusCode.TRANSIENT_ERROR);
  }

  @Test
  public void testDeleteSchema_internalError() throws Exception {
    String message = "This message should be shown to the user";
    RequestStatus status =
        RequestStatus.newBuilder()
            .setCode(ErrorCode.INTERNAL_ERROR)
            .setErrorDetail(message)
            .build();
    expectDeleteSchemaResponse(status);
    DeleteException e = assertThrows(DeleteException.class, () -> index.deleteSchema());
    OperationResult result = new OperationResult(ErrorCode.INTERNAL_ERROR, message);
    assertThat(e.getOperationResult().getMessage()).isEqualTo(message);
    assertThat(e.getOperationResult()).isEqualTo(result);
  }

  @Test
  public void testDelete_internalError() throws Exception {
    String message = "This message should be shown to the user";
    RequestStatus status =
        RequestStatus.newBuilder()
            .setCode(ErrorCode.INTERNAL_ERROR)
            .setErrorDetail(message)
            .build();
    expectDeleteDocumentResponse(status);
    DeleteException e = assertThrows(DeleteException.class, () -> index.delete("documentId"));
    OperationResult result = new OperationResult(ErrorCode.INTERNAL_ERROR, message);
    assertThat(e.getOperationResult().getMessage()).isEqualTo(message);
    assertThat(e.getOperationResult()).isEqualTo(result);
  }

  @Test
  public void testDeleteSchema_invalidRequest() throws Exception {
    expectDeleteSchemaResponse(statusInvalidRequest);
    DeleteException e = assertThrows(DeleteException.class, () -> index.deleteSchema());
    assertThat(e.getOperationResult().getCode()).isEqualTo(StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testDelete_invalidRequest() throws Exception {
    expectDeleteDocumentResponse(statusInvalidRequest);
    DeleteException e = assertThrows(DeleteException.class, () -> index.delete("documentId"));
    assertThat(e.getOperationResult().getCode()).isEqualTo(StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testDeleteAsync_tooMany() throws Exception {
    expectDeleteDocumentResponse(
        Collections.nCopies(SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, statusOK)
            .toArray(new RequestStatus[0]));
    expectDeleteDocumentResponse(statusInvalidRequest); // Expected error.

    List<String> list = Lists.newArrayList();
    for (int i = 0; i < SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST; i++) {
      list.add("id_" + i);
    }

    // Check that the call works for documents number less than maximum allowed.
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = index.deleteAsync(list);
    list.add("id_" + (SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST + 1));

    assertThrows(IllegalArgumentException.class, () -> index.deleteAsync(list));
  }

  @Test
  public void testDeleteAsync_emptyDocumentIdInList() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> index.deleteAsync(Lists.newArrayList("")));
  }

  @Test
  public void testDeleteAsync_okList() throws Exception {
    expectDeleteDocumentResponse(statusOK, statusOK);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = index.deleteAsync(Lists.newArrayList("doc1", "doc2"));
    // No exception equals success.
    verify(apiHelper).makeAsyncDeleteDocumentCall(any(), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testDelete_list() throws Exception {
    expectDeleteDocumentResponse(statusOK);
    index.delete(Lists.newArrayList("documentId"));
    // No exception equals success.
    verify(apiHelper).makeAsyncDeleteDocumentCall(any(), any());
  }

  @Test
  public void testDeleteAsync_okAndFailure() throws Exception {
    expectDeleteDocumentResponse(statusTransientError, statusOK);
    Future<Void> response = index.deleteAsync(Lists.newArrayList("doc1", "doc2"));
    assertThat(response).isNotNull();
    ExecutionException e = assertThrows(ExecutionException.class, response::get);
    DeleteException deleteException = (DeleteException) e.getCause();
    assertThat(deleteException.getOperationResult()).isEqualTo(TRANSIENT_ERROR);
    assertThat(deleteException.getResults()).containsExactly(TRANSIENT_ERROR, OK).inOrder();
  }

  @Test
  public void testDeleteSchemaAsync_moreOksThanRequested() throws Exception {
    expectDeleteSchemaResponse(statusOK, statusOK);
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.deleteSchemaAsync().get());
    DeleteException deleteException = (DeleteException) e.getCause();
    assertThat(deleteException.getOperationResult().getCode()).isEqualTo(StatusCode.INTERNAL_ERROR);
    assertThat(deleteException.getResults()).containsExactly(OK, OK).inOrder();
  }

  @Test
  public void testDeleteAsync_moreOksThanRequested() throws Exception {
    expectDeleteDocumentResponse(statusOK, statusOK);
    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> index.deleteAsync(Lists.newArrayList("doc1")).get());
    DeleteException deleteException = (DeleteException) e.getCause();
    assertThat(deleteException.getOperationResult().getCode()).isEqualTo(StatusCode.INTERNAL_ERROR);
    assertThat(deleteException.getResults()).containsExactly(OK, OK).inOrder();
  }

  @Test
  public void testDelete_transientError() throws Exception {
    expectDeleteDocumentResponse(statusTransientError);
    DeleteException e =
        assertThrows(DeleteException.class, () -> index.delete(Lists.newArrayList("documentId")));
    assertThat(e.getResults()).containsExactly(TRANSIENT_ERROR);
  }

  @Test
  public void testPutAsync_nullDocument() throws Exception {
    assertThrows(NullPointerException.class, () -> index.putAsync((Document) null));
  }

  @Test
  public void testPutAsync_nullDocumentBuilder() throws Exception {
    assertThrows(NullPointerException.class, () -> index.putAsync((Document.Builder) null));
  }

  @Test
  public void testPutAsync_ok() throws Exception {
    expectIndexDocumentResponse(statusOK);
    index.putAsync(createDocument("doc1", "subject", "good stuff")).get();
    verify(apiHelper).makeAsyncIndexDocumentCall(any(), any());
  }

  @Test
  public void testPutAsync_okBuilder() throws Exception {
    expectIndexDocumentResponse(statusOK);
    index.putAsync(createDocumentBuilder("doc1", "subject", "good stuff")).get();
    verify(apiHelper).makeAsyncIndexDocumentCall(any(), any());
  }

  @Test
  public void testPut() throws Exception {
    expectIndexDocumentResponse(statusOK);
    PutResponse response = index.put(createDocument("documentId", "name", "value"));
    assertThat(response.getResults().get(0)).isEqualTo(OK);
  }

  @Test
  public void testPutMultipleNoIds() throws Exception {
    expectIndexDocumentResponse(statusOK, statusOK);
    List<Document> documents =
        Lists.newArrayList(
            createDocument(null, "subject", "good stuff"),
            createDocument(null, "subject", "good stuff"));
    PutResponse response = index.put(documents);
    assertThat(response.getResults()).hasSize(2);
    assertThat(response.getResults().get(0)).isEqualTo(OK);
    assertThat(response.getResults().get(1)).isEqualTo(OK);
  }

  @Test
  public void testPut_builder() throws Exception {
    expectIndexDocumentResponse(statusOK);
    PutResponse response = index.put(createDocumentBuilder("documentId", "name", "value"));
    assertThat(response.getResults().get(0)).isEqualTo(OK);
  }

  @Test
  public void testPutAsync_failure() throws Exception {
    expectIndexDocumentResponse();
    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> index.putAsync(createDocument("doc1", "subject", "good stuff")).get());
    PutException putException = (PutException) e.getCause();
    assertThat(putException.getOperationResult().getCode()).isEqualTo(StatusCode.INTERNAL_ERROR);
    assertThat(putException.getResults()).isEmpty();
  }

  @Test
  public void testPut_internalError() throws Exception {
    String message = "This message should be shown to the user";
    RequestStatus status =
        RequestStatus.newBuilder()
            .setCode(ErrorCode.INTERNAL_ERROR)
            .setErrorDetail(message)
            .build();
    expectIndexDocumentResponse(status);
    PutException e =
        assertThrows(
            PutException.class, () -> index.put(createDocument("documentId", "name", "value")));
    OperationResult result = new OperationResult(ErrorCode.INTERNAL_ERROR, message);
    assertThat(e).hasMessageThat().isEqualTo(message);
    assertThat(e.getOperationResult()).isEqualTo(result);
    assertThat(e.getResults()).containsExactly(result);
  }

  @Test
  public void testPutAsync_emptyList() throws Exception {
    List<Document> documents = Lists.newArrayList();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = index.putAsync(documents);
  }

  @Test
  public void testPutAsync_listOk() throws Exception {
    expectIndexDocumentResponse(statusOK);
    List<Document> documents = Lists.newArrayList(createDocument("doc1", "subject", "good stuff"));
    index.putAsync(documents).get();
    verify(apiHelper).makeAsyncIndexDocumentCall(any(), any());
  }

  @Test
  public void testPutAsync_tooMany() throws Exception {
    RequestStatus[] statuses = new RequestStatus[SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST];
    Arrays.fill(statuses, 0, SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, statusOK);
    expectIndexDocumentResponse(statuses);
    List<Document> documents = Lists.newArrayList();
    // Document doc = createDocument("doc1", "subject", "good stuff");
    for (int i = 0; i < SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST; i++) {
      documents.add(createDocument("doc" + i, "subject", "good stuff"));
    }

    // Check that the call works for documents number less than maximum allowed.
    index.putAsync(documents).get();
    documents.add(
        createDocument(
            "doc" + SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, "subject", "good stuff"));

    assertThrows(IllegalArgumentException.class, () -> index.putAsync(documents).get());
  }

  @Test
  public void testPut_repeatedButEqualIsOK() throws Exception {
    RequestStatus[] statuses = new RequestStatus[] {statusOK};
    expectIndexDocumentResponse(statuses);
    List<Document> documents = Lists.newArrayList();
    Document doc = createDocument("doc1", "subject", "good stuff");
    documents.addAll(Collections.nCopies(SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, doc));

    // Check that the call works for documents number less than maximum allowed.
    index.putAsync(documents).get();
    verify(apiHelper).makeAsyncIndexDocumentCall(any(), any());
  }

  @Test
  public void testPut_repeatedEqualAndNoIDsIsOK() throws Exception {
    RequestStatus[] statuses = new RequestStatus[SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST];
    Arrays.fill(statuses, 0, SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, statusOK);
    expectIndexDocumentResponse(statuses);
    List<Document> documents = Lists.newArrayList();
    Document doc = createDocument(null, "subject", "good stuff");
    documents.addAll(Collections.nCopies(SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST, doc));

    // Check that the call works for documents number less than maximum allowed.
    index.putAsync(documents).get();
    verify(apiHelper).makeAsyncIndexDocumentCall(any(), any());
  }

  @Test
  public void testPut_repeatedButDifferentFails() throws Exception {
    List<Document> documents = Lists.newArrayList();
    createDocument("doc1", "subject", "good stuff");
    for (int i = 0; i < SearchApiLimits.PUT_MAXIMUM_DOCS_PER_REQUEST; i++) {
      documents.add(createDocument("doc1", "subject" + i, "good stuff"));
    }
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> index.putAsync(documents).get());
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Put request with documents with the same ID \"doc1\" but different content");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testPut_list() throws Exception {
    expectIndexDocumentResponse(statusOK);
    PutResponse response =
        index.put(Lists.newArrayList(createDocument("documentId", "name", "value")));
    assertThat(response.getResults()).containsExactly(OK);
  }

  @Test
  public void testPutAsync_listFailure() throws Exception {
    expectIndexDocumentResponse(statusTransientError);
    List<Document> documents = Lists.newArrayList(createDocument("doc1", "subject", "good stuff"));
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.putAsync(documents).get());
    PutException putException = (PutException) e.getCause();
    assertThat(putException.getResults()).hasSize(1);
    assertThat(putException.getResults().get(0).getCode())
        .isEqualTo(StatusCode.fromErrorCode(statusTransientError.getCode()));
  }

  @Test
  public void testPutAsync_okAndFailure() throws Exception {
    expectIndexDocumentResponse(statusOK, statusTransientError);
    List<Document> documents =
        Lists.newArrayList(
            createDocument("doc1", "subject", "good stuff"),
            createDocument("doc2", "subject", "this fails to index"));
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.putAsync(documents).get());
    PutException putException = (PutException) e.getCause();
    OperationResult failure = new OperationResult(statusTransientError.getCode(), null);
    assertThat(putException.getOperationResult()).isEqualTo(failure);
    assertThat(putException.getResults()).containsExactly(OK, failure).inOrder();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testPut_listInternalError() throws Exception {
    expectIndexDocumentResponse(statusInternalError);
    List<Document> documents = Lists.newArrayList(createDocument("documentId", "name", "value"));
    PutException e = assertThrows(PutException.class, () -> index.put(documents));
    assertThat(e.getOperationResult()).isEqualTo(INTERNAL_ERROR);
    assertThat(e.getResults()).containsExactly(INTERNAL_ERROR);
  }

  @Test
  public void testSearchAsync() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .addResult(
                SearchServicePb.SearchResult.newBuilder()
                    .setDocument(
                        DocumentPb.Document.newBuilder()
                            .setId("doc1")
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("name")
                                    .setValue(
                                        FieldValue.newBuilder().setStringValue("value").build())
                                    .build())
                            .build())
                    .build())
            .addFacetResult(
                SearchServicePb.FacetResult.newBuilder()
                    .setName("facet")
                    .addValue(
                        SearchServicePb.FacetResultValue.newBuilder()
                            .setName("fvalue1")
                            .setCount(10)
                            .setRefinement(
                                SearchServicePb.FacetRefinement.newBuilder()
                                    .setName("facet")
                                    .setValue("fvalue1"))))
            .setMatchedCount(100L)
            .setStatus(statusOK));

    Results<ScoredDocument> results = index.searchAsync("foo").get();

    assertThat(results.getNumberReturned()).isEqualTo(1);
    assertThat(results.getNumberFound()).isEqualTo(100L);
    ScoredDocument document = results.iterator().next();
    assertThat(document.getId()).isEqualTo("doc1");
    assertThat(document.getFieldNames()).containsExactly("name");
    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("value");
    assertThat(fieldsWithName.hasNext()).isFalse();
    assertThat(results.getResults()).hasSize(1);
    assertThat(results.getFacets()).hasSize(1);
    FacetResult facet = results.getFacets().iterator().next();
    assertThat(facet.getName()).isEqualTo("facet");
    assertThat(facet.getValues()).hasSize(1);
    assertThat(facet.getValues().get(0).getLabel()).isEqualTo("fvalue1");
    assertThat(facet.getValues().get(0).getCount()).isEqualTo(10);
    assertThat(facet.getValues().get(0).getRefinementToken())
        .isEqualTo(FacetRefinement.withValue("facet", "fvalue1").toTokenString());
  }

  @Test
  public void testSearchAsync_nullQueryString() throws Exception {
    assertThrows(NullPointerException.class, () -> index.searchAsync((String) null));
  }

  @Test
  public void testSearchAsync_nullQuery() throws Exception {
    assertThrows(NullPointerException.class, () -> index.searchAsync((Query) null));
  }

  @Test
  public void testSearchAsync_validQuery() throws Exception {
    Query query =
        Query.newBuilder()
            .setOptions(
                QueryOptions.newBuilder()
                    .setLimit(1)
                    .setFieldsToReturn("subject", "body")
                    .setFieldsToSnippet("content")
                    .setSortOptions(
                        SortOptions.newBuilder()
                            .addSortExpression(
                                SortExpression.newBuilder()
                                    .setExpression("subject")
                                    .setDirection(SortExpression.SortDirection.ASCENDING)
                                    .setDefaultValue("ZZZZZZZ")))
                    .setCursor(Cursor.newBuilder().build("false:here"))
                    .build())
            .build("subject:first body:rant");

    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .addResult(
                SearchServicePb.SearchResult.newBuilder()
                    .setDocument(
                        DocumentPb.Document.newBuilder()
                            .setId("doc1")
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("subject")
                                    .setValue(
                                        FieldValue.newBuilder()
                                            .setStringValue("first goodness")
                                            .build())
                                    .build())
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("body")
                                    .setValue(
                                        FieldValue.newBuilder()
                                            .setStringValue("another rant")
                                            .build())
                                    .build())
                            .build())
                    .addExpression(
                        DocumentPb.Field.newBuilder()
                            .setName("content")
                            .setValue(
                                FieldValue.newBuilder()
                                    .setType(FieldValue.ContentType.HTML)
                                    .setStringValue("my <b>first rant</b>")))
                    .build())
            .setMatchedCount(100L)
            .setCursor("more of the same")
            .setStatus(statusOK));

    Results<ScoredDocument> results = index.searchAsync(query).get();

    assertThat(results.getNumberReturned()).isEqualTo(1);
    assertThat(results.getNumberFound()).isEqualTo(100L);
    Cursor cursor = results.getCursor();
    assertThat(cursor.toWebSafeString()).isEqualTo("false:more of the same");
    ScoredDocument result = results.iterator().next();
    assertThat(result.getSortScores()).isEmpty();
    assertThat(result.getCursor()).isNull();
    assertThat(result.getId()).isEqualTo("doc1");
    Iterator<Field> fieldsWithName = result.getFields("subject").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("first goodness");
    assertThat(fieldsWithName.hasNext()).isFalse();
    fieldsWithName = result.getFields("body").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("another rant");
    assertThat(fieldsWithName.hasNext()).isFalse();
    assertThat(result.getExpressions()).hasSize(1);
    assertThat(result.getExpressions().get(0).getName()).isEqualTo("content");
    assertThat(result.getExpressions().get(0).getHTML()).isEqualTo("my <b>first rant</b>");
  }

  @Test
  public void testSearchAsync_defaultCursor() throws Exception {
    Query query =
        Query.newBuilder()
            .setOptions(
                QueryOptions.newBuilder()
                    .setLimit(1)
                    .setCursor(Cursor.newBuilder().build())
                    .build())
            .build("query");

    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .addResult(
                SearchServicePb.SearchResult.newBuilder()
                    .setDocument(
                        DocumentPb.Document.newBuilder()
                            .setId("doc1")
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("subject")
                                    .setValue(
                                        FieldValue.newBuilder().setStringValue("query").build())
                                    .build())
                            .build())
                    .build())
            .setMatchedCount(100L)
            .setCursor("first cursor back")
            .setStatus(statusOK));

    Results<ScoredDocument> results = index.searchAsync(query).get();

    assertThat(results.getNumberReturned()).isEqualTo(1);
    assertThat(results.getNumberFound()).isEqualTo(100L);
    Cursor cursor = results.getCursor();
    assertThat(cursor.toWebSafeString()).isEqualTo("false:first cursor back");
    ScoredDocument result = results.iterator().next();
    assertThat(result.getSortScores()).isEmpty();
    assertThat(result.getCursor()).isNull();
    assertThat(result.getId()).isEqualTo("doc1");
    Iterator<Field> fieldsWithName = result.getFields("subject").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("query");
    assertThat(fieldsWithName.hasNext()).isFalse();
    assertThat(result.getExpressions()).isEmpty();
  }

  @Test
  public void testSearchAsync_validQueryPerResultCursor() throws Exception {
    Query query =
        Query.newBuilder()
            .setOptions(
                QueryOptions.newBuilder()
                    .setLimit(1)
                    .setFieldsToReturn("subject", "body")
                    .setFieldsToSnippet("content")
                    .setSortOptions(
                        SortOptions.newBuilder()
                            .addSortExpression(
                                SortExpression.newBuilder()
                                    .setExpression("subject")
                                    .setDirection(SortExpression.SortDirection.ASCENDING)
                                    .setDefaultValue("ZZZZZZZ")))
                    .setCursor(Cursor.newBuilder().build("true:here"))
                    .build())
            .build("subject:first body:rant");

    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .addResult(
                SearchServicePb.SearchResult.newBuilder()
                    .setDocument(
                        DocumentPb.Document.newBuilder()
                            .setId("doc1")
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("subject")
                                    .setValue(
                                        FieldValue.newBuilder()
                                            .setStringValue("first goodness")
                                            .build())
                                    .build())
                            .addField(
                                DocumentPb.Field.newBuilder()
                                    .setName("body")
                                    .setValue(
                                        FieldValue.newBuilder()
                                            .setStringValue("another rant")
                                            .build())
                                    .build())
                            .build())
                    .addExpression(
                        DocumentPb.Field.newBuilder()
                            .setName("content")
                            .setValue(
                                FieldValue.newBuilder()
                                    .setType(FieldValue.ContentType.HTML)
                                    .setStringValue("my <b>first rant</b>")))
                    .setCursor("more of the same")
                    .build())
            .setMatchedCount(100L)
            .setStatus(statusOK));

    Results<ScoredDocument> results = index.searchAsync(query).get();

    assertThat(results.getNumberReturned()).isEqualTo(1);
    assertThat(results.getNumberFound()).isEqualTo(100L);
    assertThat(results.getCursor()).isNull();
    ScoredDocument result = results.iterator().next();
    assertThat(result.getSortScores()).isEmpty();
    assertThat(result.getCursor().toWebSafeString()).isEqualTo("true:more of the same");
    assertThat(result.getId()).isEqualTo("doc1");
    Iterator<Field> fieldsWithName = result.getFields("subject").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("first goodness");
    assertThat(fieldsWithName.hasNext()).isFalse();
    fieldsWithName = result.getFields("body").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("another rant");
    assertThat(fieldsWithName.hasNext()).isFalse();
    assertThat(result.getExpressions()).hasSize(1);
    assertThat(result.getExpressions().get(0).getName()).isEqualTo("content");
    assertThat(result.getExpressions().get(0).getHTML()).isEqualTo("my <b>first rant</b>");
  }

  @Test
  public void testSearchAsync_failure() throws Exception {
    String message = "This message should be shown to the user";
    RequestStatus status =
        RequestStatus.newBuilder()
            .setCode(ErrorCode.TRANSIENT_ERROR)
            .setErrorDetail(message)
            .build();
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder().setMatchedCount(0L).setStatus(status));
    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.searchAsync("foo").get());
    OperationResult result = new OperationResult(ErrorCode.TRANSIENT_ERROR, message);
    SearchException searchException = (SearchException) e.getCause();
    assertThat(searchException.getOperationResult().getMessage()).isEqualTo(message);
    assertThat(searchException.getOperationResult()).isEqualTo(result);
  }

  @Test
  public void testSearch_query() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder().setMatchedCount(0L).setStatus(statusOK));

    Results<ScoredDocument> resultsReturned = index.search("query");
    assertThat(resultsReturned.getResults()).isEmpty();
    assertThat(resultsReturned.getNumberFound()).isEqualTo(0L);
    assertThat(resultsReturned.getNumberReturned()).isEqualTo(0);
    assertThat(resultsReturned.getCursor()).isNull();
  }

  @Test
  public void testSearch_invalidQuery() throws Exception {
    assertThrows(SearchQueryException.class, () -> index.search("this:should:not:parse"));
  }

  @Test
  public void testSearch_queryWithCursor() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .setMatchedCount(0L)
            .setStatus(statusOK)
            .setCursor("cursor"));

    Results<ScoredDocument> resultsReturned = index.search("query");
    assertThat(resultsReturned.getResults()).isEmpty();
    assertThat(resultsReturned.getNumberFound()).isEqualTo(0L);
    assertThat(resultsReturned.getNumberReturned()).isEqualTo(0);
    Cursor cursor = resultsReturned.getCursor();
    assertThat(cursor.toWebSafeString()).isEqualTo("false:cursor");
    assertThat(cursor.isPerResult()).isFalse();
  }

  @Test
  public void testSearch_queryInterruptedException() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .setMatchedCount(0L)
            .setStatus(statusInternalError));
    SearchException e = assertThrows(SearchException.class, () -> index.search("query"));
    assertThat(e.getOperationResult()).isEqualTo(INTERNAL_ERROR);
  }

  @Test
  public void testSearch_queryBuilder() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder().setMatchedCount(0L).setStatus(statusOK));
    Results<ScoredDocument> resultsReturned =
        index.search(
            Query.newBuilder()
                .setOptions(QueryOptions.newBuilder().setLimit(1).build())
                .build("subject:first body:rant"));

    assertThat(resultsReturned.getResults()).isEmpty();
    assertThat(resultsReturned.getNumberFound()).isEqualTo(0L);
    assertThat(resultsReturned.getNumberReturned()).isEqualTo(0);
  }

  @Test
  public void testSearch_queryBuilderInterruptedException() throws Exception {
    expectSearchResponse(
        SearchServicePb.SearchResponse.newBuilder()
            .setMatchedCount(0L)
            .setStatus(statusInternalError));
    Query query =
        Query.newBuilder()
            .setOptions(QueryOptions.newBuilder().setLimit(1).build())
            .build("subject:first body:rant");
    SearchException e = assertThrows(SearchException.class, () -> index.search(query));
    assertThat(e.getOperationResult()).isEqualTo(INTERNAL_ERROR);
  }

  @Test
  public void testListDocumentsAsync_okGetRequest() throws Exception {
    expectListDocumentsResponse(
        SearchServicePb.ListDocumentsResponse.newBuilder()
            .addDocument(
                DocumentPb.Document.newBuilder()
                    .setId("doc1")
                    .addField(
                        DocumentPb.Field.newBuilder()
                            .setName("name")
                            .setValue(FieldValue.newBuilder().setStringValue("value").build())
                            .build())
                    .build())
            .setStatus(statusOK));

    GetRequest request = GetRequest.newBuilder().setLimit(1).build();

    GetResponse<Document> response = index.getRangeAsync(request).get();

    assertThat(response.getResults()).hasSize(1);
    Document document = response.getResults().get(0);
    assertThat(document.getId()).isEqualTo("doc1");
    assertThat(document.getFieldNames()).containsExactly("name");
    Iterator<Field> fieldsWithName = document.getFields("name").iterator();
    assertThat(fieldsWithName.next().getText()).isEqualTo("value");
    assertThat(fieldsWithName.hasNext()).isFalse();
  }

  @Test
  public void testListDocumentsAsync_nullGetRequest() throws Exception {
    assertThrows(NullPointerException.class, () -> index.getRangeAsync((GetRequest) null));
  }

  @Test
  public void testGetRangeAsync_failureGetRequestDeprecated() throws Exception {
    expectListDocumentsResponse(
        SearchServicePb.ListDocumentsResponse.newBuilder().setStatus(statusTransientError));

    GetRequest request = GetRequest.newBuilder().setLimit(1).build();

    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.getRangeAsync(request).get());
    GetException listException = (GetException) e.getCause();
    assertThat(listException.getOperationResult()).isEqualTo(TRANSIENT_ERROR);
  }

  @Test
  public void testGetRangeAsync_failureGetRequest() throws Exception {
    expectListDocumentsResponse(
        SearchServicePb.ListDocumentsResponse.newBuilder().setStatus(statusTransientError));

    GetRequest request = GetRequest.newBuilder().setLimit(1).build();

    ExecutionException e =
        assertThrows(ExecutionException.class, () -> index.getRangeAsync(request).get());
    GetException getException = (GetException) e.getCause();
    assertThat(getException.getOperationResult()).isEqualTo(TRANSIENT_ERROR);
  }

  @Test
  public void testGetRange_okGetRequest() throws Exception {
    expectListDocumentsResponse(
        SearchServicePb.ListDocumentsResponse.newBuilder().setStatus(statusOK));

    GetRequest request = GetRequest.newBuilder().setLimit(50).build();

    GetResponse<Document> response = index.getRange(request);
    assertThat(response.getResults()).isEmpty();
  }

  @Test
  public void testEquals() throws Exception {
    SearchService searchService = SearchServiceFactory.getSearchService();
    Index a = searchService.getIndex(IndexSpec.newBuilder().setName("name").build());
    Index b = searchService.getIndex(IndexSpec.newBuilder().setName("name").build());
    assertThat(b).isEqualTo(a);
    assertThat(b.hashCode()).isEqualTo(a.hashCode());
  }

  @Test
  public void testGet() throws Exception {
    Document document =
        Document.newBuilder()
            .setId("doc_id")
            .addField(Field.newBuilder().setName("name").setText("value"))
            .build();

    expectListDocumentsResponse(
        SearchServicePb.ListDocumentsResponse.newBuilder()
            .addDocument(
                DocumentPb.Document.newBuilder()
                    .setId("doc_id")
                    .addField(
                        DocumentPb.Field.newBuilder()
                            .setName("name")
                            .setValue(FieldValue.newBuilder().setStringValue("value").build())
                            .build())
                    .build())
            .setStatus(statusOK));

    Document documentReturned = index.get("doc_id");

    assertThat(documentReturned).isEqualTo(document);
  }
}
