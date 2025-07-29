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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SendErrorTest extends JavaRuntimeViaHttpBase {

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
            {"jetty94", false},
            {"jetty94", true},
            {"ee8", false},
            {"ee8", true},
            {"ee11", false},
            {"ee11", true},
        });
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;


  public SendErrorTest(String environment, boolean httpMode) {
    this.environment = environment;
    this.httpMode = httpMode;
    System.setProperty("appengine.use.HttpConnector", Boolean.toString(httpMode));
  }

  @Before
  public void start() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/senderrorapp/" + environment;
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
  public void testSendError() throws Exception {
    String url = runtime.jettyUrl("/send-error");
    ContentResponse response = httpClient.GET(url);
    assertEquals(HttpStatus.OK_200, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Hello, welcome to App Engine Java Standard!</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=404");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.NOT_FOUND_404, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>404 - Page Not Found (App Engine Java Standard)</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=500");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>500 - Internal Server Error (App Engine Java Standard)</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=503");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE_503, response.getStatus());
    assertThat(response.getContentAsString(), containsString("<h1>Unhandled Error - Service Temporarily Unavailable (App Engine Java Standard)</h1>"));

  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }

}