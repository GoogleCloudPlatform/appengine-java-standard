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

import com.google.appengine.api.search.proto.SearchServicePb.FacetAutoDetectParam;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRefinement;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRefinement.Range;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequest;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequestParam;
import com.google.appengine.api.search.proto.SearchServicePb.FacetResult;
import com.google.appengine.api.search.proto.SearchServicePb.FacetResultValue;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.appengine.api.search.proto.SearchServicePb.SearchResult;
import com.google.apphosting.api.search.DocumentPb;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link SimpleFacet}. */
@RunWith(JUnit4.class)
public class SimpleFacetTest {

  DocumentPb.Document fdoc1;
  DocumentPb.Document fdoc2;
  DocumentPb.Document fdoc3;
  DocumentPb.Document fdoc4;
  DocumentPb.Document fdoc5;
  DocumentPb.Document fdoc6;
  DocumentPb.Document fdoc7;
  DocumentPb.Document fdoc8;
  DocumentPb.Document fdoc9;
  DocumentPb.Document fdoc10;
  DocumentPb.Document fdoc11;

  private DocumentPb.Facet facet(String name, String value) {
    return DocumentPb.Facet.newBuilder()
        .setName(name)
        .setValue(
            DocumentPb.FacetValue.newBuilder()
                .setType(DocumentPb.FacetValue.ContentType.ATOM)
                .setStringValue(value))
        .build();
  }

  private DocumentPb.Facet facet(String name, Double value) {
    return DocumentPb.Facet.newBuilder()
        .setName(name)
        .setValue(
            DocumentPb.FacetValue.newBuilder()
                .setType(DocumentPb.FacetValue.ContentType.NUMBER)
                .setStringValue(Double.toString(value)))
        .build();
  }

  @Before
  public void setUp() {
    DocumentPb.Field oneField =
        DocumentPb.Field.newBuilder()
            .setName("foo")
            .setValue(
                DocumentPb.FieldValue.newBuilder()
                    .setType(DocumentPb.FieldValue.ContentType.ATOM)
                    .setStringValue("bar"))
            .build();
    fdoc1 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-1")
            .addField(oneField)
            .addFacet(facet("genre", "sci-fi"))
            .addFacet(facet("rating", 3.5))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 1995.0))
            .build();
    fdoc2 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-2")
            .addField(oneField)
            .addFacet(facet("genre", "fantasy"))
            .addFacet(facet("rating", 2.0))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 2003.0))
            .build();
    fdoc3 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-3")
            .addField(oneField)
            .addFacet(facet("wine_type", "red"))
            .addFacet(facet("type", "wine"))
            .addFacet(facet("vintage", 1991.0))
            .build();
    fdoc4 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-4")
            .addField(oneField)
            .addFacet(facet("genre", "kids"))
            .addFacet(facet("genre", "fantasy"))
            .addFacet(facet("rating", 1.5))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 2000.0))
            .build();
    fdoc5 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-5")
            .addField(oneField)
            .addFacet(facet("wine_type", "white"))
            .addFacet(facet("type", "wine"))
            .addFacet(facet("vintage", 1995.0))
            .build();
    fdoc6 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-6")
            .addField(oneField)
            .addFacet(facet("wine_type", "white"))
            .addFacet(facet("type", "wine"))
            .addFacet(facet("vintage", 1898.0))
            .build();
    fdoc7 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-7")
            .addField(oneField)
            .addFacet(facet("wine_type", "white"))
            .addFacet(facet("type", "wine"))
            .addFacet(facet("vintage", 1990.0))
            .build();
    fdoc8 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-8")
            .addField(oneField)
            .addFacet(facet("wine_type", "red"))
            .addFacet(facet("type", "wine"))
            .addFacet(facet("vintage", 1988.0))
            .build();
    fdoc9 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-9")
            .addField(oneField)
            .addFacet(facet("genre", "fantasy"))
            .addFacet(facet("rating", 4.0))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 2010.0))
            .build();
    fdoc10 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-10")
            .addField(oneField)
            .addFacet(facet("genre", "fantasy"))
            .addFacet(facet("rating", 3.9))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 2011.0))
            .build();
    fdoc11 =
        DocumentPb.Document.newBuilder()
            .setId("fdoc-11")
            .addField(oneField)
            .addFacet(facet("genre", "sci-fi"))
            .addFacet(facet("rating", 2.9))
            .addFacet(facet("type", "movie"))
            .addFacet(facet("year", 2009.0))
            .build();
  }

  static class Result extends Scorer.Result {
    public Result(Document doc) {
      super(doc);
    }

    @Override
    public void addScores(SearchResult.Builder resultBuilder) {}
  }

  Scorer.Result[] createResult(DocumentPb.Document... docs) {
    List<Result> ret = new ArrayList<>(docs.length);
    for (DocumentPb.Document doc : docs) {
      ret.add(new Result(LuceneUtils.toLuceneDocument(doc.getId(), doc)));
    }
    return ret.toArray(new Result[0]);
  }

  Scorer.Result[] createTestResult() {
    return createResult(
        fdoc1, fdoc2, fdoc3, fdoc4, fdoc5, fdoc6, fdoc7, fdoc8, fdoc9, fdoc10, fdoc11);
  }

  static FacetResult makeFacetResult(String name, Object... values) {
    FacetResult.Builder resultBuilder = FacetResult.newBuilder();
    resultBuilder.setName(name);
    for (int i = 0; i < values.length; i += 2) {
      String label = (String) values[i];
      int count = (int) values[i + 1];
      FacetRefinement.Builder ref = FacetRefinement.newBuilder();
      ref.setName(name);
      if (label.startsWith("[")) {
        Range.Builder range = ref.getRangeBuilder();
        // its a range label
        String startString = label.substring(1, label.indexOf(','));
        String endString = label.substring(label.indexOf(',') + 1, label.indexOf(')'));
        if (!startString.equals("-Infinity")) {
          range.setStart(startString);
        }
        if (!endString.equals("Infinity")) {
          range.setEnd(endString);
        }
      } else {
        ref.setValue(label);
      }
      resultBuilder.addValue(
          FacetResultValue.newBuilder().setName(label).setCount(count).setRefinement(ref.build()));
    }
    return resultBuilder.build();
  }

  private void checkFacetResult(FacetResult[] expected, FacetResult[] actual) {
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testAutoDiscoverFacetsOnly() throws Exception {
    FacetResult[] result;
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder().setAutoDiscoverFacetCount(10).buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {
          makeFacetResult("type", "movie", 6, "wine", 5),
          makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1),
          makeFacetResult("rating", "[1.5,4.0)", 6),
          makeFacetResult("year", "[1995.0,2011.0)", 6),
          makeFacetResult("wine_type", "white", 3, "red", 2),
          makeFacetResult("vintage", "[1898.0,1995.0)", 5)
        },
        result);
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder().setAutoDiscoverFacetCount(2).buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {
          makeFacetResult("type", "movie", 6, "wine", 5),
          makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1)
        },
        result);
  }

  @Test
  public void testManualFacetsWithNameOnly() throws Exception {
    FacetResult[] result;
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder()
                .addIncludeFacet(FacetRequest.newBuilder().setName("type"))
                .addIncludeFacet(FacetRequest.newBuilder().setName("rating"))
                .buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {
          makeFacetResult("type", "movie", 6, "wine", 5), makeFacetResult("rating", "[1.5,4.0)", 6)
        },
        result);
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder()
                .addIncludeFacet(FacetRequest.newBuilder().setName("type"))
                .addIncludeFacet(FacetRequest.newBuilder().setName("rating"))
                .setAutoDiscoverFacetCount(2)
                .buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {
          makeFacetResult("type", "movie", 6, "wine", 5),
          makeFacetResult("rating", "[1.5,4.0)", 6),
          makeFacetResult("genre", "fantasy", 4, "sci-fi", 2, "kids", 1),
          makeFacetResult("year", "[1995.0,2011.0)", 6)
        },
        result);
  }

  @Test
  public void testManualFacetsWithValueConstraint() throws Exception {
    FacetResult[] result;
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("genre")
                        .setParams(
                            FacetRequestParam.newBuilder()
                                .addValueConstraint("sci-fi")
                                .addValueConstraint("fantasy")))
                .buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {makeFacetResult("genre", "fantasy", 4, "sci-fi", 2)}, result);
  }

  @Test
  public void testManualFacetsWithValueLimit() throws Exception {
    FacetResult[] result;
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("genre")
                        .setParams(FacetRequestParam.newBuilder().setValueLimit(1)))
                .buildPartial(),
            createTestResult());
    checkFacetResult(new FacetResult[] {makeFacetResult("genre", "fantasy", 4)}, result);
  }

  @Test
  public void testManualFacetsWithRange() throws Exception {
    FacetResult[] result;
    result =
        SimpleFacet.getFacetResult(
            SearchParams.newBuilder()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("year")
                        .setParams(
                            FacetRequestParam.newBuilder()
                                .addRange(FacetRange.newBuilder().setEnd("2000.0"))
                                .addRange(
                                    FacetRange.newBuilder().setStart("2000.0").setEnd("2005.0"))
                                .addRange(FacetRange.newBuilder().setStart("2005.0"))))
                .buildPartial(),
            createTestResult());
    checkFacetResult(
        new FacetResult[] {
          makeFacetResult(
              "year", "[2005.0,Infinity)", 3, "[2000.0,2005.0)", 2, "[-Infinity,2000.0)", 1)
        },
        result);
  }

  @Test
  public void testGetRefinementQuery() throws Exception {
    Query q =
        SimpleFacet.getRefinementQuery(
            SearchParams.newBuilder()
                .addFacetRefinement(FacetRefinement.newBuilder().setName("test").setValue("test"))
                .buildPartial());
    assertThat(q.toString()).isEqualTo("+(facet_ATOM@test:test)");
  }

  @Test
  public void testIsFacetingRequested() throws Exception {
    assertThat(SimpleFacet.isFacetingRequested(SearchParams.newBuilder().buildPartial())).isFalse();
    assertThat(
            SimpleFacet.isFacetingRequested(
                SearchParams.newBuilder()
                    .setFacetAutoDetectParam(FacetAutoDetectParam.newBuilder().setValueLimit(10))
                    .buildPartial()))
        .isFalse();
    assertThat(
            SimpleFacet.isFacetingRequested(
                SearchParams.newBuilder().setFacetDepth(1000).buildPartial()))
        .isFalse();
    assertThat(
            SimpleFacet.isFacetingRequested(
                SearchParams.newBuilder()
                    .addFacetRefinement(
                        FacetRefinement.newBuilder().setName("test").setValue("test"))
                    .buildPartial()))
        .isFalse();
    assertThat(
            SimpleFacet.isFacetingRequested(
                SearchParams.newBuilder().setAutoDiscoverFacetCount(1).buildPartial()))
        .isTrue();
    assertThat(
            SimpleFacet.isFacetingRequested(
                SearchParams.newBuilder()
                    .addIncludeFacet(FacetRequest.newBuilder().setName("facet"))
                    .buildPartial()))
        .isTrue();
  }
}
