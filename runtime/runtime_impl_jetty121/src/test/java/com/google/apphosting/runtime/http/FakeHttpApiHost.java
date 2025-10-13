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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Doubles;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fake HTTP-based server for APIHost. This is intended to mimic <a
 * href="http://google3/apphosting/sandbox/titanium/http_apihost.cc">this server</a>.
 */
public class FakeHttpApiHost {
  public interface ApiRequestHandler {
    RemoteApiPb.Response handle(RemoteApiPb.Request request);
  }

  static final String RPC_ENDPOINT_HEADER = "X-Google-RPC-Service-Endpoint";
  static final String RPC_ENDPOINT_VALUE = "app-engine-apis";
  static final String RPC_METHOD_HEADER = "X-Google-RPC-Service-Method";
  static final String RPC_METHOD_VALUE = "/VMRemoteAPI.CallRemoteAPI";
  static final String CONTENT_TYPE_HEADER = "Content-Type";
  static final String CONTENT_TYPE_VALUE = "application/octet-stream";
  static final String REQUEST_ENDPOINT = "/rpc_http";
  static final String DEADLINE_HEADER = "X-Google-RPC-Service-Deadline";
  static final ByteString BAD_RESPONSE = ByteString.copyFromUtf8("_BAD");

  private final HttpServer httpApiHostServer;
  private final URL httpApiHostUrl;
  private final ReentrantLock freezeLock;

  private FakeHttpApiHost(
      HttpServer httpApiHostServer, URL httpApiHostUrl, ReentrantLock freezeLock) {
    this.httpApiHostServer = httpApiHostServer;
    this.httpApiHostUrl = httpApiHostUrl;
    this.freezeLock = freezeLock;
  }

  public static FakeHttpApiHost create(int port, ApiRequestHandler apiRequestHandler)
      throws IOException {
    InetSocketAddress socketAddress = new InetSocketAddress(port);
    HttpServer httpApiHostServer = HttpServer.create(socketAddress, 0);
    ReentrantLock freezeLock = new ReentrantLock();
    httpApiHostServer.createContext(
        REQUEST_ENDPOINT, new ApiHandler(freezeLock, apiRequestHandler));
    httpApiHostServer.start();
    String url =
        String.format(
            "http://%s%s",
            HostAndPort.fromParts(socketAddress.getHostString(), port), REQUEST_ENDPOINT);
    URL httpApiHostUrl = new URL(url);
    return new FakeHttpApiHost(httpApiHostServer, httpApiHostUrl, freezeLock);
  }

  URL getUrl() {
    return httpApiHostUrl;
  }

  public void stop() {
    httpApiHostServer.stop(0);
  }

  void freeze() {
    freezeLock.lock();
  }

  void unfreeze() {
    if (freezeLock.isHeldByCurrentThread()) {
      freezeLock.unlock();
    }
  }

  private static class ApiHandler implements HttpHandler {
    private final Lock freezeLock;
    private final ApiRequestHandler apiRequestHandler;

    ApiHandler(Lock freezeLock, ApiRequestHandler apiRequestHandler) {
      this.freezeLock = freezeLock;
      this.apiRequestHandler = apiRequestHandler;
    }

    @Override
    @SuppressWarnings("LockNotBeforeTry") // this lock/unlock business confuses ErrorProne
    public void handle(HttpExchange exchange) throws IOException {
      // Block if the server is frozen, until it is unfrozen. If it's not frozen then the lock and
      // unlock succeed at once.
      freezeLock.lock();
      freezeLock.unlock();
      try {
        handleOrThrow(exchange);
      } catch (RuntimeException e) {
        e.printStackTrace();
        exchange.sendResponseHeaders(400, 0);
        exchange.getResponseBody().close();
      }
    }

    private void handleOrThrow(HttpExchange exchange) throws IOException {
      if (!exchange.getRequestMethod().equals("POST")) {
        throw new IllegalArgumentException(
            "HTTP method must be POST, not " + exchange.getRequestMethod());
      }
      Headers requestHeaders = exchange.getRequestHeaders();
      String endpoint = requestHeaders.getFirst(RPC_ENDPOINT_HEADER);
      if (!RPC_ENDPOINT_VALUE.equals(endpoint)) {
        throw new IllegalArgumentException(
            RPC_ENDPOINT_HEADER + " should be " + RPC_ENDPOINT_VALUE + ", not " + endpoint);
      }
      String method = requestHeaders.getFirst(RPC_METHOD_HEADER);
      if (!RPC_METHOD_VALUE.equals(method)) {
        throw new IllegalArgumentException(
            RPC_METHOD_HEADER + " should be " + RPC_METHOD_VALUE + ", not " + method);
      }
      String contentType = requestHeaders.getFirst(CONTENT_TYPE_HEADER);
      if (!CONTENT_TYPE_VALUE.equals(contentType)) {
        throw new IllegalArgumentException(
            CONTENT_TYPE_HEADER + " should be " + CONTENT_TYPE_VALUE + ", not " + contentType);
      }
      String deadlineString = requestHeaders.getFirst(DEADLINE_HEADER);
      Double deadline;
      if (deadlineString == null) {
        deadline = null;
      } else {
        deadline = Doubles.tryParse(deadlineString);
      }
      if (deadline == null) {
        throw new IllegalArgumentException(
            "Missing or incorrect deadline header in request: " + deadlineString);
      }
      RemoteApiPb.Request requestPb;
      try (InputStream in = exchange.getRequestBody()) {
        requestPb = RemoteApiPb.Request.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
        if (in.read() >= 0) {
          throw new IllegalArgumentException("Extra junk after request");
        }
      }

      Headers responseHeaders = exchange.getResponseHeaders();
      responseHeaders.put(CONTENT_TYPE_HEADER, ImmutableList.of(CONTENT_TYPE_VALUE));
      RemoteApiPb.Response responsePb = handleRequestInThread(requestPb, deadline);

      if (responsePb.getResponse().equals(BAD_RESPONSE)) {
        // Add a bad TE header to make the response a bad message.
        responseHeaders.put("Transfer-Encoding", ImmutableList.of("bad,chunked,badly"));
      }

      byte[] responseBytes = responsePb.toByteArray();
      exchange.sendResponseHeaders(200, responseBytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(responseBytes);
      }
    }

    // Simulates deadline handling by running the request in a separate thread and waiting for
    // the result with a deadline.
    private RemoteApiPb.Response handleRequestInThread(
        final RemoteApiPb.Request requestPb, double deadline) {
      final BlockingQueue<RemoteApiPb.Response> responseQueue = new ArrayBlockingQueue<>(1);
      Runnable runnable =
          () -> {
            RemoteApiPb.Response response = apiRequestHandler.handle(requestPb);
            responseQueue.add(response);
          };
      Thread thread = new Thread(runnable);
      thread.start();
      long deadlineMs = (long) (deadline * 1000);
      RemoteApiPb.Response response;
      try {
        response = responseQueue.poll(deadlineMs, MILLISECONDS);
      } catch (InterruptedException e) {
        response = null;
      }
      if (response == null) {
        thread.interrupt();
        return timeoutResponse(deadline);
      } else {
        return response;
      }
    }

    private RemoteApiPb.Response timeoutResponse(double deadline) {
      RemoteApiPb.RpcError rpcError =
          RemoteApiPb.RpcError.newBuilder()
              .setCode(RemoteApiPb.RpcError.ErrorCode.DEADLINE_EXCEEDED_VALUE)
              .setDetail("Deadline of " + deadline + "s was exceeded")
              .build();
      return RemoteApiPb.Response.newBuilder().setRpcError(rpcError).build();
    }
  }
}
