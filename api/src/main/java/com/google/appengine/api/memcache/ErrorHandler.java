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
 * Handles errors raised by the {@code MemcacheService}, registered with
 * {@link MemcacheService#setErrorHandler(ErrorHandler)}.
 *
 * <p>The default error handler is an instance of
 * {@link LogAndContinueErrorHandler}. In most cases, this will log
 * exceptions without throwing, which looks like a cache-miss behavior to
 * the caller. A less permissive alternative is {@link StrictErrorHandler},
 * which will throw a {@link MemcacheServiceException} to surface errors
 * to callers.
 *
 * <p>To guarantee that all instances of {@link MemcacheServiceException}
 * are directed to the error handler, use a {@link ConsistentErrorHandler}
 * such as {@link ErrorHandlers#getConsistentLogAndContinue(Level)} or
 * {@link ErrorHandlers#getStrict()}.
 *
 */
public interface ErrorHandler {

  /**
   * Handles deserialization errors.  This method is called from either of the
   * {@link MemcacheService#get(Object) get} methods, if the retrieved value
   * cannot be deserialized.  This normally indicates an application upgrade
   * since the cache entry was stored, and should thus be treated as a cache
   * miss, which is the behavior of {@link LogAndContinueErrorHandler} (the
   * default).
   */
  void handleDeserializationError(InvalidValueException ivx);

  /**
   * Handles back-end service errors. This method is called from most of the
   * {@code MemcacheService} methods in the event of a service error. This is
   * also called for {@link MemcacheService#put(Object, Object)} when the
   * combined key and value size is too large.
   *
   * <p>The handler may throw any {@link RuntimeException}, or it may simply
   * return for "permissive" error handling, which will generally emulate
   * behavior of a cache miss due to a discarded entry.
   *
   *  @param ex the service error exception
   */
  void handleServiceError(MemcacheServiceException ex);

}
