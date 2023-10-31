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
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * {@code LocalOAuthAuthorizeTokenServlet} is the servlet responsible for
 * implementing the token authorization step of the fake OAuth authentication
 * flow provided by the Development AppServer.
 * <p>
 * This serlvet will redirect to the URL specified in the 'oauth_callback'
 * parameter after access is granted. It does not currently support callback
 * URLs provided during the request token acquisition step.
 *
 */
public class LocalOAuthAuthorizeTokenServlet extends HttpServlet {
  private static final long serialVersionUID = 1789085416447898108L;
  private static final String BLUE_BOX_STYLE = "width: 20em;"
      + "margin: 1em auto;"
      + "text-align: left;"
      + "padding: 0 2em 1.25em 2em;"
      + "background-color: #d6e9f8;"
      + "font: 13px sans-serif;"
      + "border: 2px solid #67a7e3";

  // TODO: Validate that the token exists.
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String oauthCallback = req.getParameter("oauth_callback");
    if (oauthCallback == null) {
      oauthCallback = "";
    }

    // TODO: Move to a JSP?
    resp.setContentType("text/html");
    PrintWriter out = resp.getWriter();
    out.println("<html>");
    out.println("<body>");
    out.println("<form method='post'>");
    out.printf("<div style='%s'>\n", BLUE_BOX_STYLE);
    out.println("<h3>OAuth Access Request</h3>");
    out.printf("<input type='hidden' name='oauth_callback' value='%s'/>\n",
        HtmlEscapers.htmlEscaper().escape(oauthCallback));
    out.println("<p style='margin-left: 3em;'>");
    out.println("<input name='action' type='submit' value='Grant Access' id='btn-grant-access'/>");
    out.println("</p>");
    out.println("</div>");
    out.println("</form>");
    out.println("</body>");
    out.println("</html>");
  }

  // TODO: Mark the token as approved.
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String oauthCallback = req.getParameter("oauth_callback");
    if (oauthCallback != null && oauthCallback.length() > 0) {
      resp.sendRedirect(oauthCallback);
    } else {
      // TODO: Move to a JSP?
      resp.setContentType("text/html");
      PrintWriter out = resp.getWriter();
      out.println("<html>");
      out.println("<body>");
      out.printf("<div style='%s'>\n", BLUE_BOX_STYLE);
      out.println("<h3>OAuth Access Granted</h3>");
      out.println("</div>");
      out.println("</body>");
      out.println("</html>");
    }
  }
}
