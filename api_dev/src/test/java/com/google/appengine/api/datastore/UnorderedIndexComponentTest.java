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

import static com.google.common.collect.Collections2.permutations;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link UnorderedIndexComponent} class.
 *
 */
@RunWith(JUnit4.class)
public class UnorderedIndexComponentTest extends IndexComponentTestCase {
  private IndexComponent unorderedComponent;

  @Before
  public void setUp() {
    unorderedComponent = new UnorderedIndexComponent(Sets.newHashSet("P5", "P6", "P4"));
  }

  /** Tests that any ordering can satisfy the unordered components. */
  @Test
  public void testOrdering() {
    List<Property> indexProperties = newIndex("P4", "P5", "P6");

    for (List<Property> props : permutations(indexProperties)) {
      assertThat(unorderedComponent.matches(props)).isTrue();
    }
  }

  /** Tests that duplicate values do not get matched by the unordered component. */
  @Test
  public void testNoDuplicates() {
    List<Property> index = newIndex("P4", "P5", "P5", "P6");

    assertThat(unorderedComponent.matches(index)).isFalse();
  }

  /** Tests that we can match against the suffix of an index.. */
  @Test
  public void testPartialMatch() {
    List<Property> index = newIndex("P4", "P4", "P5", "P6");

    assertThat(unorderedComponent.matches(index)).isTrue();
  }

  /** Tests that we don't match against an index that doesn't satisfy the component. */
  @Test
  public void testIncompleteMatch() {
    List<Property> index = newIndex("P4", "P5");

    assertThat(unorderedComponent.matches(index)).isFalse();
  }

  /** Tests that the preferred index is correct. */
  @Test
  public void testPreferredIndexProperties() {
    List<Property> index = newIndex("P4", "P5", "P6");

    // The preferred index should be in lexicographically ascending order with direction ASC.
    assertThat(unorderedComponent.preferredIndexProperties()).isEqualTo(index);
  }

  /** Tests that the size is equal to the number of constraints. */
  @Test
  public void testSize() {
    assertThat(unorderedComponent.size()).isEqualTo(3);
  }
}
