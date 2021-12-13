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

/**
 * {@link Iterator} implementation that respects the provided offset and limit.
 *
 */
class SlicingIterator<T> extends AbstractIterator<T> {

  private final Iterator<T> delegate;
  // we'll decrement this as we consume results
  private int remainingOffset;
  private final Integer limit;
  private Integer numEntitiesReturned = 0;

  /**
   * @param delegate The {@link Iterator} that we're slicing.
   * @param offset The number of results to consume before we actually return any data. Can be null.
   * @param limit The maximum number of results we'll return. Can be null.
   */
  SlicingIterator(Iterator<T> delegate, Integer offset, Integer limit) {
    this.remainingOffset = offset == null ? 0 : offset;
    this.limit = limit;
    this.delegate = delegate;
  }

  @Override
  protected T computeNext() {
    if (numEntitiesReturned.equals(limit)) {
      endOfData();
    }
    T next = null;
    // Keep consuming as long as there is more data and remainingOffset isn't
    // negative
    while (delegate.hasNext() && remainingOffset-- >= 0) {
      next = delegate.next();
    }

    if (remainingOffset >= 0 || next == null) {
      // Either we didn't exhaust the offset or we did
      // exhaust the offset and we ran out of data.  Either way, we're done.
      endOfData();
    } else {
      // reset to 0 to ensure we consume exactly one entity the next time through
      remainingOffset = 0;
      numEntitiesReturned++;
    }
    return next;
  }
}
