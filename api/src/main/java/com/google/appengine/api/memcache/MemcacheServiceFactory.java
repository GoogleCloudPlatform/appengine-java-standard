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

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * The factory by which users acquire a handle to the MemcacheService.
 *
 */
public class MemcacheServiceFactory {
  /**
   * Gets a handle to the cache service.  Although there is only one actual
   * cache, an application may make as many {@code MemcacheService} instances
   * as it finds convenient.
   * <p>
   * If using multiple instances, note that the error handler established with
   * {@link MemcacheService#setErrorHandler(ErrorHandler)} is specific to each
   * instance.
   *
   * All operations in the {@code MemcacheService} will use the current
   * namespace provided by
   * {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return a new {@code MemcacheService} instance.
   */
  public static MemcacheService getMemcacheService() {
    return getMemcacheService(null);
  }

  /**
   * Gets a handle to the cache service, forcing use of specific namespace.
   * The method returns {@code MemcacheService}
   * similar to the one returned by
   * {@link MemcacheServiceFactory#getMemcacheService()}
   * but it will use specified {@code namespace} for all operations.
   *
   * @param namespace if not {@code null} forces the use of {@code namespace}
   * for all operations in {@code MemcacheService} . If {@code namespace} is
   * {@code null} - created {@code MemcacheService} will use current namespace
   * provided by {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return a new {@code MemcacheService} instance.
   */
  public static MemcacheService getMemcacheService(String namespace) {
    return getFactory().getMemcacheService(namespace);
  }

  /**
   * Similar to {@link #getMemcacheService()} but returns a handle to an
   * asynchronous version of the cache service.
   */
  public static AsyncMemcacheService getAsyncMemcacheService() {
    return getAsyncMemcacheService(null);
  }

  /**
   * Similar to {@link #getMemcacheService(String)} but returns a handle to an
   * asynchronous version of the cache service.
   */
  public static AsyncMemcacheService getAsyncMemcacheService(String namespace) {
    return getFactory().getAsyncMemcacheService(namespace);
  }

  private MemcacheServiceFactory() {
    // non-instantiable
  }

  private static IMemcacheServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(IMemcacheServiceFactory.class);
  }
}
