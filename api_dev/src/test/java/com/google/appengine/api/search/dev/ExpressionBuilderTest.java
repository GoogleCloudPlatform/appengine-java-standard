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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.SearchQueryException;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExpressionBuilderTest {
  private Map<String, Set<ContentType>> fieldTypes;

  @Before
  public void setUp() throws Exception {
    fieldTypes = new TreeMap<>();
  }

  public void addFieldTypes(String fieldName, ContentType... types) {
    Set<ContentType> typeSet = EnumSet.noneOf(ContentType.class);
    for (ContentType type : types) {
      typeSet.add(type);
    }
    fieldTypes.put(fieldName, typeSet);
  }

  public void expectIllegalArgumentException(String expr, String expectedExceptionString) {
    ExpressionBuilder builder = new ExpressionBuilder(fieldTypes);
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> builder.parse(expr));
    assertThat(e).hasMessageThat().contains(expectedExceptionString);
  }

  public void expectSearchQueryException(String expr, String expectedExceptionString) {
    ExpressionBuilder builder = new ExpressionBuilder(fieldTypes);
    SearchQueryException e = assertThrows(SearchQueryException.class, () -> builder.parse(expr));
    assertThat(e).hasMessageThat().contains(expectedExceptionString);
  }

  @Test
  public void testParse() throws Exception {
    addFieldTypes("A", ContentType.NUMBER);
    addFieldTypes("B", ContentType.TEXT);
    ExpressionBuilder builder = new ExpressionBuilder(fieldTypes);

    // Check count
    builder.parse("count(A)");
    // an unknown field might be merely "not yet known", so allow this (it will default to "")
    builder.parse("count(C)");
    builder.parse("10");

    // Check numerical binary expressions
    builder.parse("count(A) > 10");
    builder.parse("count(A) + count(B) * count(A) / count(B) < count(A)");
    builder.parse("count(A) = count(B)");
    expectIllegalArgumentException("count(A) == count(B)", "parse error at line 1 position 10");
    builder.parse("-count(A)");

    // Check snippet
    expectIllegalArgumentException("snippet()", "parse error at line 1 position 8");
    expectIllegalArgumentException("snippet(query)", "Missing required arguments");
    expectIllegalArgumentException("snippet(query, C)", "Unknown field");
    expectIllegalArgumentException(
        "snippet(query, A)", "Can only snippet TEXT, HTML, and ATOM fields");
    builder.parse("snippet(query, B)");
    builder.parse("snippet(\"hello world\", B)");
    builder.parse("snippet(query, B, 10)");
    builder.parse("snippet(query, B, 10, 1)");
    builder.parse("snippet(query, B, A)");
    expectSearchQueryException("snippet(\"bad query (\", B, A)", "Failed to parse");
    expectIllegalArgumentException("snippet(query, B, B)", "Field type mismatch");
    expectIllegalArgumentException(
        "snippet(query, A, A)", "Can only snippet TEXT, HTML, and ATOM fields");

    // Check field
    builder.parse("A");
    builder.parse("B");
    expectIllegalArgumentException("C", "Unknown field");
    builder.parse("A + 1");
    expectIllegalArgumentException("C + 1", "Unknown field");
    expectIllegalArgumentException("B + 1", "Field type mismatch");
    addFieldTypes("B", ContentType.NUMBER);
    builder.parse("B + 1");
    builder.parse("B");

    // TODO not implemented yet
    expectIllegalArgumentException("A < B0.F + C * D[0]", "Not yet implemented");
    expectIllegalArgumentException("A < B0.F + C * D[0] * E", "Not yet implemented");

    // Illegal combo of numeric and text expressions.
    expectIllegalArgumentException(
        "-snippet(count(A0))", "Function snippet does not return numeric value");

    // count should not work with sorters
    SearchException se =
        assertThrows(SearchException.class, () -> builder.parse("count(A)").getSorters(1, 0, ""));
    assertThat(se)
        .hasMessageThat()
        .isEqualTo(
            "Failed to parse sort expression 'count(A)':"
                + " count() is not supported in sort expressions");
  }
}
