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
 * An exception for backend non-availability or similar error states which
 * may occur, but are not necessarily indicative of a coding or usage error
 * by the application. Also used for {@link MemcacheService#put(Object,
 * Object)} when the combined key and value size is too large.
 *
 * <p>In most cases these will be given to the {@link ErrorHandler} to resolve.
 * Use {@link ConsistentErrorHandler} if in all cases this exception should be
 * directed to the {@code ErrorHandler}.
 *
 */
public class MemcacheServiceException extends RuntimeException {
  private static final long serialVersionUID = -7792738618083636868L;

  public MemcacheServiceException(String message, Throwable ex) {
    super(message, ex);
  }
  public MemcacheServiceException(String message) {
    super(message);
  }
}
