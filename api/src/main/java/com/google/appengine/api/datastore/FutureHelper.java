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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.AppEngineInternal;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utilities for working with {@link Future Futures} in the synchronous datastore api.
 *
 */
@AppEngineInternal
public final class FutureHelper {

  /**
   * Return the result of the provided {@link Future}, converting all checked exceptions to
   * unchecked exceptions so the caller doesn't have to handle them. If an {@link ExecutionException
   * ExecutionException} is thrown the cause is wrapped in a {@link RuntimeException}. If an {@link
   * InterruptedException} is thrown it is wrapped in a {@link DatastoreFailureException}.
   *
   * @param future The Future whose result we want to return.
   * @param <T> The type of the provided Future.
   * @return The result of the provided Future.
   */
  public static <T> T quietGet(Future<T> future) {
    try {
      return getInternal(future);
    } catch (ExecutionException e) {
      throw propagateAsRuntimeException(e);
    }
  }

  /**
   * Return the result of the provided {@link Future}, converting all checked exceptions except
   * those of the provided type to unchecked exceptions so the caller doesn't have to handle them.
   * If an {@link ExecutionException ExecutionException} is thrown and the type of the cause does
   * not equal {@code exceptionClass} the cause is wrapped in a {@link RuntimeException}. If the
   * type of the cause does equal {@code exceptionClass} the cause itself is thrown. If an {@link
   * InterruptedException} is thrown it is wrapped in a {@link DatastoreFailureException}.
   *
   * @param future The Future whose result we want to return.
   * @param <T> The type of the provided Future.
   * @param exceptionClass Exceptions of this type will be rethrown.
   * @return The result of the provided Future.
   * @throws E Thrown If an ExecutionException with a cause of the appropriate type is caught.
   */
  public static <T, E extends Exception> T quietGet(Future<T> future, Class<E> exceptionClass)
      throws E {
    try {
      return getInternal(future);
    } catch (ExecutionException e) {
      if (e.getCause().getClass().equals(exceptionClass)) {
        @SuppressWarnings("unchecked")
        E exception = (E) e.getCause();
        throw exception;
      }
      throw propagateAsRuntimeException(e);
    }
  }

  private static <T> T getInternal(Future<T> future) throws ExecutionException {
    try {
      return future.get();
    } catch (InterruptedException e) {
      // RequestManager is known to interrupt the thread when the request times out to give it a
      // chance to clean up and shut down.

      // According to java.lang.Thread docs, the sole effect of setting interrupt is to cause
      // Thread.interrupted() to return true. In particular, it does not cause subsequent entry into
      // thread code (sleep, condition, whatever) to have an additional thread interrupt injected.
      // That only happens if a thread calls interrupt on another thread that is in a wait of some
      // sort.
      //
      // Generally InterruptedException should be propagated. In this case we propagated the
      // exception as part of the DatastoreFailureException.  We also set the interrupt bit which
      // is a bit dubious.
      Thread.currentThread().interrupt();
      // We try to give a friendly error message.
      if (ApiProxy.getCurrentEnvironment().getRemainingMillis() <= 0) {
        throw new DatastoreFailureException(
            "The thread has been interrupted and the request has timed out", e);
      } else {
        throw new DatastoreFailureException("The thread has been interrupted", e);
      }
    }
  }

  /**
   * Propagates the {@code cause} of the given {@link ExecutionException} as a RuntimeException.
   *
   * @return nothing will ever be returned; this return type is only for convenience.
   */
  private static RuntimeException propagateAsRuntimeException(ExecutionException ee) {
    if (ee.getCause() instanceof RuntimeException) {
      // unwrap and rethrow
      throw (RuntimeException) ee.getCause();
    } else if (ee.getCause() instanceof Error) {
      // unwrap and rethrow
      throw (Error) ee.getCause();
    } else {
      throw new UndeclaredThrowableException(ee.getCause());
    }
  }

  /**
   * A base class for a {@link Future} that derives its result from multiple other futures.
   *
   * @param <K> The type used by sub-futures.
   * @param <V> The type returned by this future.
   */
  abstract static class MultiFuture<K, V> implements Future<V> {
    protected final Iterable<Future<K>> futures;

    public MultiFuture(Iterable<Future<K>> futures) {
      this.futures = futures;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean result = true;
      for (Future<K> future : futures) {
        result &= future.cancel(mayInterruptIfRunning);
      }
      return result;
    }

    @Override
    public boolean isCancelled() {
      boolean result = true;
      for (Future<K> future : futures) {
        result &= future.isCancelled();
      }
      return result;
    }

    @Override
    public boolean isDone() {
      boolean result = true;
      for (Future<K> future : futures) {
        result &= future.isDone();
      }
      return result;
    }
  }

  /**
   * We need a future implementation that clears out the txn callbacks when any variation of get()
   * is called. This is important because if get() throws an exception, we don't want that exception
   * to resurface when the txn gets committed or rolled back.
   */
  static final class TxnAwareFuture<T> implements Future<T> {
    private final Future<T> future;
    private final Transaction txn;
    private final TransactionStack txnStack;

    TxnAwareFuture(Future<T> future, Transaction txn, TransactionStack txnStack) {
      this.future = future;
      this.txn = txn;
      this.txnStack = txnStack;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return future.isCancelled();
    }

    @Override
    public boolean isDone() {
      return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      txnStack.getFutures(txn).remove(future);
      return future.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      txnStack.getFutures(txn).remove(future);
      return future.get(timeout, unit);
    }
  }

  /**
   * Wraps an already-resolved result in a {@link Future}.
   *
   * @param <T> The type of the Future.
   */
  static class FakeFuture<T extends @Nullable Object> implements Future<T> {
    private final T result;

    FakeFuture(T result) {
      this.result = result;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      // cancel() returns false if the Future has already executed
      return false;
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
    @SuppressWarnings("unused")
    public T get() throws ExecutionException {
      return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) {
      return result;
    }
  }
}
