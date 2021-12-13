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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.checkers.SearchApiLimits;
import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.ScorerSpec;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SortOptions}.
 *
 */
@RunWith(JUnit4.class)
public class SortOptionsTest {

  @Test
  public void testBuilder_build() throws Exception {
    SortOptions sortOptions = SortOptions.newBuilder().build();
    assertThat(sortOptions.getLimit()).isEqualTo(SearchApiLimits.SEARCH_DEFAULT_SORTED_LIMIT);
  }

  private SearchParams.Builder getBuilder() {
    SearchServicePb.IndexSpec spec =
        SearchServicePb.IndexSpec.newBuilder().setName("index").build();
    return SearchParams.newBuilder().setIndexSpec(spec).setQuery("query");
  }

  @Test
  public void testCopyToProtocolBufferSimple() throws Exception {
    SearchParams.Builder paramsBuilder = getBuilder();
    SortOptions.newBuilder().build().copyToProtocolBuffer(paramsBuilder);
    SearchParams params = paramsBuilder.build();
    assertThat(params.hasScorerSpec()).isTrue();
    SearchServicePb.ScorerSpec spec = params.getScorerSpec();
    assertThat(spec.getScorer()).isEqualTo(ScorerSpec.Scorer.MATCH_SCORER);
    assertThat(spec.hasLimit()).isTrue();
    assertThat(spec.getLimit()).isEqualTo(1000);
  }

  @Test
  public void testCopyToProtocolBuffer() throws Exception {
    SearchParams.Builder paramsBuilder = getBuilder();
    SortOptions.newBuilder()
        .setMatchScorer(RescoringMatchScorer.newBuilder())
        .addSortExpression(
            SortExpression.newBuilder()
                .setExpression("_score + (importance * .001)")
                .setDirection(SortExpression.SortDirection.ASCENDING)
                .setDefaultValueNumeric(0.0))
        .addSortExpression(
            SortExpression.newBuilder()
                .setExpression("subject")
                .setDirection(SortExpression.SortDirection.ASCENDING)
                .setDefaultValue("zzz"))
        .setLimit(97)
        .build()
        .copyToProtocolBuffer(paramsBuilder);
    SearchParams params = paramsBuilder.build();
    SearchServicePb.ScorerSpec spec = params.getScorerSpec();
    assertThat(spec.getScorer()).isEqualTo(ScorerSpec.Scorer.RESCORING_MATCH_SCORER);
    assertThat(spec.getLimit()).isEqualTo(97);
    assertThat(params.getSortSpecCount()).isEqualTo(2);
    SearchServicePb.SortSpec sort1 = params.getSortSpec(0);
    assertThat(sort1.getSortExpression()).isEqualTo("_score + (importance * .001)");
    assertThat(sort1.getSortDescending()).isFalse();
    assertThat(sort1.getDefaultValueNumeric()).isEqualTo(0.0);
    SearchServicePb.SortSpec sort2 = params.getSortSpec(1);
    assertThat(sort2.getSortExpression()).isEqualTo("subject");
    assertThat(sort2.getSortDescending()).isFalse();
    assertThat(sort2.getDefaultValueText()).isEqualTo("zzz");
  }

  @Test
  public void testLimit() throws Exception {
    SortOptions spec = SortOptions.newBuilder().setLimit(99).build();
    assertThat(spec.getLimit()).isEqualTo(99);
    assertThat(
            SortOptions.newBuilder()
                .setLimit(SearchApiLimits.SEARCH_MAXIMUM_SORTED_LIMIT)
                .build()
                .getLimit())
        .isEqualTo(SearchApiLimits.SEARCH_MAXIMUM_SORTED_LIMIT);
    assertThrows(
        IllegalArgumentException.class,
        () -> SortOptions.newBuilder().setLimit(SearchApiLimits.SEARCH_MAXIMUM_SORTED_LIMIT + 1));
  }

  @Test
  public void testToString() throws Exception {
    assertThat(
            SortOptions.newBuilder()
                .setLimit(999)
                .setMatchScorer(MatchScorer.newBuilder())
                .build()
                .toString())
        .isEqualTo("SortOptions(limit=999, matchScorer=MatchScorer(), sortExpressions=[])");
  }
}
