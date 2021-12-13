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
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;

/**
 * Scorer which orders documents using lucene build in Sort functionality.
 *
 */
public class SimpleScorer extends Scorer {
  private final Sort sort;

  // TODO: scorer limit is ignored
  public SimpleScorer(Sort sort) {
    this.sort = sort;
  }

  static Sort naturalOrder() {
    return new Sort(
        new SortField(LuceneUtils.ORDER_ID_FIELD_NAME, SortField.INT, true));
  }

  /**
   * Result class specific for SimpleScorer. It does not keep any additional
   * information.
   */
  public class Result extends Scorer.Result {
    public Result(Document doc) {
      super(doc);
    }

    @Override
    public void addScores(SearchServicePb.SearchResult.Builder resultBuilder) {}
  }

  @Override
  public SearchResults search(
      IndexSearcher indexSearcher,
      Query q,
      int offset,
      int limit) throws IOException {
    int docsToFind = offset + limit;
    TopDocs topDocs = indexSearcher.search(q, null, docsToFind, sort);
    int max = Math.min(topDocs.scoreDocs.length, docsToFind);

    if (max < offset) {
      return new SearchResults(new Result[0], 0);
    }

    Result[] resultsArray = new Result[max - offset];
    for (int i = offset; i < max; i++) {
      resultsArray[i - offset] = new Result(indexSearcher.doc(topDocs.scoreDocs[i].doc));
    }

    return new SearchResults(resultsArray, topDocs.totalHits);
  }
}
