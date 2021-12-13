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

package com.google.appengine.api.log;

/**
 * {@code LogService} allows callers to request the logs for an application
 * using supplied filters. Logs are returned in an {@code Iterable} that yields
 * {@link RequestLogs}, which contain request-level information and optionally
 * {@link AppLogLine} objects containing the application logs from the request.
 *
 */
public interface LogService {
  /**
   * The number of items that each underlying RPC call will retrieve by default.
   */
  int DEFAULT_ITEMS_PER_FETCH = 20;
  
  enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
  }
  
  /**
   * Retrieve logs for the current application with the constraints provided
   * by the user as parameters to this function. Acts synchronously.
   *
   * @param query A LogQuery object that contains the various query parameters
   * that should be used in the LogReadRequest. If versions and majorVersionIds
   * are both empty, fetch will retrieve logs for the current running version.
   * @return An Iterable that contains a set of logs matching the
   * requested filters.
   */
  Iterable<RequestLogs> fetch(LogQuery query);  
}
