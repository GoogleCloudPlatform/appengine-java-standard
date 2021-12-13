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

package com.google.appengine.tools.remoteapi;

import com.google.apphosting.utils.remoteapi.RemoteApiPb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An RPC transport that sends protocol buffers over HTTP.
 */
class RemoteRpc {
  private static final Logger logger = Logger.getLogger(RemoteRpc.class.getName());

  private final RemoteApiClient client;
  private int rpcCount = 0;

  // NOTE: Do not remove this seemingly unnecessary constructor.
  // See the long note in InternalRemoteApiInstaller for more info on why this
  // is required.
  RemoteRpc(AppEngineClient client) {
    this.client = client;
  }

  RemoteRpc(RemoteApiClient client) {
    this.client = client;
  }

  /**
   * Makes an RPC call using the injected RemoteRpc instance. Logs how long it took and
   * any exceptions.
   * @throws RemoteApiException if the RPC fails.
   * @throws RuntimeException if the server threw a Java runtime exception
   */
  byte[] call(String serviceName, String methodName, String logSuffix, byte[] request) {
    logger.log(Level.FINE, "remote API call: {0}.{1}{2}",
        new Object[] {serviceName, methodName, logSuffix});

    long startTime = System.currentTimeMillis();
    try {

      RemoteApiPb.Request requestProto = makeRequest(serviceName, methodName, request);
      RemoteApiPb.Response responseProto = callImpl(requestProto);

      if (responseProto.hasJavaException()) {
        logger.fine("remote API call: failed due to a server-side Java exception");
        Object contents = parseJavaException(responseProto,
            requestProto.getServiceName(), requestProto.getMethod());
        if (contents instanceof ConcurrentModificationException) {
          ConcurrentModificationException serverSide = (ConcurrentModificationException) contents;
          ConcurrentModificationException clientSide =
              new ConcurrentModificationException(serverSide.getMessage());
          clientSide.initCause(serverSide);
          throw clientSide;
        } else if (contents instanceof IllegalArgumentException) {
          IllegalArgumentException serverSide = (IllegalArgumentException) contents;
          throw new IllegalArgumentException(serverSide.getMessage(), serverSide);
        } else if (contents instanceof RuntimeException) {
            // TODO this eats the client-side stack trace, which is
            // usually more important for debugging. We should throw a new exception
            // of the same type for any subtypes of RuntimeException we care about.
            throw (RuntimeException) contents;
        } else if (contents instanceof Throwable) {
          throw new RemoteApiException("response was an exception",
              requestProto.getServiceName(), requestProto.getMethod(), (Throwable) contents);
        } else {
          throw new RemoteApiException("unexpected response type: " + contents.getClass(),
              requestProto.getServiceName(), requestProto.getMethod(), null);
        }
      } else if (responseProto.hasException()) {
        String pickle = responseProto.getException();

        logger.log(Level.FINE,
            "remote API call: failed due to a server-side Python exception:\n{0}", pickle);
        throw new RemoteApiException("response was a python exception:\n" + pickle,
            requestProto.getServiceName(), requestProto.getMethod(), null);
      }

      return responseProto.getResponseAsBytes();

    } finally {
      long elapsedTime = System.currentTimeMillis() - startTime;
      logger.log(Level.FINE, "remote API call: took {0} ms", elapsedTime);
    }
  }

  RemoteApiPb.Response callImpl(RemoteApiPb.Request requestProto) {
    rpcCount++;

    byte[] requestBytes = requestProto.toByteArray();

    AppEngineClient.Response httpResponse;
    try {
      String path = client.getRemoteApiPath();
      httpResponse = client.post(path, "application/octet-stream", requestBytes);
    } catch (IOException e) {
      throw makeException("I/O error", e, requestProto);
    }

    if (httpResponse.getStatusCode() != 200) {
      throw makeException("unexpected HTTP response: " + httpResponse.getStatusCode(),
          null, requestProto);
    }

    // parse the response
    RemoteApiPb.Response parsedResponse = new RemoteApiPb.Response();
    boolean parsed = parsedResponse.parseFrom(httpResponse.getBodyAsBytes());
    if (!parsed || !parsedResponse.isInitialized()) {
      throw makeException("Could not parse response bytes", null, requestProto);
    }
    return parsedResponse;
  }

  void resetRpcCount() {
    rpcCount = 0;
  }

  int getRpcCount() {
    return rpcCount;
  }

  private static final AtomicLong requestId = new AtomicLong();

  private static RemoteApiPb.Request makeRequest(String packageName, String methodName,
      byte[] payload) {
    RemoteApiPb.Request result = new RemoteApiPb.Request();
    result.setServiceName(packageName);
    result.setMethod(methodName);
    result.setRequestAsBytes(payload);
    result.setRequestId(Long.toString(requestId.incrementAndGet()));

    return result;
  }

  
  private static Object parseJavaException(
      RemoteApiPb.Response parsedResponse, String packageName, String methodName) {
    try {
      InputStream ins = new ByteArrayInputStream(parsedResponse.getJavaExceptionAsBytes());
      ObjectInputStream in = new ObjectInputStream(ins);
      return in.readObject();
    } catch (IOException e) {
      throw new RemoteApiException(
          "remote API call: " + "can't deserialize server-side exception", packageName, methodName,
          e);
    } catch (ClassNotFoundException e) {
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

  RemoteApiClient getClient() {
    return client;
  }
}
