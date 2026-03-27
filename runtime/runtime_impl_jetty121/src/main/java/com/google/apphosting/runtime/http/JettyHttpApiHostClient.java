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

package com.google.apphosting.runtime.http;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.common.base.Preconditions;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Longs;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Response.CompleteListener;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

/** A client of the APIHost service over HTTP, implemented using the Jetty client API. */
class JettyHttpApiHostClient extends HttpApiHostClient {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final String url;
  private final HttpClient httpClient;

  private JettyHttpApiHostClient(String url, HttpClient httpClient, Config config) {
    super(config);
    this.url = url;
    this.httpClient = httpClient;
  }

  /**
   * Creates and starts a {@link JettyHttpApiHostClient}.
   *
   * <p>The {@code config.maxConnectionsPerDestination()} parameter is used to configure both
   * {@link HttpClient#setMaxConnectionsPerDestination(int)} and the maximum number of threads in
   * the {@link QueuedThreadPool}.
   *
   * @param url The URL of the API host.
   * @param config Configuration for the client.
   * @return A new, started {@link JettyHttpApiHostClient}.
   */
  static JettyHttpApiHostClient create(String url, Config config) {
    Preconditions.checkNotNull(url);
    HttpClient httpClient = new HttpClient();
    long idleTimeout = 58000; // 58 seconds, should be less than 60 used server-side.
    String envValue = System.getenv("APPENGINE_API_CALLS_IDLE_TIMEOUT_MS");
    if (envValue != null) {
      try {
        idleTimeout = Long.parseLong(envValue);
      } catch (NumberFormatException e) {
        logger.atWarning().withCause(e).log("Invalid idle timeout value: %s", envValue);
      }
    }
    httpClient.setIdleTimeout(idleTimeout);
    String schedulerName =
        HttpClient.class.getSimpleName() + "@" + httpClient.hashCode() + "-scheduler";
    ClassLoader myLoader = JettyHttpApiHostClient.class.getClassLoader();
    ThreadGroup myThreadGroup = Thread.currentThread().getThreadGroup();
    boolean daemon = false;
    Scheduler scheduler =
        new ScheduledExecutorScheduler(schedulerName, daemon, myLoader, myThreadGroup);
    /*
     * Thread Pool Configuration & Bug Analysis:
     *
     * In previous versions of the runtime, an unbounded CachedThreadPool was used here:
     * `httpClient.setExecutor(Executors.newCachedThreadPool(factory));`
     *
     * Under high load (e.g., when a customer's custom retry logic aggressively retries failing
     * RPCs like `BeginTransaction`), an unbounded thread pool creates thousands of threads instantly.
     * This leads to a system collapse:
     * 1. JVM Overload: The Java container becomes severely memory and CPU constrained.
     * 2. Appserver Flooded: The avalanche of concurrent requests from the Java container floods the
     *    C++ Appserver proxy.
     * 3. Triggering the C++ Bug Mask: Under massive load, the C++ Appserver's gRPC calls to the
     *    Datastore fail with UNAVAILABLE or RESOURCE_EXHAUSTED errors.
     * 4. The Response: Because these aren't standard application errors, the C++ code
     *    (DatastoreClientHelper::DoneImpl) masks them as `Error::INTERNAL_ERROR` and returns the
     *    message "Internal Datastore Error" to the Java client to prevent leaking internal
     *    infrastructure details.
     * 5. The Java client throws DatastoreFailureException, triggering the customer's loop again.
     *
     * To prevent this "retry storm", we explicitly use a bounded QueuedThreadPool.
     * We cap the threads at `maxConnectionsPerDestination` (which defaults to 100)
     * instead of a hardcoded 200 to prevent severe memory pressure (Thread Stack sizes)
     * on smaller AppEngine instance classes like F1 (256MB) or F2 (512MB).
     * If the system experiences a spike, Jetty will safely queue the outgoing RPCs, preventing the
     * JVM and the Appserver from being overwhelmed and eliminating the INTERNAL_ERROR fallback loop.
     */
    int maxThreads = config.maxConnectionsPerDestination().orElse(100);
    QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, 10, 60000, null, myThreadGroup);
    threadPool.setName("JettyHttpApiHostClient");
    threadPool.setDaemon(true);
    httpClient.setExecutor(threadPool);
    httpClient.setScheduler(scheduler);
    config.maxConnectionsPerDestination().ifPresent(httpClient::setMaxConnectionsPerDestination);
    try {
      httpClient.start();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new JettyHttpApiHostClient(url, httpClient, config);
  }

  private class Listener implements Response.Listener {

    private static final int MAX_LENGTH = MAX_PAYLOAD + EXTRA_CONTENT_BYTES;

    private final Context context;
    private final AnyRpcCallback<APIResponse> callback;
    private byte[] buffer;
    private int offset;

    Listener(Context context, AnyRpcCallback<APIResponse> callback) {
      this.context = context;
      this.callback = callback;
    }

    @Override
    public void onHeaders(Response response) {
      HttpFields headers = response.getHeaders();
      String lengthString = headers.get(HttpHeader.CONTENT_LENGTH.asString());
      Long length = (lengthString == null) ? null : Longs.tryParse(lengthString);
      if (length == null || config().ignoreContentLength()) {
        // We expect there to be a Content-Length, but we should be correct if less efficient
        // even if not.
        buffer = new byte[2048];
      } else if (length > MAX_LENGTH) {
        abortBecauseTooLarge(response);
        return;
      } else {
        buffer = new byte[length.intValue()];
      }
      offset = 0;
    }

    @Override
    public void onContent(Response response, ByteBuffer byteBuffer) {
      int byteCount = byteBuffer.remaining();
      if (offset + byteCount > MAX_LENGTH) {
        abortBecauseTooLarge(response);
        return;
      }
      int bufferRemaining = buffer.length - offset;
      if (byteCount > bufferRemaining) {
        int newSize = max((int) (buffer.length * 1.5), offset + byteCount);
        logger.atInfo().log(
            "Had to resize buffer, %d > %d; resizing to %d", byteCount, bufferRemaining, newSize);
        buffer = Arrays.copyOf(buffer, newSize);
        bufferRemaining = buffer.length - offset;
        Preconditions.checkState(byteCount <= bufferRemaining);
      }
      byteBuffer.get(buffer, offset, byteCount);
      offset += byteCount;
    }

    private void abortBecauseTooLarge(Response response) {
      var unused = response.abort(new ApiProxy.ResponseTooLargeException(null, null));
      // This exception will be replaced with a proper one in onComplete().
    }

    @Override
    public void onComplete(Result result) {
      if (result.isFailed()) {
        Throwable failure = result.getFailure();
        if (failure instanceof ApiProxy.ResponseTooLargeException) {
          responseTooBig(callback);
        } else if (failure instanceof TimeoutException) {
          logger.atWarning().withCause(failure).log("HTTP communication timed out");
          timeout(callback);
        } else if (failure instanceof EofException
            && failure.getCause() instanceof ClosedByInterruptException) {
          // This is a very specific combination of exceptions, which we observe is produced with
          // the particular Jetty client we're using. HttpApiProxyImplTest#interruptedApiCall
          // should detect if a future Jetty version produces a different combination.
          logger.atWarning().withCause(failure).log("HTTP communication interrupted");
          cancelled(callback);
        } else if ((failure instanceof ClosedChannelException
                || failure instanceof ClosedSelectorException)
            && config().treatClosedChannelAsCancellation()) {
          logger.atWarning().log("Treating %s as cancellation", failure.getClass().getSimpleName());
          cancelled(callback);
        } else if (failure instanceof RejectedExecutionException) {
          logger.atWarning().withCause(failure).log("API connection appears to be disabled");
          cancelled(callback);
        } else if (failure instanceof HttpResponseException) {
          // TODO(b/111131627) remove this once upgraded to Jetty that includes the cause
          HttpResponseException hre = (HttpResponseException) failure;
          Response response = hre.getResponse();
          String httpError = response.getStatus() + " " + response.getReason();
          logger.atWarning().withCause(failure).log("HTTP communication failed: %s", httpError);
          if (hre.getCause() == null) {
            failure = new Exception(httpError, hre);
          }
          communicationFailure(context, failure + ": " + httpError, callback, failure);
        } else {
          logger.atWarning().withCause(failure).log("HTTP communication failed");
          communicationFailure(context, String.valueOf(failure), callback, failure);
        }
      } else {
        Response response = result.getResponse();
        if (response.getStatus() == HttpURLConnection.HTTP_OK) {
          receivedResponse(buffer, offset, context, callback);
        } else {
          String httpError = response.getStatus() + " " + response.getReason();
          logger.atWarning().log("HTTP communication got error: %s", httpError);
          communicationFailure(context, httpError, callback, null);
        }
      }
    }
  }

  /**
   * Asynchronously sends an API request to the API host.
   *
   * @param requestBytes The serialized API request.
   * @param context The context for the request, including deadline information.
   * @param callback Callback to be invoked with the API response or failure.
   */
  @Override
  void send(
      byte[] requestBytes,
      HttpApiHostClient.Context context,
      AnyRpcCallback<APIResponse> callback) {
    Request request =
        httpClient
            .newRequest(url)
            .method(HttpMethod.POST)
            .body(new BytesRequestContent(CONTENT_TYPE_VALUE, requestBytes));

    request =
        request.headers(
            headers -> {
              for (Map.Entry<String, String> header : HEADERS.entrySet()) {
                headers.add(header.getKey(), header.getValue());
              }
            });

    if (context.getDeadlineNanos().isPresent()) {
      double deadlineSeconds = context.getDeadlineNanos().get() / 1e9;

      request =
          request.headers(
              headers -> headers.add(DEADLINE_HEADER, Double.toString(deadlineSeconds)));

      // If the request exceeds the deadline, one of two things can happen: (1) the API server
      // returns with a deadline-exceeded status; (2) ApiProxyImpl will time out because of the
      // TimedFuture class that it uses. The only purpose of this fallback deadline is to ensure
      // that, if the server is genuinely unresponsive, we will eventually free up the resources
      // associated with the HTTP request.
      // If ApiProxyImpl times out, it will be 0.5 seconds after the called-for time out, which is
      // sooner than here with the default value of extraTimeoutSeconds.
      double fallbackDeadlineSeconds = deadlineSeconds + config().extraTimeoutSeconds();
      request.timeout((long) (fallbackDeadlineSeconds * 1e9), NANOSECONDS);
    }
    CompleteListener completeListener = new Listener(context, callback);
    request.send(completeListener);
  }

  /**
   * Disables the client by stopping the underlying {@link HttpClient}. Subsequent calls to {@link
   * #send} may fail until {@link #enable()} is called.
   */
  @Override
  public synchronized void disable() {
    try {
      httpClient.stop();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Enables the client by starting the underlying {@link HttpClient}. */
  @Override
  public synchronized void enable() {
    try {
      httpClient.start();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
