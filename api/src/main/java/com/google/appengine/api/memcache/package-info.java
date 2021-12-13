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

/**
 * Provides fast but unreliable data storage, also accessible via a <a
 * href="http://jcp.org/en/jsr/detail?id=107">JCache</a> interface. Objects may be stored in the
 * cache with an explicit expiration time, but may also be evicted before that expiration to make
 * room for newer, more active entries.
 *
 * <p>The cache is accessed via a {@link MemcacheService} object, obtained from the {@link
 * MemcacheServiceFactory}. It offers the cache as a map from key {@link Object} to value {@link
 * Object}.
 *
 * <p>In the Development Server, the system property {@code memcache.maxsize} can be set to limit
 * the available cache, taking values like "100M" (the default), "10K", or "768" (bytes).
 *
 * <p>Because the cache offers best-effort data storage, by default most errors are treated as a
 * cache miss. More explicit error handling can be installed via {@link
 * MemcacheService#setErrorHandler(ErrorHandler)}.
 *
 * @see com.google.appengine.api.memcache.MemcacheService
 * @see <a href="http://cloud.google.com/appengine/docs/java/memcache/">The Memcache Java API in the
 *     <em>Google App Engine Developer's Guide</em></a>.
 * @see <a href="http://jcp.org/en/jsr/detail?id=107">JCache API</a>
 */
package com.google.appengine.api.memcache;
