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
import com.google.apphosting.api.ApiProxy.ApiProxyException;
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
 * {@link RemoteApiDelegate} implementation for use inside standalone Java
 * apps.
 *
 */
class StandaloneRemoteApiDelegate extends RemoteApiDelegate {
  private final static Logger logger = Logger.getLogger(RemoteApiDelegate.class.getName());

  private final ExecutorService executor;
  private final String currentUserEmail;

  public StandaloneRemoteApiDelegate(RemoteRpc rpc, RemoteApiOptions options) {
    super(rpc, options);
    this.currentUserEmail = options.getUserEmail();
    this.executor = Executors.newFixedThreadPool(options.getMaxConcurrentRequests());
  }

  @Override
  public byte[] makeSyncCall(Environment env, String serviceName, String methodName,
      byte[] request) {
    // NOTE: currentUserEmail will be null if the options use
    // OAuth credentials.
    if (currentUserEmail != null && !env.getEmail().equals(currentUserEmail)) {
      String message =
          String.format("remote API call: user '%s' can't use client that's logged in as '%s'",
              env.getEmail(), currentUserEmail);
      throw new ApiProxyException(message);
    }
    return makeDefaultSyncCall(serviceName, methodName, request);
  }

  @Override
  public Future<byte[]> makeAsyncCall(final Environment env, final String serviceName,
      final String methodName, final byte[] request, ApiConfig apiConfig) {
    // TODO(b/68190111): Respect deadline in apiConfig.
    return executor.submit(new Callable<byte[]>() {
      @Override
      public byte[] call() throws Exception {
        // Note that any exceptions thrown will be captured and thrown by the Future instead.
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

  @Override
  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
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
