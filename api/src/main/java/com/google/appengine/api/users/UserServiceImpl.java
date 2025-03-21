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

package com.google.appengine.api.users;

import static java.util.Objects.requireNonNull;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.CreateLoginURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLoginURLResponse;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLResponse;
import com.google.apphosting.api.UserServicePb.UserServiceError;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UninitializedMessageException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The UserService provides information useful for forcing a user to
 * log in or out, and retrieving information about the user who is
 * currently logged-in.
 *
 */
final class UserServiceImpl implements UserService {
  static final String USER_ID_KEY =
      "com.google.appengine.api.users.UserService.user_id_key";

  static final String FEDERATED_IDENTITY_KEY =
    "com.google.appengine.api.users.UserService.federated_identity";

  static final String FEDERATED_AUTHORITY_KEY =
    "com.google.appengine.api.users.UserService.federated_authority";

  static final String IS_FEDERATED_USER_KEY =
    "com.google.appengine.api.users.UserService.is_federated_user";

  private static final String PACKAGE = "user";
  private static final String LOGIN_URL_METHOD = "CreateLoginURL";
  private static final String LOGOUT_URL_METHOD = "CreateLogoutURL";

  private static final String OPENID_DECOMMISSION_ERROR =
      "Open ID 2.0 support in the App Engine Users service is decommissioned. Please see "
          + "https://cloud.google.com/appengine/docs/deprecations/open_id "
          + "for details.";

  @Override
  public String createLoginURL(String destinationURL) {
    return createLoginURL(destinationURL, null);
  }

  @Override
  public String createLoginURL(String destinationURL, @Nullable String authDomain) {
    CreateLoginURLRequest.Builder request =
        CreateLoginURLRequest.newBuilder().setDestinationUrl(destinationURL);
    if (authDomain != null) {
      request.setAuthDomain(authDomain);
    }
    byte[] responseBytes = makeSyncCall(LOGIN_URL_METHOD, request.build(), destinationURL);
    CreateLoginURLResponse response;
    try {
      response =
          CreateLoginURLResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
      throw new UserServiceFailureException("Could not parse CreateLoginURLResponse", e);
    }
    return response.getLoginUrl();
  }

  public String createLoginURL(
      String destinationURL,
      String authDomain,
      String federatedIdentity,
      Set<String> attributesRequest) {
    if (federatedIdentity != null) {
      throw new IllegalArgumentException(OPENID_DECOMMISSION_ERROR);
    }
    return createLoginURL(destinationURL, authDomain);
  }

  @Override
  public String createLogoutURL(String destinationURL) {
    return createLogoutURL(destinationURL, null);
  }

  @Override
  public String createLogoutURL(String destinationURL, @Nullable String authDomain) {
    CreateLogoutURLRequest.Builder request =
        CreateLogoutURLRequest.newBuilder().setDestinationUrl(destinationURL);
    if (authDomain != null) {
      request.setAuthDomain(authDomain);
    }
    byte[] responseBytes = makeSyncCall(LOGOUT_URL_METHOD, request.build(), destinationURL);
    CreateLogoutURLResponse response;
    try {
      response =
          CreateLogoutURLResponse.parseFrom(
              responseBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
      throw new UserServiceFailureException("Could not parse CreateLogoutURLResponse", e);
    }
    return response.getLogoutUrl();
  }

  @Override
  public boolean isUserLoggedIn() {
    ApiProxy.Environment environment = getCurrentEnvironmentOrThrow();
    return environment.isLoggedIn();
  }

  @Override
  public boolean isUserAdmin() {
    if (isUserLoggedIn()) {
      return getCurrentEnvironmentOrThrow().isAdmin();
    } else {
      throw new IllegalStateException("The current user is not logged in.");
    }
  }

  @Override
  public @Nullable User getCurrentUser() {
    ApiProxy.Environment environment = getCurrentEnvironmentOrThrow();
    if (!environment.isLoggedIn()) {
      return null;
    }
    String userId = (String) environment.getAttributes().get(USER_ID_KEY);
    Boolean isFederated = (Boolean) environment.getAttributes().get(IS_FEDERATED_USER_KEY);
    if ((isFederated == null) || !isFederated) {
        return new User(environment.getEmail(), environment.getAuthDomain(), userId);
    } else {
      String authority =
          requireNonNull((String) environment.getAttributes().get(FEDERATED_AUTHORITY_KEY));
      String identity =
          requireNonNull((String) environment.getAttributes().get(FEDERATED_IDENTITY_KEY));
      return new User(environment.getEmail(), authority, userId, identity);
    }
  }

  private byte[] makeSyncCall(
      String methodName, MessageLite request, String destinationURL) {
    byte[] responseBytes;
    try {
      byte[] requestBytes = request.toByteArray();
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, methodName, requestBytes);
    } catch (ApiProxy.ApplicationException ex) {
      UserServiceError.ErrorCode errorCode =
          UserServiceError.ErrorCode.forNumber(ex.getApplicationError());
      switch (errorCode) {
        case REDIRECT_URL_TOO_LONG:
          throw new IllegalArgumentException("URL too long: " + destinationURL);
        case NOT_ALLOWED:
          throw new IllegalArgumentException(
              "The requested URL was not allowed: " + destinationURL);
        default:
          // the python stub just rethrows, but I don't think we should be
          // letting ApiProxy.ApplicationException propagate above the api
          // layer.  Also, having an api-specific failure exception is consistent with
          // the datastore api.
          throw new UserServiceFailureException(ex.getErrorDetail());
      }
    }

    return responseBytes;
  }

  private static ApiProxy.Environment getCurrentEnvironmentOrThrow() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new IllegalStateException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    return environment;
  }
}
