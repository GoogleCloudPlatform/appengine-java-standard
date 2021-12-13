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
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a range of unique datastore identifiers from {@code getStart().getId()} to {@code
 * getEnd().getId()} inclusive. If an instance of this class is the result of a call to {@code
 * DatastoreService.allocateIds()}, the {@link Key Keys} returned by this instance have been
 * consumed in the datastore's id-space and are guaranteed never to be reused. <br>
 * This class can be used to construct {@link Entity Entities} with {@link Key Keys} that have
 * specific id values without fear of the datastore creating new records with those same ids at a
 * later date. This can be helpful as part of a data migration or large bulk upload where you may
 * need to preserve existing ids and relationships between entities. <br>
 * This class is threadsafe but the {@link Iterator Iterators} returned by {@link #iterator()} are
 * not.
 *
 */
public final class KeyRange implements Iterable<Key>, Serializable {
  static final long serialVersionUID = 962890261927141064L;

  private final @Nullable Key parent;
  private final String kind;
  private final Key start;
  private final Key end;
  private final AppIdNamespace appIdNamespace;

  // Intentionally public to support users who want to allocate a range
  // and then reconstruct it later, perhaps in another request.
  public KeyRange(Key parent, String kind, long start, long end) {
    this(parent, kind, start, end, DatastoreApiHelper.getCurrentAppIdNamespace());
  }

  KeyRange(Key parent, String kind, long start, long end, AppIdNamespace appIdNamespace) {
    if (parent != null && !parent.isComplete()) {
      throw new IllegalArgumentException("Invalid parent: not a complete key");
    }

    if (kind == null || kind.isEmpty()) {
      throw new IllegalArgumentException("Invalid kind: cannot be null or empty");
    }

    if (start < 1) {
      throw new IllegalArgumentException("Illegal start " + start + ": less than 1");
    }

    if (end < start) {
      throw new IllegalArgumentException("Illegal end " + end + ": less than start " + start);
    }

    this.parent = parent;
    this.kind = kind;
    this.appIdNamespace = appIdNamespace;
    this.start = KeyFactory.createKey(parent, kind, start, appIdNamespace);
    this.end = KeyFactory.createKey(parent, kind, end, appIdNamespace);
  }

  /**
   * This constructor exists for frameworks (e.g. Google Web Toolkit) that require it for
   * serialization purposes. It should not be called explicitly.
   */
  @SuppressWarnings({"unused", "nullness"})
  private KeyRange() {
    parent = null;
    kind = null;
    start = null;
    end = null;
    appIdNamespace = null;
  }

  AppIdNamespace getAppIdNamespace() {
    return appIdNamespace;
  }

  /** @return The parent {@link Key} of the range. */
  @Nullable
  Key getParent() {
    return parent;
  }

  /** @return The kind of the range. */
  String getKind() {
    return kind;
  }

  /** Returns the first {@link Key} in the range. */
  public Key getStart() {
    return start;
  }

  /** Returns the last {@link Key} in the range. */
  public Key getEnd() {
    return end;
  }

  /** Returns the size of the range. */
  public long getSize() {
    // range is inclusive so we add one to the difference
    return end.getId() - start.getId() + 1;
  }

  @Override
  public Iterator<Key> iterator() {
    return new IdRangeIterator();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof KeyRange)) {
      return false;
    }
    KeyRange that = (KeyRange) obj;

    // only nee to compare start and end, since they contain parent and kind.
    return (this.start.equals(that.start) && this.end.equals(that.end));
  }

  @Override
  public int hashCode() {
    return 31 * start.hashCode() + end.hashCode();
  }

  /**
   * {@link Iterator} implementation that returns {@link Key Keys} in the range defined by the
   * enclosing {@link KeyRange}.
   */
  private final class IdRangeIterator implements Iterator<Key> {
    private long next = start.getId();

    @Override
    public boolean hasNext() {
      // range is inclusive
      return next <= end.getId();
    }

    @Override
    public Key next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return KeyFactory.createKey(parent, kind, next++, appIdNamespace);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
