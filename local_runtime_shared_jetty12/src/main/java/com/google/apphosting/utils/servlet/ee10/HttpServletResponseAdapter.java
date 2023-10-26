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

package com.google.apphosting.utils.servlet.ee10;

import com.google.apphosting.utils.http.HttpResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Simple adapter for a Serlvet Http Response.
 *
 */
public class HttpServletResponseAdapter implements HttpResponse {
  private final HttpServletResponse response;

  public HttpServletResponseAdapter(HttpServletResponse response) {
    this.response = response;
  }

  @Override
  public void setHeader(String name, String value) {
    response.setHeader(name, value);
  }

  @Override
  public boolean isCommitted() {
    return response.isCommitted();
  }

  @Override
  public void sendError(int error) throws IOException {
    response.sendError(error);
  }

  @Override
  public void sendError(int error, String message) throws IOException {
    response.sendError(error, message);
  }

  @Override
  public void setContentType(String contentType) {
    response.setContentType(contentType);
  }

  @Override
  public void write(String content) throws IOException {
    PrintWriter writer = response.getWriter();
    writer.write(content);
    writer.flush();
  }

  @Override
  public void setStatus(int status) {
    response.setStatus(status);
  }
}
