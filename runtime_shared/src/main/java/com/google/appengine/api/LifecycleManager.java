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

package com.google.appengine.api;

import com.google.apphosting.api.ApiProxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Information about the current status of the Java Runtime. */
// TODO Ideally this should not be a shared class. That would
// require moving most of this logic into ApiProxy, or creating an
// actual API for it.  We need to revisit this once we've got
// more experience using servers.
public final class LifecycleManager {

  private static final LifecycleManager instance = new LifecycleManager();

  private volatile boolean shuttingDown = false;
  private volatile long deadline = -1L;

  private LifecycleManager() { }

  public static LifecycleManager getInstance() {
    return instance;
  }

  public boolean isShuttingDown() {
    return shuttingDown;
  }

  /**
   * Register a ShutdownHook to be called when the runtime shuts down.
   *
   * @throws NullPointerException if the calling thread is neither a request thread nor a thread
   *     created by {@link com.google.appengine.api.ThreadManager ThreadManager}.
   */
  public synchronized void setShutdownHook(ShutdownHook hook) {
    hooks.put(currentAppVersionId(), hook);
  }

  /** Calls Thread.interrupt() on all threads running requests for this application. */
  public void interruptAllRequests() {
    List<Thread> threads = ApiProxy.getRequestThreads();
    if (threads != null) {
      for (Thread thread : threads) {
        thread.interrupt();
      }
    }
  }

  /**
   * If the runtime is shutting down, returns how long, in
   * milliseconds, is left for shutdown code to clean up. Otherwise,
   * returns -1.
   */
  public long getRemainingShutdownTime() {
    long value = deadline;
    if (value == -1L) {
      return -1L;
    } else {
      return value - System.currentTimeMillis();
    }
  }

  /**
   * For testing purposes only:
   * Notifies the LifecycleManager that the runtime is shutting down.
   */
  public synchronized void beginShutdown(long deadline) {
    this.shuttingDown = true;
    this.deadline = deadline;
    // Shutdown this app/version only. Presumably we'd have multiple
    // shutdown requests if we have multiple app/versions in the same
    // VM.
    ShutdownHook hook = hooks.get(currentAppVersionId());
    if (hook != null) {
      hook.shutdown();
    }
  }

  private String currentAppVersionId() {
    ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
    if (env == null) {
      throw new NullPointerException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    return env.getAppId() + "/" + env.getVersionId();
  }

  private Map<String, ShutdownHook> hooks = new HashMap<String, ShutdownHook>();

  public interface ShutdownHook {
    void shutdown();
  }
}
