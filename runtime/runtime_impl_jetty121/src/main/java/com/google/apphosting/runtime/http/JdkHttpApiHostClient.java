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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An alternative API client that uses the JDK's built-in HTTP client. This is likely to be much
 * less performant than {@link JettyHttpApiHostClient} but should allow us to determine whether
 * communications problems we are seeing are due to the Jetty client.
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

  static JdkHttpApiHostClient create(String url, Config config) {
    try {
      ThreadFactory factory =
          runnable -> {
            Thread t = new Thread(rootThreadGroup(), runnable);
            t.setName("JdkHttp-" + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
          };
      Executor executor = Executors.newCachedThreadPool(factory);
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

  @Override
  public void enable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void disable() {
    throw new UnsupportedOperationException();
  }
}
