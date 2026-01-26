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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Java bindings for the Memcache service.
 *
 */
class MemcacheServiceImpl implements MemcacheService {

  private final AsyncMemcacheServiceImpl async;

  MemcacheServiceImpl(String namespace) {
    async = new AsyncMemcacheServiceImpl(namespace);
  }

  private static <T> T quietGet(Future<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MemcacheServiceException("Unexpected failure", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      } else if (cause instanceof Error error) {
        throw error;
      } else {
        throw new UndeclaredThrowableException(cause);
      }
    }
  }

  @Override
  public boolean contains(Object key) {
    return quietGet(async.contains(key));
  }

  @Override
  public Object get(Object key) {
    return quietGet(async.get(key));
  }

  @Override
  public IdentifiableValue getIdentifiable(Object key) {
    return quietGet(async.getIdentifiable(key));
  }

  @Override
  public <T> Map<T, IdentifiableValue> getIdentifiables(Collection<T> keys) {
    return quietGet(async.getIdentifiables(keys));
  }

  @Override
  public <T> Map<T, Object> getAll(Collection<T> keys) {
    return quietGet(async.getAll(keys));
  }
  
  @Override
  public ItemForPeek getItemForPeek(Object key) {
    return quietGet(async.getItemForPeek(key));
  }

  @Override
  public <T> Map<T, ItemForPeek> getItemsForPeek(Collection<T> keys) {
   return quietGet(async.getItemsForPeek(keys));
 }

  @Override
  public boolean put(Object key, Object value, Expiration expires, SetPolicy policy) {
    return quietGet(async.put(key, value, expires, policy));
  }

  @Override
  public void put(Object key, Object value, Expiration expires) {
    quietGet(async.put(key, value, expires));
  }

  @Override
  public void put(Object key, Object value) {
    quietGet(async.put(key, value));
  }

  @Override
  public boolean putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue,
      Expiration expires) {
    return quietGet(async.putIfUntouched(key, oldValue, newValue, expires));
  }

  @Override
  public boolean putIfUntouched(Object key, IdentifiableValue oldValue, Object newValue) {
    return quietGet(async.putIfUntouched(key, oldValue, newValue));
  }

  @Override
  public <T> Set<T> putIfUntouched(Map<T, CasValues> values) {
    return quietGet(async.putIfUntouched(values));
  }

  @Override
  public <T> Set<T> putIfUntouched(Map<T, CasValues> values, Expiration expiration) {
    return quietGet(async.putIfUntouched(values, expiration));
  }

  @Override
  public <T> Set<T> putAll(Map<T, ?> values, Expiration expires, SetPolicy policy) {
    return quietGet(async.putAll(values, expires, policy));
  }

  @Override
  public void putAll(Map<?, ?> values, Expiration expires) {
    quietGet(async.putAll(values, expires));
  }

  @Override
  public void putAll(Map<?, ?> values) {
    quietGet(async.putAll(values));
  }

  @Override
  public boolean delete(Object key) {
    return quietGet(async.delete(key));
  }

  @Override
  public boolean delete(Object key, long millisNoReAdd){
    return quietGet(async.delete(key, millisNoReAdd));
  }

  @Override
  public <T> Set<T> deleteAll(Collection<T> keys) {
    return quietGet(async.deleteAll(keys));
  }

  @Override
  public <T> Set<T> deleteAll(Collection<T> keys, long millisNoReAdd) {
    return quietGet(async.deleteAll(keys, millisNoReAdd));
  }

  @Override
  public Long increment(Object key, long delta) {
    return  quietGet(async.increment(key, delta));
  }

  @Override
  public Long increment(Object key, long delta, Long initialValue) {
    return quietGet(async.increment(key, delta, initialValue));
  }

  @Override
  public <T> Map<T, Long> incrementAll(Collection<T> keys, long delta) {
    return quietGet(async.incrementAll(keys, delta));
  }

  @Override
  public <T> Map<T, Long> incrementAll(Collection<T> keys, long delta, Long initialValue) {
    return quietGet(async.incrementAll(keys, delta, initialValue));
  }

  @Override
  public <T> Map<T, Long> incrementAll(Map<T, Long> offsets) {
     return quietGet(async.incrementAll(offsets));
  }

  @Override
  public <T> Map<T, Long> incrementAll(final Map<T, Long> offsets, Long initialValue) {
    return quietGet(async.incrementAll(offsets, initialValue));
  }

  @Override
  public void clearAll() {
    quietGet(async.clearAll());
  }

  @Override
  public Stats getStatistics() {
    return quietGet(async.getStatistics());
  }

  @Override
  public String getNamespace() {
    return async.getNamespace();
  }

  @Deprecated
  @Override
  public void setNamespace(String newNamespace) {
    async.setNamespace(newNamespace);
  }

  @Override
  public ErrorHandler getErrorHandler() {
    return async.getErrorHandler();
  }

  @Override
  public void setErrorHandler(ErrorHandler newHandler) {
    async.setErrorHandler(newHandler);
  }
}
