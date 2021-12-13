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

package com.google.appengine.api.internal;

import java.util.Collections;
import java.util.List;

/** Utilities for dealing with repackaged classes. Not part of the public API. */
public final class Repackaged {
  private Repackaged() {}

  static final String REPACKAGED_PREFIX = "com.google.appengine.repackaged.";

  /**
   * Returns an immutable copy of the given list if it is of a repackaged class, otherwise returns
   * the original list.
   */
  public static <E> List<E> copyIfRepackagedElseOriginal(List<E> list) {
    return isRepackaged(list) ? ImmutableCopy.list(list) : list;
  }

  /**
   * Returns an immutable copy of the given list if it is of a repackaged class, otherwise returns
   * an unmodifiable wrapper around the original list.
   */
  public static <E> List<E> copyIfRepackagedElseUnmodifiable(List<E> list) {
    return isRepackaged(list) ? ImmutableCopy.list(list) : Collections.unmodifiableList(list);
  }

  /** Returns true if the given object has a class that is the result of App Engine repackaging. */
  public static boolean isRepackaged(Object object) {
    return object != null && object.getClass().getName().startsWith(REPACKAGED_PREFIX);
  }
}
