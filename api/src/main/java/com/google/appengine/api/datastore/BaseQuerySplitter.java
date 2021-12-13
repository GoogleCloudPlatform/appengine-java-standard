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

import com.google.appengine.api.datastore.Query.SortDirection;
import java.util.Collections;
import java.util.Comparator;

/**
 * Base implementation of {@link QuerySplitter} with logic common to all splitters.
 *
 */
abstract class BaseQuerySplitter implements QuerySplitter {
  protected static final Comparator<ComparableValue> VALUE_COMPARATOR_ASC =
      new Comparator<ComparableValue>() {
        @Override
        public int compare(ComparableValue o1, ComparableValue o2) {
          return EntityProtoComparators.MULTI_TYPE_COMPARATOR.compare(
              o1.comparableValue, o2.comparableValue);
        }
      };

  protected static final Comparator<ComparableValue> VALUE_COMPARATOR_DESC =
      Collections.reverseOrder(VALUE_COMPARATOR_ASC);

  protected BaseQuerySplitter() {}

  /**
   * A class that allows arbitrary property values to be compared while preserving their original
   * value.
   *
   * <p>This is needed because converting a value into a {@code Comparable<Object>} is potentially
   * destructive (meaning the {@code Comparable<Object>} value cannot be used as a property value).
   */
  protected static class ComparableValue {
    private final Comparable<Object> comparableValue;
    private final Object originalValue;

    public ComparableValue(Object value) {
      this.comparableValue = DataTypeTranslator.getComparablePropertyValue(value);
      this.originalValue = value;
    }

    public Object getValue() {
      return originalValue;
    }
  }

  protected static Comparator<ComparableValue> getValueComparator(SortDirection sortDirection) {
    if (sortDirection == SortDirection.DESCENDING) {
      return VALUE_COMPARATOR_DESC;
    } else {
      return VALUE_COMPARATOR_ASC;
    }
  }
}
