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

import java.util.Iterator;
import java.util.NoSuchElementException;

// This is almost an exact copy of com.google.common.collect.AbstractIterator,
// and that's a shame.  The problem is that we don't want our api code
// depending on common.collect because it's too hard to manage all the
// transitive deps and too hard to catch 1.6isms that are bound to creep in.
// At least we're keeping it out of the public api.
//
// I'm open to alternate solutions, but at the moment I don't see a better way.
/** @see com.google.common.collect.AbstractIterator */
abstract class AbstractIterator<T> implements Iterator<T> {
  private State state = State.NOT_READY;

  enum State {
    READY,
    NOT_READY,
    DONE,
    FAILED,
  }

  private T next;

  protected abstract T computeNext();

  protected final T endOfData() {
    state = State.DONE;
    return null;
  }

  @Override
  public final boolean hasNext() {
    if (state == State.FAILED) {
      throw new IllegalStateException();
    }
    switch (state) {
      case DONE:
        return false;
      case READY:
        return true;
      default:
    }
    return tryToComputeNext();
  }

  private boolean tryToComputeNext() {
    state = State.FAILED;
    next = computeNext();
    if (state != State.DONE) {
      state = State.READY;
      return true;
    }
    return false;
  }

  @Override
  public final T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    state = State.NOT_READY;
    return next;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
