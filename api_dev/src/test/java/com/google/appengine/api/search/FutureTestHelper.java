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

import java.util.concurrent.ExecutionException;

/**
 */
public class FutureTestHelper {
  public static class InterruptedFuture<T> extends FutureHelper.FakeFuture<T> {
    InterruptedFuture(T result) {
      super(result);
    }

    @Override
    public T get() throws InterruptedException {
      throw new InterruptedException("bad");
    }
  }

  public static class ExecutionExceptionFuture<T> extends FutureHelper.FakeFuture<T> {
    ExecutionExceptionFuture(T result) {
      super(result);
    }

    @Override
    public T get() throws ExecutionException {
      throw new ExecutionException("bad", new SearchServiceException("worse"));
    }
  }
}
