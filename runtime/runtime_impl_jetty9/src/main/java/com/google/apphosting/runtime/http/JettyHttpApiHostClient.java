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
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.CompleteListener;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * A client of the APIHost service over HTTP, implemented using the Jetty client API.
 *
 */
class JettyHttpApiHostClient extends HttpApiHostClient {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final AtomicInteger threadCount = new AtomicInteger();

  private final String url;
  private final HttpClient httpClient;

  private JettyHttpApiHostClient(String url, HttpClient httpClient, Config config) {
    super(config);
    this.url = url;
    this.httpClient = httpClient;
  }

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
    ThreadFactory factory =
        runnable -> {
          Thread t = new Thread(myThreadGroup, runnable);
          t.setName("JettyHttpApiHostClient-" + threadCount.incrementAndGet());
          t.setDaemon(true);
          return t;
        };
    // By default HttpClient will use a QueuedThreadPool with minThreads=8 and maxThreads=200.
    // 8 threads is probably too much for most apps, especially since asynchronous I/O means that
    // 8 concurrent API requests probably don't need that many threads. It's also not clear
    // what advantage we'd get from using a QueuedThreadPool with a smaller minThreads value, versus
    // just one of the standard java.util.concurrent pools. Here we have minThreads=1, maxThreads=∞,
    // and idleTime=60 seconds. maxThreads=200 and maxThreads=∞ are probably equivalent in practice.
    httpClient.setExecutor(Executors.newCachedThreadPool(factory));
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

  private class Listener extends Response.Listener.Adapter {

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
      response.abort(new ApiProxy.ResponseTooLargeException(null, null));
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

  @Override
  void send(byte[] requestBytes, HttpApiHostClient.Context context,
      AnyRpcCallback<APIResponse> callback) {
    Request request = httpClient
        .newRequest(url)
        .method(HttpMethod.POST)
        .content(new BytesContentProvider(requestBytes), CONTENT_TYPE_VALUE);
    for (Map.Entry<String, String> header : HEADERS.entrySet()) {
      request.header(header.getKey(), header.getValue());
    }
    if (context.getDeadlineNanos().isPresent()) {
      double deadlineSeconds = context.getDeadlineNanos().get() / 1e9;
      request.header(DEADLINE_HEADER, Double.toString(deadlineSeconds));
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
