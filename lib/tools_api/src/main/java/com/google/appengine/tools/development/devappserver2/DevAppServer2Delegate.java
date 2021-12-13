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

import com.google.appengine.tools.development.DevLogService;
import com.google.appengine.tools.development.DevServices;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * ApiProxy.Delegate that handles API calls by making an RPC to a remote API server. Because of the
 * structure of the Java Remote API client support, we must construct a separate delegate for every
 * thread that might make RPCs. This class hides that by creating per-thread delegates on demand and
 * forwarding every API call to the current thread's delegate.  It also avoids infinite recursion by
 * ensuring that the HTTP request wrapping the RPC uses native sockets rather than being diverted to
 * the URLFetch service.
 *
 */
class DevAppServer2Delegate implements ApiProxy.Delegate<Environment>, DevServices {
  private final ThreadLocal<RemoteApiDelegate> threadLocalRemoteApiDelegate = new ThreadLocal<>();
  private final RemoteApiOptions remoteApiOptions;

  DevAppServer2Delegate(RemoteApiOptions remoteApiOptions) {
    this.remoteApiOptions = remoteApiOptions;
  }

  /**
   * Return a remote API delegate for the current thread. This may be a new object or the cached
   * result of an earlier call to this method from the same thread.
   */
  private synchronized ApiProxy.Delegate<Environment> remoteApiDelegate() {
    RemoteApiDelegate delegate = threadLocalRemoteApiDelegate.get();
    if (delegate == null) {
      RemoteRpc remoteRpc = new RemoteRpc(remoteApiOptions);
      delegate = new RemoteApiDelegate(remoteRpc, remoteApiOptions);
      threadLocalRemoteApiDelegate.set(delegate);
    }
    return delegate;
  }

  @Override
  public byte[] makeSyncCall(
      Environment environment, String packageName, String methodName, byte[] request) {
    return remoteApiDelegate().makeSyncCall(environment, packageName, methodName, request);
  }

  @Override
  public Future<byte[]> makeAsyncCall(
      Environment environment,
      String packageName,
      String methodName,
      byte[] request,
      ApiProxy.ApiConfig apiConfig) {
    return remoteApiDelegate()
        .makeAsyncCall(environment, packageName, methodName, request, apiConfig);
  }

  @Override
  public void log(Environment environment, ApiProxy.LogRecord record) {
    remoteApiDelegate().log(environment, record);
  }

  @Override
  public void flushLogs(Environment environment) {
    remoteApiDelegate().flushLogs(environment);
  }

  @Override
  public List<Thread> getRequestThreads(Environment environment) {
    return remoteApiDelegate().getRequestThreads(environment);
  }

  @Override
  public DevLogService getLogService() {
    return new DevLogServiceImpl();
  }

  private static class DevLogServiceImpl implements DevLogService {
    private static StreamHandler streamHandler;

    private static synchronized StreamHandler getStreamHandler() {
      // TODO: send log message to the log service instead of this.
      if (streamHandler == null) {
        streamHandler = new ConsoleHandler();
        streamHandler.setFormatter(new Formatter() {
          @Override
          public String format(LogRecord record) {
            return record.getMessage() + "\n";
          }
        });
      }
      return streamHandler;
    }

    @Override
    public Handler getLogHandler() {
      return getStreamHandler();
    }
  }
}
