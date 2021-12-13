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

package com.google.appengine.api.search;

import com.google.apphosting.api.AppEngineInternal;

/**
 * Builds {@link SearchService} instances that are pinned to a specific application and namespace
 * regardless of the "current" appId provided by {@code ApiProxy.getCurrentEnvironment().getAppId()}
 * and the "current" namespace provided by {@code NamespaceManager.get()}.
 * <p>
 * Note: users should not access this class directly.
 */
@AppEngineInternal
public final class AdminSearchServiceFactory {

  /**
   * Returns a {@link SearchService} that is pinned to a specific application and namespace. This
   * implementation ignores the "current" appId provided by
   * {@code ApiProxy.getCurrentEnvironment().getAppId()} and the "current" namespace provided by
   * {@code NamespaceManager.get()}.
   */
  public SearchService getSearchService(final String appId, SearchServiceConfig config) {
    if (appId == null) {
      throw new IllegalArgumentException();
    }
    if (config.getNamespace() == null) {
      throw new IllegalArgumentException();
    }
    SearchApiHelper helper = new SearchApiHelper(appId);
    return new SearchServiceImpl(helper, config);
  }
}
