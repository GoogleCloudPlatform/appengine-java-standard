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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.JettyConstants;
import com.google.apphosting.runtime.SessionStore;
import com.google.apphosting.runtime.SessionStoreFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandlerContainer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  private final Map<AppVersionKey, AppVersion> appVersionMap;
  private final Map<AppVersionKey, Handler> handlerMap;

  public AppVersionHandlerMap(AppVersionHandlerFactory appVersionHandlerFactory) {
    this.appVersionHandlerFactory = appVersionHandlerFactory;
    this.appVersionMap = new HashMap<>();
    this.handlerMap = new HashMap<>();
  }

  public void addAppVersion(AppVersion appVersion) {
    appVersionMap.put(appVersion.getKey(), appVersion);
  }

  public void removeAppVersion(AppVersionKey appVersionKey) {
    appVersionMap.remove(appVersionKey);
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
    Handler handler = handlerMap.get(appVersionKey);
    if (handler == null) {
      AppVersion appVersion = appVersionMap.get(appVersionKey);
      if (appVersion != null) {
        handler = appVersionHandlerFactory.createHandler(appVersion);
        addManaged(handler);
        Handler oldHandler = handlerMap.put(appVersionKey, handler);
        if (oldHandler != null) {
          removeBean(oldHandler);
        }

        if (Boolean.getBoolean("jetty.server.dumpAfterStart")) {
          handler.getServer().dumpStdErr();
        }
      }
    }
    return handler;
  }

  /**
   * Forward the specified request on to the {@link Handler} associated with its application
   * version.
   */
  @Override
  public void handle(
      String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
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
    } catch (RuntimeException ex) {
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
    return handlerMap.values().toArray(new Handler[0]);
  }

  /**
   * Not supported.
   *
   * @throws UnsupportedOperationException
   */
  public void setHandlers(Handler[] handlers) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not supported.
   *
   * @throws UnsupportedOperationException
   */
  public void addHandler(Handler handler) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not supported.
   *
   * @throws UnsupportedOperationException
   */
  public void removeHandler(Handler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void expandChildren(List<Handler> list, Class<?> byClass) {
    for (Handler handler : getHandlers()) {
      expandHandler(handler, list, byClass);
    }
  }
}
