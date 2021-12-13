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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;

/**
 * Tests the TimedFuture class.
 *
 */
public class TimedFutureTest extends TestCase {

  private final Future<Object> sleepForever = new FutureTask<Object>(new Callable<Object>() {
      @Override
      public Object call() {
        try {
          Thread.sleep(5000);
          return null;
        } catch (InterruptedException ex) {
          return new RuntimeException(ex);
        }
      }
  });

  public void testGet() throws Exception {
    Future<Object> future = new TimedFuture<Object>(sleepForever, 1000) {
      @Override
      public RuntimeException createDeadlineException() {
        return new RuntimeException("deadline");
      }
    };
    try {
      future.get();
    } catch (ExecutionException ex) {
      assertEquals("java.lang.RuntimeException: deadline", ex.getCause().toString());
    }
  }

  public void testGetWithShortTimeout() throws Exception {
    Future<Object> future = new TimedFuture<Object>(sleepForever, 1000) {
      @Override
      public RuntimeException createDeadlineException() {
        return new RuntimeException("deadline");
      }
    };
    try {
      future.get(500, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      // expected
    }
  }

  public void testGetWithLongTimeout() throws Exception {
    Future<Object> future = new TimedFuture<Object>(sleepForever, 1000) {
      @Override
      public RuntimeException createDeadlineException() {
        return new RuntimeException("deadline");
      }
    };
    try {
      future.get(2000, TimeUnit.MILLISECONDS);
    } catch (ExecutionException ex) {
      assertEquals("java.lang.RuntimeException: deadline", ex.getCause().toString());
    }
  }
}
