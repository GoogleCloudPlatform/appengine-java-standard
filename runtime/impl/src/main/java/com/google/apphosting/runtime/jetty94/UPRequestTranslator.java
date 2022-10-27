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

package com.google.apphosting.runtime.jetty94;

import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.HttpPb.HttpRequest;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.apphosting.runtime.TraceContextHelper;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.GoogleLogger;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.Collections;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/** Translates HttpServletRequest to the UPRequest proto, and vice versa for the response. */
public class UPRequestTranslator {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String DEFAULT_SECRET_KEY = "secretkey";

  /**
   * The HTTP headers that are handled specially by this proxy are defined in lowercae because HTTP
   * headers are case insensitive and we look then up in a set or switch after converting to
   * lower-case.
   */
  private static final String X_FORWARDED_PROTO = "x-forwarded-proto";

  private static final String X_APPENGINE_API_TICKET = "x-appengine-api-ticket";
  private static final String X_APPENGINE_HTTPS = "x-appengine-https";
  private static final String X_APPENGINE_USER_IP = "x-appengine-user-ip";
  private static final String X_APPENGINE_USER_EMAIL = "x-appengine-user-email";
  private static final String X_APPENGINE_AUTH_DOMAIN = "x-appengine-auth-domain";
  private static final String X_APPENGINE_USER_ID = "x-appengine-user-id";
  private static final String X_APPENGINE_USER_NICKNAME = "x-appengine-user-nickname";
  private static final String X_APPENGINE_USER_ORGANIZATION = "x-appengine-user-organization";
  private static final String X_APPENGINE_USER_IS_ADMIN = "x-appengine-user-is-admin";
  private static final String X_APPENGINE_TRUSTED_IP_REQUEST = "x-appengine-trusted-ip-request";
  private static final String X_APPENGINE_LOAS_PEER_USERNAME = "x-appengine-loas-peer-username";
  private static final String X_APPENGINE_GAIA_ID = "x-appengine-gaia-id";
  private static final String X_APPENGINE_GAIA_AUTHUSER = "x-appengine-gaia-authuser";
  private static final String X_APPENGINE_GAIA_SESSION = "x-appengine-gaia-session";
  private static final String X_APPENGINE_APPSERVER_DATACENTER = "x-appengine-appserver-datacenter";
  private static final String X_APPENGINE_APPSERVER_TASK_BNS = "x-appengine-appserver-task-bns";
  private static final String X_APPENGINE_DEFAULT_VERSION_HOSTNAME =
      "x-appengine-default-version-hostname";
  private static final String X_APPENGINE_REQUEST_LOG_ID = "x-appengine-request-log-id";
  private static final String X_APPENGINE_QUEUENAME = "x-appengine-queuename";
  private static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "x-google-internal-skipadmincheck";
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC =
      "X-Google-Internal-SkipAdminCheck";
  private static final String X_GOOGLE_INTERNAL_PROFILER = "x-google-internal-profiler";
  private static final String X_CLOUD_TRACE_CONTEXT = "x-cloud-trace-context";

  private static final String IS_ADMIN_HEADER_VALUE = "1";
  private static final String IS_TRUSTED = "1";

  // The impersonated IP address of warmup requests (and also background)
  //     (<internal20>)
  private static final String WARMUP_IP = "0.1.0.3";

  private static final ImmutableSet<String> PRIVATE_APPENGINE_HEADERS =
      ImmutableSet.of(
          X_APPENGINE_API_TICKET,
          X_APPENGINE_HTTPS,
          X_APPENGINE_USER_IP,
          X_APPENGINE_USER_EMAIL,
          X_APPENGINE_AUTH_DOMAIN,
          X_APPENGINE_USER_ID,
          X_APPENGINE_USER_NICKNAME,
          X_APPENGINE_USER_ORGANIZATION,
          X_APPENGINE_USER_IS_ADMIN,
          X_APPENGINE_TRUSTED_IP_REQUEST,
          X_APPENGINE_LOAS_PEER_USERNAME,
          X_APPENGINE_GAIA_ID,
          X_APPENGINE_GAIA_AUTHUSER,
          X_APPENGINE_GAIA_SESSION,
          X_APPENGINE_APPSERVER_DATACENTER,
          X_APPENGINE_APPSERVER_TASK_BNS,
          X_APPENGINE_DEFAULT_VERSION_HOSTNAME,
          X_APPENGINE_REQUEST_LOG_ID,
          X_APPENGINE_TIMEOUT_MS,
          X_GOOGLE_INTERNAL_PROFILER);

  private final AppInfoFactory appInfoFactory;
  private final boolean passThroughPrivateHeaders;
  private final boolean skipPostData;

  /**
   * Construct an UPRequestTranslator.
   *
   * @param appInfoFactory An {@link AppInfoFactory}.
   * @param passThroughPrivateHeaders Include internal App Engine headers in translation (mostly
   *     X-AppEngine-*) instead of eliding them.
   * @param skipPostData Don't read the request body. This is useful for callers who will read it
   *     directly, since the read can only happen once.
   */
  public UPRequestTranslator(
      AppInfoFactory appInfoFactory, boolean passThroughPrivateHeaders, boolean skipPostData) {
    this.appInfoFactory = appInfoFactory;
    this.passThroughPrivateHeaders = passThroughPrivateHeaders;
    this.skipPostData = skipPostData;
  }

  /**
   * Translate from a response proto to a javax.servlet response.
   *
   * @param response the Jetty response object to fill
   * @param rpcResp the proto info available to extract info from
   */
  public final void translateResponse(Response response, RuntimePb.UPResponse rpcResp) {
    HttpPb.HttpResponse rpcHttpResp = rpcResp.getHttpResponse();

    if (rpcResp.getError() != RuntimePb.UPResponse.ERROR.OK.getNumber()) {
      populateErrorResponse(response, "Request failed: " + rpcResp.getErrorMessage());
      return;
    }
    response.setStatus(rpcHttpResp.getResponsecode());
    for (HttpPb.ParsedHttpHeader header : rpcHttpResp.getOutputHeadersList()) {
      response.addHeader(header.getKey(), header.getValue());
    }

    try {
      response.getHttpOutput().sendContent(rpcHttpResp.getResponse().asReadOnlyByteBuffer());
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Makes a UPRequest from an HttpServletRequest
   *
   * @param realRequest the http request object
   * @return equivalent UPRequest object
   */
  @SuppressWarnings("JdkObsolete")
  public final RuntimePb.UPRequest translateRequest(HttpServletRequest realRequest) {
    UPRequest.Builder upReqBuilder =
        UPRequest.newBuilder()
            .setAppId(appInfoFactory.getGaeApplication())
            .setVersionId(appInfoFactory.getGaeVersion())
            .setModuleId(appInfoFactory.getGaeService())
            .setModuleVersionId(appInfoFactory.getGaeServiceVersion());

    // TODO(b/78515194) Need to find a mapping for all these upReqBuilder fields:
    /*
    setRequestLogId();
    setEventIdHash();
    setSecurityLevel());
    */

    upReqBuilder.setSecurityTicket(DEFAULT_SECRET_KEY);
    upReqBuilder.setNickname("");
    if (realRequest instanceof Request) {
      // user efficient header iteration
      for (HttpField field : ((Request) realRequest).getHttpFields()) {
        builderHeader(upReqBuilder, field.getName(), field.getValue());
      }
    } else {
      // slower iteration used for test case fake request only
      for (String name : Collections.list(realRequest.getHeaderNames())) {
        String value = realRequest.getHeader(name);
        builderHeader(upReqBuilder, name, value);
      }
    }

    AppinfoPb.Handler handler =
        upReqBuilder
            .getHandler()
            .newBuilderForType()
            .setType(AppinfoPb.Handler.HANDLERTYPE.CGI_BIN.getNumber())
            .setPath("unused")
            .build();
    upReqBuilder.setHandler(handler);

    HttpPb.HttpRequest.Builder httpRequest =
        upReqBuilder
            .getRequestBuilder()
            .setHttpVersion(realRequest.getProtocol())
            .setProtocol(realRequest.getMethod())
            .setUrl(getUrl(realRequest))
            .setUserIp(realRequest.getRemoteAddr());

    if (realRequest instanceof Request) {
      // user efficient header iteration
      for (HttpField field : ((Request) realRequest).getHttpFields()) {
        requestHeader(upReqBuilder, httpRequest, field.getName(), field.getValue());
      }
    } else {
      // slower iteration used for test case fake request only
      for (String name : Collections.list(realRequest.getHeaderNames())) {
        String value = realRequest.getHeader(name);
        requestHeader(upReqBuilder, httpRequest, name, value);
      }
    }

    if (!skipPostData) {
      try {
        httpRequest.setPostdata(ByteString.readFrom(realRequest.getInputStream()));
      } catch (IOException ex) {
        throw new IllegalStateException("Could not read POST content:", ex);
      }
    }

    if ("/_ah/background".equals(realRequest.getRequestURI())) {
      if (WARMUP_IP.equals(httpRequest.getUserIp())) {
        upReqBuilder.setRequestType(UPRequest.RequestType.BACKGROUND);
      }
    } else if ("/_ah/start".equals(realRequest.getRequestURI())) {
      if (WARMUP_IP.equals(httpRequest.getUserIp())) {
        // This request came from within App Engine via secure internal channels; tell Jetty
        // it's HTTPS to avoid 403 because of web.xml security-constraint checks.
        httpRequest.setIsHttps(true);
      }
    }

    return upReqBuilder.build();
  }

  private static void builderHeader(UPRequest.Builder upReqBuilder, String name, String value) {
    if (Strings.isNullOrEmpty(value)) {
      return;
    }
    String lower = Ascii.toLowerCase(name);
    switch (lower) {
      case X_APPENGINE_API_TICKET:
        upReqBuilder.setSecurityTicket(value);
        return;

      case X_APPENGINE_USER_EMAIL:
        upReqBuilder.setEmail(value);
        return;

      case X_APPENGINE_USER_NICKNAME:
        upReqBuilder.setNickname(value);
        return;

      case X_APPENGINE_USER_IS_ADMIN:
        upReqBuilder.setIsAdmin(value.equals(IS_ADMIN_HEADER_VALUE));
        return;

      case X_APPENGINE_AUTH_DOMAIN:
        upReqBuilder.setAuthDomain(value);
        return;

      case X_APPENGINE_USER_ORGANIZATION:
        upReqBuilder.setUserOrganization(value);
        return;

      case X_APPENGINE_LOAS_PEER_USERNAME:
        upReqBuilder.setPeerUsername(value);
        return;

      case X_APPENGINE_GAIA_ID:
        upReqBuilder.setGaiaId(Long.parseLong(value));
        return;

      case X_APPENGINE_GAIA_AUTHUSER:
        upReqBuilder.setAuthuser(value);
        return;

      case X_APPENGINE_GAIA_SESSION:
        upReqBuilder.setGaiaSession(value);
        return;

      case X_APPENGINE_APPSERVER_DATACENTER:
        upReqBuilder.setAppserverDatacenter(value);
        return;

      case X_APPENGINE_APPSERVER_TASK_BNS:
        upReqBuilder.setAppserverTaskBns(value);
        return;

      case X_APPENGINE_USER_ID:
        upReqBuilder.setObfuscatedGaiaId(value);
        return;

      case X_APPENGINE_DEFAULT_VERSION_HOSTNAME:
        upReqBuilder.setDefaultVersionHostname(value);
        return;

      case X_APPENGINE_REQUEST_LOG_ID:
        upReqBuilder.setRequestLogId(value);
        return;

      default:
        return;
    }
  }

  private void requestHeader(
      UPRequest.Builder upReqBuilder, HttpRequest.Builder httpRequest, String name, String value) {
    if (Strings.isNullOrEmpty(value)) {
      return;
    }
    String lower = Ascii.toLowerCase(name);
    switch (lower) {
      case X_APPENGINE_TRUSTED_IP_REQUEST:
        // If there is a value, then the application is trusted
        // If the value is IS_TRUSTED, then the user is trusted
        httpRequest.setTrusted(value.equals(IS_TRUSTED));
        upReqBuilder.setIsTrustedApp(true);
        break;

      case X_APPENGINE_HTTPS:
        httpRequest.setIsHttps(value.equals("on"));
        break;

      case X_APPENGINE_USER_IP:
        httpRequest.setUserIp(value);
        break;

      case X_FORWARDED_PROTO:
        httpRequest.setIsHttps(value.equals("https"));
        break;

      case X_CLOUD_TRACE_CONTEXT:
        try {
          TraceContextProto proto = TraceContextHelper.parseTraceContextHeader(value);
          upReqBuilder.setTraceContext(proto);
        } catch (NumberFormatException e) {
          logger.atWarning().withCause(e).log("Could not parse trace context header: %s", value);
        }
        break;

      case X_GOOGLE_INTERNAL_SKIPADMINCHECK:
        // may be set by X_APPENGINE_QUEUENAME below
        if (upReqBuilder.getRuntimeHeadersList().stream()
            .map(ParsedHttpHeader::getKey)
            .noneMatch(X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC::equalsIgnoreCase)) {
          upReqBuilder.addRuntimeHeaders(
              createRuntimeHeader(X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC, "true"));
        }
        break;

      case X_APPENGINE_QUEUENAME:
        httpRequest.setIsOffline(true);
        // See b/139183416, allow for cron jobs and task queues to access login: admin urls
        if (upReqBuilder.getRuntimeHeadersList().stream()
            .map(ParsedHttpHeader::getKey)
            .noneMatch(X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC::equalsIgnoreCase)) {
          upReqBuilder.addRuntimeHeaders(
              createRuntimeHeader(X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC, "true"));
        }
        break;

      case X_APPENGINE_TIMEOUT_MS:
        upReqBuilder.addRuntimeHeaders(createRuntimeHeader(X_APPENGINE_TIMEOUT_MS, value));
        break;

      case X_GOOGLE_INTERNAL_PROFILER:
        try {
          TextFormat.merge(value, upReqBuilder.getProfilerSettingsBuilder());
        } catch (IOException ex) {
          throw new IllegalStateException("X-Google-Internal-Profiler read content error:", ex);
        }
        break;

      default:
        break;
    }
    if (passThroughPrivateHeaders || !PRIVATE_APPENGINE_HEADERS.contains(lower)) {
      // Only non AppEngine specific headers are passed to the application.
      httpRequest.addHeadersBuilder().setKey(name).setValue(value);
    }
  }

  private String getUrl(HttpServletRequest req) {
    StringBuffer url = req.getRequestURL();
    String query = req.getQueryString();
    // No need to escape, URL retains any %-escaping it might have, which is what we want.
    if (query != null) {
      url.append('?').append(query);
    }
    return url.toString();
  }

  /**
   * Populates a response object from some error message.
   *
   * @param resp response message to fill with info
   * @param errMsg error text.
   */
  static void populateErrorResponse(HttpServletResponse resp, String errMsg) {
    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    try {
      ServletOutputStream outstr = resp.getOutputStream();
      outstr.print("<html><head><title>Server Error</title></head>");
      outstr.print("<body>" + HtmlEscapers.htmlEscaper().escape(errMsg) + "</body></html>");
    } catch (IOException iox) {
      throw new IllegalStateException(iox);
    }
  }

  private static HttpPb.ParsedHttpHeader.Builder createRuntimeHeader(String key, String value) {
    return HttpPb.ParsedHttpHeader.newBuilder().setKey(key).setValue(value);
  }
}
