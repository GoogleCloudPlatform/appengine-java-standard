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

import java.util.List;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
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
  public static List<Object[]> version() {
    return allVersions();
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private final HttpClient httpClient = new HttpClient();
  private RuntimeContext<?> runtime;
  private String url;

  public RemoteAddressTest(
      String runtimeVersion, String jettyVersion, String jakartaVersion, boolean useHttpConnector) {
    super(runtimeVersion, jettyVersion, jakartaVersion, useHttpConnector);
  }

  @Before
  public void before() throws Exception {
    String app = "com/google/apphosting/runtime/jetty9/remoteaddrapp/";
    if (isJakarta()) {
      app = app + "ee10";
    } else {
      app = app + "ee8";
    }
    copyAppToDir(app, temp.getRoot().toPath());
    httpClient.start();
    runtime = runtimeContext();
    url = runtime.jettyUrl("/");
    System.err.println("==== Using Environment: " + jakartaVersion + " ====");
  }

  @After
  public void after() throws Exception {
    httpClient.stop();
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  public void testWithHostHeader() throws Exception {
    ContentResponse response =
        httpClient
            .newRequest(url)
            .headers(
                headers ->
                    headers
                        .put(HttpHeader.HOST, "foobar:1234")
                        .put("X-AppEngine-User-IP", "203.0.113.1"))
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
            .headers(
                h ->
                    h.put(HttpHeader.HOST, "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:1234")
                        .put("X-AppEngine-User-IP", "203.0.113.1"))
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
            .headers(
                h ->
                    h.put(HttpHeader.HOST, "203.0.113.1:1234")
                        .put("X-AppEngine-User-IP", "2001:db8:85a3:8d3:1319:8a2e:370:7348"))
            .send();
    assertThat(response.getStatus(), equalTo(HttpStatus.OK_200));
    contentReceived = response.getContentAsString();

    // The correct behaviour for getRemoteAddr and getRemoteHost is to not include []
    // because they return raw IP/hostname and not URI-formatted addresses.
    assertThat(
        contentReceived, containsString("getRemoteAddr: 2001:db8:85a3:8d3:1319:8a2e:370:7348"));
    assertThat(
        contentReceived, containsString("getRemoteHost: 2001:db8:85a3:8d3:1319:8a2e:370:7348"));

    assertThat(contentReceived, containsString("getRemotePort: 0"));
    assertThat(contentReceived, containsString("getLocalAddr: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalName: 0.0.0.0"));
    assertThat(contentReceived, containsString("getLocalPort: 0"));
    assertThat(contentReceived, containsString("getServerName: 203.0.113.1"));
    assertThat(contentReceived, containsString("getServerPort: 1234"));
  }

  @Test
  public void testWithoutHostHeader() throws Exception {
    Request request =
        httpClient
            .newRequest(url)
            .version(HttpVersion.HTTP_1_0)
            .headers(
                h ->
                    h.put("X-AppEngine-User-IP", "203.0.113.1")
                        .remove(HttpHeader.HOST) // Cleaner way to handle the null/removal intent
                );
    ContentResponse response = request.send();

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
            .headers(
                h ->
                    h.put(HttpHeader.HOST, "foobar:1234")
                        .put("X-AppEngine-User-IP", "203.0.113.1")
                        .put(HttpHeader.X_FORWARDED_FOR, "test1:2221")
                        .put(HttpHeader.X_FORWARDED_PROTO, "test2:2222")
                        .put(HttpHeader.X_FORWARDED_HOST, "test3:2223")
                        .put(HttpHeader.X_FORWARDED_PORT, "test4:2224")
                        .put(HttpHeader.FORWARDED, "test5:2225"))
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
    return createRuntimeContext(config);
  }
}
