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

package servletthree;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/** */
@WebServlet(
    name = "servlet3test",
    urlPatterns = {"/test/*"},
    initParams = {
      @WebInitParam(name = "prefix", value = "<<<"),
      @WebInitParam(name = "suffix", value = ">>>")
    })
public class JakartaServlet3Test extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/plain");
    resp.setStatus(200);
    try (PrintWriter writer =
        new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(resp.getOutputStream(), UTF_8)))) {
      String prefix = getInitParameter("prefix");
      String suffix = getInitParameter("suffix");
      writer.println(prefix + req.getRequestURI() + suffix);
      // Check we are not running with a security manager:
      SecurityManager security = System.getSecurityManager();
      if (security != null) {
        throw new RuntimeException("Security manager detected.");
      }
    }
  }
}
