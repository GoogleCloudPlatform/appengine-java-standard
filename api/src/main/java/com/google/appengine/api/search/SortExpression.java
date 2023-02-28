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

import com.google.appengine.api.search.checkers.FieldChecker;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.common.base.Preconditions;
import java.util.Date;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Sorting specification for a single dimension. Multi-dimensional sorting
 * is supported by a collection of SortExpressions.
 */
public final class SortExpression {

  /**
   * The expression to be used if you wish to sort by document id field
   * {@link Document#getId()}.
   * You need to create a sort expression as
   * <pre>{@code
   * SortExpression expr = SortExpression.newBuilder()
   *     .setExpression(SortExpression.DOCUMENT_ID_FIELD_NAME)
   *     .setDefaultValue("")
   *     .build();
   * }</pre>
   */
  public static final String DOCUMENT_ID_FIELD_NAME = "_doc_id";

  /**
   * The expression to be used if you wish to sort by language
   * code associated with the locale field {@link Document#getLocale()}.
   * You need to create a sort expression as
   * <pre>{@code
   * SortExpression expr = SortExpression.newBuilder()
   *     .setExpression(SortExpression.LANGUAGE_FIELD_NAME)
   *     .setDefaultValue("")
   *     .build();
   * }</pre>
   */
  public static final String LANGUAGE_FIELD_NAME = "_lang";

  /**
   * The expression to be used if you wish to sort by rank field.
   * By default, results are sorted in descending value of rank.
   * To sort in ascending order, you need to create a sort expression as
   * <pre>{@code
   * SortExpression expr = SortExpression.newBuilder()
   *     .setExpression(SortExpression.RANK_FIELD_NAME)
   *     .setDirection(SortExpression.SortDirection.ASCENDING)
   *     .setDefaultValueNumeric(0)
   *     .build();
   * }</pre>
   */
  public static final String RANK_FIELD_NAME = "_rank";

  /**
   * The expression to be used if you wish to sort by document score.
   * You need to create a sort expression as
   * <pre>{@code
   * SortExpression expr = SortExpression.newBuilder()
   *     .setExpression(String.format(
   *         "%s + rating * 0.01", SortExpression.SCORE_FIELD_NAME))
   *     .setDirection(SortExpression.SortDirection.DESCENDING)
   *     .setDefaultValueNumeric(0)
   *     .build();
   * }</pre>
   */
  public static final String SCORE_FIELD_NAME = "_score";

  /**
   * The expression to be used if you wish to sort by
   * seconds since EPOCH that the document was written.
   * You need to create a sort expression as
   * <pre>{@code
   * SortExpression expr = SortExpression.newBuilder()
   *     .setExpression(SortExpression.TIMESTAMP_FIELD_NAME)
   *     .setDefaultValueNumeric(0)
   *     .build();
   * }</pre>
   */
  public static final String TIMESTAMP_FIELD_NAME = "_timestamp";

  /**
   * The direction search results are sorted by, either ascending or descending.
   */
  public enum SortDirection {
    /**
     * The search results are sorted in ascending order,
     * e.g. alphabetic aaa ... zzz
     */
    ASCENDING,

    /**
     * The search results are sorted in descending order,
     * e.g. zzz ... aaa
     */
    DESCENDING
  }

  /**
   * A builder that constructs {@link SortExpression SortExpressions}. The user
   * must provide an expression. The expression can be as simple as a field
   * name, or can be some other expression such as <code>&quot;score +
   * count(likes) * 0.1&quot;</code>, which combines a scorer score with a count
   * of the number of likes values times 0.1. A default value must be specified
   * for the expression.
   * <p>
   * <pre>{@code
   *   SortExpression expr = SortExpression.newBuilder()
   *       .setExpression(String.format(
   *           "%s + count(likes) * 0.1", SortExpression.SCORE_FIELD_NAME))
   *       .setDirection(SortExpression.SortDirection.ASCENDING)
   *       .setDefaultValueNumeric(0.0)
   *       .build()
   * }</pre>
   */
  public static final class Builder {
    // Optional
    private String expression;
    private SortDirection direction;
    @Nullable private String defaultValue;
    @Nullable private Double defaultValueNumeric;
    @Nullable private Date defaultValueDate;

    private Builder() {
    }

    /**
     * Sets an expression to be evaluated for each document to sort by.
     * A default string value {@link #setDefaultValue(String)} or numeric
     * {@link #setDefaultValueNumeric(double)} or date
     * {@link #setDefaultValueDate(Date)} must be specified for the expression.
     *
     * @param expression the expression to evaluate for each
     * document to sort by
     * @return this Builder
     * @throws IllegalArgumentException if the expression is invalid
     */
    public Builder setExpression(String expression) {
      this.expression = FieldChecker.checkSortExpression(expression);
      return this;
    }

    /**
     * Sets the direction to sort the search results in.
     *
     * @param direction the direction to sort the search results in. The
     * default direction is {@link SortDirection#DESCENDING}
     * @return this Builder
     */
    public Builder setDirection(SortDirection direction) {
      this.direction = direction;
      return this;
    }

    /**
     * Sets the default value for the field for sorting purposes. Must provide
     * for text sorts.
     *
     * @param defaultValue the default value for the field
     * @return this Builder
     * @throws IllegalArgumentException if the {@code defaultValue} is not valid
     */
    public Builder setDefaultValue(String defaultValue) {
      // TODO: check that backend could provide default values if required.
      this.defaultValue = FieldChecker.checkText(defaultValue);
      return this;
    }

    /**
     * Sets the default value for the field for sorting purposes. Must provide
     * for numeric sorts.
     *
     * @param defaultValue the default value for the field
     * @return this Builder
     */
    public Builder setDefaultValueNumeric(double defaultValue) {
      this.defaultValueNumeric = defaultValue;
      return this;
    }

    /**
     * Sets the default value for the field for sorting purposes. Must provide
     * for Date sorts. Typically, you should use
     * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MINIMUM_DATE_VALUE} or
     * {@link com.google.appengine.api.search.checkers.SearchApiLimits#MAXIMUM_DATE_VALUE}
     * as a default value.
     *
     * @param defaultValue the default value for the field
     * @return this Builder
     */
    public Builder setDefaultValueDate(Date defaultValue) {
      this.defaultValueDate = FieldChecker.checkDate(defaultValue);
      return this;
    }

    /**
     * Builds a {@link SortExpression} from the set values.
     *
     * @return a {@link SortExpression} built from the set values
     * @throws IllegalArgumentException if the field name or
     * default value is invalid
     */
    public SortExpression build() {
      return new SortExpression(this);
    }
  }

  // Mandatory
  private final SortDirection direction;
  private final String expression;

  // Optional
  // A default value is required for text sorting.
  private final String defaultValue;
  // A default value is required for numeric sorting.
  private final Double defaultValueNumeric;
  // A default value is required for date sorting.
  private final Date defaultValueDate;

  /**
   * Constructs a text sort specification using the values from the
   * {@link Builder}.
   */
  private SortExpression(Builder builder) {
    this.expression = builder.expression;
    this.direction = Util.defaultIfNull(builder.direction, SortDirection.DESCENDING);
    this.defaultValue = builder.defaultValue;
    this.defaultValueNumeric = builder.defaultValueNumeric;
    this.defaultValueDate = builder.defaultValueDate;
    checkValid();
  }

  /**
   * @return the expression to evaluate for each document and sort by
   */
  public String getExpression() {
    return expression;
  }

  /**
   * @return the direction to sort the search results in
   */
  public SortDirection getDirection() {
    return direction;
  }

  /**
   * @return the default value for the field. Can be null
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * @return the default numeric value for the field. Can be null
   */
  public Double getDefaultValueNumeric() {
    return defaultValueNumeric;
  }

  /**
   * @return the default date value for the field. Can be null
   */
  public Date getDefaultValueDate() {
    return defaultValueDate;
  }

  /**
   * Checks whether this sort specification is valid.
   *
   * @return this {@code SortExpression}
   * @throws IllegalArgumentException if the expression is null or invalid, or more than one default
   * value for the expression is specified or a default string value is too long.
   */
  private SortExpression checkValid() {
    Preconditions.checkNotNull(expression, "expression cannot be null");
    int defaultValueCount = 0;
    if (defaultValue != null) {
      defaultValueCount++;
    }
    if (defaultValueNumeric != null) {
      defaultValueCount++;
    }
    if (defaultValueDate != null) {
      defaultValueCount++;
    }
    Preconditions.checkArgument(defaultValueCount <= 1,
        "At most one default value can be specified for the SortExpression");
    if (defaultValue != null) {
      FieldChecker.checkText(defaultValue);
    }
    return this;
  }

  /**
   * Copies this sort specification object contents into a protocol buffer.
   *
   * @return the protocol buffer with the contents of the {@code sortSpec}
   */
  SearchServicePb.SortSpec copyToProtocolBuffer() {
    SearchServicePb.SortSpec.Builder builder = SearchServicePb.SortSpec.newBuilder();
    if (SortDirection.ASCENDING.equals(getDirection())) {
        builder.setSortDescending(false);
    }
    builder.setSortExpression(getExpression());
    if (getDefaultValue() != null) {
      builder.setDefaultValueText(getDefaultValue());
    }
    if (getDefaultValueNumeric() != null) {
      builder.setDefaultValueNumeric(getDefaultValueNumeric());
    }
    if (getDefaultValueDate() != null) {
      builder.setDefaultValueText(DateUtil.serializeDate(getDefaultValueDate()));
    }
    return builder.build();
  }

  /**
   * Creates and returns a SortExpression Builder.
   *
   * @return a new {@link SortExpression.Builder}. Set the parameters for the sort
   * specification on the Builder, and use the {@link Builder#build()} method
   * to create a concrete instance of SortExpression
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("SortExpression")
        .addField("direction", direction)
        .addField("expression", expression)
        .addField("defaultValue", defaultValue)
        .addField("defaultValueNumeric", defaultValueNumeric)
        .addField("defaultValueDate", defaultValueDate)
        .finish();
  }
}
