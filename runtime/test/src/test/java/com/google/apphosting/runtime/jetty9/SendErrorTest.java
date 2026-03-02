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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.List;
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
  public static List<Object[]> version() {
    return allVersions();
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;

  public SendErrorTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws Exception {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  @Before
  public void start() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/senderrorapp/";
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
    runtime.close();
  }

  @Test
  public void testSendError() throws Exception {
    String url = runtime.jettyUrl("/send-error");
    ContentResponse response = httpClient.GET(url);
    assertEquals(HttpStatus.OK_200, response.getStatus());
    assertThat(
        response.getContentAsString(),
        containsString("<h1>Hello, welcome to App Engine Java Standard!</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=404");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.NOT_FOUND_404, response.getStatus());
    assertThat(
        response.getContentAsString(),
        containsString("<h1>404 - Page Not Found (App Engine Java Standard)</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=500");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, response.getStatus());
    assertThat(
        response.getContentAsString(),
        containsString("<h1>500 - Internal Server Error (App Engine Java Standard)</h1>"));

    url = runtime.jettyUrl("/send-error?errorCode=503");
    response = httpClient.GET(url);
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE_503, response.getStatus());
    assertThat(
        response.getContentAsString(),
        containsString(
            "<h1>Unhandled Error - Service Temporarily Unavailable (App Engine Java"
                + " Standard)</h1>"));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return createRuntimeContext(config);
  }
}
