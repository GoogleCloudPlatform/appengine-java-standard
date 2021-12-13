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

package com.google.appengine.api.memcache;

import com.google.appengine.api.memcache.MemcacheService.CasValues;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * An asynchronous version of {@link MemcacheService}.  All methods return
 * immediately and provide {@link Future Futures} as their return values.
 *
 */
public interface AsyncMemcacheService extends BaseMemcacheService {

  /**
   * @see MemcacheService#get(Object)
   */
  Future<Object> get(Object key);

  /**
   * @see MemcacheService#getIdentifiable(Object)
   */
  Future<IdentifiableValue> getIdentifiable(Object key);

  /**
   * @see MemcacheService#getIdentifiables(Collection)
   */
  <T> Future<Map<T, IdentifiableValue>> getIdentifiables(Collection<T> keys);

  /**
   * @see MemcacheService#contains(Object)
   */
  Future<Boolean> contains(Object key);

  /**
   * @see MemcacheService#getAll(Collection)
   */
  <T> Future<Map<T, Object>> getAll(Collection<T> keys);

  /**
   * @see MemcacheService#put(Object, Object, Expiration, SetPolicy)
   */
  Future<Boolean> put(Object key, Object value, Expiration expires, SetPolicy policy);

  /**
   * @see MemcacheService#put(Object, Object, Expiration)
   */
  Future<Void> put(Object key, Object value, Expiration expires);

  /**
   * @see MemcacheService#put(Object, Object)
   */
  Future<Void> put(Object key, Object value);

  /**
   * @see MemcacheService#putAll(Map, Expiration, SetPolicy)
   */
  <T> Future<Set<T>> putAll(Map<T, ?> values, Expiration expires, SetPolicy policy);

  /**
   * @see MemcacheService#putAll(Map, Expiration)
   */
  Future<Void> putAll(Map<?, ?> values, Expiration expires);

  /**
   * @see MemcacheService#putAll(Map)
   */
  Future<Void> putAll(Map<?, ?> values);

  /**
   * @see MemcacheService#putIfUntouched(Object, IdentifiableValue, Object,
   *                                        Expiration)
   */
  Future<Boolean> putIfUntouched(Object key, IdentifiableValue oldValue,
      Object newValue, Expiration expires);

  /**
   * @see MemcacheService#putIfUntouched(Object, IdentifiableValue, Object)
   */
  Future<Boolean> putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue);

  /**
   * @see MemcacheService#putIfUntouched(Map)
   */
  <T> Future<Set<T>> putIfUntouched(Map<T, CasValues> values);

  /**
   * @see MemcacheService#putIfUntouched(Map, Expiration)
   */
  <T> Future<Set<T>> putIfUntouched(Map<T, CasValues> values, Expiration expiration);

  /**
   * @see MemcacheService#delete(Object)
   */
  Future<Boolean> delete(Object key);

  /**
   * @see MemcacheService#delete(Object, long)
   */
  Future<Boolean> delete(Object key, long millisNoReAdd);

  /**
   * @see MemcacheService#deleteAll(Collection)
   */
  <T> Future<Set<T>> deleteAll(Collection<T> keys);

  /**
   * @see MemcacheService#deleteAll(Collection, long)
   */
  <T> Future<Set<T>> deleteAll(Collection<T> keys, long millisNoReAdd);

  /**
   * @see MemcacheService#increment(Object, long)
   */
  Future<Long> increment(Object key, long delta);

  /**
   * @see MemcacheService#increment(Object, long, Long)
   */
  Future<Long> increment(Object key, long delta, Long initialValue);

  /**
   * @see MemcacheService#incrementAll(Collection, long)
   */
  <T> Future<Map<T, Long>> incrementAll(Collection<T> keys, long delta);

  /**
   * @see MemcacheService#incrementAll(Collection, long, Long)
   */
  <T> Future<Map<T, Long>> incrementAll(Collection<T> keys, long delta, Long initialValue);

  /**
   * @see MemcacheService#incrementAll(Map)
   */
  <T> Future<Map<T, Long>> incrementAll(Map<T, Long> offsets);

  /**
   * @see MemcacheService#incrementAll(Map, Long)
   */
  <T> Future<Map<T, Long>> incrementAll(Map<T, Long> offsets, Long initialValue);

  /**
   * @see MemcacheService#clearAll()
   */
  Future<Void> clearAll();

  /**
   * @see MemcacheService#getStatistics()
   */
  Future<Stats> getStatistics();
}
