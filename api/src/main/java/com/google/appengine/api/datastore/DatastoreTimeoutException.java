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

package com.google.appengine.api.datastore;

/**
 * {@code DatastoreTimeoutException} is thrown when a datastore operation times out. This can happen
 * when you attempt to put, get, or delete too many entities or an entity with too many properties,
 * or if the datastore is overloaded or having trouble.
 *
 */
public class DatastoreTimeoutException extends RuntimeException {
  private static final long serialVersionUID = -613835301335028222L;

  public DatastoreTimeoutException(String message) {
    super(message);
  }

  public DatastoreTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
