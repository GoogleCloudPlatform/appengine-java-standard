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

import static com.google.common.io.BaseEncoding.base64Url;

import com.google.appengine.api.search.checkers.FacetQueryChecker;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A Facet Refinement to filter out search results based on a facet value.
 * <p>
 * We recommend using refinement token strings instead of this class. We include a refinement
 * token string with each {@link FacetResultValue} returned by the backend that can be passed to
 * {@link Query.Builder#addFacetRefinementFromToken(String)} to refine follow-up queries.
 * <p>
 * We also support manually-specified query refinements by passing an instance of this class to
 * {@link Query.Builder#addFacetRefinement(FacetRefinement)}.
 * <p>
 * Example: Request to only return documents that have a number facet named "rating" with a value
 * between one and two:
 * <pre>{@code
 * FacetRefinement lowRating = FacetRefinement.withRange("rating", FacetRange.startEnd(1.0, 2.0));
 * query.addFacetRefinement(lowRating);
 * }</pre>
 */
public final class FacetRefinement {

  private static final int MAX_TOKEN_LENGTH_IN_EXCEPTIONS = 100;

  /**
   * Create a {@link FacetRefinement} with the given {@code name} and {@code value}.
   *
   * @param name the name of the facet.
   * @param value the value of the facet (both numeric and atom).
   * @return an instance of {@link FacetRefinement}.
   * @throws IllegalArgumentException if {@code name} is null or empty or the {@code value} length
   * is less than 1 or greater than {@literal SearchApiLimits#FACET_MAXIMUM_VALUE_LENGTH}.
   */
  public static FacetRefinement withValue(String name, String value) {
    Preconditions.checkArgument(name != null && !name.isEmpty(), "Name should not be empty");
    FacetQueryChecker.checkFacetValue(value);
    return new FacetRefinement(name, value, null);
  }

  /**
   * Create a {@link FacetRefinement} with the given {@code name} and {@code range}.
   *
   * @param name the name of the facet.
   * @param range the range of the numeric facet.
   * @return an instance of {@link FacetRefinement}.
   * @throws IllegalArgumentException if {@code name} is null or empty.
   */
  public static FacetRefinement withRange(String name, FacetRange range) {
    Preconditions.checkArgument(name != null && !name.isEmpty(), "Name should not be empty");
    return new FacetRefinement(name, null, range);
  }

  // Mandatory
  private final String name;

  // Optional
  private final String value;
  private final FacetRange range;

  private FacetRefinement(String name, String value, FacetRange range) {
    this.name = name;
    this.value = value;
    this.range = range;
    checkValid();
  }

  static FacetRefinement withProtoMessage(SearchServicePb.FacetRefinement refinementPb) {
    if (refinementPb.hasValue()) {
      return new FacetRefinement(refinementPb.getName(), refinementPb.getValue(), null);
    } else if (refinementPb.hasRange()) {
      FacetRange range;
      if (refinementPb.getRange().hasStart()) {
        if (refinementPb.getRange().hasEnd()) {
          range = FacetRange.withStartEnd(
              Facet.stringToNumber(refinementPb.getRange().getStart()),
              Facet.stringToNumber(refinementPb.getRange().getEnd()));
        } else {
          range = FacetRange.withStart(
              Facet.stringToNumber(refinementPb.getRange().getStart()));
        }
      } else {
        if (refinementPb.getRange().hasEnd()) {
          range = FacetRange.withEnd(
              Facet.stringToNumber(refinementPb.getRange().getEnd()));
        } else {
          throw new IllegalStateException(String.format(
              "Refinement %s has invalid range.", refinementPb.getName()));
        }
      }
      return new FacetRefinement(refinementPb.getName(), null, range);
    } else {
      throw new IllegalStateException(String.format(
          "Refinement %s should have value or range.", refinementPb.getName()));
    }
  }

  /**
   * Returns the name of the facet.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the value of the facet or null if there is no value.
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns the range for numeric facets or null if there is no range.
   */
  public FacetRange getRange() {
    return range;
  }

  /**
  * Checks if the FacetRefinement is valid. FacetRefinement should have name and either
  * range or value.
  *
  * @throws IllegalArgumentException if FacetRefinement is invalid.
  */
  private void checkValid() {
    Preconditions.checkArgument(getName() != null && !getName().isEmpty(),
        "name should not be null or empty.");
    Preconditions.checkArgument(
        getValue() != null || getRange() != null,
        "Neither value nor range is set for FacetRefinement %s",
        getName());
    Preconditions.checkArgument(
        getValue() == null || getRange() == null,
        "Both value and range are set for FacetRefinement %s",
        getName());
  }

  SearchServicePb.FacetRefinement toProtocolBuffer() {
    SearchServicePb.FacetRefinement.Builder builder =
        SearchServicePb.FacetRefinement.newBuilder();
    builder.setName(getName());
    if (getValue() != null) {
      builder.setValue(getValue());
    }
    if (getRange() != null) {
      SearchServicePb.FacetRefinement.Range.Builder rangePb = builder.getRangeBuilder();
      if (getRange().getStart() != null) {
        rangePb.setStart(getRange().getStart());
      }
      if (getRange().getEnd() != null) {
        rangePb.setEnd(getRange().getEnd());
      }
    }
    return builder.build();
  }

  /**
   * Converts this refinement to a token string safe to be used in HTML.
   * <p>
   * NOTE: Do not persist token strings. The format may change.
   *
   * @return A token string safe to be used in HTML for this facet refinement.
   */
  public String toTokenString() {
    try {
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      toProtocolBuffer().writeTo(data);
      return base64Url().omitPadding().encode(data.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("should never happen", e);
    }
  }

  // TODO: add a unit test for this method.
  private static String truncateAtMaxLength(String source, int maxLength) {

    if (source.length() <= maxLength) {
      return source;
    }
    return source.substring(0, maxLength) + " ...";
  }

  /**
   * Converts a token string to a FacetRefinement object.
   * <p>
   * NOTE: Do not persist token strings. The format may change.
   *
   * @param token A token string returned by {@link FacetResultValue#getRefinementToken} or
   *    {@link FacetRefinement#toTokenString}.
   *
   * @return A {@link FacetRefinement} object.
   * @throws IllegalArgumentException if the given token cannot be processed.
   */
  public static FacetRefinement fromTokenString(String token) {
    try {
      return withProtoMessage(
          SearchServicePb.FacetRefinement.parseFrom(base64Url().decode(token)));
    } catch (InvalidProtocolBufferException|IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot process refinement token: "
          + truncateAtMaxLength(token, MAX_TOKEN_LENGTH_IN_EXCEPTIONS), e);
    }
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("FacetRefinement")
        .addField("name", name)
        .addField("value", value)
        .addField("range", range)
        .finish();
  }
}
