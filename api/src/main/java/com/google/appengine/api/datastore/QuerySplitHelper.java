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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Helper class for query splitting.
 *
 * <p>This class creates a {@link MultiQueryBuilder} that will produce a sequence of lists of
 * queries. Each list of queries in the sequence should have their results merged (this list could
 * consist of a single query, which can just be executed normally). The results of each list should
 * then be concatenated with the next list in the sequence.
 *
 * <p>This class guarantees that the result of merging the result in the manner described above will
 * produce a valid result set.
 *
 * <p>The algorithm employed here is as efficient as possible. It has been optimized to favor
 * concatenation over merging results in memory, as concatenating results allows for greater
 * leveraging of limit, prefetch, and count parameters. This should also improve the performance
 * slightly when compared to a query splitter algorithm that attempts to merge all result as the
 * results for all queries are fetched synchronously. Even when we start to use the async query
 * framework the only loss of speed would be the time saved by the first async prefetch for each set
 * of queries. This potential loss is both small and greatly out weighed by the value of respecting
 * limit, prefetch and count more accurately. It can also be eliminated by starting the next round
 * of queries before the last is done.
 *
 * <p>There are also many situations where all queries can be done sequentially. In these cases we
 * can also support sorts on keys_only queries.
 *
 * <p>This class does not preserve the order in which the filters appear in the query provided it.
 *
 * <p>As the number of queries that need to be executed to generate the result set is
 * mult_i(|component_i.filters|) (which grows very fast) we rely on {@link #MAX_PARALLEL_QUERIES} to
 * limit the number of queries that need to be run in parallel.
 *
 */
final class QuerySplitHelper {
  private QuerySplitHelper() {}

  private static final int MAX_PARALLEL_QUERIES = 30;
  private static final Collection<QuerySplitter> QUERY_SPLITTERS =
      Collections.synchronizedCollection(
          Arrays.<QuerySplitter>asList(new NotEqualQuerySplitter(), new InQuerySplitter()));

  /**
   * Splits the provided {@link Query} into a list of datastore supported sub-queries using the
   * default set of {@link QuerySplitter}s.
   *
   * @return the resulting list of {@code MultiQueryBuilder}.
   */
  static List<MultiQueryBuilder> splitQuery(Query query) {
    return splitQuery(query, QUERY_SPLITTERS);
  }

  /**
   * Splits the provided {@link Query} into a list of datastore supported sub-queries.
   *
   * @param query the query to split
   * @param splitters the splitters to use
   * @return the resulting list of {@code MultiQueryBuilder}.
   */
  @SuppressWarnings("deprecation")
  static List<MultiQueryBuilder> splitQuery(Query query, Collection<QuerySplitter> splitters) {
    List<MultiQueryBuilder> result;
    if (query.getFilter() == null) {
      result = Collections.singletonList(splitQuery(query.getFilterPredicates(), query, splitters));
    } else {
      Set<Set<FilterPredicate>> dnf = getDisjunctiveNormalForm(query.getFilter());
      result = Lists.newArrayListWithCapacity(dnf.size());
      for (Set<FilterPredicate> filters : dnf) {
        result.add(splitQuery(filters, query, splitters));
      }
    }

    int totalParallelQueries = 0;
    for (MultiQueryBuilder builder : result) {
      totalParallelQueries += builder.getParallelQuerySize();
    }

    checkArgument(
        totalParallelQueries <= MAX_PARALLEL_QUERIES,
        "Splitting the provided query requires that too many subqueries are merged in memory.");
    return result;
  }

  /**
   * Splits a single list of {@link FilterPredicate}s.
   *
   * @param filters the filters to split
   * @param baseQuery the base query to consider
   * @param splitters the splitters to use
   * @return the resulting list of {@code MultiQueryBuilder}.
   */
  static MultiQueryBuilder splitQuery(
      Collection<FilterPredicate> filters, Query baseQuery, Collection<QuerySplitter> splitters) {
    List<FilterPredicate> remainingFilters = Lists.newLinkedList(filters);
    List<QuerySplitComponent> components = Lists.newArrayList();
    for (QuerySplitter splitter : splitters) {
      components.addAll(splitter.split(remainingFilters, baseQuery.getSortPredicates()));
    }

    return new MultiQueryBuilder(
        remainingFilters, components, !baseQuery.getSortPredicates().isEmpty());
  }

  /**
   * Returns the disjunctive normal form of the given filter.
   *
   * @return A set of sets of filters, where the outer set should be combined with OR and the inner
   *     sets should be combined with AND.
   */
  static Set<Set<FilterPredicate>> getDisjunctiveNormalForm(Filter filter) {
    if (filter instanceof CompositeFilter) {
      return getDisjunctiveNormalForm((CompositeFilter) filter);
    } else if (filter instanceof FilterPredicate) {
      return Collections.<Set<FilterPredicate>>singleton(
          Sets.newLinkedHashSet(ImmutableSet.of((FilterPredicate) filter)));
    }

    throw new IllegalArgumentException("Unknown expression type: " + filter.getClass());
  }

  /**
   * @return the disjunctive normal form of the given composite filter
   * @see #getDisjunctiveNormalForm(Filter)
   */
  static Set<Set<FilterPredicate>> getDisjunctiveNormalForm(CompositeFilter filter) {
    switch (filter.getOperator()) {
      case AND:
        return getDisjunctiveNormalFormAnd(filter.getSubFilters());
      case OR:
        return getDisjunctiveNormalFormOr(filter.getSubFilters());
    }
    throw new IllegalArgumentException("Unknown expression operator: " + filter.getOperator());
  }

  /**
   * @return the disjunctive normal form of the given sub filters using the distributive law
   * @see #getDisjunctiveNormalForm(Filter)
   */
  static Set<Set<FilterPredicate>> getDisjunctiveNormalFormAnd(Collection<Filter> subFilter) {
    Set<FilterPredicate> predicates = Sets.newLinkedHashSetWithExpectedSize(subFilter.size());
    Set<Set<FilterPredicate>> result = null;
    for (Filter subExp : subFilter) {
      if (subExp instanceof FilterPredicate) {
        predicates.add((FilterPredicate) subExp);
      } else if (subExp instanceof CompositeFilter) {
        Set<Set<FilterPredicate>> dnf = getDisjunctiveNormalForm((CompositeFilter) subExp);
        if (result == null) {
          result = dnf;
        } else {
          // Apply the distributive law: (X or Y) and (A or B) becomes
          // (X and A) or (X and B) or (Y and A) or (Y and B).
          Set<Set<FilterPredicate>> combinedDnf =
              Sets.newLinkedHashSetWithExpectedSize(dnf.size() * result.size());
          for (Set<FilterPredicate> rhs : result) {
            for (Set<FilterPredicate> lhs : dnf) {
              Set<FilterPredicate> combined =
                  Sets.newLinkedHashSetWithExpectedSize(rhs.size() + lhs.size());
              combined.addAll(rhs);
              combined.addAll(lhs);
              combinedDnf.add(combined);
            }
          }
          result = combinedDnf;
        }
      } else {
        throw new IllegalArgumentException("Unknown expression type: " + subExp.getClass());
      }
    }

    if (result == null) {
      return Collections.singleton(predicates);
    }

    if (!predicates.isEmpty()) {
      // Apply half of the distributive law: (X or Y) and A becomes
      // (X and A) or (Y and A).
      for (Set<FilterPredicate> clause : result) {
        clause.addAll(predicates);
      }
    }
    return result;
  }

  /**
   * @return the disjunctive normal form of the given sub filters by flattening them
   * @see #getDisjunctiveNormalForm(Filter)
   */
  static Set<Set<FilterPredicate>> getDisjunctiveNormalFormOr(Collection<Filter> subFilters) {
    Set<Set<FilterPredicate>> result = Sets.newLinkedHashSetWithExpectedSize(subFilters.size());

    for (Filter subExp : subFilters) {
      result.addAll(getDisjunctiveNormalForm(subExp));
    }
    return result;
  }
}
