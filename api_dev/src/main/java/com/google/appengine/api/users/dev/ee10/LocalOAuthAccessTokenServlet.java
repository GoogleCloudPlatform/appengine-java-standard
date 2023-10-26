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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code LocalOAuthAccessTokenServlet} is the servlet responsible for
 * implementing the access token acquisition step of the fake OAuth
 * authentication flow provided by the Development AppServer.
 *
 */
public class LocalOAuthAccessTokenServlet extends HttpServlet {
  private static final long serialVersionUID = -2295106902703316041L;

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
    resp.getWriter().print("oauth_token=ACCESS_TOKEN");
    resp.getWriter().print("&");
    resp.getWriter().print("oauth_token_secret=ACCESS_TOKEN_SECRET");
  }
}
