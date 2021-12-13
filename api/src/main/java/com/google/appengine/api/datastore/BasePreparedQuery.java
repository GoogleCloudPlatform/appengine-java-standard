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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;

import com.google.errorprone.annotations.DoNotCall;
import java.util.Iterator;

/**
 * A base implementations for a PreparedQuery.
 *
 * <p>This class forwards all calls to {@link PreparedQuery#asIterator(FetchOptions)}/{@link
 * PreparedQuery#asQueryResultIterator(FetchOptions)} and {@link
 * PreparedQuery#asList(FetchOptions)}/{@link PreparedQuery#asQueryResultList(FetchOptions)} which
 * need to be implemented in a sub class.
 */
abstract class BasePreparedQuery implements PreparedQuery {
  @Override
  public Iterable<Entity> asIterable(final FetchOptions fetchOptions) {
    return new Iterable<Entity>() {
      @Override
      public Iterator<Entity> iterator() {
        return asIterator(fetchOptions);
      }
    };
  }
  @Override
  public Iterable<Entity> asIterable() {
    return asIterable(withDefaults());
  }
  @Override
  public Iterator<Entity> asIterator() {
    return asIterator(withDefaults());
  }
  @Override
  public QueryResultIterable<Entity> asQueryResultIterable(final FetchOptions fetchOptions) {
    return new QueryResultIterable<Entity>() {
      @Override
      public QueryResultIterator<Entity> iterator() {
        return asQueryResultIterator(fetchOptions);
      }
    };
  }
  @Override
  public QueryResultIterable<Entity> asQueryResultIterable() {
    return asQueryResultIterable(withDefaults());
  }
  @Override
  public QueryResultIterator<Entity> asQueryResultIterator() {
    return asQueryResultIterator(withDefaults());
  }

  @DoNotCall
  @Deprecated
  @Override
  public final int countEntities() {
    // Adding a limit of 1000 so we don't break the expectations of apps
    // currently using this method
    return countEntities(withDefaults().limit(1000));
  }
}
