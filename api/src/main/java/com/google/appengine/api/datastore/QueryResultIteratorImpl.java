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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link QueryResultIteratorImpl} returns zero or more {@link Entity} objects that are the result
 * of a {@link Query} that has been executed.
 *
 * <p>{@link Entity} objects can be retrieved one-at-a-time using the standard {@link
 * QueryResultIterator} interface. In addition, we extend this interface to allow access to batches
 * of {@link Entity} objects by calling {@link #nextList}.
 *
 * <p>If the {@link Query} is keys only, i.e. {@link Query#isKeysOnly()} returns true, then the
 * {@link Entity} objects returned by this iterator will only have their key populated. They won't
 * have properties or other data.
 *
 * <p>Note: this class is not thread-safe.
 *
 */
class QueryResultIteratorImpl implements QueryResultIterator<Entity> {
  private final PreparedQuery query;
  private final QueryResultsSource resultsSource;
  private final LinkedList<Entity> entityBuffer;
  // Contains entityBuffer.size() + 1 Cursors, any of which may be null, indicating we need to fall
  // back on start/end cursors or Cursor.advance().
  private final LinkedList<Cursor> entityCursorBuffer;
  private final Transaction txn;
  private Cursor lastCursor = null;
  private Cursor nextCursor;
  private int resultsSinceLastCursor = 0;

  /**
   * Create a QueryIterator that wraps around the specified Cursor. Elements will be retrieved in
   * batches {@code minRequestSize}.
   */
  QueryResultIteratorImpl(
      PreparedQuery query,
      QueryResultsSource resultsSource,
      FetchOptions fetchOptions,
      Transaction txn) {
    this.query = query;
    this.resultsSource = resultsSource;
    this.entityBuffer = new LinkedList<Entity>();
    this.entityCursorBuffer = new LinkedList<Cursor>();
    this.txn = txn;

    if (fetchOptions.getCompile() == Boolean.TRUE) {
      // Create new cursor that points to the position in the query
      // before the offset is applied
      nextCursor =
          fetchOptions.getStartCursor() == null
              ? new Cursor()
              : new Cursor(fetchOptions.getStartCursor());

      // Cursor does not include offset, so we must include it here.
      if (fetchOptions.getOffset() != null) {
        resultsSinceLastCursor = fetchOptions.getOffset();
      }
    }
  }

  @Override
  public boolean hasNext() {
    return ensureLoaded();
  }

  @Override
  public Entity next() {
    TransactionImpl.ensureTxnActive(txn);
    if (ensureLoaded()) {
      ++resultsSinceLastCursor;
      entityCursorBuffer.removeFirst();
      return entityBuffer.removeFirst();
    } else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public List<Index> getIndexList() {
    return resultsSource.getIndexList();
  }

  @Override
  public Cursor getCursor() {
    ensureInitialized(); // ensuring we have have pulled at least the first cursor
    Cursor cursor = null;
    if (!entityCursorBuffer.isEmpty() && entityCursorBuffer.getFirst() != null) {
      // Return the per result cursor.
      cursor = entityCursorBuffer.getFirst();
    } else if (entityBuffer.isEmpty()) {
      // perfect! we don't have to use an internal offset, just have to grab the
      // latest cursor (which may be null)
      cursor = nextCursor;
    } else if (lastCursor != null) {
      // The cursor points to a spot further back in the result set than the
      // user is expecting so we will have to manually advance the cursor to the
      // correct position.
      lastCursor = lastCursor.advance(resultsSinceLastCursor, query);
      resultsSinceLastCursor = 0;
      cursor = lastCursor;
    }

    // Returning a cursor to the user
    if (cursor != null) {
      // Returning a copy of our cursor to the user so they can't change our
      // internal cursor
      return new Cursor(cursor);
    } else {
      return null; // no cursor to return
    }
  }

  /**
   * Returns a {@code List} of up to {@code maximumElements} elements. If there are fewer than this
   * many entities left to be retrieved, the {@code List} returned will simply contain less than
   * {@code maximumElements} elements. In particular, calling this when there are no elements
   * remaining is not an error, it simply returns an empty list.
   */
  public List<Entity> nextList(int maximumElements) {
    TransactionImpl.ensureTxnActive(txn);
    ensureLoaded(maximumElements);

    int numberToReturn = Math.min(maximumElements, entityBuffer.size());
    List<Entity> backingList = entityBuffer.subList(0, numberToReturn);
    List<Entity> returnList = new ArrayList<Entity>(backingList);
    backingList.clear();
    entityCursorBuffer.subList(0, numberToReturn).clear();
    resultsSinceLastCursor += returnList.size();
    return returnList;
  }

  private void saveNextCursor(int bufferSize, Cursor next) {
    if (next != null) {
      // lastCursor is null for the first request. In this case the buffer is empty and
      // the offset has been processed (and already pre-populated in resultsSinceLastCursor).
      // TODO: Refactor the handling of offset and simplify this code.
      if (lastCursor != null) {
        // NOTE: Since we just threw out the last cursor and replaced
        // it with the current next cursor all the results in the buffer came
        // before the new last cursor so we must indicate this in the
        // resultsSinceLastCursor variable. If a cursor is pulled off while
        // this value is negative Cursor.advance() will throw an exception.
        // This should never happen as this function will only be called
        // when at least entityBuffer.size() + 1 are about to be pulled off the
        // buffer.
        resultsSinceLastCursor = -bufferSize;
      }
      // Save the current cursor location
      lastCursor = nextCursor;
      nextCursor = next;
    }
  }

  private void ensureInitialized() {
    saveNextCursor(
        entityBuffer.size(), resultsSource.loadMoreEntities(0, entityBuffer, entityCursorBuffer));
  }

  /**
   * Request additional {@code Entity} objects from the current cursor so that there is at least one
   * pending object.
   */
  private boolean ensureLoaded() {
    if (entityBuffer.isEmpty() && resultsSource.hasMoreEntities()) {
      saveNextCursor(
          entityBuffer.size(), resultsSource.loadMoreEntities(entityBuffer, entityCursorBuffer));
    }
    return !entityBuffer.isEmpty();
  }

  /**
   * Request enough additional {@code Entity} objects from the current results source that there are
   * {@code numberDesired} pending objects.
   */
  private boolean ensureLoaded(int numberDesired) {
    int numberToLoad = numberDesired - entityBuffer.size();
    if (numberToLoad > 0 && resultsSource.hasMoreEntities()) {
      saveNextCursor(
          entityBuffer.size(),
          resultsSource.loadMoreEntities(numberToLoad, entityBuffer, entityCursorBuffer));
    }
    return entityBuffer.size() >= numberDesired;
  }

  /**
   * The optional remove method is not supported.
   *
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void remove() {
    // TODO: seems like we could support this...
    throw new UnsupportedOperationException();
  }

  public int getNumSkipped() {
    ensureInitialized(); // ensuring we have attempted to satisfy the offset
    return resultsSource.getNumSkipped();
  }
}
