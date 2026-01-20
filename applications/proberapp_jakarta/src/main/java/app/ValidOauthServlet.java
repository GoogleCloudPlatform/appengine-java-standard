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
package app;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.oauth.OAuthServiceFailureException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** A servlet that tests the OAuth service with a valid OAuth request. */
@WebServlet(name = "ValidOauthServlet", value = "/validoauth")
public class ValidOauthServlet extends HttpServlet {
  private static final String SCOPE = "https://www.googleapis.com/auth/userinfo.email";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthService oauthService = OAuthServiceFactory.getOAuthService();

    try {
      assertThat(oauthService.getCurrentUser(SCOPE)).isNotNull();
    } catch (OAuthRequestException | OAuthServiceFailureException e) {
      throw new AssertionError("getCurrentUser() failed", e);
    }

    try {
      assertThat(oauthService.isUserAdmin()).isTrue();
    } catch (OAuthRequestException | OAuthServiceFailureException e) {
      throw new AssertionError("isUserAdmin() failed", e);
    }

    try {
      assertThat(oauthService.getClientId(SCOPE)).isNotEmpty();
    } catch (OAuthRequestException | OAuthServiceFailureException e) {
      throw new AssertionError("getClientId() failed", e);
    }

    try {
      String[] scopes = oauthService.getAuthorizedScopes(SCOPE);
      assertThat(scopes).hasLength(1);
      assertThat(scopes[0]).isEqualTo(SCOPE);
    } catch (OAuthRequestException | OAuthServiceFailureException e) {
      throw new AssertionError("getAuthorizedScopes() failed", e);
    }

    resp.getWriter().println("ValidOauth passed");
  }
}
