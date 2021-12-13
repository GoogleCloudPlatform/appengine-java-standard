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

import java.io.Serializable;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A user-provided integer rating for a piece of content. Normalized to a 0-100 scale.
 *
 */
// TODO: Make the file GWT-compatible (the method
// String.format(String, int, int) is not implemented in GWT).
// Once done add this file to the gwt-datastore BUILD target
public final class Rating implements Serializable, Comparable<Rating> {

  public static final long serialVersionUID = 362898405551261187L;

  /** The minimum legal value for a rating. */
  public static final int MIN_VALUE = 0;

  /** The maximum legal value for a rating. */
  public static final int MAX_VALUE = 100;

  // This attribute needs to be non-final to support GWT serialization
  private int rating;

  /**
   * @throws IllegalArgumentException If {@code rating} is smaller than {@link #MIN_VALUE} or
   *     greater than {@link #MAX_VALUE}
   */
  public Rating(int rating) {
    if (rating < MIN_VALUE || rating > MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(
              "rating must be no smaller than %d and no greater than %d (received %d)",
              MIN_VALUE, MAX_VALUE, rating));
    }
    this.rating = rating;
  }

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings("unused")
  private Rating() {
    this(0);
  }

  public int getRating() {
    return rating;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Rating rating1 = (Rating) o;

    if (rating != rating1.rating) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return rating;
  }

  @Override
  public int compareTo(Rating o) {
    return Integer.compare(rating, o.rating);
  }
}
