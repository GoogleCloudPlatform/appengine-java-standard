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

/**
 * A strict error handler, which will throw {@link MemcacheServiceException}
 * or {@link InvalidValueException} for any service error condition.
 *
 */
public class StrictErrorHandler implements ConsistentErrorHandler {

  /**
   * Throws {@link InvalidValueException} for any call.
   *
   * @param t the classpath error exception
   * @throws MemcacheServiceException for any service error.
   */
  @Override
  public void handleDeserializationError(InvalidValueException t) {
    throw t;
  }

  /**
   * Throws {@link MemcacheServiceException} for any call.
   *
   * @param t the service error exception
   * @throws MemcacheServiceException for any service error.
   */
  @Override
  public void handleServiceError(MemcacheServiceException t) {
    throw t;
  }
}

