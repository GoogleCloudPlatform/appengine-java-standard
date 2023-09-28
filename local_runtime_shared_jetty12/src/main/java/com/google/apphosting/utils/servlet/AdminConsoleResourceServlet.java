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

package com.google.apphosting.utils.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that serves resources required by the admin console ui.
 * This is needed because the resources live in the SDK jar, not
 * the user's WAR, and as a result can't be referenced directly.
 *
 */
@SuppressWarnings("serial")
public class AdminConsoleResourceServlet extends HttpServlet {

  // Hard-coding the resources we serve so that user code
  // can't serve arbitrary resources from our jars.
  private enum Resources {
    google("ah/images/google.gif"),
    webhook("js/webhook.js"),
    multipart_form_data("js/multipart_form_data.js"),
    rfc822_date("js/rfc822_date.js");

    private final String filename;

    Resources(String filename) {
      this.filename = filename;
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String resource = req.getParameter("resource");
    InputStream in = getClass().getResourceAsStream(Resources.valueOf(resource).filename);
    try {
      OutputStream out = resp.getOutputStream();
      int next;
      while ((next = in.read()) != -1) {
        out.write(next);
      }
      out.flush();
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
}
