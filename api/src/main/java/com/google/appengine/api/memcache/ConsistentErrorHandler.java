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
 * A marker interface to indicate that all {@link MemcacheServiceException}
 * exceptions should be handled by
 * {@link ErrorHandler#handleServiceError(MemcacheServiceException)}.
 * This interface was added to enable handling {@link MemcacheServiceException}
 * thrown by the various {@link MemcacheService#put(Object, Object)} methods while
 * preserving backward compatibility with {@link ErrorHandler} which did not handle
 * such cases.
 *
 */
// TODO: This is a "hack" to make ErrorHandler behave as expected
// without breaking existing code. Future versions of this API should not
// have this interface but rather treat ErrorHandler as if it was
// ConsistentErrorHandler.
public interface ConsistentErrorHandler extends ErrorHandler {

  @Override
  void handleDeserializationError(InvalidValueException ivx);

  @Override
  void handleServiceError(MemcacheServiceException ex);
}

