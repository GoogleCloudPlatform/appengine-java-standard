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

import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.apphosting.api.search.DocumentPb;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LuceneQueryBuilder}.
 *
 */
@RunWith(JUnit4.class)
public class LuceneQueryBuilderTest {
  private static final String GLOBAL = LuceneUtils.FIELDLESS_FIELD_NAME;
  private final Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes = new TreeMap<>();

  private LuceneQueryBuilder queryBuilder;

  private void addFieldType(String field, DocumentPb.FieldValue.ContentType... types) {
    Set<DocumentPb.FieldValue.ContentType> typesSet =
        EnumSet.noneOf(DocumentPb.FieldValue.ContentType.class);
    for (DocumentPb.FieldValue.ContentType type : types) {
      typesSet.add(type);
    }
    fieldTypes.put(field, typesSet);
  }

  @Before
  public void setUp() {
    addFieldType("foo", DocumentPb.FieldValue.ContentType.HTML);
    addFieldType("bar", DocumentPb.FieldValue.ContentType.TEXT);
    addFieldType("num", DocumentPb.FieldValue.ContentType.NUMBER);
    addFieldType("date", DocumentPb.FieldValue.ContentType.DATE);
    addFieldType(
        "text_and_num",
        DocumentPb.FieldValue.ContentType.TEXT,
        DocumentPb.FieldValue.ContentType.NUMBER);
    addFieldType("geo", DocumentPb.FieldValue.ContentType.GEO);
    addFieldType(
        "text_and_atom",
        DocumentPb.FieldValue.ContentType.TEXT,
        DocumentPb.FieldValue.ContentType.ATOM);

    queryBuilder = new LuceneQueryBuilder(fieldTypes);
  }

  private Query parseQuery(String q) {
    return queryBuilder.parse(SearchParams.newBuilder().setQuery(q).buildPartial());
  }

  private void testConvert(String out, String in) {
    assertThat(parseQuery(in).toString()).isEqualTo(out);
  }

  @Test
  public void testSimple() throws Exception {
    assertThat(parseQuery("")).isEqualTo(LuceneUtils.getMatchAnyDocumentQuery());
    assertThat(parseQuery("hello")).isEqualTo(new TermQuery(new Term(GLOBAL, "hello")));
  }

  @Test
  public void testPhrase() {
    PhraseQuery q1 = new PhraseQuery();
    q1.add(new Term(GLOBAL, "hello"));
    q1.add(new Term(GLOBAL, "world"));
    PhraseQuery q2 = new PhraseQuery();
    q2.add(new Term(GLOBAL, "hello world"));
    BooleanQuery expected = new BooleanQuery();
    expected.add(q1, BooleanClause.Occur.SHOULD);
    expected.add(q2, BooleanClause.Occur.SHOULD);
    assertThat(parseQuery("\"hello world\"")).isEqualTo(expected);
  }

  @Test
  public void testField() {
    testConvert("HTML2TEXT@foo:hello", "foo:hello");
  }

  @Test
  public void testConjunction() {
    testConvert("+HTML2TEXT@foo:hello +TEXT@bar:world", "foo:hello bar:world");
  }

  @Test
  public void testUpperCase() {
    testConvert("+HTML2TEXT@foo:hello +TEXT@bar:world", "foo:HELLO bar:World");
  }

  @Test
  public void testSingleNot() {
    testConvert("+_ALLDOC:X -HTML2TEXT@foo:hello", "NOT foo:hello");
  }

  @Test
  public void testConjunctionWithNot() {
    testConvert("+HTML2TEXT@foo:hello +(+_ALLDOC:X -TEXT@bar:world)", "foo:hello NOT bar:world");
  }

  @Test
  public void testDate() {
    testConvert("DATE@date:[0 TO 1}", "date:1970-01-01");
    testConvert("DATE@date:[-365 TO -364}", "date:1969-01-01");
    testConvert("DATE@date:[15161 TO 15162}", "date:2011-07-06");
  }

  @Test
  public void testNumber() {
    testConvert("NUMBER@num:[1976.0 TO 1976.0]", "num:1976");
    testConvert("_ALLDOC:Y", "num:zero");
    testConvert("_ALLDOC:Y", "num: zero");
    testConvert("_ALLDOC:Y", "num:num");
    testConvert("_ALLDOC:Y", "num: num");
  }

  @Test
  public void testNumericRange() {
    testConvert(
        "+NUMBER@num:[1976.0 TO 1.7976931348623157E308] "
            + "+NUMBER@num:[-1.7976931348623157E308 TO 2011.0]",
        "num >= 1976 num <= 2011");
    testConvert("NUMBER@num:[-1.7976931348623157E308 TO 1976.0]", "num <= 1976");
    testConvert("NUMBER@text_and_num:{1976.0 TO 1.7976931348623157E308]", "text_and_num > 1976");
    // Field is not numeric and the query matches nothing
    testConvert("_ALLDOC:Y", "foo <= 1976");
  }

  @Test
  public void testDateRange() {
    // Test different brackets combinations:
    testConvert(
        "+DATE@date:[2550 TO 9223372036854775807]" + " +DATE@date:[-9223372036854775808 TO 15161]",
        "date >= 1976-12-25 date <= 2011-07-06");
    testConvert(
        "+DATE@date:{2550 TO 9223372036854775807]" + " +DATE@date:[-9223372036854775808 TO 15161]",
        "date > 1976-12-25 date <= 2011-07-06");
    testConvert(
        "+DATE@date:[2550 TO 9223372036854775807]" + " +DATE@date:[-9223372036854775808 TO 15161}",
        "date >= 1976-12-25 date <2011-07-06");
    testConvert(
        "+DATE@date:{2550 TO 9223372036854775807]" + " +DATE@date:[-9223372036854775808 TO 15161}",
        "date > 1976-12-25 date < 2011-07-06");
    testConvert("DATE@date:[2550 TO 9223372036854775807]", "date >= 1976-12-25");
    testConvert("DATE@date:[-9223372036854775808 TO 15161]", "date <= 2011-07-06");
    testConvert("DATE@date:{2550 TO 9223372036854775807]", "date > 1976-12-25");
    testConvert("DATE@date:[-9223372036854775808 TO 15161}", "date < 2011-07-06");
  }

  @Test
  public void testAmbiguous() {
    testConvert(
        "TEXT@text_and_num:1976 NUMBER@text_and_num:[1976.0 TO 1976.0]", "text_and_num:1976");
  }

  @Test
  public void testUnknownField() {
    // Field doesn't exist, are now treated as plain text.
    BooleanQuery conjunction = new BooleanQuery();
    conjunction.add(new TermQuery(new Term(GLOBAL, "unknown")), BooleanClause.Occur.MUST);
    conjunction.add(new TermQuery(new Term(GLOBAL, "1976")), BooleanClause.Occur.MUST);
    BooleanQuery disjunction = new BooleanQuery();
    disjunction.add(new TermQuery(new Term(GLOBAL, "unknown:1976")), BooleanClause.Occur.SHOULD);
    disjunction.add(conjunction, BooleanClause.Occur.SHOULD);
    assertThat(parseQuery("unknown:1976")).isEqualTo(disjunction);
  }

  @Test
  public void testComplex() {
    testConvert("+_GLOBAL:a +(_GLOBAL:b _GLOBAL:c)", "a AND (b OR c)");
  }

  @Test
  public void testGeometric() {
    testConvert(
        "GeometricQuery(field='geo' geopoint=(-33.857000,151.215000) op=LT distance=0.100000)",
        "distance(geo, geopoint(-33.857, 151.215)) < 0.1");
  }

  @Test
  public void testTextAndAtom() {
    testConvert("TEXT@text_and_atom:ab_cd ATOM@text_and_atom:ab_cd", "text_and_atom:AB_CD");
    testConvert("_GLOBAL:ab_cd", "AB_CD");
    testConvert("TEXT@text_and_atom:uber ATOM@text_and_atom:über", "text_and_atom:über");
    testConvert("TEXT@text_and_atom:uber ATOM@text_and_atom:über", "text_and_atom:über");
    testConvert("_GLOBAL:uber", "über");
  }
}
