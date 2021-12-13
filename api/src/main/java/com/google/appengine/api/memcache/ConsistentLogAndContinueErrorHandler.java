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
 * Similar to the deprecated {@link LogAndContinueErrorHandler} but consistently
 * handles all back-end related errors.
 *
 */
public class ConsistentLogAndContinueErrorHandler extends LogAndContinueErrorHandler
    implements ConsistentErrorHandler {

  private static final Logger logger =
      Logger.getLogger(ConsistentLogAndContinueErrorHandler.class.getName());

  public ConsistentLogAndContinueErrorHandler(Level level) {
    super(level);
  }

  @Override
  Logger getLogger() {
    return logger;
  }
}
