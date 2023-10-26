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

package com.google.appengine.api.memcache.dev;

import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheBatchIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheDeleteResponse.DeleteStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheFlushResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementRequest.Direction;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheIncrementResponse.IncrementStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheServiceError;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest.SetPolicy;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheStatsResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MergedNamespaceStats;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LatencyPercentiles;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.auto.service.AutoService;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java bindings for the local Memcache service. The local cache will by default hold up to 100Mb of
 * combined key-and-value size. That limit can be changed with the init property {@code
 * memcache.maxsize}, set in megabytes ("100M"), in kilobytes ("102400K"), or in bytes
 * ("104857600").
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalMemcacheService extends AbstractLocalRpcService {

  /** The package name for this service. */
  public static final String PACKAGE = "memcache";

  public static final String SIZE_PROPERTY = "memcache.maxsize";
  private static final String DEFAULT_MAX_SIZE = "100M";
  private static final String UTF8 = "UTF-8";
  private static final BigInteger UINT64_MIN_VALUE = BigInteger.ZERO;
  private static final BigInteger UINT64_MAX_VALUE = new BigInteger("FFFFFFFFFFFFFFFF", 16);

  // global unique counter for compare-and-swap operations.
  // This will be globally incremented each time a new CAS ID is assigned, eventually wrapping
  // around.  See //depot/google3/cacheserving/memcacheg/server/item.cc.
  private final AtomicLong globalNextCasId;

  /**
   * A single entry in the cache.
   *
   */
  private class CacheEntry extends LRU.AbstractChainable<CacheEntry>
      implements Comparable<CacheEntry> {
    public final String namespace;
    public final Key key;
    public byte[] value;
    int flags;

    /** Expiration in milliseconds-since-epoch */
    public long expires;

    /** Access time in milliseconds-since-epoch */
    public long access;

    public long bytes;

    /** "compare-and-swap" ID. See comments in //apphosting/api/memcache/memcache_service.proto. */
    public Long casId; // null ==> entry does not have a CAS ID.

    /**
     * Creates a new entry
     *
     * @param namespace the containing namespace
     * @param key the key
     * @param value the value
     * @param flags value-interpretation flags
     * @param expiration expiration in milliseconds-since-epoch
     */
    public CacheEntry(String namespace, Key key, byte[] value, int flags, long expiration) {
      this.namespace = namespace;
      this.key = key;
      this.value = value;
      this.flags = flags;
      this.expires = expiration;
      this.access = clock.getCurrentTime();
      this.bytes = key.getBytes().length + value.length;
      this.casId = null;
    }

    /** Sort cache entries by descending access times. */
    @Override
    public int compareTo(CacheEntry entry) {
      return Long.compare(access, entry.access);
    }

    /**
     * Ensures the CacheEntry has a CAS ID. This CacheEntry will be mutated and assigned a CAS ID if
     * it does not already have one.
     *
     * <p>This mutation happens during a set operation that specifies SetPolicy.CAS.
     */
    void markWithCasId() {
      if (this.hasCasId()) {
        return;
      } else {
        this.casId = globalNextCasId.addAndGet(1) - 1;
      }
    }

    long getCasId() {
      return casId.longValue();
    }

    boolean hasCasId() {
      return (casId != null);
    }
  }

  private class LocalStats {
    private long hits;
    private long misses;
    private long hitBytes;
    private long itemCount;
    private long totalBytes;

    private LocalStats(long hits, long misses, long hitBytes, long itemCount, long totalBytes) {
      this.hits = hits;
      this.misses = misses;
      this.hitBytes = hitBytes;
      this.itemCount = itemCount;
      this.totalBytes = totalBytes;
    }

    public MergedNamespaceStats getAsMergedNamespaceStats() {
      return MergedNamespaceStats.newBuilder()
          .setHits(hits)
          .setMisses(misses)
          .setByteHits(hitBytes)
          .setBytes(totalBytes)
          .setItems(itemCount)
          .setOldestItemAge(getMaxSecondsWithoutAccess())
          .build();
    }

    public int getMaxSecondsWithoutAccess() {
      if (lru.isEmpty()) {
        return 0; // no entries
      }
      CacheEntry entry = lru.getOldest();
      return (int) ((clock.getCurrentTime() - entry.access) / 1000);
    }

    public void recordHit(CacheEntry ce) {
      hits++;
      hitBytes += ce.bytes;
    }

    public void recordMiss() {
      misses++;
    }

    public void recordAdd(CacheEntry ce) {
      itemCount++;
      totalBytes += ce.bytes;
      while (totalBytes > maxSize) {
        CacheEntry oldest = lru.getOldest();
        internalDelete(oldest.namespace, oldest.key);
        itemCount--;
        totalBytes -= oldest.bytes;
      }
    }

    public void recordDelete(CacheEntry ce) {
      itemCount--;
      totalBytes -= ce.bytes;
    }
  }

  /**
   * Our keys will be byte[], which by default doesn't do hashCode() and equals() correctly. This
   * wraps it to do so, using java.util.Arrays.
   */
  private class Key {
    private byte[] keyval;

    public Key(byte[] bytes) {
      keyval = bytes;
    }

    public byte[] getBytes() {
      return keyval;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof Key) {
        return Arrays.equals(keyval, ((Key) other).keyval);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(keyval);
    }
  }

  private LRU<CacheEntry> lru;
  private final Map<String, Map<Key, CacheEntry>> mockCache;
  private final Map<String, Map<Key, Long>> deleteHold;
  private long maxSize;
  private LocalStats stats;
  private Clock clock;

  public LocalMemcacheService() {
    lru = new LRU<CacheEntry>();
    mockCache = new HashMap<String, Map<Key, CacheEntry>>();
    deleteHold = new HashMap<String, Map<Key, Long>>();
    stats = new LocalStats(0, 0, 0, 0, 0);
    globalNextCasId = new AtomicLong(1);
  }

  private <K1, K2, V> Map<K2, V> getOrMakeSubMap(Map<K1, Map<K2, V>> map, K1 key) {
    Map<K2, V> subMap = map.get(key);
    if (subMap == null) {
      subMap = new HashMap<K2, V>();
      map.put(key, subMap);
    }
    return subMap;
  }

  private CacheEntry getWithExpiration(String namespace, Key key) {
    CacheEntry entry;
    synchronized (mockCache) {
      entry = getOrMakeSubMap(mockCache, namespace).get(key);
      if (entry != null) {
        if (entry.expires == 0 || clock.getCurrentTime() < entry.expires) {
          entry.access = clock.getCurrentTime();
          lru.update(entry);
          return entry;
        }
        // Clean up expired item.
        getOrMakeSubMap(mockCache, namespace).remove(key);
        lru.remove(entry);
        stats.recordDelete(entry);
      }
    }
    return null;
  }

  private CacheEntry internalDelete(String namespace, Key key) {
    CacheEntry ce;
    synchronized (mockCache) {
      ce = getOrMakeSubMap(mockCache, namespace).remove(key);
      if (ce != null) {
        lru.remove(ce);
      }
    }
    return ce;
  }

  private void internalSet(String namespace, Key key, CacheEntry entry) {
    synchronized (mockCache) {
      Map<Key, CacheEntry> namespaceMap = getOrMakeSubMap(mockCache, namespace);
      CacheEntry old = namespaceMap.get(key);
      if (old != null) {
        // The old entry is no longer valid so remove it from the LRU. The new entry will take its
        // place when we add it below.
        lru.remove(old);
        stats.recordDelete(old);
      }
      namespaceMap.put(key, entry);
      lru.update(entry);
      stats.recordAdd(entry);
    }
  }

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    this.clock = context.getClock();
    String propValue = properties.get(SIZE_PROPERTY);
    if (propValue == null) {
      propValue = DEFAULT_MAX_SIZE;
    } else {
      propValue = propValue.toUpperCase();
    }
    int multiplier = 1;
    if (propValue.endsWith("M") || propValue.endsWith("K")) {
      if (propValue.endsWith("M")) {
        multiplier = 1024 * 1024;
      } else {
        multiplier = 1024;
      }
      propValue = propValue.substring(0, propValue.length() - 1);
    }
    try {
      maxSize = Long.parseLong(propValue) * multiplier;
    } catch (NumberFormatException ex) {
      throw new MemcacheServiceException(
          "Can't parse cache size limit '" + properties.get(SIZE_PROPERTY) + "'", ex);
    }
  }

  /**
   * Skips the system properties to set the limit values for size and element counts.
   *
   * @param bytes number of bytes of keys + values that is allowed before old entries are discarded.
   */
  public void setLimits(int bytes) {
    maxSize = bytes;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  public MemcacheGetResponse get(Status status, MemcacheGetRequest req) {
    MemcacheGetResponse.Builder result = MemcacheGetResponse.newBuilder();

    for (int i = 0; i < req.getKeyCount(); i++) {
      // our key is always a SHA1 hashcode
      Key key = new Key(req.getKey(i).toByteArray());
      CacheEntry entry = getWithExpiration(req.getNameSpace(), key);
      if (entry == null) {
        stats.recordMiss();
      } else {
        stats.recordHit(entry);
        MemcacheGetResponse.Item.Builder item = MemcacheGetResponse.Item.newBuilder();

        item.setKey(ByteString.copyFrom(key.getBytes()))
            .setFlags(entry.flags)
            .setValue(ByteString.copyFrom(entry.value));

        if (req.hasForCas() && req.getForCas()) {
          entry.markWithCasId();
          item.setCasId(entry.getCasId());
        }

        result.addItem(item.build());
      }
    }
    status.setSuccessful(true);
    return result.build();
  }

  public MemcacheSetResponse set(Status status, MemcacheSetRequest req) {
    MemcacheSetResponse.Builder result = MemcacheSetResponse.newBuilder();
    final String namespace = req.getNameSpace();

    for (int i = 0; i < req.getItemCount(); i++) {
      MemcacheSetRequest.Item item = req.getItem(i);
      Key key = new Key(item.getKey().toByteArray());
      SetPolicy policy = item.getSetPolicy();
      Map<Key, Long> timeoutMap = getOrMakeSubMap(deleteHold, namespace);
      Long timeout = timeoutMap.get(key);

      if (timeout != null && policy == SetPolicy.SET) {
        // A SET operation overrides and clears any timeout that may exist
        timeout = null;
        timeoutMap.remove(key);
      }

      if ((timeout != null && clock.getCurrentTime() < timeout)
          || (policy == SetPolicy.CAS && !item.hasCasId())) {
        result.addSetStatus(SetStatusCode.NOT_STORED);
        continue;
      }

      synchronized (mockCache) {
        CacheEntry existingEntry = getWithExpiration(namespace, key);
        if ((policy == SetPolicy.REPLACE && existingEntry == null)
            || (policy == SetPolicy.ADD && existingEntry != null)
            || (policy == SetPolicy.CAS && existingEntry == null)) {
          result.addSetStatus(SetStatusCode.NOT_STORED);
        } else if (policy == SetPolicy.CAS
            && (!existingEntry.hasCasId() || existingEntry.getCasId() != item.getCasId())) {
          result.addSetStatus(SetStatusCode.EXISTS);
        } else {
          long expiry = item.hasExpirationTime() ? (long) item.getExpirationTime() : 0L;
          byte[] value = item.getValue().toByteArray();
          int flags = item.getFlags();

          // We create a new cacheEntry every time (rather than updating existing ones) to
          // avoid having to synchronize on reads. (Otherwise a reader on another thread
          // could be exposed to a partially modified entry).
          CacheEntry newEntry = new CacheEntry(namespace, key, value, flags, expiry * 1000);
          internalSet(namespace, key, newEntry);
          result.addSetStatus(SetStatusCode.STORED);
        }
      }
    }
    status.setSuccessful(true);
    return result.build();
  }

  @LatencyPercentiles(latency50th = 4)
  public MemcacheDeleteResponse delete(Status status, MemcacheDeleteRequest req) {
    MemcacheDeleteResponse.Builder result = MemcacheDeleteResponse.newBuilder();
    final String namespace = req.getNameSpace();

    for (int i = 0; i < req.getItemCount(); i++) {
      MemcacheDeleteRequest.Item item = req.getItem(i);
      Key key = new Key(item.getKey().toByteArray());
      CacheEntry ce = internalDelete(namespace, key);
      result.addDeleteStatus(ce == null ? DeleteStatusCode.NOT_FOUND : DeleteStatusCode.DELETED);
      if (ce != null) {
        stats.recordDelete(ce);
      }
      // open spec whether this happens if there was no deletion
      if (item.hasDeleteTime()) {
        int millisNoReAdd = item.getDeleteTime() * 1000;
        getOrMakeSubMap(deleteHold, namespace).put(key, clock.getCurrentTime() + millisNoReAdd);
      }
    }
    status.setSuccessful(true);
    return result.build();
  }

  public MemcacheIncrementResponse increment(Status status, MemcacheIncrementRequest req) {
    MemcacheIncrementResponse.Builder result = MemcacheIncrementResponse.newBuilder();
    final String namespace = req.getNameSpace();
    final Key key = new Key(req.getKey().toByteArray());
    final long delta = req.getDirection() == Direction.DECREMENT ? -req.getDelta() : req.getDelta();

    synchronized (mockCache) { // only increment offers atomicity
      CacheEntry ce = getWithExpiration(namespace, key);
      if (ce == null) {
        if (req.hasInitialValue()) {
          // initial value is considered as uint64 and therefore can never be negative
          BigInteger value = BigInteger.valueOf(req.getInitialValue()).and(UINT64_MAX_VALUE);
          int flags =
              req.hasInitialFlags()
                  ? req.getInitialFlags()
                  : MemcacheSerialization.Flag.LONG.ordinal();
          ce = new CacheEntry(namespace, key, value.toString().getBytes(), flags, 0);
          internalSet(namespace, key, ce);
        } else {
          stats.recordMiss();
          return result.build(); // with hasNewValue() == false
        }
      }
      stats.recordHit(ce);
      BigInteger value;
      try {
        value = new BigInteger(new String(ce.value, UTF8));
      } catch (NumberFormatException e) {
        status.setSuccessful(false);
        throw new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.INVALID_VALUE_VALUE, "Format error");
      } catch (UnsupportedEncodingException e) {
        throw new ApiProxy.UnknownException(UTF8 + " encoding was not found.");
      }
      if (value.compareTo(UINT64_MAX_VALUE) > 0 || value.signum() < 0) {
        status.setSuccessful(false);
        throw new ApiProxy.ApplicationException(
            MemcacheServiceError.ErrorCode.INVALID_VALUE_VALUE,
            "Value to be incremented must be in the range of an unsigned 64-bit number");
      }

      value = value.add(BigInteger.valueOf(delta));
      if (value.signum() < 0) {
        value = UINT64_MIN_VALUE;
      } else if (value.compareTo(UINT64_MAX_VALUE) > 0) {
        value = value.and(UINT64_MAX_VALUE);
      }
      stats.recordDelete(ce);
      try {
        ce.value = value.toString().getBytes(UTF8);
      } catch (UnsupportedEncodingException e) {
        throw new ApiProxy.UnknownException(UTF8 + " encoding was not found.");
      }
      // don't change the flags; it keeps its original size/type
      ce.bytes = key.getBytes().length + ce.value.length;
      Map<Key, CacheEntry> namespaceMap = getOrMakeSubMap(mockCache, namespace);
      namespaceMap.remove(key);
      namespaceMap.put(key, ce);
      stats.recordAdd(ce);
      result.setNewValue(value.longValue());
    }
    status.setSuccessful(true);
    return result.build();
  }

  public MemcacheBatchIncrementResponse batchIncrement(
      Status status, MemcacheBatchIncrementRequest batchReq) {
    MemcacheBatchIncrementResponse.Builder result = MemcacheBatchIncrementResponse.newBuilder();
    String namespace = batchReq.getNameSpace();

    synchronized (mockCache) { // only increment offers atomicity
      for (MemcacheIncrementRequest req : batchReq.getItemList()) {
        MemcacheIncrementResponse.Builder resp = MemcacheIncrementResponse.newBuilder();

        Key key = new Key(req.getKey().toByteArray());
        long delta = req.getDelta();
        if (req.getDirection() == Direction.DECREMENT) {
          delta = -delta;
        }

        CacheEntry ce = getWithExpiration(namespace, key);
        long newvalue;
        if (ce == null) {
          if (req.hasInitialValue()) {
            MemcacheSerialization.ValueAndFlags value;
            try {
              value = MemcacheSerialization.serialize(req.getInitialValue());
            } catch (IOException e) {
              throw new ApiProxy.UnknownException("Serialzation error: " + e);
            }
            ce = new CacheEntry(namespace, key, value.value, value.flags.ordinal(), 0);
          } else {
            stats.recordMiss();
            resp.setIncrementStatus(IncrementStatusCode.NOT_CHANGED);
            result.addItem(resp);
            continue;
          }
        }
        stats.recordHit(ce);
        Long longval;
        try {
          longval = Long.parseLong(new String(ce.value, UTF8));
        } catch (NumberFormatException e) {
          resp.setIncrementStatus(IncrementStatusCode.NOT_CHANGED);
          result.addItem(resp);
          continue;
        } catch (UnsupportedEncodingException e) {
          resp.setIncrementStatus(IncrementStatusCode.NOT_CHANGED);
          result.addItem(resp);
          continue;
        }
        if (longval < 0) {
          resp.setIncrementStatus(IncrementStatusCode.NOT_CHANGED);
          result.addItem(resp);
          continue;
        }

        newvalue = longval;
        newvalue += delta;
        if (delta < 0 && newvalue < 0) {
          newvalue = 0;
        }
        stats.recordDelete(ce);
        try {
          ce.value = Long.toString(newvalue).getBytes(UTF8);
        } catch (UnsupportedEncodingException e) {
          // Shouldn't happen.
          throw new ApiProxy.UnknownException(UTF8 + " encoding was not found.");
        }

        // don't change the flags; it keeps its original size/type
        ce.bytes = key.getBytes().length + ce.value.length;
        Map<Key, CacheEntry> namespaceMap = getOrMakeSubMap(mockCache, namespace);
        namespaceMap.remove(key);
        namespaceMap.put(key, ce);
        stats.recordAdd(ce);

        resp.setIncrementStatus(IncrementStatusCode.OK);
        resp.setNewValue(newvalue);
        result.addItem(resp);
      }
    }
    status.setSuccessful(true);
    return result.build();
  }

  public MemcacheFlushResponse flushAll(Status status, MemcacheFlushRequest req) {
    MemcacheFlushResponse.Builder result = MemcacheFlushResponse.newBuilder();
    synchronized (mockCache) {
      mockCache.clear();
      deleteHold.clear();
      lru.clear();
      stats = new LocalStats(0, 0, 0, 0, 0);
    }

    status.setSuccessful(true);
    return result.build();
  }

  public MemcacheStatsResponse stats(Status status, MemcacheStatsRequest req) {
    MemcacheStatsResponse result =
        MemcacheStatsResponse.newBuilder().setStats(stats.getAsMergedNamespaceStats()).build();
    status.setSuccessful(true);
    return result;
  }

  public long getMaxSizeInBytes() {
    return maxSize;
  }

  @Override
  public Integer getMaxApiRequestSize() {
    // Keep this in sync with MAX_REQUEST_SIZE in //apphosting/api/memcache/memcache_stub.py.
    return 32 << 20; // 32 MB
  }

  /* @VisibleForTesting */
  LRU<?> getLRU() {
    return lru;
  }
}
