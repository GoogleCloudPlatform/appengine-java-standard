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

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalUserServiceTestConfig;
import java.net.URLDecoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for the LocalUserService. */
@RunWith(JUnit4.class)
public class LocalUserServiceIntegrationTest {
  private UserService userService;
  private LocalServiceTestHelper helper;

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalUserServiceTestConfig());
    helper.setUp();
    userService = UserServiceFactory.getUserService();
  }

  @After
  public void tearDown() throws Exception {
    userService = null;
    helper.tearDown();
  }

  @Test
  public void testCreateLoginURL() throws Exception {
    String destURL = "http://foo.com/bar";
    String loginURL = "/_ah/login?continue=http://foo.com/bar";

    assertThat(URLDecoder.decode(userService.createLoginURL(destURL), "UTF-8")).isEqualTo(loginURL);
  }

  @Test
  public void testCreateLoginURLWithAuthDomain() throws Exception {
    String destURL = "http://foo.com/bar";
    String loginURL = "/_ah/login?continue=http://foo.com/bar";
    String authDomain = "bar.com";

    assertThat(URLDecoder.decode(userService.createLoginURL(destURL, authDomain), "UTF-8"))
        .isEqualTo(loginURL);
  }

  @Test
  public void testCreateLogoutURL() throws Exception {
    String destURL = "http://foo.com/bar";
    String logoutURL = "/_ah/logout?continue=http://foo.com/bar";

    assertThat(URLDecoder.decode(userService.createLogoutURL(destURL))).isEqualTo(logoutURL);
  }

  @Test
  public void testCreateLogoutURLWithAuthDomain() throws Exception {
    String destURL = "http://foo.com/bar";
    String logoutURL = "/_ah/logout?continue=http://foo.com/bar";
    String authDomain = "bar.com";

    assertThat(URLDecoder.decode(userService.createLogoutURL(destURL, authDomain)))
        .isEqualTo(logoutURL);
  }
}
