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

package com.google.appengine.api.users.dev;

import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb;
import com.google.apphosting.api.UserServicePb.CreateLoginURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLoginURLResponse;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLResponse;
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import com.google.apphosting.api.UserServicePb.GetOAuthUserResponse;
import com.google.auto.service.AutoService;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * {@local LocalUserService} creates URLs that point to {@link
 * LocalLoginServlet} and {@link LocalLogoutServlet} when used within
 * the Development AppServer environment.
 *
 * There is a known discrepancy between this implementation and the production
 * implementation.  The production version will throw a
 * {@link ApiProxy.ApplicationException} with {@code applicationError} set to
 * {@link UserServicePb.UserServiceError.ErrorCode#REDIRECT_URL_TOO_LONG}
 * when the url passed to {@link #createLoginURL} or {@link #createLogoutURL}
 * is too long.  This implementation does not perform this check and therefore
 * never returns this error.
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalUserService extends AbstractLocalRpcService {
  // These URLs need to be kept in sync with:
  //   java/com/google/appengine/tools/development/jetty9/webdefault.xml
  private static final String LOGIN_URL = "/_ah/login";
  private static final String LOGOUT_URL = "/_ah/logout";

  // Properties from which to fetch values to return in OAuth calls
  // TODO: Provide an implementation that deals with actual
  // (multiple) users and handles the full token lifecycle.
  public static final String OAUTH_CONSUMER_KEY_PROPERTY = "oauth.consumer_key";
  public static final String OAUTH_EMAIL_PROPERTY = "oauth.email";
  public static final String OAUTH_USER_ID_PROPERTY = "oauth.user_id";
  public static final String OAUTH_AUTH_DOMAIN_PROPERTY = "oauth.auth_domain";
  public static final String OAUTH_IS_ADMIN_PROPERTY = "oauth.is_admin";

  // Defaults used by the stub OAuth API implementation.
  private String oauthEmail = "example@example.com";
  private String oauthUserId = "0";
  private String oauthAuthDomain = "gmail.com";
  private boolean oauthIsAdmin = false;

  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "user";

  public CreateLoginURLResponse createLoginURL(Status status, CreateLoginURLRequest request) {
    return CreateLoginURLResponse.newBuilder()
        .setLoginUrl(LOGIN_URL + "?continue=" + encode(request.getDestinationUrl()))
        .build();
  }

  public CreateLogoutURLResponse createLogoutURL(Status status, CreateLogoutURLRequest request) {
    return CreateLogoutURLResponse.newBuilder()
        .setLogoutUrl(LOGOUT_URL + "?continue=" + encode(request.getDestinationUrl()))
        .build();
  }

  public GetOAuthUserResponse getOAuthUser(Status status, GetOAuthUserRequest request) {
    return GetOAuthUserResponse.newBuilder()
        .setEmail(oauthEmail)
        .setUserId(oauthUserId)
        .setAuthDomain(oauthAuthDomain)
        .setIsAdmin(oauthIsAdmin)
        .build();
  }

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    String oauthEmailProp = properties.get(OAUTH_EMAIL_PROPERTY);
    if (oauthEmailProp != null) {
      oauthEmail = oauthEmailProp;
    }
    String oauthUserIdProp = properties.get(OAUTH_USER_ID_PROPERTY);
    if (oauthUserIdProp != null) {
      oauthUserId = oauthUserIdProp;
    }
    String oauthAuthDomainProp = properties.get(OAUTH_AUTH_DOMAIN_PROPERTY);
    if (oauthAuthDomainProp != null) {
      oauthAuthDomain = oauthAuthDomainProp;
    }
    String oauthIsAdminProp = properties.get(OAUTH_IS_ADMIN_PROPERTY);
    if (oauthIsAdminProp != null) {
      oauthIsAdmin = Boolean.parseBoolean(oauthIsAdminProp);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  private static String encode(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");
    } catch (UnsupportedEncodingException ex) {
      // This should not happen.
      throw new RuntimeException("Could not find UTF-8 encoding", ex);
    }
  }
}
