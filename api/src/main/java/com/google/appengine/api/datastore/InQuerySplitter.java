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

import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;
import static com.google.appengine.api.datastore.Query.FilterOperator.IN;

import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortPredicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class splits a query with an IN filter using the following logic:
 *
 * <p>Create 1 query for each element in the list of predicate values. Each query has all the same
 * attributes as the original, except we replace the IN filter with an EQUALS filter for one element
 * in the list of predicate values. So say we're given:
 *
 * <pre>{@code
 *  select from Person where age IN (33, 44, 55)
 * }</pre>
 *
 * <p>We would turn this into:
 *
 * <pre>{@code
 *  select from Person where age == 33
 *  select from Person where age == 44
 *  select from Person where age == 55
 * }</pre>
 *
 */
class InQuerySplitter extends BaseQuerySplitter {

  @Override
  public List<QuerySplitComponent> split(
      List<FilterPredicate> remainingFilters, List<SortPredicate> sorts) {
    List<QuerySplitComponent> result = new ArrayList<QuerySplitComponent>();
    Iterator<FilterPredicate> itr = remainingFilters.iterator();
    while (itr.hasNext()) {
      FilterPredicate filter = itr.next();
      if (filter.getOperator() == IN) {
        QuerySplitComponent component = new QuerySplitComponent(filter.getPropertyName(), sorts);
        // Grabbing all the values
        List<ComparableValue> comparableValues = new ArrayList<ComparableValue>();
        for (Object value : (Iterable<?>) filter.getValue()) {
          comparableValues.add(new ComparableValue(value));
        }
        if (comparableValues.size() <= 1) {
          // Skipping as the datastore backend will turn this into an equals
          continue;
        }
        if (component.getDirection() != null) {
          // Sorting the values so the query results can possibly be serialized
          // while maintaining the proper sort order
          Collections.sort(comparableValues, getValueComparator(component.getDirection()));
        }
        // Adding the split filters
        for (ComparableValue value : comparableValues) {
          component.addFilters(
              new FilterPredicate(filter.getPropertyName(), EQUAL, value.getValue()));
        }
        result.add(component);
        itr.remove(); // removing filter as this component handles it
      }
    }

    return result;
  }
}
