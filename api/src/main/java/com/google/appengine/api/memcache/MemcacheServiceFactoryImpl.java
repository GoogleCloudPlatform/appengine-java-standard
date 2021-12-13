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
final class MemcacheServiceFactoryImpl implements IMemcacheServiceFactory {
  @Override
  public MemcacheService getMemcacheService(String namespace) {
    return new MemcacheServiceImpl(namespace);
  }

  @Override
  public AsyncMemcacheService getAsyncMemcacheService(String namespace) {
    return new AsyncMemcacheServiceImpl(namespace);
  }

}
