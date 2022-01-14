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
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code ShortBlob} contains an array of bytes no longer than {@link
 * DataTypeUtils#MAX_SHORT_BLOB_PROPERTY_LENGTH}. Unlike {@link Blob}, {@code ShortBlobs} are
 * indexed by the datastore and can therefore be filtered and sorted on in queries. If your data is
 * too large to fit in a {@code ShortBlob} use {@link Blob} instead.
 */
// TODO: Make the file GWT-compatible (remove non-gwt-compatible
// dependencies). Once done add this file to the gwt-datastore BUILD target
public final class ShortBlob implements Serializable, Comparable<ShortBlob> {

  public static final long serialVersionUID = -3427166602866836472L;

  // This attribute needs to be non-final to support GWT serialization
  private byte[] bytes;

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings("unused")
  private ShortBlob() {
    bytes = new byte[0];
  }

  /**
   * Construct a new {@code ShortBlob} with the specified bytes. This blob cannot be modified after
   * construction.
   */
  public ShortBlob(byte[] bytes) {
    // TODO Fail fast when array is too big.

    // defensive copy - not using Arrays.copyOf to maintain java 5
    // compatibility
    this.bytes = new byte[bytes.length];
    System.arraycopy(bytes, 0, this.bytes, 0, bytes.length);
  }

  /** Return the bytes stored in this {@code ShortBlob}. */
  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  /** Two {@code ShortBlob} objects are considered equal if their contained bytes match exactly. */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof ShortBlob) {
      ShortBlob other = (ShortBlob) object;
      return Arrays.equals(bytes, other.bytes);
    }
    return false;
  }

  /** Simply prints the number of bytes contained in this {@code ShortBlob}. */
  @Override
  public String toString() {
    // TODO Consider printing hex-encoded bytes since the size is
    // capped pretty low.
    return "<ShortBlob: " + bytes.length + " bytes>";
  }

  @Override
  public int compareTo(ShortBlob other) {
    DataTypeTranslator.ComparableByteArray cba1 = new DataTypeTranslator.ComparableByteArray(bytes);
    DataTypeTranslator.ComparableByteArray cba2 =
        new DataTypeTranslator.ComparableByteArray(other.bytes);
    return cba1.compareTo(cba2);
  }
}
