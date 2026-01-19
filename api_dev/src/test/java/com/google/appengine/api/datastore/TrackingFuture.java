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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of {@link Future} that exposes the number of times that get() has been called.
 *
 * @param <T> the type of the Future.
 */
@SuppressWarnings("ShouldNotSubclass")
class TrackingFuture<T> implements Future<T> {
  private final T result;
  private int numCallsToGet = 0;

  /**
   * @param result the value to return from get. Will be returned as many times as get is called.
   */
  TrackingFuture(T result) {
    this.result = result;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return true;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    numCallsToGet++;
    return result;
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {

    numCallsToGet++;
    return result;
  }

  public int getNumCallsToGet() {
    return numCallsToGet;
  }
}
