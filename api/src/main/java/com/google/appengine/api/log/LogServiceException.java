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

package com.google.appengine.api.log;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Log errors apart from InvalidRequestException. These errors will generally benefit from retrying
 * the operation.
 *
 */
public final class LogServiceException extends RuntimeException {
  private static final long serialVersionUID = 4330439878478751420L;

  /**
   * Constructs a new LogServiceException with an error message.
   *
   * @param errorDetail Log service error detail.
   */
  public LogServiceException(@Nullable String errorDetail) {
    super(errorDetail);
  }

  public LogServiceException(@Nullable String errorDetail, Throwable cause) {
    super(errorDetail, cause);
  }
}
