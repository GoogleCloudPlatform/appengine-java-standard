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

package com.google.appengine.runtime.lite;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.appengine.api.ThreadManager;
import com.google.apphosting.runtime.BackgroundRequestCoordinator;
import com.google.common.flogger.GoogleLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Dispatches a incoming background thread request, connecting to the waiting runnable from the app
 * code which initiated the background thread.
 */
class BackgroundRequestDispatcher extends BackgroundRequestCoordinator {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * How long should we wait for {@code ApiProxyImpl} to exchange the background thread's {@code
   * Runnable}.
   */
  private static final Duration WAIT_FOR_USER_RUNNABLE_DEADLINE = Duration.ofSeconds(60);

  private static final String X_APPENGINE_USER_IP = "x-appengine-user-ip";
  private static final String X_APPENGINE_BACKGROUNDREQUEST = "x-appengine-backgroundrequest";
  private static final String BACKGROUND_REQUEST_URL = "/_ah/background";
  private static final String BACKGROUND_REQUEST_SOURCE_IP = "0.1.0.3";

  public AbstractHandler createHandler() {
    return new BackgroundRequestHandler();
  }

  /**
   * A runnable which lets us start running before we even know what to run. The run method first
   * waits to be given a Runnable (from another thread) via the supplyRunnable method, and then we
   * run that.
   */
  static class EagerRunner implements Runnable {
    private final Exchanger<Runnable> runnableExchanger = new Exchanger<>();

    /**
     * Pass the given runnable to whatever thread's running our run method. This will block until
     * run() is called if it hasn't been already.
     */
    void supplyRunnable(Runnable runnable) throws InterruptedException, TimeoutException {
      runnableExchanger.exchange(
          runnable, WAIT_FOR_USER_RUNNABLE_DEADLINE.toMillis(), MILLISECONDS);
    }

    @Override
    public void run() {
      // We don't actually know what to run yet! Wait on someone to call supplyRunnable:
      Runnable runnable;
      try {
        runnable =
            runnableExchanger.exchange(
                null, WAIT_FOR_USER_RUNNABLE_DEADLINE.toMillis(), MILLISECONDS);
      } catch (TimeoutException ex) {
        logger.atSevere().withCause(ex).log("Timed out while awaiting runnable");
        return;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        logger.atSevere().withCause(ex).log("Interrupted while awaiting runnable");
        return;
      }

      // Now actually run:
      runnable.run();
    }
  }

  /** Dispatch an incoming background request, connecting it to the waiting app code. */
  void dispatch(String requestId) throws InterruptedException, TimeoutException {
    // First, create an ordinary request thread as a child of this background thread.
    // The interface of waitForUserRunnable() requires us to provide the app code with a working
    // thread *in the same exchange* where we get the runnable the user wants to run in the thread.
    // This prevents us from actually directly feeding that runnable to the thread. To work around
    // this conundrum, we create an EagerRunner, which lets us start running the thread without
    // knowing yet what we want to run.
    EagerRunner eagerRunner = new EagerRunner();
    Thread thread = ThreadManager.createThreadForCurrentRequest(eagerRunner);

    // Give this thread to the app code and get its desired runnable in response:
    Runnable runnable =
        waitForUserRunnable(requestId, thread, WAIT_FOR_USER_RUNNABLE_DEADLINE.toMillis());

    // Finally, hand that runnable to the thread so it can actually start working.
    // This will block until Thread.start() is called by the app code. This is by design: we must
    // not exit this request handler until the thread has started *and* completed, otherwise the
    // serving infrastructure will cancel our ability to make API calls. We're effectively "holding
    // open the door" on the spawned thread's ability to make App Engine API calls.
    eagerRunner.supplyRunnable(runnable);

    // Wait for the thread to end:
    thread.join();
  }

  class BackgroundRequestHandler extends AbstractHandler {
    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {
      if (!BACKGROUND_REQUEST_URL.equals(request.getRequestURI())) {
        return;
      }

      if (!BACKGROUND_REQUEST_SOURCE_IP.equals(request.getHeader(X_APPENGINE_USER_IP))) {
        return;
      }

      String backgroundRequestId =
          Optional.ofNullable(request.getHeader(X_APPENGINE_BACKGROUNDREQUEST))
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Did not receive a background request identifier."));

      try {
        dispatch(backgroundRequestId);
      } catch (InterruptedException | TimeoutException ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new ServletException("Failed to dispatch background request", ex);
      }
      response.setContentType("text/plain");
      PrintWriter out = response.getWriter();
      out.print("OK");

      baseRequest.setHandled(true);
    }
  }
}
