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
 * Provides an abstraction for retrieving {@code Entity} objects in arbitrarily-sized batches.
 *
 */
interface QueryResultsSource {
  /**
   * Returns true when there maybe more {@code Entity} objects that can be returned from this {@code
   * QueryResultsSource}.
   */
  boolean hasMoreEntities();

  /**
   * Load at least one {@code Entity} object if there are more entities.
   *
   * @param buffer An out parameter to which the requested entities will be added.
   * @param cursorBuffer An out parameter to which the requested entity cursors will be added.
   * @return the cursor that points to the {@link Entity} after the last {@link Entity} returned or
   *     {@code null} (see {@link #loadMoreEntities(int, List, List)} for a description of when this
   *     will be {@code null})
   */
  Cursor loadMoreEntities(List<Entity> buffer, List<Cursor> cursorBuffer);

  /**
   * Load the specified number of {@code Entity} objects and add them to the given buffer. This
   * method will only return less than {@code numberToLoad} if less than {@code numberToLoad}
   * entities are present. However this method may return more than {@code numberToLoad} entities at
   * any time.
   *
   * <p>Requesting 0 results to be loaded has the effect of ensuring any offset requested has been
   * satisfied but not requiring any results be loaded (although some might be. This is usually
   * needed before calling {@link #getNumSkipped()} or to get the first {@link Cursor}.
   *
   * <p>This will return {@code null} in any of the following conditions:
   *
   * <ul>
   *   <li>{@link #hasMoreEntities()} is false
   *   <li>No results were requested and no offset needed to be satisfied.
   *   <li>the query does not support the compile flag
   *   <li>the compile flag was not set on the {@link FetchOptions} used to run the query
   *       <ul>
   *
   * @param numberToLoad the number of entities to get.
   * @param buffer An out parameter to which the requested entities will be added.
   * @param cursorBuffer An out parameter to which the requested entity cursors will be added.
   * @return the cursor that points to the {@link Entity} after the last {@link Entity} returned or
   *     {@code null}
   */
  Cursor loadMoreEntities(int numberToLoad, List<Entity> buffer, List<Cursor> cursorBuffer);

  /**
   * Returns the number of entities that have been skipped so far.
   *
   * <p>Entities are skipped when an offset has been set on the query.
   */
  int getNumSkipped();

  /**
   * Get the indexes used to perform the query. May sometimes return only the indexes used so far.
   *
   * @return A list of index ids, with no duplicates, or {@code null} if the indexes are not known.
   */
  // @Nullable
  List<Index> getIndexList();
}
