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

package com.google.apphosting.runtime;

/**
 * Describes an object that knows how to lookup, save, and delete
 * session data.
 *
 */
public interface SessionStore {
  SessionData getSession(String key);

  void saveSession(String key, SessionData data) throws Retryable;

  void deleteSession(String key);

  /**
   * Indicates that the operation can be retried.
   */
  class Retryable extends Exception {
    public Retryable(RuntimeException cause) {
      super(cause);
    }

    @Override
    public RuntimeException getCause() {
      return (RuntimeException) super.getCause();
    }
  }
}
