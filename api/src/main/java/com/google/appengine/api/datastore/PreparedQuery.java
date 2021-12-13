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

package com.google.appengine.api.datastore;

import java.util.Iterator;
import java.util.List;

/**
 * Contains methods for fetching and returning entities from a {@link Query}. If the {@link Query}
 * specified a sort order, {@link Entity Entities} are returned in that order. Otherwise, the order
 * is undefined.
 *
 * <p>A {@link PreparedQuery} does not cache results. Each use of {@link PreparedQuery} results in a
 * new trip to the datastore.
 */
public interface PreparedQuery {
  /**
   * Retrieves the {@link Query} {@link Entity Entities} as a {@link List} using the provided {@link
   * FetchOptions}.
   *
   * <p>Note that if {@link FetchOptions#getLimit()} is greater than the number of {@link Entity
   * Entities}, the length of the returned {@link List} will be smaller than{@link
   * FetchOptions#getLimit()}.
   *
   * <p>To operate on large result sets, you should prefer {@link #asIterable} and {@link
   * #asIterator}, which stream the results from the datastore.
   *
   * @param fetchOptions The fetch options to apply.
   * @return The result of the PreparedQuery, represented as a {@link List}.
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   * @see FetchOptions
   */
  List<Entity> asList(FetchOptions fetchOptions);

  /** Similar to {@link #asList} except a {@link QueryResultIterator} is returned. */
  QueryResultList<Entity> asQueryResultList(FetchOptions fetchOptions);

  /**
   * Retrieves the {@link Query} {@link Entity Entities} as an {@link Iterable} using the provided
   * {@link FetchOptions}.
   *
   * <p>Each use of {@link Iterable#iterator} results in an entirely new and independent {@link
   * Iterator}.
   *
   * @param fetchOptions The fetch options to apply.
   * @return The result of the PreparedQuery, represented as an {@link Iterable}.
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   * @see FetchOptions
   */
  Iterable<Entity> asIterable(FetchOptions fetchOptions);

  /** Equivalent to {@link #asIterable(FetchOptions)} but uses default {@link FetchOptions}. */
  Iterable<Entity> asIterable();

  /**
   * Similar to {@link #asIterable(FetchOptions)} except a {@link QueryResultIterable} is returned.
   * Call this method to have (indirect) access to {@link Cursor}s for your result set.
   */
  QueryResultIterable<Entity> asQueryResultIterable(FetchOptions fetchOptions);

  /**
   * Similar to {@link #asIterable()} except a {@link QueryResultIterable} is returned. Call this
   * method to have (indirect) access to {@link Cursor}s for your result set.
   */
  QueryResultIterable<Entity> asQueryResultIterable();

  /**
   * Retrieves the {@link Query} {@link Entity Entities} as an {@link Iterator} using the provided
   * {@link FetchOptions}.
   *
   * @param fetchOptions The fetch strategy to apply.
   * @return The result of the PreparedQuery, represented as an {@link Iterator}.
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   * @see FetchOptions
   */
  Iterator<Entity> asIterator(FetchOptions fetchOptions);

  /** Equivalent to {@link #asIterator(FetchOptions)} but uses default {@link FetchOptions}. */
  Iterator<Entity> asIterator();

  /**
   * Similar to {@link #asIterator(FetchOptions)} except a {@link QueryResultIterator} is returned.
   * Call this method to have access to {@link Cursor}s for your result set.
   */
  QueryResultIterator<Entity> asQueryResultIterator(FetchOptions fetchOptions);

  /**
   * Similar to {@link #asIterator()} except a {@link QueryResultIterator} is returned. Call this
   * method to have access to {@link Cursor}s for your result set.
   */
  QueryResultIterator<Entity> asQueryResultIterator();

  /**
   * Retrieves the one and only result for the {@link Query}.
   *
   * @throws TooManyResultsException if more than one result is returned from the {@link Query}.
   * @return the single, matching result, or {@code null} if no entities match
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   */
  Entity asSingleEntity() throws TooManyResultsException;

  /**
   * Retrieves the number of {@link Entity Entities} that currently match this {@link Query}.
   *
   * @return a count {@literal >= 0}
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   */
  int countEntities(FetchOptions fetchOptions);

  /**
   * Retrieves the number of {@link Entity Entities} that currently match this {@link Query}.
   *
   * @return a count {@literal >= 0}
   * @throws IllegalStateException If the query being executed is associated with a {@link
   *     Transaction} that is not active.
   * @deprecated Use {@link #countEntities(FetchOptions)} instead. Calling this function imposes a
   *     maximum result limit of 1000.
   */
  @Deprecated
  int countEntities();

  /** Indicates that too many results were found for {@link PreparedQuery#asSingleEntity}. */
  class TooManyResultsException extends RuntimeException {
    private static final long serialVersionUID = 7466953877290278464L;
  }
}
