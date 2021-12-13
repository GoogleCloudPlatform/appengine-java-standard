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

import com.google.appengine.api.utils.FutureWrapper;
import java.util.concurrent.Future;

/**
 * An abstract {@link FutureWrapper} implementation that invokes callbacks before returning its
 * value. The base class ensures that callbacks only run once.
 *
 * @param <T> The type of Future.
 */
abstract class PostOpFuture<T> extends FutureWrapper<T, T> {

  final DatastoreCallbacks datastoreCallbacks;

  PostOpFuture(Future<T> delegate, DatastoreCallbacks datastoreCallbacks) {
    super(delegate);
    this.datastoreCallbacks = datastoreCallbacks;
  }

  @Override
  protected final T wrap(T result) {
    executeCallbacks(result);
    return result;
  }

  @Override
  protected final Throwable convertException(Throwable cause) {
    return cause;
  }

  /** Responsible for actual execution of the callbacks. */
  abstract void executeCallbacks(T result);
}
