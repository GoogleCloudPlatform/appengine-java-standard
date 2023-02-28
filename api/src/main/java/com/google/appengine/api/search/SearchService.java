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

import java.util.concurrent.Future;

/**
 * The SearchService is used to get available indexes, which can be
 * queried about their metadata or have index/delete/search operations
 * performed on them.  For example:
 *
 * <pre>{@code
 * SearchService searchService = SearchServiceFactory.getSearchService();
 * GetResponse<Index> response = searchService.getIndexes(
 *     GetIndexesRequest.newBuilder());
 * for (Index index : response) {
 *   index.getName();
 *   index.getNamespace();
 *   index.search("query");
 * }
 * }</pre>
 *
 * SearchService is also responsible for creating new indexes.  For
 * example:
 *
 * <pre>{@code
 * SearchService searchService = SearchServiceFactory.getSearchService();
 * Index index = searchService.getIndex(IndexSpec.newBuilder().setName("myindex"));
 * }</pre>
 *
 */
public interface SearchService {

  /**
   * Returns an instance of {@link Index} corresponding to the provided
   * specification.
   *
   * @return an instance of {@link Index} corresponding to the given
   * {@code spec}
   */
  public Index getIndex(IndexSpec spec);

  /**
   * Returns an instance of {@link Index} corresponding to the
   * specification built from the given {@code builder}.
   *
   * @return an instance of {@link Index} corresponding to the given
   * {@code spec}
   */
  public Index getIndex(IndexSpec.Builder builder);

  /**
   * Returns the namespace associated with this search service. Each
   * service instance is assigned one namespace and all operations,
   * such as listing documents, indexes inherit it. Also, when you
   * get an index, the namespace of this service is passed to the
   * returned index.
   *
   * @return the namespace associated with this search service.
   */
  public String getNamespace();

  /**
   * Gets the indexes specified. The following code fragment shows how
   * to get the schemas of each available {@link Index}.
   *
   * <pre>{@code
   *   // Get the SearchService for the default namespace
   *   SearchService searchService = SearchServiceFactory.newSearchService();
   *
   *   // Get the first page of indexes available and retrieve schemas
   *   GetResponse<Index> response = searchService.getIndexes(
   *       GetIndexesRequest.newBuilder().setSchemaFetched(true).build());
   *
   *   // List out elements of Schema
   *   for (Index index : response) {
   *     String name = index.getName();
   *     Schema schema = index.getSchema();
   *     for (String fieldName : schema.getFieldNames()) {
   *        List<FieldType> typesForField = schema.getFieldTypes(fieldName);
   *     }
   *   }
   * }</pre>
   *
   * @param request a request specifying which indexes to get
   * @return a {@link GetResponse}{@code <Index>} containing a list of existing indexes
   * @throws GetException if there is a failure in the search service
   * getting indexes
   */
  public GetResponse<Index> getIndexes(GetIndexesRequest request);

  /**
   * Gets the indexes specified in the request built from the {@code builder}.
   *
   * @param builder a builder to be used to construct a {@link GetIndexesRequest}
   *   specifying which indexes to get
   * @return a {@link GetResponse}{@code <Index>} containing a list of existing indexes
   */
  public GetResponse<Index> getIndexes(GetIndexesRequest.Builder builder);

  /**
   * Gets the indexes requested asynchronously.
   *
   * @param request a request specifying which indexes to get
   * @return a {@link Future} that will allow getting a
   * {@link GetResponse}{@code <Index>} containing a list of existing indexes
   */
  public Future<GetResponse<Index>> getIndexesAsync(GetIndexesRequest request);

  /**
   * Gets the indexes asynchronously for those specified in the request built from
   * the {@code builder}.
   *
   * @param builder a builder to be used to construct a {@link GetIndexesRequest}
   *   specifying which indexes to get
   * @return a {@link Future} that will allow getting a
   * {@link GetResponse}{@code <Index>} containing a list of existing indexes
   */
  public Future<GetResponse<Index>> getIndexesAsync(GetIndexesRequest.Builder builder);
}
