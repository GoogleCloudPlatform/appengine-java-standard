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

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;

/**
 * JCache CacheEntry implementation using Memcache.
 *
 */
public class GCacheEntry implements CacheEntry {

  private final Object key;
  private Object value;
  private final Cache cache;

  /**
   * Creates a GCacheEntry contained within the GCache {@code cache}, with key {@code key} and value
   * {@code value}.
   *
   * @param cache The cache containing this entry.
   * @param key The key of this entry.
   * @param value The value of this entry.
   */
  GCacheEntry(Cache cache, Object key, Object value) {
    this.cache = cache;
    this.key = key;
    this.value = value;
  }

  /** Not supported. */
  @Override
  public long getCost() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public long getCreationTime() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public long getExpirationTime() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public int getHits() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public long getLastAccessTime() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public long getLastUpdateTime() {
    throw new UnsupportedOperationException();
  }

  /** Not supported. */
  @Override
  public long getVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CacheEntry) {
      CacheEntry other = (CacheEntry) obj;
      if ((key == null) ? (other.getKey() == null) : key.equals(other.getKey())) {
        if ((value == null) ? (other.getValue() == null) : value.equals(other.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return key.hashCode() * 37 + value.hashCode();
  }

  @Override
  public boolean isValid() {
    return equals(cache.getCacheEntry(key));
  }

  @Override
  public Object getKey() {
    return key;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public Object setValue(Object newValue) {
    this.value = newValue;
    return cache.put(key, value);
  }
}
