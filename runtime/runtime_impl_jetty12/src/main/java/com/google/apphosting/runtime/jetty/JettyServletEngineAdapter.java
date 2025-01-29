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

import static com.google.apphosting.runtime.AppEngineConstants.GAE_RUNTIME;
import static com.google.apphosting.runtime.AppEngineConstants.HTTP_CONNECTOR_MODE;
import static com.google.apphosting.runtime.AppEngineConstants.IGNORE_RESPONSE_SIZE_LIMIT;
import static com.google.apphosting.runtime.AppEngineConstants.LEGACY_MODE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.LocalRpcContext;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.apphosting.runtime.jetty.delegate.DelegateConnector;
import com.google.apphosting.runtime.jetty.delegate.impl.DelegateRpcExchange;
import com.google.apphosting.runtime.jetty.http.JettyHttpHandler;
import com.google.apphosting.runtime.jetty.proxy.JettyHttpProxy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppYaml;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.MultiPartCompliance;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.SizeLimitHandler;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * This is an implementation of ServletEngineAdapter that uses the third-party Jetty servlet engine.
 */
public class JettyServletEngineAdapter implements ServletEngineAdapter {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final String DEFAULT_APP_YAML_PATH = "/WEB-INF/appengine-generated/app.yaml";
  private static final int MIN_THREAD_POOL_THREADS = 0;
  private static final int MAX_THREAD_POOL_THREADS = 100;
  private static final long MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  private AppVersionKey lastAppVersionKey;

  static {
    // Set legacy system property to dummy value because external libraries
    // (google-auth-library-java)
    // test if this value is null to decide whether it is Java 7 runtime.
    System.setProperty("org.eclipse.jetty.util.log.class", "DEPRECATED");
  }

  private Server server;
  private DelegateConnector rpcConnector;
  private AppVersionHandler appVersionHandler;

  public JettyServletEngineAdapter() {}

  private static AppYaml getAppYaml(ServletEngineAdapter.Config runtimeOptions) {
    String applicationPath = runtimeOptions.fixedApplicationPath();
    File appYamlFile = new File(applicationPath + DEFAULT_APP_YAML_PATH);
    AppYaml appYaml = null;
    try {
      appYaml = AppYaml.parse(new InputStreamReader(new FileInputStream(appYamlFile), UTF_8));
    } catch (FileNotFoundException | AppEngineConfigException e) {
      logger.atWarning().log(
          "Failed to load app.yaml file at location %s - %s",
          appYamlFile.getPath(), e.getMessage());
    }
    return appYaml;
  }

  @Override
  public void start(String serverInfo, ServletEngineAdapter.Config runtimeOptions) {
    boolean isHttpConnectorMode = Boolean.getBoolean(HTTP_CONNECTOR_MODE);
    QueuedThreadPool threadPool =
        new QueuedThreadPool(MAX_THREAD_POOL_THREADS, MIN_THREAD_POOL_THREADS);
    // Try to enable virtual threads if requested and on java21:
    if (Boolean.getBoolean("appengine.use.virtualthreads")
        && "java21".equals(GAE_RUNTIME)) {
      threadPool.setVirtualThreadsExecutor(VirtualThreads.getDefaultVirtualThreadsExecutor());
      logger.atInfo().log("Configuring Appengine web server virtual threads.");
    }

    server =
        new Server(threadPool) {
          @Override
          public InvocationType getInvocationType() {
            return InvocationType.BLOCKING;
          }
        };

    // Don't add the RPC Connector if in HttpConnector mode.
    if (!isHttpConnectorMode) {
      rpcConnector =
          new DelegateConnector(server, "RPC") {
            @Override
            public void run(Runnable runnable) {
              // Override this so that it does the initial run in the same thread.
              // Currently, we block until completion in serviceRequest() so no point starting new
              // thread.
              runnable.run();
            }
          };

      HttpConfiguration httpConfiguration = rpcConnector.getHttpConfiguration();
      httpConfiguration.setSendDateHeader(false);
      httpConfiguration.setSendServerVersion(false);
      httpConfiguration.setSendXPoweredBy(false);

      // If runtime is using EE8, then set URI compliance to LEGACY to behave like Jetty 9.4.
      if (Objects.equals(AppVersionHandlerFactory.getEEVersion(), AppVersionHandlerFactory.EEVersion.EE8)) {
        httpConfiguration.setUriCompliance(UriCompliance.LEGACY);
      }

      if (LEGACY_MODE) {
        httpConfiguration.setUriCompliance(UriCompliance.LEGACY);
        httpConfiguration.setHttpCompliance(HttpCompliance.RFC7230_LEGACY);
        httpConfiguration.setRequestCookieCompliance(CookieCompliance.RFC2965);
        httpConfiguration.setResponseCookieCompliance(CookieCompliance.RFC2965);
        httpConfiguration.setMultiPartCompliance(MultiPartCompliance.LEGACY);
      }

      server.addConnector(rpcConnector);
    }

    AppVersionHandlerFactory appVersionHandlerFactory =
        AppVersionHandlerFactory.newInstance(server, serverInfo);
    appVersionHandler = new AppVersionHandler(appVersionHandlerFactory);
    server.setHandler(appVersionHandler);

    // In HttpConnector mode we will combine both SizeLimitHandlers.
    boolean ignoreResponseSizeLimit = Boolean.getBoolean(IGNORE_RESPONSE_SIZE_LIMIT);
    if (!ignoreResponseSizeLimit && !isHttpConnectorMode) {
      server.insertHandler(new SizeLimitHandler(-1, MAX_RESPONSE_SIZE));
    }

    boolean startJettyHttpProxy = false;
    if (runtimeOptions.useJettyHttpProxy()) {
      AppInfoFactory appInfoFactory;
      AppVersionKey appVersionKey;
      /* The init actions are not done in the constructor as they are not used when testing */
      try {
        String appRoot = runtimeOptions.applicationRoot();
        String appPath = runtimeOptions.fixedApplicationPath();
        appInfoFactory = new AppInfoFactory(System.getenv());
        AppinfoPb.AppInfo appinfo = appInfoFactory.getAppInfoFromFile(appRoot, appPath);
        // TODO Should we also call ApplyCloneSettings()?
        LocalRpcContext<EmptyMessage> context = new LocalRpcContext<>(EmptyMessage.class);
        EvaluationRuntimeServerInterface evaluationRuntimeServerInterface =
            Objects.requireNonNull(runtimeOptions.evaluationRuntimeServerInterface());
        evaluationRuntimeServerInterface.addAppVersion(context, appinfo);
        context.getResponse();
        appVersionKey = AppVersionKey.fromAppInfo(appinfo);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      if (isHttpConnectorMode) {
        logger.atInfo().log("Using HTTP_CONNECTOR_MODE to bypass RPC");
        server.insertHandler(
            new JettyHttpHandler(
                runtimeOptions, appVersionHandler.getAppVersion(), appVersionKey, appInfoFactory));
        JettyHttpProxy.insertHandlers(server, ignoreResponseSizeLimit);
        server.addConnector(JettyHttpProxy.newConnector(server, runtimeOptions));
      } else {
        server.setAttribute(
            "com.google.apphosting.runtime.jetty.appYaml",
            JettyServletEngineAdapter.getAppYaml(runtimeOptions));
        // Delay start of JettyHttpProxy until after the main server and application is started.
        startJettyHttpProxy = true;
      }
    }
    try {
      server.start();
      if (startJettyHttpProxy) {
        JettyHttpProxy.startServer(runtimeOptions);
      }
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
  public void addAppVersion(AppVersion appVersion) {
    appVersionHandler.addAppVersion(appVersion);
  }

  @Override
  public void deleteAppVersion(AppVersion appVersion) {
    appVersionHandler.removeAppVersion(appVersion.getKey());
  }

  @Override
  public void setSessionStoreFactory(com.google.apphosting.runtime.SessionStoreFactory factory) {
    // No op with the new Jetty Session management.
  }

  @Override
  public void serviceRequest(UPRequest upRequest, MutableUpResponse upResponse) throws Exception {
    if (upRequest.getHandler().getType() != AppinfoPb.Handler.HANDLERTYPE.CGI_BIN_VALUE) {
      upResponse.setError(UPResponse.ERROR.UNKNOWN_HANDLER_VALUE);
      upResponse.setErrorMessage("Unsupported handler type: " + upRequest.getHandler().getType());
      return;
    }
    // Optimise this adaptor assuming one deployed appVersionKey, so use the last one if it matches
    // and only check the handler is available if we see a new/different key.
    AppVersionKey appVersionKey = AppVersionKey.fromUpRequest(upRequest);
    AppVersionKey lastVersionKey = lastAppVersionKey;
    if (lastVersionKey != null) {
      // We already have created the handler on the previous request, so no need to do another
      // getHandler().
      // The two AppVersionKeys must be the same as we only support one app version.
      if (!Objects.equals(appVersionKey, lastVersionKey)) {
        upResponse.setError(UPResponse.ERROR.UNKNOWN_APP_VALUE);
        upResponse.setErrorMessage("Unknown app: " + appVersionKey);
        return;
      }
    } else {
      if (!appVersionHandler.ensureHandler(appVersionKey)) {
        upResponse.setError(UPResponse.ERROR.UNKNOWN_APP_VALUE);
        upResponse.setErrorMessage("Unknown app: " + appVersionKey);
        return;
      }
      lastAppVersionKey = appVersionKey;
    }

    DelegateRpcExchange rpcExchange = new DelegateRpcExchange(upRequest, upResponse);
    rpcExchange.setAttribute(AppEngineConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);
    rpcExchange.setAttribute(AppEngineConstants.ENVIRONMENT_ATTR, ApiProxy.getCurrentEnvironment());
    rpcConnector.service(rpcExchange);
    try {
      rpcExchange.awaitResponse();
    } catch (Throwable t) {
      Throwable error = t;
      if (error instanceof ExecutionException) {
        error = error.getCause();
      }
      upResponse.setError(UPResponse.ERROR.UNEXPECTED_ERROR_VALUE);
      upResponse.setErrorMessage("Unexpected Error: " + error);
    }
  }
}
