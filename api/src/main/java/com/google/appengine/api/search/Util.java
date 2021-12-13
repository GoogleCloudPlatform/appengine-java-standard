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

package com.google.appengine.api.search;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * A utility class that does various checks on search and indexing parameters.
 */
final class Util {
  /**
   * A helper method for testing two objects are equal.
   *
   * @return whether the two objects a and b are equal
   */
  static boolean equalObjects(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  /**
   * A helper method for overriding null values with a default value.
   *
   * @param value the value of some field
   * @param defaultValue the default value for the field
   * @return value if it is not null, otherwise the defaultValue
   */
  static <T> T defaultIfNull(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * Returns a string representation of the iterable objects. This is
   * used in debugging. The limit parameter is used to control how many
   * elements of the iterable are used to build the final string. Use
   * 0 or negative values, to include all.
   *
   * @param objects an iterable of objects to be turned into a string
   * @param limit the maximum number of objects from iterable to be used
   * to build a string
   */
  static <T> String iterableToString(Iterable<T> objects, int limit) {
    StringBuilder builder = new StringBuilder()
        .append("[");
    String sep = "";
    int head = (limit <= 0) ? Integer.MAX_VALUE : (limit + 1) / 2;
    int tail = (limit <= 0) ? 0 : limit - head;
    Iterator<T> iter = objects.iterator();
    while (iter.hasNext() && --head >= 0) {
      builder.append(sep).append(iter.next());
      sep = ", ";
    }
    LinkedList<T> tailMembers = new LinkedList<T>();
    int seen = 0;
    while (iter.hasNext()) {
      tailMembers.add(iter.next());
      if (++seen > tail) {
        tailMembers.removeFirst();
      }
    }
    if (seen > tail) {
      builder.append(", ...");
    }
    for (T o : tailMembers) {
      builder.append(sep).append(o);
      sep = ", ";
    }
    return builder.append("]").toString();
  }

  /**
   * Helper for constructing {@link String} representations of Search API objects.
   */
  static class ToStringHelper {
    private final StringBuilder sb;
    private boolean first = true;
    private boolean done = false;

    ToStringHelper(String objectName) {
      sb = new StringBuilder(objectName + "(");
    }

    ToStringHelper addField(String fieldName, Object value) {
      if (done) {
        throw new IllegalStateException();
      }
      if (value != null) {
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append(fieldName).append("=").append(value);
      }
      return this;
    }

    ToStringHelper addIterableField(String fieldName, Iterable<?> objects, int max) {
      if (done) {
        throw new IllegalStateException();
      }
      Iterator<?> iterator = objects.iterator();
      if (iterator.hasNext()) {
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append(fieldName).append("=").append(Util.iterableToString(objects, max));
      }
      return this;
    }

    ToStringHelper addIterableField(String fieldName, Iterable<?> objects) {
      return addIterableField(fieldName, objects, 0);
    }

    String finish() {
      done = true;
      sb.append(")");
      return sb.toString();
    }

    @Override
    public String toString() {
      return done ? sb.toString() : sb + ")";
    }
  }

  private Util() {}
}
