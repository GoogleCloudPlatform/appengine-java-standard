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

import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRefinement;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequest;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequestParam;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.apphosting.api.AppEngineInternal;
import com.google.common.base.Preconditions;

/**
 * Provides checks for faceted search related query options.
 *
 */
@AppEngineInternal
public final class FacetQueryChecker {

  private static void checkMaximum(int number, int max, String context) {
    Preconditions.checkArgument(number > 0, "%s should be positive", context);
    Preconditions.checkArgument(number <= max,
        "%s must be less than or equal to %s", context, max);
  }

  /**
   * Checks that a discovery limit is valid. The value must
   * be greater than 0 and less than {@link SearchApiLimits#FACET_MAXIMUM_DISCOVERY_LIMIT}.
   *
   * @param value the discovery limit to check
   * @return the discovery limit
   * @throws IllegalArgumentException if the discovery limit is less than 1 or
   * greater than {@literal SearchApiLimits#FACET_MAXIMUM_DISCOVERY_LIMIT}.
   */
  public static int checkDiscoveryLimit(int value) {
    checkMaximum(value, SearchApiLimits.FACET_MAXIMUM_DISCOVERY_LIMIT,
        "Facet discovery limit");
    return value;
  }

  /**
   * Checks that a value constraint is valid. The Value length must
   * be at least 1 and less than {@link SearchApiLimits#MAXIMUM_ATOM_LENGTH}.
   *
   * @param value the value constraint to check
   * @return the value constraint
   * @throws IllegalArgumentException if the Value length is less than 1 or
   * greater than {@literal SearchApiLimits#FACET_MAXIMUM_VALUE_LENGTH}.
   */
  public static String checkFacetValue(String value) {
    return FacetChecker.checkAtom(value);
  }

  /**
   * Checks that a facet depth option is valid. The facet depth must
   * be greater than 0 and less than {@link SearchApiLimits#FACET_MAXIMUM_DEPTH}.
   *
   * @param value the facet depth option to check
   * @return the facet depth
   * @throws IllegalArgumentException if the facet depth option is less than 1 or
   * greater than {@literal SearchApiLimits#FACET_MAXIMUM_DEPTH}.
   */
  public static Integer checkDepth(Integer value) {
    if (value != null) {
      checkMaximum(value, SearchApiLimits.FACET_MAXIMUM_DEPTH, "Facet depth option");
    }
    return value;
  }

  /**
   * Checks whether discovery value limit option is valid. The discovery value limit must
   * be greater than 0 and less than {@link SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}.
   *
   * @param value the discovery value limit to check
   * @return the discovery value limit
   * @throws IllegalArgumentException if the discovery value limit is less than 1 or
   * greater than {@literal SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}.
   */
  public static Integer checkDiscoveryValueLimit(Integer value) {
    if (value != null) {
      checkMaximum(value, SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT,
          "Facet discovery value limit");
    }
    return value;
  }

  /**
   * Checks whether a value limit option is valid. The value limit must
   * be greater than 0 and less than {@link SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}.
   *
   * @param value the value limit to check
   * @return the value limit
   * @throws IllegalArgumentException if the value limit is less than 1 or
   * greater than {@literal SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}.
   */
  public static Integer checkValueLimit(Integer value) {
    if (value != null) {
      checkMaximum(value, SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT, "Facet value limit");
    }
    return value;
  }

  private static void checkRange(FacetRange range) {
    Preconditions.checkArgument(range.getName().isEmpty(),
        "Facet range name (%s) must be empty.", range.getName());
    Preconditions.checkArgument(range.hasStart() || range.hasEnd(), "Facet range is unbounded.");
    Preconditions.checkArgument(!range.hasStart() || isFinite(range.getStart()),
        "Facet range start (%s) must be finite.", range.getStart());
    Preconditions.checkArgument(!range.hasEnd() || isFinite(range.getEnd()),
        "Facet range end (%s) must be finite.", range.getEnd());
  }

  private static void checkRefinementRange(FacetRefinement.Range range) {
    Preconditions.checkArgument(range.hasStart() || range.hasEnd(),
        "Facet refinement range is unbounded.");
    Preconditions.checkArgument(!range.hasStart() || isFinite(range.getStart()),
        "Facet refinement range start (%s) must be finite.", range.getStart());
    Preconditions.checkArgument(!range.hasEnd() || isFinite(range.getEnd()),
        "Facet refinement range end (%s) must be finite.", range.getEnd());
  }

  private static boolean isFinite(String numberString) {
    try {
      Double number = Double.parseDouble(numberString);
      return !number.isNaN() && !number.isInfinite();
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Checks whether all options related to faceted search are valid.
   *
   * @param params the SearchParams to check
   * @return this checked SearchParams
   * @throws IllegalArgumentException if some part of the specification is invalid
   */
  public static SearchParams checkValid(SearchParams params) {
    if (params.getAutoDiscoverFacetCount() != 0) {
      checkDiscoveryLimit(params.getAutoDiscoverFacetCount());
    }
    checkDepth(params.getFacetDepth());
    checkDiscoveryValueLimit(params.getFacetAutoDetectParam().getValueLimit());
    for (FacetRequest facetRequest : params.getIncludeFacetList()) {
      FacetRequestParam reqParams = facetRequest.getParams();
      FacetChecker.checkFacetName(facetRequest.getName());
      if (reqParams.hasValueLimit()) {
        checkValueLimit(reqParams.getValueLimit());
      }
      Preconditions.checkArgument(reqParams.getValueConstraintCount()
          <= SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS, "More than %s constraints.",
          SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS);
      Preconditions.checkArgument(reqParams.getRangeCount()
          <= SearchApiLimits.FACET_MAXIMUM_RANGES,
          "More than %s ranges.", SearchApiLimits.FACET_MAXIMUM_RANGES);
      Preconditions.checkArgument(reqParams.getValueConstraintCount() == 0
          || reqParams.getRangeCount() == 0, "Constraints and ranges set at the same request.");
      for (String constraint : reqParams.getValueConstraintList()) {
        checkFacetValue(constraint);
      }
      for (FacetRange range : reqParams.getRangeList()) {
        checkRange(range);
      }
    }
    for (FacetRefinement ref : params.getFacetRefinementList()) {
      FacetChecker.checkFacetName(ref.getName());
      Preconditions.checkArgument(
          ref.hasValue() || ref.hasRange(),
          "Neither value nor range is set for FacetRefinement %s",
          ref.getName());
      Preconditions.checkArgument(
          !ref.hasValue() || !ref.hasRange(),
          "Both value and range are set for FacetRefinement %s",
          ref.getName());
      if (ref.hasValue()) {
        checkFacetValue(ref.getValue());
      }
      if (ref.hasRange()) {
        checkRefinementRange(ref.getRange());
      }
    }
    return params;
  }

  private FacetQueryChecker() {
  }
}
