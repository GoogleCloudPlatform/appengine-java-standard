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

package com.google.appengine.tools.development.jakarta;

import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.ApiUtils;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.CallNotFoundException;
import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.apphosting.base.protos.api_bytes.RemoteApiPb.RpcError;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.primitives.Doubles;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling POST requests to serve App Engine Standard API calls implemented by the API stub
 * implementations used by the dev app server. This can be used in a local dev environment to
 * emulate App Engine APIs, or in a test environment. The protocol buffer used is the same as the
 * App Engine remote APIs documented at
 * https://cloud.google.com/appengine/docs/standard/java/tools/remoteapi and the one used from the
 * Java clones in production to make API calls.
 */
public class ApiServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(ApiServlet.class.getName());

  private static final String RPC_ENDPOINT_HEADER = "X-Google-RPC-Service-Endpoint";
  private static final String RPC_ENDPOINT_VALUE = "app-engine-apis";
  private static final String RPC_METHOD_HEADER = "X-Google-RPC-Service-Method";
  private static final String RPC_METHOD_VALUE = "/VMRemoteAPI.CallRemoteAPI";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String CONTENT_TYPE_VALUE = "application/octet-stream";
  private static final String DEADLINE_HEADER = "X-Google-RPC-Service-Deadline";
  private static final String RUNTIME_PORT_CONFIG = "java_runtime_port";
  private static final String RUNTIME_HOST_CONFIG = "java_runtime_host";
  private static final String EXECUTOR_POOL_SIZE = "executor_pool_size";

  private final Map<String, Method> methodCache = new ConcurrentHashMap<>();
  private ApiProxyLocal apiProxyLocal;
  private int serverPort;
  private String serverHost;
  private ExecutorService executor;
  private static final int EXECUTOR_THREAD_POOL_DEFAULT_SIZE = 10;

  /**
   * Configure the APIServlet with 2 servlet init paramerters:
   *
   * <pre>
   * java_runtime_port:  the local port of the java clone. This is needed for the taskqueue APIs to
   * be able to post callback to the clone.
   * java_runtime_host:  the hostname of the java clone. (default to localhost).
   *    * executor_pool_size: size of the threadpool handling API calls. Default is 10.
   * </pre>
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    if (config.getInitParameter(RUNTIME_PORT_CONFIG) == null) {
      throw new NumberFormatException("Missing " + RUNTIME_PORT_CONFIG + " init parameter.");
    }
    serverPort = Integer.parseInt(config.getInitParameter(RUNTIME_PORT_CONFIG));
    if (config.getInitParameter(RUNTIME_HOST_CONFIG) == null) {
      serverHost = "localhost";
    } else {
      serverHost = config.getInitParameter(RUNTIME_HOST_CONFIG);
    }
    apiProxyLocal = new ApiProxyLocalFactory().create(new LocalEnv(serverHost, serverPort));
    // True to put the datastore into "memory-only" mode.
    apiProxyLocal.setProperty("datastore.no_storage", "true");
    String executorSize = config.getInitParameter(EXECUTOR_POOL_SIZE);
    int poolSize =
        (executorSize == null) ? EXECUTOR_THREAD_POOL_DEFAULT_SIZE : Integer.parseInt(executorSize);
    executor = Executors.newFixedThreadPool(poolSize);
  }

  @Override
  public void destroy() {
    executor.shutdown();
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Double deadline = validateHeaders(request);
    try (InputStream in = request.getInputStream()) {
      RemoteApiPb.Request requestPb =
          RemoteApiPb.Request.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
      if (in.read() >= 0) {
        throw new IllegalArgumentException("Extra data after request");
      }
      response.addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE_VALUE);
      RemoteApiPb.Response responsePb = handleRequestInThread(apiProxyLocal, requestPb, deadline);
      try (OutputStream out = response.getOutputStream()) {
        out.write(responsePb.toByteArray());
      }
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "bad request:", e);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  // Checks mandatory headers, and return the expected deadline (in seconds) for the request.
  @VisibleForTesting
  Double validateHeaders(HttpServletRequest request) {
    String endpoint = request.getHeader(RPC_ENDPOINT_HEADER);
    if (!RPC_ENDPOINT_VALUE.equals(endpoint)) {
      throw new IllegalArgumentException(
          RPC_ENDPOINT_HEADER + " should be " + RPC_ENDPOINT_VALUE + ", not " + endpoint + ".");
    }
    String method = request.getHeader(RPC_METHOD_HEADER);
    if (!RPC_METHOD_VALUE.equals(method)) {
      throw new IllegalArgumentException(
          RPC_METHOD_HEADER + " should be " + RPC_METHOD_VALUE + ", not " + method + ".");
    }
    String contentType = request.getHeader(CONTENT_TYPE_HEADER);
    if (!CONTENT_TYPE_VALUE.equals(contentType)) {
      throw new IllegalArgumentException(
          CONTENT_TYPE_HEADER + " should be " + CONTENT_TYPE_VALUE + ", not " + contentType + ".");
    }
    String deadlineString = request.getHeader(DEADLINE_HEADER);
    Double deadline = (deadlineString == null) ? null : Doubles.tryParse(deadlineString);

    if (deadline == null) {
      throw new IllegalArgumentException(
          "Missing or incorrect deadline header in request: " + deadlineString + ".");
    }
    return deadline;
  }

  // Simulates deadline handling by running the request in a separate thread and waiting for
  // the result with a deadline.
  private RemoteApiPb.Response handleRequestInThread(
      final ApiProxyLocal apiProxy, final RemoteApiPb.Request requestPb, double deadline) {

    Callable<RemoteApiPb.Response> task =
        new Callable<RemoteApiPb.Response>() {
          @Override
          public RemoteApiPb.Response call() {
            return handle(apiProxy, requestPb);
          }
        };
    Future<RemoteApiPb.Response> future = executor.submit(task);
    try {
      return future.get((long) (deadline * 1000), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      return exceptionResponse(e);
    } catch (InterruptedException | TimeoutException e) {
      future.cancel(/* mayInterruptIfRunning= */ true);
      return timeoutResponse(deadline);
    }
  }

  /**
   * Invokes an API call using the Java implementations.
   *
   * @param apiProxy the local ApiProxy environment
   * @param packageName the name of the API service, eg datastore_v3
   * @param methodName the name of the API method, eg Query
   * @param requestBytes the serialized proto, eg DatastoreV3Pb.Query
   * @return the serialized API response
   */
  @VisibleForTesting
  byte[] invokeApiMethodJava(
      ApiProxyLocal apiProxy, String packageName, String methodName, byte[] requestBytes)
      throws IllegalAccessException, InstantiationException, InvocationTargetException,
          NoSuchMethodException {

    LocalRpcService service = apiProxy.getService(packageName);
    if (service == null) {
      throw new CallNotFoundException(packageName, methodName);
    }
    Method method = getDispatchMethod(service, packageName, methodName);
    LocalRpcService.Status status = new LocalRpcService.Status();
    // For a method like public QueryResult runQuery(Status status, Query query) {...}
    // return the Query class.
    Class<?> requestClass = method.getParameterTypes()[1];
    Object request = ApiUtils.convertBytesToPb(requestBytes, requestClass);

    return ApiUtils.convertPbToBytes(method.invoke(service, status, request));
  }

  @VisibleForTesting
  Method getDispatchMethod(LocalRpcService service, String packageName, String methodName) {
    // e.g. RunQuery --> runQuery
    String dispatchName = Ascii.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
    // e.g. datastore_v3.runQuery
    String methodId = packageName + "." + dispatchName;
    Method method = methodCache.get(methodId);
    if (method != null) {
      return method;
    }
    for (Method candidate : service.getClass().getMethods()) {
      if (dispatchName.equals(candidate.getName())) {
        methodCache.put(methodId, candidate);
        return candidate;
      }
    }
    throw new CallNotFoundException(packageName, methodName);
  }

  @VisibleForTesting
  RemoteApiPb.Response handle(ApiProxyLocal apiProxy, RemoteApiPb.Request request) {
    byte[] resp;
    try {
      resp =
          invokeApiMethodJava(
              apiProxy,
              request.getServiceName(),
              request.getMethod(),
              request.getRequest().toByteArray());
    } catch (InvocationTargetException ex) {
      logger.log(
          Level.INFO,
          "Exception calling service"
          + request.getServiceName()
          + " and method: "
          + request.getMethod(), ex);
      throw new ApiProxyException(
          "API invocation error for service: "
              + request.getServiceName()
              + " and method: "
              + request.getMethod(),
          ex.getCause());
    } catch (ReflectiveOperationException | RuntimeException | Error ex) {
      logger.log(
          Level.INFO,
          "Exception calling service"
          + request.getServiceName()
          + " and method: "
          + request.getMethod(), ex);
      throw new CallNotFoundException(request.getServiceName(), request.getMethod());
    }
    return RemoteApiPb.Response.newBuilder().setResponse(ByteString.copyFrom(resp)).build();
  }

  private RemoteApiPb.Response timeoutResponse(double deadline) {
    return RemoteApiPb.Response.newBuilder()
        .setRpcError(
            RemoteApiPb.RpcError.newBuilder()
                .setCode(RemoteApiPb.RpcError.ErrorCode.DEADLINE_EXCEEDED.getNumber())
                .setDetail("Deadline of " + deadline + "s was exceeded"))
        .build();
  }

  private RemoteApiPb.Response exceptionResponse(ExecutionException exception) {
    RpcError rpcError =
        RemoteApiPb.RpcError.newBuilder()
            .setCode(RemoteApiPb.RpcError.ErrorCode.BAD_REQUEST.getNumber())
            .setDetail("Execution exception " + exception.getMessage())
            .build();
    RemoteApiPb.Response.Builder response = RemoteApiPb.Response.newBuilder();
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (ObjectOutput out = new ObjectOutputStream(byteStream)) {
      out.writeObject(exception);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Cannot serialize the exception: ", e);
    }
    byte[] serializedException = byteStream.toByteArray();
    response.setJavaException(ByteString.copyFrom(serializedException));

    response.setRpcError(rpcError);
    return response.build();
  }

  private static class LocalEnv implements LocalServerEnvironment {
    private final String javaRuntimeHost;
    private final int javaRuntimePort;

    LocalEnv(String javaRuntimeHost, int javaRuntimePort) {
      this.javaRuntimeHost = javaRuntimeHost;
      this.javaRuntimePort = javaRuntimePort;
    }

    @Override
    public File getAppDir() {
      return new File(".");
    }

    @Override
    public String getAddress() {
      return new InetSocketAddress(javaRuntimePort).getHostString();
    }

    @Override
    public String getHostName() {
      return javaRuntimeHost;
    }

    @Override
    public int getPort() {
      return javaRuntimePort;
    }

    @Override
    public void waitForServerToStart() throws InterruptedException {}

    @Override
    public boolean simulateProductionLatencies() {
      return false;
    }

    @Override
    public boolean enforceApiDeadlines() {
      // Not used by this servlet. Instead, the value of DEADLINE_HEADER is used for deadlines.
      return false;
    }
  }
}
