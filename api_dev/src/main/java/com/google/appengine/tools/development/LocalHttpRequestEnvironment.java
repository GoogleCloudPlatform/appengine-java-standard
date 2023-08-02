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

package com.google.appengine.tools.development;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.users.dev.LoginCookieUtils;
import javax.servlet.http.HttpServletRequest;

/**
 * {@code LocalHttpRequestEnvironment} is a simple {@link LocalEnvironment} for
 * use during http request handling.
 *
 * This sets {@link LocalEnvironment#getAttributes()} from
 * <ol>
 * <li> Authentication details from the cookie that is maintained
 *      by the stub implementation of {@link UserService}
 * </li>
 * <li> The passed in {@link ModulesFilterHelper}. </li>
 * </ol>
 */
public class LocalHttpRequestEnvironment extends LocalEnvironment {
  // Keep this in sync with X_APPENGINE_DEFAULT_NAMESPACE in
  // google3/apphosting/base/http_proto.cc and
  // com.google.apphosting.runtime.ApiProxyImpl.EnvironmentImpl.DEFAULT_NAMESPACE_HEADER.
  /**
   * The name of the HTTP header specifying the default namespace
   * for API calls.
   */
  static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";
  static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";
  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  private static final String USER_ID_KEY =
      "com.google.appengine.api.users.UserService.user_id_key";
  private static final String USER_ORGANIZATION_KEY =
      "com.google.appengine.api.users.UserService.user_organization";
  private static final String X_APPENGINE_QUEUE_NAME = "X-AppEngine-QueueName";

  private final LoginCookieUtils.CookieData loginCookieData;

  public LocalHttpRequestEnvironment(String appId, String serverName, String majorVersionId,
      int instance, Integer port, HttpServletRequest request, Long deadlineMillis,
      ModulesFilterHelper modulesFilterHelper) {
    super(appId, serverName, majorVersionId, instance, port, deadlineMillis);
    this.loginCookieData = LoginCookieUtils.getCookieData(request);
    String requestNamespace = request.getHeader(DEFAULT_NAMESPACE_HEADER);
    if (requestNamespace != null) {
      attributes.put(APPS_NAMESPACE_KEY, requestNamespace);
    }
    String currentNamespace = request.getHeader(CURRENT_NAMESPACE_HEADER);
    if (currentNamespace != null) {
      attributes.put(CURRENT_NAMESPACE_KEY, currentNamespace);
    } else {
      // Jetty request header for CURRENT_NAMESPACE_HEADER is not set
      // when CURRENT_NAMESPACE_KEY value should be the default "" namespace, so we set it correctly
      // to default there.
      // See https://dev.eclipse.org/mhonarc/lists/jetty-users/msg03339.html
      attributes.put(CURRENT_NAMESPACE_KEY, "");
    }
    if (loginCookieData != null) {
      attributes.put(USER_ID_KEY, loginCookieData.getUserId());
      attributes.put(USER_ORGANIZATION_KEY, "");
    }
    if (request.getHeader(X_APPENGINE_QUEUE_NAME) != null) {
      attributes.put(ApiProxyLocalImpl.IS_OFFLINE_REQUEST_KEY, Boolean.TRUE);
    }
    attributes.put(HTTP_SERVLET_REQUEST, request);
    if (modulesFilterHelper != null) {
      attributes.put(DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY, modulesFilterHelper);
    }
  }

  @Override
  public boolean isLoggedIn() {
    return loginCookieData != null;
  }

  @Override
  public String getEmail() {
    if (loginCookieData == null) {
      return null;
    } else {
      return loginCookieData.getEmail();
    }
  }

  @Override
  public boolean isAdmin() {
    if (loginCookieData == null) {
      return false;
    } else {
      return loginCookieData.isAdmin();
    }
  }
}
