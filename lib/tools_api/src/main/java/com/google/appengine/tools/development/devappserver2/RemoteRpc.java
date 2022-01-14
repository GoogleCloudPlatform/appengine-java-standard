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

package com.google.appengine.tools.development.devappserver2;

import com.google.appengine.tools.remoteapi.RemoteApiException;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
// <internal22>
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * An RPC transport that sends protocol buffers over HTTP. This class is not thread-safe, in that
 * two different threads cannot use it to send requests at the same time.
 */
class RemoteRpc {
  private static final Logger logger = Logger.getLogger(RemoteRpc.class.getName());

  private final RemoteApiOptions options;
  private final HttpClient httpClient;  // This is why the class isn't thread-safe.

  private int rpcCount = 0;

  RemoteRpc(RemoteApiOptions options) {
    this.options = options;
    this.httpClient = HttpClientBuilder.create().disableRedirectHandling().build();
  }

  /**
   * Makes an RPC call using the injected RemoteRpc instance. Logs how long it took and
   * any exceptions.
   * @throws RemoteApiException if the RPC fails.
   * @throws RuntimeException if the server threw a Java runtime exception
   */
  byte[] call(String serviceName, String methodName, String requestId, byte[] request) {
    logger.log(Level.FINE, "remote API call: {0}.{1}", new Object[] {serviceName, methodName});

    long startTime = System.currentTimeMillis();
    try {

      RemoteApiPb.Request requestProto = makeRequest(serviceName, methodName, requestId, request);
      RemoteApiPb.Response responseProto = callImpl(requestProto);

      if (responseProto.hasJavaException()) {
        // We don't expect this at all from the devappserver2 API server, so handle it minimally.
        logger.fine("remote API call: failed due to a server-side Java exception");
        Throwable exception = parseJavaException(responseProto,
            requestProto.getServiceName(), requestProto.getMethod());
        throw new RemoteApiException("response was an exception",
            requestProto.getServiceName(), requestProto.getMethod(), exception);
      } else if (responseProto.hasException()) {
        String pickle = responseProto.getException().toString();

        logger.log(Level.FINE,
            "remote API call: failed due to a server-side Python exception:\n{0}", pickle);
        throw new RemoteApiException("response was a python exception:\n" + pickle,
            requestProto.getServiceName(), requestProto.getMethod(), null);
      }

      return responseProto.getResponse().toByteArray();

    } finally {
      long elapsedTime = System.currentTimeMillis() - startTime;
      logger.log(Level.FINE, "remote API call: took {0} ms", elapsedTime);
    }
  }

  RemoteApiPb.Response callImpl(RemoteApiPb.Request requestProto) {
    rpcCount++;

    byte[] requestBytes = requestProto.toByteArray();
    String url = "http://" + options.getHostname() + ":" + options.getPort()
        + options.getRemoteApiPath();
    HttpPost post = new HttpPost(url);
    post.addHeader("Host", options.getHostname());
    post.addHeader("X-appcfg-api-version", "1");
    post.addHeader("Content-Type", "application/octet-stream");
    post.setEntity(new ByteArrayEntity(requestBytes));
    try {
      HttpResponse response = httpClient.execute(post);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw makeException(
            "unexpected HTTP response: " + response.getStatusLine(), null, requestProto);
      }
      int max = options.getMaxHttpResponseSize();
      byte[] buf = new byte[65536];
      byte[] body;
      try (InputStream in = response.getEntity().getContent();
          ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
        int n;
        while (bout.size() < max
            && (n = in.read(buf, 0, Math.min(buf.length, max - bout.size()))) > 0) {
          bout.write(buf, 0, n);
        }
        body = bout.toByteArray();
      }
      return RemoteApiPb.Response.parseFrom(body, ExtensionRegistry.getEmptyRegistry());

    } catch (IOException e) {
      throw makeException("I/O error", e, requestProto);
    }
  }

  void resetRpcCount() {
    rpcCount = 0;
  }

  int getRpcCount() {
    return rpcCount;
  }

  private static RemoteApiPb.Request makeRequest(
      String packageName, String methodName, String requestId, byte[] payload) {
    return RemoteApiPb.Request.newBuilder()
        .setServiceName(packageName)
        .setMethod(methodName)
        .setRequest(ByteString.copyFrom(payload))
        .setRequestId(requestId)
        .build();
  }

  // <internal23>
  private static Throwable parseJavaException(
      RemoteApiPb.Response parsedResponse, String packageName, String methodName) {
    try {
      InputStream ins = parsedResponse.getJavaException().newInput();
      ObjectInputStream in = new ObjectInputStream(ins);
      return (Throwable) in.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RemoteApiException(
          "remote API call: " + "can't deserialize server-side exception", packageName, methodName,
          e);
    }
  }

  private static RemoteApiException makeException(String message, Throwable cause,
      RemoteApiPb.Request request) {
    logger.log(Level.FINE, "remote API call: {0}", message);
    return new RemoteApiException("remote API call: " + message,
        request.getServiceName(), request.getMethod(), cause);
  }
}
