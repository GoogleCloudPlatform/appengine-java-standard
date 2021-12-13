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
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import com.google.apphosting.api.UserServicePb.GetOAuthUserResponse;
import com.google.apphosting.api.UserServicePb.UserServiceError;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UninitializedMessageException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Implementation of {@link OAuthService}.
 *
 */
final class OAuthServiceImpl implements OAuthService {
  // These constants must be kept in sync with the ones in
  // jgc/apphosting/client/serviceapp/AuthServiceImpl.java.
  static final String GET_OAUTH_USER_RESPONSE_KEY =
      "com.google.appengine.api.oauth.OAuthService.get_oauth_user_response";
  static final String GET_OAUTH_USER_SCOPE_KEY =
      "com.google.appengine.api.oauth.OAuthService.get_oauth_user_scope";

  private static final String PACKAGE = "user";
  private static final String GET_OAUTH_USER_METHOD = "GetOAuthUser";

  @Override
  public User getCurrentUser() throws OAuthRequestException {
    return getCurrentUser((String[]) null);
  }

  @Override
  public User getCurrentUser(String scope) throws OAuthRequestException {
    String[] scopes = {scope};
    return getCurrentUser(scopes);
  }

  @Override
  public User getCurrentUser(String... scopes) throws OAuthRequestException {
    GetOAuthUserResponse response = getGetOAuthUserResponse(scopes);
    return new User(response.getEmail(), response.getAuthDomain(),
        response.getUserId());
  }

  @Override
  public boolean isUserAdmin() throws OAuthRequestException {
    return isUserAdmin((String[]) null);
  }

  @Override
  public boolean isUserAdmin(String scope) throws OAuthRequestException {
    String[] scopes = {scope};
    return isUserAdmin(scopes);
  }

  @Override
  public boolean isUserAdmin(String... scopes) throws OAuthRequestException {
    return getGetOAuthUserResponse(scopes).getIsAdmin();
  }

  @Override
  public String getOAuthConsumerKey() throws OAuthRequestException {
    // OAuth1 support is deprecated and disabled
    throw new OAuthRequestException("Two-legged OAuth1 is not supported any more");
  }

  @Override
  public String getClientId(String scope) throws OAuthRequestException {
    String[] scopes = {scope};
    return getClientId(scopes);
  }

  @Override
  public String getClientId(String... scopes) throws OAuthRequestException {
    GetOAuthUserResponse response = getGetOAuthUserResponse(scopes);
    return response.getClientId();
  }

  @Override
  public String[] getAuthorizedScopes(String... scopes) throws OAuthRequestException {
    GetOAuthUserResponse response = getGetOAuthUserResponse(scopes);
    return response.getScopesList().toArray(new String[response.getScopesCount()]);
  }

  private GetOAuthUserResponse getGetOAuthUserResponse(String[] scopes)
      throws OAuthRequestException {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new IllegalStateException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    GetOAuthUserResponse response = (GetOAuthUserResponse)
        environment.getAttributes().get(GET_OAUTH_USER_RESPONSE_KEY);
    String scopesKey = "[]";
    if (scopes != null && scopes.length > 0) {
      String[] scopesCopy = scopes.clone();
      Arrays.sort(scopesCopy);
      scopesKey = Arrays.toString(scopesCopy);
    }
    String lastScopesKey = (String) environment.getAttributes().get(GET_OAUTH_USER_SCOPE_KEY);
    if (response == null || !Objects.equals(lastScopesKey, scopesKey)) {
      GetOAuthUserRequest.Builder request = GetOAuthUserRequest.newBuilder();
      if (scopes != null) {
        for (String scope : scopes) {
          request.addScopes(scope);
        }
      }
      byte[] responseBytes = makeSyncCall(GET_OAUTH_USER_METHOD, request.build());
      try {
        response =
            GetOAuthUserResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
      } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
        throw new OAuthServiceFailureException("Could not parse GetOAuthUserResponse", e);
      }
      // WARNING: in the development server attributes is a ConcurrentHashMap which does not allow
      // null keys or values.
      environment.getAttributes().put(GET_OAUTH_USER_RESPONSE_KEY, response);
      environment.getAttributes().put(GET_OAUTH_USER_SCOPE_KEY, scopesKey);
    }
    return response;
  }

  private byte[] makeSyncCall(String methodName, MessageLite request)
      throws OAuthRequestException {
    byte[] responseBytes;
    try {
      byte[] requestBytes = request.toByteArray();
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, methodName, requestBytes);
    } catch (ApiProxy.ApplicationException ex) {
      UserServiceError.ErrorCode errorCode =
          UserServiceError.ErrorCode.forNumber(ex.getApplicationError());
      switch (errorCode) {
        case NOT_ALLOWED:
        case OAUTH_INVALID_REQUEST:
          throw new InvalidOAuthParametersException(ex.getErrorDetail());
        case OAUTH_INVALID_TOKEN:
          throw new InvalidOAuthTokenException(ex.getErrorDetail());
        case OAUTH_ERROR:
        default:
          throw new OAuthServiceFailureException(ex.getErrorDetail());
      }
    }

    return responseBytes;
  }

}
