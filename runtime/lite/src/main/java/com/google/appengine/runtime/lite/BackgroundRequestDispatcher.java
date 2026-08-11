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

import static com.google.apphosting.runtime.AppEngineConstants.BACKGROUND_REQUEST_SOURCE_IP;
import static com.google.apphosting.runtime.AppEngineConstants.BACKGROUND_REQUEST_URL;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_BACKGROUNDREQUEST;
import static com.google.apphosting.runtime.AppEngineConstants.X_APPENGINE_USER_IP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.appengine.api.ThreadManager;
import com.google.apphosting.runtime.BackgroundRequestCoordinator;
import com.google.common.flogger.GoogleLogger;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

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

  public Handler createHandler() {
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
    EagerRunner eagerRunner = new EagerRunner();
    Thread thread = ThreadManager.createThreadForCurrentRequest(eagerRunner);

    Runnable runnable =
        waitForUserRunnable(requestId, thread, WAIT_FOR_USER_RUNNABLE_DEADLINE.toMillis());

    eagerRunner.supplyRunnable(runnable);

    thread.join();
  }

  class BackgroundRequestHandler extends Handler.Abstract {
    @Override
    public boolean handle(
        Request request,
        Response response,
        Callback callback)
        throws Exception {
      String decodedPath = request.getHttpURI().getDecodedPath();
      if (!decodedPath.equals(BACKGROUND_REQUEST_URL)) {
        return false;
      }

      String userIp = request.getHeaders().get(X_APPENGINE_USER_IP);
      if (!Objects.equals(userIp, BACKGROUND_REQUEST_SOURCE_IP)) {
        return false;
      }

      String backgroundRequestId = request.getHeaders().get(X_APPENGINE_BACKGROUNDREQUEST);
      if (backgroundRequestId == null) {
        throw new IllegalArgumentException("Did not receive a background request identifier.");
      }

      try {
        dispatch(backgroundRequestId);
      } catch (InterruptedException | TimeoutException ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new ServletException("Failed to dispatch background request", ex);
      }
      response.setStatus(200);
      response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain");
      response.write(true, ByteBuffer.wrap("OK".getBytes(UTF_8)), callback);
      return true;
    }
  }
}
