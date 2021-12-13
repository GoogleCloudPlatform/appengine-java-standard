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
import java.util.logging.Logger;

/**
 * The default error handler, which will cause most service errors to behave
 * as though there were a cache miss, not an error.
 *
 * <p>To guarantee that all {@link MemcacheServiceException} are directed to
 * the error handler use a {@link ConsistentErrorHandler} instead such as
 * {@link ErrorHandlers#getConsistentLogAndContinue(Level)}.
 *
 */
public class LogAndContinueErrorHandler implements ErrorHandler {

  private static final Logger logger =
      Logger.getLogger(LogAndContinueErrorHandler.class.getName());

  private final Level level;

  /**
   * Constructor for a given logging level.
   *
   * @param level the level at which back-end errors should be logged.
   */
  public LogAndContinueErrorHandler(Level level) {
    this.level = level;
  }

  /**
   * Logs the {@code thrown} error condition, but does not expose it to
   * application code.
   *
   * @param thrown the classpath error exception
   */
  @Override
  public void handleDeserializationError(InvalidValueException thrown) {
    getLogger().log(level, "Deserialization error in memcache", thrown);
  }

  /**
   * Logs the {@code thrown} error condition, but does not expose it to
   * application code.
   *
   * @param thrown the service error exception
   */
  @Override
  public void handleServiceError(MemcacheServiceException thrown) {
    getLogger().log(level, "Service error in memcache", thrown);
  }

  Logger getLogger() {
    return logger;
  }
}

