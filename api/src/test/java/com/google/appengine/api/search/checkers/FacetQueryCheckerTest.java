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

package com.google.appengine.api.search.checkers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.FacetAutoDetectParam;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRefinement;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequest;
import com.google.appengine.api.search.proto.SearchServicePb.FacetRequestParam;
import com.google.appengine.api.search.proto.SearchServicePb.IndexSpec;
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.appengine.api.search.proto.SearchServicePb.SearchRequest;
import com.google.common.base.Strings;
import junit.framework.AssertionFailedError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FacetQueryChecker}. */
@RunWith(JUnit4.class)
public class FacetQueryCheckerTest {
  private void assertFailRequest(SearchParams.Builder params) {
    assertFailRequest(params.build());
  }

  private void assertFailRequest(SearchParams params) {
    assertThrows(IllegalArgumentException.class, () -> FacetQueryChecker.checkValid(params));
  }

  private void assertPassRequest(SearchParams.Builder params) {
    assertPassRequest(params.build());
  }

  private void assertPassRequest(SearchParams params) {
    try {
      FacetQueryChecker.checkValid(params);
    } catch (IllegalArgumentException cause) {
      // Preserve the cause by chaining for easier testing.
      AssertionFailedError e = new AssertionFailedError();
      e.initCause(cause);
      throw e;
    }
  }

  interface TestFunction<T> {
    SearchParams.Builder apply(T input);
  }

  private <T> void runTest(T[] valid, T[] invalid, TestFunction<? super T> f) {
    for (T v : valid) {
      assertPassRequest(f.apply(v));
    }
    for (T v : invalid) {
      assertFailRequest(f.apply(v));
    }
  }

  private static SearchParams.Builder newSearchParams() {
    return SearchParams.newBuilder()
        .setIndexSpec(IndexSpec.newBuilder().setName("foo").build())
        .setQuery("")
        .setAutoDiscoverFacetCount(10)
        // Facet depth is ignored if less than scorer limit.  Since scorer specs are not
        // used in any existing facet tests, force value to 0 to ensure it doesn't disable
        // facet depth.
        .setScorerSpec(SearchServicePb.ScorerSpec.newBuilder().setLimit(0));
  }

  @Test
  public void testFacetDepth() {
    runTest(
        new Integer[] {1, 10000},
        new Integer[] {10001, -1},
        new TestFunction<Integer>() {
          @Override
          public SearchParams.Builder apply(Integer input) {
            return newSearchParams().setFacetDepth(input);
          }
        });
  }

  @Test
  public void testAutoDiscoverFacetCount() {
    runTest(
        new Integer[] {1, 100},
        new Integer[] {101},
        new TestFunction<Integer>() {
          @Override
          public SearchParams.Builder apply(Integer input) {
            return newSearchParams().setAutoDiscoverFacetCount(input);
          }
        });
  }

  Integer[] validValueLimit = {1, 100};
  Integer[] invalidValueLimit = {0, -1, 101};

  @Test
  public void testAutoDetectParamValueLimit() {
    runTest(
        validValueLimit,
        invalidValueLimit,
        new TestFunction<Integer>() {
          @Override
          public SearchParams.Builder apply(Integer input) {
            return newSearchParams()
                .setFacetAutoDetectParam(FacetAutoDetectParam.newBuilder().setValueLimit(input));
          }
        });
  }

  String[] validNames = {"a", "Test", "Test123", Strings.repeat("a", 500)};

  String[] invalidNames = {"", "123", Strings.repeat("a", 501)};

  @Test
  public void testRequestName() {
    runTest(
        validNames,
        invalidNames,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams().addIncludeFacet(FacetRequest.newBuilder().setName(input));
          }
        });
  }

  @Test
  public void testRequestValueLimit() {
    runTest(
        validValueLimit,
        invalidValueLimit,
        new TestFunction<Integer>() {
          @Override
          public SearchParams.Builder apply(Integer input) {
            return newSearchParams()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("test")
                        .setParams(FacetRequestParam.newBuilder().setValueLimit(input)));
          }
        });
  }

  @Test
  public void testRequestRangeCount() {
    FacetRequestParam.Builder param = FacetRequestParam.newBuilder();
    for (int i = 0; i < 50; i++) {
      param.addRange(FacetRange.newBuilder().setStart("0.0"));
    }
    assertPassRequest(
        newSearchParams()
            .addIncludeFacet(FacetRequest.newBuilder().setName("test").setParams(param)));
    param.addRange(FacetRange.newBuilder().setStart("0.0"));
    assertFailRequest(
        newSearchParams()
            .addIncludeFacet(FacetRequest.newBuilder().setName("test").setParams(param)));
  }

  @Test
  public void testRequestRangeName() {
    runTest(
        new String[] {""},
        new String[] {"a", "abc"},
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("test")
                        .setParams(
                            FacetRequestParam.newBuilder()
                                .addRange(FacetRange.newBuilder().setName(input).setStart("0"))));
          }
        });
  }

  String[] validRangeValue = {"1.0", "-1.0", "1.1e5", "0"};
  String[] invalidRangeValue = {"NaN", "-Infinite", "Infinite", ""};

  @Test
  public void testRequestRangeStart() {
    runTest(
        validRangeValue,
        invalidRangeValue,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("test")
                        .setParams(
                            FacetRequestParam.newBuilder()
                                .addRange(FacetRange.newBuilder().setStart(input))));
          }
        });
  }

  @Test
  public void testRequestRangeEnd() {
    runTest(
        validRangeValue,
        invalidRangeValue,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("test")
                        .setParams(
                            FacetRequestParam.newBuilder()
                                .addRange(FacetRange.newBuilder().setEnd(input))));
          }
        });
  }

  @Test
  public void testRequestUnboundedRange() {
    assertFailRequest(
        newSearchParams()
            .addIncludeFacet(
                FacetRequest.newBuilder()
                    .setName("test")
                    .setParams(
                        FacetRequestParam.newBuilder().addRange(FacetRange.getDefaultInstance()))));
  }

  @Test
  public void testRequestValueConstraints() {
    runTest(
        new String[] {"a", Strings.repeat("a", 500)},
        new String[] {"", Strings.repeat("a", 501)},
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("test")
                        .setParams(FacetRequestParam.newBuilder().addValueConstraint(input)));
          }
        });
  }

  @Test
  public void testRefinementName() {
    runTest(
        validNames,
        invalidNames,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addFacetRefinement(FacetRefinement.newBuilder().setName(input).setValue("test"));
          }
        });
  }

  @Test
  public void testRefinementValue() {
    runTest(
        new String[] {"a", Strings.repeat("a", 500)},
        new String[] {"", Strings.repeat("a", 501)},
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addFacetRefinement(FacetRefinement.newBuilder().setName("test").setValue(input));
          }
        });
  }

  @Test
  public void testRefinementRangeStart() {
    runTest(
        validRangeValue,
        invalidRangeValue,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addFacetRefinement(
                    FacetRefinement.newBuilder()
                        .setName("test")
                        .setRange(FacetRefinement.Range.newBuilder().setStart(input)));
          }
        });
  }

  @Test
  public void testRefinementRangeEnd() {
    runTest(
        validRangeValue,
        invalidRangeValue,
        new TestFunction<String>() {
          @Override
          public SearchParams.Builder apply(String input) {
            return newSearchParams()
                .addFacetRefinement(
                    FacetRefinement.newBuilder()
                        .setName("test")
                        .setRange(FacetRefinement.Range.newBuilder().setEnd(input)));
          }
        });
  }

  @Test
  public void testRefinement_valid() throws Exception {
    good(withRefinement("m"));
  }

  @Test
  public void testRefinement_empty() throws Exception {
    facetValueEmpty(withRefinement(""));
  }

  @Test
  public void testConstraint_valid() throws Exception {
    good(withConstraint("m"));
  }

  @Test
  public void testConstraint_empty() throws Exception {
    facetValueEmpty(withConstraint(""));
  }

  /** Base search params with required fields filled in. */
  private static final SearchParams SEARCH_PARAMS =
      SearchParams.newBuilder()
          .setQuery("q")
          .setIndexSpec(IndexSpec.newBuilder().setName("i"))
          .build();

  /** Creates a request with a FacetRequest having the given value constraint. */
  private SearchRequest withConstraint(String valueConstraint) {
    return SearchRequest.newBuilder()
        .setParams(
            SEARCH_PARAMS.toBuilder()
                .addIncludeFacet(
                    FacetRequest.newBuilder()
                        .setName("n")
                        .setParams(
                            FacetRequestParam.newBuilder().addValueConstraint(valueConstraint))))
        .build();
  }

  /**
   * Creates a request with a facet refinement having the given value (possibly empty but not
   * missing).
   */
  private SearchRequest withRefinement(String refinementValue) {
    return SearchRequest.newBuilder()
        .setParams(
            SEARCH_PARAMS.toBuilder()
                .addFacetRefinement(
                    FacetRefinement.newBuilder().setName("n").setValue(refinementValue)))
        .build();
  }

  private void good(SearchRequest req) {
    FacetQueryChecker.checkValid(req.getParams());
  }

  private void facetValueEmpty(SearchRequest req) {
    IllegalArgumentException expected =
        assertThrows(
            IllegalArgumentException.class, () -> FacetQueryChecker.checkValid(req.getParams()));
    assertThat(expected).hasMessageThat().isEqualTo("Facet value is empty");
  }
}
