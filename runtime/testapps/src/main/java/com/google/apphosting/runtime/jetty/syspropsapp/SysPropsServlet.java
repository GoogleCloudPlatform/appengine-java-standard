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

package com.google.apphosting.runtime.jetty.syspropsapp;

import static java.util.stream.Collectors.toMap;

import com.google.appengine.api.utils.SystemProperty;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that prints all the system properties. */
public class SysPropsServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    Map<String, String> properties =
        System.getProperties().keySet().stream()
            .map(p -> (String) p)
            .collect(toMap(p -> p, System::getProperty));
    new TreeMap<>(properties).forEach((k, v) -> writer.printf("%s = %s\n", k, v));
    writer.printf("SystemProperty.environment.value() = %s\n", SystemProperty.environment.value());
  }
}
