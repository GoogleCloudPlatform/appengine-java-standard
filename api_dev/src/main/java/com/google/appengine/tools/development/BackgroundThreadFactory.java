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

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@link ThreadFactory} creates {@link Thread} objects that
 * live independent of the current request.  This means that they
 * receive their own {@code LocalEnvironment} and have their own set
 * of request ID and {@link RequestEndListener} objects.
 *
 */
public class BackgroundThreadFactory implements ThreadFactory {
  private static final Logger logger = Logger.getLogger(BackgroundThreadFactory.class.getName());

  private static final int API_CALL_LATENCY_MS = 20;
  private static final int THREAD_STARTUP_LATENCY_MS = 20;

  private final String appId;
  private final String moduleName;
  private final String majorVersionId;

  public BackgroundThreadFactory(String appId, String moduleName, String majorVersionId) {
    this.appId = appId;
    this.moduleName = moduleName;
    this.majorVersionId = majorVersionId;
  }

  @Override
  public Thread newThread(final Runnable runnable) {
    // Note we dynamically grab the instance/port from the local environment so
    // we pick up the correct value when handling a forwarded request.
    final LocalBackgroundEnvironment environment =
        new LocalBackgroundEnvironment(appId, moduleName, majorVersionId,
            LocalEnvironment.getCurrentInstance(), LocalEnvironment.getCurrentPort());

    sleepUninterruptably(API_CALL_LATENCY_MS);
    return AccessController.doPrivileged(new PrivilegedAction<Thread>() {
        @Override
        public Thread run() {
          // TODO: Only allow this to be used from a backend.
          Thread thread = new Thread(runnable) {
              @Override
              public void run() {
                sleepUninterruptably(THREAD_STARTUP_LATENCY_MS);
                ApiProxy.setEnvironmentForCurrentThread(environment);
                try {
                  runnable.run();
                } finally {
                  environment.callRequestEndListeners();
                }
              }
            };
            System.setProperty("devappserver-thread-" + thread.getName(), "true");
          return thread;
        }
    });
  }

  final String getAppId() {
    return appId;
  }

  private void sleepUninterruptably(long sleepMillis) {
    try {
      Thread.sleep(sleepMillis);
    } catch (InterruptedException ex) {
      // We can't propagate the exception from here so
      // just log, reset the bit, and continue.
      logger.log(Level.INFO, "Interrupted simulating latency:", ex);
      Thread.currentThread().interrupt();
    }
  }

  private static class LocalBackgroundEnvironment extends LocalEnvironment {
    public LocalBackgroundEnvironment(String appId, String moduleName,
        String majorVersionId, int instance, int port) {
      super(appId, moduleName, majorVersionId, instance, port, null);
    }

    @Override
    public String getEmail() {
      // No user
      return null;
    }

    @Override
    public boolean isLoggedIn() {
      // No user
      return false;
    }

    @Override
    public boolean isAdmin() {
      // No user
      return false;
    }
  }
}
