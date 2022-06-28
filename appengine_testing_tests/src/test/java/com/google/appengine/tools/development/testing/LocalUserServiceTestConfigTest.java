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
import com.google.apphosting.api.UserServicePb.GetOAuthUserRequest;
import junit.framework.TestCase;

/**
 * Tests for {@link LocalUserServiceTestConfig}.
 *
 */
public class LocalUserServiceTestConfigTest extends TestCase {
  private static final String OAUTH_EMAIL = "no@example.com";
  private static final String OAUTH_USER_ID = "99";
  private static final String OAUTH_AUTH_DOMAIN = "foo.com";
  private static final boolean OAUTH_IS_ADMIN = true;

  public void testConfig() {
    LocalUserServiceTestConfig config = new LocalUserServiceTestConfig();
    config.setOAuthEmail(OAUTH_EMAIL)
        .setOAuthUserId(OAUTH_USER_ID)
        .setOAuthAuthDomain(OAUTH_AUTH_DOMAIN)
        .setOAuthIsAdmin(OAUTH_IS_ADMIN);

    assertEquals(OAUTH_EMAIL, config.getOAuthEmail());
    assertEquals(OAUTH_USER_ID, config.getOAuthUserId());
    assertEquals(OAUTH_AUTH_DOMAIN, config.getOAuthAuthDomain());
    assertEquals((Boolean) OAUTH_IS_ADMIN, config.getOAuthIsAdmin());

    LocalServiceTestHelper helper = new LocalServiceTestHelper(config);
    helper.setUp();
    LocalUserService service = LocalUserServiceTestConfig.getLocalUserService();

    GetOAuthUserRequest request = GetOAuthUserRequest.getDefaultInstance();
    assertEquals(OAUTH_EMAIL, service.getOAuthUser(null, request).getEmail());
    assertEquals(OAUTH_USER_ID, service.getOAuthUser(null, request).getUserId());
    assertEquals(OAUTH_AUTH_DOMAIN, service.getOAuthUser(null, request).getAuthDomain());
    assertEquals(OAUTH_IS_ADMIN, service.getOAuthUser(null, request).getIsAdmin());
  }
}
