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

import static com.google.apphosting.runtime.AppEngineConstants.BACKGROUND_REQUEST_URL;
import static com.google.apphosting.runtime.AppEngineConstants.DEFAULT_SECRET_KEY;
import static com.google.apphosting.runtime.AppEngineConstants.IS_ADMIN_HEADER_VALUE;
import static com.google.apphosting.runtime.AppEngineConstants.IS_TRUSTED;
import static com.google.apphosting.runtime.AppEngineConstants.PRIVATE_APPENGINE_HEADERS;
import static com.google.apphosting.runtime.AppEngineConstants.WARMUP_IP;
import static com.google.apphosting.runtime.AppEngineConstants.WARMUP_REQUEST_URL;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_API_TICKET;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_APPSERVER_DATACENTER;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_APPSERVER_TASK_BNS;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_AUTH_DOMAIN;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_DEFAULT_VERSION_HOSTNAME;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_AUTHUSER;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_ID;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_SESSION;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_HTTPS;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_LOAS_PEER_USERNAME;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_QUEUENAME;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_REQUEST_LOG_ID;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_TIMEOUT_MS;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_TRUSTED_IP_REQUEST;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_EMAIL;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_ID;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_IP;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_IS_ADMIN;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_ORGANIZATION;
import static com.google.apphosting.runtime.AppEngineConstants.X_CLOUD_TRACE_CONTEXT;
import static com.google.apphosting.runtime.AppEngineConstants.X_FORWARDED_PROTO;
import static com.google.apphosting.runtime.AppEngineConstants.X_GOOGLE_INTERNAL_PROFILER;
import static com.google.apphosting.runtime.AppEngineConstants.X_GOOGLE_INTERNAL_SKIPADMINCHECK;

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
import static com.google.apphosting.runtime.AppEngineConstants.X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_NICKNAME;


/** Translates HttpServletRequest to the UPRequest proto, and vice versa for the response. */
public class UPRequestTranslator {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String DEFAULT_SECRET_KEY = "secretkey";

 

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

    if (BACKGROUND_REQUEST_URL.equals(realRequest.getRequestURI())) {
      if (WARMUP_IP.equals(httpRequest.getUserIp())) {
        upReqBuilder.setRequestType(UPRequest.RequestType.BACKGROUND);
      }
    } else if (WARMUP_REQUEST_URL.equals(realRequest.getRequestURI())) {
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
