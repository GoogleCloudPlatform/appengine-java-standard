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

package com.google.apphosting.runtime.jetty9.senderrorapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SendErrorServletEE11 extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    int errorCode;
    if (req.getParameter(
        "errorCode") == null) {
      errorCode = 0;
    } else {
      try {
        errorCode = Integer.parseInt(req.getParameter("errorCode"));
      } catch (NumberFormatException e) {
        errorCode = -1;
      }
    }
    switch (errorCode) {
      case -1:
        throw new RuntimeException("try to handle me");
      case 0:
        req.getRequestDispatcher("/hello.html").forward(req, resp);
        break;
      default:
        resp.sendError(errorCode);
        break;
    }
  }
}