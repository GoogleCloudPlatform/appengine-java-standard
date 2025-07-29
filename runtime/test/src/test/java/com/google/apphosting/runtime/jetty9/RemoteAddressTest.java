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
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
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
          {"ee11", false},
          {"ee11", true},
        });
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private final boolean httpMode;
  private final String environment;
  private RuntimeContext<?> runtime;
  private String url;

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
    url = runtime.jettyUrl("/");
    System.err.println("==== Using Environment: " + environment + " " + httpMode + " ====");
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    runtime.close();
  }

  @Test
  public void testWithHostHeader() throws Exception {
    ContentResponse response =
        httpClient
            .newRequest(url)
            .header("Host", "foobar:1234")
            .header("X-AppEngine-User-IP", "203.0.113.1")
            .send();

    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = response.getContentAsString();
    assertThat(contentReceived, containsString("getRemoteAddr: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemoteHost: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: foobar"));
    assertThat(contentReceived, containsString("getServerPort: 1234"));
  }

  @Test
  public void testWithIPv6() throws Exception {
    // Test the host header to be IPv6 with a port.
    ContentResponse response =
        httpClient
            .newRequest(url)
            .header("Host", "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:1234")
            .header("X-AppEngine-User-IP", "203.0.113.1")
            .send();
    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = response.getContentAsString();
    assertThat(contentReceived, containsString("getRemoteAddr: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemoteHost: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(
        contentReceived, containsString("getServerName: [2001:db8:85a3:8d3:1319:8a2e:370:7348]"));
    assertThat(contentReceived, containsString("getServerPort: 1234"));

    // Test the user IP to be IPv6 with a port.
    response =
        httpClient
            .newRequest(url)
            .header("Host", "203.0.113.1:1234")
            .header("X-AppEngine-User-IP", "2001:db8:85a3:8d3:1319:8a2e:370:7348")
            .send();
    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    contentReceived = response.getContentAsString();
    if ("jetty94".equals(environment)) {
      assertThat(
          contentReceived, containsString("getRemoteAddr: [2001:db8:85a3:8d3:1319:8a2e:370:7348]"));
      assertThat(
          contentReceived, containsString("getRemoteHost: [2001:db8:85a3:8d3:1319:8a2e:370:7348]"));
    } else {
      // The correct behaviour for getRemoteAddr and getRemoteHost is to not include []
      // because they return raw IP/hostname and not URI-formatted addresses.
      assertThat(
          contentReceived, containsString("getRemoteAddr: 2001:db8:85a3:8d3:1319:8a2e:370:7348"));
      assertThat(
          contentReceived, containsString("getRemoteHost: 2001:db8:85a3:8d3:1319:8a2e:370:7348"));
    }
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: 203.0.113.1"));
    assertThat(contentReceived, containsString("getServerPort: 1234"));
  }

  @Test
  public void testWithoutHostHeader() throws Exception {
    ContentResponse response =
        httpClient
            .newRequest(url)
            .version(HttpVersion.HTTP_1_0)
            .header("X-AppEngine-User-IP", "203.0.113.1")
            .onRequestHeaders(request -> request.getHeaders().remove("Host"))
            .send();

    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = response.getContentAsString();
    assertThat(contentReceived, containsString("getRemoteAddr: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemoteHost: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: 127.0.0.1"));
    assertThat(contentReceived, containsString("getServerPort: " + runtime.getPort()));
  }

  @Test
  public void testForwardedHeadersIgnored() throws Exception {
    ContentResponse response =
        httpClient
            .newRequest(url)
            .header("Host", "foobar:1234")
            .header("X-AppEngine-User-IP", "203.0.113.1")
            .header(HttpHeader.X_FORWARDED_FOR, "test1:2221")
            .header(HttpHeader.X_FORWARDED_PROTO, "test2:2222")
            .header(HttpHeader.X_FORWARDED_HOST, "test3:2223")
            .header(HttpHeader.X_FORWARDED_PORT, "test4:2224")
            .header(HttpHeader.FORWARDED, "test5:2225")
            .send();

    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    String contentReceived = response.getContentAsString();
    assertThat(contentReceived, containsString("getRemoteAddr: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemoteHost: 203.0.113.1"));
    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: foobar"));
    assertThat(contentReceived, containsString("getServerPort: 1234"));
  }

  private RuntimeContext<?> runtimeContext() throws Exception {
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(temp.getRoot().toString()).build();
    return RuntimeContext.create(config);
  }
}
