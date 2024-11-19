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

package com.google.apphosting.runtime;

import com.google.common.collect.ImmutableSet;

/** {@code AppEngineConstants} centralizes some constants that are specific to our use of Jetty. */
public final class AppEngineConstants {
  /**
   * This {@code ServletContext} attribute contains the {@link AppVersion} for the current
   * application.
   */
  public static final String APP_VERSION_CONTEXT_ATTR =
      "com.google.apphosting.runtime.jetty.APP_VERSION_CONTEXT_ATTR";

  /**
   * This {@code ServletRequest} attribute contains the {@code AppVersionKey} identifying the
   * current application. identify which application version to use.
   */
  public static final String APP_VERSION_KEY_REQUEST_ATTR =
      "com.google.apphosting.runtime.jetty.APP_VERSION_REQUEST_ATTR";

  public static final String APP_YAML_ATTRIBUTE_TARGET =
      "com.google.apphosting.runtime.jetty.appYaml";

  /**
   * The HTTP headers that are handled specially by this proxy are defined in lowercase because HTTP
   * headers are case-insensitive, and we look then up in a set or switch after converting to
   * lower-case.
   */
  public static final String X_FORWARDED_PROTO = "x-forwarded-proto";

  public static final String X_APPENGINE_API_TICKET = "x-appengine-api-ticket";
  public static final String X_APPENGINE_HTTPS = "x-appengine-https";
  public static final String X_APPENGINE_USER_IP = "x-appengine-user-ip";
  public static final String X_APPENGINE_USER_EMAIL = "x-appengine-user-email";
  public static final String X_APPENGINE_AUTH_DOMAIN = "x-appengine-auth-domain";
  public static final String X_APPENGINE_USER_ID = "x-appengine-user-id";
  public static final String X_APPENGINE_USER_NICKNAME = "x-appengine-user-nickname";
  public static final String X_APPENGINE_USER_ORGANIZATION = "x-appengine-user-organization";
  public static final String X_APPENGINE_USER_IS_ADMIN = "x-appengine-user-is-admin";
  public static final String X_APPENGINE_TRUSTED_IP_REQUEST = "x-appengine-trusted-ip-request";
  public static final String X_APPENGINE_LOAS_PEER_USERNAME = "x-appengine-loas-peer-username";
  public static final String X_APPENGINE_ID_HASH = "x-appengine-request-id-hash";
  public static final String X_APPENGINE_GAIA_ID = "x-appengine-gaia-id";
  public static final String X_APPENGINE_GAIA_AUTHUSER = "x-appengine-gaia-authuser";
  public static final String X_APPENGINE_GAIA_SESSION = "x-appengine-gaia-session";
  public static final String X_APPENGINE_APPSERVER_DATACENTER = "x-appengine-appserver-datacenter";
  public static final String X_APPENGINE_APPSERVER_TASK_BNS = "x-appengine-appserver-task-bns";
  public static final String X_APPENGINE_DEFAULT_VERSION_HOSTNAME =
      "x-appengine-default-version-hostname";
  public static final String X_APPENGINE_REQUEST_LOG_ID = "x-appengine-request-log-id";
  public static final String X_APPENGINE_QUEUENAME = "x-appengine-queuename";
  public static final String X_APPENGINE_TIMEOUT_MS = "x-appengine-timeout-ms";
  public static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "x-google-internal-skipadmincheck";
  public static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK_UC =
      "X-Google-Internal-SkipAdminCheck";
  public static final String X_GOOGLE_INTERNAL_PROFILER = "x-google-internal-profiler";
  public static final String X_CLOUD_TRACE_CONTEXT = "x-cloud-trace-context";

  public static final String X_APPENGINE_BACKGROUNDREQUEST = "x-appengine-backgroundrequest";
  public static final String BACKGROUND_REQUEST_URL = "/_ah/background";
  public static final String WARMUP_REQUEST_URL = "/_ah/start";
  public static final String BACKGROUND_REQUEST_SOURCE_IP = "0.1.0.3";

  public static final ImmutableSet<String> PRIVATE_APPENGINE_HEADERS =
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

  public static final String IS_ADMIN_HEADER_VALUE = "1";
  public static final String IS_TRUSTED = "1";
  public static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  // The impersonated IP address of warmup requests (and also background)
  //     (<internal20>)
  public static final String WARMUP_IP = "0.1.0.3";

  public static final String UNSPECIFIED_IP = "0.0.0.0";

  public static final String DEFAULT_SECRET_KEY = "secretkey";

  public static final String ENVIRONMENT_ATTR = "appengine.environment";

  public static final String HTTP_CONNECTOR_MODE = "appengine.use.HttpConnector";

  public static final String IGNORE_RESPONSE_SIZE_LIMIT = "appengine.ignore.responseSizeLimit";

  private AppEngineConstants() {}
}
