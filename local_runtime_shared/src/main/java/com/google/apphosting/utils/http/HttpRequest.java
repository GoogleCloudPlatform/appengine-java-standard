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

/**
 * Generic Http request (subset of the Servlet specification.)
 */
public interface HttpRequest {
  /**
   * Returns any extra path information associated with the URL the client sent when it made this
   * request. The extra path information follows the servlet path but precedes the query string and
   * will start with a "/" character.
   *
   * <p>This method returns <code>null</code> if there was no extra path information.
   *
   * <p>Same as the value of the CGI variable PATH_INFO.
   *
   * @return a <code>String</code>, decoded by the web container, specifying extra path information
   * that comes after the servlet path but before the query string in the request URL; or
   * <code>null</code> if the URL does not have any extra path information
   */
  String getPathInfo();

  /**
   * Returns the value of the specified request header as an <code>int</code>. If the request does
   * not have a header of the specified name, this method returns -1. If the header cannot be
   * converted to an integer, this method throws a <code>NumberFormatException</code>.
   *
   * <p>The header name is case insensitive.
   *
   * @param name a <code>String</code> specifying the name of a request header
   * @return an integer expressing the value of the request header or -1 if the request doesn't
   * have a header of this name
   * @exception NumberFormatException    If the header value can't be converted to an
   * <code>int</code>
   */
  String getHeader(String name);

  /**
   * Returns the value of a request parameter as a <code>String</code>, or <code>null</code> if the
   * parameter does not exist. Request parameters are extra information sent with the request.  For
   * HTTP servlets, parameters are contained in the query string or posted form data.
   *
   * <p>You should only use this method when you are sure the parameter has only one value. If the
   * parameter might have more than one value, use {@link #getParameterValues}.
   *
   * <p>If you use this method with a multivalued parameter, the value returned is equal to the
   * first value in the array returned by <code>getParameterValues</code>.
   *
   * <p>If the parameter data was sent in the request body, such as occurs with an HTTP POST
   * request, then reading the body directly via {@link #getInputStream} or {@link #getReader} can
   * interfere with the execution of this method.
   *
   * @param name a <code>String</code> specifying the name of the parameter
   * @return a <code>String</code> representing the single value of the parameter
   */
  String getParameter(String name);

  /**
   * Stores an attribute in this request. Attributes are reset between requests.  This method is
   * most often used in conjunction with {@link javax.servlet.RequestDispatcher}.
   *
   * <p>Attribute names should follow the same conventions as package names. Names beginning with
   * <code>java.*</code>, <code>javax.*</code>, and <code>com.sun.*</code>, are reserved for use by
   * Sun Microsystems. <br> If the object passed in is null, the effect is the same as calling
   * {@link #removeAttribute}. <br> It is warned that when the request is dispatched from the
   * servlet resides in a different web application by <code>RequestDispatcher</code>, the object
   * set by this method may not be correctly retrieved in the caller servlet.
   *
   * @param name a <code>String</code> specifying the name of the attribute
   * @param value    the <code>Object</code> to be stored
   */
  void setAttribute(String name, Object value);
}
