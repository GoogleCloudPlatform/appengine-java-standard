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

/**
 * A factory that creates default implementation of {@link SearchService}.
 *
 */
public interface ISearchServiceFactory {

  /**
   * Returns an instance of the {@link SearchService}.  The instance
   * will exist in the user provided namespace. The namespace must be
   * valid, as per {@link com.google.appengine.api.NamespaceManager#validateNamespace(String)}
   * method. Equivalent to
   * <code>
   * getSearchService(SearchServiceConfig.newBuilder().setNamespace(namespace).build())
   * </code>
   *
   * @param namespace a namespace to be assigned to the returned
   * search service.
   * @return the default implementation of {@link SearchService}.
   * @throws IllegalArgumentException if the namespace is invalid
   * @deprecated Use {@link ISearchServiceFactory#getSearchService(SearchServiceConfig)}
   */
  @Deprecated
  SearchService getSearchService(String namespace);

  /**
   * Returns an instance of the {@link SearchService} with the given config.
   *
   * @param config a {@link SearchServiceConfig} instance that describes the
   *   requested search service. If no namespace provided in config,
   *   NamespaceManager.get() will be used.
   * will be used.
   * @return the default implementation of {@link SearchService}.
   */
  SearchService getSearchService(SearchServiceConfig config);
}
