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

package com.google.appengine.tools.util;

import java.io.IOException;

/**
 * Exception type for HTTP header parsing errors.
 *
 */
public class HttpHeaderParseException extends IOException {
  private static final long serialVersionUID = 1L;

  /**
   * Create an exception with no error message.
   */
  public HttpHeaderParseException() {
    super();
  }

  /**
   * Create an exception with a given error message.
   * @param message error message.
   */
  public HttpHeaderParseException(String message) {
    super(message);
  }
}
