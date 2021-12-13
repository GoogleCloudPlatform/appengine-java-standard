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

package com.google.appengine.api.oauth;

import com.google.appengine.api.users.User;

/**
 * The OAuthService provides methods useful for validating OAuth requests.
 *
 * @see <a href="http://tools.ietf.org/html/rfc5849">RFC 5849</a> for
 * the OAuth specification.
 */
public interface OAuthService {
  /**
   * Returns the {@link User} on whose behalf the request was made.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  User getCurrentUser() throws OAuthRequestException;

  /**
   * Returns the {@link User} on whose behalf the request was made.
   * @param scope The custom OAuth scope that is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  User getCurrentUser(String scope) throws OAuthRequestException;

  /**
   * Returns the {@link User} on whose behalf the request was made.
   * @param scopes The custom OAuth scopes at least one of which is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  User getCurrentUser(String... scopes) throws OAuthRequestException;

  /**
   * Returns true if the user on whose behalf the request was made is an admin
   * for this application, false otherwise.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  boolean isUserAdmin() throws OAuthRequestException;

  /**
   * Returns true if the user on whose behalf the request was made is an admin
   * for this application, false otherwise.
   * @param scope The custom OAuth scope that is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   *
   * @since App Engine 1.7.3.
   */
  boolean isUserAdmin(String scope) throws OAuthRequestException;

  /**
   * Returns true if the user on whose behalf the request was made is an admin
   * for this application, false otherwise.
   * @param scopes The custom OAuth scopes at least one of which is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  boolean isUserAdmin(String... scopes) throws OAuthRequestException;

  /**
   * Throws OAuthRequestException
   *
   * @deprecated OAuth1 is no longer supported
   */
  @Deprecated
  String getOAuthConsumerKey() throws OAuthRequestException;

  /**
   * Returns the client_id from oauth2 request.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth2 request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  String getClientId(String scope) throws OAuthRequestException;

  /**
   * Returns the client_id from oauth2 request.
   * @param scopes The custom OAuth scopes at least one of which is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth2 request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  String getClientId(String... scopes) throws OAuthRequestException;

  /**
   * Return authorized scopes from input scopes.
   * @param scopes The custom OAuth scopes at least one of which is accepted.
   *
   * @throws OAuthRequestException If the request was not a valid OAuth2 request.
   * @throws OAuthServiceFailureException If an unknown OAuth error occurred.
   */
  String[] getAuthorizedScopes(String... scopes) throws OAuthRequestException;
}
