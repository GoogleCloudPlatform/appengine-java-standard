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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * {@link RemoteApiDelegate} implementation for use inside an App Engine
 * container.  We let the {@link #containerDelegate} handle calls to the url
 * fetch service for local fulfillment since there's little to be gained from
 * asking some other app to make the same outbound call.  If a good reason for
 * doing this pops up we can add a config option that will let people opt-in to
 * this behavior. 
 *
 */
class HostedRemoteApiDelegate extends RemoteApiDelegate {
  private final Delegate<Environment> containerDelegate;

  public HostedRemoteApiDelegate(RemoteRpc rpc, RemoteApiOptions options,
      Delegate<Environment> containerDelegate) {
    super(rpc, options);
    if (containerDelegate == null) {
      throw new IllegalArgumentException("Options indicate we are running in an App Engine "
          + "container but App Engine services are not available.");
    }
    this.containerDelegate = containerDelegate;
  }

  @Override
  public byte[] makeSyncCall(Environment env, String serviceName, String methodName,
      byte[] request) {
    if (serviceName.equals("urlfetch")) {
      // When remote api is installed in an App Engine container we use the
      // url fetch service to communicate with the remote app, so we need to
      // make sure these calls use the real local urlfetch service.
      return containerDelegate.makeSyncCall(env, serviceName, methodName, request);
    }
    return makeDefaultSyncCall(serviceName, methodName, request);
  }

  @Override
  public Future<byte[]> makeAsyncCall(Environment env, String serviceName,
      String methodName, byte[] request, ApiConfig apiConfig) {
    // TODO: Handle async calls when running inside an App Engine
    // container.
    try {
      return new FakeFuture<byte[]>(makeSyncCall(env, serviceName, methodName, request));
    } catch (Exception e) {
      return new FakeFuture<byte[]>(e);
    }
  }

  @Override
  public void log(Environment environment, ApiProxy.LogRecord record) {
    containerDelegate.log(environment, record);
  }

  @Override
  public List<Thread> getRequestThreads(Environment environment) {
    return containerDelegate.getRequestThreads(environment);
  }

  @Override
  public void flushLogs(Environment environment) {
    containerDelegate.flushLogs(environment);
  }

  @Override
  public void shutdown() {
    // nothing to do
  }

  /**
   * Wraps an already-resolved result in a {@link Future}.
   * @param <T> The type of the Future.
   */
  @SuppressWarnings("ShouldNotSubclass")
  private static class FakeFuture<T> implements Future<T> {
    private final T result;
    private final Exception exception;

    FakeFuture(T result) {
      this(result, null);
    }

    FakeFuture(Exception e) {
      this(null, e);
    }

    private FakeFuture(T result, Exception exception) {
      this.result = result;
      this.exception = exception;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      // cancel() returns false if the Future has already executed
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws ExecutionException {
      if (exception != null) {
        throw new ExecutionException(exception);
      }
      return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws ExecutionException {
      if (exception != null) {
        throw new ExecutionException(exception);
      }
      return result;
    }
  }
}
