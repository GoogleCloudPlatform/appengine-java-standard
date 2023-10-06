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

package com.google.appengine.api.users.dev.ee10;

import java.io.IOException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code LocalOAuthRequestTokenServlet} is the servlet responsible for
 * implementing the request token acquisition step of the fake OAuth
 * authentication flow provided by the Development AppServer.
 *
 */
public class LocalOAuthRequestTokenServlet extends HttpServlet {
  private static final long serialVersionUID = -4775143023488708165L;

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    handleRequest(resp);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    handleRequest(resp);
  }

  // TODO: Validate the incoming request and issue an actual token,
  // using the datastore for token storage.
  private void handleRequest(HttpServletResponse resp) throws IOException {
    resp.setContentType("text/plain");
    resp.getWriter().print("oauth_token=REQUEST_TOKEN");
    resp.getWriter().print("&");
    resp.getWriter().print("oauth_token_secret=REQUEST_TOKEN_SECRET");
  }
}
