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

import java.util.Map;
import javax.cache.Cache;
import javax.cache.CacheFactory;

/**
 * JCache CacheFactory implementation using Memcache.
 *
 */
public class GCacheFactory implements CacheFactory {

  /** Property key for expiration time in seconds set for all put operations as an Integer. */
  public static final int EXPIRATION_DELTA = 0;

  /** Property key for expiration time in milliseconds set for all put operations as an Integer. */
  public static final int EXPIRATION_DELTA_MILLIS = 1;

  /** Property key for absolute expiration time for all put operations as a {@link Date}. */
  public static final int EXPIRATION = 2;

  /**
   * Property key for put operation policy as a {@link
   * com.google.appengine.api.memcache.MemcacheService.SetPolicy}. Defaults to {@link
   * SetPolicy.SET_ALWAYS} if not specified.
   */
  public static final int SET_POLICY = 3;

  /**
   * Property key for memcache service to use as a {@link
   * com.google.appengine.api.memcache.MemcacheService}. Defaults to that provided by {@link
   * MemcacheServiceFactory.getMemcacheService} if not specified.
   */
  public static final int MEMCACHE_SERVICE = 4;

  /**
   * Property key for defining a non-default namespace. This has the same effect setting a namespace
   * using {@link
   * com.google.appengine.api.memcache.MemcacheServiceFactory#getMemcacheService(String)}. This
   * property is ignored if MEMCACHE_SERVICE property specified.
   */
  public static final int NAMESPACE = 5;

  /**
   * Creates a cache instance using the memcache service.
   *
   * @param map A map of properties.
   * @return a cache.
   */
  @SuppressWarnings("rawtypes")
  @Override
  public Cache createCache(Map map) {
    return new GCache(map);
  }
}
