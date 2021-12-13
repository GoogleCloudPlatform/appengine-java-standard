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
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteDocumentRequest;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteDocumentResponse;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteSchemaRequest;
import com.google.appengine.api.search.proto.SearchServicePb.DeleteSchemaResponse;
import com.google.appengine.api.search.proto.SearchServicePb.IndexDocumentRequest;
import com.google.appengine.api.search.proto.SearchServicePb.IndexDocumentResponse;
import com.google.appengine.api.search.proto.SearchServicePb.ListDocumentsRequest;
import com.google.appengine.api.search.proto.SearchServicePb.ListDocumentsResponse;
import com.google.appengine.api.search.proto.SearchServicePb.ListIndexesRequest;
import com.google.appengine.api.search.proto.SearchServicePb.ListIndexesResponse;
import com.google.appengine.api.search.proto.SearchServicePb.RequestStatus;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link AdminSearchServiceFactory}. */
@RunWith(JUnit4.class)
public class AdminSearchServiceFactoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String APP_ID = "my-app";
  private static final String OTHER_APP_ID = "other-app";
  private static final String OTHER_NAMESPACE = "other-namespace";
  private static final String OTHER_INDEX = "other-index";
  private static final SearchServicePb.IndexSpec OTHER_INDEX_SPEC =
      SearchServicePb.IndexSpec.newBuilder()
          .setName(OTHER_INDEX)
          .setNamespace(OTHER_NAMESPACE)
          .build();

  private RequestStatus statusOK;
  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  private ApiProxy.Environment env;
  private AdminSearchServiceFactory factory;

  @Before
  public void setUp() throws Exception {
    statusOK = RequestStatus.newBuilder().setCode(ErrorCode.OK).build();
    env = new MockEnvironment(APP_ID, "v1");
    ApiProxy.setEnvironmentForCurrentThread(env);
    factory = new AdminSearchServiceFactory();
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test
  public void testNamespaceManagerIgnored() throws Exception {
    NamespaceManager.set("hzvjqtrkevwv");
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      assertThat(service.getNamespace()).isEqualTo(OTHER_NAMESPACE);
    } finally {
      NamespaceManager.set(null);
    }
  }

  @Test
  public void testInvalidAppId() throws Exception {
    SearchServiceConfig config = SearchServiceConfig.newBuilder().setNamespace("").build();
    assertThrows(IllegalArgumentException.class, () -> factory.getSearchService(null, config));
  }

  @Test
  public void testInvalidSearchServiceConfig() throws Exception {
    SearchServiceConfig config = SearchServiceConfig.newBuilder().build();
    assertThrows(
        IllegalArgumentException.class, () -> factory.getSearchService(OTHER_APP_ID, config));
  }

  @Test
  public void testIndexDocumentAppIdOverride() throws Exception {
    ApiProxy.setDelegate(delegate);
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      IndexDocumentResponse response =
          IndexDocumentResponse.newBuilder().addStatus(statusOK).build();
      when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
          .thenReturn(Futures.immediateFuture(response.toByteArray()));
      Index index = service.getIndex(IndexSpec.newBuilder().setName(OTHER_INDEX).build());
      index.put(
          Document.newBuilder()
              .addField(Field.newBuilder().setName("color").setAtom("blue").build())
              .build());
      ArgumentCaptor<byte[]> actualRequestBytes = ArgumentCaptor.forClass(byte[].class);
      verify(delegate)
          .makeAsyncCall(
              same(env), eq("search"), eq("IndexDocument"), actualRequestBytes.capture(), any());
      IndexDocumentRequest actualRequest =
          IndexDocumentRequest.parseFrom(
              actualRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      assertThat(actualRequest.getAppId()).isEqualTo(ByteString.copyFromUtf8(OTHER_APP_ID));
      assertThat(actualRequest.getParams().getIndexSpec()).isEqualTo(OTHER_INDEX_SPEC);
    } finally {
      ApiProxy.setDelegate(null);
    }
  }

  @Test
  public void testDeleteDocumentAppIdOverride() throws Exception {
    ApiProxy.setDelegate(delegate);
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      DeleteDocumentResponse response =
          DeleteDocumentResponse.newBuilder().addStatus(statusOK).build();
      when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
          .thenReturn(Futures.immediateFuture(response.toByteArray()));
      Index index = service.getIndex(IndexSpec.newBuilder().setName(OTHER_INDEX).build());
      index.delete("foo");
      ArgumentCaptor<byte[]> actualRequestBytes = ArgumentCaptor.forClass(byte[].class);
      verify(delegate)
          .makeAsyncCall(
              same(env), eq("search"), eq("DeleteDocument"), actualRequestBytes.capture(), any());
      DeleteDocumentRequest actualRequest =
          DeleteDocumentRequest.parseFrom(
              actualRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      assertThat(actualRequest.getAppId()).isEqualTo(ByteString.copyFromUtf8(OTHER_APP_ID));
      assertThat(actualRequest.getParams().getIndexSpec()).isEqualTo(OTHER_INDEX_SPEC);
    } finally {
      ApiProxy.setDelegate(null);
    }
  }

  @Test
  public void testListDocumentsAppIdOverride() throws Exception {
    ApiProxy.setDelegate(delegate);
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      ListDocumentsResponse response =
          ListDocumentsResponse.newBuilder().setStatus(statusOK).build();
      when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
          .thenReturn(Futures.immediateFuture(response.toByteArray()));
      Index index = service.getIndex(IndexSpec.newBuilder().setName(OTHER_INDEX).build());
      index.getRange(GetRequest.newBuilder().build());
      ArgumentCaptor<byte[]> actualRequestBytes = ArgumentCaptor.forClass(byte[].class);
      verify(delegate)
          .makeAsyncCall(
              same(env), eq("search"), eq("ListDocuments"), actualRequestBytes.capture(), any());
      ListDocumentsRequest actualRequest =
          ListDocumentsRequest.parseFrom(
              actualRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      assertThat(actualRequest.getAppId()).isEqualTo(ByteString.copyFromUtf8(OTHER_APP_ID));
      assertThat(actualRequest.getParams().getIndexSpec()).isEqualTo(OTHER_INDEX_SPEC);
    } finally {
      ApiProxy.setDelegate(null);
    }
  }

  @Test
  public void testListIndexesAppIdOverride() throws Exception {
    ApiProxy.setDelegate(delegate);
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      ListIndexesResponse response = ListIndexesResponse.newBuilder().setStatus(statusOK).build();
      when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
          .thenReturn(Futures.immediateFuture(response.toByteArray()));
      service.getIndexes(GetIndexesRequest.newBuilder().build());
      ArgumentCaptor<byte[]> actualRequestBytes = ArgumentCaptor.forClass(byte[].class);
      verify(delegate)
          .makeAsyncCall(
              same(env), eq("search"), eq("ListIndexes"), actualRequestBytes.capture(), any());
      ListIndexesRequest actualRequest =
          ListIndexesRequest.parseFrom(
              actualRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      assertThat(actualRequest.getAppId()).isEqualTo(ByteString.copyFromUtf8(OTHER_APP_ID));
    } finally {
      ApiProxy.setDelegate(null);
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeleteSchemaAppIdOverride() throws Exception {
    ApiProxy.setDelegate(delegate);
    try {
      SearchServiceConfig config =
          SearchServiceConfig.newBuilder().setNamespace(OTHER_NAMESPACE).build();
      SearchService service = factory.getSearchService(OTHER_APP_ID, config);
      DeleteSchemaResponse response = DeleteSchemaResponse.newBuilder().addStatus(statusOK).build();
      when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
          .thenReturn(Futures.immediateFuture(response.toByteArray()));
      Index index = service.getIndex(IndexSpec.newBuilder().setName(OTHER_INDEX).build());
      index.deleteSchema();
      ArgumentCaptor<byte[]> actualRequestBytes = ArgumentCaptor.forClass(byte[].class);
      verify(delegate)
          .makeAsyncCall(
              same(env), eq("search"), eq("DeleteSchema"), actualRequestBytes.capture(), any());
      DeleteSchemaRequest actualRequest =
          DeleteSchemaRequest.parseFrom(
              actualRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      assertThat(actualRequest.getAppId()).isEqualTo(ByteString.copyFromUtf8(OTHER_APP_ID));
      assertThat(actualRequest.getParams().getIndexSpecList()).containsExactly(OTHER_INDEX_SPEC);
    } finally {
      ApiProxy.setDelegate(null);
    }
  }
}
