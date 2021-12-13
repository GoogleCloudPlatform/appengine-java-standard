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

import java.nio.charset.Charset;

/**
 * A temporary class used by the internal Remote API to represent HTTP
 * responses. See {@link "http://b/17462897"} for details.
 */
class LegacyResponse {
  private final int statusCode;
  private final byte[] responseBody;
  private final Charset responseCharset;

  LegacyResponse(int statusCode, byte[] responseBody, Charset responseCharset) {
    this.statusCode = statusCode;
    this.responseBody = responseBody;
    this.responseCharset = responseCharset;
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
