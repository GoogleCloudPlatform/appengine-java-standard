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
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.List;
import java.util.concurrent.Future;

/**
 * An {@link com.google.apphosting.api.ApiProxy.Delegate} implementation that
 * allows users to install another
 * {@link com.google.apphosting.api.ApiProxy.Delegate} that is only
 * visible to the current thread.  If there is nothing associated with the
 * current thread, the global delegate provided as a constructor argument is
 * used instead.
 *
 */
class ThreadLocalDelegate<E extends Environment> implements ApiProxy.Delegate<E> {

  private final Delegate<E> globalDelegate;

  private final ThreadLocal<Delegate<E>> threadLocalDelegate;

  ThreadLocalDelegate(Delegate<E> globalDelegate, Delegate<E> threadDelegate) {
    this.globalDelegate = globalDelegate;
    this.threadLocalDelegate = new ThreadLocal<Delegate<E>>();
    this.threadLocalDelegate.set(threadDelegate);
  }

  Delegate<E> getDelegate() {
    ApiProxy.Delegate<E> result = threadLocalDelegate.get();
    if (result == null) {
      result = globalDelegate;
    }
    return result;
  }

  @Override
  public byte[] makeSyncCall(E environment, String pkg, String method, byte[] bytes)
      throws ApiProxy.ApiProxyException {
    return getDelegate().makeSyncCall(environment, pkg, method, bytes);
  }

  @Override
  public Future<byte[]> makeAsyncCall(E environment, String pkg, String method, byte[] bytes,
      ApiProxy.ApiConfig apiConfig) {
    return getDelegate().makeAsyncCall(environment, pkg, method, bytes, apiConfig);
  }

  @Override
  public void log(E environment, ApiProxy.LogRecord logRecord) {
    getDelegate().log(environment, logRecord);
  }

  @Override
  public void flushLogs(E environment) {
    getDelegate().flushLogs(environment);
  }

  @Override
  public List<Thread> getRequestThreads(E environment) {
    return getDelegate().getRequestThreads(environment);
  }

  Delegate<E> getDelegateForThread() {
    return threadLocalDelegate.get();
  }

  void setDelegateForThread(Delegate<E> delegate) {
    threadLocalDelegate.set(delegate);
  }

  void clearThreadDelegate() {
    threadLocalDelegate.remove();
  }

  Delegate<E> getGlobalDelegate() {
    return globalDelegate;
  }
}
