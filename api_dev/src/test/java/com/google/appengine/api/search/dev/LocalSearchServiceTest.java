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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.DateTestHelper;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.FacetOptions;
import com.google.appengine.api.search.FacetRange;
import com.google.appengine.api.search.FacetRefinement;
import com.google.appengine.api.search.FacetRequest;
import com.google.appengine.api.search.FacetResult;
import com.google.appengine.api.search.FacetResultValue;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.FieldExpression;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.GetIndexesRequest;
import com.google.appengine.api.search.GetRequest;
import com.google.appengine.api.search.GetResponse;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.MatchScorer;
import com.google.appengine.api.search.Query;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.Schema;
import com.google.appengine.api.search.ScoredDocument;
import com.google.appengine.api.search.SearchBaseException;
import com.google.appengine.api.search.SearchException;
import com.google.appengine.api.search.SearchQueryException;
import com.google.appengine.api.search.SearchService;
import com.google.appengine.api.search.SearchServiceConfig;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.search.StatusCode;
import com.google.appengine.tools.development.testing.LocalSearchServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import junit.framework.AssertionFailedError;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LocalSearchService}. This is a medium test due to use of futures.
 *
 */
@RunWith(JUnit4.class)
public class LocalSearchServiceTest {

  private static final String INBOX_INDEX_NAME = "inbox:x@someservice.com";

  private final LocalServiceTestHelper helper = new LocalServiceTestHelper(
      new LocalSearchServiceTestConfig());
  private Document docA;
  private Document docB;
  private Document docC;
  private Document docD;
  private Document docE;
  private Document docF;
  private Document doc1;
  private Document doc2;
  private Document doc3;
  private Document doc4;
  private Document doc5;
  private Document doc6;
  private Document doc7;
  private Document doc8;
  private Document fdoc1;
  private Document fdoc2;
  private Document fdoc3;
  private Document fdoc4;
  private Document fdoc5;
  private Document fdoc6;
  private Document fdoc7;
  private Document fdoc8;
  private Document fdoc9;
  private Document fdoc10;
  private Document fdoc11;
  private Document fdoc12;
  private Document pdoc1;
  private Index inboxIndex;
  private GetIndexesRequest getIndexesRequest;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    Date d = DateTestHelper.parseDate("2011-07-06");
    Date d2 = DateTestHelper.parseDate("2011-07-05");

    docA = Document.newBuilder().setId("doc-1")
        .addField(Field.newBuilder().setName("subject").setText("hello there 555 2011-07-06"))
        .addField(Field.newBuilder().setName("content").setHTML("<h1>blah</h1>"))
        .addField(Field.newBuilder().setName("numeric").setNumber(6))
        .addField(Field.newBuilder().setName("number").setNumber(444))
        .addField(Field.newBuilder().setName("date").setDate(d2))
        .addField(Field.newBuilder().setName("text").setText("2011-04-03"))
        .addField(Field.newBuilder().setName("tag").setText("tag1"))
        .addField(Field.newBuilder().setName("tag").setText("tag2"))
        .setRank(1)
        .build();
    docB = Document.newBuilder().setId("doc-2")
        .addField(Field.newBuilder().setName("subject").setText("hello world"))
        .addField(Field.newBuilder().setName("content").setHTML("<b>the weather</b> is fine"))
        .addField(Field.newBuilder().setName("location").setHTML("Sydney"))
        .addField(Field.newBuilder().setName("number").setNumber(445))
        .addField(Field.newBuilder().setName("date").setDate(d))
        .addField(Field.newBuilder().setName("numeric").setText("6"))
        .addField(Field.newBuilder().setName("fourteen").setNumber(14))
        .addField(Field.newBuilder().setName("atom").setAtom("Abc"))
        .addField(Field.newBuilder().setName("html_need_escaping").setHTML(
            "<b>aaa</b> &lt; <b>bbb</b>"))
        .addField(Field.newBuilder().setName("text_need_escaping").setText("aaa < bbb"))
        .setRank(2)
        .build();
    // Japanese document: subject says ii tenki desu (it is nice weather) and
    // the content says omedetou (best wishes)
    docC = Document.newBuilder().setId("doc-3")
        .addField(Field.newBuilder()
            .setName("subject").setText("\u3044\u3044\u3066\u3093\u304D\u3067\u3059"))
        .addField(Field.newBuilder()
            .setName("content").setHTML("\u304B\u3093\u3052\u304D\u3057\u3066\u308B"))
        .setRank(3)
        .build();
    // Another sort-of-email
    docD = Document.newBuilder().setId("doc-5")
        .addField(Field.newBuilder().setName("subject").setText("aardvark hello"))
        .addField(Field.newBuilder().setName("content").setHTML("<p>A paragraph of text</p>"))
        .addField(Field.newBuilder().setName("numeric").setNumber(8675))
        .addField(Field.newBuilder().setName("number").setNumber(309))
        .addField(Field.newBuilder().setName("date").setDate(d2))
        .setRank(6)
        .build();
    // A restaurant description.
    docE = Document.newBuilder().setId("doc-4")
        .addField(Field.newBuilder().setName("name").setText("Guillaume at Bennelong"))
        .addField(Field.newBuilder().setName("cuisine").setAtom("french"))
        .addField(Field.newBuilder().setName("location").setGeoPoint(new GeoPoint(-33.84, 151.26)))
        .setRank(3)
        .build();
    // Another sort-of-email
    docF = Document.newBuilder().setId("doc-6")
        .addField(Field.newBuilder().setName("subject").setText("test"))
        .addField(Field.newBuilder().setName("myfield").setAtom("some@where.com"))
        .addField(Field.newBuilder().setName("atom2").setAtom("foo bar"))
        .addField(Field.newBuilder().setName("atom3").setAtom("cat OR dog"))
        //.addField(Field.newBuilder().setName("sender").setAtom("foo@bar.com"))
        .setRank(6)
        .build();
    fdoc1 = Document.newBuilder().setId("fdoc-1")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "sci-fi"))
        .addFacet(Facet.withNumber("rating", 3.5))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 1995.0))
        .build();
    fdoc2 = Document.newBuilder().setId("fdoc-2")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "fantasy"))
        .addFacet(Facet.withNumber("rating", 2.0))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 2003.0))
        .build();
    fdoc3 = Document.newBuilder().setId("fdoc-3")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("wine_type", "red"))
        .addFacet(Facet.withAtom("type", "wine"))
        .addFacet(Facet.withNumber("vintage", 1991.0))
        .build();
    fdoc4 = Document.newBuilder().setId("fdoc-4")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "kids"))
        .addFacet(Facet.withAtom("genre", "fantasy"))
        .addFacet(Facet.withNumber("rating", 1.5))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 2000.0))
        .build();
    fdoc5 = Document.newBuilder().setId("fdoc-5")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("wine_type", "white"))
        .addFacet(Facet.withAtom("type", "wine"))
        .addFacet(Facet.withNumber("vintage", 1995.0))
        .build();
    fdoc6 = Document.newBuilder().setId("fdoc-6")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("wine_type", "white"))
        .addFacet(Facet.withAtom("type", "wine"))
        .addFacet(Facet.withNumber("vintage", 1898.0))
        .build();
    fdoc7 = Document.newBuilder().setId("fdoc-7")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("wine_type", "white"))
        .addFacet(Facet.withAtom("type", "wine"))
        .addFacet(Facet.withNumber("vintage", 1990.0))
        .build();
    fdoc8 = Document.newBuilder().setId("fdoc-8")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("wine_type", "red"))
        .addFacet(Facet.withAtom("type", "wine"))
        .addFacet(Facet.withNumber("vintage", 1988.0))
        .build();
    fdoc9 = Document.newBuilder().setId("fdoc-9")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "fantasy"))
        .addFacet(Facet.withNumber("rating", 4.0))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 2010.0))
        .build();
    fdoc10 = Document.newBuilder().setId("fdoc-10")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "fantasy"))
        .addFacet(Facet.withNumber("rating", 3.9))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 2011.0))
        .build();
    fdoc11 = Document.newBuilder().setId("fdoc-11")
        .addField(Field.newBuilder().setName("foo").setText("bar"))
        .addFacet(Facet.withAtom("genre", "sci-fi"))
        .addFacet(Facet.withNumber("rating", 2.9))
        .addFacet(Facet.withAtom("type", "movie"))
        .addFacet(Facet.withNumber("year", 2009.0))
        .build();
    fdoc12 = Document.newBuilder().setId("fdoc-12")
        .addField(Field.newBuilder().setName("foo2").setText("bar2"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withAtom("should_not_appear_facet", "should_not_appear_value"))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .addFacet(Facet.withNumber("should_not_appear_facet_num", -1.0))
        .build();
    pdoc1 = Document.newBuilder().setId("pdoc-1")
        .addField(Field.newBuilder().setName("tprefix").setTokenizedPrefix("The Quick Brown Fox"))
        .addField(Field.newBuilder().setName("uprefix").setUntokenizedPrefix("The Quick Brown Fox"))
        .build();
    LocalServiceTestHelper.getApiProxyLocal().setProperty(
        LocalSearchService.USE_RAM_DIRECTORY, "true");
    // TODO Add PER_DOCUMENT style index and use it in tests
    inboxIndex =
        SearchServiceFactory.getSearchService()
            .getIndex(IndexSpec.newBuilder().setName(INBOX_INDEX_NAME).build());
    getIndexesRequest = GetIndexesRequest.newBuilder().build();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  private static List<Document> getAllDocs(Results<ScoredDocument> response) {
    List<Document> all = Lists.newArrayList();
    for (ScoredDocument result : response) {
      all.add(result);
    }
    return all;
  }

  private List<String> fieldsToStrings(Document doc) {
    List<String> strings = Lists.newArrayList();
    for (Field field : doc.getFields()) {
      if (field.getType() == Field.FieldType.ATOM) {
        strings.add(field.toString().toLowerCase());
      } else {
        strings.add(field.toString());
      }
    }
    return strings;
  }

  public void checkResponse(Results<ScoredDocument> resp, int returned, int matched) {
    assertEquals(matched, resp.getNumberFound());
    assertEquals(returned, resp.getNumberReturned());
    assertEquals(returned, resp.getResults().size());
  }

  private void checkSearchResults(Results<ScoredDocument> response, int docsMatched,
      int docsReturned, Document... docs) {
    Map<String, Document> documents = Maps.newHashMap();
    for (Document d : docs) {
      documents.put(d.getId(), d);
    }
    assertEquals(StatusCode.OK, response.getOperationResult().getCode());
    checkResponse(response, docsReturned, docsMatched);
    if (docs != null) {
      List<Document> responseDocs = getAllDocs(response);
      assertThat(responseDocs).containsExactlyElementsIn(docs);
      for (Document d : responseDocs) {
        assertThat(fieldsToStrings(d)).containsExactlyElementsIn(
            fieldsToStrings(documents.get(d.getId())));
      }
    }
  }

  private void checkSearchResultIds(Results<ScoredDocument> response, Document... docs) {
    assertEquals(StatusCode.OK, response.getOperationResult().getCode());
    checkResponse(response, docs.length, docs.length);

    Set<String> expectedIds = Sets.newHashSet();
    for (Document d : docs) {
      expectedIds.add(d.getId());
    }
    for (Document d : getAllDocs(response)) {
      assertTrue("Unexpected doc id returned: " + d.getId(), expectedIds.remove(d.getId()));
    }
    assertEquals("Expected doc ids not returned: " + expectedIds.toString(), 0, expectedIds.size());
  }

  @Test
  public void testIndexDocuments() throws Exception {
    assertEquals(StatusCode.OK, inboxIndex.put(docA).getResults().get(0).getCode());
  }

  @Test
  public void testIndexDocumentsAsync() throws Exception {
    assertEquals(StatusCode.OK,
                 inboxIndex.putAsync(docA).get().getResults().get(0).getCode());
  }

  @Test
  public void testIndexDocuments_nullFields() throws Exception {
    Document doc;
    Document ret;

    doc = Document.newBuilder().setId("null-fields")
        .addField(Field.newBuilder().setName("null").setText(null))
        .addField(Field.newBuilder().setName("empty").setText(""))
        .build();
    indexDocuments(doc);
    ret = inboxIndex.get("null-fields");
    assertEquals(ret.getOnlyField("null").getText(), ret.getOnlyField("empty").getText());

    doc = Document.newBuilder().setId("null-fields")
        .addField(Field.newBuilder().setName("null").setAtom(null))
        .addField(Field.newBuilder().setName("empty").setAtom(""))
        .build();
    indexDocuments(doc);
    ret = inboxIndex.get("null-fields");
    assertEquals(ret.getOnlyField("null").getAtom(), ret.getOnlyField("empty").getAtom());

    doc = Document.newBuilder().setId("null-fields")
        .addField(Field.newBuilder().setName("null").setHTML(null))
        .addField(Field.newBuilder().setName("empty").setHTML(""))
        .build();
    indexDocuments(doc);
    ret = inboxIndex.get("null-fields");
    assertEquals(ret.getOnlyField("null").getHTML(), ret.getOnlyField("empty").getHTML());
  }

  void indexDocuments(Document... docs) throws SearchBaseException {
    for (Document doc : docs) {
      assertEquals(StatusCode.OK, inboxIndex.put(doc).getResults().get(0).getCode());
    }
  }

  @Test
  public void testSearchAtomFieldWithPunc() {
    Results<ScoredDocument> response = inboxIndex.search("hello");
    assertEquals(StatusCode.OK, response.getOperationResult().getCode());
    checkResponse(response, 0, 0);

    indexDocuments(docF);
    response = inboxIndex.search("some@where.com");
    checkSearchResults(response, 1, 1, docF);
    response = inboxIndex.search("atom2:\"foo bar\"");
    checkSearchResults(response, 1, 1, docF);
    response = inboxIndex.search("\"foo bar\"");
    checkSearchResults(response, 1, 1, docF);
    response = inboxIndex.search("foo bar");
    checkSearchResults(response, 1, 1, docF);
    response = inboxIndex.search("\"cat OR dog\"");
    checkSearchResults(response, 1, 1, docF);
    response = inboxIndex.search("~foo ~bar");
    checkSearchResults(response, 0, 0);
    response = inboxIndex.search("~\"foo bar\"");
    checkSearchResults(response, 1, 1, docF);
  }
  
  @Test
  public void testSearchPrefixFields() {
    indexDocuments(pdoc1);
    // Untokenized Prefix Field tests
    Results<ScoredDocument> response = inboxIndex.search("uprefix: t ");
    checkSearchResults(response, 1, 1, pdoc1);
    String wholeQuery = "the quick brown fox";
    String queryTemplate = "uprefix: \"%s\"";
    for (int i = 0; i < wholeQuery.length(); i++) {
      if (Character.isWhitespace(wholeQuery.charAt(i))) {
        continue;
      }
      String query = String.format(queryTemplate, wholeQuery.substring(0, i + 1));
      response = inboxIndex.search(query);
      checkSearchResults(response, 1, 1, pdoc1);
    }
    // Tokenized Prefix Field tests
    response = inboxIndex.search("tprefix: qui");
    checkSearchResults(response, 1, 1, pdoc1);
    response = inboxIndex.search("tprefix: \"th qui\"");
    checkSearchResults(response, 1, 1, pdoc1);
    response = inboxIndex.search("tprefix: \"TH QUi\"");
    checkSearchResults(response, 1, 1, pdoc1);
    // Test Tokenized Prefix Field preserves phrase semantics.
    response = inboxIndex.search("tprefix: \"qui th\"");
    checkSearchResults(response, 0, 0);
  }

  @Test
  public void testSearch() throws Exception {
    Results<ScoredDocument> response = inboxIndex.search("hello");
    assertEquals(StatusCode.OK, response.getOperationResult().getCode());
    checkResponse(response, 0, 0);

    // Insert document A into the index and try to find it.
    indexDocuments(docA);
    response = inboxIndex.search("hello");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("subject:hello");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("content:blah");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("fhqwhgads");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("subject:blah");
    checkSearchResults(response, 0, 0);

    // Add more stuff to index
    indexDocuments(docB);

    response = inboxIndex.search("hello");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("Hello");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search(Query.newBuilder()
        .setOptions(QueryOptions.newBuilder().setLimit(1))
        .build("hello"));
    checkSearchResults(response, 2, 1, docB);

    response = inboxIndex.search("hello AND NOT here AND blah");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("blah");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("there");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("hello there");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("hello There");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("hello world");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("hello");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("world");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("blah world");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("blah OR world");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("blah OR World");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("hello AND (blah OR world)");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("hello AND (blah AND NOT world)");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("hello AND NOT (blah AND NOT world)");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("(weather OR blah) AND NOT (blah AND NOT world)");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("\"hello world\"");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("\"hello World\"");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("\"world hello\"");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("number:445");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("number:446");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("numeric:6");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("numeric:5");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("blah AND numeric:6");
    checkSearchResults(response, 1, 1, docA);

    // TODO truncate doesn't work correctly
    response = inboxIndex.search("date:2011-07-06");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("date:2011-07-08");
    checkSearchResults(response, 0, 0);

    // matches numeric field value as text
    response = inboxIndex.search("444");
    checkSearchResults(response, 1, 1, docA);
    response = inboxIndex.search("444.00");
    checkSearchResults(response, 0, 0);

    // matches numeric field value as number
    response = inboxIndex.search("number:444");
    checkSearchResults(response, 1, 1, docA);
    response = inboxIndex.search("number:444.00");
    checkSearchResults(response, 1, 1, docA);

    // matches text field value as text
    response = inboxIndex.search("555");
    checkSearchResults(response, 1, 1, docA);
    response = inboxIndex.search("555.0");
    checkSearchResults(response, 0, 0);

    // still matches text field value as text
    response = inboxIndex.search("subject:555");
    checkSearchResults(response, 1, 1, docA);
    response = inboxIndex.search("subject:555.0");
    checkSearchResults(response, 0, 0);

    // match atoms with wrong case
    response = inboxIndex.search("abc");
    checkSearchResults(response, 1, 1, docB);
    response = inboxIndex.search("aBc");
    checkSearchResults(response, 1, 1, docB);
    response = inboxIndex.search("ABC");
    checkSearchResults(response, 1, 1, docB);
  }

  private Document createTestDocument(String docId, String fieldName, String value) {
    Document.Builder docBuilder = Document.newBuilder();
    docBuilder.setId(docId);
    docBuilder.addField(Field.newBuilder().setName(fieldName).setAtom(value).build());
    return docBuilder.build();
  }
  
  @Test
  public void testSearchOverLotsOfDocsAndFields() throws Exception {
    Results<ScoredDocument> response = inboxIndex.search("");
    assertEquals(0, response.getResults().size());
    indexDocuments(createTestDocument("firstDoc", "firstField", "1"));
    response = inboxIndex.search("firstField: 1");
    assertEquals(1, response.getResults().size());
    List<Document> additionalDocs = new ArrayList<Document>();
    int numExtraDocs = 200; 
    for (int i = 0; i < numExtraDocs; i++) {
      additionalDocs.add(createTestDocument(Integer.toString(i),
          "otherField", "2"));
    }
    indexDocuments(additionalDocs.toArray(new Document[0]));
    response = search("", 1000);
    assertEquals(numExtraDocs + 1, response.getResults().size());
    response = search("2", 1000);
    assertEquals(numExtraDocs, response.getResults().size());
    response = search("firstField: 1", 1000);
    assertEquals(1, response.getResults().size());
  }
  
  public Results<ScoredDocument> search(String queryString, int numToReturn) {
    Query query = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder().setLimit(numToReturn).build()).build(queryString);
    return inboxIndex.search(query);
  }

  @Test
  public void testNumericRangeQuery() throws Exception {
    indexDocuments(docA, docB);

    Results<ScoredDocument> response = inboxIndex.search("number >= 444 number <= 445");
    checkSearchResults(response, 2, 2, docA, docB);

    // Test different brackets combinations:
    response = inboxIndex.search("number > 444 number <= 445");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("number >= 444 number < 445");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("number > 444 number < 445");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("number > 443 number < 445");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("number < 445");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("number > 444");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("number <= 444");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("number >= 445");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("numeric >= 1 numeric <= 10");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("subject >= 1 subject <= 10");
    checkSearchResults(response, 0, 0);
  }

  @Test
  public void testDateRangeQuery() throws Exception {
    indexDocuments(docA, docB);

    Results<ScoredDocument> response = inboxIndex.search("date >= 2010-12-31 date <= 2012-01-01");
    checkSearchResults(response, 2, 2, docA, docB);

    // Test different brackets combinations:
    response = inboxIndex.search("date >= 2011-07-05 date <= 2011-07-06");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("date > 2011-07-05 date <= 2011-07-06");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("date >= 2011-07-05 date < 2011-07-06");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("date > 2011-07-05 date < 2011-07-06");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("date > 2011-07-04 date < 2011-07-06");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("date >= 2011-07-06");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("date > 2011-07-05");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("date <= 2011-07-05");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("date < 2011-07-06");
    checkSearchResults(response, 1, 1, docA);

    response = inboxIndex.search("text >= 2010-12-31 text <= 2012-01-01");
    checkSearchResults(response, 0, 0);

    response = inboxIndex.search("date = 2011-07-06");
    checkSearchResults(response, 1, 1, docB);

    response = inboxIndex.search("date > -1000-1-1");
    checkSearchResults(response, 2, 2, docA, docB);
  }

  @Test
  public void testGlobalDateSearch() throws Exception {
    indexDocuments(docA, docB);
    Results<ScoredDocument> response;

    // Should find the date in docB and the text in docA.
    response = inboxIndex.search("2011-07-06");
    checkSearchResults(response, 2, 2, docA, docB);
  }

  @Test
  public void testSearchInvalidQuery() throws Exception {
    indexDocuments(docA);
    try {
      inboxIndex.search("this:is:not:a:valid:query");
      fail("invalid query parsed");
    } catch (SearchQueryException e) {
      // exception expected.
    }
  }

  @Test
  public void testGeoSearch() throws Exception {
    double lat = -33.84;
    double lng = 151.26;
    indexDocuments(docE);
    Results<ScoredDocument> results = inboxIndex.search(
        String.format("distance(location, geopoint(%f, %f)) < 1000", lat, lng));

    // For now we only test that the search was successful.
    assertEquals(StatusCode.OK, results.getOperationResult().getCode());
    // TODO Add test that checks for correct documents returned once
    // geopoints are supported by dev server.

    // Test that sorting by distance does not do any harm.
    Query query =
        Query.newBuilder()
            .setOptions(
                QueryOptions.newBuilder()
                    .setSortOptions(
                        SortOptions.newBuilder()
                            .addSortExpression(
                                SortExpression.newBuilder()
                                    .setExpression(
                                        String.format(
                                            "distance(location, geopoint(%f, %f))", lat, lng))
                                    .setDefaultValueNumeric(50000.0))))
            .build("");
    inboxIndex.search(query);
    assertEquals(StatusCode.OK, results.getOperationResult().getCode());
    // TODO Add test that checks for correct sort order once
    // geopoints are supported by dev server.
  }

  @Test
  public void testSearchWithPaging() throws Exception {
    indexDocuments(docA, docB);

    Set<Document> found = Sets.newHashSet();

    Query reqA = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
            .setLimit(1)
            .setCursor(Cursor.newBuilder()))
        .build("hello");
    Results<ScoredDocument> respA = inboxIndex.search(reqA);
    assertEquals(StatusCode.OK, respA.getOperationResult().getCode());
    checkResponse(respA, 1, 2);
    assertNotNull(respA.getCursor());
    found.add(respA.getResults().iterator().next());

    Query reqB = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
            .setLimit(1)
            .setCursor(respA.getCursor()))
        .build("hello");

    Results<ScoredDocument> respB = inboxIndex.search(reqB);
    assertEquals(StatusCode.OK, respB.getOperationResult().getCode());
    checkResponse(respB, 1, 2);
    assertNull(respB.getCursor());
    found.add(respB.getResults().iterator().next());

    assertEquals(ImmutableSet.of(docA, docB), found);
  }

  @Test
  public void testSearchWithCorruptCursor() throws Exception {
    indexDocuments(docA);
    Query req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
            .setCursor(Cursor.newBuilder().build("false:not a real cursor")))
        .build("hello");
    try {
      inboxIndex.search(req);
      fail("SearchException expected");
    } catch (SearchException e) {
      assertEquals(StatusCode.INVALID_REQUEST, e.getOperationResult().getCode());
    }
  }

  @Test
  public void testSearchWithSelectedFields() throws Exception {
    indexDocuments(docA, docB);

    Query req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
            .setFieldsToReturn("subject"))
        .build("hello");
    Results<ScoredDocument> resp = inboxIndex.search(req);
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 2, 2);
    for (ScoredDocument result : resp.getResults()) {
      assertEquals(ImmutableSet.of("subject"), result.getFieldNames());
    }
  }

  @Test
  public void testSearchWithScoring() throws Exception {
    indexDocuments(docA, docB, docD);

    Query req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
          .setSortOptions(SortOptions.newBuilder()
              .setMatchScorer(MatchScorer.newBuilder())
              .addSortExpression(SortExpression.newBuilder()
                  .setExpression(SortExpression.SCORE_FIELD_NAME)
                  .setDefaultValue("")
                  .setDirection(SortExpression.SortDirection.DESCENDING)))
          .setLimit(100))
        .build("hello");

    Results<ScoredDocument> resp = inboxIndex.search(req);
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());

    // TODO When we support sorting on score, verify that these are correctly sorted.
  }

  @Test
  public void testSearchWithSorting() throws Exception {
    indexDocuments(docA, docB, docD);

    Query req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
          .setSortOptions(SortOptions.newBuilder()
              .addSortExpression(SortExpression.newBuilder()
                  .setExpression("subject")
                  .setDefaultValue("")
                  .setDirection(SortExpression.SortDirection.DESCENDING)))
          .setLimit(100))
        .build("hello");
    Results<ScoredDocument> resp = inboxIndex.search(req);
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    List<Document> foundDocs = Lists.newArrayList();
    for (ScoredDocument result : resp.getResults()) {
      foundDocs.add(result);
    }
    assertEquals(ImmutableList.of(docB, docA, docD), foundDocs);
  }

  @Test
  public void testSearchWithDateSorting() throws Exception {
    Date dx = DateTestHelper.parseDate("2011-01-01");
    Document docX = Document.newBuilder().setId("doc-X")
        .addField(Field.newBuilder().setName("subject").setText("hello hello"))
        .addField(Field.newBuilder().setName("date").setDate(dx)).build();

    // Dates: docX = 2011-1-1, docB = 2011-7-6, docD has none
    indexDocuments(docX, docB, docD);

    // Default is between A and B
    Date d = DateTestHelper.parseDate("2011-06-05");

    Query req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder()
          .setSortOptions(SortOptions.newBuilder()
              .addSortExpression(SortExpression.newBuilder()
                  .setExpression("date")
                  .setDefaultValueNumeric(d.getTime())
                  .setDirection(SortExpression.SortDirection.ASCENDING)))
          .setLimit(100))
        .build("hello");
    Results<ScoredDocument> resp = inboxIndex.search(req);
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    List<Document> foundDocs = Lists.newArrayList();
    String foundIds = "";
    for (ScoredDocument result : resp.getResults()) {
      foundDocs.add(result);
      foundIds += result.getId() + ", ";
    }
    assertEquals(foundIds, ImmutableList.of(docX, docD, docB), foundDocs);
  }

  @Test
  public void testBooleanSearchOperators() throws Exception {
    indexDocuments(docA);

    Results<ScoredDocument> response = inboxIndex.search("world OR hello");
    checkSearchResults(response, 1, 1, docA);

    indexDocuments(docB);

    response = inboxIndex.search("world OR hello");
    checkSearchResults(response, 2, 2, docA, docB);

    response = inboxIndex.search("subject:hello NOT subject:there");
    checkSearchResults(response, 1, 1, docB);
  }

  @Test
  public void testCJKSearch() throws Exception {
    indexDocuments(docC);

    // TODO Add tests for Japanese, Korean and Chinese
    // once we add non-core analyzers to Lucene.

    // Results<ScoredDocument> response = inboxIndex.search("\u3066\u3093\u304D");
    // checkSearchResults(response, 1, 1, docC);
  }

  @Test
  public void testSearchAsync() throws Exception {
    Future<Results<ScoredDocument>> futureResponse = inboxIndex.searchAsync("hello");
    Results<ScoredDocument> response = futureResponse.get();
    assertEquals(StatusCode.OK, response.getOperationResult().getCode());
  }

  @Test
  public void testDelete() throws Exception {
    // Deleting from an empty index always succeeds.
    inboxIndex.delete(docA.getId());
    // No exception equals success.

    // Insert docA into an index; verify it can be found.
    indexDocuments(docA);
    checkSearchResults(inboxIndex.search("hello"), 1, 1, docA);

    // Delete docA
    inboxIndex.delete(docA.getId());
    // No exception equals success.
    checkSearchResults(inboxIndex.search("hello"), 0, 0);
  }

  @Test
  public void testSearchIdsOnly() throws Exception {
    indexDocuments(docA, docB);
    Document docAId = Document.newBuilder().setId("doc-1").build();
    Document docBId = Document.newBuilder().setId("doc-2").build();
    Query.Builder req = Query.newBuilder()
        .setOptions(QueryOptions.newBuilder().setReturningIdsOnly(true));
    Results<ScoredDocument> response = inboxIndex.search(req.build("hello"));
    checkSearchResults(response, 2, 2, docAId, docBId);
  }

  @Test
  public void testGetIndexes() throws Exception {
    SearchService searchService = SearchServiceFactory.getSearchService();
    assertTrue(searchService.getIndexes(getIndexesRequest).getResults().isEmpty());

    indexDocuments(docA);
    GetResponse<Index> response = searchService.getIndexes(getIndexesRequest);
    assertEquals(1, response.getResults().size());
    assertEquals(inboxIndex.getName(), response.getResults().iterator().next().getName());
  }

  @Test
  public void testGetIndexes_fetchSchema() throws Exception {
    SearchService searchService = SearchServiceFactory.getSearchService();
    indexDocuments(docA);
    GetResponse<Index> response = searchService.getIndexes(
        GetIndexesRequest.newBuilder().setSchemaFetched(true).build());
    Index index = response.getResults().iterator().next();
    assertEquals(1, response.getResults().size());
    assertEquals(inboxIndex.getName(), index.getName());
    assertEquals(inboxIndex.getNamespace(), index.getNamespace());
    Schema schema = index.getSchema();
    assertEquals(ImmutableSet.of("subject", "content", "numeric", "number", "date", "text", "tag"),
        schema.getFieldNames());
    assertEquals(ImmutableList.of(Field.FieldType.TEXT), schema.getFieldTypes("subject"));
    assertEquals(ImmutableList.of(Field.FieldType.HTML), schema.getFieldTypes("content"));
    assertEquals(ImmutableList.of(Field.FieldType.NUMBER), schema.getFieldTypes("numeric"));
    assertEquals(ImmutableList.of(Field.FieldType.NUMBER), schema.getFieldTypes("number"));
    assertEquals(ImmutableList.of(Field.FieldType.DATE), schema.getFieldTypes("date"));
    assertEquals(ImmutableList.of(Field.FieldType.TEXT), schema.getFieldTypes("text"));
    assertEquals(ImmutableList.of(Field.FieldType.TEXT), schema.getFieldTypes("tag"));
  }

  @Test
  public void testQueryStorageSansGetIndexesCall() {
    final String expectedMessageSnippet = "is not available";
    indexDocuments(docA);
    SearchServiceFactory.getSearchService();
    try {
      inboxIndex.getStorageUsage();
      fail("getStorageUsage() on Index not returned from GetIndexes should throw exception");
    } catch (UnsupportedOperationException e) {
      String msg = e.getMessage();
      assertNotNull(msg);
      assertTrue(msg.contains(expectedMessageSnippet));
    }
  }

  @Test
  public void testGetIndexes_fetchStorage() throws Exception {
    indexDocuments(docA);
    SearchService searchService = SearchServiceFactory.getSearchService();
    GetResponse<Index> response = searchService.getIndexes(GetIndexesRequest.newBuilder().build());
    assertEquals(1, response.getResults().size());
    Index index = response.getResults().iterator().next();
    assertEquals(inboxIndex.getName(), index.getName());
    long initialAmount = index.getStorageUsage();

    // See that storage amount increases after adding a document to
    // the index.  (However, note that a test like this would not be
    // guaranteed to work in production, because of Quota Server
    // caching.)
    indexDocuments(docB);
    response = searchService.getIndexes(GetIndexesRequest.newBuilder().build());
    assertEquals(1, response.getResults().size());
    index = response.getResults().iterator().next();
    long subsequentAmount = index.getStorageUsage();
    assertTrue(subsequentAmount > initialAmount);

    // See that inserting something into a different index leaves the
    // storage amount of the original index unchanged (again, this
    // wouldn't be guaranteed to work in prod: the amount for the
    // original index could change asynchronously at any time, due to
    // cache sync and daily replenishment pipeline activity).
    String index2Name = INBOX_INDEX_NAME + "2";
    Index index2 = SearchServiceFactory.getSearchService().getIndex(
        IndexSpec.newBuilder().setName(index2Name).build());
    index2.put(docC);
    response = searchService.getIndexes(
        GetIndexesRequest.newBuilder()
        .setStartIndexName(INBOX_INDEX_NAME)
        .setIncludeStartIndex(true)
        .setLimit(1));
    assertEquals(1, response.getResults().size());
    index = response.getResults().iterator().next();
    long amount = index.getStorageUsage();
    assertEquals(subsequentAmount, amount);

    // Having put docC into the second index, let's see that the
    // amount of storage used there is less than the amount that was
    // used in the first index when we had put only docA.  We expect
    // this to work only because docC looks obviously smaller than
    // docA, though we don't have a precise expectation of the size.
    // This step is intended as a feeble attempt to make sure the
    // calculation is at least somewhat sensitive to the actual
    // documents in the index.
    response = searchService.getIndexes(
        GetIndexesRequest.newBuilder()
        .setStartIndexName(index2Name)
        .setIncludeStartIndex(true)
        .setLimit(1));
    assertEquals(1, response.getResults().size());
    index = response.getResults().iterator().next();
    long amount2 = index.getStorageUsage();
    assertTrue(amount2 < initialAmount);
  }

  @Test
  public void testGetIndexes_namespaced() throws Exception {
    SearchServiceConfig config = SearchServiceConfig.newBuilder().setNamespace("other").build();
    SearchService service1 = SearchServiceFactory.getSearchService(config);
    Index idx = service1.getIndex(IndexSpec.newBuilder().setName("index"));
    idx.put(Document.newBuilder().setId("doc1")
            .addField(Field.newBuilder().setName("field").setAtom("atom")).build());

    SearchService service2 = SearchServiceFactory.getSearchService();
    idx = service2.getIndex(IndexSpec.newBuilder().setName("index"));
    idx.put(Document.newBuilder().setId("doc2")
            .addField(Field.newBuilder().setName("field").setNumber(4)).build());

    GetResponse<Index> response = service1.getIndexes(
        GetIndexesRequest.newBuilder().setSchemaFetched(true).build());
    assertEquals(1, response.getResults().size());

    Schema schema1 = response.getResults().get(0).getSchema();
    assertEquals(Field.FieldType.ATOM, schema1.getFieldTypes("field").get(0));

    response = service2.getIndexes(
        GetIndexesRequest.newBuilder().setSchemaFetched(true).build());
    assertEquals(1, response.getResults().size());

    Schema schema2 = response.getResults().get(0).getSchema();
    assertEquals(Field.FieldType.NUMBER, schema2.getFieldTypes("field").get(0));
  }

  @Test
  public void testNumericGeneratedFields() throws Exception {
    Map<String, Double> exprValues = new TreeMap<String, Double>();
    exprValues.put("count(tag)", 2.);
    exprValues.put("count(tag) + 10", 12.);
    exprValues.put("count(numeric)", 1.);
    exprValues.put("1 < 1", 0.);
    exprValues.put("1 < 2", 1.);
    exprValues.put("22 > 21", 1.);
    exprValues.put("count(tag) <= 2", 1.);
    exprValues.put("2 >= 2", 1.);
    exprValues.put("2 >= 3", 0.);
    exprValues.put("10 != 11", 1.);
    exprValues.put("10 != 10", 0.);
    exprValues.put("11 + 22", 33.);
    exprValues.put("22 - 30", -8.);
    exprValues.put("55 / 11", 5.);
    exprValues.put("2 * 2", 4.);
    indexDocuments(docA);
    QueryOptions.Builder options = QueryOptions.newBuilder()
        .addExpressionToReturn(
            FieldExpression.newBuilder()
            .setName("num_tags")
            .setExpression("count(tag)"));

    Map<String, Double> exprs = new TreeMap<String, Double>();
    exprs.put("num_tags", 2.0);
    int i = 0;
    for (Map.Entry<String, Double> e : exprValues.entrySet()) {
      String name = "name_" + i;
      options.addExpressionToReturn(FieldExpression.newBuilder()
          .setName(name)
          .setExpression(e.getKey()));
      exprs.put(name, e.getValue());
      i++;
    }

    Query.Builder req = Query.newBuilder().setOptions(options);

    Results<ScoredDocument> resp = inboxIndex.search(req.build("hello"));

    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 1);
    ScoredDocument result = resp.getResults().iterator().next();
    assertEquals(exprs.size(), result.getExpressions().size());
    Map<String, Double> exprResults = new TreeMap<String, Double>();
    for (Field field : result.getExpressions()) {
      assertEquals(Field.FieldType.NUMBER, field.getType());
      exprResults.put(field.getName(), field.getNumber());
    }

    for (Map.Entry<String, Double> e : exprs.entrySet()) {
      assertEquals(e.getKey(), e.getValue(), exprResults.get(e.getKey()));
    }
  }

  @Test
  public void testSnippetGeneration() throws Exception {
    Map<String, String> exprValues = new TreeMap<String, String>();
    exprValues.put("snippet(\"world hello\",subject)", "<b>hello</b> <b>world</b>");
    exprValues.put(
        "snippet(\"subject:world hello\",subject, fourteen / 2)",
        "<b>hello</b> <b>w</b><b>...</b>");
    // Html tags stripped
    exprValues.put("snippet(\"weather\",content,3 * 5)", "the <b>weather</b> is <b>...</b>");
    // Html tags stripped and html escaping preserved
    exprValues.put("snippet(aaa,html_need_escaping)", "<b>aaa</b> &lt; bbb");
    // Text to Html escaping
    exprValues.put("snippet(aaa,text_need_escaping)", "<b>aaa</b> &lt; bbb");
    // Case insensitive
    exprValues.put("snippet(sYDNEY,location)", "<b>Sydney</b>");
    indexDocuments(docB);

    QueryOptions.Builder options = QueryOptions.newBuilder();

    Map<String, String> exprs = new TreeMap<String, String>();
    int i = 0;
    for (Map.Entry<String, String> e : exprValues.entrySet()) {
      String name = "name_" + i;
      options.addExpressionToReturn(
          FieldExpression.newBuilder()
          .setName(name)
          .setExpression(e.getKey()));
      exprs.put(name, e.getValue());
      i++;
    }
    // Check that fields to snippet works ok:
    options.setFieldsToSnippet("subject");
    exprs.put("subject", "hello <b>world</b>");
    Query.Builder req = Query.newBuilder().setOptions(options);
    Results<ScoredDocument> resp = inboxIndex.search(req.build("world"));

    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 1);
    ScoredDocument result = resp.getResults().iterator().next();
    assertEquals(exprs.size(), result.getExpressions().size());

    // Ensure that fields are present despite not setting which fields to return in QueryOptions.
    assertTrue(result.getFieldNames().size() > 0);

    Map<String, String> exprResults = new TreeMap<String, String>();
    for (Field field : result.getExpressions()) {
      assertEquals(Field.FieldType.HTML, field.getType());
      exprResults.put(field.getName(), field.getHTML());
    }

    for (Map.Entry<String, String> e : exprs.entrySet()) {
      assertEquals(e.getKey(), e.getValue(), exprResults.get(e.getKey()));
    }
  }

  public void checkDocumentsOrder(ScoredDocument[] results, Document... exp) {
    assertEquals(results.length, exp.length);
    for (int i = 0; i < exp.length; i++) {
      String actualId = results[i].getId();
      assertEquals(i + ": " + actualId, exp[i].getId(), actualId);
    }
  }

  public void checkScores(ScoredDocument[] results, double... scores) {
    int numScoresPerResult = scores.length / results.length;
    assertEquals(scores.length, numScoresPerResult * results.length);

    for (int result = 0; result < results.length; result++) {
      assertEquals(numScoresPerResult, results[result].getSortScores().size());
    }

    try {
      for (int result = 0; result < results.length; result++) {
        for (int score = 0; score < numScoresPerResult; score++) {
          double expected = scores[result * numScoresPerResult + score];
          double actual = results[result].getSortScores().get(score);
          assertEquals(
              "Result["
                  + result
                  + "].score["
                  + score
                  + "], actualDocId: "
                  + results[result].getId(),
              expected,
              actual,
              0.0);
        }
      }
    } catch (AssertionFailedError e) {
      System.err.println("Document scores received:");
      for (int result = 0; result < results.length; result++) {
        StringBuilder str = new StringBuilder();
        str.append(results[result].getId());
        str.append(' ');
        for (int score = 0; score < numScoresPerResult; score++) {
          str.append(results[result].getSortScores().get(score));
          str.append(", ");
        }
        System.err.println(str.toString());
      }
      throw e;
    }
  }

  @Test
  public void testMatchScorer() throws Exception {
    Document doc1 = Document.newBuilder().setId("doc-1") // contains one word
        .addField(Field.newBuilder().setName("subject").setText("world one two three"))
        .addField(Field.newBuilder().setName("body").setText("two"))
        .setRank(1)
        .build();
    Document doc2 = Document.newBuilder().setId("doc-2") // contains one word several times
        .addField(Field.newBuilder().setName("subject").setText("hello hello hello hello"))
        .addField(Field.newBuilder().setName("body").setText("one"))
        .setRank(2)
        .build();
    Document doc3 = Document.newBuilder().setId("doc-3") // contains both words
        .addField(Field.newBuilder().setName("subject").setText("hello world"))
        .addField(Field.newBuilder().setName("body").setText("one"))
        .setRank(3)
        .build();
    Document doc4 = Document.newBuilder().setId("doc-4") // doesn't match
        .addField(Field.newBuilder().setName("subject").setText("one two three"))
        .addField(Field.newBuilder().setName("body").setText("hello one two three"))
        .setRank(4)
        .build();
    indexDocuments(doc1, doc2, doc3, doc4);

    String helloWorld = "world OR subject:hello";

    QueryOptions.Builder options = QueryOptions.newBuilder()
      .setSortOptions(SortOptions.newBuilder()
          .setMatchScorer(MatchScorer.newBuilder()));
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(
        req.setOptions(options).build(helloWorld));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 3, 3);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc3, doc2, doc1);
    // TODO the scores are copied from lucene returned values, check it
    checkScores(results, 1.024344801902771, 0.4552643597126007, 0.19917815923690796);

    // Check paging and cursors
    options.setLimit(1).setCursor(Cursor.newBuilder());
    resp = inboxIndex.search(req.setOptions(options).build(helloWorld));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 3);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc3);
    checkScores(results, 1.024344801902771);

    options.setCursor(resp.getCursor());
    resp = inboxIndex.search(req.setOptions(options).build(helloWorld));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 3);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc2);
    checkScores(results, 0.4552643597126007);

    options.setCursor(resp.getCursor());
    resp = inboxIndex.search(req.setOptions(options).build(helloWorld));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 3);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1);
    checkScores(results, 0.19917815923690796);
    assertEquals(null, resp.getCursor());
  }
  
  @Test
  public void testScoreFieldExpression() throws Exception {
    Document doc1 =
        Document.newBuilder()
            .setId("doc-1") // contains one word
            .addField(Field.newBuilder().setName("subject").setText("world one two three"))
            .addField(Field.newBuilder().setName("body").setText("two"))
            .setRank(1)
            .build();
    Document doc2 =
        Document.newBuilder()
            .setId("doc-2") // contains one word several times
            .addField(Field.newBuilder().setName("subject").setText("hello hello hello hello"))
            .addField(Field.newBuilder().setName("body").setText("one"))
            .setRank(2)
            .build();
    Document doc3 =
        Document.newBuilder()
            .setId("doc-3") // contains both words
            .addField(Field.newBuilder().setName("subject").setText("hello world"))
            .addField(Field.newBuilder().setName("body").setText("one"))
            .setRank(3)
            .build();
    Document doc4 =
        Document.newBuilder()
            .setId("doc-4") // doesn't match
            .addField(Field.newBuilder().setName("subject").setText("one two three"))
            .addField(Field.newBuilder().setName("body").setText("hello one two three"))
            .setRank(4)
            .build();
    indexDocuments(doc1, doc2, doc3, doc4);

    String helloWorld = "world OR subject:hello";

    // Request _score in two fields, plain in one case and
    // as part of a larger expression in the other.
    QueryOptions.Builder options =
        QueryOptions.newBuilder()
            .setSortOptions(SortOptions.newBuilder().setMatchScorer(MatchScorer.newBuilder()))
            .addExpressionToReturn(
                FieldExpression.newBuilder()
                    .setName("score")
                    .setExpression(SortExpression.SCORE_FIELD_NAME)
                    .build())
            .addExpressionToReturn(
                FieldExpression.newBuilder()
                    .setName("scorePlus1")
                    .setExpression(SortExpression.SCORE_FIELD_NAME + " + 1.0")
                    .build());
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build(helloWorld));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 3, 3);

    // Both results should be present. In the dev context _score can only have the value 0.
    ImmutableMap<String, Double> expected = ImmutableMap.of("score", 0.0, "scorePlus1", 1.0);
    for (ScoredDocument scoredDocument : resp.getResults()) {
      List<Field> expressions = scoredDocument.getExpressions();
      assertThat(expressions).isNotNull();
      Map<String, Double> exprValues = new HashMap<>();
      for (Field field : expressions) {
        exprValues.put(field.getName(), field.getNumber());
      }
      assertThat(exprValues).containsExactlyEntriesIn(expected);
    }
  }

  // Should sort according to OrderId
  @Test
  public void testDefaultScorer() throws Exception {
    Document doc1 = Document.newBuilder().setId("doc-1")
        .addField(Field.newBuilder().setName("subject").setText("a"))
        .setRank(1)
        .build();
    Document doc2 = Document.newBuilder().setId("doc-2")
        .addField(Field.newBuilder().setName("subject").setText("a"))
        .setRank(3)
        .build();
    Document doc3 = Document.newBuilder().setId("doc-3")
        .addField(Field.newBuilder().setName("subject").setText("a"))
        .setRank(2)
        .build();
    Document doc4 = Document.newBuilder().setId("doc-4")
        .addField(Field.newBuilder().setName("subject").setText("a"))
        .setRank(0)
        .build();
    indexDocuments(doc1, doc2, doc3, doc4);
    QueryOptions.Builder options = QueryOptions.newBuilder();
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build("a"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc2, doc3, doc1, doc4);
    checkScores(results);

    // Check paging and cursors
    options.setLimit(1).setCursor(Cursor.newBuilder());
    resp = inboxIndex.search(req.setOptions(options).build("a"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc2);
    checkScores(results);

    options.setCursor(resp.getCursor());
    resp = inboxIndex.search(req.setOptions(options).build("a"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc3);
    checkScores(results);

    options.setCursor(resp.getCursor()).setLimit(2);
    resp = inboxIndex.search(req.setOptions(options).build("a"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 2, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1, doc4);
    checkScores(results);
    assertEquals(null, resp.getCursor());
  }

  @Test
  public void testGenericScorerSimpleNumericExpression() throws Exception {
    Document doc1 = Document.newBuilder().setId("doc-1")
        .addField(Field.newBuilder().setName("num").setNumber(101))
        .build();
    Document doc2 = Document.newBuilder().setId("doc-2")
        .addField(Field.newBuilder().setName("num").setNumber(11))
        .build();
    Document doc3 = Document.newBuilder().setId("doc-3")
        .addField(Field.newBuilder().setName("num").setNumber(10.5))
        .build();
    Document doc4 = Document.newBuilder().setId("doc-4")
        .addField(Field.newBuilder().setName("subject").setText("a"))
        .build();
    indexDocuments(doc1, doc2, doc3, doc4);

    String queryString = "a OR (num >= 1 num <= 1000)";
    // Test numeric sort, default values
    QueryOptions.Builder options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("num")
                .setDefaultValueNumeric(50)));
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build(queryString));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1, doc4, doc2, doc3);
    checkScores(results, 101, 50, 11, 10.5);

    // Test numeric sort, reverse order
    options = QueryOptions.newBuilder().setSortOptions(SortOptions.newBuilder()
        .addSortExpression(SortExpression.newBuilder()
            .setExpression("num")
            .setDirection(SortExpression.SortDirection.ASCENDING)
            .setDefaultValueNumeric(50)));
    resp = inboxIndex.search(req.setOptions(options).build(queryString));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc3, doc2, doc4, doc1);
    checkScores(results, 10.5, 11, 50, 101);

    // More interesting case, expression is: "num - 10"
    options = QueryOptions.newBuilder().setSortOptions(SortOptions.newBuilder()
        .addSortExpression(SortExpression.newBuilder()
            .setExpression("num - 12")
            .setDefaultValueNumeric(50)));
    resp = inboxIndex.search(req.setOptions(options).build(queryString));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1, doc4, doc2, doc3);
    checkScores(results, 89, 50, -1, -1.5);
  }

  @Test
  public void testGenericScorerSimpleTextSorting() throws Exception {
    Document doc1 = Document.newBuilder().setId("doc-1")
        .addField(Field.newBuilder().setName("text").setText("the cat"))
        .build();
    Document doc2 = Document.newBuilder().setId("doc-2")
        .addField(Field.newBuilder().setName("text").setText("the mouse"))
        .build();
    Document doc3 = Document.newBuilder().setId("doc-3")
        .addField(Field.newBuilder().setName("text").setText("the DOG"))
        .build();
    Document doc4 = Document.newBuilder().setId("doc-4")
        .addField(Field.newBuilder().setName("other").setText("the field name is different"))
        .build();
    indexDocuments(doc1, doc2, doc3, doc4);

    // Test text sort, default value, scores
    QueryOptions.Builder options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("text")
                .setDefaultValue("the cow")));
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc2, doc3, doc4, doc1);
    checkScores(results, 0, 0, 0, 0);

    // Reverse order
    options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("text")
                .setDirection(SortExpression.SortDirection.ASCENDING)
                .setDefaultValue("the cow")));
    resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 4, 4);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1, doc4, doc3, doc2);
    checkScores(results, 0, 0, 0, 0);
  }

  public void setupDocsForSorting() throws SearchBaseException {
    doc5 = Document.newBuilder().setId("doc-5") // 8: 15/the cat
        .addField(Field.newBuilder().setName("value").setNumber(15))
        .addField(Field.newBuilder().setName("value").setText("the cat"))
        .build();
    doc7 = Document.newBuilder().setId("doc-7") // 6: 15/the mouse
        .addField(Field.newBuilder().setName("value").setNumber(15))
        .addField(Field.newBuilder().setName("value").setText("the mouse"))
        .build();
    doc2 = Document.newBuilder().setId("doc-2") // 7: 15/the dog
        .addField(Field.newBuilder().setName("value").setNumber(15))
        .addField(Field.newBuilder().setName("other").setText("the field name is different"))
        .build();

    doc6 = Document.newBuilder().setId("doc-6") // 5: 20/the cat
        .addField(Field.newBuilder().setName("value").setText("the cat"))
        .build();
    doc8 = Document.newBuilder().setId("doc-8") // 3: 20/the mouse
        .addField(Field.newBuilder().setName("value").setText("the mouse"))
        .build();
    doc4 = Document.newBuilder().setId("doc-4") // 4: 20/the dog
        .addField(Field.newBuilder().setName("other").setText("the field name is different"))
        .build();

    doc1 = Document.newBuilder().setId("doc-1") // 1: 30/the cat
        .addField(Field.newBuilder().setName("value").setNumber(30))
        .addField(Field.newBuilder().setName("value").setText("the cat"))
        .build();

    doc3 = Document.newBuilder().setId("doc-3") // 2: 25/the dig
        .addField(Field.newBuilder().setName("value").setNumber(25))
        .addField(Field.newBuilder().setName("value").setText("the field name is different"))
        .build();
    indexDocuments(doc1, doc2, doc3, doc4, doc5, doc6, doc7, doc8);
  }

  @Test
  public void testGenericScorerMixedFields() throws Exception {
    setupDocsForSorting();

    // Sort mixed numeric and text fields
    QueryOptions.Builder options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("value")
                .setDefaultValue("the dog")));

    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 8, 8);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc1, doc3, doc7, doc2, doc5, doc8, doc4, doc6);
    checkScores(results, 30, 25, 15, 15, 15, 0, 0, 0);

    // Reverse order
    options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("value")
                .setDirection(SortExpression.SortDirection.ASCENDING)
                .setDefaultValue("the dog")));
    resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 8, 8);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc6, doc4, doc8, doc5, doc2, doc7, doc3, doc1);
    checkScores(results, 0, 0, 0, 15, 15, 15, 25, 30);
  }

  @Test
  public void testGenericScorerMultipleDimensions() throws Exception {
    setupDocsForSorting();
    Document doc9 = Document.newBuilder().setId("doc-9")
        .addField(Field.newBuilder().setName("value").setText("the cat"))
        .addField(Field.newBuilder().setName("num").setNumber(1))
        .build();
    Document doc10 = Document.newBuilder().setId("doc-10")
        .addField(Field.newBuilder().setName("value").setText("the cat"))
        .addField(Field.newBuilder().setName("num").setNumber(2))
        .build();
    indexDocuments(doc9, doc10);

    // Test text sort, default value, scores
    QueryOptions.Builder options = QueryOptions.newBuilder()
        .setSortOptions(SortOptions.newBuilder()
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("count(value) * value")
                .setDirection(SortExpression.SortDirection.ASCENDING)
                .setDefaultValueNumeric(25))
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("value")
                .setDefaultValue("the dog"))
            .addSortExpression(SortExpression.newBuilder()
                .setExpression("num * 100")
                .setDefaultValueNumeric(150)
                .setDirection(SortExpression.SortDirection.ASCENDING)));
    Query.Builder req = Query.newBuilder();
    Results<ScoredDocument> resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 10, 10);
    ScoredDocument[] results = resp.getResults().toArray(new ScoredDocument[0]);

    checkScores(results,
        15, 15, 150,
        25, 0, 150,
        25, 0, 150,
        25, 0, 100,
        25, 0, 150,
        25, 0, 200,
        30, 15, 150,
        30, 15, 150,
        50, 25, 150,
        60, 30, 150
        );

    checkDocumentsOrder(results,
        doc2,  // 15, 15/the dog, 150

        doc8,  // 25, 20/the mouse, 150
        doc4,  // 25, 20/the dog, 150
        doc9,  // 25, 20/the cat, 100
        doc6,  // 25, 20/the cat, 150
        doc10, // 25, 20/the cat, 200

        doc7,  // 30, 15/the mouse, 150
        doc5,  // 30, 15/the cat, 150

        doc3,  // 50, 25/the dog, 150
        doc1   // 60, 30/the cat, 150
        );

    // Check paging and cursors
    options.setLimit(1).setCursor(Cursor.newBuilder());
    resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 1, 10);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results, doc2); // 15, 15/the dog, 150
    checkScores(results, 15, 15, 150);

    options.setCursor(resp.getCursor()).setLimit(2);
    resp = inboxIndex.search(req.setOptions(options).build("the"));
    assertEquals(StatusCode.OK, resp.getOperationResult().getCode());
    checkResponse(resp, 2, 10);
    results = resp.getResults().toArray(new ScoredDocument[0]);
    checkDocumentsOrder(results,
        doc8,  // 25, 20/the mouse, 150
        doc4); // 25, 20/the dog, 150
    checkScores(results,
        25, 0, 150,
        25, 0, 150);
  }

  @Test
  public void testIndexDocumentsReindex() {
    Document docNoId = Document.newBuilder()
        .addField(Field.newBuilder().setName("subject")
                  .setText("hello world"))
        .build();
    assertEquals(StatusCode.OK, inboxIndex.put(docA).getResults().get(0).getCode());
    assertEquals(StatusCode.OK, inboxIndex.put(docB).getResults().get(0).getCode());
    assertEquals(StatusCode.OK, inboxIndex.put(docNoId).getResults().get(0).getCode());
    Results<ScoredDocument> response = inboxIndex.search("hello");
    checkResponse(response, 3, 3);
    String generatedId = null;
    for (ScoredDocument result : response) {
      generatedId = result.getId();
      if (!(generatedId.equals("doc-1") || generatedId.equals("doc-2"))) {
        // Looking for generated doc id
        break;
      }
    }

    Document newDoc = Document.newBuilder().setId("doc-1")
        .addField(Field.newBuilder().setName("subject")
                  .setText("hello again"))
        .build();
    Document newDoc2 = Document.newBuilder().setId(generatedId)
        .addField(Field.newBuilder().setName("subject")
                  .setText("hello once more"))
        .build();
    assertEquals(StatusCode.OK, inboxIndex.put(newDoc).getResults().get(0).getCode());
    assertEquals(StatusCode.OK, inboxIndex.put(newDoc2).getResults().get(0).getCode());
    response = inboxIndex.search("hello");
    checkSearchResults(response, 3, 3, docB, newDoc, newDoc2);
  }

  public Document docId(String id) {
    return Document.newBuilder().setId(id).build();
  }

  @Test
  public void testListDocuments() {
    indexDocuments(docC, docA, docB);
    GetRequest.Builder req = GetRequest.newBuilder();
    GetResponse<Document> resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).containsExactly(docA, docB, docC).inOrder();

    req.setLimit(2);
    resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).containsExactly(docA, docB).inOrder();

    req.setStartId("doc-2");
    resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).containsExactly(docB, docC).inOrder();

    req.setIncludeStart(false);
    resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).containsExactly(docC);

    req.setReturningIdsOnly(true);
    resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).containsExactly(docId("doc-3"));

    req.setStartId("doc-3");
    resp = inboxIndex.getRange(req.build());
    assertThat(resp.getResults()).isEmpty();
  }

  /** Test for listing documents on an empty index. This tracks regressions on http://b/5951087 */
  @Test
  public void testListDocumentsEmptyIndex() {
    Index testIndex = SearchServiceFactory.getSearchService().getIndex(
        IndexSpec.newBuilder().setName("empty-index").build());
    GetResponse<Document> resp = testIndex.getRange(GetRequest.newBuilder().build());
    assertEquals(0, resp.getResults().size());
  }

  @Test
  public void testFieldAndDocumentLocales() throws Exception {
    Document document = Document.newBuilder().setId("french-field").addField(
        Field.newBuilder().setName("field").setLocale(Locale.FRENCH)
        .setText("C'est la vie").build()).build();
    assertEquals(document.getOnlyField("field").getLocale(), Locale.FRENCH);

    indexDocuments(document);
    assertEquals(Locale.FRENCH, inboxIndex.get("french-field").getOnlyField("field").getLocale());

    document = Document.newBuilder().setId("french").setLocale(Locale.FRENCH).addField(
        Field.newBuilder().setName("field").setText("Mais oui")).build();
    indexDocuments(document);

    assertEquals(Locale.FRENCH, inboxIndex.get("french").getLocale());

    Results<ScoredDocument> resp = inboxIndex.search("field:oui");
    assertEquals(1, resp.getResults().size());

    ScoredDocument scored = resp.iterator().next();
    assertEquals(Locale.FRENCH, scored.getLocale());
    assertEquals(0, scored.getFieldCount("_LOCALE"));
  }

  @Test
  public void testSearchDiacriticals() {
    Document doc1 = Document.newBuilder().setId("diacriticals-1")
        .addField(Field.newBuilder().setName("f1").setText("Français"))
        .addField(Field.newBuilder().setName("f2").setText("Björk"))
        .build();

    Document doc2 = Document.newBuilder().setId("diacriticals-1")
        .addField(Field.newBuilder().setName("f1").setText("Francais"))
        .addField(Field.newBuilder().setName("f2").setText("Bjork"))
        .build();

    indexDocuments(doc1, doc2);
    Results<ScoredDocument> response;

    response = inboxIndex.search("français");
    checkSearchResultIds(response, doc1);

    response = inboxIndex.search("francais");
    checkSearchResultIds(response, doc2);

    response = inboxIndex.search("björk");
    checkSearchResultIds(response, doc1);

    response = inboxIndex.search("bjork");
    checkSearchResultIds(response, doc2);
  }

  @Test
  public void testNumberLocales() {
    // The German locale will encode the number 10.1 as "10,1"
    Locale.setDefault(Locale.GERMAN);

    Document doc1 = Document.newBuilder().setId("number-locales-1")
        .addField(Field.newBuilder().setName("f1").setNumber(10.1))
        .build();
    indexDocuments(doc1);

    Results<ScoredDocument> response = inboxIndex.search("f1:10.1");
    checkSearchResults(response, 1, 1, doc1);

    // Restore state after test completes
    Locale.setDefault(Locale.ENGLISH);
  }

  @Test
  public void testFieldRegression() {
    Field stacktrace = Field.newBuilder()
        .setName("stacktrace")
        .setText("java.lang.RuntimeException: test died")
        .build();

    Document document = Document.newBuilder()
        .addField(stacktrace).build();
    indexDocuments(document);

    assertEquals(1, inboxIndex.search("stacktrace:java").getResults().size());
    assertEquals(1, inboxIndex.search(
        "stacktrace:java stacktrace:lang stacktrace:RuntimeException").getResults().size());
    assertEquals(1, inboxIndex.search("java lang RuntimeException").getResults().size());
    assertEquals(1, inboxIndex.search("java.lang.RuntimeException").getResults().size());
    assertEquals(1, inboxIndex.search(
        "RuntimeException.lang.java.RuntimeException").getResults().size());

    assertEquals(1, inboxIndex.search("stacktrace:java.lang.RuntimeException").getResults().size());
    assertEquals(1, inboxIndex.search(
        "stacktrace:RuntimeException.lang.java.RuntimeException").getResults().size());
    assertEquals(1, inboxIndex.search(
        "stacktrace:\"java.lang.RuntimeException: test died\"").getResults().size());
    assertEquals(1, inboxIndex.search(
        "stacktrace:\"java.lang.RuntimeException\"").getResults().size());
  }

  @Test
  public void testMixedCaseFields() {
    Document d1 = Document.newBuilder()
        .addField(Field.newBuilder().setName("companyId").setText("108190245178618693539"))
        .addField(Field.newBuilder().setName("tagIds").setText("gold"))
        .addField(Field.newBuilder().setName("tagIds").setText("silver"))
        .build();
    Document d2 = Document.newBuilder()
        .addField(Field.newBuilder().setName("companyId").setText("other thing"))
        .addField(Field.newBuilder().setName("tagIds").setText("bronze"))
        .addField(Field.newBuilder().setName("tagIds").setText("gold"))
        .build();
    indexDocuments(d1, d2);

    assertEquals(1, inboxIndex.search("companyId:108190245178618693539").getResults().size());
    assertEquals(2, inboxIndex.search("(tagIds:bronze OR tagIds:silver)").getResults().size());
  }

  private static void assertSearchResultKeys(Index index, String query, String... expectedIds) {
    Collection<ScoredDocument> results = index.search(query).getResults();
    List<String> actualIds = new ArrayList<>();
    for (ScoredDocument document : results) {
      actualIds.add(document.getId());
    }
    assertThat(actualIds).containsExactlyElementsIn(expectedIds);
  }

  @Test
  public void testAtomWithSpecialChars() {
    Document withColon = Document.newBuilder()
      .setId("withColon")
      .addField(Field.newBuilder().setName("xyz").setAtom("test:atom"))
      .build();
    Document withoutUnderscoreAtom = Document.newBuilder()
      .setId("withoutUnderscore")
      .addField(Field.newBuilder().setName("name").setAtom("AB-CD"))
      .build();
    Document withUnderscoreAtom = Document.newBuilder()
      .setId("withUnderscoreAtom")
      .addField(Field.newBuilder().setName("name").setAtom("AB_CD"))
      .build();
    Document withUnderscoreText = Document.newBuilder()
      .setId("withUnderscoreText")
      .addField(Field.newBuilder().setName("name").setText("AB_CD"))
      .build();

    indexDocuments(withColon, withoutUnderscoreAtom, withUnderscoreAtom, withUnderscoreText);

    assertSearchResultKeys(inboxIndex, "xyz:\"test:atom\"", "withColon");
    assertSearchResultKeys(inboxIndex, "\"test:atom\"", "withColon");
    assertSearchResultKeys(inboxIndex, "AB-CD", "withoutUnderscore");
    assertSearchResultKeys(inboxIndex, "AB_CD", "withUnderscoreAtom", "withUnderscoreText");
    assertSearchResultKeys(inboxIndex, "name:\"AB_CD\"",
        "withUnderscoreAtom", "withUnderscoreText");
  }

  @Test
  public void testInvalidExpression() {
    try {
      inboxIndex.search(
          Query.newBuilder().setOptions(
              QueryOptions.newBuilder().addExpressionToReturn(
                  FieldExpression.newBuilder().setName("x").setExpression("1 < 2 > 3")))
          .build("test"));
      fail("Unparsable expressions should raise an exception.");
    } catch (IllegalArgumentException e) {
      // expected.
    }
  }

  @Test
  public void testUnicodeCombining() {
    Document d = Document.newBuilder().setId("somethingDifferent")
        .addField(Field.newBuilder().setName("weird").setText("\u0301"))
        .addField(Field.newBuilder().setName("weird").setText("foo\u0301bar"))
        .build();
    indexDocuments(d);
    // absence of a RuntimeException means this test passes.
  }

  static FacetResult makeFacetResult(String name, Object... values) {
    FacetResult.Builder resultBuilder = FacetResult.newBuilder();
    resultBuilder.setName(name);
    for (int i = 0; i < values.length; i += 2) {
      String label = (String) values[i];
      int count = (int) values[i + 1];
      FacetRefinement ref = null;
      if (label.startsWith("[")) {
        // its a range label
        String startString = label.substring(1, label.indexOf(","));
        String endString = label.substring(
            label.indexOf(",") + 1,
            label.indexOf(")"));
        boolean hasStart = !startString.equals("-Infinity");
        boolean hasEnd = !endString.equals("Infinity");
        if (hasStart) {
          if (hasEnd) {
            ref = FacetRefinement.withRange(name, FacetRange.withStartEnd(
                Double.parseDouble(startString), Double.parseDouble(endString)));
          } else {
            ref = FacetRefinement.withRange(name, FacetRange.withStart(
                Double.parseDouble(startString)));
          }
        } else {
          if (hasEnd) {
            ref = FacetRefinement.withRange(name, FacetRange.withEnd(
                Double.parseDouble(endString)));
          } else {
            fail("range should have at least start or end.");
          }
        }
      } else {
        ref = FacetRefinement.withValue(name, label);
      }
      resultBuilder.addValue(FacetResultValue.create(label, count, ref.toTokenString()));
    }
    return resultBuilder.build();
  }

  private void indexFacetDocuments() {
    Results<ScoredDocument> response;
    indexDocuments(fdoc1, fdoc2, fdoc3, fdoc4, fdoc5, fdoc6, fdoc7, fdoc8, fdoc9, fdoc10, fdoc11,
        fdoc12);
    response = inboxIndex.search("foo:bar");
    checkSearchResults(response, 11, 11,
        fdoc1, fdoc2, fdoc3, fdoc4, fdoc5, fdoc6, fdoc7, fdoc8, fdoc9, fdoc10, fdoc11);
  }

  @Test
  public void testAutoDiscoverFacetsOnly() throws Exception {
  Results<ScoredDocument> response;
  indexFacetDocuments();

  response = inboxIndex.search(Query.newBuilder()
      .setEnableFacetDiscovery(true).build("foo:bar"));
  assertEquals(Arrays.asList(new FacetResult[] {
      makeFacetResult("type", "movie", 6, "wine", 5),
      makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1),
      makeFacetResult("rating", "[1.5,4.0)", 6),
      makeFacetResult("year", "[1995.0,2011.0)", 6),
      makeFacetResult("wine_type", "white", 3, "red", 2),
      makeFacetResult("vintage", "[1898.0,1995.0)", 5)}).toString(),
      response.getFacets().toString());

  response = inboxIndex.search(Query.newBuilder()
      .setEnableFacetDiscovery(true)
      .setFacetOptions(FacetOptions.newBuilder().setDiscoveryLimit(2).build()).build("foo:bar"));
  assertEquals(Arrays.asList(new FacetResult[] {
      makeFacetResult("type", "movie", 6, "wine", 5),
      makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1)}).toString(),
      response.getFacets().toString());
  }

  @Test
  public void testManualFacetsWithNameOnly() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .addReturnFacet("type")
        .addReturnFacet("rating")
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("type", "movie", 6, "wine", 5),
        makeFacetResult("rating", "[1.5,4.0)", 6)
        }).toString(),
        response.getFacets().toString());
    response = inboxIndex.search(Query.newBuilder()
        .addReturnFacet("type")
        .addReturnFacet("rating")
        .setEnableFacetDiscovery(true)
        .setFacetOptions(FacetOptions.newBuilder().setDiscoveryLimit(2).build())
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("type", "movie", 6, "wine", 5),
        makeFacetResult("rating", "[1.5,4.0)", 6),
        makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1),
        makeFacetResult("year", "[1995.0,2011.0)", 6)
        }).toString(),
        response.getFacets().toString());
  }

  @Test
  public void testManualFacetsWithValueConstraint() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .addReturnFacet(FacetRequest.newBuilder()
            .setName("genre")
            .addValueConstraint("sci-fi").addValueConstraint("fantasy")
            .build())
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("genre", "fantasy", 4, "sci-fi", 2)
        }).toString(),
        response.getFacets().toString());
  }

  @Test
  public void testManualFacetsWithValueLimit() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .addReturnFacet(FacetRequest.newBuilder()
            .setName("genre")
            .setValueLimit(1)
            .build())
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("genre", "fantasy", 4)
        }).toString(),
        response.getFacets().toString());
  }

  @Test
  public void testManualFacetsWithRange() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .addReturnFacet(FacetRequest.newBuilder()
            .setName("year")
            .addRange(FacetRange.withEnd(2000.0))
            .addRange(FacetRange.withStartEnd(2000.0, 2005.0))
            .addRange(FacetRange.withStart(2005.0))
            .build())
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("year", "[2005.0,Infinity)", 3, "[2000.0,2005.0)", 2,
            "[-Infinity,2000.0)", 1)
        }).toString(),
        response.getFacets().toString());
  }

  @Test
  public void testFacetDepth() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .setEnableFacetDiscovery(true)
        .setFacetOptions(FacetOptions.newBuilder().setDepth(1).build())
        .build("foo:bar"));
    assertEquals(Arrays.asList(new FacetResult[] {
        makeFacetResult("genre", "sci-fi", 1),
        makeFacetResult("rating", "[3.5,3.5)", 1),
        makeFacetResult("type", "movie", 1),
        makeFacetResult("year", "[1995.0,1995.0)", 1)
        }).toString(),
        response.getFacets().toString());
  }

  @Test
  public void testRefineResults() throws Exception {
    Results<ScoredDocument> response;
    indexFacetDocuments();
    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withValue("type", "wine"))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc3, fdoc5, fdoc6, fdoc7, fdoc8);

    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withRange("rating", FacetRange.withStart(2.0)))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc1, fdoc2, fdoc9, fdoc10, fdoc11);

    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withRange("rating", FacetRange.withStartEnd(2.0, 3.5)))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc2, fdoc11);

    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withRange("rating", FacetRange.withEnd(2.1)))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc2, fdoc4);

    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withValue("type", "movie"))
        .addFacetRefinement(FacetRefinement.withValue("year", "2000.0"))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc4);

    response = inboxIndex.search(Query.newBuilder()
        .addFacetRefinement(FacetRefinement.withValue("type", "movie"))
        .addFacetRefinement(FacetRefinement.withValue("year", "2000.0"))
        .addFacetRefinement(FacetRefinement.withValue("year", "1995.0"))
        .build("foo:bar"));
    checkSearchResultIds(response, fdoc1, fdoc4);
  }

  private List<String> getResultIds(QueryOptions queryOptions, String queryString) {
    List<String> ids = new ArrayList<>();
    Cursor cursor = Cursor.newBuilder().build();
    do {
      // build options and query
      queryOptions = QueryOptions.newBuilder(queryOptions).setCursor(cursor).build();
      Query query = Query.newBuilder().setOptions(queryOptions).build(queryString);

      // search at least once
      Results<ScoredDocument> results = inboxIndex.search(query);
      for (ScoredDocument match : results) {
        ids.add(match.getId());
      }
      cursor = results.getCursor();
    } while (cursor != null);
    return ids;
  }

  // Test for b/119662376
  @Test
  public void testQueryCursorWithSortExpression_withTies() {
    List<String> docIds = new ArrayList<>();
    List<String> sortedFirst = new ArrayList<>();
    List<String> sortedLast = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String docId = "doc" + i;
      Document.Builder builder = Document.newBuilder().setId(docId);
      // For this test case, we need there to be ties in the sort order, so we set the 'foo' field
      // to either 0 or 1 for each document.
      int sortOrder = i % 2;
      builder.addField(Field.newBuilder().setName("foo").setNumber(sortOrder));
      inboxIndex.put(builder.build());
      docIds.add(docId);
      if (sortOrder == 0) {
        sortedFirst.add(docId);
      } else {
        sortedLast.add(docId);
      }
    }

    QueryOptions queryOptions =
        QueryOptions.newBuilder()
            .setSortOptions(
                SortOptions.newBuilder()
                    .addSortExpression(
                        SortExpression.newBuilder()
                            .setExpression("foo")
                            .setDirection(SortExpression.SortDirection.ASCENDING)))
            .setLimit(5)
            .build();

    List<String> resultIds = getResultIds(queryOptions, "");
    assertThat(resultIds).containsExactlyElementsIn(docIds);
    assertThat(resultIds.subList(0, 10)).containsExactlyElementsIn(sortedFirst);
    assertThat(resultIds.subList(10, 20)).containsExactlyElementsIn(sortedLast);
  }

  @Test
  public void testQueryCursorWithSortExpression() throws Exception {
    List<String> docIds = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String docId = "doc" + i;
      Document.Builder builder = Document.newBuilder().setId(docId);
      builder.addField(Field.newBuilder().setName("foo").setText("N" + i));
      inboxIndex.put(builder.build());
      docIds.add(docId);
    }

    List<String> expectedIds = new ArrayList<>(docIds);
    Collections.sort(expectedIds);

    // define query options
    SortExpression sortExpression = SortExpression.newBuilder()
        .setExpression("foo")
        .setDirection(SortExpression.SortDirection.ASCENDING)
        .setDefaultValue("")
        .build();
    SortOptions sortOptions = SortOptions.newBuilder()
        .addSortExpression(sortExpression)
        .build();
    QueryOptions queryOptions = QueryOptions.newBuilder()
        .setSortOptions(sortOptions)
        .build();

    assertEquals(expectedIds, getResultIds(queryOptions, ""));

    // Lower limit means batching takes place.
    queryOptions = QueryOptions.newBuilder(queryOptions).setLimit(5).build();
    assertEquals(expectedIds, getResultIds(queryOptions, ""));

    // We now try with an explicit scoring limit.
    sortOptions = SortOptions.newBuilder()
        .addSortExpression(sortExpression)
        .setLimit(20)
        .build();
    queryOptions = QueryOptions.newBuilder(queryOptions).setSortOptions(sortOptions).build();
    assertEquals(expectedIds, getResultIds(queryOptions, ""));

    // No cursor required due to there being fewer results than the limit.
    queryOptions = QueryOptions.newBuilder(queryOptions).setLimit(20).build();
    assertEquals(expectedIds, getResultIds(queryOptions, ""));

    // Lower limit means batching takes place.
    queryOptions = QueryOptions.newBuilder(queryOptions).setLimit(10).build();
    assertEquals(expectedIds, getResultIds(queryOptions, ""));
  }
}
