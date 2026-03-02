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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.ResponseTooLargeException;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalURLFetchServiceTestConfig;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.testing.PortPicker;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.flogger.GoogleLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the {@link LocalURLFetchService}.
 *
 */
@RunWith(JUnit4.class)
public class LocalURLFetchServiceIntegrationTest {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String GSE_URL_FORMAT = "http://localhost:%s";
  private static final int MAX_RETRIES = 3;

  private static final PortPicker portPicker = PortPicker.create();

  private LocalServiceTestHelper helper;
  // Picks a random port between 5000 and 10000

  private URLFetchService fetchService;
  private Server server;
  private ServletHandler servletHandler;
  private int port;

  @Before
  public final void setUp() throws Exception {
    helper =
        new LocalServiceTestHelper(new LocalURLFetchServiceTestConfig())
            .setEnforceApiDeadlines(true);
    helper.setUp();
    fetchService = URLFetchServiceFactory.getURLFetchService();
    port = portPicker.pickUnusedPort();
    server = new Server(port);
    servletHandler = new ServletHandler();
    server.setHandler(servletHandler);
  }

  @After
  public final void tearDown() throws Exception {
    server.stop();
    server.join();
    fetchService = null;
    helper.tearDown();
  }

  @Test
  public void testGet() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/getTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(new URL(String.format(GSE_URL_FORMAT, port) + servletPath));
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(getClass().getName());
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getFinalUrl()).isNull();
  }

  @Test
  public void testGetWithTruncatedResponse() throws Exception {
    char[] testChars = new char[LocalURLFetchService.DEFAULT_MAX_RESPONSE_LENGTH + 1];
    Arrays.fill(testChars, 'a');
    String testString = new String(testChars);
    TestServlet servlet = new TestServlet(getHeadersToExpect(), testString);
    String servletPath = "/getTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request1 =
        new HTTPRequest(new URL(String.format(GSE_URL_FORMAT, port) + servletPath));
    addHeadersToSend(request1);
    assertThrows(ResponseTooLargeException.class, () -> fetchService.fetch(request1));
    HTTPRequest request2 =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath),
            HTTPMethod.GET,
            FetchOptions.Builder.allowTruncate());
    addHeadersToSend(request2);
    HTTPResponse response = fetchService.fetch(request2);
    byte[] content = response.getContent();
    assertThat(content).hasLength(LocalURLFetchService.DEFAULT_MAX_RESPONSE_LENGTH);
    assertThat(new String(content, UTF_8))
        .isEqualTo(testString.substring(0, testString.length() - 1));
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
  }

  @Test
  public void testTimeout() throws Exception {
    final CountDownLatch cdl = new CountDownLatch(1);
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            try {
              cdl.await();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };
    try {
      String servletPath = "/timeoutTest";
      addServlet(servletPath, servlet);
      startServer();
      String urlString = String.format(GSE_URL_FORMAT, port) + servletPath;
      HTTPRequest request =
          new HTTPRequest(new URL(urlString), HTTPMethod.GET, FetchOptions.Builder.withDeadline(5));
      SocketTimeoutException exception =
          assertThrows(SocketTimeoutException.class, () -> fetchService.fetch(request));
      assertThat(exception).hasMessageThat().isEqualTo("Timeout while fetching URL: " + urlString);
    } finally {
      cdl.countDown();
    }
  }

  @Test
  public void testBadURL() throws Exception {
    startServer();
    LocalRpcService.Status status = new LocalRpcService.Status();
    URLFetchRequest request =
        URLFetchRequest.newBuilder().setMethod(RequestMethod.GET).setUrl("a bad url").build();
    LocalURLFetchService localFetchService = new LocalURLFetchService();
    localFetchService.init(null, ImmutableMap.of());
    localFetchService.setTimeoutInMs(5000);
    ApiProxy.ApplicationException exception =
        assertThrows(
            ApiProxy.ApplicationException.class, () -> localFetchService.fetch(status, request));
    assertThat(exception.getApplicationError()).isEqualTo(ErrorCode.INVALID_URL.getNumber());
  }

  @Test
  public void testPostNoPayload() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/postTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath + "?val=yar"),
            HTTPMethod.POST,
            FetchOptions.Builder.doNotFollowRedirects());
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(servlet.params).hasSize(1);
    assertThat(servlet.params.get("val")).containsExactly("yar");
    verifyHeaders(response, servlet.receivedHeaders);
    // It's a bit unclear whether the status should be STATUS_TO_RETURN or 302 (Found), since the
    // POST handler does both setStatus and sendRedirect. So we allow it to be either.
    assertThat(response.getResponseCode())
        .isIn(ImmutableSet.of(TestServlet.STATUS_TO_RETURN, HttpServletResponse.SC_FOUND));
  }

  @Test
  public void testPostWithContentLength() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/postTest";
    addServlet(servletPath, servlet);
    startServer();
    LocalRpcService.Status status = new LocalRpcService.Status();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath + "?val=yar"),
            HTTPMethod.POST,
            FetchOptions.Builder.doNotFollowRedirects());
    addHeadersToSend(request);
    // Content length header should be stripped
    request.addHeader(new HTTPHeader("Content-Length", "100"));
    HTTPResponse response = fetchService.fetch(request);
    assertThat(status.getErrorCode()).isEqualTo(ErrorCode.OK.getNumber());
    assertThat(servlet.params).hasSize(1);
    assertThat(servlet.params.get("val")).containsExactly("yar");
    verifyHeaders(response, servlet.receivedHeaders);
    // It's a bit unclear whether the status should be STATUS_TO_RETURN or 302 (Found), since the
    // POST handler does both setStatus and sendRedirect. So we allow it to be either.
    assertThat(response.getResponseCode())
        .isIn(ImmutableSet.of(TestServlet.STATUS_TO_RETURN, HttpServletResponse.SC_FOUND));
  }

  @Test
  public void testRedirectAfterPost() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doPost(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader(
                "Location", String.format(GSE_URL_FORMAT, port) + "/redirectTest2");
            httpServletResponse.setStatus(301);
          }
        };
    String servletPath = "/redirectTest";
    TestServlet secondServlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String secondServletPath = "/redirectTest2";
    addServlet(servletPath, servlet);
    addServlet(secondServletPath, secondServlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.POST);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    verifyHeaders(response, secondServlet.receivedHeaders);
  }

  @Test
  public void testRedirect307AfterPost() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doPost(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader(
                "Location", String.format(GSE_URL_FORMAT, port) + "/redirectTest2");
            httpServletResponse.setStatus(307);
          }
        };
    String payload = "big ol' payload";
    String servletPath = "/redirectTest";
    TestServlet secondServlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String secondServletPath = "/redirectTest2";
    addServlet(servletPath, servlet);
    addServlet(secondServletPath, secondServlet);
    addServlet("/", secondServlet); // the redirected POST will re-redirect here
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.POST);
    request.setPayload(payload.getBytes(UTF_8));
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    verifyHeaders(response, secondServlet.receivedHeaders);
    verifyHeaders(
        ImmutableMap.of("Content-Type", "application/x-www-form-urlencoded"),
        secondServlet.receivedHeaders);
    // The payload is copied in case of POST and not GET in TestServlet which
    // ensures that POST happened in case of 307 redirect
    assertThat(secondServlet.payload).isEqualTo(payload);
  }

  @Test
  public void testPostWithPayload() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/postTest";
    addServlet(servletPath, servlet);
    startServer();
    LocalRpcService.Status status = new LocalRpcService.Status();
    String payload = "big ol' payload";
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath + "?val=yar"),
            HTTPMethod.POST,
            FetchOptions.Builder.doNotFollowRedirects());
    request.setPayload(payload.getBytes(UTF_8));
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(status.getErrorCode()).isEqualTo(ErrorCode.OK.getNumber());
    assertThat(servlet.params).containsEntry("val", ImmutableList.of("yar"));
    verifyHeaders(response, servlet.receivedHeaders);
    verifyHeaders(
        ImmutableMap.of("Content-Type", "application/x-www-form-urlencoded"),
        servlet.receivedHeaders);
    // It's a bit unclear whether the status should be STATUS_TO_RETURN or 302 (Found), since the
    // POST handler does both setStatus and sendRedirect. So we allow it to be either.
    assertThat(response.getResponseCode())
        .isIn(ImmutableSet.of(TestServlet.STATUS_TO_RETURN, HttpServletResponse.SC_FOUND));
    assertThat(servlet.payload).isEqualTo(payload);
  }

  @Test
  public void testHead() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/headTest";
    addServlet(servletPath, servlet);
    startServer();
    URL url = new URL(String.format(GSE_URL_FORMAT, port) + servletPath);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("HEAD");
    conn.disconnect();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.HEAD);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
  }

  @Test
  public void testDelete() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/deleteTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.DELETE);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
  }

  @Test
  public void testPut() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/putTest";
    addServlet(servletPath, servlet);
    startServer();
    String payload = "big ol' payload";
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath),
            HTTPMethod.PUT,
            FetchOptions.Builder.doNotFollowRedirects());
    request.setPayload(payload.getBytes(UTF_8));
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    assertThat(servlet.payload).isEqualTo(payload);
  }

  @Test
  public void testPatch() throws Exception {
    TestServlet servlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/patchTest";
    addServlet(servletPath, servlet);
    startServer();
    String payload = "big ol' payload";
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath),
            HTTPMethod.PATCH,
            FetchOptions.Builder.doNotFollowRedirects());
    request.setPayload(payload.getBytes(UTF_8));
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, servlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    assertThat(servlet.payload).isEqualTo(payload);
  }

  @Test
  public void testRedirects() throws Exception {
    final String redirectUrl = String.format(GSE_URL_FORMAT, port) + "/redirectTest2";
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", redirectUrl);
            httpServletResponse.setStatus(301);
          }
        };
    String servletPath = "/redirectTest";
    TestServlet secondServlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String secondServletPath = "/redirectTest2";
    addServlet(servletPath, servlet);
    addServlet(secondServletPath, secondServlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.GET);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, secondServlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    assertThat(response.getFinalUrl()).isEqualTo(new URL(redirectUrl));
  }

  @Test
  public void testRedirects_relativeRedirect() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest2");
            httpServletResponse.setStatus(301);
          }
        };
    String servletPath = "/redirectTest";
    TestServlet secondServlet = new TestServlet(getHeadersToExpect(), getClass().getName());
    String secondServletPath = "/redirectTest2";
    addServlet(servletPath, servlet);
    addServlet(secondServletPath, secondServlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.GET);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    verifyHeaders(response, secondServlet.receivedHeaders);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
  }

  @Test
  public void testRedirects_tooMany() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest2");
            httpServletResponse.setStatus(301);
          }
        };
    HttpServlet servlet2 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest3");
            httpServletResponse.setStatus(302);
          }
        };
    HttpServlet servlet3 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest4");
            httpServletResponse.setStatus(303);
          }
        };
    HttpServlet servlet4 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest5");
            httpServletResponse.setStatus(307);
          }
        };
    HttpServlet servlet5 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest6");
            httpServletResponse.setStatus(301);
          }
        };
    HttpServlet servlet6 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest7");
            httpServletResponse.setStatus(301);
          }
        };
    String servletPath = "/redirectTest";
    String servletPath2 = "/redirectTest2";
    String servletPath3 = "/redirectTest3";
    String servletPath4 = "/redirectTest4";
    String servletPath5 = "/redirectTest5";
    String servletPath6 = "/redirectTest6";
    addServlet(servletPath, servlet);
    addServlet(servletPath2, servlet2);
    addServlet(servletPath3, servlet3);
    addServlet(servletPath4, servlet4);
    addServlet(servletPath5, servlet5);
    addServlet(servletPath6, servlet6);
    startServer();
    String urlString = String.format(GSE_URL_FORMAT, port) + servletPath;
    HTTPRequest request = new HTTPRequest(new URL(urlString), HTTPMethod.GET);
    addHeadersToSend(request);
    IOException exception = assertThrows(IOException.class, () -> fetchService.fetch(request));
    assertThat(exception).hasMessageThat().startsWith("Too many redirects at URL: " + urlString);
  }

  @Test
  public void testRedirects_maxAllowed() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest2");
            httpServletResponse.setStatus(301);
          }
        };
    HttpServlet servlet2 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest3");
            httpServletResponse.setStatus(302);
          }
        };
    HttpServlet servlet3 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest4");
            httpServletResponse.setStatus(303);
          }
        };
    HttpServlet servlet4 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest5");
            httpServletResponse.setStatus(307);
          }
        };
    HttpServlet servlet5 =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest6");
            httpServletResponse.setStatus(301);
          }
        };
    TestServlet servlet6 = new TestServlet(getHeadersToExpect(), getClass().getName());
    String servletPath = "/redirectTest";
    String servletPath2 = "/redirectTest2";
    String servletPath3 = "/redirectTest3";
    String servletPath4 = "/redirectTest4";
    String servletPath5 = "/redirectTest5";
    String servletPath6 = "/redirectTest6";
    addServlet(servletPath, servlet);
    addServlet(servletPath2, servlet2);
    addServlet(servletPath3, servlet3);
    addServlet(servletPath4, servlet4);
    addServlet(servletPath5, servlet5);
    addServlet(servletPath6, servlet6);
    startServer();
    HTTPRequest request =
        new HTTPRequest(new URL(String.format(GSE_URL_FORMAT, port) + servletPath), HTTPMethod.GET);
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(getClass().getName());
    verifyHeaders(response, servlet6.receivedHeaders);
  }

  @Test
  public void testRedirects_noLocation() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setStatus(302);
          }
        };
    String servletPath = "/redirectTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath),
            HTTPMethod.GET,
            FetchOptions.Builder.followRedirects());
    addHeadersToSend(request);
    IOException exception = assertThrows(IOException.class, () -> fetchService.fetch(request));
    assertThat(exception).hasMessageThat().contains("Malformed HTTP reply received from server");
    assertThat(exception).hasMessageThat().contains("Missing \"Location\" header for redirect.");
  }

  @Test
  public void testRedirects_notFollow() throws Exception {
    HttpServlet servlet =
        new HttpServlet() {
          @Override
          protected void doGet(
              HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            httpServletResponse.setHeader("Location", "/redirectTest2");
            httpServletResponse.setStatus(301);
          }
        };
    String servletPath = "/redirectTest";
    addServlet(servletPath, servlet);
    startServer();
    HTTPRequest request =
        new HTTPRequest(
            new URL(String.format(GSE_URL_FORMAT, port) + servletPath),
            HTTPMethod.GET,
            FetchOptions.Builder.doNotFollowRedirects());
    addHeadersToSend(request);
    HTTPResponse response = fetchService.fetch(request);
    assertThat(response.getResponseCode()).isEqualTo(301);
    Optional<String> location =
        response.getHeaders().stream()
            .filter(h -> Ascii.equalsIgnoreCase(h.getName(), "location"))
            .map(HTTPHeader::getValue)
            .findFirst();
    assertThat(location).hasValue("/redirectTest2");
  }

  @Test
  public void testFetchNonExistentSite() throws Exception {
    String urlString = "http://i.do.not.exist/";
    HTTPRequest request = new HTTPRequest(new URL(urlString), HTTPMethod.GET);
    addHeadersToSend(request);
    IOException exception = assertThrows(IOException.class, () -> fetchService.fetch(request));
    assertThat(exception).hasMessageThat().startsWith("Could not fetch URL: " + urlString);
  }

  void setHttpProxy(String proxyHost, int proxyPort) {
    System.setProperty("http.proxyHost", proxyHost);
    System.setProperty("http.proxyPort", Integer.toString(proxyPort));
  }

  @Test
  public void testHttpProxy() throws Exception {
    try {
      TestServlet servlet = new TestServlet(new HashMap<String, String>(), getClass().getName());
      addServlet("/", servlet);
      startServer();

      setHttpProxy("localhost", port);

      String urlString = "http://i.do.not.exist/";
      HTTPResponse response =
          fetchService.fetch(new HTTPRequest(new URL(urlString), HTTPMethod.GET));
      assertThat(response.getResponseCode()).isEqualTo(TestServlet.STATUS_TO_RETURN);
    } finally {
      // Manually clear system properties to isolate this test case and prevent them from being read
      // by other test cases
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
    }
  }

  private void addHeadersToSend(HTTPRequest request) {
    getHeadersToSend().forEach((name, value) -> request.addHeader(new HTTPHeader(name, value)));
    // Increase the deadline from the default 5 seconds to prevent occasional flakiness.
    request.getFetchOptions().setDeadline(20.0);
  }

  private ImmutableMap<String, String> getHeadersToExpect() {
    return ImmutableMap.of("yar", "yam", "bar", "bam");
  }

  private ImmutableMap<String, String> getHeadersToSend() {
    return ImmutableMap.of("key1", "val1", "key2", "val2");
  }

  private void verifyHeaders(HTTPResponse response, Map<String, String> receivedHeaders) {
    verifyHeaders(getHeadersToExpect(), response);
    verifyHeaders(getHeadersToSend(), receivedHeaders);
  }

  private void verifyHeaders(Map<String, String> expected, HTTPResponse response) {
    Map<String, String> responseHeaderMap =
        response.getHeaders().stream().collect(toMap(HTTPHeader::getName, HTTPHeader::getValue));
    verifyHeaders(expected, responseHeaderMap);
  }

  private void verifyHeaders(Map<String, String> expected, Map<String, String> actual) {
    assertThat(actual).containsAtLeastEntriesIn(expected);
  }

  private void startServer() throws Exception {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        server.start();
        return;
      } catch (Exception e) {
        if (i == MAX_RETRIES - 1) {
          throw e;
        }
        logger.atWarning().withCause(e).log("Server start failed on port %d, retrying", port);
        // Retry with a new port
        port = portPicker.pickUnusedPort();
        server = new Server(port);
        server.setHandler(servletHandler);
      }
    }
  }

  private void addServlet(String servletPath, HttpServlet servlet) {
    servletHandler.addServletWithMapping(new ServletHolder(servlet), servletPath);
  }

  private static final class TestServlet extends HttpServlet {

    private static final String METHOD_PATCH = "PATCH";

    private static final int STATUS_TO_RETURN = 418; // Arbitrary response code for testing.

    private final Map<String, List<String>> params = new HashMap<>();
    private final String doGetResponse;
    private final Map<String, String> receivedHeaders = new HashMap<>();
    private final Map<String, String> headersToReturn;
    private String payload;

    public TestServlet(Map<String, String> headersToReturn, String doGetResponse) {
      this.headersToReturn = headersToReturn;
      this.doGetResponse = doGetResponse;
    }

    @Override
    protected void service(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      String method = httpServletRequest.getMethod();
      // By default, HttpServlet only supports GET,POST,PUT,DELETE,TRACE,HEAD and OPTIONS
      // When serving a request, it checks a method and if the method matches one of these
      // it will call do{HTTP_METHOD_NAME}. Here, we first check if a patch request and call
      // doPatch if this is the case, otherwise we let the method from the superclass (the
      // default provided by HttpServlet) handle the request. It is safe here to call doPatch
      // since this specific sublass defines doPatch.
      if (method.equals(METHOD_PATCH)) {
        doPatch(httpServletRequest, httpServletResponse);
      } else {
        super.service(httpServletRequest, httpServletResponse);
      }
    }

    @Override
    protected void doPost(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      payload = extractPayload(httpServletRequest);
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
      httpServletRequest.getParameterMap().forEach((k, v) -> params.put(k, Arrays.asList(v)));
      httpServletResponse.sendRedirect("/");
      httpServletResponse.setStatus(STATUS_TO_RETURN);
    }

    @Override
    protected void doGet(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
      try (PrintWriter writer = httpServletResponse.getWriter()) {
        writer.print(doGetResponse);
      }
    }

    @Override
    protected void doHead(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
      httpServletResponse.flushBuffer();
    }

    @Override
    protected void doPut(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
      payload = extractPayload(httpServletRequest);
    }

    void doPatch(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
      payload = extractPayload(httpServletRequest);
    }

    @Override
    protected void doDelete(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      manageHeadersAndStatus(httpServletRequest, httpServletResponse);
    }

    private void manageHeadersAndStatus(HttpServletRequest request, HttpServletResponse response) {
      addReceivedHeaders(request);
      // Jetty apparently needs setStatus to happen before calling response.setHeader. Otherwise
      // it overwrites the status with 200.
      response.setStatus(STATUS_TO_RETURN);
      addHeadersToReturn(response);
    }

    private static String extractPayload(HttpServletRequest request) throws IOException {
      try (BufferedReader br = new BufferedReader(request.getReader())) {
        return CharStreams.toString(br);
      }
    }

    private void addHeadersToReturn(HttpServletResponse response) {
      headersToReturn.forEach(response::setHeader);
      // GSE complains a lot if there isn't a content type header.
      response.setHeader("Content-Type", "text/html");
    }

    private void addReceivedHeaders(HttpServletRequest request) {
      for (String headerName : Collections.list(request.getHeaderNames())) {
        String headerVal = request.getHeader(headerName);
        receivedHeaders.put(headerName, headerVal);
      }
    }
  }
}
