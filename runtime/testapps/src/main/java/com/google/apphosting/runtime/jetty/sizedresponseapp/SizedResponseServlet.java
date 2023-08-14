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

package com.google.apphosting.runtime.jetty.sizedresponseapp;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(urlPatterns = "/*")
public class SizedResponseServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String sizeParam = req.getParameter("size");
    long size = (sizeParam == null) ? 0 : Long.parseLong(sizeParam);

    resp.setContentType("text/plain");
    ServletOutputStream outputStream = resp.getOutputStream();
    for (int i = 0; i < size; i++)
    {
      outputStream.write((byte)'x');
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int length = 0;
    ServletInputStream inputStream = req.getInputStream();
    while (true)
    {
      int read = inputStream.read();
      if (read < 0)
        break;
      length++;
    }

    resp.getWriter().print("length=" + length);
  }
}
