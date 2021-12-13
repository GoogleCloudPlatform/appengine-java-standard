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

package com.google.appengine.api.search.checkers;

import com.google.common.base.Preconditions;

/**
 * Checks values of {@link com.google.appengine.api.search.Cursor}.
 *
 */
public final class CursorChecker {

  /**
   * Checks the cursor string if provided is not empty nor too long.
   *
   * @param cursor the search cursor to check
   * @return the checked cursor
   * @throws IllegalArgumentException if the cursor is empty or is too long
   */
  public static String checkCursor(String cursor) {
    if (cursor != null) {
      Preconditions.checkArgument(!cursor.isEmpty(),
          "cursor cannot be empty");
      Preconditions.checkArgument(
          cursor.length() <= SearchApiLimits.MAXIMUM_CURSOR_LENGTH,
          "cursor cannot be longer than %s characters",
          SearchApiLimits.MAXIMUM_CURSOR_LENGTH);
    }
    return cursor;
  }
}
