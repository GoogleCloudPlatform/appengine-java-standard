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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SizeLimitIgnoreTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  private static final int MAX_SIZE = 32 * 1024 * 1024;

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;

  public SizeLimitIgnoreTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws Exception {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
    System.setProperty("appengine.ignore.responseSizeLimit", "true");
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
  public void testResponseContentAboveMaxLengthIgnored() throws Exception {
    long contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/?size=" + contentLength);
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    AtomicLong contentReceived = new AtomicLong();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (response, content, callback) -> {
              contentReceived.addAndGet(content.remaining());
              callback.succeeded();
            })
        .header("setCustomHeader", "true")
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.get(), equalTo(contentLength));
    assertThat(result.getResponse().getHeaders().get("custom-header"), equalTo("true"));
  }

  @Test
  public void testResponseContentAboveMaxLengthGzipIgnored() throws Exception {
    long contentLength = MAX_SIZE + 1;
    String url = runtime.jettyUrl("/?size=" + contentLength);
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    AtomicLong contentReceived = new AtomicLong();
    httpClient.getContentDecoderFactories().clear();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (response, content, callback) -> {
              contentReceived.addAndGet(content.remaining());
              callback.succeeded();
            })
        .header(HttpHeader.ACCEPT_ENCODING, "gzip")
        .send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getHeaders().get(HttpHeader.CONTENT_ENCODING), equalTo("gzip"));
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.get(), lessThan(contentLength));
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
}
