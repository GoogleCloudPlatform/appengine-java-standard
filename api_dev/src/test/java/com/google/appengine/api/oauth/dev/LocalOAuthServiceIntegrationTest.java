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

package com.google.appengine.api.oauth.dev;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.users.User;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the OAuthService with the local Users API implementation.
 */
@RunWith(JUnit4.class)
public class LocalOAuthServiceIntegrationTest {

  private OAuthService oauthService;
  private LocalServiceTestHelper helper;

  static final String EMAIL = "example@example.com";
  static final String AUTH_DOMAIN = "gmail.com";
  static final String USER_ID = "xxxxxxxx";
  private static final String SCOPE = "https://www.googleapis.com/auth/buzz";
  private static final String SCOPE_RO = SCOPE + ".readonly";

  @Before
  public void setUp() throws Exception {
    helper =
        new LocalServiceTestHelper(
            new LocalUserServiceTestConfig()
                .setOAuthEmail(EMAIL)
                .setOAuthUserId(USER_ID)
                .setOAuthAuthDomain(AUTH_DOMAIN)
                .setOAuthIsAdmin(true));
    helper.setUp();
    oauthService = OAuthServiceFactory.getOAuthService();
  }

  @Test
  public void testGetCurrentUser() throws Exception {
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
  }

  @Test
  public void testIsUserAdmin() throws Exception {
    assertThat(oauthService.isUserAdmin()).isTrue();
  }

  @Test
  public void testIsUserAdminWithCustomScope() throws Exception {
    assertThat(oauthService.isUserAdmin(SCOPE)).isTrue();
    assertThat(oauthService.isUserAdmin(SCOPE_RO, SCOPE)).isTrue();
    assertThat(oauthService.isUserAdmin(new String[]{SCOPE_RO, SCOPE})).isTrue();
  }

  @Test
  public void testGetOAuthConsumerKeyAlwaysFails() throws Exception {
    try {
      oauthService.getOAuthConsumerKey();
      fail("Two-legged OAuth1 is not supported any more");
    } catch (OAuthRequestException ex) {
      // Expected.
    }
  }
}
