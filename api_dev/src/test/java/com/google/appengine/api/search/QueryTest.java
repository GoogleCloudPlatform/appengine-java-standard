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
import java.util.Calendar;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Query}. */
@RunWith(JUnit4.class)
public class QueryTest {

  private Query.Builder mandatoryBuilder() {
    return Query.newBuilder();
  }

  @Test
  public void testBuilder_buildEmptyRequest() throws Exception {
    assertThrows(NullPointerException.class, () -> Query.newBuilder().build());
  }

  @Test
  public void testBuilder_emptyQuery() throws Exception {
    Query request = Query.newBuilder().build("");
    assertThat(request.getQueryString()).isEmpty();
  }

  // http://b/6630822
  @Test
  public void testBuilder_korean() throws Exception {
    Query request = Query.newBuilder().build("조선");
    assertThat(request.getQueryString()).isEqualTo("조선");
  }

  @Test
  public void testBuilder_queryTooLong() throws Exception {
    String query1 = Strings.repeat("a", SearchApiLimits.MAXIMUM_QUERY_LENGTH);
    assertThat(Query.newBuilder().build(query1).getQueryString()).isEqualTo(query1);
    String query2 = Strings.repeat("a", SearchApiLimits.MAXIMUM_QUERY_LENGTH + 1);
    IllegalArgumentException e1 =
        assertThrows(IllegalArgumentException.class, () -> Query.newBuilder().build(query2));
    assertThat(e1)
        .hasMessageThat()
        .isEqualTo("query string must not be longer than 2000 bytes, was 2001");
    String query3 = Strings.repeat("\u2603", SearchApiLimits.MAXIMUM_QUERY_LENGTH / 3);
    assertThat(Query.newBuilder().build(query3).getQueryString()).isEqualTo(query3);
    String query4 = Strings.repeat("\u2603", (SearchApiLimits.MAXIMUM_QUERY_LENGTH / 3) + 1);
    IllegalArgumentException e2 =
        assertThrows(IllegalArgumentException.class, () -> Query.newBuilder().build(query4));
    assertThat(e2)
        .hasMessageThat()
        .isEqualTo("query string must not be longer than 2000 bytes, was 2001");
  }

  @Test
  public void testBuilder_parseQuery() {
    assertThrows(
        SearchQueryException.class, () -> Query.newBuilder().build("this:should:not:parse"));
    assertThrows(SearchQueryException.class, () -> Query.newBuilder().build("a:::c"));
    assertThrows(SearchQueryException.class, () -> Query.newBuilder().build("*()#*"));
  }

  // Tests issue http://b/5824337
  @Test
  public void testBuilder_parseLongQuery() throws Exception {
    String query =
        "(p4347646e:"
            + "62726f6b656e2c20696e2062616420636f6e646974696f6e206f7220696e636f6d706c657465"
            + " OR p4347646e:5f5f414c4c5f5f) AND (p41753547:3630 OR"
            + " p41753547:5f5f414c4c5f5f) AND (p42333248:333130 OR"
            + " p42333248:5f5f414c4c5f5f) AND (p41745671:3134 OR"
            + " p41745671:5f5f414c4c5f5f) AND (p494c3368:5f6f74686572 OR"
            + " p494c3368:5f5f414c4c5f5f) AND (p507a7377:3432 OR"
            + " p507a7377:5f5f414c4c5f5f) AND"
            + " (p574e4f55:7361756e612062656e636820626f617264 OR"
            + " p574e4f55:5f5f414c4c5f5f) AND (p47385867:31383030 OR"
            + " p47385867:5f5f414c4c5f5f) AND (p3a6367:6350424a) AND (p3a64767279:6669"
            + " OR p3a64767279:6c696e6b2d61637776746234303033) AND"
            + " (p3a7374617465:7075626c6963) AND (p3a74797065:6f66666572)";
    Query request = Query.newBuilder().build(query);
    assertThat(request.getQueryString()).isEqualTo(query);
  }

  @Test
  public void testBuild_mandatoryFilledIn() throws Exception {
    Query request = mandatoryBuilder().build("subject:goodness");
    assertThat(request.getQueryString()).isEqualTo("subject:goodness");
    assertThat(request.getOptions()).isNull();
  }

  @Test
  public void testBuild_parseNumbers() throws Exception {
    assertThat(mandatoryBuilder().build("foo<=100").getQueryString()).isEqualTo("foo<=100");
    assertThat(mandatoryBuilder().build("foo>=-100").getQueryString()).isEqualTo("foo>=-100");
  }

  @Test
  public void testDateFieldToStringIsParsable() throws Exception {
    Calendar instance = DateTestHelper.getCalendar();
    instance.set(2009, 1, 23, 0, 0, 0);
    instance.set(Calendar.MILLISECOND, 0);
    Field dateField = Field.newBuilder().setName("when").setDate(instance.getTime()).build();
    assertThat(
            mandatoryBuilder()
                .build(
                    String.format(
                        "%s:%s",
                        dateField.getName(), DateTestHelper.formatDate(dateField.getDate())))
                .getQueryString())
        .isEqualTo("when:2009-02-23");
  }

  @Test
  public void testNewBuilder_buildPartialFail() throws Exception {
    QueryOptions options = QueryOptions.newBuilder().build();
    Query.Builder partialBuilder = Query.newBuilder().setOptions(options);
    assertThrows(NullPointerException.class, partialBuilder::build);
  }

  @Test
  public void testNewBuilder_allOptions() throws Exception {
    QueryOptions options = QueryOptions.newBuilder().build();
    Query query = Query.newBuilder().setOptions(options).build("some query");
    assertThat(query.getQueryString()).isEqualTo("some query");
    assertThat(query.getOptions()).isEqualTo(options);
  }

  @Test
  public void testNewBuilderRequest() throws Exception {
    Query request = Query.newBuilder(Query.newBuilder().build("subject:goodness")).build();
    assertThat(request.getQueryString()).isEqualTo("subject:goodness");
    assertThat(request.getOptions()).isNull();
  }

  @Test
  public void testCopyToProtocolBuffer() throws Exception {
    FieldExpression expression =
        FieldExpression.newBuilder()
            .setName("snippet")
            .setExpression("snippet(\"query here\", field_to_snippet)")
            .build();
    FieldExpression.Builder expressionBuilder =
        FieldExpression.newBuilder()
            .setName("snippet2")
            .setExpression("snippet(\"query here\", field_to_snippet2)");

    Cursor cursor = Cursor.newBuilder().build("true:continueHere");

    QueryOptions options =
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
                    .addSortExpression(
                        SortExpression.newBuilder()
                            .setExpression("name")
                            .setDirection(SortExpression.SortDirection.ASCENDING)
                            .setDefaultValue("zzz"))
                    .addSortExpression(
                        SortExpression.newBuilder()
                            .setExpression("subject")
                            .setDirection(SortExpression.SortDirection.ASCENDING)
                            .setDefaultValue("zzz")))
            .build();
    FacetOptions facetOptions =
        FacetOptions.newBuilder()
            .setDepth(100)
            .setDiscoveryLimit(5)
            .setDiscoveryValueLimit(8)
            .build();
    SearchParams.Builder params =
        Query.newBuilder()
            .setOptions(options)
            .setEnableFacetDiscovery(true)
            .setFacetOptions(facetOptions)
            .addFacetRefinementFromToken(
                FacetRefinement.withValue("facet3", "value3").toTokenString())
            .addFacetRefinement(
                FacetRefinement.withRange("facet4", FacetRange.withStartEnd(4.0, 5.0)))
            .addFacetRefinement(FacetRefinement.withRange("facet4", FacetRange.withEnd(5.0)))
            .addFacetRefinement(FacetRefinement.withRange("facet4", FacetRange.withStart(4.0)))
            .addFacetRefinement(FacetRefinement.withValue("facet5", "6.0"))
            .addReturnFacet(
                FacetRequest.newBuilder()
                    .setName("facet1")
                    .addValueConstraint("value1")
                    .setValueLimit(12)
                    .build())
            .addReturnFacet(
                FacetRequest.newBuilder()
                    .setName("facet2")
                    .addRange(FacetRange.withStartEnd(2.0, 3.0)))
            .addReturnFacet(
                FacetRequest.newBuilder().setName("facet2").addRange(FacetRange.withEnd(3.0)))
            .addReturnFacet(
                FacetRequest.newBuilder().setName("facet2").addRange(FacetRange.withStart(2.0)))
            .addReturnFacet("facet3")
            .build("query")
            .copyToProtocolBuffer();

    // TODO testing too much in one unit test. break this up.
    assertThat(params.getAutoDiscoverFacetCount()).isEqualTo(5);
    assertThat(params.getFacetDepth()).isEqualTo(100);
    assertThat(params.getFacetAutoDetectParam().getValueLimit()).isEqualTo(8);

    assertThat(params.getFacetRefinementCount()).isEqualTo(5);
    assertThat(params.getFacetRefinement(0).getName()).isEqualTo("facet3");
    assertThat(params.getFacetRefinement(0).getValue()).isEqualTo("value3");
    assertThat(params.getFacetRefinement(1).getName()).isEqualTo("facet4");
    assertThat(params.getFacetRefinement(1).getRange().getStart()).isEqualTo("4.0");
    assertThat(params.getFacetRefinement(1).getRange().getEnd()).isEqualTo("5.0");
    assertThat(params.getFacetRefinement(2).getRange().hasStart()).isFalse();
    assertThat(params.getFacetRefinement(2).getRange().getEnd()).isEqualTo("5.0");
    assertThat(params.getFacetRefinement(3).getRange().getStart()).isEqualTo("4.0");
    assertThat(params.getFacetRefinement(3).getRange().hasEnd()).isFalse();
    assertThat(params.getFacetRefinement(4).getName()).isEqualTo("facet5");
    assertThat(params.getFacetRefinement(4).getValue()).isEqualTo("6.0");

    assertThat(params.getIncludeFacetCount()).isEqualTo(5);
    assertThat(params.getIncludeFacet(0).getName()).isEqualTo("facet1");
    assertThat(params.getIncludeFacet(0).getParams().getValueConstraintCount()).isEqualTo(1);
    assertThat(params.getIncludeFacet(0).getParams().getRangeCount()).isEqualTo(0);
    assertThat(params.getIncludeFacet(0).getParams().getValueLimit()).isEqualTo(12);
    assertThat(params.getIncludeFacet(0).getParams().getValueConstraint(0)).isEqualTo("value1");
    assertThat(params.getIncludeFacet(1).getName()).isEqualTo("facet2");
    assertThat(params.getIncludeFacet(1).getParams().getValueConstraintCount()).isEqualTo(0);
    assertThat(params.getIncludeFacet(1).getParams().getRangeCount()).isEqualTo(1);
    assertThat(params.getIncludeFacet(1).getParams().getRange(0).hasName()).isFalse();
    assertThat(params.getIncludeFacet(1).getParams().getRange(0).getStart()).isEqualTo("2.0");
    assertThat(params.getIncludeFacet(1).getParams().getRange(0).getEnd()).isEqualTo("3.0");
    assertThat(params.getIncludeFacet(2).getParams().getRange(0).hasName()).isFalse();
    assertThat(params.getIncludeFacet(2).getParams().getRange(0).hasStart()).isFalse();
    assertThat(params.getIncludeFacet(2).getParams().getRange(0).getEnd()).isEqualTo("3.0");
    assertThat(params.getIncludeFacet(3).getParams().getRange(0).hasName()).isFalse();
    assertThat(params.getIncludeFacet(3).getParams().getRange(0).getStart()).isEqualTo("2.0");
    assertThat(params.getIncludeFacet(3).getParams().getRange(0).hasEnd()).isFalse();
    assertThat(params.getIncludeFacet(4).getName()).isEqualTo("facet3");
    assertThat(params.getIncludeFacet(4).getParams().hasValueLimit()).isFalse();
    assertThat(params.getIncludeFacet(4).getParams().getRangeCount()).isEqualTo(0);
    assertThat(params.getIncludeFacet(4).getParams().getValueConstraintCount()).isEqualTo(0);

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
    assertThat(params.getQuery()).isEqualTo("query");
    assertThat(params.getMatchedCountAccuracy()).isEqualTo(100);
    assertThat(params.getLimit()).isEqualTo(19);

    SearchServicePb.ScorerSpec spec = params.getScorerSpec();
    assertThat(spec.hasScorer()).isFalse();
    assertThat(params.getSortSpecCount()).isEqualTo(2);
    SearchServicePb.SortSpec sort1 = params.getSortSpec(0);
    assertThat(sort1.getSortExpression()).isEqualTo("name");
    assertThat(sort1.getSortDescending()).isFalse();
    assertThat(sort1.getDefaultValueText()).isEqualTo("zzz");
    SearchServicePb.SortSpec sort2 = params.getSortSpec(1);
    assertThat(sort2.getSortExpression()).isEqualTo("subject");
    assertThat(sort2.getSortDescending()).isFalse();
    assertThat(sort2.getDefaultValueText()).isEqualTo("zzz");

    // No docs to return set, nor a cursor type.
    params = Query.newBuilder().build("query").copyToProtocolBuffer();
    assertThat(params.getLimit()).isEqualTo(SearchApiLimits.SEARCH_DEFAULT_LIMIT);
    assertThat(params.getCursorType()).isEqualTo(SearchParams.CursorType.NONE);
  }

  @Test
  public void testCopyToProtocolBuffer_defaultCursor() throws Exception {
    Cursor cursor = Cursor.newBuilder().build();

    QueryOptions options = QueryOptions.newBuilder().setCursor(cursor).build();
    SearchParams.Builder params =
        Query.newBuilder().setOptions(options).build("query").copyToProtocolBuffer();

    assertThat(params.getCursorType()).isEqualTo(SearchParams.CursorType.SINGLE);
    assertThat(params.hasCursor()).isFalse();
    assertThat(params.getQuery()).isEqualTo("query");
  }

  @Test
  public void testToString() throws Exception {
    assertThat(Query.newBuilder().build("query").toString()).isEqualTo("Query(queryString=query)");

    assertThat(
            Query.newBuilder()
                .setOptions(
                    QueryOptions.newBuilder().setLimit(19).setReturningIdsOnly(true).build())
                .build("query")
                .toString())
        .isEqualTo("Query(queryString=query, options=QueryOptions(limit=19, IDsOnly=true))");
  }
}
