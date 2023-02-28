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
import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * An factory that creates default implementation of {@link SearchService}.
 *
 * <pre>{@code
 *   SearchService search = SearchServiceFactory.getSearchService();
 * }</pre>
 *
 * Optionally, you may pass a {@link SearchServiceConfig} instance to customize
 * the search service. e.g, setting deadline and namespace:
 *
 * <pre>{@code
 *   SearchServiceFactory.getSearchService(
 *       SearchServiceConfig.newBuilder().setDeadline(10.0).setNamespace("acme").build())
 * }</pre>
 *
 */
public final class SearchServiceFactory {

  /**
   * Returns an instance of the {@link SearchService}.  The instance
   * will exist in the user provided namespace. The namespace must be
   * valid, as per {@link NamespaceManager#validateNamespace(String)}
   * method.
   *
   * @param namespace a namespace to be assigned to the returned
   * search service.
   * @return the default implementation of {@link SearchService}.
   * @throws IllegalArgumentException if the namespace is invalid
   * @deprecated Use {@link SearchServiceFactory#getSearchService(SearchServiceConfig)}
   */
  @Deprecated
  public static SearchService getSearchService(String namespace) {
    return getFactory().getSearchService(namespace);
  }

  /**
   * Returns an instance of the {@link SearchService} with the given config.
   *
   * @param config a {@link SearchServiceConfig} instance that describes the
   *   requested search service. If no namespace provided in config,
   *   NamespaceManager.get() will be used.
   * @return the default implementation of {@link SearchService}.
   */
  public static SearchService getSearchService(SearchServiceConfig config) {
    return getFactory().getSearchService(config);
  }

  /**
   * Equivalent to
   * {@link SearchServiceFactory#getSearchService(SearchServiceConfig)
   *   getSearchService(SearchServiceConfig.newBuilder().build())}.
   */
  public static SearchService getSearchService() {
    return getSearchService(SearchServiceConfig.newBuilder().build());
  }

  /**
   * No instances of this class may be created.
   */
  private SearchServiceFactory() {}

  private static ISearchServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(ISearchServiceFactory.class);
  }
}
