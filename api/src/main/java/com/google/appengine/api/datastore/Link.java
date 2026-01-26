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
import org.jspecify.annotations.Nullable;

/**
 * A {@code Link} is a URL of limited length.
 *
 * <p>In addition to adding the meaning of {@code URL} onto a String, a {@code Link} can also be
 * longer than a Text value, with a limit of 2083 characters.
 *
 */
public final class Link implements Serializable, Comparable<Link> {

  public static final long serialVersionUID = 731239796613544443L;

  // This attribute needs to be non-final to support GWT serialization
  private String value;

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings({"nullness", "unused"})
  private Link() {
    value = null;
  }

  /**
   * Constructs a new {@code Link} object with the specified value. This object cannot be modified
   * after construction.
   */
  public Link(String value) {
    // TODO Fail fast when string is too long.
    this.value = value;
  }

  /** Returns the value of this {@code Link}. */
  public String getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  /** Two {@code Link} objects are considered equal if their content strings match exactly. */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof Link key) {
      return value.equals(key.value);
    }
    return false;
  }

  /** Returns the entire text of this {@code Link}. */
  @Override
  public String toString() {
    return value;
  }

  @Override
  public int compareTo(Link l) {
    return value.compareTo(l.value);
  }
}
