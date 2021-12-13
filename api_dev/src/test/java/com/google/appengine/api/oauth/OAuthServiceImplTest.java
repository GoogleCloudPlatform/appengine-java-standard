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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.testing.MockEnvironment;
import com.google.appengine.api.users.User;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import com.google.apphosting.api.UserServicePb.GetOAuthUserResponse;
import com.google.apphosting.api.UserServicePb.UserServiceError;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for OAuthServiceImpl.
 *
 */
@RunWith(JUnit4.class)
public class OAuthServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  private ApiProxy.Environment environment;
  private OAuthService oauthService;

  private static final String EMAIL = "example@example.com";
  private static final String AUTH_DOMAIN = "gmail.com";
  private static final String USER_ID = "xxxxxxxx";
  private static final String SCOPE = "https://www.googleapis.com/auth/buzz";
  private static final String SCOPE_RO = SCOPE + ".readonly";
  private static final String CLIENT_ID = "123456789.apps.googleusercontent.com";
  private static final String[] AUTHORIZED_SCOPES = {SCOPE};

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);

    environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);

    oauthService = new OAuthServiceImpl();
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  private void setGetOAuthUserMocks() {
    GetOAuthUserRequest request = GetOAuthUserRequest.getDefaultInstance();
    GetOAuthUserResponse response = buildResponse().setIsAdmin(true).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray())
        .thenThrow(new AssertionError("Response should be cached"));
  }

  private void setGetOAuthUserWithErrorMocks(
      UserServiceError.ErrorCode error, String errorMessage) {
    GetOAuthUserRequest request = GetOAuthUserRequest.getDefaultInstance();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray())))
        .thenThrow(new ApiProxy.ApplicationException(error.getNumber(), errorMessage));
  }

  private void setGetOAuthUserWithScopeMocks() {
    // Do not mix successful mocks with and without scopes as it can hide errors.
    // Calls with scopes are using OAuth2 and those without OAuth1.
    // If a token is a valid OAuth2 one it will be and invalid OAuth1 token and viceversa.
    GetOAuthUserRequest request = GetOAuthUserRequest.newBuilder().addScopes(SCOPE).build();
    GetOAuthUserRequest request2 =
        GetOAuthUserRequest.newBuilder().addScopes(SCOPE_RO).addScopes(SCOPE).build();

    // Note: calls to GetOAuthUser using different scopes will return the same User
    // The mocks will verify that the respective calls are actually done.
    GetOAuthUserResponse response = buildResponse().setIsAdmin(true).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray());
    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request2.toByteArray())))
        .thenReturn(response.toByteArray());
  }

  private void verifyGetOAuthUserWithScopeMocks() {
    GetOAuthUserRequest request = GetOAuthUserRequest.newBuilder().addScopes(SCOPE).build();
    GetOAuthUserRequest request2 =
        GetOAuthUserRequest.newBuilder().addScopes(SCOPE_RO).addScopes(SCOPE).build();
    InOrder inOrder = inOrder(delegate);
    inOrder.verify(delegate).makeSyncCall(
        same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray()));
    inOrder.verify(delegate).makeSyncCall(
        same(environment), eq("user"), eq("GetOAuthUser"), eq(request2.toByteArray()));
    inOrder.verify(delegate).makeSyncCall(
        same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray()));
    verifyNoMoreInteractions(delegate);
  }

  private GetOAuthUserResponse.Builder buildResponse() {
    return GetOAuthUserResponse.newBuilder()
        .setEmail(EMAIL)
        .setUserId(USER_ID)
        .setAuthDomain(AUTH_DOMAIN)
        .setClientId(CLIENT_ID)
        .addScopes(SCOPE);
  }

  @Test
  public void testGetCurrentUser() throws Exception {
    setGetOAuthUserMocks();
    User user = oauthService.getCurrentUser();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getUserId()).isEqualTo(USER_ID);
    assertThat(user.getAuthDomain()).isEqualTo(AUTH_DOMAIN);
    User user2 = oauthService.getCurrentUser();
    assertThat(user2).isEqualTo(user);
    User user3 = oauthService.getCurrentUser((String[]) null);
    assertThat(user3).isEqualTo(user);
    User user4 = oauthService.getCurrentUser("");
    assertThat(user4).isEqualTo(user);
  }

  @Test
  public void testGetCurrentUserWithCustomScope() throws Exception {
    setGetOAuthUserWithScopeMocks();
    User user = oauthService.getCurrentUser(SCOPE);
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getUserId()).isEqualTo(USER_ID);
    assertThat(user.getAuthDomain()).isEqualTo(AUTH_DOMAIN);
    User user2 = oauthService.getCurrentUser(SCOPE_RO, SCOPE);
    User user3 = oauthService.getCurrentUser(SCOPE_RO, SCOPE);
    User user4 = oauthService.getCurrentUser(SCOPE);
    assertThat(user2).isEqualTo(user);
    assertThat(user3).isEqualTo(user);
    assertThat(user4).isEqualTo(user);
    verifyGetOAuthUserWithScopeMocks();
  }

  @Test
  public void testGetCurrentUserNotAllowed() throws Exception {
    setGetOAuthUserWithErrorMocks(UserServiceError.ErrorCode.NOT_ALLOWED, "Not allowed");
    assertThrows(InvalidOAuthParametersException.class, () -> oauthService.getCurrentUser());
  }

  @Test
  public void testGetCurrentUserInvalidRequest() throws Exception {
    setGetOAuthUserWithErrorMocks(UserServiceError.ErrorCode.OAUTH_INVALID_REQUEST, "GetOAuthUser");
    assertThrows(InvalidOAuthParametersException.class, () -> oauthService.getCurrentUser());
  }

  @Test
  public void testGetCurrentUserInvalidToken() throws Exception {
    setGetOAuthUserWithErrorMocks(UserServiceError.ErrorCode.OAUTH_INVALID_TOKEN, "Invalid token");
    assertThrows(InvalidOAuthTokenException.class, () -> oauthService.getCurrentUser());
  }

  @Test
  public void testGetCurrentUserError() throws Exception {
    setGetOAuthUserWithErrorMocks(UserServiceError.ErrorCode.OAUTH_ERROR, "Error");
    assertThrows(OAuthServiceFailureException.class, () -> oauthService.getCurrentUser());
  }

  @Test
  public void testIsUserAdmin() throws Exception {
    setGetOAuthUserMocks();
    assertThat(oauthService.isUserAdmin()).isTrue();
    assertThat(oauthService.isUserAdmin()).isTrue();
    assertThat(oauthService.isUserAdmin()).isTrue();
  }

  @Test
  public void testIsUserAdminWithCustomScope() throws Exception {
    setGetOAuthUserWithScopeMocks();
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    assertThat(oauthService.isUserAdmin(SCOPE_RO, SCOPE)).isTrue();
    assertThat(oauthService.isUserAdmin(new String[] {SCOPE_RO, SCOPE})).isTrue();
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    verifyGetOAuthUserWithScopeMocks();
  }

  @Test
  public void testGetOAuthUserCallsCached() throws Exception {
    setGetOAuthUserMocks();
    User user = oauthService.getCurrentUser();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(oauthService.isUserAdmin()).isTrue();
  }

  @Test
  public void testGetOAuthUserCallsCachedDifferentScope() throws Exception {
    setGetOAuthUserWithScopeMocks();
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    User user = oauthService.getCurrentUser(SCOPE);
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    User user2 = oauthService.getCurrentUser(SCOPE_RO, SCOPE);
    assertThat(user2).isEqualTo(user);
    User user3 = oauthService.getCurrentUser(SCOPE);
    assertThat(user3).isEqualTo(user);
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    verifyGetOAuthUserWithScopeMocks();
  }

  @Test
  public void testGetOAuthUserErrorsNotCached() throws Exception {
    setGetOAuthUserWithErrorMocks(UserServiceError.ErrorCode.OAUTH_INVALID_REQUEST, "GetOAuthUser");
    assertThrows(InvalidOAuthParametersException.class, () -> oauthService.getCurrentUser());
    assertThrows(InvalidOAuthParametersException.class, () -> oauthService.isUserAdmin());
  }

  @Test
  public void testGetOAuthConsumerKeyAlwaysFails() throws Exception {
    assertThrows(OAuthRequestException.class, () -> oauthService.getOAuthConsumerKey());
  }

  @Test
  public void testGetClientId() throws Exception {
    setGetOAuthUserWithScopeMocks();
    oauthService.getCurrentUser(SCOPE);
    assertThat(oauthService.getClientId(SCOPE)).isEqualTo(CLIENT_ID); // use cache
    assertThat(oauthService.getClientId(SCOPE_RO, SCOPE)).isEqualTo(CLIENT_ID);
    assertThat(oauthService.getClientId(new String[] {SCOPE_RO, SCOPE}))
        .isEqualTo(CLIENT_ID); // use cache
    assertThat(oauthService.getClientId(SCOPE)).isEqualTo(CLIENT_ID);
    verifyGetOAuthUserWithScopeMocks();
  }

  @Test
  public void testGetClientIdError() throws Exception {
    GetOAuthUserRequest request = GetOAuthUserRequest.newBuilder().addScopes(SCOPE).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                UserServiceError.ErrorCode.OAUTH_INVALID_REQUEST.getNumber(), "Error"));

    assertThrows(InvalidOAuthParametersException.class, () -> oauthService.getClientId(SCOPE));
  }

  @Test
  public void testGetAuthorizedScopes() throws Exception {
    setGetOAuthUserWithScopeMocks();
    oauthService.getCurrentUser(SCOPE);
    // Use cache.
    assertThat(oauthService.getAuthorizedScopes(SCOPE)).isEqualTo(AUTHORIZED_SCOPES);
    assertThat(oauthService.getAuthorizedScopes(SCOPE_RO, SCOPE)).isEqualTo(AUTHORIZED_SCOPES);
    assertThat(oauthService.getAuthorizedScopes(new String[] {SCOPE_RO, SCOPE}))
        .isEqualTo(AUTHORIZED_SCOPES); // Use cache.
    assertThat(oauthService.getAuthorizedScopes(SCOPE)).isEqualTo(AUTHORIZED_SCOPES);
    verifyGetOAuthUserWithScopeMocks();
  }

  @Test
  public void testGetAuthorizedScopesError() throws Exception {
    GetOAuthUserRequest request = GetOAuthUserRequest.newBuilder().addScopes(SCOPE).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("GetOAuthUser"), eq(request.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                UserServiceError.ErrorCode.OAUTH_INVALID_REQUEST.getNumber(), "Error"));

    assertThrows(
        InvalidOAuthParametersException.class, () -> oauthService.getAuthorizedScopes(SCOPE));
  }
}
