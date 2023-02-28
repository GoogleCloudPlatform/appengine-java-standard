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

import com.google.appengine.api.search.checkers.FacetQueryChecker;
import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb.FacetAutoDetectParam;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;

/**
 * A {@code FacetOptions} represents facet options such as the number of facets to discover
 * ({@code discoveryLimit}), the number of values to be included in each discovered
 * facet ({@code discoveryValueLimit}), and the depth of the results to check ({@code depth}).
 * Note that discovery is disabled when {@code discoveryLimit} is zero.
 * <p>
 * For example, to discover 10 facets with 5 values each over 1000 extended results:
 * <pre>{@code
 *   FacetOptions facetOption = FacetOptions.newBuilder()
 *       .setDiscoverLimit(10)
 *       .setDiscoverValueLimit(5)
 *       .setDepth(1000)
 *       .build();
 * }</pre>
 */
public final class FacetOptions {

  /**
   * Builder for {@link FacetOptions}.
   */
  public static final class Builder {
    // Mandatory for FacetOptions. If user does not provide any of these values, default values in
    // SearchApiLimits will be used.
    private Integer discoveryValueLimit;
    private Integer discoveryLimit;
    private Integer depth;

    private Builder() {
    }

    /**
     * Constructs a {@link FacetOptions} builder with the given facet options.
     *
     * @param options the search request to populate the builder
     */
    private Builder(FacetOptions options) {
      discoveryValueLimit = options.getDiscoveryValueLimit();
      discoveryLimit = options.getDiscoveryLimit();
      depth = options.getDepth();
    }

    /**
     * Sets the maximum number of values each discovered facet should have.
     *
     * @return this Builder
     * @throws IllegalArgumentException if value is negative or zero or greater than
     * {@link SearchApiLimits#FACET_MAXIMUM_VALUE_LIMIT}
     */
    public Builder setDiscoveryValueLimit(int value) {
      this.discoveryValueLimit = FacetQueryChecker.checkDiscoveryValueLimit(value);
      return this;
    }

    /**
     * Sets the number of facets to be discovered.
     *
     * @return this Builder
     * @throws IllegalArgumentException if the value is zero or negative or is larger
     * than {@link SearchApiLimits#FACET_MAXIMUM_DISCOVERY_LIMIT}
     */
    public Builder setDiscoveryLimit(int value) {
      this.discoveryLimit = FacetQueryChecker.checkDiscoveryLimit(value);
      return this;
    }

    /**
     * Sets the number of documents from the search result to be analyzed for facet discovery.
     *
     * @return this Builder
     * @throws IllegalArgumentException if the value is zero or negative or is larger
     * than {@link SearchApiLimits#FACET_MAXIMUM_DEPTH}.
     */
    public Builder setDepth(int value) {
      this.depth = FacetQueryChecker.checkDepth(value);
      return this;
    }

    /**
     * Returns an immutable {@link FacetOptions} that reflects the current state of this Builder.
     */
    public FacetOptions build() {
      return new FacetOptions(this);
    }
  }

  private final Integer discoveryValueLimit;
  private final Integer discoveryLimit;
  private final Integer depth;

  private FacetOptions(Builder builder) {
    discoveryValueLimit = builder.discoveryValueLimit;
    discoveryLimit = builder.discoveryLimit;
    depth = builder.depth;
  }

  /**
   * Returns the number of facets to be discovered or null if unset.
   */
  public Integer getDiscoveryLimit() {
    return discoveryLimit;
  }

  /**
   * Returns the maximum number of values for each discovered facet or null if unset.
   */
  public Integer getDiscoveryValueLimit() {
    return discoveryValueLimit;
  }

  /**
   * Returns the number of documents from the search result to be analyzed for facet discovery
   * or null if unset.
   */
  public Integer getDepth() {
    return depth;
  }

  /**
   * Creates and returns an empty {@link Builder}.
   *
   * @return a {@link Builder} which can construct a facet options.
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates and returns a {@link Builder} that reflects the given options.
   *
   * @param options the options that the returned builder will reflect.
   * @return a new builder with values set from the given options.
   */
  public static Builder newBuilder(FacetOptions options) {
    return new Builder(options);
  }

  /**
   * Copies the contents of this {@link FacetOptions} object into a
   * {@link SearchParams} protocol buffer builder.
   */
  void copyToProtocolBuffer(SearchParams.Builder builder,
      boolean enableFacetDiscovery) {
    if (enableFacetDiscovery) {
      if (discoveryLimit == null) {
        builder.setAutoDiscoverFacetCount(SearchApiLimits.FACET_DEFAULT_DISCOVERY_LIMIT);
      } else {
        builder.setAutoDiscoverFacetCount(discoveryLimit);
      }
    } else {
      builder.clearAutoDiscoverFacetCount();
    }
    if (depth != null) {
      builder.setFacetDepth(depth);
    }
    if (discoveryValueLimit != null) {
      builder.setFacetAutoDetectParam(
          FacetAutoDetectParam.newBuilder().setValueLimit(discoveryValueLimit));
    }
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("FacetOptions")
        .addField("discoveryValueLimit", getDiscoveryValueLimit())
        .addField("discoveryLimit", getDiscoveryLimit())
        .addField("depth", getDepth())
        .finish();
  }
}

