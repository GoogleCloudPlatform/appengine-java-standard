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

import com.google.appengine.api.NamespaceManager;

/**
 * State and behavior that is common to both synchronous and asynchronous
 * MemcacheService API implementations.
 *
 */
abstract class BaseMemcacheServiceImpl implements BaseMemcacheService {

  /**
   * Default handler just logs errors at INFO.
   */
  private volatile ErrorHandler handler = ErrorHandlers.getDefault();

  /**
   * If the namespace is not null it overrides current namespace on all API
   * calls. The current namespace is defined as the one returned by
   * {@link NamespaceManager#get()}.
   */
  private String namespace;

  BaseMemcacheServiceImpl(String namespace) {
    if (namespace != null) {
      NamespaceManager.validateNamespace(namespace);
    }
    this.namespace = namespace;
  }

  @Override
  public ErrorHandler getErrorHandler() {
    return handler;
  }

  @Override
  public void setErrorHandler(ErrorHandler newHandler) {
    if (newHandler == null) {
      throw new NullPointerException("ErrorHandler must not be null");
    }
    handler = newHandler;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  void setNamespace(String newNamespace) {
    // N.B.: For backwards compatibility reasons, we cannot
    // do any validation of the namespace here.
    namespace = newNamespace;
  }

  /**
   * Returns namespace which is about to be used by API call. By default it is
   * the value returned by {@link NamespaceManager#get()} with the exception
   * that {@code null} is substituted with "" (empty string).
   * If the {@link #namespace} is not null it overrides the default value.
   */
  protected String getEffectiveNamespace() {
    if (namespace != null) {
      return namespace;
    }
    String namespace1 = NamespaceManager.get();
    return namespace1 == null ? "" : namespace1;
  }
}
