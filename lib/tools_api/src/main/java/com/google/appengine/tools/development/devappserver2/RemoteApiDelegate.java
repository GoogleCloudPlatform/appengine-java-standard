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

import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ApiProxy.Delegate} implementation that forwards API requests to an API server over HTTP.
 * This implementation is not thread-safe so every thread that needs to make API calls must have
 * its own instance.
 *
 */
class RemoteApiDelegate implements Delegate<Environment> {
  private static final Logger logger = Logger.getLogger(RemoteApiDelegate.class.getName());

  private final ExecutorService executor;
  private final RemoteRpc remoteRpc;

  RemoteApiDelegate(RemoteRpc rpc, RemoteApiOptions options) {
    this.executor = Executors.newFixedThreadPool(options.getMaxConcurrentRequests());
    this.remoteRpc = new RemoteRpc(options);
  }

  @Override
  public byte[] makeSyncCall(Environment env, String serviceName, String methodName,
      byte[] request) {
    String requestId = RequestIdFilter.threadRequestId();
    if (requestId == null) {
      requestId = "no-request-id";
    }
    return remoteRpc.call(serviceName, methodName, requestId, request);
  }

  @Override
  public Future<byte[]> makeAsyncCall(final Environment env, final String serviceName,
      final String methodName, final byte[] request, ApiConfig apiConfig) {
    // TODO respect deadline in apiConfig
    return executor.submit(new Callable<byte[]>() {
      @Override
      public byte[] call() throws Exception {
        // note that any exceptions thrown will be captured and thrown by the Future instead.
        return makeSyncCall(env, serviceName, methodName, request);
      }
    });
  }

  @Override
  public void log(Environment environment, ApiProxy.LogRecord record) {
    logger.log(toJavaLevel(record.getLevel()),
        "[" + record.getTimestamp() + "] " + record.getMessage());
  }

  @Override
  public List<Thread> getRequestThreads(Environment environment) {
    return Collections.emptyList(); // not implemented
  }

  @Override
  public void flushLogs(Environment environment) {
    // not implemented
  }

  private static Level toJavaLevel(ApiProxy.LogRecord.Level apiProxyLevel) {
    switch (apiProxyLevel) {
      case debug:
        return Level.FINE;
      case info:
        return Level.INFO;
      case warn:
        return Level.WARNING;
      case error:
        return Level.SEVERE;
      case fatal:
        return Level.SEVERE;
      default:
        return Level.WARNING;
    }
  }
}
