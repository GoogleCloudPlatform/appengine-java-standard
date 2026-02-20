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

import com.google.apphosting.api.ApiProxy;
import com.google.common.flogger.GoogleLogger;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * This {@link ThreadFactory} creates {@link Thread} objects that
 * replicate the current request's environment, and are interrupted
 * when the current request completes.
 *
 */
public class RequestThreadFactory implements ThreadFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final int THREAD_STARTUP_LATENCY_MS = 20;

  // This should be kept in sync with default_frontend_userland_deadline_ms
  private static final int ONLINE_REQUEST_DEADLINE_MS = 60000;
  // This should be kept in sync with default_offline_userland_deadline_ms
  private static final int OFFLINE_REQUEST_DEADLINE_MS = 600000;

  @Override
  public Thread newThread(final Runnable runnable) {

    final ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();

    Thread thread =
        new Thread() {
          /**
           * If the thread is started, install a {@link RequestEndListener} to interrupt the thread
           * at the end of the request. We don't yet enforce request deadlines in the DevAppServer
           * so we don't need to handle other interrupt cases yet.
           */
          @Override
          @SuppressWarnings("Interruption")
          public synchronized void start() {
            try {
              Thread.sleep(THREAD_STARTUP_LATENCY_MS);
            } catch (InterruptedException ex) {
              // We can't propagate the exception from here so
              // just log, reset the bit, and continue.
              logger.atInfo().withCause(ex).log(
                  "Interrupted while simulating thread startup latency");
              Thread.currentThread().interrupt();
            }
            super.start();
            final Thread thread = this; // Thread.this doesn't work from an anon subclass
            RequestEndListenerHelper.register(
                env -> {
                  if (thread.isAlive()) {
                    logger.atInfo().log("Interrupting request thread: %s", thread);
                    // This is one of the few places where it's okay to call thread.interrupt().
                    thread.interrupt();
                    logger.atInfo().log("Waiting up to 100ms for thread to complete: %s", thread);
                    try {
                      thread.join(100);
                    } catch (InterruptedException ex) {
                      logger.atInfo().log("Interrupted while waiting.");
                    }
                    if (thread.isAlive()) {
                      logger.atInfo().log("Interrupting request thread again: %s", thread);
                      thread.interrupt();
                      long remaining = getRemainingDeadlineMillis(env);
                      logger.atInfo().log(
                          "Waiting up to %s ms for thread to complete: %s", remaining, thread);
                      try {
                        thread.join(remaining);
                      } catch (InterruptedException ex) {
                        logger.atInfo().log("Interrupted while waiting.");
                      }
                      if (thread.isAlive()) {
                        Throwable stack = new Throwable();
                        stack.setStackTrace(thread.getStackTrace());
                        logger.atSevere().withCause(stack).log(
                            "Thread left running: %s.  In production this will cause the request to"
                                + " fail.",
                            thread);
                      }
                    }
                  }
                });
          }

          @Override
          public void run() {
            // Copy the current environment to the new thread.
            ApiProxy.setEnvironmentForCurrentThread(environment);
            // Switch back to the calling context before running the user's code.
            runnable.run();
            // Don't bother unsetting the environment.  We're
            // not going to reuse this thread and we want the
            // environment still to be set during any
            // UncaughtExceptionHandler (which happens after
            // run() completes/throws).
          }
        };
    // This system property is used to check if the thread is
    // running user code (ugly, I know).  This thread is now
    // running user code so we set it as well.
    System.setProperty("devappserver-thread-" + thread.getName(), "true");
    return thread;
          
       
  }

  private long getRemainingDeadlineMillis(ApiProxy.Environment environment) {
    int requestTimeMillis;
    Map<String, Object> attributes = environment.getAttributes();
    Date startTime = (Date) attributes.get(LocalEnvironment.START_TIME_ATTR);
    if (startTime != null) {
      startTime = new Date();
    }
    Boolean offline = (Boolean) attributes.get(ApiProxyLocalImpl.IS_OFFLINE_REQUEST_KEY);
    if (offline != null && offline) {
      requestTimeMillis = OFFLINE_REQUEST_DEADLINE_MS;
    } else {
      requestTimeMillis = ONLINE_REQUEST_DEADLINE_MS;
    }
    return requestTimeMillis - (System.currentTimeMillis() - startTime.getTime());
  }
}
