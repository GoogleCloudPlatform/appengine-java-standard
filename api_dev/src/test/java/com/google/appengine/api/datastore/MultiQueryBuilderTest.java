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

import com.google.appengine.api.datastore.MultiQueryComponent.Order;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the functionality of {@link MultiQueryBuilder}.
 *
 */
@RunWith(JUnit4.class)
public class MultiQueryBuilderTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  List<MultiQueryComponent> components = new ArrayList<MultiQueryComponent>();

  private static FilterPredicate[] simpleFilter(String... names) {
    FilterPredicate[] result = new FilterPredicate[names.length];
    for (int i = 0; i < result.length; ++i) {
      result[i] = new FilterPredicate(names[i], FilterOperator.EQUAL, 0);
    }
    return result;
  }

  private static MultiQueryComponent simpleComponent(
      MultiQueryComponent.Order order, FilterPredicate[]... filters) {
    MultiQueryComponent component;
    component = new MultiQueryComponent(order);
    for (FilterPredicate[] filter : filters) {
      component.addFilters(filter);
    }
    return component;
  }

  private void assertResults(FilterPredicate[][][] expected) {
    MultiQueryBuilder multiQuery =
        new MultiQueryBuilder(Collections.<FilterPredicate>emptyList(), components, -1);
    int i = 0;
    for (List<List<FilterPredicate>> filtersList : multiQuery) {
      assertThat(i).isLessThan(expected.length);
      assertThat(filtersList).hasSize(expected[i].length);
      for (int j = 0; j < filtersList.size(); ++j) {
        List<FilterPredicate> filters = filtersList.get(j);
        assertThat(filters).hasSize(expected[i][j].length);
        for (int k = 0; k < filters.size(); ++k) {
          FilterPredicate filter = filters.get(k);
          assertThat(filter).isEqualTo(expected[i][j][k]);
        }
      }
      ++i;
    }
    assertThat(i).isEqualTo(expected.length);
  }

  @Test
  public void testResultsSerial() {
    components.add(
        simpleComponent(Order.SERIAL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));
    FilterPredicate[][][] expected = {
      {simpleFilter("a")}, {simpleFilter("b")}, {simpleFilter("c")},
    };
    assertResults(expected);
  }

  @Test
  public void testResultsParallel() {
    components.add(
        simpleComponent(Order.PARALLEL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));
    FilterPredicate[][][] expected = {
      {simpleFilter("a"), simpleFilter("b"), simpleFilter("c")},
    };
    assertResults(expected);
  }

  @Test
  public void testResultsSerialParallel() {
    components.add(
        simpleComponent(Order.SERIAL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));
    components.add(
        simpleComponent(Order.PARALLEL, simpleFilter("d"), simpleFilter("e"), simpleFilter("f")));

    FilterPredicate[][][] expected = {
      {simpleFilter("a", "d"), simpleFilter("a", "e"), simpleFilter("a", "f")},
      {simpleFilter("b", "d"), simpleFilter("b", "e"), simpleFilter("b", "f")},
      {simpleFilter("c", "d"), simpleFilter("c", "e"), simpleFilter("c", "f")},
    };
    assertResults(expected);
  }

  @Test
  public void testResultsParallelSerial() {
    components.add(
        simpleComponent(Order.PARALLEL, simpleFilter("d"), simpleFilter("e"), simpleFilter("f")));
    components.add(
        simpleComponent(Order.SERIAL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));

    FilterPredicate[][][] expected = {
      {simpleFilter("d", "a"), simpleFilter("e", "a"), simpleFilter("f", "a")},
      {simpleFilter("d", "b"), simpleFilter("e", "b"), simpleFilter("f", "b")},
      {simpleFilter("d", "c"), simpleFilter("e", "c"), simpleFilter("f", "c")},
    };
    assertResults(expected);
  }

  @Test
  public void testResultsParallelParallel() {
    components.add(
        simpleComponent(Order.PARALLEL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));
    components.add(
        simpleComponent(Order.PARALLEL, simpleFilter("d"), simpleFilter("e"), simpleFilter("f")));

    FilterPredicate[][][] expected = {
      {
        simpleFilter("a", "d"),
        simpleFilter("a", "e"),
        simpleFilter("a", "f"),
        simpleFilter("b", "d"),
        simpleFilter("b", "e"),
        simpleFilter("b", "f"),
        simpleFilter("c", "d"),
        simpleFilter("c", "e"),
        simpleFilter("c", "f")
      },
    };
    assertResults(expected);
  }

  @Test
  public void testResultsSerialSerial() {
    components.add(
        simpleComponent(Order.SERIAL, simpleFilter("a"), simpleFilter("b"), simpleFilter("c")));
    components.add(
        simpleComponent(Order.SERIAL, simpleFilter("d"), simpleFilter("e"), simpleFilter("f")));

    FilterPredicate[][][] expected = {
      {simpleFilter("a", "d")}, {simpleFilter("a", "e")}, {simpleFilter("a", "f")},
      {simpleFilter("b", "d")}, {simpleFilter("b", "e")}, {simpleFilter("b", "f")},
      {simpleFilter("c", "d")}, {simpleFilter("c", "e")}, {simpleFilter("c", "f")},
    };
    assertResults(expected);
  }
}
