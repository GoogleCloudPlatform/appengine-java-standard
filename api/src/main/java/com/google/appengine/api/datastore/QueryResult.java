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

import java.util.List;

/**
 * An interface shared by all QueryResult classes.
 *
 */
interface QueryResult {

  /**
   * Get the indexes used to perform the query.
   *
   * @return A list of index references, with no duplicates, or {@code null} if the indexes are not
   *     known.
   */
  // @Nullable
  List<Index> getIndexList();

  /**
   * Gets a {@link Cursor} that points to the {@link Entity} immediately after the last {@link
   * Entity} retrieved.
   *
   * @return a {@link Cursor} or {@code null} if this query result cannot be resumed
   */
  // @Nullable
  Cursor getCursor();
}
