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

package com.google.appengine.api.memcache.jsr107cache;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.Stats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;
import net.sf.jsr107cache.CacheListener;
import net.sf.jsr107cache.CacheStatistics;

/**
 * JCache Cache implementation using Memcache.
 *
 */
public class GCache implements Cache {

  private final MemcacheService service;
  private final List<CacheListener> listeners;
  private final Expiration expiration;
  private final MemcacheService.SetPolicy setPolicy;
  private final boolean throwOnPutFailure;

  /**
   * Creates a JCache implementation over the provided service with the given properties.
   *
   * @param properties Properties for this cache.
   */
  public GCache(Map<?, ?> properties) {
    listeners = new ArrayList<CacheListener>();
    if (properties != null) {
      // Expiration in seconds.
      Object expirationProperty = properties.get(GCacheFactory.EXPIRATION_DELTA);
      int millis = 0;
      if (expirationProperty instanceof Integer) {
        millis = (Integer) expirationProperty * 1000;
      }
      // Expiration in milliseconds.
      expirationProperty = properties.get(GCacheFactory.EXPIRATION_DELTA_MILLIS);
      if (expirationProperty instanceof Integer) {
        millis = (Integer) expirationProperty;
      }
      if (millis != 0) {
        expiration = Expiration.byDeltaMillis(millis);
      } else {
        expirationProperty = properties.get(GCacheFactory.EXPIRATION);
        if (expirationProperty instanceof Date) {
          expiration = Expiration.onDate((Date) expirationProperty);
        } else {
          expiration = null;
        }
      }
      Object setProperty = properties.get(GCacheFactory.SET_POLICY);
      if (setProperty instanceof MemcacheService.SetPolicy) {
        setPolicy = (MemcacheService.SetPolicy) setProperty;
      } else {
        setPolicy = MemcacheService.SetPolicy.SET_ALWAYS;
      }
      Object memcacheService = properties.get(GCacheFactory.MEMCACHE_SERVICE);
      if (memcacheService instanceof MemcacheService) {
        this.service = (MemcacheService) memcacheService;
      } else {
        Object namespace = properties.get(GCacheFactory.NAMESPACE);
        if (!(namespace instanceof String)) {
          namespace = null;
        }
        this.service = MemcacheServiceFactory.getMemcacheService((String) namespace);
      }
      Object throwOnPutFailureValue = properties.get(GCacheFactory.THROW_ON_PUT_FAILURE);
      if (throwOnPutFailureValue instanceof Boolean) {
        throwOnPutFailure = ((Boolean) throwOnPutFailureValue).booleanValue();
      } else {
        throwOnPutFailure = false;
      }
    } else {
      expiration = null;
      throwOnPutFailure = false;
      setPolicy = MemcacheService.SetPolicy.SET_ALWAYS;
      this.service = MemcacheServiceFactory.getMemcacheService();
    }
  }

  @Override
  public void addListener(CacheListener cacheListener) {
    listeners.add(cacheListener);
  }

  @Override
  public void evict() {
    // Do nothing. Expiration based eviction happens without user input.
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Map getAll(Collection collection) {
    Collection<?> collection1 = collection;
    return service.getAll(collection1);
  }

  @Override
  public CacheEntry getCacheEntry(Object o) {
    Object value = service.get(o);
    if (value == null && !service.contains(o)) {
      return null;
    }
    return new GCacheEntry(this, o, value);
  }

  @Override
  public CacheStatistics getCacheStatistics() {
    return new GCacheStats(service.getStatistics());
  }

  /** Not supported. */
  @Override
  public void load(Object o) {
    // We don't support asynchronous cache loading.
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @SuppressWarnings("rawtypes")
  @Override
  public void loadAll(Collection collection) {
    // We don't support asynchronous cache loading.
    throw new UnsupportedOperationException();
  }

  @Override
  public Object peek(Object o) {
    return get(o);
  }

  @Override
  public void removeListener(CacheListener cacheListener) {
    listeners.remove(cacheListener);
  }

  @Override
  public int size() {
    return getCacheStatistics().getObjectCount();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(Object key) {
    return service.contains(key);
  }

  /** Not supported. */
  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object get(Object key) {
    return service.get(key);
  }

  @Override
  public Object put(Object key, Object value) {
    for (CacheListener listener : listeners) {
      listener.onPut(value);
    }
    boolean added = service.put(key, value, expiration, setPolicy);
    if (!added && throwOnPutFailure) {
      throw new GCacheException("Policy prevented put operation");
    }
    return null;
  }

  @Override
  public Object remove(Object key) {
    for (CacheListener listener : listeners) {
      listener.onRemove(key);
    }
    Object value = service.get(key);
    service.delete(key);
    return value;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void putAll(Map m) {
    @SuppressWarnings("unchecked")
    Set<?> added = service.putAll(m, expiration, setPolicy);
    if (throwOnPutFailure && added.size() < m.size()) {
      throw new GCacheException("Policy prevented some put operations");
    }
  }

  @Override
  public void clear() {
    for (CacheListener listener : listeners) {
      listener.onClear();
    }
    service.clearAll();
  }

  /** Not supported. */
  @SuppressWarnings("rawtypes")
  @Override
  public Set keySet() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @SuppressWarnings("rawtypes")
  @Override
  public Collection values() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @SuppressWarnings("rawtypes")
  @Override
  public Set entrySet() {
    throw new UnsupportedOperationException();
  }

  /** Implementation of the JCache {@link CacheStatistics} using memcache provided statistics. */
  private static class GCacheStats implements CacheStatistics {

    private final Stats stats;

    /**
     * Creates a statistics snapshot using the provided stats.
     *
     * @param stats Statistics to use.
     */
    private GCacheStats(Stats stats) {
      this.stats = stats;
    }

    @Override
    public void clearStatistics() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCacheHits() {
      return (int) stats.getHitCount();
    }

    @Override
    public int getCacheMisses() {
      return (int) stats.getMissCount();
    }

    @Override
    public int getObjectCount() {
      return (int) stats.getItemCount();
    }

    @Override
    public int getStatisticsAccuracy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return stats.toString();
    }
  }
}
