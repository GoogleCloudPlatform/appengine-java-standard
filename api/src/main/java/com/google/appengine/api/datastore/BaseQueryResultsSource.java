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

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Concrete implementation of QueryResultsSource which knows how to make callbacks back into the
 * datastore to retrieve more entities for the specified cursor.
 *
 */
abstract class BaseQueryResultsSource<InitialResultT, NextRequestT, NextResultT>
    implements QueryResultsSource {

  /** A common interface for working with a query result. */
  interface WrappedQueryResult {
    /** Get the end cursor associated with the wrapped query result. */
    Cursor getEndCursor();

    /**
     * Get the entities included in the wrapped query result.
     *
     * @param projections Projections from the initial {@link Query}.
     */
    List<Entity> getEntities(Collection<Projection> projections);

    /**
     * A list of Cursor objections which correspond to the entities returned by {@link
     * #getEntities(Collection)}.
     */
    List<@Nullable Cursor> getResultCursors();

    /** Return the cursor just after the last result skipped due to an offset. */
    @Nullable
    Cursor getSkippedResultsCursor();

    /**
     * Indices whether a {@link BaseQueryResultsSource#makeNextCall(Object, WrappedQueryResult,
     * Integer, Integer)} call with the wrapped object will return more results.
     */
    boolean hasMoreResults();

    /** The number of results skipped over due to an offset. */
    int numSkippedResults();

    /**
     * Parses information about the indexes used in the query.
     *
     * @param monitoredIndexBuffer Indexes with the 'only use if required' flag set will be added to
     *     this buffer.
     * @return CompositeIndexes which were used in the query.
     */
    List<Index> getIndexInfo(Collection<Index> monitoredIndexBuffer);

    /**
     * Return false if the client has detected that this query result made no progress compared to
     * the previous result. Note that the v3 API doesn't require this kind of client-side logic.
     */
    boolean madeProgress(WrappedQueryResult previousResult);
  }

  /* @VisibleForTesting */
  static Logger logger = Logger.getLogger(BaseQueryResultsSource.class.getName());
  private static final int AT_LEAST_ONE = -1;
  private static final String DISABLE_CHUNK_SIZE_WARNING_SYS_PROP =
      "appengine.datastore.disableChunkSizeWarning";
  // Only log the warning when the user has returned more than this many
  // results with the default offset.
  private static final int CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD = 1000;
  // Only log the warning at most once every 5 minutes (per jvm) to avoid
  // spammy logs.
  private static final long MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS = 1000 * 60 * 5;
  /* @VisibleForTesting */
  static MonitoredIndexUsageTracker monitoredIndexUsageTracker = new MonitoredIndexUsageTracker();
  /* @VisibleForTesting */
  static final AtomicLong lastChunkSizeWarning = new AtomicLong(0);

  private final DatastoreCallbacks callbacks;
  private final int chunkSize;
  private final int offset;
  private final Transaction txn;
  private final Query query;
  private final CurrentTransactionProvider currentTransactionProvider;

  private Future<NextResultT> queryResultFuture = null;
  private int skippedResults;
  private int totalResults = 0;
  // Set by peekQueryResultAndIfFirstRecordIndexList from the first query result pb.
  private List<Index> indexList = null;
  private boolean addedSkippedCursor;
  private final Future<InitialResultT> initialQueryResultFuture;

  /**
   * Prototype for next/continue requests. This field remains null until initialQueryResultFuture is
   * processed.
   */
  private NextRequestT nextQueryPrototype = null;

  public BaseQueryResultsSource(
      DatastoreCallbacks callbacks,
      FetchOptions fetchOptions,
      final Transaction txn,
      Query query,
      Future<InitialResultT> initialQueryResultFuture) {
    this.callbacks = callbacks;
    this.chunkSize =
        fetchOptions.getChunkSize() != null ? fetchOptions.getChunkSize() : AT_LEAST_ONE;
    this.offset = fetchOptions.getOffset() != null ? fetchOptions.getOffset() : 0;
    this.txn = txn;
    this.query = query;
    this.currentTransactionProvider =
        new CurrentTransactionProvider() {
          @Override
          public Transaction getCurrentTransaction(Transaction defaultValue) {
            return txn;
          }
        };
    this.initialQueryResultFuture = initialQueryResultFuture;
    this.skippedResults = 0;
  }

  // Methods to work with service specific code for constructing and sending queries.

  /** Wrap an initial query result and provide a standard interface for data extraction. */
  abstract WrappedQueryResult wrapInitialResult(InitialResultT res);

  /** Wrap a continue query request and provide a standard interface for data extraction. */
  abstract WrappedQueryResult wrapResult(NextResultT res);

  /**
   * Construct base object for continuing queries if more results are needed. This object will
   * passed into {@link #makeNextCall(Object, WrappedQueryResult, Integer, Integer)}.
   */
  abstract NextRequestT buildNextCallPrototype(InitialResultT res);

  /**
   * Issue a continue query request to the {@link AsyncDatastoreService}.
   *
   * @return The future containing the result.
   */
  abstract Future<NextResultT> makeNextCall(
      NextRequestT prototype,
      WrappedQueryResult latestResult,
      Integer fetchCountOrNull,
      Integer offsetOrNull);

  @Override
  public boolean hasMoreEntities() {
    return (nextQueryPrototype == null || queryResultFuture != null);
  }

  @Override
  public int getNumSkipped() {
    return skippedResults;
  }

  @Override
  public List<Index> getIndexList() {
    if (indexList == null) {
      // The first query result contains the index list.
      InitialResultT res = FutureHelper.quietGet(initialQueryResultFuture);
      Set<Index> monitoredIndexBuffer = Sets.newHashSet();
      indexList = wrapInitialResult(res).getIndexInfo(monitoredIndexBuffer);
      if (!monitoredIndexBuffer.isEmpty()) {
        monitoredIndexUsageTracker.addNewUsage(monitoredIndexBuffer, query);
      }
    }
    return indexList;
  }

  @Override
  public Cursor loadMoreEntities(List<Entity> buffer, List<Cursor> cursorBuffer) {
    return loadMoreEntities(AT_LEAST_ONE, buffer, cursorBuffer);
  }

  @Override
  public Cursor loadMoreEntities(int numberToLoad, List<Entity> buffer, List<Cursor> cursorBuffer) {
    TransactionImpl.ensureTxnActive(txn);
    if (!hasMoreEntities()) {
      return null;
    }

    // We possibly have more results.

    if (numberToLoad == 0 // special request to satisfy the offset
        && offset <= skippedResults) { // which may already be satisfied
      if (!addedSkippedCursor) {
        cursorBuffer.add(null); // Use the start cursor.
        addedSkippedCursor = true;
      }
      // Offset has already been satisfied.
      return null;
    }

    WrappedQueryResult res;
    if (nextQueryPrototype == null) {
      getIndexList(); // Maybe fill in index data.
      InitialResultT initialRes = FutureHelper.quietGet(initialQueryResultFuture);
      nextQueryPrototype = buildNextCallPrototype(initialRes);
      res = wrapInitialResult(initialRes);
    } else {
      res = wrapResult(FutureHelper.quietGet(queryResultFuture));
      queryResultFuture = null;
    }

    int fetchedSoFar = processQueryResult(res, buffer, cursorBuffer);

    Integer fetchCountOrNull = null;
    Integer offsetOrNull = null;
    if (res.hasMoreResults()) {
      boolean setCount = true; // if the count needs to be tracked
      if (numberToLoad <= 0) {
        setCount = false;
        if (chunkSize != AT_LEAST_ONE) {
          fetchCountOrNull = chunkSize;
        }
        if (numberToLoad == AT_LEAST_ONE) {
          numberToLoad = 1;
        }
      }

      // Synchronously grab any extra needed
      while (res.hasMoreResults() // While there are more results
          && (skippedResults < offset // and we either have more offset to satisfy
              || fetchedSoFar < numberToLoad)) { // or have more results to fetch
        if (skippedResults < offset) {
          offsetOrNull = offset - skippedResults;
        } else {
          offsetOrNull = null;
        }
        if (setCount) {
          fetchCountOrNull = Math.max(chunkSize, numberToLoad - fetchedSoFar);
        }
        WrappedQueryResult nextRes =
            wrapResult(
                FutureHelper.quietGet(
                    makeNextCall(nextQueryPrototype, res, fetchCountOrNull, offsetOrNull)));
        if (!nextRes.madeProgress(res)) {
          throw new DatastoreTimeoutException("The query was not able to make any progress.");
        }
        res = nextRes;
        fetchedSoFar += processQueryResult(res, buffer, cursorBuffer);
      }
    }

    // Start next async call iff we have more results to get.
    if (res.hasMoreResults()) {
      fetchCountOrNull = chunkSize != AT_LEAST_ONE ? chunkSize : null;
      offsetOrNull = null; // At this point the offset must have been met.
      queryResultFuture = makeNextCall(nextQueryPrototype, res, fetchCountOrNull, offsetOrNull);
    }
    return res.getEndCursor();
  }

  /**
   * Helper function to process the query results.
   *
   * <p>This function adds results to the given buffer and updates {@link #skippedResults}.
   *
   * @param res The {@link com.google.apphosting.datastore.proto2api.DatastoreV3Pb.QueryResult} to process
   * @param buffer the buffer to which to add results
   * @return The number of new results added to buffer.
   */
  private int processQueryResult(
      WrappedQueryResult res, List<Entity> buffer, List<Cursor> cursorBuffer) {
    skippedResults += res.numSkippedResults();
    if (skippedResults >= offset && !addedSkippedCursor) {
      // Use the skip cursor if provided, otherwise use the start cursor or Cursor.advance().
      cursorBuffer.add(res.getSkippedResultsCursor());
      addedSkippedCursor = true;
    }

    List<Entity> entityList = res.getEntities(query.getProjections());
    buffer.addAll(entityList);
    cursorBuffer.addAll(res.getResultCursors());
    for (Entity entity : entityList) {
      // Since results are streamed back over time we just create one context
      // per result and execute postLoad callbacks within that context.
      callbacks.executePostLoadCallbacks(new PostLoadContext(currentTransactionProvider, entity));
    }
    totalResults += entityList.size();
    // If the user has pulled back a large number of results without setting
    // a chunk size, log a warning.
    if (chunkSize == AT_LEAST_ONE
        && totalResults > CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD
        && System.getProperty(DISABLE_CHUNK_SIZE_WARNING_SYS_PROP) == null) {
      logChunkSizeWarning();
    }
    return entityList.size();
  }

  void logChunkSizeWarning() {
    long now = System.currentTimeMillis();
    if ((now - lastChunkSizeWarning.get()) < MAX_CHUNK_SIZE_WARNING_FREQUENCY_MS) {
      return;
    }
    logger.warning(
        "This query does not have a chunk size set in FetchOptions and has returned over "
            + CHUNK_SIZE_WARNING_RESULT_SET_SIZE_THRESHOLD
            + " results.  If result sets of this "
            + "size are common for this query, consider setting a chunk size to improve "
            + "performance.\n  To disable this warning set the following system property in "
            + "appengine-web.xml (the value of the property doesn't matter): '"
            + DISABLE_CHUNK_SIZE_WARNING_SYS_PROP
            + "'");
    lastChunkSizeWarning.set(now);
  }
}
