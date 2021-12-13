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
import com.google.apphosting.api.search.DocumentPb;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 * A scorer that returns a score based on term frequency divided by document
 * frequency.
 *
 */
public class MatchScorer extends Scorer {
  public MatchScorer() {}

  public static Scorer newInstance(SearchServicePb.SearchParams searchParams,
      Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes) {
    // TODO: limit and match_scorer_parameters are ignored
    return new MatchScorer();
  }

  /** Result class for MatchScorer. Keeps lucene score for a document. */
  public static class Result extends Scorer.Result {
    public float score;

    Result(Document doc, float score) {
      super(doc);
      this.score = score;
    }

    @Override
    public void addScores(SearchServicePb.SearchResult.Builder resultBuilder) {
      resultBuilder.addScore(score);
    }
  }

  @Override
  public SearchResults search(IndexSearcher indexSearcher, Query q, int offset, int limit)
      throws IOException {
    TopDocs topDocs = indexSearcher.search(q, null, offset + limit);

    int numResults = Math.max(0, topDocs.scoreDocs.length - offset);
    Result[] results = new Result[numResults];
    ScoreDoc[] scoreDocs = topDocs.scoreDocs;

    for (int i = 0; i < numResults; i++) {
      ScoreDoc scoreDoc = scoreDocs[i + offset];
      results[i] = new Result(indexSearcher.doc(scoreDoc.doc), scoreDoc.score);
    }

    return new SearchResults(results, topDocs.totalHits);
  }
}
