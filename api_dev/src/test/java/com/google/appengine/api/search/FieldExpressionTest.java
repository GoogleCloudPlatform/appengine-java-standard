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

/**
 * Tests for {@link FieldExpression}.
 *
 */
@RunWith(JUnit4.class)
public class FieldExpressionTest {

  @Test
  public void testBuild_newFieldExpressionBuilder() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> FieldExpression.newBuilder().build());
  }

  @Test
  public void testSetName_null() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> FieldExpression.newBuilder().setName(null));
  }

  @Test
  public void testSetName_empty() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> FieldExpression.newBuilder().setName(""));
  }

  @Test
  public void testSetName_nameTooLong() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FieldExpression.newBuilder()
                .setName(Strings.repeat("a", SearchApiLimits.MAXIMUM_NAME_LENGTH + 1)));
  }

  @Test
  public void testSetName_reserved() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> FieldExpression.newBuilder().setName("_RESERVED"));
  }

  @Test
  public void testSetExpression_null() throws Exception {
    assertThrows(
        NullPointerException.class, () -> FieldExpression.newBuilder().setExpression(null));
  }

  @Test
  public void testSetExpression_disallowed() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> FieldExpression.newBuilder().setName("tax price"));
  }

  @Test
  public void testSetExpression_parsable() throws Exception {
    FieldExpression expression =
        FieldExpression.newBuilder().setName("tax_price").setExpression("price + tax").build();
    assertThat(expression.getExpression()).isEqualTo("price + tax");
    assertThrows(
        IllegalArgumentException.class,
        () -> FieldExpression.newBuilder().setExpression("price + + tax"));
  }

  @Test
  public void testBuild_minimalSpec() throws Exception {
    FieldExpression spec =
        FieldExpression.newBuilder()
            .setName("snippet")
            .setExpression("snippet(\"good story\", content)")
            .build();
    assertThat(spec.getName()).isEqualTo("snippet");
    assertThat(spec.getExpression()).isEqualTo("snippet(\"good story\", content)");
  }

  @Test
  public void testCopyToProtocolBuffer() throws Exception {
    FieldExpression spec =
        FieldExpression.newBuilder()
            .setName("snippet")
            .setExpression("snippet(\"good story\", content)")
            .build();
    SearchServicePb.FieldSpec.Expression pb = spec.copyToProtocolBuffer().build();
    assertThat(pb.getName()).isEqualTo("snippet");
    assertThat(pb.getExpression()).isEqualTo("snippet(\"good story\", content)");
  }

  @Test
  public void testToString() throws Exception {
    assertThat(
            FieldExpression.newBuilder()
                .setName("name")
                .setExpression("expression")
                .build()
                .toString())
        .isEqualTo("FieldExpression(name=name, expression=expression)");
  }
}
