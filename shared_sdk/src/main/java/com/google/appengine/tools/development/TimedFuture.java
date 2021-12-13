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

package com.google.appengine.tools.development;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code TimedFuture} is a {@link Future} that will not return
 * successfully after the specified amount of time has elapsed.
 *
 * <p>All methods call through to the underlying future.  However, if
 * the specified time elapses (either before or while in one of the
 * {@code get} methods), an ExecutionException will be thrown that
 * wraps the exception returned by {@link #createDeadlineException()}.
 * No attempt is made to automatically cancel the underlying Future in
 * this case.
 *
 */
public abstract class TimedFuture<T> implements Future<T> {
  private final Future<T> future;
  private final Clock clock;
  private final long deadlineTime;

  public TimedFuture(Future<T> future, long deadlineMillis) {
    this(future, deadlineMillis, Clock.DEFAULT);
  }

  public TimedFuture(Future<T> future, long deadlineMillis, Clock clock) {
    this.future = future;
    this.deadlineTime = clock.getCurrentTime() + deadlineMillis;
    this.clock = clock;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    long millisRemaining = getMillisRemaining();
    try {
      return future.get(millisRemaining, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      // The underlying Future is out of time.
      throw new ExecutionException(createDeadlineException());
    }
  }

  @Override
  public T get(long value, TimeUnit units)
      throws InterruptedException, ExecutionException, TimeoutException {
    long millisRequested = units.toMillis(value);
    long millisRemaining = getMillisRemaining();
    // If this method will time out sooner than the underlying future,
    // only wait the time requested and let the TimeoutException propagate.
    if (millisRequested < millisRemaining) {
      return future.get(millisRequested, TimeUnit.MILLISECONDS);
    }
    // Wait until the underlying future runs out of time.
    try {
      return future.get(millisRemaining, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      throw new ExecutionException(createDeadlineException());
    }
  }

  protected abstract RuntimeException createDeadlineException();

  private long getMillisRemaining() {
    return Math.max(0, deadlineTime - clock.getCurrentTime());
  }

  @Override
  public boolean isCancelled() {
    return future.isCancelled();
  }

  @Override
  public boolean isDone() {
    return future.isDone() || getMillisRemaining() == 0;
  }

  @Override
  public boolean cancel(boolean force) {
    return future.cancel(force);
  }
}
