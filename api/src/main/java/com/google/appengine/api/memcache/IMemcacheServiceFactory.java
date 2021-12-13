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

/**
 * The factory by which users acquire a handle to the MemcacheService.
 *
 */
public interface IMemcacheServiceFactory {
  /**
   * Gets a handle to the cache service, forcing use of specific namespace.
   *
   * <p>Although there is only one actual cache, an application may make as many
   * {@code MemcacheService} instances as it finds convenient. If using multiple instances, note
   * that the error handler established with {@link MemcacheService#setErrorHandler(ErrorHandler)}
   * is specific to each instance.
   *
   * @param namespace if not {@code null} forces the use of {@code namespace}
   * for all operations in {@code MemcacheService} . If {@code namespace} is
   * {@code null} - created {@code MemcacheService} will use current namespace
   * provided by {@link com.google.appengine.api.NamespaceManager#get()}.
   *
   * @return a new {@code MemcacheService} instance.
   */
   MemcacheService getMemcacheService(String namespace);

  /**
   * Similar to {@link #getMemcacheService(String)} but returns a handle to an
   * asynchronous version of the cache service.
   */
   AsyncMemcacheService getAsyncMemcacheService(String namespace);

}
