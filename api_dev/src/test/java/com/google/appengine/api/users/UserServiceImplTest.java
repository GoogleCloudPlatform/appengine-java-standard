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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.UserServicePb.CreateLoginURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLoginURLResponse;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLRequest;
import com.google.apphosting.api.UserServicePb.CreateLogoutURLResponse;
import com.google.apphosting.api.UserServicePb.UserServiceError;
import com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for the UserServiceImpl implementation.
 *
 */
@RunWith(JUnit4.class)
public class UserServiceImplTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  @Mock private ApiProxy.Environment environment;
  private UserService userService;

  private static final String NICK = "foo";
  private static final String AUTH_DOMAIN = "bar.com";
  private static final String EMAIL = "foo@bar.com";
  private static final String USER_ID = "xxxxxxx";
  private static final String IDENTITY = "http://me.yahoo.com";
  private static final String AUTHORITY = "yahoo.com";

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(environment);

    userService = new UserServiceImpl();
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test
  public void testCreateLoginURL() throws Exception {
    String destURL = "http://foo.com/bar";
    String loginURL = "http://login?continue=http://foo.com/bar";

    CreateLoginURLRequest request =
        CreateLoginURLRequest.newBuilder().setDestinationUrl(destURL).build();
    CreateLoginURLResponse response =
        CreateLoginURLResponse.newBuilder().setLoginUrl(loginURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLoginURL"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray());
    assertThat(userService.createLoginURL(destURL)).isEqualTo(loginURL);
  }

  @Test
  public void testCreateLoginURLWithAuthDomain() throws Exception {
    String destURL = "http://foo.com/bar";
    String loginURL = "http://login?continue=http://foo.com/bar";
    String authDomain = "bar.com";

    CreateLoginURLRequest request =
        CreateLoginURLRequest.newBuilder()
            .setDestinationUrl(destURL)
            .setAuthDomain(authDomain)
            .build();
    CreateLoginURLResponse response =
        CreateLoginURLResponse.newBuilder().setLoginUrl(loginURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLoginURL"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray());
    assertThat(userService.createLoginURL(destURL, authDomain)).isEqualTo(loginURL);
  }

  @Test
  public void testCreateLogoutURL() throws Exception {
    String destURL = "http://foo.com/bar";
    String logoutURL = "http://logout?continue=http://foo.com/bar";

    CreateLogoutURLRequest request =
        CreateLogoutURLRequest.newBuilder().setDestinationUrl(destURL).build();
    CreateLogoutURLResponse response =
        CreateLogoutURLResponse.newBuilder().setLogoutUrl(logoutURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLogoutURL"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray());
    assertThat(userService.createLogoutURL(destURL)).isEqualTo(logoutURL);
  }

  @Test
  public void testCreateLogoutURLWithAuthDomain() throws Exception {
    String destURL = "http://foo.com/bar";
    String logoutURL = "http://logout?continue=http://foo.com/bar";
    String authDomain = "bar.com";

    CreateLogoutURLRequest request =
        CreateLogoutURLRequest.newBuilder()
            .setDestinationUrl(destURL)
            .setAuthDomain(authDomain)
            .build();
    CreateLogoutURLResponse response =
        CreateLogoutURLResponse.newBuilder().setLogoutUrl(logoutURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLogoutURL"), eq(request.toByteArray())))
        .thenReturn(response.toByteArray());
    assertThat(userService.createLogoutURL(destURL, authDomain)).isEqualTo(logoutURL);
  }

  @Test
  public void testIsUserAdminFalse() throws Exception {
    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.isAdmin()).thenReturn(false);
    assertThat(userService.isUserAdmin()).isFalse();
  }

  @Test
  public void testIsUserAdminTrue() throws Exception {
    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.isAdmin()).thenReturn(true);
    assertThat(userService.isUserAdmin()).isTrue();
  }

  @Test
  public void testIsUserAdminNotLoggedIn() throws Exception {
    when(environment.isLoggedIn()).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> userService.isUserAdmin());
  }

  @Test
  public void testGetCurrentUserLoggedIn() throws Exception {
    ImmutableMap<String, Object> attributes =
        ImmutableMap.of(
            UserServiceImpl.USER_ID_KEY, USER_ID, UserServiceImpl.IS_FEDERATED_USER_KEY, false);

    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.getAttributes()).thenReturn(attributes);
    when(environment.getEmail()).thenReturn(EMAIL);
    when(environment.getAuthDomain()).thenReturn(AUTH_DOMAIN);
    User user = userService.getCurrentUser();
    assertThat(user.getNickname()).isEqualTo(NICK);
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getAuthDomain()).isEqualTo(AUTH_DOMAIN);
    assertThat(user.getUserId()).isEqualTo(USER_ID);
  }

  @Test
  public void testGetCurrentUserLoggedInNoUserId() throws Exception {
    ImmutableMap<String, Object> attributes =
        ImmutableMap.of(UserServiceImpl.IS_FEDERATED_USER_KEY, false);

    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.getAttributes()).thenReturn(attributes);

    when(environment.getEmail()).thenReturn(EMAIL);
    when(environment.getAuthDomain()).thenReturn(AUTH_DOMAIN);
    User user = userService.getCurrentUser();
    assertThat(user.getNickname()).isEqualTo(NICK);
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getAuthDomain()).isEqualTo(AUTH_DOMAIN);
    assertThat(user.getUserId()).isEqualTo(null);
  }

  @Test
  public void testGetCurrentUserLoggedOut() throws Exception {
    when(environment.isLoggedIn()).thenReturn(false);
    assertThat(userService.getCurrentUser()).isNull();
  }

  @Test
  public void testCreateLoginURLTooLong() throws Exception {
    String destURL = "http://foo.com/bar";

    CreateLoginURLRequest request =
        CreateLoginURLRequest.newBuilder().setDestinationUrl(destURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLoginURL"), eq(request.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                UserServiceError.ErrorCode.REDIRECT_URL_TOO_LONG.getNumber(), "Too long."));
    assertThrows(IllegalArgumentException.class, () -> userService.createLoginURL(destURL));
  }

  @Test
  public void testCreateLoginURLNotAllowed() throws Exception {
    String destURL = "http://foo.com/bar";

    CreateLoginURLRequest request =
        CreateLoginURLRequest.newBuilder().setDestinationUrl(destURL).build();

    when(delegate.makeSyncCall(
            same(environment), eq("user"), eq("CreateLoginURL"), eq(request.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                UserServiceError.ErrorCode.NOT_ALLOWED.getNumber(), "Not allowed."));
    assertThrows(IllegalArgumentException.class, () -> userService.createLoginURL(destURL));
  }

  // Federated users tests
  @Test
  public void testFederatedCreateLoginURLNotAllowed() throws Exception {
    String destURL = "http://foo.com/bar";
    String claimedId = "http://bar.com";
    String authority = "yahoo.com";

    assertThrows(
        IllegalArgumentException.class,
        () -> userService.createLoginURL(destURL, authority, claimedId, null));
  }

  @Test
  public void testFederatedGetCurrentUserLoggedIn() throws Exception {
    ImmutableMap<String, Object> attributes =
        ImmutableMap.of(
            UserServiceImpl.USER_ID_KEY, USER_ID,
            UserServiceImpl.IS_FEDERATED_USER_KEY, true,
            UserServiceImpl.FEDERATED_IDENTITY_KEY, IDENTITY,
            UserServiceImpl.FEDERATED_AUTHORITY_KEY, AUTHORITY);

    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.getAttributes()).thenReturn(attributes);
    when(environment.getEmail()).thenReturn(EMAIL);
    User user = userService.getCurrentUser();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getFederatedIdentity()).isEqualTo(IDENTITY);
    assertThat(user.getAuthDomain()).isEqualTo(AUTHORITY);
    assertThat(user.getUserId()).isEqualTo(USER_ID);
  }

  @Test
  public void testFederatedGetCurrentUserLoggedInNoUserId() throws Exception {
    ImmutableMap<String, Object> attributes =
        ImmutableMap.of(
            UserServiceImpl.IS_FEDERATED_USER_KEY, true,
            UserServiceImpl.FEDERATED_IDENTITY_KEY, IDENTITY,
            UserServiceImpl.USER_ID_KEY, USER_ID,
            UserServiceImpl.FEDERATED_AUTHORITY_KEY, AUTHORITY);

    when(environment.isLoggedIn()).thenReturn(true);
    when(environment.getAttributes()).thenReturn(attributes);
    when(environment.getEmail()).thenReturn(EMAIL);
    User user = userService.getCurrentUser();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getUserId()).isEqualTo(USER_ID);
  }

  @Test
  public void testFederatedGetCurrentUserLoggedOut() throws Exception {
    when(environment.isLoggedIn()).thenReturn(false);
    assertThat(userService.getCurrentUser()).isNull();
  }
}
