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

package com.google.appengine.api.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * {@code FutureWrapper} is a simple {@link Future} that wraps a
 * parent {@code Future}.  This class is thread-safe.
 *
 * @param <K> The type of this {@link Future}
 * @param <V> The type of the wrapped {@link Future}
 */
public abstract class FutureWrapper<K extends @Nullable Object, V> implements Future<V> {

  private final Future<K> parent;

  // Guarded by lock.
  // Allows us to distinguish between a null result and a result we have not
  // yet fetched.
  private boolean hasResult;
  private V successResult;
  private ExecutionException exceptionResult;

  private final Lock lock = new ReentrantLock();

  public FutureWrapper(Future<K> parent) {
    if (parent == null) {
      throw new NullPointerException();
    }
    this.parent = parent;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return parent.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return parent.isCancelled();
  }

  @Override
  public boolean isDone() {
    return parent.isDone();
  }

  private V handleParentException(Throwable cause) throws Throwable {
    return setSuccessResult(absorbParentException(cause));
  }

  private V wrapAndCache(K data) throws Exception {
    return setSuccessResult(wrap(data));
  }

  private V setSuccessResult(V result) {
    successResult = result;
    hasResult = true;
    return result;
  }

  private ExecutionException setExceptionResult(Throwable ex) {
    exceptionResult = new ExecutionException(ex);
    hasResult = true;
    return exceptionResult;
  }

  private V getCachedResult() throws ExecutionException {
    if (exceptionResult != null) {
      throw exceptionResult;
    }
    return successResult;
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    lock.lock();
    try {
      if (hasResult) {
        return getCachedResult();
      }

      try {
        K value;
        try {
          value = parent.get();
        } catch (ExecutionException ex) {
          return handleParentException(ex.getCause());
        }
        // TODO: We should really be catching InterruptedException here
        // instead of down below because it should only propagate when it came
        // from the parent future.  This will make the try/catch logic pretty
        // ugly but it will be more correct.
        return wrapAndCache(value);
      } catch (InterruptedException ex) {
        // TODO: See TODO above, we shouldn't be catching this here
        // because that will allow an Interrupted exception that originates
        // inside wrapAndCache to propagate when it should really be wrapped
        // in an ExecutionException
        throw ex;
      } catch (Throwable ex) {
        throw setExceptionResult(convertException(ex));
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V get(long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException, ExecutionException {
    long tryLockStart = System.currentTimeMillis();
    if (!lock.tryLock(timeout, unit)) {
      // We exhausted the timeout waiting for the lock.
      throw new TimeoutException();
    }
    try {
      if (hasResult) {
        return getCachedResult();
      }
      // We got the lock before the timeout expired but now we need to reduce
      // the timeout provided by the user by the time we spent waiting for the
      // lock.
      long remainingDeadline = TimeUnit.MILLISECONDS.convert(timeout, unit) -
          (System.currentTimeMillis() - tryLockStart);
      try {
        K value;
        try {
          value = parent.get(remainingDeadline, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
          return handleParentException(ex.getCause());
        }
        // TODO: We should really be catching InterruptedException and
        // TimeoutException here instead of down below because they should only
        // propagate when they came from the parent future.  This will make the
        // try/catch logic pretty ugly but it will be more correct.
        return wrapAndCache(value);
      } catch (InterruptedException ex) {
        // TODO: See TODO above, we shouldn't be catching this here
        // because that will allow an Interrupted exception that originates
        // inside wrapAndCache to propagate when it should really be wrapped
        // in an ExecutionException
        throw ex;
      } catch (TimeoutException ex) {
        // TODO: See TODO above, we shouldn't be catching this here
        // because that will allow an Timeout exception that originates
        // inside wrapAndCache to propagate when it should really be wrapped
        // in an ExecutionException
        throw ex;
      } catch (Throwable ex) {
        throw setExceptionResult(convertException(ex));
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final int hashCode() {
    // making it explicit that we use the default hashCode() implementation so
    // that this behavior can be safely depended upon
    return super.hashCode();
  }

  @Override
  public final boolean equals(@Nullable Object obj) {
    // making it explicit that we use the default equals() implementation so
    // that this behavior can be safely depended upon
    return super.equals(obj);
  }

  protected abstract V wrap(@Nullable K key) throws Exception;

  /**
   * Override this method if you want to suppress an exception thrown by the
   * parent and return a value instead.
   */
  protected V absorbParentException(Throwable cause) throws Throwable {
    throw cause;
  }

  protected abstract Throwable convertException(Throwable cause);
}
