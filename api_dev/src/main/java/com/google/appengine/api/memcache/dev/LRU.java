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

package com.google.appengine.api.memcache.dev;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implements a simple LRU cache by intrusive chaining on elements.
 *
 */
class LRU<C extends LRU.Chainable<C>> {
  /** Interface for organising a set of elements into a chain. */
  interface Chainable<E> {
    /**
     * Get the next newer element.
     *
     * @return the element or {@code null} if there is no newer element.
     */
    E getNewer();

    /**
     * Get the next older element.
     *
     * @return the element or {@code null} if there is no older element.
     */
    E getOlder();

    /**
     * Set the next newest element.
     *
     * @param newer the next newest element.
     */
    void setNewer(E newer);

    /**
     * Set the next oldest element.
     *
     * @param older the next oldest element.
     */
    void setOlder(E older);
  }

  /**
   * Convenience class for chainable implementations to derive from. Pass the subclass in as the
   * type parameter.
   */
  public abstract static class AbstractChainable<E> implements Chainable<E> {
    private E newer = null;
    private E older = null;

    @Override
    public E getNewer() {
      return newer;
    }

    @Override
    public E getOlder() {
      return older;
    }

    @Override
    public void setNewer(E newer) {
      this.newer = newer;
    }

    @Override
    public void setOlder(E older) {
      this.older = older;
    }
  }

  private C newest;
  private C oldest;

  public LRU() {
    clear();
  }

  /** Empty the LRU. Element pointers are not modified. */
  public void clear() {
    newest = null;
    oldest = null;
  }

  /**
   * Test if the LRU is empty.
   *
   * @return true iff the LRU has no elements.
   */
  public boolean isEmpty() {
    return (getNewest() == null && getOldest() == null);
  }

  /**
   * Get the newest element in the chain.
   *
   * @return the element or {@code null} if the chain is empty.
   */
  public C getNewest() {
    return newest;
  }

  /**
   * Get the oldest element in the chain.
   *
   * @return the element or {@code null} if the chain is empty.
   */
  public C getOldest() {
    return oldest;
  }

  /**
   * Insert or update an item in the chain.
   *
   * @param element the element being updated. May not be {@code null}.
   */
  public void update(C element) {
    checkNotNull(element, "element cannot be null");
    remove(element);
    if (newest != null) newest.setNewer(element);
    element.setNewer(null);
    element.setOlder(newest);
    newest = element;
    if (oldest == null) oldest = element;
  }

  /**
   * Remove an element from the chain.
   *
   * @param element the element to remove. May not be {@code null}.
   */
  public void remove(C element) {
    checkNotNull(element, "element cannot be null");
    C newer = element.getNewer();
    C older = element.getOlder();
    if (newer != null) newer.setOlder(older);
    if (older != null) older.setNewer(newer);
    if (element == newest) newest = older;
    if (element == oldest) oldest = newer;
    element.setNewer(null);
    element.setOlder(null);
  }

  /**
   * Remove and return the oldest element in the chain.
   *
   * @return the oldest element in the chain.
   */
  public C removeOldest() {
    C oldest = getOldest();
    remove(oldest);
    return oldest;
  }

  /** Returns the length of the internal chain. Only use this in tests! */
  /* @VisibleForTesting */
  long getChainLength() {
    return getChainLengthJavaStub();
  }

  /** Returns The length of the internal chain. Runs in linear time. Only use this in tests! */
  private long getChainLengthJavaStub() {
    int length = 0;
    C current = newest;
    while (current != null) {
      length++;
      current = current.getOlder();
    }
    return length;
  }
}
