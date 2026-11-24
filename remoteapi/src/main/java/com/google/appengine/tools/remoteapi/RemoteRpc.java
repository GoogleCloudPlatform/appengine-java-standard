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

import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
// <internal22>
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An RPC transport that sends protocol buffers over HTTP. */
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
   * Makes an RPC call using the injected RemoteRpc instance. Logs how long it took and any
   * exceptions.
   *
   * @throws RemoteApiException if the RPC fails.
   * @throws RuntimeException if the server threw a Java runtime exception
   */
  byte[] call(String serviceName, String methodName, String logSuffix, byte[] request) {
    logger.log(
        Level.FINE,
        "remote API call: {0}.{1}{2}",
        new Object[] {serviceName, methodName, logSuffix});

    long startTime = System.currentTimeMillis();
    try {

      RemoteApiPb.Request requestProto = makeRequest(serviceName, methodName, request);
      RemoteApiPb.Response responseProto = callImpl(requestProto);

      if (responseProto.hasJavaException()) {
        logger.fine("remote API call: failed due to a server-side Java exception");
        Object contents =
            parseJavaException(
                responseProto, requestProto.getServiceName(), requestProto.getMethod());
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
          // TODO(b/68190110) this eats the client-side stack trace, which is
          // usually more important for debugging. We should throw a new exception
          // of the same type for any subtypes of RuntimeException we care about.
          throw (RuntimeException) contents;
        } else if (contents instanceof Throwable) {
          throw new RemoteApiException(
              "response was an exception",
              requestProto.getServiceName(),
              requestProto.getMethod(),
              (Throwable) contents);
        } else {
          throw new RemoteApiException(
              "unexpected response type: " + (contents == null ? "null" : contents.getClass()),
              requestProto.getServiceName(),
              requestProto.getMethod(),
              null);
        }
      } else if (responseProto.hasException()) {
        String pickle = responseProto.getException().toString();
        logger.log(
            Level.FINE,
            "remote API call: failed due to a server-side Python exception:\n{0}",
            pickle);
        throw new RemoteApiException(
            "response was a python exception:\n" + pickle,
            requestProto.getServiceName(),
            requestProto.getMethod(),
            null);
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

    AppEngineClient.Response httpResponse;
    try {
      String path = client.getRemoteApiPath();
      httpResponse = client.post(path, "application/octet-stream", requestBytes);
    } catch (IOException e) {
      throw makeException("I/O error", e, requestProto);
    }

    if (httpResponse.getStatusCode() != 200) {
      throw makeException(
          "unexpected HTTP response: " + httpResponse.getStatusCode(), null, requestProto);
    }

    // parse the response
    RemoteApiPb.Response.Builder parsedResponse = RemoteApiPb.Response.newBuilder();
    boolean parsed = true;
    try {
      parsedResponse.mergeFrom(httpResponse.getBodyAsBytes(), ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      parsed = false;
    }
    if (!parsed || !parsedResponse.isInitialized()) {
      throw makeException("Could not parse response bytes", null, requestProto);
    }
    return parsedResponse.build();
  }

  void resetRpcCount() {
    rpcCount = 0;
  }

  int getRpcCount() {
    return rpcCount;
  }

  private static final AtomicLong requestId = new AtomicLong();

  private static RemoteApiPb.Request makeRequest(
      String packageName, String methodName, byte[] payload) {
    return RemoteApiPb.Request.newBuilder()
        .setServiceName(packageName)
        .setMethod(methodName)
        .setRequest(ByteString.copyFrom(payload))
        .setRequestId(Long.toString(requestId.incrementAndGet()))
        .build();
  }

  // <internal23>
  private static Object parseJavaException(
      RemoteApiPb.Response parsedResponse, String packageName, String methodName) {
    try (InputStream ins = parsedResponse.getJavaException().newInput();
        ObjectInputStream in = new SafeExceptionObjectInputStream(ins)) {
      return in.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RemoteApiException(
          "remote API call: " + "can't deserialize server-side exception",
          packageName,
          methodName,
          e);
    }
  }

  // <internal23>
  private static final class SafeExceptionObjectInputStream extends ObjectInputStream {
    private static final long MAX_DEPTH = 20;
    private static final long MAX_REFERENCES = 2000;
    private static final long MAX_ARRAY_LENGTH = 1000;
    private static final long MAX_BYTES = 65536;

    // Internal non-Throwable classes required to deserialize standard java.lang.Throwable fields
    // (stackTrace and suppressedExceptions).
    private static final ImmutableSet<String> ALLOWED_NON_THROWABLE_CLASSES =
        ImmutableSet.of(
            "java.lang.StackTraceElement",
            "[Ljava.lang.StackTraceElement;",
            "java.util.ArrayList",
            "java.util.Collections$EmptyList",
            "java.util.Collections$SingletonList",
            "java.util.Collections$UnmodifiableList",
            "java.util.Collections$UnmodifiableRandomAccessList",
            "java.util.Collections$UnmodifiableCollection",
            "java.util.ImmutableCollections$List12",
            "java.util.ImmutableCollections$ListN",
            "java.util.ImmutableCollections$CollSer");

    // Package prefixes from which Throwable subclasses are allowed to be deserialized.
    // The protobuf prefixes cover InvalidProtocolBufferException and friends, which the
    // remote_api servlet can serialize when it fails to parse a request.
    private static final ImmutableSet<String> ALLOWED_EXCEPTION_PACKAGE_PREFIXES =
        ImmutableSet.of(
            "java.lang.",
            "java.io.",
            "java.util.",
            "java.net.",
            "java.security.",
            "com.google.apphosting.api.",
            "com.google.apphosting.utils.remoteapi.",
            "com.google.appengine.api.",
            "com.google.appengine.tools.remoteapi.",
            "com.google.common.base.",
            "com.google.appengine.repackaged.com.google.common.base.",
            "com.google.protobuf.",
            "com.google.appengine.repackaged.com.google.protobuf.");

    SafeExceptionObjectInputStream(InputStream in) throws IOException {
      super(in);
      setObjectInputFilter(SafeExceptionObjectInputStream::filterCheck);
    }

    private static ObjectInputFilter.Status filterCheck(ObjectInputFilter.FilterInfo info) {
      if (info.depth() > MAX_DEPTH
          || info.references() > MAX_REFERENCES
          || info.arrayLength() > MAX_ARRAY_LENGTH
          || info.streamBytes() > MAX_BYTES) {
        return ObjectInputFilter.Status.REJECTED;
      }
      Class<?> serialClass = info.serialClass();
      if (serialClass != null
          && info.depth() == 1
          && !Throwable.class.isAssignableFrom(serialClass)) {
        return ObjectInputFilter.Status.REJECTED;
      }
      return ObjectInputFilter.Status.UNDECIDED;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
        throws IOException, ClassNotFoundException {
      String name = desc.getName();
      if (!isAllowedClassName(name)) {
        throw new InvalidClassException(name, "Unauthorized deserialization attempt");
      }
      Class<?> clazz = super.resolveClass(desc);
      if (ALLOWED_NON_THROWABLE_CLASSES.contains(name)) {
        return clazz;
      }
      if (!Throwable.class.isAssignableFrom(clazz)) {
        throw new InvalidClassException(
            name, "Non-Throwable class is not allowed in remote exception deserialization");
      }
      if (Externalizable.class.isAssignableFrom(clazz)) {
        throw new InvalidClassException(
            name, "Externalizable class is not allowed in remote exception deserialization");
      }
      return clazz;
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
      throw new InvalidClassException(
          "Proxy classes are not allowed in remote exception deserialization");
    }

    private static boolean isAllowedClassName(String name) {
      if (ALLOWED_NON_THROWABLE_CLASSES.contains(name)) {
        return true;
      }
      for (String prefix : ALLOWED_EXCEPTION_PACKAGE_PREFIXES) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    }
  }

  private static RemoteApiException makeException(
      String message, Throwable cause, RemoteApiPb.Request request) {
    logger.log(Level.FINE, "remote API call: {0}", message);
    return new RemoteApiException(
        "remote API call: " + message, request.getServiceName(), request.getMethod(), cause);
  }

  RemoteApiClient getClient() {
    return client;
  }
}
