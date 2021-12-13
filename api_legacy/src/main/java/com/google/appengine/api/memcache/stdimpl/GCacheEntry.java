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

package com.google.appengine.api.memcache.stdimpl;

import java.util.Objects;
import javax.cache.Cache;
import javax.cache.CacheEntry;

/**
 * JCache CacheEntry implementation using Memcache.
 *
 */
public class GCacheEntry implements CacheEntry {

  private Object key;
  private Object value;
  private Cache cache;

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
  public long getHits() {
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
      return Objects.equals(key, other.getKey()) && Objects.equals(value, other.getValue());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public boolean isValid() {
    return Objects.equals(this, cache.getCacheEntry(key));
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
  @SuppressWarnings("unchecked")
  public Object setValue(Object newValue) {
    this.value = newValue;
    return cache.put(key, value);
  }
}
