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

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.common.base.Preconditions;
import java.io.Serializable;

/**
 * Represents a single facet result value. The value has a label, a count, and a refinementToken.
 */
public final class FacetResultValue implements Serializable {

  private static final long serialVersionUID = 1171761338331659834L;

  /**
   * Creates and returns a facet result value.
   *
   * @param label The label of the result returned by the backend that is the name of facet
   * for atom facets and the string "[start,end)" for number facets.
   * @param count an integer representing how many times this value is repeated in the result.
   * @param refinementToken the token string for further refinement of the search result. To
   * combine values for a single facet, add each of them separately to FacetRequest. There
   * will be a disjunction between refinements for the same facet.
   * @return an instance of {@link FacetResultValue}.
   * @throws IllegalArgumentException if label or refinementToken are empty.
   */
  public static FacetResultValue create(String label, int count, String refinementToken) {
    return new FacetResultValue(label, count, refinementToken);
  }

  // Mandatory
  private final String label;
  private final int count;
  private final String refinementToken;

  /**
   * Constructs a facet result value.
   */
  private FacetResultValue(String label, int count, String refinementToken) {
    Preconditions.checkNotNull(label, "label cannot be null");
    Preconditions.checkNotNull(refinementToken, "refinementToken cannot be null");
    Preconditions.checkArgument(!label.isEmpty(), "label cannot be empty");
    Preconditions.checkArgument(!refinementToken.isEmpty(), "refinementToken cannot be empty");
    this.label = label;
    this.count = count;
    this.refinementToken = refinementToken;
    checkValid();
  }

  /**
   * Returns the label of this facet result value. The value label returned by the backend can be a
   * single facet value name, or a range label in "[start,end)" format.
   *
   * @return label as string
   */
  public String getLabel() {
    return label;
  }

  /**
   * Returns the refinement token for this result value. This token can be used to
   * filter the result of new searches using this facet value.
   *
   * @return the refinement token string.
   */
  public String getRefinementToken() {
    return refinementToken;
  }

  /**
   * Returns the count of the result value, which is an integer representing
   * how many times this value is repeated in the result for the given facet value or range.
   *
   * @return the count of this label in facet result.
   */
  public int getCount() {
    return count;
  }

  private void checkValid() {
    Preconditions.checkState(label != null && !label.isEmpty(), "Label cannot be empty.");
    Preconditions.checkState(refinementToken != null && !refinementToken.isEmpty(),
        "Refinement token cannot be empty.");
  }

  /**
   * Creates a new facet result value from the given protocol
   * buffer facet result value object.
   *
   * @param facetResultValue the facet result value protocol buffer to build
   * a facet result value object from.
   * @return the facet result value initialized from a facet result value protocol buffer.
   */
  static FacetResultValue withProtoMessage(
      SearchServicePb.FacetResultValue facetResultValue) {
    return create(
        facetResultValue.getName(),
        facetResultValue.getCount(),
        FacetRefinement.withProtoMessage(facetResultValue.getRefinement()).toTokenString());
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("FacetResultValue")
        .addField("label", getLabel())
        .addField("count", getCount())
        .addField("refinementToken", getRefinementToken())
        .finish();
  }
}
