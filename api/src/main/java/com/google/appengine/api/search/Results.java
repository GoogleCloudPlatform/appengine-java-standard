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

import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a result of executing a search.
 * The Results include an {@link OperationResult}, a collection of
 * results, and a number of found and returned results.
 *
 * @param <T> The type of search result.
 */
public class Results<T> implements Iterable<T>, Serializable {
  private static final long serialVersionUID = -8342630524311776674L;

  private static final int MAX_RESULTS_TO_STRING = 10;
  private static final int MAX_FACET_RESULTS_TO_STRING = 10;

  private final OperationResult operationResult;
  private final Collection<T> results;
  private final Collection<FacetResult> facets;
  private final long numberFound;
  private final int numberReturned;
  private final Cursor cursor;

  /**
   * Creates a {@link Results} by specifying a collection of search
   * results, the number of results found, and the number of results
   * returned.
   *
   * @param operationResult the result of the search operation
   * @param results a collection of results that resulted from the search
   * @param numberFound the number of results found by the search
   * @param numberReturned the number of results returned
   * @param cursor the {@link Cursor} if there are more results and user
   * requested it
   */
  protected Results(OperationResult operationResult, Collection<T> results,
      long numberFound, int numberReturned, Cursor cursor) {
    this(operationResult, results, numberFound, numberReturned, cursor,
        Collections.<FacetResult>emptyList());
  }

  /**
   * Creates a {@link Results} by specifying a collection of search
   * results, the number of results found, and the number of results
   * returned.
   *
   * @param operationResult the result of the search operation
   * @param results a collection of results that resulted from the search
   * @param numberFound the number of results found by the search
   * @param numberReturned the number of results returned
   * @param cursor the {@link Cursor} if there are more results and user
   * requested it
   * @param facets aggregated facets of this search results as a collection of {@link FacetResult}
   * from the search
   */
  protected Results(OperationResult operationResult, Collection<T> results,
      long numberFound, int numberReturned, Cursor cursor, Collection<FacetResult> facets) {
    this.operationResult = Preconditions.checkNotNull(operationResult,
        "operation result cannot be null");
    this.results = Collections.unmodifiableCollection(
        Preconditions.checkNotNull(results, "search results cannot be null"));
    this.facets = Collections.unmodifiableCollection(
        Preconditions.checkNotNull(facets, "facets cannot be null"));
    this.numberFound = numberFound;
    this.numberReturned = numberReturned;
    this.cursor = cursor;
  }

  @Override
  public Iterator<T> iterator() {
    return results.iterator();
  }

  /**
   * @return the result of the search operation
   */
  public OperationResult getOperationResult() {
    return operationResult;
  }

  /**
   * The number of results found by the search.
   * If the value is less than or equal to the corresponding
   * {@link QueryOptions#getNumberFoundAccuracy()},
   * then it is accurate, otherwise it is an approximation
   *
   * @return the number of results found
   */
  public long getNumberFound() {
    return numberFound;
  }

  /**
   * @return the number of results returned
   */
  public int getNumberReturned() {
    return numberReturned;
  }

  /**
   * @return an unmodifiable collection of search results
   */
  public Collection<T> getResults() {
    return results;
  }

  /**
   * @return an unmodifiable collection of aggregated facets for this search results
   */
  public Collection<FacetResult> getFacets() {
    return facets;
  }

  /**
   * A cursor to be used to continue the search after all the results
   * in this search response. For this field to be populated,
   * use {@link QueryOptions.Builder#setCursor} with a value of
   * {@code Cursor.newBuilder().build()}, otherwise {@link #getCursor}
   * will return null.
   *
   * @return cursor to be used to get the next set of results after the
   * end of these results, or {@code null} if there are no more results
   * to be expected or if no cursor was configured in the {@code QueryOptions}.
   */
  public Cursor getCursor() {
    return cursor;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("Results")
        .addField("operationResult", operationResult)
        .addIterableField("results", results, MAX_RESULTS_TO_STRING)
        .addIterableField("facets", facets, MAX_FACET_RESULTS_TO_STRING)
        .addField("numberFound", numberFound)
        .addField("numberReturned", numberReturned)
        .addField("cursor", cursor)
        .finish();
  }
}
