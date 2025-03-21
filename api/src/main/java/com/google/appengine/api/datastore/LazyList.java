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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

/**
 * A {@link List} implementation that pulls query results from the server lazily.
 *
 * <p>Although {@link AbstractList} only requires us to implement {@link #get(int)}, {@link
 * #size()}, {@link #set(int, Entity)}, and {@link #remove(int)}, we provide our own implementations
 * for many other methods. The reason is that many of the implementations in {@link AbstractList}
 * invoke {@link #size()}, which requires us to pull the entire result set back from the server. We
 * provide more efficient implementations wherever possible (which is most places).
 *
 */
class LazyList extends AbstractList<Entity> implements QueryResultList<Entity>, Serializable {
  static final long serialVersionUID = -288529194506134706L;

  // The iterator from which we'll pull results lazily.  Will be null if this
  // object is instantiated via deserialization.
  @Nullable private final transient QueryResultIteratorImpl resultIterator;
  final List<Entity> results = new ArrayList<Entity>();
  // True if the entire result set has been fetched from the server.
  private boolean endOfData = false;
  // True if the user has called clear().  We require a separate flag for this
  // because if a user requests a Cursor we need to fetch the entire result set
  // from the server even if clear() has been called (which in all other cases
  // prevents us from fetching additional results).
  private boolean cleared = false;

  // Null until the user asks for it.  Used to support serialization.
  /* @Nullabe */ private Cursor cursor = null;

  LazyList(QueryResultIteratorImpl resultIterator) {
    this.resultIterator = resultIterator;
  }

  /** Resolves the entire result set. */
  private void resolveAllData() {
    resolveToIndex(-1, true);
  }

  /**
   * Resolves enough of the result set to return the {@link Entity} at the specified {@code index}.
   * There is no guarantee that the result set actually has an {@link Entity} at this index, but
   * it's up to the caller to recognize this and respond appropriately.
   *
   * @param index The index to which we need to resolve.
   */
  private void resolveToIndex(int index) {
    resolveToIndex(index, false);
  }

  /**
   * Resolves enough of the result set to return the {@link Entity} at the specified {@code index}.
   * There is no guarantee that the result set actually has an {@link Entity} at this index, but
   * it's up to the caller to recognize this and respond appropriately.
   *
   * @param index The index to which we need to resolve.
   * @param fetchAll If {@code true}, ignores the provided index and fetches all data.
   */
  private void resolveToIndex(int index, boolean fetchAll) {
    if (cleared) {
      // user called clear() so don't bother trying to fetch any more data
      return;
    }
    forceResolveToIndex(index, fetchAll);
  }

  /**
   * @see #resolveToIndex(int, boolean) The only difference here is that we ignore the short-circuit
   *     that may have been set by a call to {@link #clear()}.
   */
  private void forceResolveToIndex(int index, boolean fetchAll) {
    if (endOfData) {
      // No more data so don't bother trying to fetch.
      return;
    }
    if (fetchAll || results.size() <= index) {
      // There might be more data available and we haven't resolved enough of
      // the result set to return the Entity at the index specified by the
      // user, so fetch more data.
      int numToFetch;
      if (fetchAll) {
        numToFetch = Integer.MAX_VALUE;
      } else {
        numToFetch = (index - results.size()) + 1;
      }

      if (resultIterator == null) {
        endOfData = true;
      } else {
        List<Entity> nextBatch = resultIterator.nextList(numToFetch);
        results.addAll(nextBatch);
        if (nextBatch.size() < numToFetch) {
          // If we got fewer results than we asked for then we know there is no
          // more data.
          endOfData = true;
        }
      }
    }
  }

  /** Implementation required for concrete implementations of {@link AbstractList}. */
  @Override
  public Entity get(int i) {
    resolveToIndex(i);
    // Will throw IndexOutOfBounds if the result set isn't large enough, which
    // is what we want.
    return results.get(i);
  }

  /** Implementation required for concrete implementations of {@link AbstractList}. */
  @Override
  public int size() {
    resolveAllData();
    return results.size();
  }

  /** Implementation required for concrete, modifiable implementations of {@link AbstractList}. */
  @Override
  public Entity set(int i, Entity entity) {
    resolveToIndex(i);
    return results.set(i, entity);
  }

  /**
   * Implementation required for concrete, modifiable, variable-length implementations of {@link
   * AbstractList}.
   */
  @Override
  public void add(int i, Entity entity) {
    resolveToIndex(i);
    // Will throw IndexOutOfBounds if the result set isn't large enough, which
    // is what we want.
    results.add(i, entity);
  }

  /**
   * Implementation required for concrete, modifiable, variable-length implementations of {@link
   * AbstractList}.
   */
  @Override
  public Entity remove(int i) {
    resolveToIndex(i);
    // Will throw IndexOutOfBounds if the result set isn't large enough, which
    // is what we want.
    return results.remove(i);
  }

  /** We provide our own implementation that does not invoke {@link #size()}. */
  @Override
  public Iterator<Entity> iterator() {
    return listIterator();
  }

  /**
   * We provide our own implementation that does not invoke {@link #size()}.
   *
   * @see ListIterator for the spec.
   */
  @Override
  public ListIterator<Entity> listIterator() {
    return new ListIterator<Entity>() {
      // Index into the List over which we're iterating.
      // Must be >= 0 and < size.  When next() is invoked, the element at this
      // index will be returned.  When previous() is invoked, the element at
      // this index - 1 will be returned.
      int currentIndex = 0;
      int indexOfLastElementReturned = -1;
      // The ListIterator spec has all sorts of rules about when you can call
      // different methods.  We use these members for book keeping so we can
      // enforce these rules.
      boolean elementReturned = false;
      boolean addOrRemoveCalledSinceElementReturned = false;

      @Override
      public boolean hasNext() {
        resolveToIndex(currentIndex);
        return currentIndex < results.size();
      }

      @Override
      public Entity next() {
        if (hasNext()) {
          elementReturned = true;
          addOrRemoveCalledSinceElementReturned = false;
          indexOfLastElementReturned = currentIndex++;
          return results.get(indexOfLastElementReturned);
        }
        throw new NoSuchElementException();
      }

      @Override
      public boolean hasPrevious() {
        return currentIndex > 0;
      }

      @Override
      public Entity previous() {
        if (hasPrevious()) {
          elementReturned = true;
          addOrRemoveCalledSinceElementReturned = false;
          indexOfLastElementReturned = --currentIndex;
          return results.get(indexOfLastElementReturned);
        }
        throw new NoSuchElementException();
      }

      @Override
      public int nextIndex() {
        return currentIndex;
      }

      @Override
      public int previousIndex() {
        return currentIndex - 1;
      }

      @Override
      public void remove() {
        if (!elementReturned || addOrRemoveCalledSinceElementReturned) {
          // ListIterator returned by ArrayList throws an exception with no
          // message for all these conditions.
          throw new IllegalStateException();
        }
        addOrRemoveCalledSinceElementReturned = true;
        if (indexOfLastElementReturned < currentIndex) {
          // the item we're removing is earlier than the current pointer so
          // move the current pointer down by one
          currentIndex--;
        }
        LazyList.this.remove(indexOfLastElementReturned);
      }

      @Override
      public void set(Entity entity) {
        if (!elementReturned || addOrRemoveCalledSinceElementReturned) {
          // ListIterator returned by ArrayList throws an exception with no
          // message.
          throw new IllegalStateException();
        }
        LazyList.this.set(indexOfLastElementReturned, entity);
      }

      @Override
      public void add(Entity entity) {
        addOrRemoveCalledSinceElementReturned = true;
        LazyList.this.add(currentIndex++, entity);
      }
    };
  }

  /**
   * The spec for this method says we need to throw {@link IndexOutOfBoundsException} if {@code
   * index} is < 0 or > size(). Since we need to know size up front, there's no way to service this
   * method without resolving the entire result set. The only reason for the override is to provide
   * a good location for this comment.
   */
  @Override
  public ListIterator<Entity> listIterator(int index) {
    return super.listIterator(index);
  }

  /** We provide our own implementation that does not invoke {@link #size()}. */
  @Override
  public boolean isEmpty() {
    resolveToIndex(0);
    return results.isEmpty();
  }

  /** We provide our own implementation that does not invoke {@link #size()}. */
  @Override
  public List<Entity> subList(int from, int to) {
    resolveToIndex(to - 1);
    return results.subList(from, to);
  }

  /** We provide our own implementation that does not invoke {@link #size()}. */
  @Override
  public void clear() {
    // clear out whatever we've paged in thus far
    results.clear();
    // make sure nothing else gets paged in
    cleared = true;
  }

  /** We provide our own implementation that does not invoke {@link #size()}. */
  @Override
  public int indexOf(Object o) {
    int index = 0;
    // for loop invokes iterator(), which will only pull back data as needed
    for (Entity e : this) {
      if (o == null) {
        if (e == null) {
          return index;
        }
      } else if (o.equals(e)) {
        return index;
      }
      index++;
    }
    return -1;
  }

  @Override
  public List<Index> getIndexList() {
    List<Index> indexList = null;
    if (resultIterator != null) {
      indexList = resultIterator.getIndexList();
    }
    return indexList;
  }

  @Override
  public Cursor getCursor() {
    if (cursor == null && resultIterator != null) {
      // The cursor needs to point to the end of the result set, so make sure we
      // resolve the entire result set before we actually ask for the cursor.
      forceResolveToIndex(-1, true); // Fetch all data.
      cursor = resultIterator.getCursor();
    }
    return cursor;
  }

  /**
   * Custom serialization logic to ensure that we read the entire result set before we serialize.
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    // Resolve all data before we serialize.
    resolveAllData();
    // Get ahold of the cursor before we serialize.
    cursor = getCursor();
    out.defaultWriteObject();
  }
}
