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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.http.HttpFields;
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
public class GzipHandlerTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"jetty94", false},
          {"jetty94", true},
          {"ee8", false},
          {"ee8", true},
          {"ee10", false},
          {"ee10", true},
        });
  }

  private static final int MAX_SIZE = 32 * 1024 * 1024;

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;

  public GzipHandlerTest(String environment, boolean httpMode) {
    this.environment = environment;
    this.httpMode = httpMode;
    System.setProperty("appengine.use.HttpConnector", Boolean.toString(httpMode));
  }

  @Before
  public void before() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/gzipapp/" + environment;
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
    System.err.println("==== Using Environment: " + environment + " " + httpMode + " ====");
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    runtime.close();
  }

  @Test
  public void testRequestGzipContent() throws Exception {
    int contentLength = 1024;

    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    byte[] data = new byte[contentLength];
    Arrays.fill(data, (byte) 'X');
    Utf8StringBuilder received = new Utf8StringBuilder();
    ContentProvider content = new InputStreamContentProvider(gzip(data));

    String url = runtime.jettyUrl("/");
    httpClient
        .newRequest(url)
        .content(content)
        .onResponseContentAsync(
            (response, content1, callback) -> {
              received.append(content1);
              callback.succeeded();
            })
        .header(HttpHeader.CONTENT_ENCODING, "gzip")
        .send(completionListener::complete);

    // The request was successfully decoded by the GzipHandler.
    Result response = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(response.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = received.toString();
    assertThat(contentReceived, containsString("\nX-Content-Encoding: gzip\n"));
    assertThat(contentReceived, not(containsString("\nContent-Encoding: gzip\n")));
    assertThat(contentReceived, containsString("\nAccept-Encoding: gzip\n"));

    // Server correctly echoed content of request.
    String expectedData = new String(data);
    String actualData = contentReceived.substring(contentReceived.length() - contentLength);
    assertThat(actualData, equalTo(expectedData));

    // Response was gzip encoded.
    HttpFields responseHeaders = response.getResponse().getHeaders();
    assertThat(responseHeaders.get(HttpHeader.CONTENT_ENCODING), equalTo("gzip"));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

  private static InputStream gzip(byte[] data) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(data);
    }
    return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
  }
}
