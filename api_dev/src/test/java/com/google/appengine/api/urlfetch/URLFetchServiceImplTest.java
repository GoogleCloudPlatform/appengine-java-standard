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

package com.google.appengine.api.urlfetch;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.allowTruncate;
import static com.google.appengine.api.urlfetch.FetchOptions.Builder.disallowTruncate;
import static com.google.appengine.api.urlfetch.FetchOptions.Builder.doNotFollowRedirects;
import static com.google.appengine.api.urlfetch.FetchOptions.Builder.followRedirects;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchResponse;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchServiceError.ErrorCode;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalURLFetchServiceTestConfig;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.testing.PortPicker;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee8.servlet.ServletHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the URLFetchServiceImpl class.
 *
 */
@RunWith(JUnit4.class)
public class URLFetchServiceImplTest {
  private static class EnvHasCorrectDeadline implements ArgumentMatcher<ApiProxy.Environment> {
    private final ApiProxy.Environment expectedEnvironment;
    private final Double expectedDeadline;
    private static final Logger log = Logger.getLogger(EnvHasCorrectDeadline.class.getName());

    EnvHasCorrectDeadline(Environment expectedEnvironment, Double expectedDeadline) {
      this.expectedEnvironment = expectedEnvironment;
      this.expectedDeadline = expectedDeadline;
    }

    @Override
    public boolean matches(ApiProxy.Environment actual) {
      if (actual != expectedEnvironment) {
        return false;
      }
      Double actualDeadline = (Double) actual
          .getAttributes()
          .get("com.google.apphosting.api.ApiProxy.api_deadline_key");

      if (!Objects.equals(expectedDeadline, actualDeadline)) {
        log.info("actualDeadline = " + actualDeadline + ", expectedDeadline = " + expectedDeadline);
        return false;
      }
      return true;
    }
  }

  private static ApiProxy.Environment hasCorrectDeadline(
      ApiProxy.Environment expectedEnvironment, Double expectedDeadline) {
    return argThat(new EnvHasCorrectDeadline(expectedEnvironment, expectedDeadline));
  }

  private final LocalServiceTestHelper testHelper = new LocalServiceTestHelper();

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;

  @Before
  public void setUp() {
    testHelper.setUp();
    MockitoAnnotations.initMocks(this);
    ApiProxy.setDelegate(delegate);
  }

  @After
  public void tearDown() {
    testHelper.tearDown();
    ApiProxy.setDelegate(null);
  }

  @Test
  public void testSync_URLOnly() throws Exception {
    URL url = new URL("http://google.com/foo");
    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(url);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_SimpleGET() throws Exception {
    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
    assertThat(response.getFinalUrl()).isNull();
  }

  @Test
  public void testSync_RequestHeaders() throws Exception {
    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url);
    request.addHeader(new HTTPHeader("Test-Header", "Request"));

    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .addHeader(URLFetchRequest.Header.newBuilder()
                   .setKey("Test-Header")
                   .setValue("Request"))
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_ResponseHeadersCombined() throws Exception {
    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .addHeader(
                URLFetchResponse.Header.newBuilder().setKey("Test-Header").setValue("Response1"))
            .addHeader(
                URLFetchResponse.Header.newBuilder().setKey("Test-Header").setValue("Response2"))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);

    assertThat(response.getHeaders()).hasSize(1);
    assertThat(response.getHeaders().get(0).getName()).isEqualTo("Test-Header");
    assertThat(response.getHeaders().get(0).getValue()).isEqualTo("Response1, Response2");
  }

  @Test
  public void testSync_ResponseHeadersUncombined() throws Exception {
    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .addHeader(
                URLFetchResponse.Header.newBuilder().setKey("Test-Header").setValue("Response1"))
            .addHeader(
                URLFetchResponse.Header.newBuilder().setKey("Test-Header").setValue("Response2"))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);

    assertThat(response.getHeadersUncombined()).hasSize(2);
    assertThat(response.getHeadersUncombined().get(0).getName()).isEqualTo("Test-Header");
    assertThat(response.getHeadersUncombined().get(0).getValue()).isEqualTo("Response1");
    assertThat(response.getHeadersUncombined().get(1).getName()).isEqualTo("Test-Header");
    assertThat(response.getHeadersUncombined().get(1).getValue()).isEqualTo("Response2");
  }

  @Test
  public void testSync_SimplePOST() throws Exception {
    String requestContent = "this=is&post=content";
    String responseContent = "<p>This is the desired response.</p>";

    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.POST);
    request.setPayload(requestContent.getBytes(UTF_8));

    URLFetchRequest requestProto =
        URLFetchRequest.newBuilder()
            .setUrl(url.toString())
            .setMethod(RequestMethod.POST)
            .setPayload(ByteString.copyFromUtf8(requestContent))
            .setFollowRedirects(true)
            .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_SimplePUT() throws Exception {
    String requestContent = "this=is&put=content";
    String responseContent = "<p>This is the desired response.</p>";

    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.PUT);
    request.setPayload(requestContent.getBytes(UTF_8));

    URLFetchRequest requestProto =
        URLFetchRequest.newBuilder()
            .setUrl(url.toString())
            .setMethod(RequestMethod.PUT)
            .setPayload(ByteString.copyFromUtf8(requestContent))
            .setFollowRedirects(true)
            .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_SimpleDELETE() throws Exception {
    String responseContent = "<p>This is the desired response.</p>";

    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.DELETE);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.DELETE)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_SimpleHEAD() throws Exception {
    URL url = new URL("http://google.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.HEAD);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.HEAD)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto = URLFetchResponse.newBuilder()
        .setStatusCode(200)
        .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    verify(delegate).makeSyncCall(any(), any(), any(), any());
  }

  @Test
  public void testSync_CannotConnect() throws Exception {
    URL url = new URL("http://non-existent-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(ErrorCode.FETCH_ERROR.getNumber(), errorDetails));

    IOException ex =
        assertThrows(IOException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().isEqualTo(
        "Could not fetch URL: http://non-existent-domain.com/foo, error: " + errorDetails);
  }

  @Test
  public void testSync_Timeout() throws Exception {
    URL url = new URL("http://slow-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                ErrorCode.DEADLINE_EXCEEDED.getNumber(), errorDetails));

    SocketTimeoutException ex =
        assertThrows(SocketTimeoutException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat()
        .isEqualTo("Timeout while fetching URL: http://slow-domain.com/foo");
  }

  @Test
  public void testSync_Timeout2() throws Exception {
    URL url = new URL("http://slow-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(new ApiProxy.ApiDeadlineExceededException("urlfetch", "fetch"));

    SocketTimeoutException ex =
        assertThrows(SocketTimeoutException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_TooManyRedirectsException() throws Exception {
    URL url = new URL("http://toomanyredirects.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                ErrorCode.TOO_MANY_REDIRECTS.getNumber(), errorDetails));

    IOException ex =
        assertThrows(IOException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_DnsLookupFailedException() throws Exception {
    URL url = new URL("http://dnsfailure.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(ErrorCode.DNS_ERROR.getNumber(), errorDetails));

    UnknownHostException ex =
        assertThrows(UnknownHostException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_MalformedReplyException() throws Exception {
    URL url = new URL("http://badhttpreply.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(ErrorCode.MALFORMED_REPLY.getNumber(), errorDetails));

    IOException ex =
        assertThrows(IOException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_InvalidURLException() throws Exception {
    URL url = new URL("http://badrequest.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(ErrorCode.INVALID_URL.getNumber(), errorDetails));

    MalformedURLException ex =
        assertThrows(MalformedURLException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_ConnectionClosedException() throws Exception {
    URL url = new URL("http://connectionclosed.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(new ApiProxy.ApplicationException(ErrorCode.CLOSED.getNumber(), errorDetails));

    IOException ex =
        assertThrows(IOException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_InternalTransientError() throws Exception {
    URL url = new URL("http://internaltransienterror.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                ErrorCode.INTERNAL_TRANSIENT_ERROR.getNumber(), errorDetails));

    InternalTransientException ex =
        assertThrows(
            InternalTransientException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_ResponseTooLarge() throws Exception {
    URL url = new URL("http://toomuchdata.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                ErrorCode.RESPONSE_TOO_LARGE.getNumber(), errorDetails));

    ResponseTooLargeException ex =
        assertThrows(ResponseTooLargeException.class, () -> new URLFetchServiceImpl().fetch(
            request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_UnknownException() throws Exception {
    URL url = new URL("http://wtf.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    when(delegate.makeSyncCall(any(), any(), any(), any()))
        .thenThrow(
            new ApiProxy.UnknownException("something bad happened"));

    IOException ex =
        assertThrows(IOException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains("something bad happened");
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.UnknownException.class);
  }

  @Test
  public void testSync_TruncateResponse_AllowTruncate() throws Exception {
    URL url = new URL("http://non-existent-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET, allowTruncate());

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto = URLFetchResponse.newBuilder()
        .setStatusCode(200)
        .setContentWasTruncated(true)
        .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    verify(delegate).makeSyncCall(any(), any(), any(), any());
  }

  @Test
  public void testSync_TruncateResponse_DisallowTruncate() throws Exception {
    URL url = new URL("http://non-existent-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET, disallowTruncate());

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto = URLFetchResponse.newBuilder()
        .setStatusCode(200)
        .setContentWasTruncated(true)
        .build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());
    ResponseTooLargeException ex =
        assertThrows(
            ResponseTooLargeException.class, () -> new URLFetchServiceImpl().fetch(request));
    assertThat(ex).hasMessageThat().contains(url.toString());
  }

  @Test
  public void testSync_Redirects_followRedirects() throws Exception {
    URL url = new URL("http://non-existent-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET, followRedirects());

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto = URLFetchResponse.newBuilder()
        .setStatusCode(200)
        .setFinalUrl("http://fancytown.example.com")
        .build();
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());
    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(200);
    // Avoid URL.equals, which will try to resolve the hostname in the URL.
    assertThat(response.getFinalUrl().toString())
        .isEqualTo(new URL("http://fancytown.example.com").toString());
  }

  @Test
  public void testSync_Redirects_doNotFollowRedirects() throws Exception {
    URL url = new URL("http://non-existent-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET, doNotFollowRedirects());

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(false)
        .build();

    URLFetchResponse responseProto = URLFetchResponse.newBuilder()
        .setStatusCode(302)
        .build();
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());
    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(response.getResponseCode()).isEqualTo(302);
    verify(delegate).makeSyncCall(any(), any(), any(), any());
  }

  private void deadlineMatches(Double expectedDeadline, FetchOptions options) throws Exception {
    URL url = new URL("http://www.google.com");
    HTTPRequest request;
    if (options == null) {
      request = new HTTPRequest(url, HTTPMethod.GET);
    } else {
      request = new HTTPRequest(url, HTTPMethod.GET, options);
    }

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String responseContent = "<p>This is the desired response.</p>";
    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeSyncCall(
            hasCorrectDeadline(ApiProxy.getCurrentEnvironment(), expectedDeadline),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    URLFetchServiceImpl.DeadlineParser.INSTANCE.refresh();
    
    HTTPResponse response = new URLFetchServiceImpl().fetch(request);

    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testSync_DefaultTimeout() throws Exception {
    try {
      // Test default value:
      assertThat(FetchOptions.Builder.withDefaults().getDeadline()).isNull();

      // Set new default value:
      assertThat(System.setProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY, "60.0")).isNull();
      deadlineMatches(60.0, null);

      // Test that withDeadline() overrides global default:
      FetchOptions options = FetchOptions.Builder.withDeadline(30.0);
      deadlineMatches(30.0, options);

      // Test that other threads see global default deadline after calling
      // withDeadline() locally:
      final ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
      Thread t =
          new Thread(
              () -> {
                ApiProxy.setEnvironmentForCurrentThread(env);
                try {
                  deadlineMatches(60.0, null);
                } catch (Exception ex) {
                  throw new RuntimeException("unexpected exception: " + ex);
                }
              });
      t.start();
      t.join();

      // Default still enforced:
      deadlineMatches(60.0, null);
    } finally {
      // Reset default:
      System.clearProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY);
    }

    deadlineMatches(null, null);
  }

  @Test
  public void testSync_DefaultTimeoutBogusValues() throws Exception {
    try {
      // Test that bogus property values get ignored
      assertThat(System.setProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY, "foobar")).isNull();
      deadlineMatches(null, null);
    } finally {
      // Reset default:
      System.clearProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY);
    }
  }

  @Test
  public void testAsync_URLOnly() throws Exception {
    URL url = new URL("http://google.com/foo");
    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray()),
            any()))
        .thenReturn(Futures.immediateFuture(responseProto.toByteArray()));

    Future<HTTPResponse> responseFuture = new URLFetchServiceImpl().fetchAsync(url);
    HTTPResponse response = responseFuture.get();

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testAsync_SimpleGet() throws Exception {
    URL url = new URL("http://google.com/foo");
    String responseContent = "<p>This is the desired response.</p>";

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    URLFetchResponse responseProto =
        URLFetchResponse.newBuilder()
            .setStatusCode(200)
            .setContent(ByteString.copyFromUtf8(responseContent))
            .build();

    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray()),
            any()))
        .thenReturn(Futures.immediateFuture(responseProto.toByteArray()));

    Future<HTTPResponse> responseFuture = new URLFetchServiceImpl().fetchAsync(
        new HTTPRequest(url));
    HTTPResponse response = responseFuture.get();

    assertThat(response.getResponseCode()).isEqualTo(200);
    assertThat(new String(response.getContent(), UTF_8)).isEqualTo(responseContent);
  }

  @Test
  public void testAsync_Timeout() throws Exception {
    URL url = new URL("http://slow-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    String errorDetails = "details";
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray()),
            any()))
        .thenReturn(
            Futures.immediateFailedFuture(
                new ApiProxy.ApplicationException(
                    ErrorCode.DEADLINE_EXCEEDED.getNumber(), errorDetails)));

    Future<HTTPResponse> response = new URLFetchServiceImpl().fetchAsync(request);
    ExecutionException ex = assertThrows(ExecutionException.class, response::get);
    assertThat(ex).hasCauseThat().isInstanceOf(SocketTimeoutException.class);
    assertThat(ex).hasCauseThat().hasMessageThat()
        .isEqualTo("Timeout while fetching URL: http://slow-domain.com/foo");
  }

  @Test
  public void testAsync_Timeout2() throws Exception {
    URL url = new URL("http://slow-domain.com/foo");
    HTTPRequest request = new HTTPRequest(url);

    URLFetchRequest requestProto = URLFetchRequest.newBuilder()
        .setUrl(url.toString())
        .setMethod(RequestMethod.GET)
        .setFollowRedirects(true)
        .build();

    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(URLFetchServiceImpl.PACKAGE),
            eq("Fetch"),
            eq(requestProto.toByteArray()),
            any()))
        .thenReturn(
            Futures.immediateFailedFuture(
                new ApiProxy.ApiDeadlineExceededException("urlfetch", "fetch")));

    Future<HTTPResponse> response = new URLFetchServiceImpl().fetchAsync(request);
    ExecutionException ex = assertThrows(ExecutionException.class, response::get);
    assertThat(ex).hasCauseThat().isInstanceOf(SocketTimeoutException.class);
    assertThat(ex).hasCauseThat().hasMessageThat()
        .isEqualTo("Timeout while fetching URL: http://slow-domain.com/foo");
  }

  @Test
  public void testCookieReuse() throws Exception {
    final AtomicBoolean receivedCookie = new AtomicBoolean();

    new LocalServiceTestHelper(new LocalURLFetchServiceTestConfig()).setUp();

    int port = PortPicker.create().pickUnusedPort();
    Server server = new Server(port);
    ServletHandler handler = new ServletHandler();

    ServletContextHandler contextHandler = new ServletContextHandler();
    contextHandler.setServletHandler(handler);
    server.setHandler(contextHandler);

    HttpServlet setCookieServlet = new HttpServlet() {
      @Override
      public void doGet(HttpServletRequest req, HttpServletResponse resp) {
        Cookie cookie = new Cookie("foo", "bar");
        resp.addCookie(cookie);
        resp.setHeader("Content-Type", "text/txt");
        resp.setStatus(200);
      }
    };
    handler.addServletWithMapping(new ServletHolder(setCookieServlet), "/setcookie");

    HttpServlet getCookieServlet = new HttpServlet() {
      @Override
      public void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getCookies() != null) {
          for (Cookie cookie : req.getCookies()) {
            if (cookie.getName().equals("foo")) {
              receivedCookie.set(true);
            }
          }
        }
        resp.setHeader("Content-Type", "text/txt");
      }
    };
    handler.addServletWithMapping(new ServletHolder(getCookieServlet), "/getcookie");
    server.start();

    try {
      URLFetchService service = URLFetchServiceFactory.getURLFetchService();
      service.fetch(new URL("http://localhost:" + port + "/setcookie"));
      service.fetch(new URL("http://localhost:" + port + "/getcookie"));
      assertWithMessage("Cookie should not be set in second request")
          .that(receivedCookie.get()).isFalse();
    } finally {
      server.stop();
      server.join();
    }
  }

  @Test
  public void testTlsNotValidatedByDefault() throws Exception {
    // If the special system property is not set, then we don't set the
    // MustValidateServerCertificate field in URLFetchRequest. The App Server applies its own
    // defaulting logic in that case
    // http://google3/apphosting/api/urlfetch/urlfetch_harpoon.cc?l=875&rcl=270212075
    // meaning that (at the time of writing) it does not in fact do certificate validation, even
    // though the default value for the field is true as expressed in its proto definition.
    checkTlsValidation(
        FetchOptions.Builder.withDefaults(),
        urlFetchRequest ->
            assertThat(urlFetchRequest.hasMustValidateServerCertificate()).isFalse());
  }

  @Test
  public void testTlsIsValidatedIfPropertySet() throws Exception {
    assertThat(System.getProperty(URLFetchService.DEFAULT_TLS_VALIDATION_PROPERTY)).isNull();
    try {
      System.setProperty(URLFetchServiceImpl.DEFAULT_TLS_VALIDATION_PROPERTY, "true");
      checkTlsValidation(
          FetchOptions.Builder.withDefaults(),
          urlFetchRequest -> {
            assertThat(urlFetchRequest.hasMustValidateServerCertificate()).isTrue();
            assertThat(urlFetchRequest.getMustValidateServerCertificate()).isTrue();
          });
    } finally {
      System.clearProperty(URLFetchService.DEFAULT_TLS_VALIDATION_PROPERTY);
    }
  }

  @Test
  public void testTlsNotValidatedIfPropertySetButExplicitlyCountered() throws Exception {
    assertThat(System.getProperty(URLFetchService.DEFAULT_TLS_VALIDATION_PROPERTY)).isNull();
    try {
      System.setProperty(URLFetchServiceImpl.DEFAULT_TLS_VALIDATION_PROPERTY, "true");
      checkTlsValidation(
          FetchOptions.Builder.withDefaults().doNotValidateCertificate(),
          urlFetchRequest -> {
            assertThat(urlFetchRequest.hasMustValidateServerCertificate()).isTrue();
            assertThat(urlFetchRequest.getMustValidateServerCertificate()).isFalse();
          });
    } finally {
      System.clearProperty(URLFetchService.DEFAULT_TLS_VALIDATION_PROPERTY);
    }
  }

  private void checkTlsValidation(FetchOptions fetchOptions, Consumer<URLFetchRequest> check)
      throws Exception {
    URL url = new URL("https://validate.me/");
    HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET, fetchOptions);
    URLFetchResponse urlFetchResponse = URLFetchResponse.newBuilder().setStatusCode(200).build();
    when(delegate.makeSyncCall(any(), any(), any(), any()))
        .thenReturn(urlFetchResponse.toByteArray());

    new URLFetchServiceImpl().fetch(request);

    ArgumentCaptor<byte[]> requestProtoCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(delegate)
        .makeSyncCall(
            any(), eq(URLFetchServiceImpl.PACKAGE), eq("Fetch"), requestProtoCaptor.capture());
    URLFetchRequest urlFetchRequest =
        URLFetchRequest.parseFrom(
            requestProtoCaptor.getValue(), ExtensionRegistry.getGeneratedRegistry());
    check.accept(urlFetchRequest);
  }
}
