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

package com.google.appengine.api.search;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for working with {@link Future Futures} in the synchronous
 * search api.
 *
 */
final class FutureHelper {

  /**
   * Return the result of the provided {@link Future}, converting all
   * checked exceptions to unchecked exceptions so the caller doesn't have to
   * handle them.  If an {@link ExecutionException ExecutionException} is
   * thrown the cause is wrapped in a {@link RuntimeException}.  If an {@link
   * InterruptedException} is thrown it is wrapped in a {@link
   * SearchServiceException}.
   *
   * @param future the Future whose result we want to return
   * @param <T> the type of the provided Future
   * @return the result of the provided Future
   * @throws SearchBaseException if some failure occurred in the search service
   */
  @SuppressWarnings("unchecked")
  static <T> T quietGet(Future<T> future) throws SearchBaseException {
    try {
      return getInternal(future);
    } catch (ExecutionException e) {
      return (T) processExecutionException(e);
    }
  }

  /**
   * Return the result of the provided {@link Future}, converting all
   * checked exceptions except those of the provided type to unchecked
   * exceptions so the caller doesn't have to handle them.  If an {@link
   * ExecutionException ExecutionException} is thrown and the type of the cause
   * does not equal {@code exceptionClass} the cause is wrapped in a {@link
   * RuntimeException}.  If the type of the cause does equal {@code
   * exceptionClass} the cause itself is thrown.  If an {@link
   * InterruptedException} is thrown it is wrapped in a {@link
   * SearchServiceException}.
   *
   * @param future the Future whose result we want to return
   * @param <T> the type of the provided Future
   * @param exceptionClass Exceptions of this type will be re-thrown
   * @return the result of the provided Future
   * @throws E if an ExecutionException with a cause of the appropriate
   * type is caught
   */
  @SuppressWarnings("unchecked")
  static <T, E extends Exception> T quietGet(Future<T> future, Class<E> exceptionClass)
      throws E, SearchBaseException {
    try {
      return getInternal(future);
    } catch (ExecutionException e) {
      if (e.getCause().getClass().equals(exceptionClass)) {
        @SuppressWarnings("unchecked")
        E exception = (E) e.getCause();
        throw exception;
      }
      return (T) processExecutionException(e);
    }
  }

  private static <T> T getInternal(Future<T> future) throws ExecutionException {
    try {
      return future.get();
    } catch (InterruptedException e) {
      throw new SearchServiceException("Unexpected failure", e);
    }
  }

  private static <T> T processExecutionException(ExecutionException e) {
    if (e.getCause() instanceof RuntimeException) {
      // unwrap and re-throw
      throw (RuntimeException) e.getCause();
    } else if (e.getCause() instanceof Error) {
      // unwrap and re-throw
      throw (Error) e.getCause();
    } else {
      throw new UndeclaredThrowableException(e.getCause());
    }
  }

  /**
   * Wraps an already-resolved result in a {@link Future}.
   *
   * @param <T> the type of the Future
   */
  static class FakeFuture<T> implements Future<T> {
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

    @SuppressWarnings("unused")
    @Override
    public T get() throws InterruptedException, ExecutionException {
      return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) {
      return result;
    }
  }
}
