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

package com.google.appengine.api.search;

import com.google.appengine.api.search.checkers.FacetChecker;
import com.google.appengine.api.search.checkers.FacetQueryChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * A facet request representing parameters for requesting specific facets to be returned with a
 * query result.
 *
 * <p>For example, to request a facet with a name and specific values:
 *
 * <pre>{@code
 *   FacetRequest request = FacetRequest.newBuilder().setName("wine_type")
 *       .addValueConstraint("white").addValueConstraint("red").build();
 * } </pre>
 *
 * and to request ranges:
 *
 * <pre>{@code
 * FacetRequest request = FacetRequest.newBuilder().setName("year")
 *     .addRange(null, 2000.0)           // year < 2000.0
 *     .addRange(1980.0, 2000.0)         // 1980.0 <= year < 2000.0
 *     .addRange(2000.0, null).build();  // year >= 2000.0
 * }</pre>
 */
public final class FacetRequest {

  /**
   * A facet request builder. Each facet request should at least have the {@code name} of the facet.
   * It can also includes number of values, a list of constraints on the values or a list of ranges
   * for numeric facets. Note that the list of constraints and the list of ranges are mutually
   * exclusive, i.e. you can specify one of them but not both.
   */
  public static final class Builder {
    // Mandatory
    private String name;

    private List<String> constraints = new ArrayList<>();
    private List<FacetRange> ranges = new ArrayList<>();

    // Optional
    private Integer valueLimit;

    private Builder(FacetRequest request) {
      this.name = request.name;
      this.constraints = new ArrayList<>(request.getValueConstraints());
      this.ranges = new ArrayList<>(request.getRanges());
      this.valueLimit = request.getValueLimit();
    }

    private Builder() {}

    /**
     * Sets the maximum number of values for this facet to return.
     *
     * @return this Builder
     * @throws IllegalArgumentException if valueLimit is negetive or zero or greater than {@link
     *     SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}
     */
    public Builder setValueLimit(int valueLimit) {
      this.valueLimit = FacetQueryChecker.checkValueLimit(valueLimit);
      return this;
    }

    /**
     * Sets the name of the facet for this request.
     *
     * @return this Builder
     * @throws IllegalArgumentException if name is empty or longer than {@link
     *     SearchApiLimits#MAXIMUM_NAME_LENGTH}
     */
    public Builder setName(String name) {
      this.name = FacetChecker.checkFacetName(name);
      return this;
    }

    /**
     * Adds a value {@code constraint} to this facet request. Note that ranges and value constraints
     * are mutually exclusive. Either of them can be provided, but not both for the same request.
     *
     * @return this Builder
     * @throws IllegalArgumentException if the constraint empty or longer than {@link
     *     SearchApiLimits#MAXIMUM_ATOM_LENGTH}.
     * @throws IllegalStateException if any number of ranges or {@link
     *     SearchApiLimits#FACET_MAXIMUM_CONSTRAINTS} constraints have already been added.
     */
    public Builder addValueConstraint(String constraint) {
      FacetQueryChecker.checkFacetValue(constraint);
      Preconditions.checkState(ranges.isEmpty(), "Ranges list should be empty.");
      Preconditions.checkState(
          constraints.size() < SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS,
          "More than %s constraints.",
          SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS);
      constraints.add(constraint);
      return this;
    }

    /**
     * Adds a {@link FacetRange} to this request. Note that ranges and value constraints are
     * mutually exclusive. Either of them can be provided, but not both for the same request.
     *
     * @throws NullPointerException if {@code range} is null.
     * @throws IllegalStateException if constraints list is not empty or number of ranges became
     *     greater than SearchApiLimits.FACET_MAXIMUM_RANGES by adding this range.
     * @return this Builder
     */
    public Builder addRange(FacetRange range) {
      Preconditions.checkNotNull(range, "range should not be null.");
      Preconditions.checkState(constraints.isEmpty(), "Constraints list should be empty.");
      Preconditions.checkState(
          ranges.size() < SearchApiLimits.FACET_MAXIMUM_RANGES,
          "More than %s ranges.",
          SearchApiLimits.FACET_MAXIMUM_RANGES);
      ranges.add(range);
      return this;
    }

    /**
     * Construct the final message.
     *
     * @return the FacetRequest built from the parameters entered on this Builder
     * @throws IllegalArgumentException if the facet request is invalid
     */
    public FacetRequest build() {
      return new FacetRequest(this);
    }
  }

  // Mandatory
  private final String name;

  private final ImmutableList<String> constraints;
  private final ImmutableList<FacetRange> ranges;

  // Optional
  private Integer valueLimit;

  private FacetRequest(Builder builder) {
    this.name = builder.name;
    this.constraints = ImmutableList.copyOf(builder.constraints);
    this.ranges = ImmutableList.copyOf(builder.ranges);
    this.valueLimit = builder.valueLimit;
    checkValid();
  }

  /**
   * Creates and returns a {@link FacetRequest} builder. Set the facet request parameters and use
   * the {@link Builder#build()} method to create a concrete instance of FacetRequest.
   *
   * @return a {@link Builder} which can construct a facet request
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a builder from the given FacetRequest.
   *
   * @param request the facet request for the builder to use to build another request.
   * @return a new builder with values set from the given request
   */
  public static Builder newBuilder(FacetRequest request) {
    return new Builder(request);
  }

  /** Returns the name of the face in this request. */
  public String getName() {
    return name;
  }

  /**
   * Returns the maximum number of values this facet should have. Null if the value limit is not
   * set.
   */
  public Integer getValueLimit() {
    return valueLimit;
  }

  /** Returns an unmodifiable list of {@link FacetRange}s. */
  public List<FacetRange> getRanges() {
    return ranges;
  }

  /** Returns an unmodifiable list of value constraints. */
  public List<String> getValueConstraints() {
    return constraints;
  }

  /**
   * Checks the facet request is valid, specifically, has a non-null non-empty name, non-overlapping
   * ranges and exclusive ranges or constraints, not both at the same time.
   *
   * @throws IllegalArgumentException if some part of the specification is invalid
   */
  private void checkValid() {
    FacetChecker.checkFacetName(getName());
    Preconditions.checkState(
        constraints.size() < SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS,
        "More than %s constraints.",
        SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS);
    Preconditions.checkState(
        ranges.size() < SearchApiLimits.FACET_MAXIMUM_RANGES,
        "More than %s ranges.",
        SearchApiLimits.FACET_MAXIMUM_RANGES);
    Preconditions.checkState(
        constraints.isEmpty() || ranges.isEmpty(),
        "Constraints and ranges set for the same request.");
    for (String constraint : getValueConstraints()) {
      FacetQueryChecker.checkFacetValue(constraint);
    }
  }

  /**
   * Copies the contents of this {@link FacetRequest} object into a {@link
   * SearchServicePb.FacetRequest} protocol buffer.
   *
   * @return a facet request protocol buffer with the values from this request
   */
  SearchServicePb.FacetRequest copyToProtocolBuffer() {
    if (constraints.isEmpty() && ranges.isEmpty() && valueLimit == null) {
      return SearchServicePb.FacetRequest.newBuilder().setName(name).build();
    }
    SearchServicePb.FacetRequestParam.Builder param =
        SearchServicePb.FacetRequestParam.newBuilder();
    for (String constraint : constraints) {
      param.addValueConstraint(constraint);
    }
    for (FacetRange range : ranges) {
      SearchServicePb.FacetRange.Builder rangePb = param.addRangeBuilder();
      if (range.getStart() != null) {
        rangePb.setStart(range.getStart());
      }
      if (range.getEnd() != null) {
        rangePb.setEnd(range.getEnd());
      }
    }
    if (valueLimit != null) {
      param.setValueLimit(valueLimit);
    }
    return SearchServicePb.FacetRequest.newBuilder().setName(name).setParams(param.build()).build();
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("FacetRequest")
        .addField("name", name)
        .addIterableField("valueConstraints", constraints)
        .addIterableField("ranges", ranges)
        .finish();
  }
}
