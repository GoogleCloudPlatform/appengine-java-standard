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
 * Assigns a document score based on term frequency weighted on document parts.
 *
 * If you add a MatchScorer to a SortOptions as in the following code:
 * <pre>{@code
 *  SortOptions sortOptions = SortOptions.newBuilder()
 *      .setMatchScorer(RescoringMatchScorer.newBuilder())
 *      .build();
 * }</pre>
 * then this will sort the documents in descending score order. The scores will be
 * positive. If you want to sort in ascending order, then use the following code:
 * <pre>{@code
 *   SortOptions sortOptions = SortOptions.newBuilder()
 *       .setMatchScorer(RescoringMatchScorer.newBuilder())
 *       .addSortExpression(
 *           SortExpression.newBuilder()
 *               .setExpression(SortExpression.SCORE_FIELD_NAME)
 *               .setDirection(SortExpression.SortDirection.ASCENDING)
 *               .setDefaultValueNumeric(0.0))
 *       .build();
 * }</pre>
 * The scores in this case will be negative.
 *
 */
public final class RescoringMatchScorer extends MatchScorer {

  /**
   * A builder that constructs {@link RescoringMatchScorer RescoringMatchScorers}.
   * A RescoringMatchScorer will invoke a scorer on each search result. The
   * following code illustrates building a rescoring match scorer to score documents:
   * <p>
   * <pre>
   *   RescoringMatchScorer scorer = RescoringMatchScorer.newBuilder().build();
   * </pre>
   */
  public static final class Builder extends MatchScorer.Builder {

    private Builder() {
    }

    /**
     * Builds a {@link RescoringMatchScorer} from the set values.
     *
     * @return a {@link RescoringMatchScorer} built from the set values
     */
    @Override
    public RescoringMatchScorer build() {
      return new RescoringMatchScorer();
    }
  }

  /** Constructs a text sort specification using the values from the {@link Builder}. */
  private RescoringMatchScorer() {
    super(MatchScorer.newBuilder());
  }

  /**
   * Copies the contents of the RescoringMatchScorer into a scorer
   * spec protocol buffer.
   *
   * @return the protocol buffer builder with the contents of the
   * RescoringMatchScorer scoring information
   */
  @Override
  SearchServicePb.ScorerSpec.Builder copyToScorerSpecProtocolBuffer() {
    SearchServicePb.ScorerSpec.Builder builder = SearchServicePb.ScorerSpec.newBuilder();
    builder.setScorer(SearchServicePb.ScorerSpec.Scorer.RESCORING_MATCH_SCORER);
    return builder;
  }

  /**
   * Creates and returns a RescoringMatchScorer Builder.
   *
   * @return a new {@link RescoringMatchScorer.Builder}. Set the parameters for scorer
   * on the Builder, and use the {@link Builder#build()} method
   * to create a concrete instance of RescoringMatchScorer
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "RescoringMatchScorer()";
  }
}
