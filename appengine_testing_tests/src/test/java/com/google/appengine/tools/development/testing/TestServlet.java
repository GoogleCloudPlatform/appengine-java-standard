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

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A simple servlet that we use to verify that {@link EndToEndTest} is running
 * in the same classloader as the servlet.
 *
*/
public class TestServlet extends HttpServlet {
  public static int count = 0;

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setContentType("text/plain");
    res.setHeader("X-Test-Large-Header", new String(new char[5000]));
    res.getWriter().write("junk content for b/28793327");
    res.resetBuffer();
    res.getWriter().println("Hello, World: " + ++count);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setHeader(
        "X-Test-Header-Over-Size-Limit", new String(new char[100000]));
  }
}
