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

package com.google.appengine.tools.development.jetty.ee10;

import com.google.appengine.tools.development.ee10.ResponseRewriterFilter;
import com.google.common.base.Preconditions;
import java.io.OutputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A filter that rewrites the response headers and body from the user's application.
 *
 * <p>This sanitises the headers to ensure that they are sensible and the user is not setting
 * sensitive headers, such as Content-Length, incorrectly. It also deletes the body if the response
 * status code indicates a non-body status.
 *
 * <p>This also strips out some request headers before passing the request to the application.
 */
public class JettyResponseRewriterFilter extends ResponseRewriterFilter {

  public JettyResponseRewriterFilter() {
    super();
  }

  /**
   * Creates a JettyResponseRewriterFilter for testing purposes, which mocks the current time.
   *
   * @param mockTimestamp Indicates that the current time will be emulated with this timestamp.
   */
  public JettyResponseRewriterFilter(long mockTimestamp) {
    super(mockTimestamp);
  }

  @Override
  protected ResponseWrapper getResponseWrapper(HttpServletResponse response) {
    return new ResponseWrapper(response);
  }

  private static class ResponseWrapper extends ResponseRewriterFilter.ResponseWrapper {

    public ResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
      // The user can write directly into our private buffer.
      // The response will not be committed until all rewriting is complete.
      if (bodyServletStream != null) {
        return bodyServletStream;
      } else {
        Preconditions.checkState(bodyPrintWriter == null, "getWriter has already been called");
        bodyServletStream = new ServletOutputStreamWrapper(body);
        return bodyServletStream;
      }
    }

    /** A ServletOutputStream that wraps some other OutputStream. */
    private static class ServletOutputStreamWrapper
        extends ResponseRewriterFilter.ResponseWrapper.ServletOutputStreamWrapper {

      ServletOutputStreamWrapper(OutputStream stream) {
        super(stream);
      }

      // New method and new new class WriteListener only in Servlet 3.1.
      @Override
      public void setWriteListener(WriteListener writeListener) {
        // Not used for us.
      }
    }
  }
}
