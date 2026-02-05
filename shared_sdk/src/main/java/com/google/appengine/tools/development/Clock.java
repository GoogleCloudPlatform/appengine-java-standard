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

package com.google.appengine.tools.development;

/**
 * Clock abstraction used by all local service implementations.
 * Allows tests to override 'now'.
 *
 */
public interface Clock {
  /**
   * @return current time in milliseconds-since-epoch.
   */
  long getCurrentTime();

  /**
   * The Clock instance used by local services if no override is
   * provided via {@link ApiProxyLocal#setClock(Clock)}
   */
  Clock DEFAULT = System::currentTimeMillis;
}
