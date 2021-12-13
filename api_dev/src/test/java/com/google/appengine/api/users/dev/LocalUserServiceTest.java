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

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import com.google.apphosting.api.UserServicePb.GetOAuthUserResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for LocalUserService.
 *
 */
@RunWith(JUnit4.class)
public class LocalUserServiceTest {
  private static final String OAUTH_EMAIL = "no@example.com";
  private static final String OAUTH_USER_ID = "99";
  private static final String OAUTH_AUTH_DOMAIN = "foo.com";
  private static final boolean OAUTH_IS_ADMIN = true;

  private LocalServiceTestHelper helper;
  private LocalRpcService.Status status;

  @Before
  public void setUp() throws Exception {
    status = new LocalRpcService.Status();
  }

  @After
  public void tearDown() throws Exception {
    if (helper != null) {
      helper.tearDown();
    }
  }

  @Test
  public void testGetOauthUser_Default() throws Exception {
    LocalUserService service = useDefaultConfig();
    GetOAuthUserResponse response =
        service.getOAuthUser(status, GetOAuthUserRequest.getDefaultInstance());
    assertThat(response.getEmail()).isEqualTo("example@example.com");
    assertThat(response.getUserId()).isEqualTo("0");
    assertThat(response.getAuthDomain()).isEqualTo("gmail.com");
    assertThat(response.getIsAdmin()).isFalse();
  }

  @Test
  public void testGetOauthUser_Test() throws Exception {
    LocalUserService service = useTestConfig();
    GetOAuthUserResponse response =
        service.getOAuthUser(status, GetOAuthUserRequest.getDefaultInstance());
    assertThat(response.getEmail()).isEqualTo(OAUTH_EMAIL);
    assertThat(response.getUserId()).isEqualTo(OAUTH_USER_ID);
    assertThat(response.getAuthDomain()).isEqualTo(OAUTH_AUTH_DOMAIN);
    assertThat(response.getIsAdmin()).isEqualTo(OAUTH_IS_ADMIN);
  }

  private LocalUserService useDefaultConfig() {
    helper = new LocalServiceTestHelper(new LocalUserServiceTestConfig());
    helper.setUp();
    return LocalUserServiceTestConfig.getLocalUserService();
  }

  private LocalUserService useTestConfig() {
    helper =
        new LocalServiceTestHelper(
            new LocalUserServiceTestConfig()
                .setOAuthEmail(OAUTH_EMAIL)
                .setOAuthUserId(OAUTH_USER_ID)
                .setOAuthAuthDomain(OAUTH_AUTH_DOMAIN)
                .setOAuthIsAdmin(OAUTH_IS_ADMIN));
    helper.setUp();
    return LocalUserServiceTestConfig.getLocalUserService();
  }
}
