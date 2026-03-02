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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Result;
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
public class ServletContextListenerTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;

  public ServletContextListenerTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws Exception {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }

  @Before
  public void before() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/servletcontextlistenerapp/";
    if (isJakarta()) {
      app = app + "ee10";
    } else {
      app = app + "ee8";
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
  public void testServletContextListener() throws Exception {
    String url = runtime.jettyUrl("/");
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    StringBuilder contentReceived = new StringBuilder();
    httpClient
        .newRequest(url)
        .onResponseContentAsync(
            (response, chunk, callback) -> {
              // 1. Process the chunk (get the ByteBuffer)
              // chunk.getByteBuffer() gives you the data
              contentReceived.append(BufferUtil.toString(chunk.getByteBuffer(), UTF_8));

              // 2. Signal that you have consumed this chunk and are ready for the next
              // In Jetty 12, 'callback' is a java.lang.Runnable
              callback.run();
            })
        .send(completionListener::complete);

    Result result = completionListener.get(5, SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.toString(), equalTo("ServletContextListenerAttribute: true"));
  }
}
