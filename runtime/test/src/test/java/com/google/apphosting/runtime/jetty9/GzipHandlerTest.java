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
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.BufferUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GzipHandlerTest extends JavaRuntimeViaHttpBase {

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  public GzipHandlerTest(
      String runtimeVersion, String jettyVersion, String jakartaVersion, boolean useHttpConnector) {
    super(runtimeVersion, jettyVersion, jakartaVersion, useHttpConnector);
  }

  @Before
  public void before() throws Exception {
    String app;
    if (isJakarta()) {
      app = "com/google/apphosting/runtime/jetty9/gzipapp/ee10";
    } else {
      app = "com/google/apphosting/runtime/jetty9/gzipapp/ee8";
    }
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void testRequestGzipContent() throws Exception {
    int contentLength = 1024;

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');

    // In Jetty 12, use InputStreamRequestContent
    Request.Content content = new InputStreamRequestContent(gzip(data));
    
    // Clear factories so the client receives raw compressed bytes for manual verification
    httpClient.getContentDecoderFactories().clear();

    ByteArrayOutputStream receivedBytes = new ByteArrayOutputStream();
    String url = runtime.jettyUrl("/");
    
    httpClient
        .newRequest(url)
        .body(content)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              try {
                // BufferUtil.writeTo is the efficient way to drain the chunk's ByteBuffer
                BufferUtil.writeTo(chunk.getByteBuffer(), receivedBytes);
                callback.run();
              } catch (IOException e) {
                // If writing fails, we still need to run the callback to avoid hanging the client
                callback.run();
                completionListener.completeExceptionally(e);
              }
            })
        .headers(headers -> {
            // Tell the server we are sending gzip
            headers.put(HttpHeader.CONTENT_ENCODING, "gzip");
            // Tell the server we want gzip back (Crucial for Jetty 12 tests)
            headers.put(HttpHeader.ACCEPT_ENCODING, "gzip");
        })
        .send(completionListener::complete);

    // The request was successfully decoded by the GzipHandler.
    Result result = completionListener.get(5, SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));

    // Verify response was gzip encoded
    HttpFields responseHeaders = result.getResponse().getHeaders();
    assertThat(responseHeaders.get(HttpHeader.CONTENT_ENCODING), equalTo("gzip"));

    // Manually decompress the received bytes
    String contentReceived;
    try (InputStream in =
        new GZIPInputStream(new ByteArrayInputStream(receivedBytes.toByteArray()))) {
      contentReceived = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    
    boolean isWindows = OS_NAME.value().toLowerCase(Locale.ROOT).contains("windows");
    String nl = isWindows ? "\r\n" : "\n";

    // Verify headers that were echoed back in the response body by the test app
    assertThat(contentReceived, containsString(nl + "X-Content-Encoding: gzip" + nl));
    assertThat(contentReceived, not(containsString(nl + "Content-Encoding: gzip" + nl)));
    assertThat(contentReceived, containsString(nl + "Accept-Encoding: gzip" + nl));

    // Verify the actual payload data integrity
    String expectedData = new String(data, StandardCharsets.UTF_8);
    String actualData = contentReceived.substring(contentReceived.length() - contentLength);
    assertThat(actualData, equalTo(expectedData));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }

  private static InputStream gzip(byte[] data) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(data);
    }
    return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
  }
}