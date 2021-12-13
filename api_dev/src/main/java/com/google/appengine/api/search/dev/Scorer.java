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

package com.google.appengine.api.search.dev;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.FacetResult;
import com.google.apphosting.api.search.DocumentPb;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

/**
 * Abstract scorer class.
 */
public abstract class Scorer {

  /**
   * Abstract scorer result.
   */
  public abstract static class Result {
    public final Document doc;

    public Result(Document doc) {
      this.doc = doc;
    }

    public abstract void addScores(SearchServicePb.SearchResult.Builder resultBuilder);
  }

  /**
   * Simple container class for result list and totalHits value.
   */
  public static class SearchResults {
    public final Result[] results;
    public int totalHits;
    public final FacetResult[] facetResults;


    SearchResults(Result[] results, int totalHits) {
      this(results, totalHits, new FacetResult[0]);
    }

    SearchResults(Result[] results, int totalHits, FacetResult[] facetResults) {
      this.results = results;
      this.totalHits = totalHits;
      this.facetResults = facetResults;
    }
  }

  public abstract SearchResults search(
      IndexSearcher indexSearcher,
      Query q,
      int offset,
      int limit) throws IOException;

  public static Scorer newInstance(
      final SearchServicePb.SearchParams searchParams,
      Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes) {
    final Scorer scorer = newInstanceInternal(searchParams, fieldTypes);
    if (!SimpleFacet.isFacetingRequested(searchParams)) {
      return scorer;
    }
    return new Scorer() {
      @Override
      public SearchResults search(IndexSearcher indexSearcher, Query q, int offset, int limit)
          throws IOException {
          SearchResults normalResult = scorer.search(indexSearcher, q, offset, limit);
          SearchResults extendedResults = scorer.search(
              indexSearcher, q, 0, searchParams.getFacetDepth());
          return new SearchResults(
              normalResult.results, normalResult.totalHits,
              SimpleFacet.getFacetResult(searchParams, extendedResults.results));
      }
    };
  }

  private static Scorer newInstanceInternal(
      SearchServicePb.SearchParams searchParams,
      Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes) {
    if (searchParams.getSortSpecCount() != 0) {
      return GenericScorer.newInstance(searchParams, fieldTypes);
    }
    if (!searchParams.hasScorerSpec()) {
      return new SimpleScorer(SimpleScorer.naturalOrder());
    }
    return MatchScorer.newInstance(searchParams, fieldTypes);
  }
}
