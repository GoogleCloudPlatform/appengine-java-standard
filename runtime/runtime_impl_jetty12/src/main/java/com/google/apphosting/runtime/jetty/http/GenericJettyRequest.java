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

package com.google.apphosting.runtime.jetty.http;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.TracePb;
import com.google.apphosting.runtime.GenericRequest;
import com.google.apphosting.runtime.TraceContextHelper;
import com.google.apphosting.runtime.jetty.AppInfoFactory;
import com.google.common.base.Strings;
import com.google.common.flogger.GoogleLogger;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.apphosting.base.protos.RuntimePb.UPRequest.RequestType.OTHER;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.DEFAULT_SECRET_KEY;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.IS_ADMIN_HEADER_VALUE;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.IS_TRUSTED;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.PRIVATE_APPENGINE_HEADERS;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.SKIP_ADMIN_CHECK_ATTR;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.WARMUP_IP;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_API_TICKET;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_APPSERVER_DATACENTER;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_APPSERVER_TASK_BNS;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_AUTH_DOMAIN;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_DEFAULT_VERSION_HOSTNAME;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_GAIA_AUTHUSER;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_GAIA_ID;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_GAIA_SESSION;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_HTTPS;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_ID_HASH;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_LOAS_PEER_USERNAME;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_QUEUENAME;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_REQUEST_LOG_ID;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_TIMEOUT_MS;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_TRUSTED_IP_REQUEST;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_USER_EMAIL;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_USER_ID;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_USER_IP;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_USER_IS_ADMIN;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_APPENGINE_USER_ORGANIZATION;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_CLOUD_TRACE_CONTEXT;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_FORWARDED_PROTO;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_GOOGLE_INTERNAL_PROFILER;
import static com.google.apphosting.runtime.jetty.AppEngineConstants.X_GOOGLE_INTERNAL_SKIPADMINCHECK;

public class GenericJettyRequest implements GenericRequest {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final Request originalRequest;
  private final Request request;
  private final AppInfoFactory appInfoFactory;
  private final String url;
  private Duration duration = Duration.ofNanos(Long.MAX_VALUE);
  private RuntimePb.UPRequest.RequestType requestType = OTHER;
  private String authDomain = "";
  private boolean isTrusted;
  private boolean isTrustedApp;
  private boolean isAdmin;
  private boolean isHttps;
  private boolean isOffline;
  private TracePb.TraceContextProto traceContext;
  private String obfuscatedGaiaId;
  private String userOrganization = "";
  private String peerUsername;
  private long gaiaId;
  private String authUser;
  private String gaiaSession;
  private String appserverDataCenter;
  String appserverTaskBns;
  String eventIdHash;
  private String requestLogId;
  private String defaultVersionHostname;
  private String email = "";
  private String securityTicket;


  public GenericJettyRequest(Request request, AppInfoFactory appInfoFactory, boolean passThroughPrivateHeaders) {
    this.appInfoFactory = appInfoFactory;

    // Can be overridden by X_APPENGINE_USER_IP header.
    String userIp = Request.getRemoteAddr(request);

    // Can be overridden by X_APPENGINE_API_TICKET header.
    this.securityTicket = DEFAULT_SECRET_KEY;

    HttpFields.Mutable fields = HttpFields.build();
    for (HttpField field : request.getHeaders())
    {
      String name = field.getLowerCaseName();
      String value = field.getValue();
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }

      switch (name) {
        case X_APPENGINE_TRUSTED_IP_REQUEST:
          // If there is a value, then the application is trusted
          // If the value is IS_TRUSTED, then the user is trusted
          isTrusted = value.equals(IS_TRUSTED);
          isTrustedApp = true;
          break;
        case X_APPENGINE_HTTPS:
          isHttps = value.equals("on");
          break;
        case X_APPENGINE_USER_IP:
          userIp = value;
          break;
        case X_FORWARDED_PROTO:
          isHttps = value.equals("https");
          break;
        case X_APPENGINE_USER_ID:
          obfuscatedGaiaId = value;
          break;
        case X_APPENGINE_USER_ORGANIZATION:
          userOrganization = value;
          break;
        case X_APPENGINE_LOAS_PEER_USERNAME:
          peerUsername = value;
          break;
        case X_APPENGINE_GAIA_ID:
          gaiaId = field.getLongValue();
          break;
        case X_APPENGINE_GAIA_AUTHUSER:
          authUser = value;
          break;
        case X_APPENGINE_GAIA_SESSION:
          gaiaSession = value;
          break;
        case X_APPENGINE_APPSERVER_DATACENTER:
          appserverDataCenter = value;
          break;
        case X_APPENGINE_APPSERVER_TASK_BNS:
          appserverTaskBns = value;
          break;
        case X_APPENGINE_ID_HASH:
          eventIdHash = value;
          break;
        case X_APPENGINE_REQUEST_LOG_ID:
          requestLogId = value;
          break;
        case X_APPENGINE_DEFAULT_VERSION_HOSTNAME:
          defaultVersionHostname = value;
          break;
        case X_APPENGINE_USER_IS_ADMIN:
          isAdmin = Objects.equals(value, IS_ADMIN_HEADER_VALUE);
          break;
        case X_APPENGINE_USER_EMAIL:
          email = value;
          break;
        case X_APPENGINE_AUTH_DOMAIN:
          authDomain = value;
          break;
        case X_APPENGINE_API_TICKET:
          securityTicket = value;
          break;

        case X_CLOUD_TRACE_CONTEXT:
          try {
            traceContext = TraceContextHelper.parseTraceContextHeader(value);
          } catch (NumberFormatException e) {
            logger.atWarning().withCause(e).log("Could not parse trace context header: %s", value);
          }
          break;

        case X_GOOGLE_INTERNAL_SKIPADMINCHECK:
          request.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);
          isHttps = true;
          break;

        case X_APPENGINE_QUEUENAME:
          request.setAttribute(SKIP_ADMIN_CHECK_ATTR, true);
          isOffline = true;
          break;

        case X_APPENGINE_TIMEOUT_MS:
          duration = Duration.ofMillis(Long.parseLong(value));
          break;

        case X_GOOGLE_INTERNAL_PROFILER:
          /* TODO: what to do here?
          try {
            TextFormat.merge(value, upReqBuilder.getProfilerSettingsBuilder());
          } catch (IOException ex) {
            throw new IllegalStateException("X-Google-Internal-Profiler read content error:", ex);
          }
           */
          break;

        default:
          break;
      }

      if (passThroughPrivateHeaders || !PRIVATE_APPENGINE_HEADERS.contains(name)) {
        // Only non AppEngine specific headers are passed to the application.
        fields.add(field);
      }
    }

    HttpURI httpURI;
    boolean isSecure;
    if (isHttps) {
      httpURI = HttpURI.build(request.getHttpURI()).scheme(HttpScheme.HTTPS);
      isSecure = true;
    }
    else {
      httpURI = request.getHttpURI();
      isSecure = request.isSecure();
    }

    String decodedPath = request.getHttpURI().getDecodedPath();
    if ("/_ah/background".equals(decodedPath)) {
      if (WARMUP_IP.equals(userIp)) {
        requestType = RuntimePb.UPRequest.RequestType.BACKGROUND;
      }
    } else if ("/_ah/start".equals(decodedPath)) {
      if (WARMUP_IP.equals(userIp)) {
        // This request came from within App Engine via secure internal channels; tell Jetty
        // it's HTTPS to avoid 403 because of web.xml security-constraint checks.
        isHttps = true;
      }
    }

    StringBuilder sb = new StringBuilder(HttpURI.build(httpURI).query(null).asString());
    String query = httpURI.getQuery();
    // No need to escape, URL retains any %-escaping it might have, which is what we want.
    if (query != null) {
      sb.append('?').append(query);
    }
    url = sb.toString();

    if (traceContext == null)
      traceContext = com.google.apphosting.base.protos.TracePb.TraceContextProto.getDefaultInstance();

    this.originalRequest = request;
    this.request = new Request.Wrapper(request)
    {
      @Override
      public HttpURI getHttpURI() {
        return httpURI;
      }

      @Override
      public boolean isSecure() {
        return isSecure;
      }

      @Override
      public HttpFields getHeaders() {
        return fields;
      }
    };
  }

  public Request getOriginalRequest()
  {
    return originalRequest;
  }

  public Request getWrappedRequest()
  {
    return request;
  }

  @Override
  public Stream<HttpPb.ParsedHttpHeader> getHeadersList() {
    return request.getHeaders().stream()
            .map(f -> HttpPb.ParsedHttpHeader.newBuilder().setKey(f.getName()).setValue(f.getValue()).build());
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public RuntimePb.UPRequest.RequestType getRequestType() {
    return requestType;
  }

  @Override
  public boolean hasTraceContext() {
    return traceContext != null;
  }

  @Override
  public TracePb.TraceContextProto getTraceContext() {
    return traceContext;
  }

  @Override
  public String getSecurityLevel() {
    // TODO(b/78515194) Need to find a mapping for this field.
    return null;
  }

  @Override
  public boolean getIsOffline() {
    return isOffline;
  }

  @Override
  public String getAppId() {
    return appInfoFactory.getGaeApplication();
  }

  @Override
  public String getModuleId() {
    return appInfoFactory.getGaeService();
  }

  @Override
  public String getModuleVersionId() {
    return appInfoFactory.getGaeServiceVersion();
  }

  @Override
  public String getObfuscatedGaiaId() {
    return obfuscatedGaiaId;
  }

  @Override
  public String getUserOrganization() {
    return userOrganization;
  }

  @Override
  public boolean getIsTrustedApp() {
    return isTrustedApp;
  }

  @Override
  public boolean getTrusted() {
    return isTrusted;
  }

  @Override
  public String getPeerUsername() {
    return peerUsername;
  }

  @Override
  public long getGaiaId() {
    return gaiaId;
  }

  @Override
  public String getAuthuser() {
    return authUser;
  }

  @Override
  public String getGaiaSession() {
    return gaiaSession;
  }

  @Override
  public String getAppserverDatacenter() {
    return appserverDataCenter;
  }

  @Override
  public String getAppserverTaskBns() {
    return appserverTaskBns;
  }

  @Override
  public boolean hasEventIdHash() {
    return eventIdHash != null;
  }

  @Override
  public String getEventIdHash() {
    return eventIdHash;
  }

  @Override
  public boolean hasRequestLogId() {
    return requestLogId != null;
  }

  @Override
  public String getRequestLogId() {
    return requestLogId;
  }

  @Override
  public boolean hasDefaultVersionHostname() {
    return defaultVersionHostname != null;
  }

  @Override
  public String getDefaultVersionHostname() {
    return defaultVersionHostname;
  }

  @Override
  public boolean getIsAdmin() {
    return isAdmin;
  }

  @Override
  public String getEmail() {
    return email;
  }

  @Override
  public String getAuthDomain() {
    return authDomain;
  }

  @Override
  public String getSecurityTicket() {
    return securityTicket;
  }

  public Duration getTimeRemaining() {
    return duration;
  }
}
