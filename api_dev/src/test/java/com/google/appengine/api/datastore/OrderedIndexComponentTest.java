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

import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Direction;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link OrderedIndexComponent} class.
 *
 */
@RunWith(JUnit4.class)
public class OrderedIndexComponentTest extends IndexComponentTestCase {

  private IndexComponent orderedComponent;

  @Before
  public void setUp() {
    orderedComponent =
        new OrderedIndexComponent(
            Lists.newArrayList(newProperty("P1"), newProperty("P2"), newProperty("P3")));
  }

  /** Tests that ordered components will be satisfied by ordered indexes. */
  @Test
  public void testSimple() {
    List<Property> index = newIndex("P1", "P2", "P3");

    assertThat(orderedComponent.matches(index)).isTrue();
    assertThat(orderedComponent.size()).isEqualTo(3);
  }

  /** Tests that an ordered component will not match unless all constraints are satisfied. */
  @Test
  public void testIncompleteMatch() {
    List<Property> index = newIndex("P2", "P3");

    assertThat(orderedComponent.matches(index)).isFalse();
  }

  /** Tests that ordered components can partially match an index. */
  @Test
  public void testPartialMatch() {
    List<Property> index = newIndex("P1", "P2", "P3");

    IndexComponent orderedComponent =
        new OrderedIndexComponent(Lists.newArrayList(newProperty("P2"), newProperty("P3")));

    assertThat(orderedComponent.matches(index)).isTrue();
  }

  /** Tests that the ordered components obey property direction. */
  @Test
  public void testDirection() {
    List<Property> index = newIndex("P1", newProperty("P2", Direction.DESCENDING), "P3");

    assertThat(orderedComponent.matches(index)).isFalse();
  }

  /** Tests that properties that do not have directions specified can match on any direction. */
  @Test
  public void testNoDirection() {
    Property noDirectionProperty = Property.newBuilder().setName("P1").build();

    List<Property> indexDesc = newIndex(newProperty("P1", Direction.DESCENDING), "P2", "P3");
    List<Property> indexAsc = newIndex("P1", "P2", "P3");
    List<Property> indexNoDir = newIndex(noDirectionProperty, "P2", "P3");

    IndexComponent directionlessOrderedComponent =
        new OrderedIndexComponent(
            Lists.newArrayList(noDirectionProperty, newProperty("P2"), newProperty("P3")));

    assertThat(directionlessOrderedComponent.matches(indexDesc)).isTrue();
    assertThat(directionlessOrderedComponent.matches(indexAsc)).isTrue();
    assertThat(directionlessOrderedComponent.matches(indexNoDir)).isTrue();
    assertThat(orderedComponent.matches(indexDesc)).isFalse();
    assertThat(orderedComponent.matches(indexAsc)).isTrue();
    assertThat(orderedComponent.matches(indexNoDir)).isFalse();
  }

  /** Tests the preferred index of the component. */
  @Test
  public void testPreferredIndexProperties() {
    List<Property> preferredIndex = newIndex("P1", "P2", "P3");
    assertThat(orderedComponent.preferredIndexProperties()).isEqualTo(preferredIndex);

    Property noDirectionProperty = Property.newBuilder().setName("P1").build();

    IndexComponent directionlessComponent =
        new OrderedIndexComponent(
            Lists.newArrayList(noDirectionProperty, newProperty("P2"), newProperty("P3")));

    // With a directionless property, the preferred index property should have an ASC direction.
    assertThat(directionlessComponent.preferredIndexProperties()).isEqualTo(preferredIndex);
  }

  /** Tests that the size is equal to the number of constraints. */
  @Test
  public void testSize() {
    assertThat(orderedComponent.size()).isEqualTo(3);
  }
}
