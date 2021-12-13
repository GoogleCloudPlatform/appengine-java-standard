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

import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the functionality of {@link InQuerySplitter}.
 *
 */
@SuppressWarnings("deprecation")
@RunWith(JUnit4.class)
public class InQuerySplitterTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  private final InQuerySplitter splitter = new InQuerySplitter();
  private Query query;

  @Before
  public void setUp() throws Exception {
    query = new Query("yar", KeyFactory.createKey("parent kind", 33L));
    query.setKeysOnly();
    query.addFilter("other", FilterOperator.GREATER_THAN, 0);
  }

  @Test
  public void testFilterPredicateNotFound() {
    // also proves that splitter does not modify the remainingFilters or entityFilters
    assertThat(
            splitter.split(
                ImmutableList.of(new FilterPredicate("prop", Query.FilterOperator.EQUAL, 1)),
                ImmutableList.<SortPredicate>of()))
        .isEmpty();
  }

  @Test
  public void testDoesNotSplitSingleValueIn() {
    query.addFilter("age", Query.FilterOperator.IN, Lists.newArrayList(31));
    List<FilterPredicate> remainingFilters = new ArrayList<>(query.getFilterPredicates());
    assertThat(splitter.split(remainingFilters, query.getSortPredicates())).isEmpty();
  }

  @Test
  public void testMultipleIn() {
    query.addFilter("age", Query.FilterOperator.IN, Lists.newArrayList(31, 44, 35));
    query.addFilter("color", Query.FilterOperator.IN, Lists.newArrayList("red", "blue", "green"));

    List<FilterPredicate> remainingFilters = new ArrayList<>(query.getFilterPredicates());
    List<QuerySplitComponent> components =
        splitter.split(remainingFilters, query.getSortPredicates());

    assertRemainingFilters(remainingFilters);
    assertComponents(
        components,
        Lists.newArrayList("age", "color"),
        Lists.newArrayList(31, 44, 35),
        Lists.newArrayList("red", "blue", "green"));
  }

  @Test
  public void testMultipleIn_Sort() {
    query.addFilter("age", Query.FilterOperator.IN, Lists.newArrayList(31, 44, 35));
    query.addFilter("color", Query.FilterOperator.IN, Lists.newArrayList("red", "blue", "green"));

    query.addSort("age");
    query.addSort("color", SortDirection.DESCENDING);

    List<FilterPredicate> remainingFilters = new ArrayList<>(query.getFilterPredicates());
    List<QuerySplitComponent> components =
        splitter.split(remainingFilters, query.getSortPredicates());

    assertRemainingFilters(remainingFilters);
    assertComponents(
        components,
        Lists.newArrayList("age", "color"),
        Lists.newArrayList(31, 35, 44),
        Lists.newArrayList("red", "green", "blue"));
  }

  private void assertComponents(
      List<QuerySplitComponent> actual, List<String> properties, List<?>... expected) {
    assertThat(actual).hasSize(expected.length);
    for (int i = 0; i < expected.length; ++i) {
      QuerySplitComponent component = actual.get(i);
      List<?> values = expected[i];
      assertThat(component.getFilters()).hasSize(values.size());
      for (int j = 0; j < values.size(); ++j) {
        FilterPredicate filter = component.getFilters().get(j).get(0);
        assertThat(filter.getPropertyName()).isEqualTo(properties.get(i));
        assertThat(filter.getOperator()).isEqualTo(Query.FilterOperator.EQUAL);
        assertThat(filter.getValue()).isEqualTo(values.get(j));
      }
    }
  }

  private void assertRemainingFilters(List<FilterPredicate> remainingFilters) {
    assertThat(remainingFilters).containsExactly(query.getFilterPredicates().get(0));
  }
}
