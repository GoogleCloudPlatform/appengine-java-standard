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

package com.google.appengine.api.users.dev.jakarta;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code LocalLogoutServlet} is the servlet responsible for logging
 * the current user out of the fake authentication provided by the
 * Development AppServer.  It does this by removing a cookie used to
 * store the authentication data.
 *
 * <p>After the user has been logged out, they are redirected to the URL
 * specified in the {@code "continue"} request parameter.
 *
 */
public final class LocalLogoutServlet extends HttpServlet {
  private static final long serialVersionUID = -1222014300866646022L;

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String continueUrl = req.getParameter("continue");

    // Remove our fake authentication cookie.
    LoginCookieUtils.removeCookie(req, resp);

    // Now redirect them to their continue URL.
    resp.sendRedirect(continueUrl);
  }
}
