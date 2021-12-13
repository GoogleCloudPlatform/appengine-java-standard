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

import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import java.util.List;

/**
 * Interface describing an object than knows how to split {@link FilterPredicate}s from a {@link
 * Query} into list of {@link QuerySplitComponent}s.
 *
 */
interface QuerySplitter {
  /**
   * Removes filters from the provided list and converts them into {@link QuerySplitComponent}s. All
   * filters that are satisfied by the returned {@link QuerySplitComponent}s should be removed from
   * the given list of filters.
   *
   * @param remainingFilters the filters to consider. Potentially mutated in this function.
   * @param sorts the sort orders imposed on the query being split
   * @return the {@link QuerySplitComponent}s that have been generated or an empty list.
   */
  List<QuerySplitComponent> split(
      List<FilterPredicate> remainingFilters, List<SortPredicate> sorts);
}
