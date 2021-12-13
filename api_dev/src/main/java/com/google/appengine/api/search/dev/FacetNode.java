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

package com.google.appengine.api.search.dev;

import com.google.appengine.api.search.proto.SearchServicePb.FacetRange;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A FacetNode represents a facet with its aggregated values and proper count. For example
 * a genre is a facet with values like (adventure, 12), (sci-fi, 10), etc.
 */
final class FacetNode implements Comparable<FacetNode> {

  /**
   * Value represents a single facet value.
   */
  static final class Value implements Comparable<Value> {
    final String label;
    private int count;
    // if this value represents a range, this will be the range it represents
    // otherwise will be null.
     FacetRange range;

    Value(String label, int count, FacetRange range) {
      this.label = label;
      this.count = count;
      this.range = range;
    }

    Value(String label) {
      this(label, 0, null);
    }

    void incCount(int amount) {
      this.count += amount;
    }

    int getCount() {
      return count;
    }

    @Override
    public int compareTo(Value other) {
      return Integer.compare(other.count, this.count);
    }
  }

  final String name;
  private int count;
  private Double min;
  private Double max;
  private int minMaxCount;

  // Map of values for this facet. Map is used instead of list to make sure we do not have
  // duplicate values (values with the same name).
  private final Map<String, Value> valueMap = new LinkedHashMap<>();
  final int valueLimit;

  FacetNode(String name, int valueLimit) {
    this.name = name;
    this.valueLimit = valueLimit;
    this.count = 0;
  }

  /**
   * Returns an unmodifiable view of the values.
   */
  Collection<Value> getValues() {
    return Collections.unmodifiableCollection(valueMap.values());
  }

  /**
   * Returns value with given label or null if it is not available.
   */
  Value getValue(String label) {
    return valueMap.get(label);
  }

  /**
   * Returns total count of values for this facet.
   */
  int getCount() {
    return count + minMaxCount;
  }

  /**
   * Minimum for numeric values added by addNumericValue method.
   */
  Double getMin() {
    return min;
  }

  /**
   * Maximum for numeric values added by addNumericValue method.
   */
  Double getMax() {
    return max;
  }

  /**
   * Number of numeric values added to this facet by addNumericValue method.
   */
  int getMinMaxCount() {
    return minMaxCount;
  }

  /**
   * Adds a value to this facet. If the value exists, its count
   * will be incremented by {@code countToAdd} otherwise a new value for
   * {@code (valueToAdd, countToAdd)} will be created.
   */
  void addValue(String valueToAdd, int countToAdd) {
    addValue(valueToAdd, countToAdd, null);
  }

  /**
   * Adds a value and a range associated with it to this facet. The range object will
   * be used for creating FacetRefinement.
   */
  void addValue(String valueToAdd, int countToAdd, FacetRange range) {
    Value v = valueMap.get(valueToAdd);
    if (v == null) {
      v = new Value(valueToAdd, 0, range);
      valueMap.put(valueToAdd, v);
    }
    v.incCount(countToAdd);
    count += countToAdd;
  }

  /**
   * Adds a numeric value to this facet by updating minimum and maximum for this facet. Only the
   * minimum and maximum of a numeric facet will be returned if a range is not specified.
   */
  void addNumericValue(double value) {
    if (min == null || min > value) {
      min = value;
    }
    if (max == null || max < value) {
      max = value;
    }
    minMaxCount++;
  }

  @Override
  public int compareTo(FacetNode other) {
    return Integer.compare(other.getCount(), this.getCount());
  }
}
