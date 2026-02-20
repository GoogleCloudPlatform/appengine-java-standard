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

package com.google.appengine.api.urlfetch.dev;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalURLFetchServiceTest {

  private LocalURLFetchService lufs;

  @Before
  public void setUp() throws Exception {
    lufs = new LocalURLFetchService();
  }

  @Test
  public void testIsAllowedPort() {
    assertThat(lufs.isAllowedPort(-1)).isTrue();

    assertThat(lufs.isAllowedPort(79)).isFalse();
    assertThat(lufs.isAllowedPort(80)).isTrue();
    assertThat(lufs.isAllowedPort(81)).isTrue();
    assertThat(lufs.isAllowedPort(90)).isTrue();
    assertThat(lufs.isAllowedPort(91)).isFalse();

    assertThat(lufs.isAllowedPort(439)).isFalse();
    assertThat(lufs.isAllowedPort(440)).isTrue();
    assertThat(lufs.isAllowedPort(441)).isTrue();
    assertThat(lufs.isAllowedPort(450)).isTrue();
    assertThat(lufs.isAllowedPort(451)).isFalse();

    assertThat(lufs.isAllowedPort(1023)).isFalse();
    assertThat(lufs.isAllowedPort(1024)).isTrue();
    assertThat(lufs.isAllowedPort(65535)).isTrue();
  }

  @Test
  public void testHasValidURL() {
    assertThat(
            lufs.hasValidURL(
                URLFetchRequest.newBuilder().setUrl("").setMethod(RequestMethod.GET).build()))
        .isFalse();

    assertThat(
            lufs.hasValidURL(
                URLFetchRequest.newBuilder()
                    .setUrl("httpp://google.com")
                    .setMethod(RequestMethod.GET)
                    .build()))
        .isFalse();

    assertThat(
            lufs.hasValidURL(
                URLFetchRequest.newBuilder()
                    .setUrl("http://google.com")
                    .setMethod(RequestMethod.GET)
                    .build()))
        .isTrue();

    assertThat(
            lufs.hasValidURL(
                URLFetchRequest.newBuilder()
                    .setUrl("https://google.com")
                    .setMethod(RequestMethod.GET)
                    .build()))
        .isTrue();
  }

  @Test
  public void testNonstandardPortLogsWarning() {
    final StringBuilder sb = new StringBuilder();
    Logger logger = Logger.getLogger(LocalURLFetchService.class.getName());
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            sb.append(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      lufs.hasValidURL(
          URLFetchRequest.newBuilder()
              .setUrl("https://google.com:25")
              .setMethod(RequestMethod.GET)
              .build());
    } finally {
      logger.removeHandler(handler);
    }
    assertThat(sb.toString())
        .isEqualTo(
            "urlfetch received https://google.com:25 ; port 25 is not allowed in production!");
  }

  @Test
  public void testProxyConfiguration() throws Exception {
    String currentProxyHost = System.getProperty("http.proxyHost");
    String currentProxyPort = System.getProperty("http.proxyPort");
    String currentNonProxyHosts = System.getProperty("http.nonProxyHosts");

    System.setProperty("http.proxyHost", "192.168.0.1");
    System.setProperty("http.proxyPort", "9999");
    System.setProperty("http.nonProxyHosts", "*.foo.com");

    LocalURLFetchService service = new LocalURLFetchService();
    AbstractHttpClient httpClient = (AbstractHttpClient) service.createHttpClient(false);
    HttpRoutePlanner routePlanner = httpClient.getRoutePlanner();
    DefaultHttpRequestFactory requestFactory = new DefaultHttpRequestFactory();

    try {
      HttpRequest httpRequest = requestFactory.newHttpRequest("GET", "http://www.google.com");

      HttpRoute route =
          routePlanner.determineRoute(new HttpHost("www.google.com"), httpRequest, null);
      assertThat(route.getProxyHost().getHostName()).isEqualTo("192.168.0.1");
      assertThat(route.getProxyHost().getPort()).isEqualTo(9999);

      route = routePlanner.determineRoute(new HttpHost("www.foo.com"), httpRequest, null);
      assertThat(route.getProxyHost()).isNull();
    } finally {
      if (currentProxyHost != null) {
        System.setProperty("http.proxyHost", currentProxyHost);
      } else {
        System.clearProperty("http.proxyHost");
      }
      if (currentProxyPort != null) {
        System.setProperty("http.proxyPort", currentProxyPort);
      } else {
        System.clearProperty("http.proxyPort");
      }
      if (currentNonProxyHosts != null) {
        System.setProperty("http.nonProxyHosts", currentNonProxyHosts);
      } else {
        System.clearProperty("http.nonProxyHosts");
      }
    }
  }
}
