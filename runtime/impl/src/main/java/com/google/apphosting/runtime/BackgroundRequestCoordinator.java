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

package com.google.apphosting.runtime;

import com.google.common.flogger.GoogleLogger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * BackgroundRequestCoordinator facilitates the exchange of two pieces
 * of data between the background
 * {@link java.util.concurrent.ThreadFactory} and the
 * {@link JavaRuntime} code that processes the fake request.
 *
 * <p>Background threads are backed by a "fake" request, which is
 * initiated using the System API.  The System API will return a
 * request identifier and also initiate a "fake" request with this
 * identifier stored in a header.  The runtime must match each fake
 * request up with the original API call and pass the user's
 * {@link Runnable} to the thread so it can be executed, and pass the
 * {@link Thread} back to the user's code so it can be joined and
 * referenced.
 *
 * <p>Unfortunately there is a race between these two threads.  We
 * don't know whether the System API RPC will return first or whether
 * the background request will be received first.
 *
 */
public class BackgroundRequestCoordinator {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Map from request identifiers to an {@link Exchanger} that can be
   * used to exchange a user-supplied {@link Runnable} for the
   * {@link Thread} on which the user code should run.  All access to
   * this map must be synchronized.
   */
  private final Map<String, Exchanger<Object>> exchangerMap = new HashMap<>();

  /**
   * Wait for the fake request with the specified {@code requestId} to
   * call {@link #waitForUserRunnable} and then exchange
   * {@code runnable} for the specified {@link Thread}.
   */
  public Thread waitForThreadStart(String requestId, Runnable runnable, long deadlineInMillis)
      throws InterruptedException, TimeoutException {
    logger.atInfo().log("Waiting until thread creation for %s", requestId);
    Exchanger<Object> exchanger = getOrCreateExchanger(requestId);
    try {
      return (Thread) exchanger.exchange(runnable, deadlineInMillis, TimeUnit.MILLISECONDS);
    } finally {
      removeExchanger(requestId);
    }
  }

  /**
   * Wait for the system API call with the specified {@code requestId}
   * to call {@link #waitForThreadStart} and then exchange
   * {@code thread} for the specified {@link Runnable}.
   */
  public Runnable waitForUserRunnable(String requestId, Thread thread, long deadLineInMillis)
      throws InterruptedException, TimeoutException {
    logger.atInfo().log("Got thread creation for %s", requestId);
    Exchanger<Object> exchanger = getOrCreateExchanger(requestId);
    try {
      return (Runnable) exchanger.exchange(thread, deadLineInMillis, TimeUnit.MILLISECONDS);
    } finally {
      removeExchanger(requestId);
    }
  }

  private synchronized void removeExchanger(String requestId) {
    exchangerMap.remove(requestId);
  }

  /**
   * Look up the {@link Exchanger} for the specified request.  If none
   * is available, one is atomically created.
   */
  private synchronized Exchanger<Object> getOrCreateExchanger(String requestId) {
    Exchanger<Object> exchanger = exchangerMap.get(requestId);
    if (exchanger == null) {
      exchanger = new Exchanger<>();
      exchangerMap.put(requestId, exchanger);
    }
    return exchanger;
  }
}
