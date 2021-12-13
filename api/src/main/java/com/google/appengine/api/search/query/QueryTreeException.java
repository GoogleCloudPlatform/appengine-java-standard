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

package com.google.appengine.api.search.query;

/**
 * A parsing exception thrown when the tree resulting from
 * parsing a query is in some sense invalid.
 *
 */
public class QueryTreeException extends RuntimeException {
  private static final long serialVersionUID = 4043406295942912722L;

  private final int position;

  public QueryTreeException(String message, int position) {
    super(message);
    this.position = position;
  }

  /**
   * @return the position at which the error was detected
   */
  public int getPosition() {
    return position;
  }
}
