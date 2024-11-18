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
import static org.hamcrest.MatcherAssert.assertThat;

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
public class RemoteAddressTest extends JavaRuntimeViaHttpBase {

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

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;

  public RemoteAddressTest(String environment, boolean httpMode) {
    this.environment = environment;
    this.httpMode = httpMode;
    System.setProperty("appengine.use.HttpConnector", Boolean.toString(httpMode));
  }

  @Before
  public void before() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/remoteaddrapp/" + environment;
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
  public void test() throws Exception {
    String url = runtime.jettyUrl("/");
    ContentResponse response = httpClient.newRequest(url)
            .header("X-AppEngine-User-IP", "203.0.113.1")
            .send();

    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = response.getContentAsString();
    assertThat(contentReceived, containsString("getRemoteAddr: 203.0.113.1"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getServerPort: " + runtime.getPort()));
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: 0.0.0.0"));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }
}
