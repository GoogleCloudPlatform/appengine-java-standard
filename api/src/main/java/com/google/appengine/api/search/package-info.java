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
 * Provides a service for indexing documents and retrieving them using search queries. This is a
 * low-level API that allows users to directly create {@link Document}s which can be indexed and
 * retrieved with the help of {@link Index}.
 *
 * <p>A {@link Document} is a collection of {@link Field}s. Each field is a named and typed value. A
 * document is uniquely identified by its ID and may contain zero or more fields. A field with a
 * given name can have multiple occurrences. Once documents are put into the {@link Index}, they can
 * be retrieved via search queries. Typically, a program creates an index. This operation does
 * nothing if the index was already created. Next, a number of documents are inserted into the
 * index. Finally, index is searched and matching documents, or their snippets are returned to the
 * user.
 *
 * <pre>{@code
 * public List&lt;ScoredDocument&gt; indexAndSearch(
 *     String query, Document... documents) {
 *     SearchService searchService = SearchServiceFactory.getSearchService();
 *     Index index = searchService.getIndex(
 *         IndexSpec.newBuilder().setIndexName("indexName"));
 *     for (Document document : documents) {
 *       PutResponse response = index.put(document);
 *       assert response.getResults().get(0).getCode().equals(StatusCode.OK);
 *     }
 *     Results&lt;ScoredDocument&gt; results =
 *         index.search(Query.newBuilder().build(query));
 *     List&lt;ScoredDocument&gt; matched = new ArrayList&lt;ScoredDocument&gt;(
 *         results.getNumberReturned());
 *     for (ScoredDocument result : results) {
 *       matched.add(result);
 *     }
 *     return matched;
 * }
 * }</pre>
 *
 * @see com.google.appengine.api.search.SearchServiceFactory
 */
package com.google.appengine.api.search;
