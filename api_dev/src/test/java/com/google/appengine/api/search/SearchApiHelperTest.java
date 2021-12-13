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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.appengine.api.search.SearchApiHelper.IndexExceptionConverter;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ArgumentException;
import com.google.apphosting.api.ApiProxy.OverQuotaException;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class SearchApiHelperTest {

  private static final String APP_ID = "my-app";
  private static final String INDEX_NAME = "distinctiveIndexName";

  /** Parameters for IndexDocument call */
  private static SearchServicePb.IndexDocumentParams.Builder paramsBuilder(String namespace) {
    SearchServicePb.IndexDocumentParams.Builder builder =
        SearchServicePb.IndexDocumentParams.newBuilder();
    builder.getIndexSpecBuilder().setName(INDEX_NAME);
    if (!Strings.isNullOrEmpty(namespace)) {
      builder.getIndexSpecBuilder().setNamespace(namespace);
    }
    return builder;
  }

  private static IndexExceptionConverter converter(String namespace) {
    return new IndexExceptionConverter(
        SearchServicePb.IndexDocumentRequest.newBuilder()
            .setParams(paramsBuilder(namespace))
            .build());
  }

  /** Sets environment for ApiProxy */
  @Before
  public void setUp() {
    ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment(APP_ID, "v1"));
  }

  @After
  public void tearDown() {
    ApiProxy.clearEnvironmentForCurrentThread();
    ApiProxy.setDelegate(null);
  }

  @Test
  public void exceptionConverter_noop() throws Exception {
    Throwable t = new IllegalArgumentException("foo");
    assertThat(t).isSameInstanceAs(converter("namespace").apply(t));
  }

  @Test
  public void exceptionConverter_OverQuota() throws Exception {
    OverQuotaException input = new OverQuotaException("package", "method");
    assertRewrittenOverQuotaException(input, converter("").apply(input), "");
  }

  @Test
  public void exceptionConverter_OverQuota_namespace() throws Exception {
    OverQuotaException input = new OverQuotaException("package", "method");
    assertRewrittenOverQuotaException(input, converter("namesp").apply(input), "namesp");
  }

  /**
   * If SearchApiHelper#makeAsyncIndexDocumentCall hits a non-quota exception, it is not rewritten.
   */
  @Test
  public void indexDocument_noop() throws Exception {
    ArgumentException exceptionFromProxy = new ArgumentException("package", "method");
    Throwable throwableFromHelper = indexDocument(exceptionFromProxy);
    assertThat(throwableFromHelper).isSameInstanceAs(exceptionFromProxy);
  }

  /** If SearchApiHelper#makeAsyncIndexDocumentCall hits an OverQuotaException, it is rewritten. */
  @Test
  public void indexDocument_OverQuota() throws Exception {
    OverQuotaException exceptionFromProxy = new OverQuotaException("package", "method");
    assertRewrittenOverQuotaException(exceptionFromProxy, indexDocument(exceptionFromProxy), "");
  }

  /** Asserts that the output is a new instance of OverQuotaException with an extended message. */
  private static void assertRewrittenOverQuotaException(
      OverQuotaException input, Throwable output, String namespace) {
    // Output is a new OverQuotaException
    assertThat(output).isNotSameInstanceAs(input);
    assertThat(output).isInstanceOf(OverQuotaException.class);
    // Message contains the original message plus namespace and index name
    assertThat(output).hasMessageThat().contains(input.getMessage());
    if (!Strings.isNullOrEmpty(namespace)) {
      assertThat(output).hasMessageThat().contains("in namespace");
      assertThat(output).hasMessageThat().contains(namespace);
    } else {
      assertThat(output).hasMessageThat().doesNotContain("in namespace");
    }
    assertThat(output).hasMessageThat().contains(INDEX_NAME);
  }

  /**
   * Sets up ApiProxy to throw the given exception, then sends a request through
   * SearchApiHelper#makeAsyncIndexDocumentCall, and evaluates the resulting Future, expecting an
   * ExecutionException. Uses empty namespace for index.
   *
   * @return the cause of the ExecutionException.
   */
  private Throwable indexDocument(Throwable exceptionFromApiProxy) throws Exception {
    @SuppressWarnings("unchecked")
    ApiProxy.Delegate<ApiProxy.Environment> delegate = Mockito.mock(ApiProxy.Delegate.class);
    when(delegate.makeAsyncCall(any(), any(), any(), any(), any()))
        .thenReturn(Futures.immediateFailedFuture(exceptionFromApiProxy));

    ApiProxy.setDelegate(delegate);
    SearchApiHelper helper = new SearchApiHelper();
    Future<SearchServicePb.IndexDocumentResponse.Builder> future =
        helper.makeAsyncIndexDocumentCall(paramsBuilder(""), null /* deadline */);
    try {
      future.get();
      throw new AssertionError("Expected exception");
    } catch (ExecutionException expected) {
      return expected.getCause();
    }
  }
}
