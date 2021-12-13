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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.IndexMetadata;
import com.google.appengine.api.search.proto.SearchServicePb.ListIndexesResponse;
import com.google.appengine.api.search.proto.SearchServicePb.RequestStatus;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SearchServiceImpl} which creates Index objects which are the interface to the
 * search service.
 */
@RunWith(JUnit4.class)
public class SearchServiceImplTest {
  private static final String NAMESPACE = "ns";
  public static final Double NO_DEADLINE = null;
  public static final Double TEST_DEADLINE = 2.0;
  private SearchApiHelper apiHelper;
  private RequestStatus statusOK;
  private RequestStatus statusFailure;
  private ApiProxy.Environment environment;
  private GetIndexesRequest getIndexesRequest;
  private SearchService searchService;
  private SearchService searchServiceDefaultNs;

  @Before
  public void setUp() throws Exception {
    environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);
    apiHelper = mock(SearchApiHelper.class);
    searchService =
        new SearchServiceImpl(
            apiHelper, SearchServiceConfig.newBuilder().setNamespace(NAMESPACE).build());
    searchServiceDefaultNs =
        new SearchServiceImpl(apiHelper, SearchServiceConfig.newBuilder().setNamespace("").build());
    statusOK = RequestStatus.newBuilder().setCode(ErrorCode.OK).build();
    statusFailure = RequestStatus.newBuilder().setCode(ErrorCode.TRANSIENT_ERROR).build();
    getIndexesRequest = GetIndexesRequest.newBuilder().build();
  }

  private void expectAsyncListIndexesCall(
      SearchServicePb.ListIndexesParams expectedParams,
      Double deadline,
      ListIndexesResponse.Builder responseBuilder) {
    Future<SearchServicePb.ListIndexesResponse.Builder> response =
        new FutureHelper.FakeFuture<>(responseBuilder);
    when(apiHelper.makeAsyncListIndexesCall(eq(expectedParams), same(deadline)))
        .thenReturn(response);
  }

  private static SearchServicePb.IndexSpec newIndexSpec(String name, String namespace) {
    return SearchServicePb.IndexSpec.newBuilder().setName(name).setNamespace(namespace).build();
  }

  private static String getNamespace() {
    return NamespaceManager.get() == null ? "" : NamespaceManager.get();
  }

  private static SearchServicePb.IndexMetadata newIndexMetadata(String name) {
    return newIndexMetadata(name, getNamespace());
  }

  private static SearchServicePb.IndexMetadata newIndexMetadata(String name, String namespace) {
    return SearchServicePb.IndexMetadata.newBuilder()
        .setIndexSpec(newIndexSpec(name, namespace))
        .build();
  }

  @Test
  public void testIndex_validNameWithNamespace() throws Exception {
    NamespaceManager.set("ns2");
    SearchService search = SearchServiceFactory.getSearchService();
    assertThat(search.getNamespace()).isEqualTo("ns2");
  }

  @Test
  public void testUserGivenNamespaceIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams params =
        request.copyToProtocolBuffer().setNamespace(searchService.getNamespace()).build();
    expectAsyncListIndexesCall(
        params, NO_DEADLINE, SearchServicePb.ListIndexesResponse.newBuilder().setStatus(statusOK));
    searchService.getIndexes(request);
    verify(apiHelper).makeAsyncListIndexesCall(any(), any());
  }

  @Test
  public void testUserGivenDeadlineIsPassedThrough() {
    SearchService serviceServiceWithDeadline =
        new SearchServiceImpl(
            apiHelper,
            SearchServiceConfig.newBuilder()
                .setNamespace(NAMESPACE)
                .setDeadline(TEST_DEADLINE)
                .build());
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams params =
        request.copyToProtocolBuffer().setNamespace(NAMESPACE).build();
    expectAsyncListIndexesCall(
        params,
        TEST_DEADLINE,
        SearchServicePb.ListIndexesResponse.newBuilder().setStatus(statusOK));
    serviceServiceWithDeadline.getIndexes(request);
    verify(apiHelper).makeAsyncListIndexesCall(any(), any());
  }

  @Test
  public void testDefaultNamespaceIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams params =
        request.copyToProtocolBuffer().setNamespace(searchServiceDefaultNs.getNamespace()).build();
    expectAsyncListIndexesCall(
        params, NO_DEADLINE, SearchServicePb.ListIndexesResponse.newBuilder().setStatus(statusOK));
    searchServiceDefaultNs.getIndexes(request);
    verify(apiHelper).makeAsyncListIndexesCall(any(), any());
  }

  @Test
  public void testGetIndexes() throws Exception {
    List<Index> indexList =
        ImmutableList.of(
            searchService.getIndex(IndexSpec.newBuilder().setName("index1").build()),
            searchService.getIndex(IndexSpec.newBuilder().setName("index2").build()));

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(newIndexMetadata("index1", searchService.getNamespace()))
            .addIndexMetadata(newIndexMetadata("index2", searchService.getNamespace()))
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        getIndexesRequest.copyToProtocolBuffer().setNamespace(NAMESPACE).build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);

    assertThat(searchService.getIndexes(getIndexesRequest).getResults()).isEqualTo(indexList);
  }

  @Test
  public void testGetIndexes_setSchemaFetched() throws Exception {
    Schema.Builder schemaBuilder = Schema.newBuilder();
    for (Field.FieldType fieldType : Field.FieldType.values()) {
      schemaBuilder.addTypedField(fieldType.toString(), fieldType);
    }

    IndexSpec indexSpec = IndexSpec.newBuilder().setName("index1").build();

    Index index =
        new IndexImpl(
            apiHelper,
            SearchServiceConfig.newBuilder().setNamespace(NAMESPACE).build(),
            indexSpec,
            schemaBuilder.build(),
            null,
            null);
    List<Index> indexList = ImmutableList.of(index);

    SearchServicePb.IndexMetadata.Builder metadataBuilder =
        SearchServicePb.IndexMetadata.newBuilder().setIndexSpec(newIndexSpec("index1", NAMESPACE));
    for (Field.FieldType fieldType : Field.FieldType.values()) {
      DocumentPb.FieldTypes fieldTypePb =
          DocumentPb.FieldTypes.newBuilder()
              .setName(fieldType.toString())
              .addType(Schema.mapPublicFieldTypeToPB(fieldType))
              .build();
      metadataBuilder.addField(fieldTypePb);
    }

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(metadataBuilder.build())
            .setStatus(statusOK);
    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setFetchSchema(true)
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    assertThat(
            searchService
                .getIndexes(GetIndexesRequest.newBuilder().setSchemaFetched(true))
                .getResults())
        .isEqualTo(indexList);
  }

  @Test
  public void testGetIndexes_deletedSchema() throws Exception {
    IndexSpec indexSpec = IndexSpec.newBuilder().setName("index1").build();

    Index index =
        new IndexImpl(
            apiHelper,
            SearchServiceConfig.newBuilder().setNamespace(NAMESPACE).build(),
            indexSpec,
            Schema.newBuilder().build(),
            null,
            null);
    List<Index> indexList = ImmutableList.of(index);

    IndexMetadata metadata =
        SearchServicePb.IndexMetadata.newBuilder()
            .setIndexSpec(newIndexSpec("index1", NAMESPACE))
            .build();

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder().addIndexMetadata(metadata).setStatus(statusOK);
    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setFetchSchema(true)
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    List<Index> results =
        searchService
            .getIndexes(GetIndexesRequest.newBuilder().setSchemaFetched(true))
            .getResults();
    assertThat(results).isEqualTo(indexList);
    assertThat(results.get(0).getSchema().getFieldNames()).isEmpty();
  }

  @Test
  public void testGetIndexes_storageInfo() throws Exception {
    // Prepare the expected result
    IndexSpec indexSpec = IndexSpec.newBuilder().setName("index1").build();

    Index index =
        new IndexImpl(
            apiHelper,
            SearchServiceConfig.newBuilder().setNamespace(NAMESPACE).build(),
            indexSpec,
            null,
            1732L,
            1000000000L);
    List<Index> indexList = ImmutableList.of(index);

    // Prepare the mocked actual RPC response
    SearchServicePb.IndexMetadata.Builder metadataBuilder =
        SearchServicePb.IndexMetadata.newBuilder().setIndexSpec(newIndexSpec("index1", NAMESPACE));
    metadataBuilder.setStorage(
        SearchServicePb.IndexMetadata.Storage.newBuilder()
            .setAmountUsed(1732L)
            .setLimit(1000000000L));

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(metadataBuilder.build())
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);

    assertThat(searchService.getIndexes(GetIndexesRequest.newBuilder()).getResults())
        .isEqualTo(indexList);
  }

  @Test
  public void testGetIndexes_missingStorageInfo() throws Exception {
    // Prepare the mocked actual RPC response
    SearchServicePb.IndexMetadata.Builder metadataBuilder =
        SearchServicePb.IndexMetadata.newBuilder().setIndexSpec(newIndexSpec("index1", NAMESPACE));

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(metadataBuilder.build())
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);

    List<Index> indexList = searchService.getIndexes(GetIndexesRequest.newBuilder()).getResults();
    assertThat(indexList).hasSize(1);
    Index index = indexList.get(0);
    assertThrows(UnsupportedOperationException.class, index::getStorageUsage);
  }

  @Test
  public void testGetIndexes_invalidResponse() throws Exception {
    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(newIndexMetadata("index1", NAMESPACE))
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace("")
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);

    assertThrows(
        IllegalArgumentException.class,
        () -> searchServiceDefaultNs.getIndexes(getIndexesRequest).getResults());
  }

  @Test
  public void testGetIndexes_failed() throws Exception {
    SearchServicePb.IndexMetadata metadata = newIndexMetadata("index1");
    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder().addIndexMetadata(metadata).setStatus(statusFailure);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    GetException exception =
        assertThrows(GetException.class, () -> searchService.getIndexes(getIndexesRequest));
    assertThat(exception.getOperationResult().getCode()).isEqualTo(StatusCode.TRANSIENT_ERROR);
  }

  @Test
  public void testGetIndexes_requestInterruptedException() throws Exception {
    when(apiHelper.makeAsyncListIndexesCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(new FutureTestHelper.InterruptedFuture<ListIndexesResponse.Builder>(null));
    assertThrows(SearchServiceException.class, () -> searchService.getIndexes(getIndexesRequest));
  }

  @Test
  public void testGetIndexes_requestExecutionException() throws Exception {
    when(apiHelper.makeAsyncListIndexesCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(
            new FutureTestHelper.ExecutionExceptionFuture<ListIndexesResponse.Builder>(null));
    assertThrows(SearchServiceException.class, () -> searchService.getIndexes(getIndexesRequest));
  }

  @Test
  public void testGetIndexesAsync() throws Exception {
    List<Index> indexList =
        ImmutableList.of(
            searchService.getIndex(IndexSpec.newBuilder().setName("index1").build()),
            searchService.getIndex(IndexSpec.newBuilder().setName("index2").build()));

    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(newIndexMetadata("index1", searchService.getNamespace()))
            .addIndexMetadata(newIndexMetadata("index2", searchService.getNamespace()))
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    assertThat(searchService.getIndexesAsync(getIndexesRequest).get().getResults())
        .isEqualTo(indexList);
  }

  @Test
  public void testGetIndexesAsync_invalidResponse() throws Exception {
    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder()
            .addIndexMetadata(newIndexMetadata("index1", searchService.getNamespace()))
            .setStatus(statusOK);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace("")
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    ExecutionException e =
        assertThrows(
            "Default namespace manager should not return indexes with namespace "
                + searchService.getNamespace(),
            ExecutionException.class,
            () -> searchServiceDefaultNs.getIndexesAsync(getIndexesRequest).get().getResults());
    assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testGetIndexesAsync_failed() throws Exception {
    SearchServicePb.IndexMetadata metadata = newIndexMetadata("index1");
    ListIndexesResponse.Builder listIndexesResp =
        ListIndexesResponse.newBuilder().addIndexMetadata(metadata).setStatus(statusFailure);

    SearchServicePb.ListIndexesParams params =
        SearchServicePb.ListIndexesParams.newBuilder()
            .setNamespace(NAMESPACE)
            .setLimit(SearchApiLimits.GET_INDEXES_DEFAULT_LIMIT)
            .build();
    expectAsyncListIndexesCall(params, NO_DEADLINE, listIndexesResp);
    ExecutionException exception =
        assertThrows(
            ExecutionException.class, () -> searchService.getIndexesAsync(getIndexesRequest).get());
    GetException getIndexesException = (GetException) exception.getCause();
    assertThat(getIndexesException.getOperationResult().getCode())
        .isEqualTo(StatusCode.TRANSIENT_ERROR);
  }

  @Test
  public void testGetIndexesAsync_requestInterruptedException() throws Exception {
    when(apiHelper.makeAsyncListIndexesCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(new FutureTestHelper.InterruptedFuture<ListIndexesResponse.Builder>(null));
    assertThrows(
        InterruptedException.class, () -> searchService.getIndexesAsync(getIndexesRequest).get());
  }

  @Test
  public void testGetIndexes_userGivenNamespaceIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams params =
        request.copyToProtocolBuffer().setNamespace(searchService.getNamespace()).build();
    expectAsyncListIndexesCall(
        params, NO_DEADLINE, SearchServicePb.ListIndexesResponse.newBuilder().setStatus(statusOK));
    searchService.getIndexes(request);
    verify(apiHelper).makeAsyncListIndexesCall(any(), any());
  }

  @Test
  public void testGetIndexes_defaultNamespaceIsCopied() {
    GetIndexesRequest request = GetIndexesRequest.newBuilder().build();
    SearchServicePb.ListIndexesParams params =
        request.copyToProtocolBuffer().setNamespace(searchServiceDefaultNs.getNamespace()).build();
    expectAsyncListIndexesCall(
        params, NO_DEADLINE, SearchServicePb.ListIndexesResponse.newBuilder().setStatus(statusOK));
    searchServiceDefaultNs.getIndexes(request);
    verify(apiHelper).makeAsyncListIndexesCall(any(), any());
  }

  @Test
  public void testGetIndexesAsync_requestExecutionException() throws Exception {
    when(apiHelper.makeAsyncListIndexesCall(notNull(), same(NO_DEADLINE)))
        .thenReturn(
            new FutureTestHelper.ExecutionExceptionFuture<ListIndexesResponse.Builder>(null));
    ExecutionException exception =
        assertThrows(
            ExecutionException.class, () -> searchService.getIndexesAsync(getIndexesRequest).get());
    assertThat(exception).hasCauseThat().isInstanceOf(SearchServiceException.class);
  }
}
