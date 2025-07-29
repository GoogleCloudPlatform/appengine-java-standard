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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Result;
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
public class ServletContextListenerTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"jetty94", false},
          {"ee8", false},
          {"ee11", false},
          {"ee8", true},
          {"ee11", true},
        });
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;

  public ServletContextListenerTest(String environment, boolean httpMode) {
    this.environment = environment;
    this.httpMode = httpMode;
    System.setProperty("appengine.use.HttpConnector", Boolean.toString(httpMode));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
            RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

  @Before
  public void before() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/servletcontextlistenerapp/" + environment;
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
    System.err.println("==== Using Environment: " + environment + " " + httpMode + " ====");
  }

  @After
  public void after() throws Exception
  {
    httpClient.stop();
    runtime.close();
  }

  @Test
  public void testServletContextListener() throws Exception {
    String url = runtime.jettyUrl("/");
    CompletableFuture<Result> completionListener = new CompletableFuture<>();
    Utf8StringBuilder contentReceived = new Utf8StringBuilder();
    httpClient.newRequest(url).onResponseContentAsync((response, content, callback) -> {
      contentReceived.append(content);
      callback.succeeded();
    }).send(completionListener::complete);

    Result result = completionListener.get(5, TimeUnit.SECONDS);
    assertThat(result.getResponse().getStatus(), equalTo(HttpStatus.OK_200));
    assertThat(contentReceived.toString(), equalTo("ServletContextListenerAttribute: true"));
  }
}
