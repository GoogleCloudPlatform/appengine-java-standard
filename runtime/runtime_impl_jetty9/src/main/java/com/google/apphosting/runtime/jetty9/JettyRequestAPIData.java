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

import static com.google.apphosting.base.protos.RuntimePb.UPRequest.RequestType.OTHER;
import static com.google.apphosting.runtime.AppEngineConstants.BACKGROUND_REQUEST_URL;
import static com.google.apphosting.runtime.AppEngineConstants.DEFAULT_SECRET_KEY;
import static com.google.apphosting.runtime.AppEngineConstants.IS_ADMIN_HEADER_VALUE;
import static com.google.apphosting.runtime.AppEngineConstants.IS_TRUSTED;
import static com.google.apphosting.runtime.AppEngineConstants.PRIVATE_APPENGINE_HEADERS;
import static com.google.apphosting.runtime.AppEngineConstants.SKIP_ADMIN_CHECK_ATTR;
import static com.google.apphosting.runtime.AppEngineConstants.UNSPECIFIED_IP;
import static com.google.apphosting.runtime.AppEngineConstants.WARMUP_IP;
import static com.google.apphosting.runtime.AppEngineConstants.WARMUP_REQUEST_URL;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_API_TICKET;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_APPSERVER_DATACENTER;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_APPSERVER_TASK_BNS;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_AUTH_DOMAIN;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_BACKGROUNDREQUEST;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_DEFAULT_VERSION_HOSTNAME;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_AUTHUSER;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_ID;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_GAIA_SESSION;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_HTTPS;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_ID_HASH;
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
import static com.google.apphosting.runtime.jetty9.RpcConnection.NORMALIZE_INET_ADDR;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.TracePb;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.apphosting.runtime.RequestAPIData;
import com.google.apphosting.runtime.TraceContextHelper;
import com.google.common.base.Strings;
import com.google.common.flogger.GoogleLogger;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.HostPort;

/**
 * Implementation for the {@link RequestAPIData} to allow for the Jetty {@link Request} to be used
 * directly with the Java Runtime without any conversion into the RPC {@link RuntimePb.UPRequest}.
 *
 * <p>This will interpret the AppEngine specific headers defined in {@link AppEngineConstants}. The
 * request returned by {@link #getBaseRequest()} is to be passed to the application and will hide
 * any private appengine headers from {@link AppEngineConstants#PRIVATE_APPENGINE_HEADERS}.
 */
public class JettyRequestAPIData implements RequestAPIData {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final Request baseRequest;
  private final HttpServletRequest httpServletRequest;
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
  private String backgroundRequestId;

  public JettyRequestAPIData(
      Request request,
      HttpServletRequest httpServletRequest,
      AppInfoFactory appInfoFactory,
      boolean passThroughPrivateHeaders) {
    this.appInfoFactory = appInfoFactory;

    // Can be overridden by X_APPENGINE_USER_IP header.
    String userIp = request.getRemoteAddr();

    // Can be overridden by X_APPENGINE_API_TICKET header.
    this.securityTicket = DEFAULT_SECRET_KEY;

    HttpFields fields = new HttpFields();
    for (HttpField field : request.getHttpFields()) {
      // If it has a HttpHeader it is one of the standard headers so won't match any appengine
      // specific header.
      if (field.getHeader() != null) {
        fields.add(field);
        continue;
      }

      String name = field.getName().toLowerCase(Locale.ROOT);
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
          gaiaId = Long.parseLong(value);
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

        case X_APPENGINE_BACKGROUNDREQUEST:
          backgroundRequestId = value;
          break;

        default:
          break;
      }

      if (passThroughPrivateHeaders || !PRIVATE_APPENGINE_HEADERS.contains(name)) {
        // Only non AppEngine specific headers are passed to the application.
        fields.add(field);
      }
    }

    HttpURI httpUri;
    boolean isSecure;
    if (isHttps) {
      httpUri = new HttpURI(request.getHttpURI());
      httpUri.setScheme(HttpScheme.HTTPS.asString());
      isSecure = true;
    } else {
      httpUri = request.getHttpURI();
      isSecure = request.isSecure();
    }

    String decodedPath = request.getHttpURI().getDecodedPath();
    if (Objects.equals(decodedPath, BACKGROUND_REQUEST_URL)) {
      if (Objects.equals(userIp, WARMUP_IP)) {
        requestType = RuntimePb.UPRequest.RequestType.BACKGROUND;
      }
    } else if (Objects.equals(decodedPath, WARMUP_REQUEST_URL)) {
      if (Objects.equals(userIp, WARMUP_IP)) {
        // This request came from within App Engine via secure internal channels; tell Jetty
        // it's HTTPS to avoid 403 because of web.xml security-constraint checks.
        isHttps = true;
      }
    }

    HttpURI uri = new HttpURI(httpUri);
    uri.setQuery(null);
    StringBuilder sb = new StringBuilder(uri.toString());
    String query = httpUri.getQuery();
    // No need to escape, URL retains any %-escaping it might have, which is what we want.
    if (query != null) {
      sb.append('?').append(query);
    }
    url = sb.toString();

    if (traceContext == null) {
      traceContext = TraceContextProto.getDefaultInstance();
    }

    String finalUserIp = NORMALIZE_INET_ADDR ? HostPort.normalizeHost(userIp) : userIp;
    this.httpServletRequest =
        new HttpServletRequestWrapper(httpServletRequest) {

          @Override
          public long getDateHeader(String name) {
            return fields.getDateField(name);
          }

          @Override
          public String getHeader(String name) {
            return fields.get(name);
          }

          @Override
          public Enumeration<String> getHeaders(String name) {
            return fields.getValues(name);
          }

          @Override
          public Enumeration<String> getHeaderNames() {
            return fields.getFieldNames();
          }

          @Override
          public int getIntHeader(String name) {
            return Math.toIntExact(fields.getLongField(name));
          }

          @Override
          public String getRequestURI() {
            return httpUri.getPath();
          }

          @Override
          public String getScheme() {
            return httpUri.getScheme();
          }

          @Override
          public boolean isSecure() {
            return isSecure;
          }

          @Override
          public String getRemoteAddr() {
            return finalUserIp;
          }

          @Override
          public String getRemoteHost() {
            return finalUserIp;
          }

          @Override
          public int getRemotePort() {
            return 0;
          }

          @Override
          public String getLocalName() {
            return UNSPECIFIED_IP;
          }

          @Override
          public String getLocalAddr() {
            return UNSPECIFIED_IP;
          }

          @Override
          public int getLocalPort() {
            return 0;
          }
        };

    this.baseRequest = request;
    this.baseRequest.setSecure(isSecure);
    this.baseRequest.setHttpURI(httpUri);
  }

  public Request getBaseRequest() {
    return baseRequest;
  }

  public HttpServletRequest getHttpServletRequest() {
    return httpServletRequest;
  }

  @Override
  public Stream<HttpPb.ParsedHttpHeader> getHeadersList() {
    return baseRequest.getHttpFields().stream()
        .map(
            f ->
                HttpPb.ParsedHttpHeader.newBuilder()
                    .setKey(f.getName())
                    .setValue(f.getValue())
                    .build());
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
  public String getBackgroundRequestId() {
    return backgroundRequestId;
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
