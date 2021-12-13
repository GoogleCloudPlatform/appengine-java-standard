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

package com.google.appengine.tools.admin;

import java.io.IOException;

/**
 * An exception that occurs while interacting with local files.
 *
 * @see RemoteIOException
 */
class LocalIOException extends IOException {
  LocalIOException(String message) {
    super(message);
  }

  LocalIOException(String message, Throwable cause) {
    super(message, cause);
  }

  static LocalIOException from(IOException ioe) {
    LocalIOException local = new LocalIOException(ioe.getMessage(), ioe.getCause());
    local.setStackTrace(ioe.getStackTrace());
    return local;
  }
}
