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

import com.google.auto.service.AutoService;
import java.util.Map;
import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheFactory;

/**
 * JCache CacheFactory implementation using Memcache.
 *
 */
@AutoService(CacheFactory.class)
public class GCacheFactory implements CacheFactory {

  private static final String PREFIX = "com.google.appengine.api.memcache.jsr107cache.";

  /** Property key for expiration time in seconds set for all put operations as an Integer. */
  public static final String EXPIRATION_DELTA = PREFIX + "EXPIRATION_DELTA";

  /** Property key for expiration time in milliseconds set for all put operations as an Integer. */
  public static final String EXPIRATION_DELTA_MILLIS = PREFIX + "EXPIRATION_DELTA_MILLIS";

  /** Property key for absolute expiration time for all put operations as a {@link Date}. */
  public static final String EXPIRATION = PREFIX + "EXPIRATION";

  /**
   * Property key for put operation policy as a {@link
   * com.google.appengine.api.memcache.MemcacheService.SetPolicy}. Defaults to {@link
   * SetPolicy.SET_ALWAYS} if not specified.
   */
  public static final String SET_POLICY = PREFIX + "SET_POLICY";

  /**
   * Property key for memcache service to use as a {@link
   * com.google.appengine.api.memcache.MemcacheService}. Defaults to that provided by {@link
   * MemcacheServiceFactory.getMemcacheService} if not specified.
   */
  public static final String MEMCACHE_SERVICE = PREFIX + "MEMCACHE_SERVICE";

  /**
   * Property key for defining a non-default namespace. This has the same effect setting a namespace
   * using {@link
   * com.google.appengine.api.memcache.MemcacheServiceFactory#getMemcacheService(String)}. This
   * property is ignored if MEMCACHE_SERVICE property specified.
   */
  public static final String NAMESPACE = PREFIX + "NAMESPACE";

  /**
   * Property key that determines whether to throw a {@link GCacheException} if a put method fails.
   * The value should be a boolean value, and defaults to {@code false}. If you set this to true,
   * you should be prepared to catch any {@link GCacheException} thrown from {@code put} or {@code
   * putAll} as this may happen sporadically or during scheduled maintenance.
   */
  public static final String THROW_ON_PUT_FAILURE = PREFIX + "THROW_ON_PUT_FAILURE";

  /**
   * Creates a cache instance using the memcache service.
   *
   * @param map A map of properties.
   * @return a cache.
   */
  @SuppressWarnings("rawtypes") // raw type inherited from parent, which we can't fix
  @Override
  public Cache createCache(Map map) {
    return new GCache(map);
  }
}
