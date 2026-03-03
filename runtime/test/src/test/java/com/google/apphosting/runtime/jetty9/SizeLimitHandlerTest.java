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

package com.google.apphosting.runtime.jetty9;

import static com.google.apphosting.runtime.jetty9.JavaRuntimeViaHttpBase.allVersions;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.OutputStreamRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SizeLimitHandlerTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  private static final int MAX_SIZE = 32 * 1024 * 1024;

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;

  public SizeLimitHandlerTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws Exception {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  @Before
  public void start() throws Exception {
    String app = "sizelimit";
    if (isJakarta()) {
      app = app + "ee10";
    }
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
    assertEnvironment();
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void testResponseContentBelowMaxLength() throws Exception {
    long contentLength = MAX_SIZE;
    String url = runtime.jettyUrl("/?size=" + contentLength);
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    AtomicLong contentReceived = new AtomicLong();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              contentReceived.addAndGet(chunk.getByteBuffer().remaining());
              callback.run();
            })
        .headers(h -> h.put("setCustomHeader", "true"))
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.get(), equalTo(contentLength));
    assertThat(result.getResponse().getHeaders().get("custom-header"), equalTo("true"));
  }

  @Test
  public void testResponseContentAboveMaxLength() throws Exception {
    long contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/?size=" + contentLength);

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    Utf8StringBuilder received = new Utf8StringBuilder();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (r, chunk, cb) -> {
              received.append(chunk.getByteBuffer());
              cb.run();
            })
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.MINUTES);

    if (useHttpConnector) {
      assertNull(result.getRequestFailure());
      assertNotNull(result.getResponseFailure());
    } else {
      assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.INTERNAL_SERVER_ERROR_500));
    }
    assertThat(received.length(), lessThanOrEqualTo(MAX_SIZE));

    if (!Objects.equals(jettyVersion, "9.4") && !useHttpConnector) {
      assertThat(received.toString(), containsString("Response body is too large"));
    }
  }

  @Test
  public void testResponseContentBelowMaxLengthGzip() throws Exception {
    long contentLength = MAX_SIZE;
    String url = runtime.jettyUrl("/?size=" + contentLength);
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    AtomicLong contentReceived = new AtomicLong();
    httpClient.getContentDecoderFactories().clear();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              contentReceived.addAndGet(chunk.getByteBuffer().remaining());
              callback.run();
            })
        .headers(h -> h.put(HttpHeader.ACCEPT_ENCODING, "gzip"))
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getHeaders().get(HttpHeader.CONTENT_ENCODING), equalTo("gzip"));
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.get(), lessThan(contentLength));
  }

  @Test
  public void testResponseContentAboveMaxLengthGzip() throws Exception {
    long contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/?size=" + contentLength);
    httpClient.getContentDecoderFactories().clear();

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    Utf8StringBuilder received = new Utf8StringBuilder();
    AtomicInteger receivedCount = new AtomicInteger();
    httpClient
        .newRequest(url)
        .headers(h -> h.put(HttpHeader.ACCEPT_ENCODING, "gzip"))
        .onResponseContentAsync(
            (r, chunk, cb) -> {
              ByteBuffer b = chunk.getByteBuffer();
              receivedCount.addAndGet(b.remaining());
              if (!useHttpConnector) {
                received.append(b);
              }
              cb.run();
            })
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);

    if (useHttpConnector) {
      assertNull(result.getRequestFailure());
      assertNotNull(result.getResponseFailure());
    } else {
      assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.INTERNAL_SERVER_ERROR_500));
    }
    assertThat(received.length(), lessThanOrEqualTo(MAX_SIZE));

    if (!Objects.equals(jettyVersion, "9.4") && !useHttpConnector) {
      assertThat(received.toString(), containsString("Response body is too large"));
    }
  }

  @Test
  public void testRequestContentBelowMaxLength() throws Exception {
    int contentLength = MAX_SIZE;

    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');
    Request.Content content = new BytesRequestContent(data);
    String url = runtime.jettyUrl("/");
    ContentResponse response = httpClient.newRequest(url).body(content).send();

    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(
        response.getContentAsString(), containsString("RequestContentLength: " + contentLength));
  }

  @Test
  public void testRequestContentAboveMaxLength() throws Exception {
    int contentLength = MAX_SIZE + 1;

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');
    Utf8StringBuilder received = new Utf8StringBuilder();
    Request.Content content = new BytesRequestContent(data);
    String url = runtime.jettyUrl("/");
    httpClient
        .newRequest(url)
        .body(content)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              received.append(chunk.getByteBuffer());
              callback.run();
            })
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.PAYLOAD_TOO_LARGE_413));

    // If the request was not aborted, then we expect to see the error message in the response body.
    if (result.getResponseFailure() == null) {
      assertThat(received.toString(), containsString("Request body is too large"));
    }
  }

  @Test
  public void testRequestContentBelowMaxLengthGzip() throws Exception {
    int contentLength = MAX_SIZE;

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');
    Utf8StringBuilder received = new Utf8StringBuilder();
    Request.Content content = new InputStreamRequestContent(gzip(data));

    String url = runtime.jettyUrl("/");
    httpClient
        .newRequest(url)
        .body(content)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              received.append(chunk.getByteBuffer());
              callback.run();
            })
        .headers(h -> h.put(HttpHeader.CONTENT_ENCODING, "gzip"))
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(received.toString(), containsString("RequestContentLength: " + contentLength));
  }

  @Test
  public void testRequestContentAboveMaxLengthGzip() throws Exception {
    int contentLength = MAX_SIZE + 1;

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');
    Utf8StringBuilder received = new Utf8StringBuilder();
    Request.Content content = new InputStreamRequestContent(gzip(data));

    String url = runtime.jettyUrl("/");
    httpClient
        .newRequest(url)
        .body(content)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              received.append(chunk.getByteBuffer());
              callback.run();
            })
        .headers(h -> h.put(HttpHeader.CONTENT_ENCODING, "gzip"))
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.PAYLOAD_TOO_LARGE_413));

    // If the request was not aborted, then we expect to see the error message in the response body.
    if (result.getResponseFailure() == null) {
      assertThat(received.toString(), containsString("Request body is too large"));
    }
  }

  @Test
  public void testResponseContentLengthHeader() throws Exception {
    long contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/?setContentLength=" + contentLength);
    httpClient.getContentDecoderFactories().clear();
    ContentResponse response = httpClient.newRequest(url).send();

    assertThat(response.getStatus(), equalTo(HttpStatus.INTERNAL_SERVER_ERROR_500));

    if (jettyVersion.equals("9.4") && useHttpConnector) {
      assertThat(response.getContentAsString(), containsString("Response body is too large"));
    }
  }

  @Test
  public void testRequestContentLengthHeader() throws Exception {
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    // DeferredContentProvider is replaced by OutputStreamRequestContent in Jetty 12 for similar
    // 'deferred' patterns
    OutputStreamRequestContent content = new OutputStreamRequestContent();
    int contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/");
    Utf8StringBuilder received = new Utf8StringBuilder();
    httpClient
        .newRequest(url)
        .headers(
            h -> {
              h.put(HttpHeader.CONTENT_LENGTH, Long.toString(contentLength));
              h.put("foo", "bar");
            })
        .body(content)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              received.append(chunk.getByteBuffer());
              callback.run();
              // In Jetty 12, we close the content specifically
              content.close();
            })
        .send(completionListener::complete);

    // Provide the single byte needed to start the request
    try (OutputStream os = content.getOutputStream()) {
      os.write(1);
    }

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    Response response = result.getResponse();
    assertThat(response.getStatus(), equalTo(HttpStatus.PAYLOAD_TOO_LARGE_413));

    // If the request was not aborted, then we expect to see the error message in the response body.
    if (result.getResponseFailure() == null) {
      assertThat(received.toString(), containsString("Request body is too large"));
    }
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }

  private void assertEnvironment() throws Exception {
    String match =
        switch (jakartaVersion) {
          case "EE6" ->
              useHttpConnector
                  ? "com.google.apphosting.runtime.jetty9.JettyRequestAPIData"
                  : "org.eclipse.jetty.server.Request";
          case "EE8" -> "org.eclipse.jetty.ee8";
          case "EE10" -> "org.eclipse.jetty.ee1"; // EE10 could be upgraded to EE11!
          case "EE11" -> "org.eclipse.jetty.ee11";
          default -> throw new IllegalArgumentException(jakartaVersion);
        };

    String runtimeUrl = runtime.jettyUrl("/?getRequestClass=true");
    ContentResponse response = httpClient.GET(runtimeUrl);
    assertThat(response.getContentAsString(), containsString(match));
  }

  private static InputStream gzip(byte[] data) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(data);
    }
    return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
  }
}
