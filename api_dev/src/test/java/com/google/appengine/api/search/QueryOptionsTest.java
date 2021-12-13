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
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.common.base.Strings;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link QueryOptions}. */
@RunWith(JUnit4.class)
public class QueryOptionsTest {

  private QueryOptions.Builder mandatoryBuilder(int numDocs) {
    return QueryOptions.newBuilder().setLimit(numDocs);
  }

  @Test
  public void testBuilder_missingNumDocumentsToReturn() {
    assertThat(QueryOptions.newBuilder().build().getLimit())
        .isEqualTo(SearchApiLimits.SEARCH_DEFAULT_LIMIT);
  }

  @Test
  public void testSetNumberOfDocumentsToReturn_tooBig() {
    assertThat(
            QueryOptions.newBuilder()
                .setLimit(SearchApiLimits.SEARCH_MAXIMUM_LIMIT)
                .build()
                .getLimit())
        .isEqualTo(SearchApiLimits.SEARCH_MAXIMUM_LIMIT);
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setLimit(SearchApiLimits.SEARCH_MAXIMUM_LIMIT + 1));
  }

  @Test
  public void testSetNumberOfDocumentsToReturn_tooSmall() {
    assertThrows(IllegalArgumentException.class, () -> QueryOptions.newBuilder().setLimit(0));
  }

  @Test
  public void testBuild_mandatoryFilledIn() {
    QueryOptions request = mandatoryBuilder(20).build();
    assertThat(request.getLimit()).isEqualTo(20);
    assertThat(request.getCursor()).isNull();
    assertThat(request.getNumberFoundAccuracy()).isEqualTo(-1);
    assertThat(request.getFieldsToReturn()).isEmpty();
    assertThat(request.getFieldsToSnippet()).isEmpty();
    assertThat(request.getExpressionsToReturn()).isEmpty();
    assertThat(request.getSortOptions()).isNull();
  }

  @Test
  public void testSetCursor_null() {
    assertThat(QueryOptions.newBuilder().setCursor((Cursor) null).build().getCursor()).isNull();
  }

  @Test
  public void testSearchIdsOnly() {
    QueryOptions req = QueryOptions.newBuilder().setReturningIdsOnly(true).build();
    assertThat(req.isReturningIdsOnly()).isTrue();
  }

  @Test
  public void testSearchIdsOnly_expressionsNotAllowed() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QueryOptions.newBuilder()
                .setReturningIdsOnly(true)
                .addExpressionToReturn(
                    FieldExpression.newBuilder().setExpression("a + b").setName("x"))
                .build());
  }

  @Test
  public void testSearchIdsOnly_notAllowedAfterExpressions() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QueryOptions.newBuilder()
                .addExpressionToReturn(
                    FieldExpression.newBuilder().setExpression("a + b").setName("x"))
                .setReturningIdsOnly(true)
                .build());
  }

  @Test
  public void testSearchIdsOnly_fieldsNotAllowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setReturningIdsOnly(true).setFieldsToReturn("a").build());
  }

  @Test
  public void testSearchIdsOnly_notAllowedAfterFieldsToReturn() {
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setFieldsToReturn("a").setReturningIdsOnly(true).build());
  }

  @Test
  public void testSetCursor_offsetMustNotBeSet() {
    Cursor cursor = Cursor.newBuilder().build();
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setOffset(10).setCursor(cursor));
  }

  @Test
  public void testSetOffset_offsetMustNotBeNegative() {
    assertThrows(IllegalArgumentException.class, () -> QueryOptions.newBuilder().setOffset(-10));
  }

  @Test
  public void testSetOffset_cursorMustNotBeSet() {
    Cursor cursor = Cursor.newBuilder().build();
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setCursor(cursor).setOffset(1));
  }

  @Test
  public void testSetSortOptions_null() {
    assertThat(
            QueryOptions.newBuilder().setSortOptions((SortOptions) null).build().getSortOptions())
        .isNull();
  }

  @Test
  public void testSetScorer_valid() {
    QueryOptions request =
        QueryOptions.newBuilder()
            .setLimit(10)
            .setSortOptions(
                SortOptions.newBuilder()
                    .addSortExpression(
                        SortExpression.newBuilder().setExpression("name").setDefaultValue("ZZZZ"))
                    .addSortExpression(
                        SortExpression.newBuilder()
                            .setExpression("subject")
                            .setDefaultValue("ZZZZ")))
            .build();
    SortOptions sortOptions = request.getSortOptions();
    assertThat(request.getLimit()).isEqualTo(10);
    assertThat(sortOptions.getMatchScorer()).isNull();
    assertThat(sortOptions.getSortExpressions()).hasSize(2);
    SortExpression expression = sortOptions.getSortExpressions().get(0);
    assertThat(expression.getExpression()).isEqualTo("name");
    expression = sortOptions.getSortExpressions().get(1);
    assertThat(expression.getExpression()).isEqualTo("subject");
  }

  @Test
  public void testSetScorer_validScorer() {
    QueryOptions request =
        mandatoryBuilder(20)
            .setSortOptions(SortOptions.newBuilder().setMatchScorer(MatchScorer.newBuilder()))
            .build();
    SortOptions sortOptions = request.getSortOptions();
    MatchScorer scorer = sortOptions.getMatchScorer();
    assertThat(scorer).isNotNull();
  }

  @Test
  public void testSetFieldsToReturn_emptyArray() {
    QueryOptions request = mandatoryBuilder(20).setFieldsToReturn().build();
    assertThat(request.getFieldsToReturn()).isEmpty();
  }

  @Test
  public void testSetFieldsToReturn_goodNames() {
    QueryOptions request = mandatoryBuilder(20).setFieldsToReturn("name", "subject").build();
    assertThat(request.getFieldsToReturn()).hasSize(2);
    assertThat(request.getFieldsToReturn().get(0)).isEqualTo("name");
    assertThat(request.getFieldsToReturn().get(1)).isEqualTo("subject");
  }

  @Test
  public void testSetFieldsToReturn_nullString() {
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setFieldsToReturn((String) null));
  }

  @Test
  public void testSetFieldsToReturn_empty() {
    assertThrows(
        IllegalArgumentException.class, () -> QueryOptions.newBuilder().setFieldsToReturn(""));
  }

  @Test
  public void testSetFieldsToReturn_nameTooLong() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QueryOptions.newBuilder()
                .setFieldsToReturn(Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH + 1)));
  }

  @Test
  public void testSetFieldsToReturn_tooManyFields() {
    String[] fieldNames = new String[SearchApiLimits.SEARCH_MAXIMUM_NUMBER_OF_FIELDS_TO_RETURN + 1];
    for (int i = 0; i < fieldNames.length; ++i) {
      fieldNames[i] = "name" + i;
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> QueryOptions.newBuilder().setFieldsToReturn(fieldNames));
  }

  @Test
  public void testNewBuilder_allowOverride() {
    QueryOptions request = QueryOptions.newBuilder().setLimit(20).setLimit(10).build();
    assertThat(request.getLimit()).isEqualTo(10);
  }

  private QueryOptions.Builder buildAllButCursorAndOffset(FieldExpression expression) {
    return QueryOptions.newBuilder()
        .setFieldsToReturn("name", "subject")
        .setFieldsToSnippet("subject", "content")
        .addExpressionToReturn(expression)
        .setNumberFoundAccuracy(100)
        .setLimit(20)
        .setSortOptions(
            SortOptions.newBuilder()
                .setMatchScorer(RescoringMatchScorer.newBuilder())
                .addSortExpression(
                    SortExpression.newBuilder()
                        .setExpression("name")
                        .setDirection(SortExpression.SortDirection.ASCENDING)
                        .setDefaultValue("zzz"))
                .addSortExpression(
                    SortExpression.newBuilder()
                        .setExpression("subject")
                        .setDirection(SortExpression.SortDirection.ASCENDING)
                        .setDefaultValue("zzz")));
  }

  private void checkAllButCursorAndOffset(FieldExpression expression, QueryOptions request) {
    assertThat(request.getFieldsToReturn()).containsExactly("name", "subject").inOrder();
    assertThat(request.getFieldsToSnippet()).containsExactly("subject", "content").inOrder();
    assertThat(request.getExpressionsToReturn()).containsExactly(expression);
    assertThat(request.getNumberFoundAccuracy()).isEqualTo(100);
    assertThat(request.getLimit()).isEqualTo(20);

    SortOptions sortOptions = request.getSortOptions();
    sortOptions.getMatchScorer();
    assertThat(sortOptions.getLimit()).isEqualTo(SearchApiLimits.SEARCH_DEFAULT_SORTED_LIMIT);
    assertThat(sortOptions.getSortExpressions()).hasSize(2);
    SortExpression sort1 = sortOptions.getSortExpressions().get(0);
    assertThat(sort1.getExpression()).isEqualTo("name");
    assertThat(sort1.getDirection()).isEqualTo(SortExpression.SortDirection.ASCENDING);
    assertThat(sort1.getDefaultValue()).isEqualTo("zzz");
    SortExpression sort2 = sortOptions.getSortExpressions().get(1);
    assertThat(sort2.getExpression()).isEqualTo("subject");
    assertThat(sort2.getDirection()).isEqualTo(SortExpression.SortDirection.ASCENDING);
    assertThat(sort2.getDefaultValue()).isEqualTo("zzz");
  }

  @Test
  public void testNewBuilder_allOptions() {
    FieldExpression expression =
        FieldExpression.newBuilder()
            .setName("snippet")
            .setExpression("snippet(\"query\", field_to_snippet)")
            .build();
    QueryOptions withCursor =
        buildAllButCursorAndOffset(expression)
            .setCursor(Cursor.newBuilder().build("true:continueHere"))
            .build();
    checkAllButCursorAndOffset(expression, withCursor);
    Cursor cursor = withCursor.getCursor();
    assertThat(cursor.toWebSafeString()).isEqualTo("true:continueHere");
    assertThat(cursor.isPerResult()).isTrue();
    assertThat(withCursor.getOffset()).isEqualTo(0);

    QueryOptions withOffset = buildAllButCursorAndOffset(expression).setOffset(101).build();
    checkAllButCursorAndOffset(expression, withCursor);
    assertThat(withOffset.getCursor()).isNull();
    assertThat(withOffset.getOffset()).isEqualTo(101);
  }

  @Test
  public void testNewBuilderRequest() {
    QueryOptions old = QueryOptions.newBuilder().build();
    QueryOptions request = QueryOptions.newBuilder(old).build();
    assertThat(request.getCursor()).isNull();
    assertThat(request.getFieldsToReturn()).isEmpty();
    assertThat(request.getLimit()).isEqualTo(SearchApiLimits.SEARCH_DEFAULT_LIMIT);
    assertThat(request.getNumberFoundAccuracy())
        .isEqualTo(SearchApiLimits.SEARCH_DEFAULT_NUMBER_FOUND_ACCURACY);
    assertThat(request.getOffset()).isEqualTo(0);
  }

  @Test
  public void testCopyToProtocolBuffer() {
    FieldExpression expression =
        FieldExpression.newBuilder()
            .setName("snippet")
            .setExpression("snippet(\"query here\", field_to_snippet)")
            .build();
    FieldExpression.Builder expressionBuilder =
        FieldExpression.newBuilder()
            .setName("snippet2")
            .setExpression("snippet(\"query here\", field_to_snippet2)");
    SearchParams.Builder params = SearchParams.newBuilder();
    Cursor cursor = Cursor.newBuilder().build("true:continueHere");
    params =
        QueryOptions.newBuilder()
            .setCursor(cursor)
            .setFieldsToReturn("name", "subject")
            .setFieldsToSnippet("subject", "content")
            .addExpressionToReturn(expression)
            .addExpressionToReturn(expressionBuilder)
            .setNumberFoundAccuracy(100)
            .setLimit(19)
            .setSortOptions(
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
                    .setLimit(97))
            .build()
            .copyToProtocolBuffer(params, "query");

    assertThat(params.getCursorType()).isEqualTo(SearchParams.CursorType.PER_RESULT);
    assertThat(params.getCursor()).isEqualTo("continueHere");

    assertThat(params.getFieldSpec().getNameList()).containsExactly("name", "subject").inOrder();
    List<SearchServicePb.FieldSpec.Expression> expressions =
        params.getFieldSpec().getExpressionList();

    assertThat(expressions).hasSize(4);
    SearchServicePb.FieldSpec.Expression exprPb = expressions.get(0);
    assertThat(exprPb.getName()).isEqualTo("subject");
    assertThat(exprPb.getExpression()).isEqualTo("snippet(\"query\", subject)");
    exprPb = expressions.get(1);
    assertThat(exprPb.getName()).isEqualTo("content");
    assertThat(exprPb.getExpression()).isEqualTo("snippet(\"query\", content)");
    exprPb = expressions.get(2);
    assertThat(exprPb.getName()).isEqualTo("snippet");
    assertThat(exprPb.getExpression()).isEqualTo("snippet(\"query here\", field_to_snippet)");
    exprPb = expressions.get(3);
    assertThat(exprPb.getName()).isEqualTo("snippet2");
    assertThat(exprPb.getExpression()).isEqualTo("snippet(\"query here\", field_to_snippet2)");
    assertThat(params.getMatchedCountAccuracy()).isEqualTo(100);
    assertThat(params.getLimit()).isEqualTo(19);

    SearchServicePb.ScorerSpec spec = params.getScorerSpec();
    assertThat(spec.getScorer())
        .isEqualTo(SearchServicePb.ScorerSpec.Scorer.RESCORING_MATCH_SCORER);
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

    // No docs to return set, nor a cursor type.
    params = SearchParams.newBuilder();
    params = QueryOptions.newBuilder().build().copyToProtocolBuffer(params, "query");
    assertThat(params.getLimit()).isEqualTo(SearchApiLimits.SEARCH_DEFAULT_LIMIT);
    assertThat(params.getCursorType()).isEqualTo(SearchParams.CursorType.NONE);
  }

  @Test
  public void testCopyToProtocolBuffer_defaultCursor() {
    Cursor cursor = Cursor.newBuilder().build();
    SearchParams.Builder params = SearchParams.newBuilder();
    params =
        QueryOptions.newBuilder().setCursor(cursor).build().copyToProtocolBuffer(params, "query");
    assertThat(params.getCursorType()).isEqualTo(SearchParams.CursorType.SINGLE);
    assertThat(params.hasCursor()).isFalse();
  }

  @Test
  public void testCopyToProtocolBuffer_defaultNumberFoundAccuracy() {
    SearchParams.Builder params;

    params =
        QueryOptions.newBuilder()
            .setNumberFoundAccuracy(50)
            .clearNumberFoundAccuracy()
            .build()
            .copyToProtocolBuffer(SearchParams.newBuilder(), "query");
    assertThat(params.hasMatchedCountAccuracy()).isFalse();

    params =
        QueryOptions.newBuilder().build().copyToProtocolBuffer(SearchParams.newBuilder(), "query");
    assertThat(params.hasMatchedCountAccuracy()).isFalse();
  }

  @Test
  public void testToString() {
    assertThat(QueryOptions.newBuilder().build().toString()).isEqualTo("QueryOptions(limit=20)");
    assertThat(QueryOptions.newBuilder().setNumberFoundAccuracy(100).build().toString())
        .isEqualTo("QueryOptions(limit=20, numberFoundAccuracy=100)");

    assertThat(QueryOptions.newBuilder().setReturningIdsOnly(true).build().toString())
        .isEqualTo("QueryOptions(limit=20, IDsOnly=true)");

    assertThat(QueryOptions.newBuilder().setFieldsToReturn("field").build().toString())
        .isEqualTo("QueryOptions(limit=20, fieldsToReturn=[field])");
  }
}
