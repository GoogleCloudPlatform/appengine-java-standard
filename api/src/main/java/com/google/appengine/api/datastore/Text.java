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

// TODO: deprecate in favor of an unindexed String.
/**
 * {@code Text} wraps around a string of unlimited size.
 *
 * <p>Ordinary Java strings stored as properties in {@code Entity} objects are limited to 1500
 * bytes. However, {@code Text} objects can also be stored in properties, and are unlimited in size.
 * However, they will not be indexed for query purposes.
 *
 * <p>Consider using a standard Java string and storing it with the {@link
 * Entity#setUnindexedProperty} method instead.
 */
public final class Text implements Serializable {

  public static final long serialVersionUID = -8389037235415462280L;

  // This attribute needs to be non-final to support GWT serialization
  private String value;

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings("unused")
  private Text() {}

  /**
   * Construct a new {@code Text} object with the specified value. This object cannot be modified
   * after construction.
   */
  public Text(String value) {
    // TODO: V2 throw NPE if the arg is null.
    this.value = value;
  }

  /** Return the value of this {@code Text}. Can be {@code null}. */
  public String getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    if (value == null) {
      return -1;
    }
    return value.hashCode();
  }

  /** Two {@code Text} objects are considered equal if their content strings match exactly. */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof Text) {
      Text key = (Text) object;
      if (value == null) {
        return key.value == null;
      }
      return value.equals(key.value);
    }
    return false;
  }

  /** Returns the first 70 characters of the underlying string. */
  @Override
  public String toString() {
    if (value == null) {
      return "<Text: null>";
    }
    String text = value;
    if (text.length() > 70) {
      text = text.substring(0, 70) + "...";
    }
    return "<Text: " + text + ">";
  }
}
