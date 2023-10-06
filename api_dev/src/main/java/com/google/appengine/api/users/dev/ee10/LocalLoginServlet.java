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

import com.google.common.html.HtmlEscapers;
import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code LocalLoginServlet} is the servlet responsible for implementing
 * the fake authentication provided by the Development AppServer.
 *
 * <p>This servlet responds to both {@code GET} and {@code POST}
 * requests.  {@code GET} requests result in a simple HTML form that
 * asks for an email address and whether or not the user is an
 * administrator.  {@code POST} requests expect to receive the output
 * from this form, and set a cookie that contains the same data.
 *
 * <p>After the user has been logged in, they are redirected to the URL
 * specified in the {@code "continue"} request parameter.
 *
 */
public final class LocalLoginServlet extends HttpServlet {
  private static final long serialVersionUID = 3436539147212984827L;

  private static final String BLUE_BOX_STYLE = "width: 20em;"
      + "margin: 1em auto;"
      + "text-align: left;"
      + "padding: 0 2em 1.25em 2em;"
      + "background-color: #d6e9f8;"
      + "border: 2px solid #67a7e3;";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String continueUrl = req.getParameter("continue");
    if (continueUrl == null) {
      continueUrl = "";
    }
    String email = "test@example.com";
    String isAdminChecked = "";
    LoginCookieUtils.CookieData cookieData = LoginCookieUtils.getCookieData(req);
    if (cookieData != null) {
      email = cookieData.getEmail();
      if (cookieData.isAdmin()) {
        isAdminChecked = " checked='true'";
      }
    }
    resp.setContentType("text/html");

    // TODO: We may want to move this to a JSP.
    PrintWriter out = resp.getWriter();
    out.println("<html>");
    out.println("<body>");
    out.println("<form method='post' style='text-align:center; font:13px sans-serif'>");
    out.printf("<div style='%s'>\n", BLUE_BOX_STYLE);
    out.println("<h3>Not logged in</h3>");
    out.println("<p style='padding: 0; margin: 0'>");
    out.println("<label for='email' style='width: 3em'>Email:</label>");
    out.printf(" <input type='text' name='email' id='email'value='%s'>\n", email);
    out.println("</p>");
    out.println("<p style='margin: .5em 0 0 3em; font-size:12px'>");
    out.printf("<input type='checkbox' name='isAdmin' id='isAdmin'%s>\n", isAdminChecked);
    out.println(" <label for='isAdmin'>Sign in as Administrator</label>");
    out.println("</p>");
    out.printf("<input type='hidden' name='continue' value='%s'>\n",
        HtmlEscapers.htmlEscaper().escape(continueUrl));
    out.println("<p style='margin-left: 3em;'>");
    out.println("<input name='action' type='submit' value='Log In' id='btn-login'>");
    out.println("<input name='action' type='submit' value='Log Out' id='btn-logout'>");
    out.println("</p>");
    out.println("</div>");
    out.println("</form>");
    out.println("</body>");
    out.println("</html>");
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String continueUrl = req.getParameter("continue");
    String email = req.getParameter("email");
    boolean logout = "Log Out".equalsIgnoreCase(req.getParameter("action"));
    boolean isAdmin = "on".equalsIgnoreCase(req.getParameter("isAdmin"));

    if (logout) {
      LoginCookieUtils.removeCookie(req, resp);
    } else {
      // Add our fake authentication cookie.
      resp.addCookie(LoginCookieUtils.createCookie(email, isAdmin));
    }

    // Redirect the user to their original continue URL.
    resp.sendRedirect(continueUrl);
  }
}
