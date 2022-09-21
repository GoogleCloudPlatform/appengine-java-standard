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

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that detects if the GAE APIs are in the app classpath. */
@WebServlet(
    name = "AnnotationScanningServlet",
    urlPatterns = {"/"})
public class AnnotationScanningServlet extends HttpServlet {

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    PrintWriter out = resp.getWriter();
    // Testing that appengine-api-1.0-sdk.jar is not on the application classpath
    // if the app does not define it.
    try {
      Class.forName("com.google.appengine.api.utils.SystemProperty");
      throw new IllegalArgumentException("com.google.appengine.api.utils.SystemProperty");

    } catch (ClassNotFoundException expected) {
      out.println("ok, com.google.appengine.api.utils.SystemProperty not seen.");
    }
  }
}
