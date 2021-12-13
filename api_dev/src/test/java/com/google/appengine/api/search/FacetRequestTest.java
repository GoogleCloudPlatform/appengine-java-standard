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
import com.google.common.base.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for FacetRequest */
@RunWith(JUnit4.class)
public class FacetRequestTest extends FacetTestBase {

  @Test
  public void testName_validName() {
    for (String name : generateValidNames()) {
      assertThat(FacetRequest.newBuilder().setName(name).build().getName()).isEqualTo(name);
    }
  }

  @Test
  public void testName_invalidName() {
    for (String name : generateInvalidNames()) {
      assertThrows(IllegalArgumentException.class, () -> FacetRequest.newBuilder().setName(name));
    }
    assertThrows(NullPointerException.class, () -> FacetRequest.newBuilder().setName(null));
  }

  @Test
  public void testValueConstraint_validConstraint() {
    String[] validConstraints = {
      "a", Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH), "1234", "!1234"
    };

    for (String value : validConstraints) {
      assertThat(
              FacetRequest.newBuilder()
                  .setName("name")
                  .addValueConstraint(value)
                  .build()
                  .getValueConstraints()
                  .get(0))
          .isEqualTo(value);
    }
  }

  @Test
  public void testValueConstraint_invalidConstraint() {
    String[] invalidConstraints = {
      "", Strings.repeat("a", SearchApiLimits.MAXIMUM_ATOM_LENGTH + 1)
    };
    for (String value : invalidConstraints) {
      assertThrows(
          IllegalArgumentException.class,
          () -> FacetRequest.newBuilder().setName("name").addValueConstraint(value));
    }
    assertThrows(
        NullPointerException.class,
        () -> FacetRequest.newBuilder().setName("name").addValueConstraint(null));
  }

  @Test
  public void testvalueLimit() {
    assertThat(
            (int)
                FacetRequest.newBuilder().setName("name").setValueLimit(1).build().getValueLimit())
        .isEqualTo(1);
    assertThat(
            (int)
                FacetRequest.newBuilder()
                    .setName("name")
                    .setValueLimit(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT)
                    .build()
                    .getValueLimit())
        .isEqualTo(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FacetRequest.newBuilder()
                .setName("name")
                .setValueLimit(SearchApiLimits.FACET_MAXIMUM_VALUE_LIMIT + 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> FacetRequest.newBuilder().setName("name").setValueLimit(0));
  }

  @Test
  public void testConstraintAndRange() {
    FacetRequest.Builder builder1 = FacetRequest.newBuilder().setName("name");
    builder1.addValueConstraint("value");
    assertThrows(IllegalStateException.class, () -> builder1.addRange(FacetRange.withStart(10.0)));

    FacetRequest.Builder builder2 = FacetRequest.newBuilder().setName("name");
    builder2.addRange(FacetRange.withStart(10.0));
    assertThrows(IllegalStateException.class, () -> builder2.addValueConstraint("value"));

    // Too many constraints
    FacetRequest.Builder builder3 = FacetRequest.newBuilder().setName("name");
    for (int i = 0; i < SearchApiLimits.FACET_MAXIMUM_CONSTRAINTS; i++) {
      builder3.addValueConstraint("value");
    }
    assertThrows(IllegalStateException.class, () -> builder3.addValueConstraint("value"));

    // Too many ranges
    FacetRequest.Builder builder4 = FacetRequest.newBuilder().setName("name");
    for (int i = 0; i < SearchApiLimits.FACET_MAXIMUM_RANGES; i++) {
      builder4.addRange(FacetRange.withStart(10.0));
    }

    assertThrows(IllegalStateException.class, () -> builder4.addRange(FacetRange.withStart(10.0)));
  }

  @Test
  public void test_copyToProtocolBuffer() {
    SearchServicePb.FacetRequest requestPb =
        FacetRequest.newBuilder()
            .setName("name")
            .setValueLimit(15)
            .addValueConstraint("value1")
            .addValueConstraint("value2")
            .addValueConstraint("value3")
            .build()
            .copyToProtocolBuffer();
    assertThat(requestPb.getName()).isEqualTo("name");
    assertThat(requestPb.getParams().getValueLimit()).isEqualTo(15);
    assertThat(requestPb.getParams().getRangeList()).isEmpty();
    assertThat(requestPb.getParams().getValueConstraintCount()).isEqualTo(3);
    assertThat(requestPb.getParams().getValueConstraint(0)).isEqualTo("value1");
    assertThat(requestPb.getParams().getValueConstraint(1)).isEqualTo("value2");
    assertThat(requestPb.getParams().getValueConstraint(2)).isEqualTo("value3");

    requestPb =
        FacetRequest.newBuilder()
            .setName("name")
            .addRange(FacetRange.withStart(10.0))
            .addRange(FacetRange.withEnd(20.0))
            .build()
            .copyToProtocolBuffer();
    assertThat(requestPb.getName()).isEqualTo("name");
    assertThat(requestPb.getParams().hasValueLimit()).isFalse();
    assertThat(requestPb.getParams().getValueConstraintList()).isEmpty();
    assertThat(requestPb.getParams().getRangeCount()).isEqualTo(2);
    assertThat(requestPb.getParams().getRange(0).getStart()).isEqualTo("10.0");
    assertThat(requestPb.getParams().getRange(1).getEnd()).isEqualTo("20.0");
  }
}
