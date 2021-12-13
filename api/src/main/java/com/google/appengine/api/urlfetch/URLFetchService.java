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

package com.google.appengine.api.urlfetch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.Future;

/**
 * The {@code URLFetchService} provides a way for user code to execute
 * HTTP requests to external URLs.
 *
 * <p>Chunked and hanging requests are not supported, and all content
 * will be returned in a single block.
 *
 */
public interface URLFetchService {
  /**
   * System property for defining a global default URLFetch deadline.
   */
  String DEFAULT_DEADLINE_PROPERTY = "appengine.api.urlfetch.defaultDeadline";

  /**
   * System property to turn on server certificate validation by default. The value of the property
   * should be the string {@code "true"} in order to enable validation.
   */
  String DEFAULT_TLS_VALIDATION_PROPERTY = "appengine.api.urlfetch.defaultTlsValidation";

  /**
   * Convenience method for retrieving a specific URL via a HTTP GET
   * request with no custom headers and default values for all
   * {@link FetchOptions} attributes.  For more complex requests, use
   * {@link #fetch(HTTPRequest)}.
   *
   * @param url The url to fetch.
   *
   * @return The result of the fetch.
   *
   * @throws MalformedURLException If the provided URL is malformed.
   * @throws RequestPayloadTooLargeException If the provided payload exceeds the limit.
   * @throws IOException If the remote service could not be contacted or the
   * URL could not be fetched.
   * @throws SocketTimeoutException If the request takes too long to respond.
   * @throws ResponseTooLargeException If the response is too large.
   * @throws javax.net.ssl.SSLHandshakeException If the server's SSL
   * certificate could not be validated and validation was requested.
   */
  HTTPResponse fetch(URL url) throws IOException;

  /**
   * Execute the specified request and return its response.
   *
   * @param request The http request.
   *
   * @return The result of the fetch.
   *
   * @throws IllegalArgumentException If {@code request.getMethod} is not
   * supported by the {@code URLFetchService}.
   * @throws MalformedURLException If the provided URL is malformed.
   * @throws RequestPayloadTooLargeException If the provided payload exceeds the limit.
   * @throws IOException If the remote service could not be contacted or the
   * URL could not be fetched.
   * @throws SocketTimeoutException If the request takes too long to respond.
   * @throws ResponseTooLargeException If response truncation has been disabled
   * via the {@link FetchOptions} object on {@code request} and the response is
   * too large.  Some responses are too large to even retrieve from the remote
   * server, and in these cases the exception is thrown even if response
   * truncation is enabled.
   * @throws javax.net.ssl.SSLHandshakeException If the server's SSL
   * certificate could not be validated and validation was requested.
   */
  HTTPResponse fetch(HTTPRequest request) throws IOException;

  /**
   * Convenience method for asynchronously retrieving a specific URL
   * via a HTTP GET request with no custom headers and default values
   * for all {@link FetchOptions} attributes.  For more complex
   * requests, use {@link #fetchAsync(HTTPRequest)}.
   *
   * @param url The url to fetch.
   *
   * @return A future containing the result of the fetch, or one of
   * the exceptions documented for {@link #fetch(URL)}.
   */
  Future<HTTPResponse> fetchAsync(URL url);

  /**
   * Asynchronously execute the specified request and return its response.
   *
   * @param request The http request.
   *
   * @return A future containing the result of the fetch, or one of
   * the exceptions documented for {@link #fetch(HTTPRequest)}.
   */
  Future<HTTPResponse> fetchAsync(HTTPRequest request);
}
