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

package com.google.apphosting.runtime.jetty9.echoapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that prints all the system properties. */
public class EchoServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    PrintWriter out = resp.getWriter();

    String url = req.getContextPath();
    if (req.getServletPath() != null) {
      url += req.getServletPath();
    }
    if (req.getPathInfo() != null) {
      url += req.getPathInfo();
    }
    if (req.getQueryString() != null) {
      url += '?' + req.getQueryString();
    }
    out.println("==========");
    out.printf("%s %s %s%n", req.getMethod(), url, req.getProtocol());

    Enumeration<String> names = req.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      Enumeration<String> values = req.getHeaders(name);
      while (values.hasMoreElements()) {
        String value = values.nextElement();
        out.printf("%s: %s%n", name, value);
      }
    }
    out.println("----------");

    BufferedReader in = req.getReader();
    String line = in.readLine();
    while (line != null) {
      out.println(line);
      line = in.readLine();
    }
    out.println("==========");
  }
}
