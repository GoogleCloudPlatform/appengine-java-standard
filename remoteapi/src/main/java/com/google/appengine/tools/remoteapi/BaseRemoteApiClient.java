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

import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Base implementation for Remote API clients.
 */
abstract class BaseRemoteApiClient implements RemoteApiClient {

  private final String hostname;
  private final int port;
  private final String remoteApiPath;
  private final String appId;

  BaseRemoteApiClient(RemoteApiOptions options, @Nullable String appId) {
    this.hostname = options.getHostname();
    this.port = options.getPort();
    this.remoteApiPath = options.getRemoteApiPath();
    this.appId = appId;
  }

  /**
   * @return the path to the remote api for this app (if logged in), {@code null} otherwise
   */
  @Override
  public String getRemoteApiPath() {
    return remoteApiPath;
  }

  /**
   * @return the app id for this app (if logged in), {@code null} otherwise
   */
  @Override
  public String getAppId() {
    return appId;
  }
  
  String getHostname() {
    return hostname;
  }
  
  int getPort() {
    return port;
  }

  /**
   * Returns a full URL given a path.
   */
  String makeUrl(String path) {
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException("path doesn't start with a slash: " + path);
    }
    String protocol = port == 443 ? "https" : "http";
    return protocol + "://" + hostname + ":" + port + path;
  }
  
  /**
   * Returns a mutable list of headers required on all remote api requests.
   * Each header is a 2-element array with the name in element 0 and the value
   * in element 1.
   */
  List<String[]> getHeadersBase() {
    List<String[]> headers = new ArrayList<String[]>();
    headers.add(new String[]{"Host", hostname});
    // Required for Remote API (guards against XSRF attacks).
    headers.add(new String[]{"X-appcfg-api-version", "1"});
    return headers;
  }
}
