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
import java.util.List;

/**
 * A class that simply forwards {@link Iterator} methods to one delegate and forwards {@link List}
 * to another.
 *
 * @param <T> the type of result returned by the query
 */
class QueryResultIteratorDelegator<T> implements QueryResultIterator<T> {

  private final QueryResult queryResultDelegate;
  private final Iterator<T> iteratorDelegate;

  QueryResultIteratorDelegator(QueryResult queryResultDelegate, Iterator<T> iteratorDelegate) {
    this.queryResultDelegate = queryResultDelegate;
    this.iteratorDelegate = iteratorDelegate;
  }

  @Override // @Nullable
  public List<Index> getIndexList() {
    return queryResultDelegate.getIndexList();
  }

  @Override // @Nullable
  public Cursor getCursor() {
    return queryResultDelegate.getCursor();
  }

  @Override
  public boolean hasNext() {
    return iteratorDelegate.hasNext();
  }

  @Override
  public T next() {
    return iteratorDelegate.next();
  }

  @Override
  public void remove() {
    iteratorDelegate.remove();
  }
}
