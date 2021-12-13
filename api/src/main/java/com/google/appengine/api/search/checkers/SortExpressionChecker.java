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

import com.google.appengine.api.search.proto.SearchServicePb.SortSpec;
import com.google.common.base.Preconditions;

/**
 * Checks the values of a {@link com.google.appengine.api.search.SortExpression}.
 *
 */
public class SortExpressionChecker {

  /**
   * Checks the {@link SortSpec} is valid, specifically checking the limit
   * on number of documents to score is not too large.
   *
   * @param spec the {@link SortSpec} to check
   * @return the checked spec
   * @throws IllegalArgumentException if the spec is invalid
   */
  public static SortSpec checkValid(SortSpec spec) {
    Preconditions.checkArgument(
        (!spec.hasDefaultValueText() || !spec.hasDefaultValueNumeric()),
        "at most one of default string or numeric value can be specified");
    if (spec.hasDefaultValueText()) {
      FieldChecker.checkText(spec.getDefaultValueText());
    }
    return spec;
  }
}
