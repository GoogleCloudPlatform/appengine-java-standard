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
 * Thrown when a cache entry has content, but it cannot be read.  For example:
 * <ul>
 * <li>An attempt to {@link MemcacheService#increment} a non-integral value
 * <li>Version skew between your application and the data in the cache,
 *     causing a serialization error.
 * </ul>
 *
 */
public class InvalidValueException extends RuntimeException {
  private static final long serialVersionUID = -7248182705618951808L;

  public InvalidValueException(String message, Throwable t) {
    super(message, t);
  }

  public InvalidValueException(String message) {
    super(message);
  }
}

