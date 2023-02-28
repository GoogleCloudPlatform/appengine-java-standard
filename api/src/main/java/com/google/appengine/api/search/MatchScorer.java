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

import com.google.appengine.api.search.proto.SearchServicePb;

/**
 * Assigns a document score based on frequency of terms in TextFields and HTMLFields.
 *
 * <p>If you add a MatchScorer to a SortOptions as in the following code:
 *
 * <pre>{@code
 *  SortOptions sortOptions = SortOptions.newBuilder()
 *      .setMatchScorer(MatchScorer.newBuilder())
 *      .build();
 * }</pre>
 *
 * then this will sort the documents in descending score order. The scores will be positive. If you
 * want to sort in ascending order, then use the following code:
 *
 * <pre>{@code
 *   SortOptions sortOptions = SortOptions.newBuilder()
 *       .setMatchScorer(MatchScorer.newBuilder())
 *       .addSortExpression(
 *           SortExpression.newBuilder()
 *               .setExpression(SortExpression.SCORE_FIELD_NAME)
 *               .setDirection(SortExpression.SortDirection.ASCENDING)
 *               .setDefaultValueNumeric(0.0))
 *       .build();
 * }</pre>
 *
 * In this example, the score will be negative.
 *
 */
public class MatchScorer {

  /**
   * A builder that constructs {@link MatchScorer MatchScorers}.
   * A MatchScorer will invoke a scorer on each search result. The
   * following code illustrates building a match scorer to score documents:
   * <p>
   * <pre>{@code
   *   MatchScorer scorer = MatchScorer.newBuilder().build();
   * }</pre>
   */
  public static class Builder {

    Builder() {
    }

    /**
     * Builds a {@link MatchScorer} from the set values.
     *
     * @return a {@link MatchScorer} built from the set values
     */
    public MatchScorer build() {
      return new MatchScorer(this);
    }
  }

  /**
   * Constructs a text sort specification using the values from the
   * {@link Builder}.
   */
  MatchScorer(Builder builder) {
  }

  /**
   * Copies the contents of the MatchScorer into a scorer
   * spec protocol buffer.
   *
   * @return the protocol buffer builder with the contents of the MatchScorer
   * scoring information
   */
  SearchServicePb.ScorerSpec.Builder copyToScorerSpecProtocolBuffer() {
    SearchServicePb.ScorerSpec.Builder builder = SearchServicePb.ScorerSpec.newBuilder();
    builder.setScorer(SearchServicePb.ScorerSpec.Scorer.MATCH_SCORER);
    return builder;
  }

  /**
   * Creates and returns a MatchScorer Builder.
   *
   * @return a new {@link MatchScorer.Builder}. Set the parameters for scorer
   * on the Builder, and use the {@link Builder#build()} method
   * to create a concrete instance of MatchScorer
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "MatchScorer()";
  }
}
