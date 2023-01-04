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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.jetty9.JettyLogger;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import javax.servlet.ServletException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * This is an implementation of ServletEngineAdapter that uses the third-party Jetty servlet engine.
 *
 */
public class JettyServletEngineAdapter implements ServletEngineAdapter {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final String DEFAULT_APP_YAML_PATH = "/WEB-INF/appengine-generated/app.yaml";
  private static final int MIN_THREAD_POOL_THREADS = 0;
  private static final int MAX_THREAD_POOL_THREADS = 100;
  private static final long MAX_RESPONSE_SIZE = 32 * 1024 * 1024;
  private AppVersionKey lastAppVersionKey;

  static {
    // Tell Jetty to use our custom logging class (that forwards to
    // java.util.logging) instead of writing to System.err
    // Documentation: http://www.eclipse.org/jetty/documentation/current/configuring-logging.html
    System.setProperty("org.eclipse.jetty.util.log.class", JettyLogger.class.getName());
    // Remove internal URLs.
    System.setProperty("java.vendor.url", "");
    System.setProperty("java.vendor.url.bug", "");
  }

  private Server server;
  private RpcConnector rpcConnector;
  private AppVersionHandlerMap appVersionHandlerMap;
  private final WebAppContextFactory contextFactory;
  private final Optional<AppYaml> appYaml;

  public JettyServletEngineAdapter() {
    this(Optional.empty(), Optional.empty());
  }

  public JettyServletEngineAdapter(
      Optional<WebAppContextFactory> contextFactory, Optional<AppYaml> appYaml) {
    this.contextFactory = contextFactory.orElseGet(AppEngineWebAppContextFactory::new);
    this.appYaml = appYaml;
  }

  private static AppYaml getAppYaml(ServletEngineAdapter.Config runtimeOptions) {
    String applicationPath = runtimeOptions.fixedApplicationPath();
    File appYamlFile = new File(applicationPath + DEFAULT_APP_YAML_PATH);

    AppYaml appYaml = null;
    try {
      appYaml = AppYaml.parse(new InputStreamReader(new FileInputStream(appYamlFile), UTF_8));
    } catch (FileNotFoundException | AppEngineConfigException e) {
      logger.atWarning().log("Failed to load app.yaml file at location %s - %s",
          appYamlFile.getPath(), e.getMessage());
    }
    return appYaml;
  }

  @Override
  public void start(String serverInfo, ServletEngineAdapter.Config runtimeOptions) {
    server = new Server(new QueuedThreadPool(MAX_THREAD_POOL_THREADS, MIN_THREAD_POOL_THREADS));
    rpcConnector = new RpcConnector(server);
    server.setConnectors(new Connector[] {rpcConnector});
    AppVersionHandlerFactory appVersionHandlerFactory =
        new AppVersionHandlerFactory(
            server, serverInfo, contextFactory, /*useJettyErrorPageHandler=*/ false);
    appVersionHandlerMap = new AppVersionHandlerMap(appVersionHandlerFactory);

    if (!"java8".equals(System.getenv("GAE_RUNTIME"))) {
      SizeLimitHandler sizeLimitHandler = new SizeLimitHandler(-1, MAX_RESPONSE_SIZE);
      sizeLimitHandler.setHandler(appVersionHandlerMap);
      server.setHandler(sizeLimitHandler);
    } else {
      server.setHandler(appVersionHandlerMap);
    }

    if (runtimeOptions.useJettyHttpProxy()) {
      server.setAttribute(
          "com.google.apphosting.runtime.jetty94.appYaml",
          appYaml.orElseGet(() -> JettyServletEngineAdapter.getAppYaml(runtimeOptions)));
      JettyHttpProxy.startServer(runtimeOptions);
    }

    try {
      server.start();
    } catch (Exception ex) {
      // TODO: Should we have a wrapper exception for this
      // type of thing in ServletEngineAdapter?
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void stop() {
    try {
      server.stop();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void addAppVersion(AppVersion appVersion) throws FileNotFoundException {
    appVersionHandlerMap.addAppVersion(appVersion);
  }

  @Override
  public void deleteAppVersion(AppVersion appVersion) {
    appVersionHandlerMap.removeAppVersion(appVersion.getKey());
  }

  /**
   * Sets the {@link com.google.apphosting.runtime.SessionStoreFactory} that will be used to create
   * the list of {@link com.google.apphosting.runtime.SessionStore SessionStores} to which the HTTP
   * Session will be stored, if sessions are enabled. This method must be invoked after {@link
   * #start(String)}.
   */
  @Override
  public void setSessionStoreFactory(com.google.apphosting.runtime.SessionStoreFactory factory) {
    appVersionHandlerMap.setSessionStoreFactory(factory);
  }

  @Override
  public void serviceRequest(UPRequest upRequest, MutableUpResponse upResponse)
      throws ServletException, IOException {
    if (upRequest.getHandler().getType() != AppinfoPb.Handler.HANDLERTYPE.CGI_BIN_VALUE) {
      upResponse.setError(UPResponse.ERROR.UNKNOWN_HANDLER_VALUE);
      upResponse.setErrorMessage("Unsupported handler type: " + upRequest.getHandler().getType());
      return;
    }

    // Optimise this adaptor assuming one deployed appVersionKey, so use the last one if it matches
    // and only check the handler is available if we see a new/different key.
    AppVersionKey appVersionKey = lastAppVersionKey;
    if (appVersionKey == null
        || !appVersionKey.getAppId().equals(upRequest.getAppId())
        || !appVersionKey.getVersionId().equals(upRequest.getVersionId())) {
      appVersionKey = AppVersionKey.fromUpRequest(upRequest);
      // Check that a handler exists so we can deal with error condition here
      if (appVersionHandlerMap.getHandler(appVersionKey) == null) {
        upResponse.setError(UPResponse.ERROR.UNKNOWN_APP_VALUE);
        upResponse.setErrorMessage("Unknown app: " + appVersionKey);
        return;
      }
      lastAppVersionKey = appVersionKey;
    }

    rpcConnector.serviceRequest(appVersionKey, upRequest, upResponse);
  }
}
