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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.users.dev.LocalUserService;
import com.google.appengine.tools.development.ApiProxyLocal;
import org.jspecify.annotations.Nullable;

/**
 * Config for accessing the local user service in tests.
 *
 */
public class LocalUserServiceTestConfig implements LocalServiceTestConfig {

  @Nullable private String oauthConsumerKey;
  @Nullable private String oauthEmail;
  @Nullable private String oauthUserId;
  @Nullable private String oauthAuthDomain;
  @Nullable private Boolean oauthIsAdmin;

  /**
   * Consumer key to return from {@link com.google.appengine.api.oauth#getOAuthConsumerKey}.
   * @param oauthConsumerKey
   * @return {@code this} (for chaining)
   */
  public LocalUserServiceTestConfig setOAuthConsumerKey(String oauthConsumerKey) {
    this.oauthConsumerKey = oauthConsumerKey;
    return this;
  }

  /**
   * Return configured consumer key.
   * @return configured consumer key.
   */
  public String getOAuthConsumerKey() {
    return oauthConsumerKey;
  }

  /**
   * Email to return from {@link com.google.appengine.api.oauth#getCurrentUser}.
   * @param oauthEmail
   * @return {@code this} (for chaining)
   */
  public LocalUserServiceTestConfig setOAuthEmail(String oauthEmail) {
    this.oauthEmail = oauthEmail;
    return this;
  }

  /**
   * Return configured email.
   * @return configured email.
   */
  public String getOAuthEmail() {
    return oauthEmail;
  }

  /**
   * User ID to return from {@link com.google.appengine.api.oauth#getCurrentUser}.
   * @param oauthUserId
   * @return {@code this} (for chaining)
   */
  public LocalUserServiceTestConfig setOAuthUserId(String oauthUserId) {
    this.oauthUserId = oauthUserId;
    return this;
  }

  /**
   * Return configured user ID.
   * @return configured user ID.
   */
  public String getOAuthUserId() {
    return oauthUserId;
  }

  /**
   * Auth domain to return from {@link com.google.appengine.api.oauth#getCurrentUser}.
   * @param oauthAuthDomain
   * @return {@code this} (for chaining)
   */
  public LocalUserServiceTestConfig setOAuthAuthDomain(String oauthAuthDomain) {
    this.oauthAuthDomain = oauthAuthDomain;
    return this;
  }

  /**
   * Return configured auth domain.
   * @return configured auth domain.
   */
  public String getOAuthAuthDomain() {
    return oauthAuthDomain;
  }

  /**
   * Value to return from {@link com.google.appengine.api.oauth#isUserAdmin}.
   * @param oauthIsAdmin
   * @return {@code this} (for chaining)
   */
  public LocalUserServiceTestConfig setOAuthIsAdmin(boolean oauthIsAdmin) {
    this.oauthIsAdmin = oauthIsAdmin;
    return this;
  }

  /**
   * Return configured isAdmin value.
   * @return configured isAdmin value.
   */
  public Boolean getOAuthIsAdmin() {
    return oauthIsAdmin;
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    if (oauthConsumerKey != null) {
      proxy.setProperty(LocalUserService.OAUTH_CONSUMER_KEY_PROPERTY, oauthConsumerKey);
    }
    if (oauthEmail != null) {
      proxy.setProperty(LocalUserService.OAUTH_EMAIL_PROPERTY, oauthEmail);
    }
    if (oauthUserId != null) {
      proxy.setProperty(LocalUserService.OAUTH_USER_ID_PROPERTY, oauthUserId);
    }
    if (oauthAuthDomain != null) {
      proxy.setProperty(LocalUserService.OAUTH_AUTH_DOMAIN_PROPERTY, oauthAuthDomain);
    }
    if (oauthIsAdmin != null) {
      proxy.setProperty(LocalUserService.OAUTH_IS_ADMIN_PROPERTY, Boolean.toString(oauthIsAdmin));
    }
  }

  @Override
  public void tearDown() {
    // Stateless so nothing to clean up
  }

  public static LocalUserService getLocalUserService() {
    return (LocalUserService) LocalServiceTestHelper.getLocalService(LocalUserService.PACKAGE);
  }
}
