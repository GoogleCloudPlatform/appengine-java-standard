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

package com.google.apphosting.utils.http;

import java.io.IOException;

/**
 * Generic Http Response (subset of the servlet specification.)
 */
public interface HttpResponse {
  /**
   * Sets a response header with the given name and value. If the header had already been set, the
   * new value overwrites the previous one.  The <code>containsHeader</code> method can be used to
   * test for the presence of a header before setting its value.
   *
   * @param  name  the name of the header
   * @param  value  the header value  If it contains octet string, it should be encoded according to
   * RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt)
   */
  void setHeader(String name, String value);

  /**
   * Returns a boolean indicating if the response has been committed.  A committed response has
   * already had its status code and headers written.
   *
   * @return a boolean indicating if the response has been committed
   */
  boolean isCommitted();

  /**
   * Sends an error response to the client using the specified status code and clearing the buffer.
   * <p>If the response has already been committed, this method throws an IllegalStateException.
   * After using this method, the response should be considered to be committed and should not be
   * written to.
   *
   * @param  error  the error status code
   * @exception IOException  If an input or output exception occurs
   * @exception IllegalStateException  If the response was committed before this method call
   */
  void sendError(int error) throws IOException;

  /**
   * Sends an error response to the client using the specified status.  The server defaults to
   * creating the response to look like an HTML-formatted server error page containing the specified
   * message, setting the content type to "text/html", leaving cookies and other headers
   * unmodified.
   *
   * If an error-page declaration has been made for the web application corresponding to the status
   * code passed in, it will be served back in preference to the suggested msg parameter.
   *
   * <p>If the response has already been committed, this method throws an IllegalStateException.
   * After using this method, the response should be considered to be committed and should not be
   * written to.
   *
   * @param  error  the error status code
   * @param  message  the descriptive message
   * @exception IOException  If an input or output exception occurs
   * @exception IllegalStateException  If the response was committed
   */
  void sendError(int error, String message) throws IOException;

  /**
   * Sets the content type of the response being sent to the client, if the response has not been
   * committed yet. The given content type may include a character encoding specification, for
   * example, <code>text/html;charset=UTF-8</code>. The response's character encoding is only set
   * from the given content type if this method is called before <code>getWriter</code> is called.
   * <p>This method may be called repeatedly to change content type and character encoding. This
   * method has no effect if called after the response has been committed. It does not set the
   * response's character encoding if it is called after <code>getWriter</code> has been called or
   * after the response has been committed. <p>Containers must communicate the content type and the
   * character encoding used for the servlet response's writer to the client if the protocol
   * provides a way for doing so. In the case of HTTP, the <code>Content-Type</code> header is
   * used.
   *
   * @param contentType a <code>String</code> specifying the MIME type of the content
   */
  void setContentType(String contentType);

  /**
   * Write the content to the response writer and flush it.
   *
   * @param content to write.
   */
  void write(String content) throws IOException;

  /**
   * Sets the status code for this response.  This method is used to set the return status code when
   * there is no error (for example, for the status codes SC_OK or SC_MOVED_TEMPORARILY).  If there
   * is an error, and the caller wishes to invoke an error-page defined in the web application, the
   * <code>sendError</code> method should be used instead. <p> The container clears the buffer and
   * sets the Location header, preserving cookies and other headers.
   *
   * @param  status  the status code
   * @see #sendError
   */
  void setStatus(int status);
}
