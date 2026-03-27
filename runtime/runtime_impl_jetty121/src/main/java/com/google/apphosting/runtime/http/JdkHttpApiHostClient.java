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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.common.flogger.GoogleLogger;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An alternative API client that uses the JDK's built-in HTTP client. This is likely to be much
 * less performant than {@link JettyHttpApiHostClient} but should allow us to determine whether
 * communications problems we are seeing are due to the Jetty client.
 *
 * <p>By default, this client uses a bounded thread pool to execute API calls, with the maximum
 * number of threads determined by configuration. If the system property {@code
 * appengine.api.use.virtualthreads} is set to {@code true}, it will instead use virtual threads via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.
 */
class JdkHttpApiHostClient extends HttpApiHostClient {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final int MAX_LENGTH = MAX_PAYLOAD + EXTRA_CONTENT_BYTES;

  private static final AtomicInteger threadCount = new AtomicInteger();

  private final URL url;
  private final Executor executor;

  private JdkHttpApiHostClient(Config config, URL url, Executor executor) {
    super(config);
    this.url = url;
    this.executor = executor;
  }

  /**
   * Creates a {@link JdkHttpApiHostClient}.
   *
   * <p>If the system property {@code appengine.api.use.virtualthreads} is set to {@code true}, a
   * virtual thread executor is used to run requests, and {@code
   * config.maxConnectionsPerDestination()} is ignored. Otherwise, a bounded {@link
   * ThreadPoolExecutor} is created, with {@code maxThreads} derived from {@code
   * config.maxConnectionsPerDestination()}.
   *
   * @param url The URL of the API host.
   * @param config Configuration for the client, including connection limits.
   * @return A new {@link JdkHttpApiHostClient}.
   */
  @SuppressWarnings("AllowVirtualThreads")
  static JdkHttpApiHostClient create(String url, Config config) {
    try {
      Executor executor;
      if (Boolean.getBoolean("appengine.api.use.virtualthreads")) {
        executor = Executors.newVirtualThreadPerTaskExecutor();
      } else {
        ThreadFactory factory =
            runnable -> {
              Thread t = new Thread(rootThreadGroup(), runnable);
              t.setName("JdkHttp-" + threadCount.incrementAndGet());
              t.setDaemon(true);
              return t;
            };
        /*
         * Thread Pool Configuration & Bug Analysis:
         *
         * Similar to the JettyHttpApiHostClient, we explicitly bound the thread pool.
         * We cap the threads at `maxConnectionsPerDestination` (which defaults to 100)
         * instead of a hardcoded 200 to prevent severe memory pressure (Thread Stack sizes)
         * on smaller AppEngine instance classes like F1 (256MB) or F2 (512MB).
         * An unbounded thread pool allows a failing RPC to rapidly spin up thousands
         * of threads under retry, which overwhelms the JVM and the internal Datastore
         * Appserver connection, forcing it to respond with masking INTERNAL_ERROR fallbacks.
         */
        int maxThreads = config.maxConnectionsPerDestination().orElse(100);
        ThreadPoolExecutor tpe =
            new ThreadPoolExecutor(
                maxThreads, maxThreads, 60L, SECONDS, new LinkedBlockingQueue<>(), factory);
        tpe.allowCoreThreadTimeOut(true);
        executor = tpe;
      }
      return new JdkHttpApiHostClient(config, new URL(url), executor);
    } catch (MalformedURLException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ThreadGroup rootThreadGroup() {
    ThreadGroup group = Thread.currentThread().getThreadGroup();
    ThreadGroup parent;
    while ((parent = group.getParent()) != null) {
      group = parent;
    }
    return group;
  }

  /**
   * Asynchronously sends an API request to the API host using a thread pool.
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
    executor.execute(() -> doSend(requestBytes, context, callback));
  }

  private void doSend(
      byte[] requestBytes,
      HttpApiHostClient.Context context,
      AnyRpcCallback<APIResponse> callback) {
    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      HEADERS.forEach(connection::addRequestProperty);
      connection.addRequestProperty("Content-Type", "application/octet-stream");
      if (context.getDeadlineNanos().isPresent()) {
        double deadlineSeconds = context.getDeadlineNanos().get() / 1e9;
        connection.addRequestProperty(DEADLINE_HEADER, Double.toString(deadlineSeconds));
        int deadlineMillis =
            Ints.saturatedCast(max(1, context.getDeadlineNanos().get() / 1_000_000));
        connection.setReadTimeout(deadlineMillis);
      }
      connection.setFixedLengthStreamingMode(requestBytes.length);
      connection.setRequestMethod("POST");
      try (OutputStream out = connection.getOutputStream()) {
        out.write(requestBytes);
      }
      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
        int length = connection.getContentLength();
        if (length > MAX_LENGTH) {
          connection.getInputStream().close();
          responseTooBig(callback);
        } else {
          byte[] buffer = new byte[length];
          try (InputStream in = connection.getInputStream()) {
            ByteStreams.readFully(in, buffer); // EOFException (an IOException) if too few bytes
            receivedResponse(buffer, length, context, callback);
          }
        }
      }
    } catch (SocketTimeoutException e) {
      logger.atWarning().withCause(e).log("SocketTimeoutException");
      timeout(callback);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("IOException");
      communicationFailure(context, e.toString(), callback, e);
    }
  }

  /**
   * This operation is not supported by JdkHttpApiHostClient.
   *
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void enable() {
    throw new UnsupportedOperationException();
  }

  /**
   * This operation is not supported by JdkHttpApiHostClient.
   *
   * @throws UnsupportedOperationException always.
   */
  @Override
  public void disable() {
    throw new UnsupportedOperationException();
  }
}
