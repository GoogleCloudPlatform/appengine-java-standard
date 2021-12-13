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

package com.google.appengine.api.datastore;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.datastore.Index.Property;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the {@link Index} class.
 *
 */
@RunWith(JUnit4.class)
public class IndexTest {

  private static final Property PROPERTY_1 = new Property("p1", SortDirection.ASCENDING);
  private static final Property PROPERTY_2 = new Property("p2", SortDirection.DESCENDING);
  private static final Index INDEX_1 = new Index(10, "Comment", true, ImmutableList.<Property>of());
  private static final Index INDEX_2 =
      new Index(20, "Blog", false, ImmutableList.of(PROPERTY_1, PROPERTY_2));

  @Test
  public void testConstructor() {
    assertThat(INDEX_1.getId()).isEqualTo(10);
    assertThat(INDEX_1.getKind()).isEqualTo("Comment");
    assertThat(INDEX_1.isAncestor()).isTrue();
    assertThat(INDEX_1.getProperties()).isEmpty();
    assertThat(INDEX_2.getKind()).isEqualTo("Blog");
    assertThat(INDEX_2.isAncestor()).isFalse();
    assertThat(INDEX_2.getProperties()).containsExactly(PROPERTY_1, PROPERTY_2).inOrder();
  }

  @Test
  public void testHashCode() {
    Index index = new Index(20, "Blog", false, ImmutableList.of(PROPERTY_1, PROPERTY_2));
    assertThat(INDEX_1.hashCode()).isNotEqualTo(index.hashCode());
    assertThat(new Index(20, "Blog", false, ImmutableList.of(PROPERTY_2, PROPERTY_1)).hashCode())
        .isNotEqualTo(index.hashCode());
    assertThat(INDEX_2.hashCode()).isEqualTo(index.hashCode());
  }

  @Test
  public void testEqualsObject() {
    Index index = new Index(20, "Blog", false, ImmutableList.of(PROPERTY_1, PROPERTY_2));
    assertThat(INDEX_1).isNotEqualTo(index);
    assertThat(new Index(10, "Blog", false, ImmutableList.of(PROPERTY_2, PROPERTY_1)))
        .isNotEqualTo(index);
    assertThat(INDEX_2).isEqualTo(index);
  }

  @Test
  public void testToString() {
    assertThat(INDEX_1.toString()).isEqualTo("INDEX [10] ON Comment() INCLUDES ANCESTORS");
    assertThat(INDEX_2.toString()).isEqualTo("INDEX [20] ON Blog(p1 ASCENDING, p2 DESCENDING)");
  }
}
