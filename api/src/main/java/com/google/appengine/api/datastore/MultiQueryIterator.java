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

import com.google.appengine.api.datastore.MultiQueryComponent.Order;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class constructs lists of filters as defined by the components as needed.
 *
 * <p>It uses both recursive and local stack algorithms so it can save it's position in the query
 * construction algorithm between calls to next.
 *
 */
class MultiQueryIterator implements Iterator<List<List<FilterPredicate>>> {
  private final List<MultiQueryComponent> components;
  private final List<Integer> componentSubIndex;
  private final Deque<List<FilterPredicate>> filtersStack = Queues.newArrayDeque();
  private int componentIndex = 0;
  private int parallelCount = 0;

  private boolean moreResults = true;

  public MultiQueryIterator(
      List<FilterPredicate> baseFilters, List<MultiQueryComponent> components) {
    this.components = components;
    filtersStack.push(baseFilters);

    componentSubIndex = new ArrayList<Integer>(components.size());
    for (@SuppressWarnings("unused") MultiQueryComponent component : components) {
      componentSubIndex.add(0);
    }
  }

  /**
   * Pushes a components filters onto the stack. The stack is cumulative so all filters added to the
   * stack exist in the top element of the stack.
   *
   * @param componentFilters the filters to add to the stack
   */
  private void pushFilters(List<FilterPredicate> componentFilters) {
    List<@Nullable FilterPredicate> baseFilters = filtersStack.getFirst();
    List<@Nullable FilterPredicate> filters =
        new ArrayList<>(baseFilters.size() + componentFilters.size());
    filters.addAll(baseFilters);
    filters.addAll(componentFilters);
    filtersStack.push(filters);
  }

  /**
   * This function updates {@link #componentIndex} to point to the next combination of serial
   * component filters
   *
   * @return false if the next combination has looped back to the first combination
   */
  private boolean advanceSerialComponents() {
    for (int i = components.size() - 1; i >= 0; --i) {
      MultiQueryComponent component = components.get(i);
      if (component.getOrder() != Order.PARALLEL) {
        boolean isLastFilter = componentSubIndex.get(i) + 1 == component.getFilters().size();
        if (isLastFilter) {
          componentSubIndex.set(i, 0);
        } else {
          componentSubIndex.set(i, componentSubIndex.get(i) + 1);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The function accumulates a set of queries that are intended to be run in parallel.
   *
   * @param result the list new filters lists are added to
   * @param minIndex the index to stop at when looking for more results
   */
  private void buildNextResult(List<List<FilterPredicate>> result, int minIndex) {
    while (componentIndex >= minIndex) {
      if (componentIndex >= components.size()) {
        // Found a result
        result.add(filtersStack.peek());
        // Look at the previous component for more results
        --componentIndex;
        continue;
      }

      MultiQueryComponent component = components.get(componentIndex);
      if (component.getOrder() == Order.PARALLEL) {
        // Denote that we are processing a parallel component and move to the
        // next component
        ++parallelCount;
        ++componentIndex;
        // Add results from all filters so that they are returned in a batch
        for (List<FilterPredicate> componentFilters : component.getFilters()) {
          // Build result start from the next component with our filters on the
          // stack
          pushFilters(componentFilters);
          buildNextResult(result, componentIndex);
          filtersStack.pop();
        }
        // Denote we are no longer processing a parallel component and move from
        // the next component to the previous component
        --parallelCount;
        componentIndex -= 2;
      } else {
        if (filtersStack.size() <= componentIndex + 1) {
          // Add our value to the stack and process the next component
          pushFilters(component.getFilters().get(componentSubIndex.get(componentIndex)));
          ++componentIndex;
        } else {
          // Remove our value from the stack
          filtersStack.pop();
          boolean isLastFilter =
              componentSubIndex.get(componentIndex) + 1 == component.getFilters().size();
          // Move to the previous component
          --componentIndex;
          // If there are no parallel components and we didn't just process
          // our last filter, we can stop here and preserve the rest of our
          // filterStack
          if ((parallelCount == 0) && !isLastFilter) {
            break;
          }
        }
      }
    }
    // ComponentIndex is now 1 below minIndex, so bump it back up
    ++componentIndex;
  }

  @Override
  public boolean hasNext() {
    return moreResults;
  }

  @Override
  public List<List<FilterPredicate>> next() {
    if (!moreResults) {
      throw new NoSuchElementException();
    }
    List<List<FilterPredicate>> result = Lists.newArrayList();
    buildNextResult(result, 0);
    moreResults = advanceSerialComponents();
    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
