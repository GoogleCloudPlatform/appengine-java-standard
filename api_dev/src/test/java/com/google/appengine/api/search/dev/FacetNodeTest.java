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

import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link FacetNode}. */
@RunWith(JUnit4.class)
public class FacetNodeTest {

  @Test
  public void testFacetNode() throws Exception {
    FacetNode node = new FacetNode("name", 12);
    assertThat(node.name).isEqualTo("name");
    assertThat(node.valueLimit).isEqualTo(12);
    assertThat(node.getCount()).isEqualTo(0);
    node.addValue("label", 5);
    assertThat(node.getCount()).isEqualTo(5);
    assertThat(node.getValues()).hasSize(1);
    assertThat(node.getValue("label").label).isEqualTo("label");
    assertThat(node.getValue("label").range).isNull();
    assertThat(node.getValue("label").getCount()).isEqualTo(5);
    node.addValue("label2", 3);
    assertThat(node.getCount()).isEqualTo(8);
    assertThat(node.getValues()).hasSize(2);
    assertThat(node.getValue("label2").label).isEqualTo("label2");
    assertThat(node.getValue("label2").range).isNull();
    assertThat(node.getValue("label2").getCount()).isEqualTo(3);
  }

  @Test
  public void testFacetNodeWithNumericValue() throws Exception {
    FacetNode node = new FacetNode("name", 10);
    assertThat(node.getMin()).isNull();
    assertThat(node.getMax()).isNull();
    assertThat(node.getMinMaxCount()).isEqualTo(0);
    node.addNumericValue(10);
    assertThat(node.getMin()).isEqualTo(10.0);
    assertThat(node.getMax()).isEqualTo(10.0);
    assertThat(node.getMinMaxCount()).isEqualTo(1);
    node.addNumericValue(5);
    assertThat(node.getMin()).isEqualTo(5.0);
    assertThat(node.getMax()).isEqualTo(10.0);
    assertThat(node.getMinMaxCount()).isEqualTo(2);
  }

  @Test
  public void testFacetNodeWithRangeValue() throws Exception {
    FacetNode node = new FacetNode("name", 12);
    node.addValue("label", 2, FacetRange.newBuilder().setStart("10.0").build());
    assertThat(node.getCount()).isEqualTo(2);
    assertThat(node.getValues()).hasSize(1);
    assertThat(node.getValue("label").label).isEqualTo("label");
    assertThat(node.getValue("label").range.getStart()).isEqualTo("10.0");
    assertThat(node.getValue("label").getCount()).isEqualTo(2);
  }
}
