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

package com.google.appengine.api.modules;

import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsResponse;
import com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleResponse;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleResponse;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ForwardingFuture;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Implementation of {@link ModulesService}.
 *
 */
class ModulesServiceImpl implements ModulesService {
  private static final Logger logger = Logger.getLogger(ModulesServiceImpl.class.getName());
  private static final String STARTING_STARTED_MESSAGE =
      "Attempted to start an already started module version, continuing";
  private static final String STOPPING_STOPPED_MESSAGE =
      "Attempted to stop an already stopped module version, continuing";

  // @VisibleForTesting
  static final String PACKAGE = "modules";

  /**
   * Environment attribute key where the instance id is stored.
   *
   * @see ModulesService#getCurrentInstanceId()
   */
  private static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";

  @Override
  public String getCurrentModule() {
    return getCurrentEnvironmentOrThrow().getModuleId();
  }

  @Override
  public String getCurrentVersion() {
    Environment env = getCurrentEnvironmentOrThrow();
    return Splitter.on('.').split(env.getVersionId()).iterator().next();
  }

  private static Map<String, Object> getThreadLocalAttributes() {
    return getCurrentEnvironmentOrThrow().getAttributes();
  }

  @Override
  public String getCurrentInstanceId() {
    Map<String, Object> env = getThreadLocalAttributes();
    // We assume that an instance id is not set for automatically-scaled instances.
    if (!env.containsKey(INSTANCE_ID_ENV_ATTRIBUTE)) {
      throw new ModulesException("Instance id unavailable");
    }
    String instanceId = (String) getThreadLocalAttributes().get(INSTANCE_ID_ENV_ATTRIBUTE);
    if (instanceId == null) {
      throw new ModulesException("Instance id unavailable");
    }
    return instanceId;
  }

  /**
   * Returns the result from the provided future in the form suitable for a synchronous call.
   * <p>
   * If {@link Future#get} throws an {@link ExecutionException}
   * <ol>
   * <li> if {@link ExecutionException#getCause()} is an unchecked exception
   * including {@link ModulesException} this throws the unchecked exception.
   * </li>
   * <li> otherwise {@link UndeclaredThrowableException} with the checked
   * {@link ExecutionException#getCause()} as the cause.
   * </li>
   * </ol>
   *
   * If {@link Future#get} throws an {@link InterruptedException} this throws a
   * {@link ModulesException} with the cause set to the InterruptedException.
   */
  private <V> V getAsyncResult(Future<V> asyncResult) {
    try {
      return asyncResult.get();
    } catch (InterruptedException ie) {
      throw new ModulesException("Unexpected failure", ie);
    } catch (ExecutionException ee) {
      if (ee.getCause() instanceof RuntimeException) {
        // Includes ModulesException.
        throw (RuntimeException) ee.getCause();
      } else if (ee.getCause() instanceof Error) {
        throw (Error) ee.getCause();
      } else {
        throw new UndeclaredThrowableException(ee.getCause());
      }
    }
  }

  @Override
  public Set<String> getModules() {
    return getAsyncResult(getModulesAsync());
  }

  private Future<Set<String>> getModulesAsync() {
    GetModulesRequest.Builder requestBuilder = GetModulesRequest.newBuilder();
    Future<Set<String>> result =
        new ModulesServiceFutureWrapper<Set<String>>("GetModules", requestBuilder) {

      @Override
      protected Set<String> wrap(byte[] key) throws InvalidProtocolBufferException {
        GetModulesResponse.Builder responseBuilder = GetModulesResponse.newBuilder();
        responseBuilder.mergeFrom(key);
        return Sets.newHashSet(responseBuilder.getModuleList());
      }
    };
    return result;
  }

  @Override
  public Set<String> getVersions(String module) {
    return getAsyncResult(getVersionsAsync(module));
  }

  private Future<Set<String>> getVersionsAsync(String module) {
    GetVersionsRequest.Builder requestBuilder = GetVersionsRequest.newBuilder();
    if (module != null) {
      requestBuilder.setModule(module);
    }
    Future<Set<String>> result =
        new ModulesServiceFutureWrapper<Set<String>>("GetVersions", requestBuilder) {

      @Override
      protected Set<String> wrap(byte[] key) throws InvalidProtocolBufferException {
        GetVersionsResponse.Builder responseBuilder = GetVersionsResponse.newBuilder();
        responseBuilder.mergeFrom(key);
        return Sets.newHashSet(responseBuilder.getVersionList());
      }
    };
    return result;
  }

  @Override
  public String getDefaultVersion(String module) {
    return getAsyncResult(getDefaultVersionAsync(module));
  }

  private Future<String> getDefaultVersionAsync(String module) {
    GetDefaultVersionRequest.Builder requestBuilder = GetDefaultVersionRequest.newBuilder();
    if (module != null) {
      requestBuilder.setModule(module);
    }
    Future<String> result =
        new ModulesServiceFutureWrapper<String>("GetDefaultVersion", requestBuilder) {

      @Override
      protected String wrap(byte[] key) throws InvalidProtocolBufferException {
        GetDefaultVersionResponse.Builder responseBuilder = GetDefaultVersionResponse.newBuilder();
        responseBuilder.mergeFrom(key);
        return responseBuilder.getVersion();
      }
    };
    return result;
  }

  @Override
  public int getNumInstances(String module, String version) {
    return getAsyncResult(getNumInstancesAsync(module, version));
  }

  private Future<Integer> getNumInstancesAsync(String module, String version) {
    GetNumInstancesRequest.Builder requestBuilder = GetNumInstancesRequest.newBuilder();
    if (module != null) {
      requestBuilder.setModule(module);
    }
    if (version != null) {
      requestBuilder.setVersion(version);
    }
    Future<Integer> result =
        new ModulesServiceFutureWrapper<Integer>("GetNumInstances", requestBuilder) {

      @Override
      protected Integer wrap(byte[] key) throws InvalidProtocolBufferException {
        GetNumInstancesResponse.Builder responseBuilder = GetNumInstancesResponse.newBuilder();
        responseBuilder.mergeFrom(key);
        if (responseBuilder.getInstances() < 0
            || responseBuilder.getInstances() > Integer.MAX_VALUE) {
          throw new IllegalStateException("Invalid instances value: "
            + responseBuilder.getInstances());
        }
        return (int) responseBuilder.getInstances();
      }
    };
    return result;
  }

  @Override
  public void setNumInstances(String module, String version, long instances) {
    getAsyncResult(setNumInstancesAsync(module, version, instances));
  }

  @Override
  public Future<Void> setNumInstancesAsync(String module, String version, long instances) {
    SetNumInstancesRequest.Builder requestBuilder = SetNumInstancesRequest.newBuilder();
    if (module != null) {
      requestBuilder.setModule(module);
    }
    if (version != null) {
      requestBuilder.setVersion(version);
    }
    requestBuilder.setInstances(instances);
    Future<Void> result = new ModulesServiceFutureWrapper<Void>("SetNumInstances", requestBuilder) {

      @Override
      protected Void wrap(byte[] key) throws InvalidProtocolBufferException {
        SetNumInstancesResponse unused = SetNumInstancesResponse.parseFrom(key);
        return null;
      }
    };
    return result;
  }

  @Override
  public void startVersion(String module, String version) {
    getAsyncResult(startVersionAsync(module, version));
  }

  @Override
  public Future<Void> startVersionAsync(String module, String version) {
    StartModuleRequest.Builder requestBuilder = StartModuleRequest.newBuilder();
    requestBuilder.setModule(module);
    requestBuilder.setVersion(version);
    Future<Void> modulesServiceFuture =
        new ModulesServiceFutureWrapper<Void>("StartModule", requestBuilder) {

      @Override
      protected Void wrap(byte[] key) throws InvalidProtocolBufferException {
        StartModuleResponse unused = StartModuleResponse.parseFrom(key);
        return null;
      }
    };
    return new IgnoreUnexpectedStateExceptionFuture(modulesServiceFuture,
        STARTING_STARTED_MESSAGE);
  }

  @Override
  public void stopVersion(String module, String version) {
    getAsyncResult(stopVersionAsync(module, version));
  }

  @Override
  public Future<Void> stopVersionAsync(String module, String version) {
    StopModuleRequest.Builder requestBuilder = StopModuleRequest.newBuilder();
    if (module != null) {
      requestBuilder.setModule(module);
    }
    if (version != null) {
      requestBuilder.setVersion(version);
    }
    Future<Void> modulesServiceFuture =
        new ModulesServiceFutureWrapper<Void>("StopModule", requestBuilder) {

      @Override
      protected Void wrap(byte[] key) throws InvalidProtocolBufferException {
        StopModuleResponse unused = StopModuleResponse.parseFrom(key);
        return null;
      }
    };
    return new IgnoreUnexpectedStateExceptionFuture(modulesServiceFuture,
        STOPPING_STOPPED_MESSAGE);
  }

  private Future<String> getHostnameAsync(GetHostnameRequest.Builder requestBuilder) {
    Future<String> result =
        new ModulesServiceFutureWrapper<String>("GetHostname", requestBuilder) {

      @Override
      protected String wrap(byte[] key) throws InvalidProtocolBufferException {
        GetHostnameResponse.Builder responseBuilder = GetHostnameResponse.newBuilder();
        responseBuilder.mergeFrom(key);
        return responseBuilder.getHostname();
      }
    };
    return result;
  }

  private GetHostnameRequest.Builder newGetHostnameRequestBuilder(String module, String version) {
    GetHostnameRequest.Builder builder = GetHostnameRequest.newBuilder();
    if (module != null) {
      builder.setModule(module);
    }
    if (version != null) {
      builder.setVersion(version);
    }
    return builder;
  }

  @Override
  public String getVersionHostname(String module, String version) {
    return getAsyncResult(getVersionHostnameAsync(module, version));
  }

  private Future<String> getVersionHostnameAsync(String module, String version) {
    GetHostnameRequest.Builder requestBuilder = newGetHostnameRequestBuilder(module, version);
    return getHostnameAsync(requestBuilder);
  }

  @Override
  public String getInstanceHostname(String module, String version, String instance) {
    return getAsyncResult(getInstanceHostnameAsync(module, version, instance));
  }

  private Future<String> getInstanceHostnameAsync(String module, String version, String instance) {
    GetHostnameRequest.Builder requestBuilder = newGetHostnameRequestBuilder(module, version);
    requestBuilder.setInstance(instance);
    return getHostnameAsync(requestBuilder);
  }
  // Only allow instantiation by the ModulesServiceFactoryImpl.
  ModulesServiceImpl() { }

  private abstract static class ModulesServiceFutureWrapper<V> extends FutureWrapper<byte[], V> {
    private final String method;

    public ModulesServiceFutureWrapper(String method, Message.Builder request) {
      super(ApiProxy.makeAsyncCall(PACKAGE, method, request.build().toByteArray()));
      this.method = method;
    }

    @Override
    protected Throwable convertException(Throwable cause) {
      if (cause instanceof ApiProxy.ApplicationException) {
        return convertApplicationException(method, (ApiProxy.ApplicationException) cause);
      } else if (cause instanceof InvalidProtocolBufferException){
        return new ModulesException("Unexpected failure", cause);
      } else {
        return cause;
      }
    }

    private RuntimeException convertApplicationException(String method,
        ApiProxy.ApplicationException e) {
      switch (ErrorCode.forNumber(e.getApplicationError())) {
        case INVALID_MODULE:
          @SuppressWarnings("deprecation")
          RuntimeException result1 = new ModulesException("Unknown module");
          return result1;
        case INVALID_VERSION:
          @SuppressWarnings("deprecation")
          RuntimeException result2  = new ModulesException("Unknown module version");
          return result2;
        case INVALID_INSTANCES:
          @SuppressWarnings("deprecation")
          RuntimeException result3  = new ModulesException("Invalid instance");
          return result3;
        case UNEXPECTED_STATE:
          if (method.equals("StartModule") || method.equals("StopModule")) {
            return new UnexpectedStateException("Unexpected state for method " + method);
          } else {
            return new ModulesException("Unexpected state with method '" + method + "'");
          }
        default:
          return new ModulesException("Unknown error: '" + e.getApplicationError() + "'");
      }
    }
  }

  // TODO: When b/12034257 is fixed remove this wrapper and use the new
  //     com.google.appengine.api.utils.FutureWrapper function.
  /**
   * Delegating {@link Future<Void>} to ignore {@link UnexpectedStateException} in calls to {@link
   * #get} and {@link #get(long, TimeUnit)}.
   */
  @SuppressWarnings("javadoc")
  private static class IgnoreUnexpectedStateExceptionFuture extends ForwardingFuture<Void> {
    private final Future<Void> delegate;
    private final String logMessage;
    IgnoreUnexpectedStateExceptionFuture(Future<Void> delegate, String logMessage) {
      this.delegate = delegate;
      this.logMessage = logMessage;
    }

    @Override protected Future<Void> delegate() {
      return delegate;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
      try {
        return delegate.get();
      } catch (ExecutionException ee) {
        return throwOriginalUnlessUnexpectedState(ee);
      }
    }

    @Override
    public Void get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      try {
        return delegate.get(timeout, unit);
      } catch (ExecutionException ee) {
        return throwOriginalUnlessUnexpectedState(ee);
      }
    }

    private Void throwOriginalUnlessUnexpectedState(ExecutionException original)
        throws ExecutionException {
      Throwable cause = original.getCause();
      if (cause instanceof UnexpectedStateException) {
        logger.info(logMessage);
        return null;
      } else {
        throw original;
      }
    }
  }

  private static ApiProxy.Environment getCurrentEnvironmentOrThrow() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new IllegalStateException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    return environment;
  }
}
