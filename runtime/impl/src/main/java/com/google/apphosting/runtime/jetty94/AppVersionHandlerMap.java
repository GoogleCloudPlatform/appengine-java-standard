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

package com.google.apphosting.runtime.jetty94;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.SessionStoreFactory;
import com.google.apphosting.runtime.jetty9.JettyConstants;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee8.nested.AbstractHandlerContainer;
import org.eclipse.jetty.session.SessionManager;

/**
 * {@code AppVersionHandlerMap} is a {@code HandlerContainer} that identifies each child {@code
 * Handler} with a particular {@code AppVersionKey}.
 *
 * <p>In order to identify which application version each request should be sent to, this class
 * assumes that an attribute will be set on the {@code HttpServletRequest} with a value of the
 * {@code AppVersionKey} that should be used.
 *
 */
public class AppVersionHandlerMap extends AbstractHandlerContainer {
  private final AppVersionHandlerFactory appVersionHandlerFactory;
  private AppVersion appVersion;
  private Handler handler;

  public AppVersionHandlerMap(AppVersionHandlerFactory appVersionHandlerFactory) {
    this.appVersionHandlerFactory = appVersionHandlerFactory;
  }

  public void addAppVersion(AppVersion appVersion) {
    if (appVersion != null) {
      throw new IllegalStateException("Already have an AppVersion " + this.appVersion);
    }
    this.appVersion = appVersion;
  }

  public void removeAppVersion(AppVersionKey appVersionKey) {

    if (Objects.equals(appVersionKey, appVersion.getKey()))
      throw new IllegalArgumentException("AppVersionKey does not match AppVersion " + appVersion.getKey());
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
  public synchronized Handler getHandler(AppVersionKey appVersionKey) throws ServletException {
    if (handler == null) {
      if (appVersion != null) {
        handler = appVersionHandlerFactory.createHandler(appVersion);
      }
    }
    return handler;
  }

  /**
   * Forward the specified request on to the {@link Handler} associated with its application
   * version.
   */
  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    AppVersionKey appVersionKey =
        (AppVersionKey) request.getAttribute(JettyConstants.APP_VERSION_KEY_REQUEST_ATTR);
    if (appVersionKey == null) {
      throw new ServletException("Request did not provide an application version");
    }

    Handler handler = getHandler(appVersionKey);
    if (handler == null) {
      // If we throw an exception here it'll get caught, logged, and
      // turned into a 500, which is definitely not what we want.
      // Instead, we check for this before calling handle(), so this
      // should never happen.
      throw new ServletException("Unknown application: " + appVersionKey);
    }

    try {
      handler.handle(target, baseRequest, request, response);
    } catch (ServletException | IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  protected void doStart() throws Exception {
    for (Handler handler : getHandlers()) {
      handler.start();
    }

    super.doStart();
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();

    for (Handler handler : getHandlers()) {
      handler.stop();
    }
  }

  @Override
  public void setServer(Server server) {
    super.setServer(server);

    for (Handler handler : getHandlers()) {
      handler.setServer(server);
    }
  }

  @Override
  public Handler[] getHandlers() {
    return new Handler[]{ handler };
  }

  @Override
  protected void expandChildren(List<Handler> list, Class<?> byClass) {
    for (Handler handler : getHandlers()) {
      expandHandler(handler, list, byClass);
    }
  }
}
