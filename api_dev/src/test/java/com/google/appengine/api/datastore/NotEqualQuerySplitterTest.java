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
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the functionality of {@link NotEqualQuerySplitter}.
 *
 */
@RunWith(JUnit4.class)
public class NotEqualQuerySplitterTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  NotEqualQuerySplitter splitter = new NotEqualQuerySplitter();
  Query q;

  @Before
  public void setUp() throws Exception {
    Key ancestor = KeyFactory.createKey("parent kind", 33L);
    q = new Query("yar", ancestor);
    q.setKeysOnly();
  }

  @Test
  public void testFilterPredicateNotFound() {
    // also proves that splitter does not modify the remainingFilters in this case
    assertThat(
            splitter.split(
                ImmutableList.of(new FilterPredicate("prop", Query.FilterOperator.EQUAL, 1)),
                ImmutableList.<SortPredicate>of()))
        .isEmpty();
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testMultipleNotEqualNotAllowed() {
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 30);
    q.addFilter("color", Query.FilterOperator.NOT_EQUAL, "red");

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, this::performSimpleSplit);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("Queries with NOT_EQUAL filters on different properties are not supported.");
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testNotEqualAndFirstSortOrderMustBeSame() {
    q.addFilter("age", Query.FilterOperator.EQUAL, 30);
    q.addFilter("color", Query.FilterOperator.NOT_EQUAL, "red");
    q.addSort("age");

    assertThrows(IllegalArgumentException.class, this::performSimpleSplit);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSplitFilter() {
    q.addFilter("color", Query.FilterOperator.EQUAL, "red");
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 44L);
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 30L);

    List<FilterPredicate> remainingFilters = new ArrayList<>(q.getFilterPredicates());
    QuerySplitComponent result = splitter.split(remainingFilters, q.getSortPredicates()).get(0);
    assertThat(remainingFilters).containsExactly(q.getFilterPredicates().get(0));
    assertThat(result.getDirection()).isNull();

    FilterPredicate[][] expected = {
      {new FilterPredicate("age", FilterOperator.LESS_THAN, 30L)},
      {
        new FilterPredicate("age", FilterOperator.GREATER_THAN, 30L),
        new FilterPredicate("age", FilterOperator.LESS_THAN, 44L)
      },
      {new FilterPredicate("age", FilterOperator.GREATER_THAN, 44L)},
    };

    assertFilters(expected, result.getFilters());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSplitFilterDesc() {
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 30L);
    q.addFilter("color", Query.FilterOperator.EQUAL, "red");
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 44L);
    q.addSort("age", Query.SortDirection.DESCENDING);

    List<FilterPredicate> remainingFilters = new ArrayList<>(q.getFilterPredicates());
    QuerySplitComponent result = splitter.split(remainingFilters, q.getSortPredicates()).get(0);
    assertThat(remainingFilters)
        .containsExactly(new FilterPredicate("color", Query.FilterOperator.EQUAL, "red"));
    assertThat(result.getDirection()).isEqualTo(SortDirection.DESCENDING);

    FilterPredicate[][] expected = {
      {new FilterPredicate("age", FilterOperator.GREATER_THAN, 44L)},
      {
        new FilterPredicate("age", FilterOperator.LESS_THAN, 44L),
        new FilterPredicate("age", FilterOperator.GREATER_THAN, 30L)
      },
      {new FilterPredicate("age", FilterOperator.LESS_THAN, 30L)},
    };

    assertFilters(expected, result.getFilters());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSplitFilter_Null() {
    q.addFilter("color", Query.FilterOperator.EQUAL, "red");
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, null);
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 44L);

    List<FilterPredicate> remainingFilters = new ArrayList<>(q.getFilterPredicates());
    QuerySplitComponent result = splitter.split(remainingFilters, q.getSortPredicates()).get(0);
    assertThat(remainingFilters).containsExactly(q.getFilterPredicates().get(0));
    assertThat(result.getDirection()).isNull();
    FilterPredicate[][] expected = {
      {
        new FilterPredicate("age", FilterOperator.GREATER_THAN, null),
        new FilterPredicate("age", FilterOperator.LESS_THAN, 44L)
      },
      {new FilterPredicate("age", FilterOperator.GREATER_THAN, 44L)},
    };

    assertFilters(expected, result.getFilters());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testSplitFilterDesc_Null() {
    q.addFilter("color", Query.FilterOperator.EQUAL, "red");
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, null);
    q.addFilter("age", Query.FilterOperator.NOT_EQUAL, 44L);
    q.addSort("age", Query.SortDirection.DESCENDING);

    List<FilterPredicate> remainingFilters = new ArrayList<>(q.getFilterPredicates());
    QuerySplitComponent result = splitter.split(remainingFilters, q.getSortPredicates()).get(0);
    assertThat(remainingFilters).containsExactly(q.getFilterPredicates().get(0));
    assertThat(result.getDirection()).isEqualTo(SortDirection.DESCENDING);
    FilterPredicate[][] expected = {
      {new FilterPredicate("age", FilterOperator.GREATER_THAN, 44L)},
      {
        new FilterPredicate("age", FilterOperator.LESS_THAN, 44L),
        new FilterPredicate("age", FilterOperator.GREATER_THAN, null)
      }
    };

    assertFilters(expected, result.getFilters());
  }

  @SuppressWarnings("deprecation")
  private QuerySplitComponent performSimpleSplit() {
    List<FilterPredicate> remainingFilters = new ArrayList<>(q.getFilterPredicates());
    return splitter.split(remainingFilters, q.getSortPredicates()).get(0);
  }

  private void assertFilters(
      FilterPredicate[][] expected, List<List<FilterPredicate>> listOfFilters) {
    assertThat(listOfFilters).hasSize(expected.length);
    for (int i = 0; i < listOfFilters.size(); ++i) {
      List<FilterPredicate> filters = listOfFilters.get(i);
      assertThat(filters).hasSize(expected[i].length);
      for (int j = 0; j < filters.size(); ++j) {
        assertThat(filters.get(j)).isEqualTo(expected[i][j]);
      }
    }
  }
}
