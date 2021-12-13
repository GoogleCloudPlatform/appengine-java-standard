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

package com.google.appengine.tools.remoteapi;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.http.cookie.Cookie;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Abstract class that handles making HTTP requests to App Engine using
 * cookie-based authentication. The actual mechanism by which the HTTP requests
 * are made is left to subclasses to implement.
 *
 * <p>This class is thread-safe.</p>
 *
 */
abstract class AppEngineClient extends BaseRemoteApiClient {
  private final String userEmail;
  private final Cookie[] authCookies;
  private final int maxResponseSize;

  AppEngineClient(RemoteApiOptions options, List<Cookie> authCookies, @Nullable String appId) {
    super(options, appId);
    if (options == null) {
      throw new IllegalArgumentException("options not set");
    }
    if (authCookies == null) {
      throw new IllegalArgumentException("authCookies not set");
    }
    this.userEmail = options.getUserEmail();
    this.authCookies = authCookies.toArray(new Cookie[0]);
    this.maxResponseSize = options.getMaxHttpResponseSize();
  }
  
  @Override
  public String serializeCredentials() {
    StringBuilder out = new StringBuilder();
    out.append("host=" + getHostname() + "\n");
    out.append("email=" + userEmail + "\n");
    for (Cookie cookie : authCookies) {
      out.append("cookie=" + cookie.getName() + "=" + cookie.getValue() + "\n");
    }
    return out.toString();
  }

  /**
   * @return the {@link Cookie} objects that should be used for authentication
   */
  Cookie[] getAuthCookies() {
    return authCookies;
  }

  int getMaxResponseSize() {
    return maxResponseSize;
  }

  List<String[]> getHeadersForPost(String mimeType) {
    List<String[]> headers = getHeadersBase();
    headers.add(new String[]{"Content-type", mimeType});
    return headers;
  }

  List<String[]> getHeadersForGet() {
    return getHeadersBase();
  }
  
  /**
   * Simple class representing an HTTP response.
   */
  // NOTE: This should really live in RemoteApiClient, but moving
  // it breaks clients due to the problems described in http://b/17462897.
  static class Response {
    private final int statusCode;
    private final byte[] responseBody;
    private final Charset responseCharset;
  
    Response(int statusCode, byte[] responseBody, Charset responseCharset) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
      this.responseCharset =
          (responseCharset == null) ? StandardCharsets.UTF_8 : responseCharset;
    }
  
    int getStatusCode() {
      return statusCode;
    }
  
    byte[] getBodyAsBytes() {
      return responseBody;
    }

    String getBodyAsString() {
      return new String(responseBody, responseCharset);
    }
  }

  // TODO: After these two methods reach third_party, change
  // the internal Remote API classes to use them. Once that's done, it'll
  // become possible to move Response from AppEngineClient into RemoteApiClient
  // (a prerequisite for adding appropriate hooks such that the internal
  // Remote API classes won't need to depend on non-public hooks).
  abstract LegacyResponse legacyPost(String path, String mimeType, byte[] body) throws IOException;
  abstract LegacyResponse legacyGet(String path) throws IOException;
}
