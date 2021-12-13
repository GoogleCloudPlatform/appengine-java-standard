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

/**
 * An interface describing a Remote API client. Implementations of this
 * interface must be thread-safe.
 */
interface RemoteApiClient {

  /**
   * @return The path to the Remote API handler.
   */
  String getRemoteApiPath();
  
  /**
   * @return The app id associated with this client, possibly null.
   */
  String getAppId();
  
  /**
   * @return The credentials assocated with this client as a String.
   * Non-cookie based implementations may not support this method.
   */
  String serializeCredentials();

  /**
   * Executes an HTTP POST request against {@code path}.
   */
  AppEngineClient.Response post(String path, String mimeType, byte[] body) throws IOException;
  
  /**
   * Executes an HTTP GET request against {@code path}.
   */
  AppEngineClient.Response get(String path) throws IOException;
}
