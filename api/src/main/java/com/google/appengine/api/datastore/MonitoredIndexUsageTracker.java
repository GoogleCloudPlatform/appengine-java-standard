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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy.OverQuotaException;
import com.google.apphosting.api.DeadlineExceededException;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * This class is used to log usages of indexes that have been selected for usage monitoring by the
 * application administrator.
 *
 */
/* @ThreadSafe */
class MonitoredIndexUsageTracker {
  /*
   * There are several goals for the MonitoredIndexUsageTracker class logging infrastructure:
   * (a) Guarantee that every monitored index that is used has at least one usage logged for it.
   *     This is because indexes may be monitored for automatic deletion and if no usage is logged
   *     for an index we may delete it even though it's required by the application.
   * (b) Avoid slowing down the application and flooding the datastore with logging
   *     requests that are similar but not equal.
   * (c) Handle indexes that have monitoring dynamically enabled and disabled.
   * (d) Provide enough information per usage so that clients can identify the code in their
   *     application that is resulting in the unexpected index usage.
   *
   * To achieve these goals the following design decisions were made:
   * (a) In each usage entry we store the monitored index id, the client stack trace,
   *     the query that caused the monitored index to be used, the time at which the usage
   *     occurred, and the application version id. If the application administrator modifies their
   *     application query usage and set of available indexes they should be able to associate
   *     those events by occurrence time with the usage entries.
   * (b) Each usage entry is stored on a per composite index basis. Therefore index usages from one
   *     composite index do not evict index usages from another composite index.
   * (c) The datastore is queried for successfully recorded usage events for an index
   *     the first time the application clone sees a monitored usage for that index and also
   *     on subsequent monitored usages every REFRESH_PERIOD_SECS. To minimize overhead and
   *     latency, the query performed is a keys only query and has a limit of
   *     MAX_TRACKED_USAGES_PER_INDEX keys. Having a small REFRESH_PERIOD_SECS allows application
   *     owners to quickly see the effect of their changes. Note that every time the application
   *     owner re-enables query monitoring for an index existing usages for that index
   *     are cleared from the datastore.
   * (d) If a new monitored usage event occurs and its key is in the in memory PersistedUsageIds
   *     set or if the PersistedUsageIds set is larger than MAX_TRACKED_USAGES_PER_INDEX then the
   *     new monitored usage event is ignored.
   * (e) Otherwise the new monitored usage event is added to the PersistedUsageIds set and
   *     persisted to the datastore.
   * (f) If a put fails to be persisted the usage tracking set will again become accurate
   *     when a new monitored usage event occurs after REFRESH_PERIOD_SECS.
   * (g) Deadlines have been set per RPC to avoid stalling user requests beyond a certain time
   *     limit.
   *
   * Performance:
   *
   * With this algorithm if a monitored index is heavily used expensive put operations are
   * avoided and instead fast, low cost, keys-only usage refresh queries are performed
   * at a periodic rate.
   *
   * There will be at max one usage refresh query per heavily used monitored index every
   * REFRESH_PERIOD_SECS. With a bit more code the app server could have filtered the
   * set of monitored indexes returned to the clone by maintaining its own cache of persisted
   * usages and only forwarding the onlyUseIfRequired property if the usage limit had not
   * been hit. The app server already has a per minute cache of all the indexes - so we could have
   * extended that logic. However, it is assumed that in general there will only be a couple
   * (multi-threaded) application clones per app server. So the savings would not be significant.
   * If real world testing proves otherwise we should revisit this.
   *
   * Known limitations:
   *
   * 1. The different API languages may have different hashing algorithms for the UsageEvent key.
   *    Since we don't standardize the hashing algorithm different usages for the same query per
   *    index could trample over each other or be duplicated. This is a known
   *    issue that we don't think is worth fixing at this time. The important thing is to have
   *    a few usages (not every usage) and the probability of collision is low.
   *
   * 2. Due to timing and eventual consistency there may be more than MAX_TRACKED_USAGES_PER_INDEX
   *    usages stored for a single monitored index. This window only exists for a small
   *    duration and most likely there are a set of common hot usages so the number of stored
   *    usages shouldn't be much larger than MAX_TRACKED_USAGES_PER_INDEX.
   *
   * 3. If the application is encountering repeated errors while writing (e.g. write quota limit
   *    hit) or very infrequently uses a monitored index and those usages correspond with
   *    intermittent failures (e.g. deadline exceeded) we will not be able to guarantee that a
   *    usage is logged for every required monitored index. This limitation should be documented
   *    when we implement automatic index deletion.
   */

  private static final int DEFAULT_USAGE_REFRESH_PERIOD_SECS = 60; // 1 minute
  private static final double DEFAULT_REFRESH_QUERY_DEADLINE_SECS = 0.2;
  private static final double DEFAULT_PUT_DEADLINE_SECS = 0.2;
  private static final String[] DEFAULT_API_PACKAGE_PREFIXES = {
    "com.google.appengine", "com.google.apphosting", "org.datanucleus"
  };
  /* @VisibleForTesting */
  static final String REFRESH_PERIOD_SECS_SYS_PROP =
      "appengine.datastore.indexMonitoring.persistedUsageRefreshPeriodSecs";
  private static final String REFRESH_QUERY_DEADLINE_SECS_SYS_PROP =
      "appengine.datastore.indexMonitoring.refreshUsageInfoQueryDeadlineSecs";
  private static final String PUT_DEADLINE_SECS_SYS_PROP =
      "appengine.datastore.indexMonitoring.newUsagePutDeadlineSecs";
  private static final String PACKAGE_PREFIXES_TO_SKIP_SYS_PROP =
      "appengine.datastore.apiPackagePrefixes";
  private static final String NEW_USAGE_LOGGING_THRESHOLD_SECS_SYS_PROP =
      "appengine.datastore.indexMonitoring.newUsageLoggingThresholdSecs";

  /* @VisibleForTesting */
  static final int REFRESH_QUERY_FAILURE_LOGGING_THRESHOLD = 10;
  private static final int MAX_MONITORED_INDEXES = 100;
  private static final int MAX_TRACKED_USAGES_PER_INDEX = 30;
  private static final int MAX_STACK_FRAMES_SAVED = 200;

  private static final String USAGE_ENTITY_KIND_PREFIX = "_ah_datastore_monitored_index_";
  private static final String USAGE_ENTITY_QUERY_PROPERTY = "query";
  private static final String USAGE_ENTITY_CAPTURE_TIME_PROPERTY = "diagnosticCaptureDurationNanos";
  private static final String USAGE_ENTITY_OCCURRENCE_TIME_PROPERTY = "occurrenceTime";
  private static final String USAGE_ENTITY_STACK_TRACE_PROPERTY = "stackTrace";
  private static final String USAGE_ENTITY_APP_VERSION_PROPERTY = "appVersion";

  /* @VisibleForTesting */
  static Logger logger = Logger.getLogger(MonitoredIndexUsageTracker.class.getName());
  private final int maxUsagesTrackedPerIndex;
  private final UsageIdCache perIndexUsageIds;
  private final Ticker ticker;
  private final long usageRefreshPeriodNanos;
  private final double refreshQueryDeadlineSecs;
  private final double putDeadlineSecs;
  private final long newUsageLoggingThresholdNanos;
  private final PrefixTrie<Boolean> apiPackagePrefixTrie;

  MonitoredIndexUsageTracker() {
    this(MAX_MONITORED_INDEXES, MAX_TRACKED_USAGES_PER_INDEX, Ticker.systemTicker());
  }

  /* @VisibleForTesting */
  MonitoredIndexUsageTracker(int maxIndexesTracked, int maxUsagesPerIndex, Ticker ticker) {
    this.maxUsagesTrackedPerIndex = maxUsagesPerIndex;
    this.ticker = ticker;
    usageRefreshPeriodNanos = getUsageRefreshPeriodNanos();
    refreshQueryDeadlineSecs = getRefreshQueryDeadlineSecs();
    putDeadlineSecs = getPutDeadlineSecs();
    newUsageLoggingThresholdNanos = getNewUsageLoggingThresholdNanos();
    perIndexUsageIds = new UsageIdCache(maxIndexesTracked);
    apiPackagePrefixTrie = getApiPackagePrefixesTrie();
  }

  /** Log that the specified {@code monitoredIndexes} were used to execute the {@code query}. */
  public void addNewUsage(Collection<Index> monitoredIndexes, Query query) {
    Preconditions.checkNotNull(monitoredIndexes);
    Preconditions.checkNotNull(query);

    // Create a single date timestamp for all usage entities logged for this invocation
    long methodInvocationTimeNanos = ticker.read();
    Date occurenceDate = newDate();
    LazyApiInvokerStackTrace lazyStackTrace = new LazyApiInvokerStackTrace();

    // First refresh any expired ExpiringPersistedUsageIds. Fetching the ExpiringPersistedUsageIds
    // from the cache implicitly issues a query refresh if it's needed.
    // Note that it is important to cache the ExpiringPersistedUsageIds because otherwise
    // the entries could expire after our usageIdCache.get() and then we could end up issuing
    // the refresh queries serially vs. in parallel.
    List<ExpiringPersistedUsageIds> usageIdsPerIndex =
        Lists.newArrayListWithExpectedSize(monitoredIndexes.size());
    for (Index index : monitoredIndexes) {
      usageIdsPerIndex.add(perIndexUsageIds.get(index.getId()));
    }

    // Then fetch the results from each of the refresh queries and see if we should record
    // a usage entity. The fetches may block if the current thread was the thread that issued
    // the refresh query.
    List<Entity> newUsagesToPersist = Lists.newArrayList();
    Iterator<ExpiringPersistedUsageIds> usageIdsPerIndexIter = usageIdsPerIndex.iterator();
    for (Index index : monitoredIndexes) {
      PersistedUsageIds persistedUsageIds = usageIdsPerIndexIter.next().get();
      if (persistedUsageIds.addNewUsage(getUsageEntityKeyName(query))) {
        newUsagesToPersist.add(newUsageEntity(index, query, occurenceDate, lazyStackTrace));
      }
    }

    if (!newUsagesToPersist.isEmpty()) {
      // Attempt to write the usage events. If the usages fail to write it's not critical
      // because after the refreshPeriodMs the datastore will be queried to see which
      // usage events were actually persisted. If the events occur again we'll detect that they
      // weren't persisted and have another chance to persist them. For more details about this
      // see the "Known limitations" comment at the start of the file.
      persistNewUsages(newUsagesToPersist);
    }

    long methodElapsedTimeNanos = ticker.read() - methodInvocationTimeNanos;
    if (methodElapsedTimeNanos > newUsageLoggingThresholdNanos) {
      long elapsedTimeSecs = methodElapsedTimeNanos / (1000 * 1000 * 1000);
      long elapsedTimeRemNanos = methodElapsedTimeNanos % (1000 * 1000 * 1000);
      logger.severe(
          String.format(
              "WARNING: tracking usage of monitored indexes took %d.%09d secs",
              elapsedTimeSecs, elapsedTimeRemNanos));
    }
  }

  /* @VisibleForTesting */
  void persistNewUsages(List<Entity> newUsagesToPersist) {
    AsyncDatastoreService asyncDatastore = newAsyncDatastoreService(putDeadlineSecs);
    try {
      // Do an asynchronous batch put of the new monitored index usages.
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError = asyncDatastore.put((Transaction) null, newUsagesToPersist);
    } catch (RuntimeException e) {
      logger.log(
          Level.SEVERE,
          String.format(
              "Failed to record monitored index usage: %s", newUsagesToPersist.get(0).toString()),
          e);
    }
  }

  static class QueryAndFetchOptions {
    final Query query;
    final FetchOptions fetchOptions;

    QueryAndFetchOptions(Query query, FetchOptions fetchOptions) {
      this.query = query;
      this.fetchOptions = fetchOptions;
    }
  }

  /* @VisibleForTesting */
  QueryAndFetchOptions getPersistedUsageRefreshQuery(Long indexId) {
    Query refreshQuery = new Query(getUsageEntityKind(indexId));
    refreshQuery.setKeysOnly();
    return new QueryAndFetchOptions(refreshQuery, withLimit(maxUsagesTrackedPerIndex));
  }

  private static String getUsageEntityKind(long compositeIndexId) {
    return USAGE_ENTITY_KIND_PREFIX + compositeIndexId;
  }

  private static String getUsageEntityKeyName(Query query) {
    // We don't include the client stack trace as part of the usage key because
    // although it's easy to filter appengine API frames from the client stack, what's not
    // easy is to determine which client function is an interesting function vs. a proxy
    // function used to wrap a datastore API call.
    return Integer.toString(query.hashCodeNoFilterValues());
  }

  /* @VisibleForTesting */
  Entity newUsageEntity(
      Index index, Query query, Date occurenceTime, LazyApiInvokerStackTrace lazyStackTrace) {
    String kind = getUsageEntityKind(index.getId());
    Key key = KeyFactory.createKey(kind, getUsageEntityKeyName(query));
    StackTraceInfo stackTraceInfo = lazyStackTrace.get();

    Entity entity = new Entity(key);
    entity.setProperty(USAGE_ENTITY_QUERY_PROPERTY, new Text(query.toString()));
    entity.setProperty(USAGE_ENTITY_CAPTURE_TIME_PROPERTY, stackTraceInfo.captureTimeNanos);
    entity.setProperty(USAGE_ENTITY_OCCURRENCE_TIME_PROPERTY, occurenceTime);
    entity.setProperty(USAGE_ENTITY_STACK_TRACE_PROPERTY, new Text(stackTraceInfo.stackTrace));
    entity.setProperty(USAGE_ENTITY_APP_VERSION_PROPERTY, SystemProperty.applicationVersion.get());
    return entity;
  }

  /* @VisibleForTesting */
  AsyncDatastoreService newAsyncDatastoreService(double deadlineSecs) {
    return DatastoreServiceFactory.getAsyncDatastoreService(
        DatastoreServiceConfig.Builder.withDatastoreCallbacks(
                DatastoreCallbacks.NoOpDatastoreCallbacks.INSTANCE)
            .deadline(deadlineSecs));
  }

  /* @VisibleForTesting */
  Date newDate() {
    return new Date();
  }

  /**
   * This class caches usage ids on a per index basis.
   *
   * <p>Note that initially {@code CacheBuilder} was used to implement this cache, but unfortunately
   * the datastore api library can not currently depend on {@code com.google.common.cache}.
   */
  /* @ThreadSafe */
  private class UsageIdCache {
    /* @GuardedBy("this") */
    final UsageIdCacheMap usageIdMap;

    private UsageIdCache(final int capacity) {
      usageIdMap = new UsageIdCacheMap(capacity);
    }

    private synchronized ExpiringPersistedUsageIds get(Long indexId) {
      ExpiringPersistedUsageIds usageIds = usageIdMap.get(indexId);
      if ((usageIds == null) || usageIds.isExpired()) {
        // We are issuing the refresh query from within a synchronized block, but the expected
        // impact is minimal since the query is issued asynchronously
        usageIds = new ExpiringPersistedUsageIds(indexId, usageIds);
        usageIdMap.put(indexId, usageIds);
      }
      return usageIds;
    }
  }

  /**
   * This class contains the core datastructure for the {@code UsageIdCache}. It extends from
   * LinkedHashMap which provides a bounded HashMap. It sorts the contents by LRU access order
   * (get() and put() count as accesses). Entries that are infrequently accessed are the first to be
   * evicted when the size bound for the map is exceeded.
   *
   * <p>This class had to be named (vs. being an anonymous class) because {@code SerializationTest}
   * requires every serializable object (anonymous or otherwise) to have a golden file. Note that
   * this class is never actually serialized. Therefore it's okay to break serialization
   * compatibility in the future if needed.
   */
  /* @VisibleForTesting */
  static class UsageIdCacheMap extends LinkedHashMap<Long, ExpiringPersistedUsageIds> {
    static final long serialVersionUID = -5010587885037930115L;
    private static final int DEFAULT_INITIAL_CAPACITY = 16; // Copied from HashMap
    private static final float DEFAULT_LOAD_FACTOR = 0.75f; // Copied from HashMap
    private static final boolean SORT_ELEMENTS_BY_ACCESS_ORDER = true;

    private final int capacity;

    UsageIdCacheMap(int capacity) {
      super(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, SORT_ELEMENTS_BY_ACCESS_ORDER);
      this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, ExpiringPersistedUsageIds> eldest) {
      return size() > capacity;
    }
  }

  /** This class gets an up to date snapshot of the persisted usage ids for a monitored index. */
  /* @ThreadSafe */
  private class ExpiringPersistedUsageIds {
    private final Long creationTimeNanos;
    private final Thread usageLoaderThread;

    // this number can be inexact
    private volatile int numContiguousRefreshQueryFailures;

    private volatile PersistedUsageIds usageIds;

    private Iterable<Entity> refreshQueryEntities; // Only accessible by the usageLoaderThread.

    /**
     * Constructs a new expiring persisted usage id set. The contents of the set are filled in by
     * the thread invoking the constructor. In the constructor an asynchronous query is issued to
     * fetch the set of usages that are currently persisted. The results for the query are reaped
     * when the thread invoking the constructor calls {@link #get}.
     *
     * @param indexId the id of the index for which to load usage information.
     * @param prevExpiringUsageIds the prior usage id set associated with {@code indexId}. If NULL
     *     this is the first time usage information is being loaded by this clone for the specified
     *     index.
     */
    private ExpiringPersistedUsageIds(
        Long indexId, @Nullable ExpiringPersistedUsageIds prevExpiringUsageIds) {
      this.creationTimeNanos = ticker.read();
      this.usageLoaderThread = Thread.currentThread();

      if (prevExpiringUsageIds != null) {
        // We do a dirty read of the old usage id set. Right after we do the read the
        // old usage id set could update the usageIds (because its refresh query completes). That's
        // okay as long as this is an edge case. Which it is because the ExpiringPersistedUsageIds
        // expiration time is much larger than the query refresh deadline.
        this.usageIds = prevExpiringUsageIds.usageIds;
        numContiguousRefreshQueryFailures = prevExpiringUsageIds.numContiguousRefreshQueryFailures;
      } else {
        // The first time the usage information is looked up for the instance we return the
        // tombstoned persisted usage id set until the query refresh is complete. The goal is
        // to avoid a flood of writes every time a new clone comes up. Note that the thread
        // doing the refresh will (in non error cases) write at least one usage if there
        // are no persisted usages currently stored for the index.
        usageIds = PersistedUsageIds.TOMBSTONE_INSTANCE;
        numContiguousRefreshQueryFailures = 0;
      }

      QueryAndFetchOptions refreshQuery = getPersistedUsageRefreshQuery(indexId);
      AsyncDatastoreService asyncDatastore = newAsyncDatastoreService(refreshQueryDeadlineSecs);
      PreparedQuery refreshPQ = asyncDatastore.prepare(refreshQuery.query);

      refreshQueryEntities = null;
      try {
        // Issue the refresh query asynchronously.
        refreshQueryEntities = refreshPQ.asIterable(refreshQuery.fetchOptions);
      } catch (RuntimeException e) {
        numContiguousRefreshQueryFailures++;
        logger.log(
            Level.SEVERE,
            String.format(
                "Failed to query existing monitored index usages: %s", refreshPQ.toString()),
            e);
      }
      // Note that on exception we leave the usageIds as either the tombstoned usageId
      // or as whatever it was in the past. This works because if the previous usage information
      // was not a tombstone that implies at least one persisted usage query did succeed. And it's
      // better to use that stale persisted usage id information than to force in a tombstone
      // since with a tombstone no usages are persisted.
    }

    private PersistedUsageIds get() {
      // Threads that are not the thread that created this instance will do a dirty read of
      // the usageIds. The thread that created this instance will either process the
      // the entity refresh query (if it's still pending) and update usageIds or it will just
      // return the cached usageIds.
      if ((usageLoaderThread != Thread.currentThread()) || (null == refreshQueryEntities)) {
        return usageIds;
      }

      boolean logException = false; // flood control logging by default
      Throwable throwable = null;
      try {
        PersistedUsageIds existingKeys = new PersistedUsageIds(maxUsagesTrackedPerIndex);
        for (Entity persistedEntity : refreshQueryEntities) {
          existingKeys.addNewUsage(persistedEntity.getKey().getName());
        }
        usageIds = existingKeys;
        numContiguousRefreshQueryFailures = 0;
      } catch (OverQuotaException e) {
        // An application can always become over quota.
        throwable = e;
      } catch (DeadlineExceededException e) {
        // A transient datastore stack issue may have occurred.
        throwable = e;
      } catch (DatastoreTimeoutException e) {
        // A transient datastore stack issue may have occurred.
        throwable = e;
      } catch (DatastoreFailureException e) {
        // A transient datastore stack issue may have occurred or the thread could
        // have been interrupted.
        throwable = e;
      } catch (RuntimeException e) {
        logException = true;
        throwable = e;
      }

      if (throwable != null) {
        numContiguousRefreshQueryFailures++;

        // Conditionally log recurring exceptions if we have too many back to back query refresh
        // failures. By doing this level of flood control we avoid intermittent issues from
        // polluting the customers log.
        if ((numContiguousRefreshQueryFailures % REFRESH_QUERY_FAILURE_LOGGING_THRESHOLD) == 0) {
          logException = true;
        }

        if (logException) {
          logger.log(
              Level.SEVERE,
              "Failed to query existing monitored index usage information",
              throwable);
        }
      }

      // We only attempt to get the persisted entities once. If a failure occurs then at the
      // next refresh period a new UsageIdLoader will be created and it will issue a new
      // query to fetch the persisted entities.
      refreshQueryEntities = null;

      // On exception usageIds is not updated. These are the same semantics as in the constructor.
      // See the exception handling comments in the constructor for justification.
      return usageIds;
    }

    private boolean isExpired() {
      return (ticker.read() - creationTimeNanos) > usageRefreshPeriodNanos;
    }
  }

  /**
   * This class holds the set of persisted usage ids for a monitored index. Call {@link
   * #addNewUsage} to conditionally add an element to the persisted usage set. Only items that are
   * added to the set should be persisted by the caller.
   */
  /* @ThreadSafe */
  private static class PersistedUsageIds {
    /**
     * The tombstone instance always returns FALSE from {@link #addNewUsage}. It is used to prevent
     * the persistence of new usages for an index.
     */
    private static final PersistedUsageIds TOMBSTONE_INSTANCE = new PersistedUsageIds(0);

    private final Set<String> persistedIds;
    private final int maxIdsPersisted;

    PersistedUsageIds(int maxIdsPersisted) {
      persistedIds = Sets.newHashSetWithExpectedSize(maxIdsPersisted);
      this.maxIdsPersisted = maxIdsPersisted;
    }

    /**
     * @return {@code true} if the {@code usageId} was added to the set and should be persisted by
     *     the caller.
     */
    synchronized boolean addNewUsage(String usageId) {
      if (persistedIds.size() >= maxIdsPersisted) {
        return false;
      }
      return persistedIds.add(usageId);
    }
  }

  private static long getUsageRefreshPeriodNanos() {
    String usageRefreshPeriodSecsStr = System.getProperty(REFRESH_PERIOD_SECS_SYS_PROP);
    if (usageRefreshPeriodSecsStr != null) {
      double usageRefreshPeriodSecs = Double.parseDouble(usageRefreshPeriodSecsStr);
      return (long) (usageRefreshPeriodSecs * 1000 * 1000 * 1000);
    } else {
      return TimeUnit.SECONDS.toNanos(DEFAULT_USAGE_REFRESH_PERIOD_SECS);
    }
  }

  private static double getRefreshQueryDeadlineSecs() {
    String refreshDeadlineSecsStr = System.getProperty(REFRESH_QUERY_DEADLINE_SECS_SYS_PROP);
    if (refreshDeadlineSecsStr != null) {
      return Double.parseDouble(refreshDeadlineSecsStr);
    } else {
      return DEFAULT_REFRESH_QUERY_DEADLINE_SECS;
    }
  }

  private static double getPutDeadlineSecs() {
    String putDeadlineSecsStr = System.getProperty(PUT_DEADLINE_SECS_SYS_PROP);
    if (putDeadlineSecsStr != null) {
      return Double.parseDouble(putDeadlineSecsStr);
    } else {
      return DEFAULT_PUT_DEADLINE_SECS;
    }
  }

  private static long getNewUsageLoggingThresholdNanos() {
    String newUsageLoggingThreshSecsStr =
        System.getProperty(NEW_USAGE_LOGGING_THRESHOLD_SECS_SYS_PROP);
    if (newUsageLoggingThreshSecsStr != null) {
      double newUsageLoggingThreshSecs = Double.parseDouble(newUsageLoggingThreshSecsStr);
      return (long) (newUsageLoggingThreshSecs * 1000 * 1000 * 1000);
    } else {
      // By default we don't have a threshold for logging. This is because events like
      // garbage collection and machine anomalies can cause spurious warnings to be written.
      return Long.MAX_VALUE;
    }
  }

  private static class StackTraceInfo {
    final String stackTrace;
    final long captureTimeNanos;

    StackTraceInfo(String stackTrace, long captureTimeNanos) {
      this.stackTrace = stackTrace;
      this.captureTimeNanos = captureTimeNanos;
    }
  }

  /**
   * This class lazily captures the stack trace on the first call to {@link #get} and returns the
   * same stack trace on future calls to {@code get}. The purpose of this class is to avoid any
   * repeated overhead due to capturing the stack trace multiple times in the same api invocation.
   */
  /* @VisibleForTesting */
  class LazyApiInvokerStackTrace {
    private StackTraceInfo stackTraceInfo;

    /**
     * @return a {@code Pair} containing the stack trace string and the time it took to capture the
     *     stack trace in nanoseconds.
     */
    private StackTraceInfo get() {
      if (stackTraceInfo == null) {
        long startNs = ticker.read();
        String stackTrace = getApiInvokerStackTrace(apiPackagePrefixTrie);
        long captureTimeNanos = ticker.read() - startNs;
        stackTraceInfo = new StackTraceInfo(stackTrace, captureTimeNanos);
      }
      return stackTraceInfo;
    }
  }

  private static PrefixTrie<Boolean> getApiPackagePrefixesTrie() {
    PrefixTrie<Boolean> prefixTrie = new PrefixTrie<Boolean>();

    for (String apiPrefix : DEFAULT_API_PACKAGE_PREFIXES) {
      prefixTrie.put(apiPrefix, Boolean.TRUE);
    }

    // get comma separated package prefixes
    String sysPropApiPrefixesString = System.getProperty(PACKAGE_PREFIXES_TO_SKIP_SYS_PROP);
    if (sysPropApiPrefixesString != null) {
      String[] sysPropApiPrefixes = sysPropApiPrefixesString.split("\\,");
      for (String apiPrefix : sysPropApiPrefixes) {
        // Note that the prefix trie implementations requires ascii characters. For now this
        // limitation is acceptable.
        prefixTrie.put(apiPrefix, Boolean.TRUE);
      }
    }

    return prefixTrie;
  }

  /**
   * Return the stack trace of the invoker excluding stack frames from the top of the stack (most
   * recently invoked) that have a package prefix that matches a prefix in {@code
   * apiPackagePrefixes}. At max {@code MAX_STACK_FRAMES_SAVED} stack frames are returned.
   */
  private static String getApiInvokerStackTrace(PrefixTrie<Boolean> apiPackagePrefixes) {
    // The first frame is always "java.lang.Thread.getStackTrace()
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    int frameIdx;
    for (frameIdx = 1; frameIdx < stack.length; frameIdx++) {
      String className = stack[frameIdx].getClassName();
      if (apiPackagePrefixes.get(className) == null) {
        break;
      }
    }

    if (frameIdx >= stack.length) {
      return "";
    }

    int numFrames = stack.length - frameIdx;
    numFrames = Math.min(numFrames, MAX_STACK_FRAMES_SAVED);
    StringBuilder sb = new StringBuilder();
    for (; frameIdx < stack.length; frameIdx++) {
      sb.append(stack[frameIdx]);
      sb.append("\n");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    return sb.toString();
  }
}
