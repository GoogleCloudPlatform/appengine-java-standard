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

package com.google.appengine.api.memcache.jsr107cache;

/**
 * An exception for events where an operation could not be completed by the memcache service as
 * requested by the GCache interface.
 *
 */
public class GCacheException extends RuntimeException {
  private static final long serialVersionUID = 7850626980766599482L;

  public GCacheException(String message, Throwable ex) {
    super(message, ex);
  }

  public GCacheException(String message) {
    super(message);
  }
}
