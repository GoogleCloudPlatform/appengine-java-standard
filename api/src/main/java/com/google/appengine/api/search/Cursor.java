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

import com.google.appengine.api.search.checkers.CursorChecker;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a cursor on the set of results found for executing a {@link Query}
 * during a search on the {@link Index}.
 * <p>
 * For example, the following code shows how to use a cursor to get the
 * next page of results
 * <p>
 * <pre>{@code
 * Index index = ...
 * Cursor cursor = Cursor.newBuilder().build();
 * Query query = Query.newBuilder().setOptions(
 *     QueryOptions.newBuilder().setCursor(cursor).build("some query"));
 *
 * // Get the first page of results
 * Results<ScoredDocument> results = index.search(query);
 *
 * // process results
 * ...
 *
 * // Get the next set of results from the returned cursor
 * query = Query.newBuilder().setOptions(
 *     QueryOptions.newBuilder().setCursor(
 *         results.getCursor()).build("some query"));
 *
 * results = index.search(query);
 * }</pre>
 * <p>
 * Alternatively, you can get a cursor to continue from each of the returned
 * results.
 * <pre>{@code
 * Cursor cursor =
 *     Cursor.newBuilder().setPerResult(true).build();
 * Query query = Query.newBuilder().setOptions(
 *     QueryOptions.newBuilder().setCursor(cursor).build("some query"));
 *
 * // Get the first page of results
 * Results<ScoredDocument> results = index.search(query);
 *
 * // process results
 * for (ScoredDocument result : results) {
 *   // choose a cursor from one of the results
 *   cursor = result.getCursor();
 * }
 *
 * // Get the next set of results from the result's cursor
 * query = Query.newBuilder().setOptions(
 *     QueryOptions.newBuilder().setCursor(cursor).build("some query"));
 *
 * results = index.search(query);
 * }</pre>
 */
public final class Cursor implements Serializable {
  private static final long serialVersionUID = 5983232584697220044L;

  /**
   * A builder which constructs Cursor objects.
   */
  public static final class Builder {
    // Optional
    @Nullable private String webSafeString;
    private boolean perResult;

    private Builder() {
    }

    /**
     * Constructs a {@link Cursor} builder with the given request.
     *
     * @param request the search request to populate the builder
     */
    private Builder(Cursor request) {
      setFromWebSafeString(request.toWebSafeString());
    }

    /**
     * Sets whether or not to return a Cursor on each individual result
     * in {@link Results} or a single cursor with all
     * Results.
     *
     * @param perResult if True, then return a Cursor with each
     * {@link ScoredDocument} result, otherwise return a single Cursor
     * with {@link Results}
     * @return this Builder
     */
    public Builder setPerResult(boolean perResult) {
      this.perResult = perResult;
      return this;
    }

    /**
     * Construct the final message.
     *
     * @param webSafeString use a cursor returned from a
     * previous set of search results as a starting point to retrieve
     * the next set of results. This can get you better performance, and
     * also improves the consistency of pagination through index updates
     * @return the Cursor built from the parameters entered on this
     * Builder
     * @throws IllegalArgumentException if the cursor string is invalid
     */
    public Cursor build(String webSafeString) {
      setFromWebSafeString(webSafeString);
      return new Cursor(this);
    }

    /**
     * @param webSafeString a string from some other
     * {@link Cursor#toWebSafeString}
     */
    private void setFromWebSafeString(String webSafeString) {
      CursorChecker.checkCursor(webSafeString);
      int colon = webSafeString.indexOf(":");
      Preconditions.checkArgument(colon > 0 && colon < webSafeString.length(),
          "Invalid format for cursor string");
      String booleanString = webSafeString.substring(0, colon);
      Preconditions.checkArgument("true".equals(booleanString) || "false".equals(booleanString),
          "Invalid format of webSafeString");
      this.perResult = Boolean.parseBoolean(booleanString);
      this.webSafeString = webSafeString.substring(colon + 1);
    }

    /**
     * Construct the final message.
     *
     * @return the Cursor built from the parameters entered on this
     * Builder
     */
    public Cursor build() {
      return new Cursor(this);
    }
  }

  // The following are set to default values if none is supplied by the builder.

  // Optional
  @Nullable private final String webSafeString;
  private final boolean perResult;

  /**
   * Creates a search request from the builder.
   *
   * @param builder the search request builder to populate with
   */
  private Cursor(Builder builder) {
    webSafeString = builder.webSafeString;
    perResult = builder.perResult;
    checkValid();
  }

  /**
   * A web safe string representing a cursor returned from a previous set of
   * search results to use as a starting point to retrieve the next
   * set of results. Can be null.
   *
   * @return a web safe string representation of the cursor
   */
  public String toWebSafeString() {
    if (webSafeString == null) {
      return null;
    }
    return perResult + ":" + webSafeString;
  }

  /**
   * @return whether the cursor is for all Results or one cursor per result
   */
  public boolean isPerResult() {
    return perResult;
  }

  /**
   * Creates and returns a {@link Cursor} builder. Set the search request
   * parameters and use the {@link Builder#build()} method to create a concrete
   * instance of Cursor.
   *
   * @return a {@link Builder} which can construct a search request
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a builder from the given request.
   *
   * @param request the search request for the builder to use
   * to build another request
   * @return this builder
   */
  public static Builder newBuilder(Cursor request) {
    return new Builder(request);
  }

  /**
   * Checks the search specification is valid, specifically, has
   * a non-null number of documents to return specification, a valid
   * cursor if present, valid sort specification list, a valid
   * collection of field names for sorting.
   *
   * @return this checked Cursor
   * @throws IllegalArgumentException if some part of the specification is
   * invalid
   */
  private Cursor checkValid() {
    CursorChecker.checkCursor(webSafeString);
    return this;
  }

  /**
   * Copies the contents of this {@link Cursor} object into a
   * {@link SearchParams} protocol buffer builder.
   *
   * @return a search params protocol buffer builder initialized with
   * the values from this request
   * @throws IllegalArgumentException if the cursor type is
   * unknown
   */
  SearchParams.Builder copyToProtocolBuffer(SearchParams.Builder builder) {
    if (webSafeString != null) {
       builder.setCursor(webSafeString);
    }
    if (perResult) {
        builder.setCursorType(SearchParams.CursorType.PER_RESULT);
    } else {
        builder.setCursorType(SearchParams.CursorType.SINGLE);
    }
    return builder;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("Cursor")
        .addField("webSafeString", toWebSafeString())
        .finish();
  }
}
