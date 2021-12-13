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

/**
 * {@code InternalTransientException} is thrown when
 * a temporary error occurs in retrieving the URL.
 *
 */
public final class InternalTransientException extends IOException {
  private static final long serialVersionUID = 3369913278585706290L;

  private static final String MESSAGE_FORMAT =
      "A temporary internal error has occurred. Please try again. URL: %s";

  public InternalTransientException(String url) {
    super(String.format(MESSAGE_FORMAT, url));
  }
}
