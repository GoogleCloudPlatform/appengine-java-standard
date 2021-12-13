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

package com.google.appengine.tools.development;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A Filter that verifies that the incoming request's headers are valid.
 *
 */
public class HeaderVerificationFilter implements Filter {
  private static final String CONTENT_LENGTH = "Content-Length";

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (doFilterInternal(request, response)) {
      chain.doFilter(request, response);
    }
  }

  /**
   * Helper method for doFilter() that contains the filtering logic but does
   * not invoke the remaining filters in the chain.
   *
   * @return true if the request should be passed to the remaining filters in the chain.
   */
  private boolean doFilterInternal(ServletRequest request, ServletResponse response)
      throws IOException {
    // We only know how to verify HTTP requests.  So if the request and response objects aren't
    // HTTP, simply run the remaining filters.
    if (!(request instanceof HttpServletRequest)) {
      return true;
    }
    if (!(response instanceof HttpServletResponse)) {
      return true;
    }
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    // It's an error if a POST request lacks the CONTENT_LENGTH header.
    if (httpRequest.getMethod().equals("POST") &&
        httpRequest.getHeader(CONTENT_LENGTH) == null) {
      httpResponse.sendError(HttpServletResponse.SC_LENGTH_REQUIRED, "Length required");
      return false;
    }

    return true;
  }
}
