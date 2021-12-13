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
 * Methods that are common between {@link MemcacheService} and
 * {@link AsyncMemcacheService}.
 *
 */
public interface BaseMemcacheService {

  /**
   * Method returns non-null value if the MemcacheService overrides the
   * default namespace in API calls. Default namespace is the one returned
   * by {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return {@code null} if the MemcacheService uses default namespace in
   * API calls. Otherwise it returns {@code namespace} which is overrides
   * default namespace on the API calls.
   */
  String getNamespace();

  /**
   * Fetches the current error handler.
   *
   * See {@link #setErrorHandler(ErrorHandler)}.
   */
  ErrorHandler getErrorHandler();

  /**
   * Registers a new {@code ErrorHandler}.  The {@code handler} is called
   * for errors which are not the application's fault, like a network timeout.
   * The handler can choose to propagate the error or suppress it.
   * Errors which are caused by an incorrect use of the API will not be
   * directed to the {@code handler} but rather will be thrown directly.
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
   * @param handler the new {@code ErrorHandler} to use
   */
  void setErrorHandler(ErrorHandler handler);

}
