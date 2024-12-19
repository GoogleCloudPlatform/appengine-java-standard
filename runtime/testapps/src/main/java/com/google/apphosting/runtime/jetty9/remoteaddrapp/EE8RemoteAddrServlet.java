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

package com.google.apphosting.runtime.jetty9.remoteaddrapp;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EE8RemoteAddrServlet extends HttpServlet {
  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    writer.println("getRemoteAddr: " + req.getRemoteAddr());
    writer.println("getRemoteHost: " + req.getRemoteHost());
    writer.println("getRemotePort: " + req.getRemotePort());
    writer.println("getLocalAddr: " + req.getLocalAddr());
    writer.println("getLocalName: " + req.getLocalName());
    writer.println("getLocalPort: " + req.getLocalPort());
    writer.println("getServerName: " + req.getServerName());
    writer.println("getServerPort: " + req.getServerPort());
  }
}
