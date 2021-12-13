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

import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.AND;
import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.OR;
import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.and;
import static com.google.appengine.api.datastore.Query.CompositeFilterOperator.or;
import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;
import static com.google.appengine.api.datastore.Query.FilterOperator.GREATER_THAN;
import static com.google.appengine.api.datastore.Query.FilterOperator.IN;
import static com.google.appengine.api.datastore.Query.FilterOperator.LESS_THAN;
import static com.google.appengine.api.datastore.Query.FilterOperator.NOT_EQUAL;
import static com.google.appengine.api.datastore.Query.SortDirection.ASCENDING;
import static com.google.appengine.api.datastore.Query.SortDirection.DESCENDING;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the functionality of {@link QuerySplitHelper}.
 *
 */
@RunWith(JUnit4.class)
public class QuerySplitHelperTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  private static final SortPredicate[][] sortTests = {
    // No sort
    {},
    // a Desc
    {new SortPredicate("a", DESCENDING)},
    // a desc, c
    {new SortPredicate("a", DESCENDING), new SortPredicate("c", ASCENDING)},
    // a desc, c desc
    {new SortPredicate("a", DESCENDING), new SortPredicate("b", DESCENDING)},
    // a desc, c, b desc
    {
      new SortPredicate("a", DESCENDING),
      new SortPredicate("c", ASCENDING),
      new SortPredicate("b", DESCENDING)
    },
    // c
    {new SortPredicate("c", ASCENDING)},
  };

  // This splitter determines the number of splits to create based on the
  // last remainingFilter value, which is expected to be an Integer.
  // If the kind is 3 we generate 3 QuerySplitComponents with (3, 2, 1)
  // filters. This will result in a MultiQueryBuilding that produces
  // n! sub-queries.
  private List<QuerySplitComponent> factorialSplitter(
      List<FilterPredicate> remainingFilters, List<SortPredicate> sorts) {
    int splits = (Integer) Iterables.getLast(remainingFilters).getValue();
    List<QuerySplitComponent> result = Lists.newArrayList();
    for (int j = 1; j <= splits; ++j) {
      String name = Integer.toString(j);
      QuerySplitComponent component = new QuerySplitComponent(name, sorts);
      for (int i = 0; i < j; i++) {
        component.addFilters(filter(name, EQUAL, i));
      }
      result.add(component);
    }
    return result;
  }

  // only splits queries with odd kinds
  private List<QuerySplitComponent> oddSplitter(
      List<FilterPredicate> remainingFilters, List<SortPredicate> sorts) {
    int splits = (Integer) remainingFilters.get(0).getValue();
    if (splits % 2 == 1) {
      return factorialSplitter(remainingFilters, sorts);
    }
    return ImmutableList.of();
  }

  // only splits queries with even kinds
  private List<QuerySplitComponent> evenSplitter(
      List<FilterPredicate> remainingFilters, List<SortPredicate> sorts) {
    int splits = (Integer) remainingFilters.get(0).getValue();
    if (splits % 2 == 0) {
      return factorialSplitter(remainingFilters, sorts);
    }
    return ImmutableList.of();
  }

  @Test
  public void testSplittingOneSerial() {
    assertResultsSerial(ImmutableList.of(this::factorialSplitter));
  }

  @Test
  public void testSplittingOneParallel() {
    assertResultsParallel(ImmutableList.of(this::factorialSplitter));
  }

  @Test
  public void testSplittingOneMixed() {
    assertResultsMixed(ImmutableList.of(this::factorialSplitter));
  }

  @Test
  public void testSplittingMultipleSerial() {
    // These tests install 2 splitters but the results are the same because
    // each splitter only handles half the queries.
    assertResultsSerial(Lists.newArrayList(this::oddSplitter, this::evenSplitter));
  }

  @Test
  public void testSplittingMultipleParallel() {
    // These tests install 2 splitters but the results are the same because
    // each splitter only handles half the queries.
    assertResultsParallel(Lists.newArrayList(this::oddSplitter, this::evenSplitter));
  }

  @Test
  public void testSplittingMultipleMixed() {
    // These tests install 2 splitters but the results are the same because
    // each splitter only handles half the queries.
    assertResultsMixed(Lists.newArrayList(this::oddSplitter, this::evenSplitter));
  }

  @SuppressWarnings("deprecation")
  private void assertResultsSerial(List<QuerySplitter> splitters) {
    Query query = new Query();
    List<MultiQueryBuilder> builders;

    query.addFilter("count", EQUAL, 1);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    assertThat(Lists.newArrayList(builders.get(0))).hasSize(1);

    query.addFilter("count", EQUAL, 2);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    assertThat(Lists.newArrayList(builders.get(0))).hasSize(2);

    query.addFilter("count", EQUAL, 3);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    assertThat(Lists.newArrayList(builders.get(0))).hasSize(6);

    query.addFilter("count", EQUAL, 4);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    assertThat(Lists.newArrayList(builders.get(0))).hasSize(24);

    query.addFilter("count", EQUAL, 5);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    assertThat(Lists.newArrayList(builders.get(0))).hasSize(120);
  }

  @SuppressWarnings("deprecation")
  private void assertResultsParallel(List<QuerySplitter> splitters) {
    Query query = new Query();
    query.addSort("other"); // forcing to run in parallel
    List<MultiQueryBuilder> builders;
    List<List<List<FilterPredicate>>> result;
    query.addFilter("count", EQUAL, 1);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(1);

    query.addFilter("count", EQUAL, 2);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(2);

    query.addFilter("count", EQUAL, 3);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(6);

    query.addFilter("count", EQUAL, 4);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(24);

    query.addFilter("count", EQUAL, 5);
    assertThrows(
        IllegalArgumentException.class, () -> QuerySplitHelper.splitQuery(query, splitters));
  }

  @SuppressWarnings("deprecation")
  private void assertResultsMixed(List<QuerySplitter> splitters) {
    Query query = new Query();
    // forcing n > 2 to run in parallel
    query.addSort("1"); // allows 1 to run in serial
    query.addSort("2"); // allows 2 to run in serial
    query.addSort("other"); // should prevent 4 from being run in serial
    query.addSort("4"); // should run in parallel

    List<MultiQueryBuilder> builders;
    List<List<List<FilterPredicate>>> result;
    query.addFilter("count", EQUAL, 1);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).hasSize(1); // All serial

    query.addFilter("count", EQUAL, 2);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(1); // All serial

    query.addFilter("count", EQUAL, 3);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(3); // 3 in parallel

    query.addFilter("count", EQUAL, 4);
    builders = QuerySplitHelper.splitQuery(query, splitters);
    assertThat(builders).hasSize(1);
    result = Lists.newArrayList(builders.get(0));
    assertThat(result).hasSize(2);
    assertThat(result.get(0)).hasSize(12); // 3 * 4 in parallel

    query.addFilter("count", EQUAL, 5);

    assertThrows(
        IllegalArgumentException.class,
        () -> QuerySplitHelper.splitQuery(query, splitters) // 3 * 4 * 5 in parallel
        );
  }

  @Test
  public void testSubExpressionsAnd() {
    Filter exp =
        AND.of(
            AND.of(EQUAL.of("a", 1), EQUAL.of("b", 2)),
            OR.of(GREATER_THAN.of("c", 10), LESS_THAN.of("c", 5)),
            GREATER_THAN.of("c", 0));

    ImmutableSet<ImmutableSet<FilterPredicate>> expected =
        ImmutableSet.of(
            ImmutableSet.of(
                EQUAL.of("a", 1),
                EQUAL.of("b", 2),
                GREATER_THAN.of("c", 10),
                GREATER_THAN.of("c", 0)),
            ImmutableSet.of(
                EQUAL.of("a", 1), EQUAL.of("b", 2), LESS_THAN.of("c", 5), GREATER_THAN.of("c", 0)));

    assertThat(QuerySplitHelper.getDisjunctiveNormalForm(exp)).isEqualTo(expected);
  }

  @Test
  public void testSubExpressionsOr() {
    Filter exp =
        OR.of(
            AND.of(EQUAL.of("a", 1), EQUAL.of("b", 2)),
            OR.of(GREATER_THAN.of("c", 10), LESS_THAN.of("c", 5)),
            GREATER_THAN.of("d", 0));

    ImmutableSet<ImmutableSet<FilterPredicate>> expected =
        ImmutableSet.of(
            ImmutableSet.of(EQUAL.of("a", 1), EQUAL.of("b", 2)),
            ImmutableSet.of(GREATER_THAN.of("c", 10)),
            ImmutableSet.of(LESS_THAN.of("c", 5)),
            ImmutableSet.of(GREATER_THAN.of("d", 0)));
    assertThat(QuerySplitHelper.getDisjunctiveNormalForm(exp)).isEqualTo(expected);
  }

  @Test
  public void testANotEqual() {
    // a != 0
    FilterPredicate[] filters = {filter("a", NOT_EQUAL, 0)};

    FilterPredicate[][][] asc = {{{filter("a", LESS_THAN, 0)}}, {{filter("a", GREATER_THAN, 0)}}};

    FilterPredicate[][][] desc = {{{filter("a", GREATER_THAN, 0)}}, {{filter("a", LESS_THAN, 0)}}};

    assertResults(filters, asc, desc, desc, desc, desc, new IllegalArgumentException());
  }

  @Test
  public void testANotEqualANotEqual() {
    // a != 0 && a != 1
    FilterPredicate[] filters = {filter("a", NOT_EQUAL, 0), filter("a", NOT_EQUAL, 1)};

    FilterPredicate[][][] asc = {
      {{filter("a", LESS_THAN, 0)}},
      {{filter("a", GREATER_THAN, 0), filter("a", LESS_THAN, 1)}},
      {{filter("a", GREATER_THAN, 1)}}
    };

    FilterPredicate[][][] desc = {
      {{filter("a", GREATER_THAN, 1)}},
      {{filter("a", LESS_THAN, 1), filter("a", GREATER_THAN, 0)}},
      {{filter("a", LESS_THAN, 0)}}
    };

    assertResults(filters, asc, desc, desc, desc, desc, new IllegalArgumentException());
  }

  @Test
  public void testANotEqualBNotEqual() {
    // a != 0 && b != 1
    FilterPredicate[] filters = {filter("a", NOT_EQUAL, 0), filter("b", NOT_EQUAL, 1)};

    assertResults(filters, new IllegalArgumentException());
  }

  @Test
  public void testAIn() {
    // a IN 1, 2
    FilterPredicate[] filters = {filter("a", IN, Lists.newArrayList(1, 2))};

    FilterPredicate[][][] asc = {{{filter("a", EQUAL, 1)}}, {{filter("a", EQUAL, 2)}}};

    FilterPredicate[][][] desc = {{{filter("a", EQUAL, 2)}}, {{filter("a", EQUAL, 1)}}};

    FilterPredicate[][][] par = {{{filter("a", EQUAL, 1)}, {filter("a", EQUAL, 2)}}};

    assertResults(filters, asc, desc, desc, desc, desc, par);
  }

  @Test
  public void testManyAEqualAnd() {
    // a = 1 AND a = 2 AND ....
    Query query = new Query("Foo");
    List<Filter> filters = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      filters.add(FilterOperator.EQUAL.of("a", 1));
    }
    query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
    List<MultiQueryBuilder> builders = QuerySplitHelper.splitQuery(query);
    assertThat(builders).hasSize(1);
    assertThat(builders.get(0).getParallelQuerySize()).isEqualTo(1);
  }

  @Test
  public void testAInAIn() {
    // a IN 1, 2 && a IN 3, 4
    FilterPredicate[] filters = {
      filter("a", IN, Lists.newArrayList(1, 2)), filter("a", IN, Lists.newArrayList(3, 4))
    };

    FilterPredicate[][][] asc = {
      {{filter("a", EQUAL, 1), filter("a", EQUAL, 3)}},
      {{filter("a", EQUAL, 1), filter("a", EQUAL, 4)}},
      {{filter("a", EQUAL, 2), filter("a", EQUAL, 3)}},
      {{filter("a", EQUAL, 2), filter("a", EQUAL, 4)}}
    };

    FilterPredicate[][][] desc = {
      {{filter("a", EQUAL, 2), filter("a", EQUAL, 4)}},
      {{filter("a", EQUAL, 2), filter("a", EQUAL, 3)}},
      {{filter("a", EQUAL, 1), filter("a", EQUAL, 4)}},
      {{filter("a", EQUAL, 1), filter("a", EQUAL, 3)}}
    };

    FilterPredicate[][][] par = {
      {
        {filter("a", EQUAL, 1), filter("a", EQUAL, 3)},
        {filter("a", EQUAL, 1), filter("a", EQUAL, 4)},
        {filter("a", EQUAL, 2), filter("a", EQUAL, 3)},
        {filter("a", EQUAL, 2), filter("a", EQUAL, 4)}
      }
    };

    assertResults(filters, asc, desc, desc, desc, desc, par);
  }

  @Test
  public void testAInBIn() {
    // a IN 1, 2 && b IN 3, 4
    FilterPredicate[] filters = {
      filter("a", IN, Lists.newArrayList(1, 2)), filter("b", IN, Lists.newArrayList(3, 4))
    };

    FilterPredicate[][][] asc = {
      {{filter("a", EQUAL, 1), filter("b", EQUAL, 3)}},
      {{filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", EQUAL, 2), filter("b", EQUAL, 4)}}
    };

    FilterPredicate[][][] descPar1 = {
      {
        {filter("a", EQUAL, 2), filter("b", EQUAL, 3)},
        {filter("a", EQUAL, 2), filter("b", EQUAL, 4)}
      },
      {
        {filter("a", EQUAL, 1), filter("b", EQUAL, 3)},
        {filter("a", EQUAL, 1), filter("b", EQUAL, 4)}
      }
    };

    // This is for a desc, c, b desc so b components get sorted desc
    FilterPredicate[][][] descPar2 = {
      {
        {filter("a", EQUAL, 2), filter("b", EQUAL, 4)},
        {filter("a", EQUAL, 2), filter("b", EQUAL, 3)}
      },
      {
        {filter("a", EQUAL, 1), filter("b", EQUAL, 4)},
        {filter("a", EQUAL, 1), filter("b", EQUAL, 3)}
      }
    };

    FilterPredicate[][][] par = {
      {
        {filter("a", EQUAL, 1), filter("b", EQUAL, 3)},
        {filter("a", EQUAL, 1), filter("b", EQUAL, 4)},
        {filter("a", EQUAL, 2), filter("b", EQUAL, 3)},
        {filter("a", EQUAL, 2), filter("b", EQUAL, 4)}
      }
    };

    FilterPredicate[][][] desc = {
      {{filter("a", EQUAL, 2), filter("b", EQUAL, 4)}},
      {{filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", EQUAL, 1), filter("b", EQUAL, 3)}}
    };

    assertResults(filters, asc, descPar1, descPar1, desc, descPar2, par);
  }

  @Test
  public void testANotEqualAIn() {
    // a != 0 && a IN 1, 2
    FilterPredicate[] filters = {
      filter("a", NOT_EQUAL, 0), filter("a", IN, Lists.newArrayList(1, 2))
    };

    FilterPredicate[][][] asc = {
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2)}}
    };

    FilterPredicate[][][] desc = {
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1)}}
    };

    assertResults(filters, asc, desc, desc, desc, desc, new IllegalArgumentException());
  }

  @Test
  public void testANotEqualBIn() {
    // a != 0 && b IN 3, 4
    FilterPredicate[] filters = {
      filter("a", NOT_EQUAL, 0), filter("b", IN, Lists.newArrayList(3, 4))
    };

    FilterPredicate[][][] asc = {
      {{filter("a", LESS_THAN, 0), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("b", EQUAL, 3)}},
      {{filter("a", GREATER_THAN, 0), filter("b", EQUAL, 4)}}
    };

    FilterPredicate[][][] descPar1 = {
      {
        {filter("a", GREATER_THAN, 0), filter("b", EQUAL, 3)},
        {filter("a", GREATER_THAN, 0), filter("b", EQUAL, 4)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("b", EQUAL, 3)},
        {filter("a", LESS_THAN, 0), filter("b", EQUAL, 4)}
      }
    };

    // This is for a desc, c, b desc so b components get sorted desc
    FilterPredicate[][][] descPar2 = {
      {
        {filter("a", GREATER_THAN, 0), filter("b", EQUAL, 4)},
        {filter("a", GREATER_THAN, 0), filter("b", EQUAL, 3)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("b", EQUAL, 4)},
        {filter("a", LESS_THAN, 0), filter("b", EQUAL, 3)}
      }
    };

    FilterPredicate[][][] desc = {
      {{filter("a", GREATER_THAN, 0), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("b", EQUAL, 4)}},
      {{filter("a", LESS_THAN, 0), filter("b", EQUAL, 3)}}
    };

    assertResults(filters, asc, descPar1, descPar1, desc, descPar2, new IllegalArgumentException());
  }

  @Test
  public void testANotEqualAInBIn() {
    // a != 0 && a IN 1, 2 && b IN 3, 4
    FilterPredicate[] filters = {
      filter("a", NOT_EQUAL, 0),
      filter("a", IN, Lists.newArrayList(1, 2)),
      filter("b", IN, Lists.newArrayList(3, 4))
    };

    FilterPredicate[][][] asc = {
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}}
    };

    FilterPredicate[][][] descPar1 = {
      {
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)},
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}
      },
      {
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)},
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)},
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)},
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}
      }
    };

    // This is for a desc, c, b desc so b components get sorted desc
    FilterPredicate[][][] descPar2 = {
      {
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)},
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}
      },
      {
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)},
        {filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)},
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}
      },
      {
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)},
        {filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}
      }
    };

    FilterPredicate[][][] desc = {
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", GREATER_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 4)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 2), filter("b", EQUAL, 3)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 4)}},
      {{filter("a", LESS_THAN, 0), filter("a", EQUAL, 1), filter("b", EQUAL, 3)}}
    };

    assertResults(filters, asc, descPar1, descPar1, desc, descPar2, new IllegalArgumentException());
  }

  @Test
  public void testMaxParallelQueries() {
    List<Long> lower = Lists.newArrayList();
    List<Long> upper = Lists.newArrayList();
    List<Long> all = Lists.newArrayList();

    for (int i = 0; i < 31; ++i) {
      if (i < 15) {
        lower.add(Long.valueOf(i));
      } else {
        upper.add(Long.valueOf(i));
      }
      all.add(Long.valueOf(i));
    }

    Query query = new Query("Foo");
    query.addSort("hi");

    // Works separately.
    query.setFilter(FilterOperator.IN.of("anInteger", lower));
    QuerySplitHelper.splitQuery(query);
    query.setFilter(FilterOperator.IN.of("anInteger", upper));
    QuerySplitHelper.splitQuery(query);

    // Fails together
    query.setFilter(FilterOperator.IN.of("anInteger", all));
    assertThrows(IllegalArgumentException.class, () -> QuerySplitHelper.splitQuery(query));

    query.setFilter(
        or(FilterOperator.IN.of("anInteger", lower), FilterOperator.IN.of("anInteger", upper)));
    assertThrows(IllegalArgumentException.class, () -> QuerySplitHelper.splitQuery(query));
  }

  @Test
  public void testMaxParallelQueries_Dnf() {
    Query query = new Query("Foo");
    query.setFilter(
        and(
            or(FilterOperator.EQUAL.of("aFloat", 1.125), FilterOperator.EQUAL.of("anInteger", 1)),
            or(FilterOperator.EQUAL.of("aFloat", 2.125), FilterOperator.EQUAL.of("anInteger", 2)),
            or(FilterOperator.EQUAL.of("aFloat", 3.125), FilterOperator.EQUAL.of("anInteger", 3)),
            or(FilterOperator.EQUAL.of("aFloat", 4.125), FilterOperator.EQUAL.of("anInteger", 4)),
            or(FilterOperator.EQUAL.of("aFloat", 5.125), FilterOperator.EQUAL.of("anInteger", 5))));

    assertThrows(IllegalArgumentException.class, () -> QuerySplitHelper.splitQuery(query));
  }

  private static FilterPredicate filter(String name, FilterOperator op, Object value) {
    return new FilterPredicate(name, op, value);
  }

  private void assertResults(FilterPredicate[] filters, Object... results) {
    assertThat(results).hasLength(sortTests.length);
    for (int i = 0; i < results.length; ++i) {
      SortPredicate[] sorts = sortTests[i];
      Object result = results[i];
      if (result instanceof Exception) {
        assertResults(filters, sorts, (Exception) result);
      } else if (result instanceof FilterPredicate[][][]) {
        assertResults(filters, sorts, (FilterPredicate[][][]) result);
      } else {
        throw new IllegalArgumentException("Unknown result type: " + result.getClass());
      }
    }
  }

  private void assertResults(FilterPredicate[] filters, Exception result) {
    for (SortPredicate[] sorts : sortTests) {
      assertResults(filters, sorts, result);
    }
  }

  private void assertResults(FilterPredicate[] filters, SortPredicate[] sorts, Exception result) {
    Query query = new Query();
    addToQuery(query, filters, sorts);
    Exception e = assertThrows(Exception.class, () -> QuerySplitHelper.splitQuery(query));
    assertThat(e.getClass()).isSameInstanceAs(result.getClass());
  }

  private void assertResults(
      FilterPredicate[] filters, SortPredicate[] sorts, FilterPredicate[][][] result) {
    String msg = "Sort by " + Arrays.toString(sorts);
    Query splitQuery = new Query();
    addToQuery(splitQuery, filters, sorts);
    List<MultiQueryBuilder> builders = QuerySplitHelper.splitQuery(splitQuery);
    assertThat(builders).hasSize(1);
    int i = 0;
    for (List<List<FilterPredicate>> filtersList : builders.get(0)) {
      int j = 0;
      assertWithMessage(msg + " more serial queries than expected")
          .that(i)
          .isLessThan(result.length);
      for (List<FilterPredicate> actualFilters : filtersList) {
        assertWithMessage(msg + " more parallel queries than expected")
            .that(j)
            .isLessThan(result[i].length);
        Object[] expected = result[i][j];
        assertWithMessage(msg).that(actualFilters).containsExactlyElementsIn(expected);
        ++j;
      }
      assertWithMessage(msg).that(j).isEqualTo(result[i].length);
      ++i;
    }
    assertWithMessage(msg).that(i).isEqualTo(result.length);
  }

  @SuppressWarnings("deprecation")
  private void addToQuery(Query query, FilterPredicate[] filters, SortPredicate[] sorts) {
    for (FilterPredicate filter : filters) {
      query.addFilter(filter.getPropertyName(), filter.getOperator(), filter.getValue());
    }
    for (SortPredicate sort : sorts) {
      query.addSort(sort.getPropertyName(), sort.getDirection());
    }
  }
}
