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

package com.google.appengine.api.memcache;

import java.util.logging.Level;

/**
 * Static utility for getting built-in {@link ErrorHandler}s.
 *
 */
public final class ErrorHandlers {

  private static final StrictErrorHandler STRICT = new StrictErrorHandler();
  private static final ErrorHandler DEFAULT = new LogAndContinueErrorHandler(Level.INFO);

  private ErrorHandlers() {
    // Utility class
  }

  /**
   * Returns an instance of {@link StrictErrorHandler}.
   */
  public static StrictErrorHandler getStrict() {
    return STRICT;
  }

  /**
   * Returns an instance of {@link LogAndContinueErrorHandler}.
   * To make sure that all {@link MemcacheServiceException} exceptions
   * are handled by the {@code ErrorHandler} use
   * {@link #getConsistentLogAndContinue(Level)} instead.
   *
   * @deprecated Use {@link #getConsistentLogAndContinue(Level)} instead
   */
  @Deprecated
  public static LogAndContinueErrorHandler getLogAndContinue(Level logLevel) {
    return new LogAndContinueErrorHandler(logLevel);
  }

  /**
   * Returns an instance of {@link ConsistentLogAndContinueErrorHandler} that handles all
   * {@link MemcacheServiceException} exceptions.
   */
  public static LogAndContinueErrorHandler getConsistentLogAndContinue(Level logLevel) {

    return new ConsistentLogAndContinueErrorHandler(logLevel);
  }

  /**
   * Returns the default error handler.
   */
  public static ErrorHandler getDefault() {
    return DEFAULT;
  }
}
