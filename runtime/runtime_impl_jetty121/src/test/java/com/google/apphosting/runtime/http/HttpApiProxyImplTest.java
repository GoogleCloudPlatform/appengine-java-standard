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

package com.google.apphosting.runtime.http;

import static com.google.apphosting.runtime.http.HttpApiHostClient.MAX_PAYLOAD;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.TraceWriter;
import com.google.apphosting.runtime.grpc.FakeApiProxyImplFactory;
import com.google.apphosting.testing.PortPicker;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests for HTTP-based APIHost traffic. */
@RunWith(Parameterized.class)
public class HttpApiProxyImplTest extends HttpApiProxyImplTestBase {
  @Test
  public void apiCall() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = {1, 2, 3, 4};
    byte[] responsePayload =
        apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ECHO_METHOD, requestPayload);
    assertThat(responsePayload).isEqualTo(requestPayload);
  }

  @Test
  public void apiCallNoTraceWriter() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl noTraceEnvironment =
        FakeApiProxyImplFactory.fakeEnvironment(apiProxyImpl, FAKE_SECURITY_TICKET);
    byte[] requestPayload = {1, 2, 3, 4};
    ApiProxy.ApplicationException exception =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () ->
                apiProxyImpl.makeSyncCall(
                    noTraceEnvironment, ECHO_SERVICE, SWALLOW_METHOD, requestPayload));
    assertThat(exception.getApplicationError()).isEqualTo(FAKE_APPLICATION_ERROR);
    assertThat(exception).hasMessageThat().contains("Wrong traceId");
  }

  @Test
  public void bigApiCall() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = new byte[MAX_PAYLOAD];
    Arrays.fill(requestPayload, (byte) 99);
    byte[] responsePayload =
        apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ECHO_METHOD, requestPayload);
    assertThat(responsePayload).isEqualTo(requestPayload);
  }

  @Test
  public void badApiResponse() {
    // TODO: figure out whether this test method makes sense.
    // For now, we only enable it with the Jetty client.
    assumeTrue(propertyToSet == null);
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = {1, 2, 3, 4};
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, BAD_METHOD, requestPayload));
    assertThat(exception)
        .hasCauseThat()
        .hasCauseThat()
        .hasCauseThat()
        .hasMessageThat()
        .isAnyOf(
            /* jetty 9.4 msg */ "400: Bad Transfer-Encoding, chunked not last",
            /* jetty 9.4 other msg */ "400: Transfer-Encoding and Content-Length");
  }

  @Test
  public void overBigApiRequest() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = new byte[MAX_PAYLOAD + 10_000];
    assertThrows(
        ApiProxy.RequestTooLargeException.class,
        () -> apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, SWALLOW_METHOD, requestPayload));
  }

  @Test
  public void overBigApiResponse() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = String.valueOf(MAX_PAYLOAD + 10_000).getBytes(UTF_8);
    assertThrows(
        ApiProxy.ResponseTooLargeException.class,
        () -> apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, SPEW_METHOD, requestPayload));
  }

  @Test
  public void errorApiCall() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = {1, 2, 3, 4};
    ApiProxy.ApplicationException exception =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () ->
                apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ERROR_METHOD, requestPayload));
    assertThat(exception.getApplicationError()).isEqualTo(FAKE_APPLICATION_ERROR);
    assertThat(exception.getErrorDetail()).isEqualTo(FAKE_ERROR_DETAIL);
  }

  @Test
  public void timeout() {
    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    environment.getAttributes().put(API_DEADLINE_KEY, 1); // 1 second
    byte[] requestPayload = "10".getBytes(UTF_8); // 10 seconds
    assertThrows(
        ApiProxy.ApiDeadlineExceededException.class,
        () -> apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, DELAY_METHOD, requestPayload));
  }

  /**
   * Tests that we behave correctly if the API server at the other end of our HTTP connection
   * doesn't reply in a timely fashion. We freeze the API server so it won't reply in time, and then
   * make an async API call to it with a timeout of 1 second. Because the server is frozen, it can't
   * respect the timeout, but ApiProxyImpl will time out anyway after a short delay.
   */
  @Test
  public void serverUnresponsive() throws Exception {
    fakeHttpApiHost.freeze();

    ApiProxyImpl apiProxyImpl = newApiProxyImpl();
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(1.0);
    Future<?> future =
        apiProxyImpl.makeAsyncCall(
            environment, ECHO_SERVICE, SWALLOW_METHOD, new byte[0], apiConfig);
    ExecutionException exception =
        assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
    assertThat(exception).hasCauseThat().isInstanceOf(ApiProxy.ApiDeadlineExceededException.class);
  }

  /**
   * Tests that we behave correctly if the API server at the other end of our HTTP connection
   * doesn't reply in a timely fashion. This is similar to {@link #serverUnresponsive}, but we
   * configure the HTTP client in a special way that means that it will time out earlier than both
   * the API server and the API client. That means we can exercise the logic that converts that
   * timeout into an exception.
   */
  @Test
  public void httpClientTimesOut() throws Exception {
    fakeHttpApiHost.freeze();

    // We'll issue an API call with a deadline of 10 seconds, but we'll set the "extra timeout"
    // for HTTP to -9 seconds, so HTTP will time out before everything else.
    HttpApiHostClient.Config limitedConfig =
        config.toBuilder().setExtraTimeoutSeconds(-9.0).build();
    HttpApiHostClient limitedApiHostClient =
        HttpApiHostClient.create(fakeHttpApiHost.getUrl().toString(), limitedConfig);
    ApiProxyImpl apiProxyImpl = FakeApiProxyImplFactory.newApiProxyImpl(limitedApiHostClient);
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(10.0);
    Future<?> future =
        apiProxyImpl.makeAsyncCall(
            environment, ECHO_SERVICE, SWALLOW_METHOD, new byte[0], apiConfig);
    ExecutionException exception =
        assertThrows(ExecutionException.class, () -> future.get(20, SECONDS));
    assertThat(exception).hasCauseThat().isInstanceOf(ApiProxy.ApiDeadlineExceededException.class);
  }

  /**
   * Tests that if the server abruptly closes, we get ApiProxy.UnknownException, and it has a
   * non-null cause.
   */
  @Test
  public void serverClose() throws Exception {
    int port = PortPicker.create().pickUnusedPort();
    FakeHttpApiHost closeableHttpApiHost = FakeHttpApiHost.create(port, new EchoHandler());
    HttpApiHostClient apiHostClient =
        HttpApiHostClient.create(closeableHttpApiHost.getUrl().toString(), config);
    ApiProxyImpl apiProxyImpl = FakeApiProxyImplFactory.newApiProxyImpl(apiHostClient);
    ApiProxyImpl.EnvironmentImpl environment =
        FakeApiProxyImplFactory.fakeEnvironment(
            apiProxyImpl,
            FAKE_SECURITY_TICKET,
            new TraceWriter(cloudTraceContext, new MutableUpResponse()));

    Future<byte[]> success =
        apiProxyImpl.makeAsyncCall(
            environment, ECHO_SERVICE, ECHO_METHOD, new byte[1], new ApiProxy.ApiConfig());
    assertThat(success.get()).hasLength(1);

    closeableHttpApiHost.stop();
    Future<?> failure =
        apiProxyImpl.makeAsyncCall(
            environment, ECHO_SERVICE, ECHO_METHOD, new byte[1], new ApiProxy.ApiConfig());
    ExecutionException exception = assertThrows(ExecutionException.class, failure::get);
    assertThat(exception).hasCauseThat().isInstanceOf(ApiProxy.UnknownException.class);
    Throwable cause = exception.getCause().getCause();
    assertThat(cause).isNotNull();
  }

  // TODO: test other error cases in HttpApiHostClient.
}
