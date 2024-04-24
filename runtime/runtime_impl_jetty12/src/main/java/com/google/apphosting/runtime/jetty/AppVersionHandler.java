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

package com.google.apphosting.runtime.jetty;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.SessionStoreFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HotSwapHandler;
import org.eclipse.jetty.session.SessionManager;

import java.util.Objects;

/**
 * {@code AppVersionHandlerMap} is a {@code HandlerContainer} that identifies each child {@code
 * Handler} with a particular {@code AppVersionKey}.
 *
 * <p>In order to identify which application version each request should be sent to, this class
 * assumes that an attribute will be set on the {@code HttpServletRequest} with a value of the
 * {@code AppVersionKey} that should be used.
 *
 */
public class AppVersionHandler extends HotSwapHandler {
  private final AppVersionHandlerFactory appVersionHandlerFactory;
  private AppVersion appVersion;

  public AppVersionHandler(AppVersionHandlerFactory appVersionHandlerFactory) {
    this.appVersionHandlerFactory = appVersionHandlerFactory;
  }

  public AppVersion getAppVersion() {
    return appVersion;
  }

  public void addAppVersion(AppVersion appVersion) {
    if (this.appVersion != null) {
      throw new IllegalStateException("Already have an AppVersion " + this.appVersion);
    }
    this.appVersion = Objects.requireNonNull(appVersion);
  }

  public void removeAppVersion(AppVersionKey appVersionKey) {
    if (!Objects.equals(appVersionKey, appVersion.getKey()))
      throw new IllegalArgumentException(
          "AppVersionKey does not match AppVersion " + appVersion.getKey());
    this.appVersion = null;
  }

  /**
   * Sets the {@link SessionStoreFactory} that will be used for generating the list of {@link
   * SessionStore SessionStores} that will be passed to {@link SessionManager} for apps for which
   * sessions are enabled. This setter is currently used only for testing purposes. Normally the
   * default factory is sufficient.
   */
  public void setSessionStoreFactory(SessionStoreFactory factory) {
    // No op with the new Jetty Session management.
  }

  /**
   * Returns the {@code Handler} that will handle requests for the specified application version.
   */
  public synchronized boolean ensureHandler(AppVersionKey appVersionKey) throws Exception {
    if (!Objects.equals(appVersionKey, appVersion.getKey()))
      return false;

    Handler handler = getHandler();
    if (handler == null) {
      handler = appVersionHandlerFactory.createHandler(appVersion);
      setHandler(handler);

      if (Boolean.getBoolean("jetty.server.dumpAfterStart")) {
        if (handler.isStarted())
          handler.getServer().dumpStdErr();
        else
          handler.getServer().setDumpAfterStart(true);
      }
    }
    return (handler != null);
  }
}
