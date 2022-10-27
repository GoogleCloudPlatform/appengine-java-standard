/*
 * Copyright 2022 Google LLC
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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.TraceId.TraceIdProto;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public final class UPRequestTranslatorTest {
  private static final String X_APPENGINE_HTTPS = "X-AppEngine-Https";
  private static final String X_APPENGINE_USER_IP = "X-AppEngine-User-IP";
  private static final String X_APPENGINE_USER_EMAIL = "X-AppEngine-User-Email";
  private static final String X_APPENGINE_AUTH_DOMAIN = "X-AppEngine-Auth-Domain";
  private static final String X_APPENGINE_USER_ID = "X-AppEngine-User-Id";
  private static final String X_APPENGINE_USER_NICKNAME = "X-AppEngine-User-Nickname";
  private static final String X_APPENGINE_USER_ORGANIZATION = "X-AppEngine-User-Organization";
  private static final String X_APPENGINE_USER_IS_ADMIN = "X-AppEngine-User-Is-Admin";
  private static final String X_APPENGINE_TRUSTED_IP_REQUEST = "X-AppEngine-Trusted-IP-Request";
  private static final String X_APPENGINE_LOAS_PEER_USERNAME = "X-AppEngine-LOAS-Peer-Username";
  private static final String X_APPENGINE_GAIA_ID = "X-AppEngine-Gaia-Id";
  private static final String X_APPENGINE_GAIA_AUTHUSER = "X-AppEngine-Gaia-Authuser";
  private static final String X_APPENGINE_GAIA_SESSION = "X-AppEngine-Gaia-Session";
  private static final String X_APPENGINE_APPSERVER_DATACENTER = "X-AppEngine-Appserver-Datacenter";
  private static final String X_APPENGINE_APPSERVER_TASK_BNS = "X-AppEngine-Appserver-Task-Bns";
  private static final String X_APPENGINE_DEFAULT_VERSION_HOSTNAME =
      "X-AppEngine-Default-Version-Hostname";
  private static final String X_APPENGINE_REQUEST_LOG_ID = "X-AppEngine-Request-Log-Id";
  private static final String X_APPENGINE_QUEUENAME = "X-AppEngine-QueueName";
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "X-Google-Internal-SkipAdminCheck";
  private static final String X_CLOUD_TRACE_CONTEXT = "X-Cloud-Trace-Context";
  private static final String X_APPENGINE_TIMEOUT_MS = "X-AppEngine-Timeout-Ms";

  UPRequestTranslator translator;

  @Before
  public void setUp() throws Exception {
    ImmutableMap<String, String> fakeEnv =
        ImmutableMap.of(
            "GAE_VERSION", "3.14",
            "GOOGLE_CLOUD_PROJECT", "mytestappid",
            "GAE_APPLICATION", "s~mytestappid",
            "GAE_SERVICE", "mytestservice");

    translator =
        new UPRequestTranslator(
            new AppInfoFactory(fakeEnv),
            /*passThroughPrivateHeaders=*/ false,
            /*skipPostData=*/ false);
  }

  @Test
  public void translateWithoutAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.of("testheader", "testvalue"));

    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);

    HttpPb.HttpRequest httpRequestPb = translatedUpRequest.getRequest();
    assertThat(httpRequestPb.getHttpVersion()).isEqualTo("HTTP/1.0");
    assertThat(httpRequestPb.getIsHttps()).isFalse();
    assertThat(httpRequestPb.getProtocol()).isEqualTo("GET");
    assertThat(httpRequestPb.getUserIp()).isEqualTo("127.0.0.1");
    assertThat(httpRequestPb.getIsOffline()).isFalse();
    assertThat(httpRequestPb.getUrl()).isEqualTo("http://myapp.appspot.com:80/foo/bar?a=b");
    assertThat(httpRequestPb.getHeadersList()).hasSize(2);
    for (ParsedHttpHeader header : httpRequestPb.getHeadersList()) {
      assertThat(header.getKey()).isAnyOf("testheader", "host");
      assertThat(header.getValue()).isAnyOf("testvalue", "myapp.appspot.com");
    }

    assertThat(translatedUpRequest.getAppId()).isEqualTo("s~mytestappid");
    assertThat(translatedUpRequest.getVersionId()).isEqualTo("mytestservice:3.14");
    assertThat(translatedUpRequest.getModuleId()).isEqualTo("mytestservice");
    assertThat(translatedUpRequest.getModuleVersionId()).isEqualTo("3.14");
    assertThat(translatedUpRequest.getSecurityTicket()).isEqualTo("secretkey");
    assertThat(translatedUpRequest.getNickname()).isEmpty();
    assertThat(translatedUpRequest.getEmail()).isEmpty();
    assertThat(translatedUpRequest.getUserOrganization()).isEmpty();
    assertThat(translatedUpRequest.getIsAdmin()).isFalse();
    assertThat(translatedUpRequest.getPeerUsername()).isEmpty();
    assertThat(translatedUpRequest.getAppserverDatacenter()).isEmpty();
    assertThat(translatedUpRequest.getAppserverTaskBns()).isEmpty();
  }

  private static final ImmutableMap<String, String> BASE_APPENGINE_HEADERS =
      ImmutableMap.<String, String>builder()
          .put(X_APPENGINE_USER_NICKNAME, "anickname")
          .put(X_APPENGINE_USER_IP, "auserip")
          .put(X_APPENGINE_USER_EMAIL, "ausermail")
          .put(X_APPENGINE_AUTH_DOMAIN, "aauthdomain")
          .put(X_APPENGINE_USER_ID, "auserid")
          .put(X_APPENGINE_USER_ORGANIZATION, "auserorg")
          .put(X_APPENGINE_USER_IS_ADMIN, "false")
          .put(X_APPENGINE_TRUSTED_IP_REQUEST, "atrustedip")
          .put(X_APPENGINE_LOAS_PEER_USERNAME, "aloasname")
          .put(X_APPENGINE_GAIA_ID, "3142406")
          .put(X_APPENGINE_GAIA_AUTHUSER, "aauthuser")
          .put(X_APPENGINE_GAIA_SESSION, "agaiasession")
          .put(X_APPENGINE_APPSERVER_DATACENTER, "adatacenter")
          .put(X_APPENGINE_APPSERVER_TASK_BNS, "ataskbns")
          .put(X_APPENGINE_HTTPS, "on")
          .put(X_APPENGINE_DEFAULT_VERSION_HOSTNAME, "foo.appspot.com")
          .put(X_APPENGINE_REQUEST_LOG_ID, "logid")
          .put(X_APPENGINE_TIMEOUT_MS, "20000")
          .buildOrThrow();

  @Test
  public void translateWithAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b", "127.0.0.1", BASE_APPENGINE_HEADERS);

    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);

    HttpPb.HttpRequest httpRequestPb = translatedUpRequest.getRequest();
    assertThat(httpRequestPb.getHttpVersion()).isEqualTo("HTTP/1.0");
    assertThat(httpRequestPb.getIsHttps()).isTrue();
    assertThat(httpRequestPb.getProtocol()).isEqualTo("GET");
    assertThat(httpRequestPb.getUserIp()).isEqualTo("auserip");
    assertThat(httpRequestPb.getUrl()).isEqualTo("http://myapp.appspot.com:80/foo/bar?a=b");
    assertThat(httpRequestPb.getTrusted()).isFalse();
    ImmutableSet<String> appengineHeaderNames =
        httpRequestPb.getHeadersList().stream()
            .map(h -> Ascii.toLowerCase(h.getKey()))
            .filter(h -> h.startsWith("x-appengine-"))
            .collect(toImmutableSet());
    assertThat(appengineHeaderNames).isEmpty();

    assertThat(translatedUpRequest.getModuleVersionId()).isEqualTo("3.14");
    assertThat(translatedUpRequest.getSecurityTicket()).isEqualTo("secretkey");
    assertThat(translatedUpRequest.getModuleId()).isEqualTo("mytestservice");
    assertThat(translatedUpRequest.getNickname()).isEqualTo("anickname");
    assertThat(translatedUpRequest.getEmail()).isEqualTo("ausermail");
    assertThat(translatedUpRequest.getUserOrganization()).isEqualTo("auserorg");
    assertThat(translatedUpRequest.getIsAdmin()).isFalse();
    assertThat(translatedUpRequest.getPeerUsername()).isEqualTo("aloasname");
    assertThat(translatedUpRequest.getGaiaId()).isEqualTo(3142406);
    assertThat(translatedUpRequest.getAuthuser()).isEqualTo("aauthuser");
    assertThat(translatedUpRequest.getGaiaSession()).isEqualTo("agaiasession");
    assertThat(translatedUpRequest.getAppserverDatacenter()).isEqualTo("adatacenter");
    assertThat(translatedUpRequest.getAppserverTaskBns()).isEqualTo("ataskbns");
    assertThat(translatedUpRequest.getDefaultVersionHostname()).isEqualTo("foo.appspot.com");
    assertThat(translatedUpRequest.getRequestLogId()).isEqualTo("logid");
    assertThat(translatedUpRequest.getRequest().getIsOffline()).isFalse();
    assertThat(translatedUpRequest.getIsTrustedApp()).isTrue();
    ImmutableMap<String, String> runtimeHeaders =
        translatedUpRequest.getRuntimeHeadersList().stream()
            .collect(toImmutableMap(h -> Ascii.toLowerCase(h.getKey()), h -> h.getValue()));
    assertThat(runtimeHeaders)
        .doesNotContainKey(Ascii.toLowerCase(X_GOOGLE_INTERNAL_SKIPADMINCHECK));
    assertThat(runtimeHeaders).containsEntry(Ascii.toLowerCase(X_APPENGINE_TIMEOUT_MS), "20000");
  }

  @Test
  public void translateWithAppEngineHeadersIncludingQueueName() throws Exception {
    ImmutableMap<String, String> appengineHeaders =
        ImmutableMap.<String, String>builder()
            .putAll(BASE_APPENGINE_HEADERS)
            .put(X_APPENGINE_QUEUENAME, "default")
            .buildOrThrow();
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b", "127.0.0.1", appengineHeaders);

    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    HttpPb.HttpRequest httpRequestPb = translatedUpRequest.getRequest();
    ImmutableSet<String> appengineHeaderNames =
        httpRequestPb.getHeadersList().stream()
            .map(h -> Ascii.toLowerCase(h.getKey()))
            .filter(h -> h.startsWith("x-appengine-"))
            .collect(toImmutableSet());
    assertThat(appengineHeaderNames).containsExactly(Ascii.toLowerCase(X_APPENGINE_QUEUENAME));
    ImmutableMap<String, String> runtimeHeaders =
        translatedUpRequest.getRuntimeHeadersList().stream()
            .collect(toImmutableMap(h -> Ascii.toLowerCase(h.getKey()), h -> h.getValue()));
    assertThat(runtimeHeaders)
        .containsEntry(Ascii.toLowerCase(X_GOOGLE_INTERNAL_SKIPADMINCHECK), "true");
    assertThat(translatedUpRequest.getRequest().getIsOffline()).isTrue();
  }

  @Test
  public void translateWithAppEngineHeadersTrustedUser() throws Exception {
    // Change the trusted-ip-request header from "atrustedip" to the specific value "1", which means
    // that both the app and the user are trusted.
    Map<String, String> appengineHeaders = new HashMap<>(BASE_APPENGINE_HEADERS);
    appengineHeaders.put(X_APPENGINE_TRUSTED_IP_REQUEST, "1");
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.copyOf(appengineHeaders));

    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);

    HttpPb.HttpRequest httpRequestPb = translatedUpRequest.getRequest();
    assertThat(httpRequestPb.getHttpVersion()).isEqualTo("HTTP/1.0");
    assertThat(httpRequestPb.getIsHttps()).isTrue();
    assertThat(httpRequestPb.getProtocol()).isEqualTo("GET");
    assertThat(httpRequestPb.getUserIp()).isEqualTo("auserip");
    assertThat(httpRequestPb.getUrl()).isEqualTo("http://myapp.appspot.com:80/foo/bar?a=b");
    assertThat(httpRequestPb.getTrusted()).isTrue();
    ImmutableSet<String> appengineHeaderNames =
        httpRequestPb.getHeadersList().stream()
            .map(h -> Ascii.toLowerCase(h.getKey()))
            .filter(h -> h.startsWith("x-appengine-"))
            .collect(toImmutableSet());
    assertThat(appengineHeaderNames).isEmpty();

    assertThat(translatedUpRequest.getModuleVersionId()).isEqualTo("3.14");
    assertThat(translatedUpRequest.getSecurityTicket()).isEqualTo("secretkey");
    assertThat(translatedUpRequest.getModuleId()).isEqualTo("mytestservice");
    assertThat(translatedUpRequest.getNickname()).isEqualTo("anickname");
    assertThat(translatedUpRequest.getEmail()).isEqualTo("ausermail");
    assertThat(translatedUpRequest.getUserOrganization()).isEqualTo("auserorg");
    assertThat(translatedUpRequest.getIsAdmin()).isFalse();
    assertThat(translatedUpRequest.getPeerUsername()).isEqualTo("aloasname");
    assertThat(translatedUpRequest.getGaiaId()).isEqualTo(3142406);
    assertThat(translatedUpRequest.getAuthuser()).isEqualTo("aauthuser");
    assertThat(translatedUpRequest.getGaiaSession()).isEqualTo("agaiasession");
    assertThat(translatedUpRequest.getAppserverDatacenter()).isEqualTo("adatacenter");
    assertThat(translatedUpRequest.getAppserverTaskBns()).isEqualTo("ataskbns");
    assertThat(translatedUpRequest.getDefaultVersionHostname()).isEqualTo("foo.appspot.com");
    assertThat(translatedUpRequest.getRequestLogId()).isEqualTo("logid");
    assertThat(
            translatedUpRequest.getRuntimeHeadersList().stream()
                .map(h -> Ascii.toLowerCase(h.getKey()))
                .collect(toSet()))
        .doesNotContain(Ascii.toLowerCase(X_GOOGLE_INTERNAL_SKIPADMINCHECK));
    assertThat(translatedUpRequest.getRequest().getIsOffline()).isFalse();
    assertThat(translatedUpRequest.getIsTrustedApp()).isTrue();
  }

  @Test
  public void translateEmptyGaiaIdInAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_APPENGINE_GAIA_ID, ""));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getGaiaId()).isEqualTo(0);
  }

  @Test
  public void translateErrorPageFromHttpResponseError() throws Exception {
    HttpServletResponse httpResponse = mock(HttpServletResponse.class);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(httpResponse.getOutputStream()).thenReturn(copyingOutputStream(out));
    UPRequestTranslator.populateErrorResponse(httpResponse, "Expected error during test.");

    verify(httpResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    verify(httpResponse, never()).addHeader(any(), any());
    verify(httpResponse, never()).setHeader(any(), any());
    assertThat(out.toString("UTF-8"))
        .isEqualTo(
            "<html><head><title>Server Error</title></head>"
                + "<body>Expected error during test.</body></html>");
  }

  @Test
  public void translateSkipAdminCheckInAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_GOOGLE_INTERNAL_SKIPADMINCHECK, "true"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getRuntimeHeadersList())
        .contains(
            ParsedHttpHeader.newBuilder()
                .setKey(X_GOOGLE_INTERNAL_SKIPADMINCHECK)
                .setValue("true")
                .build());
  }

  @Test
  public void translateQueueNameSetsSkipAdminCheckInAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_APPENGINE_QUEUENAME, "__cron__"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getRuntimeHeadersList())
        .contains(
            ParsedHttpHeader.newBuilder()
                .setKey(X_GOOGLE_INTERNAL_SKIPADMINCHECK)
                .setValue("true")
                .build());
  }

  @Test
  public void translateBackgroundURISetsBackgroundRequestType() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/_ah/background?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_APPENGINE_USER_IP, "0.1.0.3"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getRequestType())
        .isEqualTo(RuntimePb.UPRequest.RequestType.BACKGROUND);
  }

  @Test
  public void translateNonBackgroundURIDoesNotSetsBackgroundRequestType() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/foo/bar?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_APPENGINE_USER_IP, "0.1.0.3"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getRequestType())
        .isNotEqualTo(RuntimePb.UPRequest.RequestType.BACKGROUND);
  }

  @Test
  public void translateRealIpDoesNotSetsBackgroundRequestType() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/_ah/background?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_APPENGINE_USER_IP, "1.2.3.4"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    assertThat(translatedUpRequest.getRequestType())
        .isNotEqualTo(RuntimePb.UPRequest.RequestType.BACKGROUND);
  }

  @Test
  public void translateCloudContextInAppEngineHeaders() throws Exception {
    HttpServletRequest httpRequest =
        mockServletRequest(
            "http://myapp.appspot.com:80/_ah/background?a=b",
            "127.0.0.1",
            ImmutableMap.of(X_CLOUD_TRACE_CONTEXT, "000000000000007b00000000000001c8/789;o=1"));
    RuntimePb.UPRequest translatedUpRequest = translator.translateRequest(httpRequest);
    TraceContextProto contextProto = translatedUpRequest.getTraceContext();
    TraceIdProto traceIdProto =
        TraceIdProto.parseFrom(contextProto.getTraceId(), ExtensionRegistry.getEmptyRegistry());
    String traceIdString = String.format("%016x%016x", traceIdProto.getHi(), traceIdProto.getLo());
    assertThat(traceIdString).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.getSpanId()).isEqualTo(789L);
    assertThat(contextProto.getTraceMask()).isEqualTo(1L);
  }

  private static HttpServletRequest mockServletRequest(
      String url, String remoteAddr, ImmutableMap<String, String> userHeaders) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    String urlWithoutQuery =
        uri.getScheme()
            + "://"
            + uri.getHost()
            + (uri.getPort() > 0 ? (":" + uri.getPort()) : "")
            + nullToEmpty(uri.getPath());
    ImmutableMap<String, String> headers =
        ImmutableMap.<String, String>builder()
            .putAll(userHeaders)
            .put("host", uri.getHost())
            .buildOrThrow();
    HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    when(httpRequest.getProtocol()).thenReturn("HTTP/1.0");
    when(httpRequest.getMethod()).thenReturn("GET");
    @SuppressWarnings("JdkObsolete") // imposed by the Servlet API
    Answer<StringBuffer> requestUrlAnswer = invocation -> new StringBuffer(urlWithoutQuery);
    when(httpRequest.getRequestURL()).thenAnswer(requestUrlAnswer);
    when(httpRequest.getRequestURI()).thenReturn(uri.getPath());
    when(httpRequest.getQueryString()).thenReturn(uri.getQuery());
    when(httpRequest.getRemoteAddr()).thenReturn(remoteAddr);
    when(httpRequest.getHeaderNames())
        .thenAnswer(invocation -> Collections.enumeration(headers.keySet()));
    headers.forEach((k, v) -> when(httpRequest.getHeader(k)).thenReturn(v));
    try {
      when(httpRequest.getInputStream()).thenReturn(emptyInputStream());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return httpRequest;
  }

  private static ServletInputStream emptyInputStream() {
    return new ServletInputStream() {
      @Override
      public int read() {
        return -1;
      }

      @Override
      public void setReadListener(ReadListener listener) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public boolean isFinished() {
        return true;
      }
    };
  }

  private static ServletOutputStream copyingOutputStream(OutputStream out) {
    return new ServletOutputStream() {
      @Override
      public void write(int b) throws IOException {
        out.write(b);
      }

      @Override
      public void setWriteListener(WriteListener listener) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReady() {
        return true;
      }
    };
  }
}
