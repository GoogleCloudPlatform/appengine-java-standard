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

package com.google.appengine.api.blobstore;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@code BlobKey} contains the string identifier of a large (possibly
 * larger than 1MB) blob of binary data that was uploaded in a
 * previous request and can be streamed directly to users.
 *
 */
public final class BlobKey implements Serializable, Comparable<BlobKey> {
  private static final long serialVersionUID = 1014827161979704941L;

  private final String blobKey;

  /**
   * Construct a new {@code BlobKey} with the specified key string.
   *
   * @throws IllegalArgumentException If the specified string was null.
   */
  public BlobKey(String blobKey) {
    if (blobKey == null) {
      throw new IllegalArgumentException("Argument must not be null.");
    }
    this.blobKey = blobKey;
  }

  /**
   * Returns the blob key as a String.
   */
  public String getKeyString() {
    return blobKey;
  }

  @Override
  public int hashCode() {
    return blobKey.hashCode();
  }

  /**
   * Two {@code BlobKey} objects are considered equal if they point
   * to the same blobs.
   */
  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof BlobKey) {
      BlobKey key = (BlobKey) object;
      return Objects.equals(blobKey, key.blobKey);
    }
    return false;
  }

  @Override
  public String toString() {
    return "<BlobKey: " + blobKey + ">";
  }

  @Override
  public int compareTo(BlobKey o) {
    return blobKey.compareTo(o.blobKey);
  }
}
