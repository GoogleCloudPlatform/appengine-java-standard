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

import com.google.appengine.api.NamespaceManager;

/**
 * An factory that creates default implementation of {@link SearchService}.
 *
 */
final class SearchServiceFactoryImpl implements ISearchServiceFactory {

  static SearchApiHelper apiHelper = new SearchApiHelper();

  /**
   * Returns an instance of the {@link SearchService}.  The instance
   * will exist either in the namespace set on the {@link
   * NamespaceManager}, or, if none was set, in an empty namespace.
   *
   * @return the default implementation of {@link SearchService}.
   *
   * @VisibleForTesting
   */
  static SearchService getSearchService(SearchApiHelper helper) {
    return new SearchServiceImpl(helper == null ? apiHelper : helper,
        SearchServiceConfig.newBuilder().setNamespace(NamespaceManager.get()).build());
  }

  @Override
  public final SearchService getSearchService(String namespace) {
    return getSearchService(SearchServiceConfig.newBuilder().setNamespace(namespace).build());
  }

  @Override
  public SearchService getSearchService(SearchServiceConfig config) {
    return new SearchServiceImpl(apiHelper, config);
  }
}
