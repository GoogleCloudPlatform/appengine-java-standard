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

import com.google.appengine.api.search.proto.SearchServicePb.ScorerSpec;
import com.google.common.base.Preconditions;

/**
 * Checks the values of a {@link com.google.appengine.api.search.SortOptions}.
 *
 */
public class SortOptionsChecker {

  /**
   * Checks whether the limit on number of documents to score is between 0 and
   * the maximum.
   *
   * @param limit the maximum number of documents to score in the search
   * @return the checked limit
   * @throws IllegalArgumentException if the limit is out of range
   */
  public static int checkLimit(int limit) {
    Preconditions.checkArgument(
        limit >= 0 && limit <= SearchApiLimits.SEARCH_MAXIMUM_SORTED_LIMIT,
        "The limit %s must be between 0 and %s",
        limit,
        SearchApiLimits.SEARCH_MAXIMUM_SORTED_LIMIT);
    return limit;
  }

  /**
   * Checks the {@link ScorerSpec} is valid, specifically checking the limit
   * on number of documents to score is not too large.
   *
   * @param spec the {@link ScorerSpec} to check
   * @return the checked spec
   * @throws IllegalArgumentException if the spec is invalid
   */
  public static ScorerSpec checkValid(ScorerSpec spec) {
    checkLimit(spec.getLimit());
    return spec;
  }
}
