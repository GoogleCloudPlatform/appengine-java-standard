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

package com.google.apphosting.runtime;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb.UPAddDelete;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.auto.value.AutoBuilder;
import com.google.common.flogger.GoogleLogger;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jspecify.annotations.Nullable;

/**
 * JavaRuntime implements the Prometheus EvaluationRuntime service. It handles any requests for the
 * "java" runtime. At the moment, this only includes requests whose handler type is SERVLET. The
 * handler path is assumed to be the full class name of a class that extends {@link
 * javax.servlet.GenericServlet}.
 *
 * <p>{@code JavaRuntime} is not responsible for configuring {@code ApiProxy} with a delegate. This
 * class should probably be instantiated by {@code JavaRuntimeFactory}, which also sets up {@code
 * ApiProxy}.
 *
 */
public class JavaRuntime implements EvaluationRuntimeServerInterface {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /** Environment variable for the GCP project. */
  private static final String GOOGLE_CLOUD_PROJECT_ENV_VAR = "GOOGLE_CLOUD_PROJECT";

  /** Default directory for JSON-formatted logs. */
  private static final Path DEFAULT_JSON_LOG_OUTPUT_DIR = Paths.get("/var/log");

  /** Filename for JSON-formatted logs. */
  private static final String JSON_LOG_OUTPUT_FILE = "app";

  /**
   * ServletEngineAdapter is a wrapper around the servlet engine to whom we are deferring servlet
   * lifecycle and request/response management.
   */
  private final ServletEngineAdapter servletEngine;

  /** Sandbox-agnostic plugin. */
  private final NullSandboxPlugin sandboxPlugin;

  /** {@code AppVersionFactory} can construct {@link AppVersion} instances. */
  private final AppVersionFactory appVersionFactory;

  /** Handles request setup and tear-down. */
  private final RequestManager requestManager;

  /** The string that should be returned by {@code ServletContext.getServerInfo()}. */
  private final String runtimeVersion;

  /** A template runtime configuration for applications. */
  private final ApplicationEnvironment.RuntimeConfiguration templateConfiguration;

  private final Logging logging = new Logging();

  private final BackgroundRequestCoordinator coordinator;

  private final boolean clearLogHandlers;

  private final Path jsonLogDir;

  /**
   * The app version that has been received by this runtime, or null if no version has been received
   * yet. We only ever receive one version.
   */
  private AppVersion appVersion;

  /** Get a partly-initialized builder. */
  public static Builder builder() {
    return new AutoBuilder_JavaRuntime_Builder()
        .setForceUrlfetchUrlStreamHandler(false)
        .setFixedApplicationPath(null)
        .setClearLogHandlers(true)
        .setJsonLogDir(DEFAULT_JSON_LOG_OUTPUT_DIR);
  }

  /** Builder for JavaRuntime. */
  @AutoBuilder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder setServletEngine(ServletEngineAdapter servletEngine);

    public abstract ServletEngineAdapter servletEngine();

    public abstract Builder setSandboxPlugin(NullSandboxPlugin sandboxPlugin);

    public abstract Builder setSharedDirectory(File sharedDirectory);

    public abstract File sharedDirectory();

    public abstract Builder setRequestManager(RequestManager requestManager);

    public abstract Builder setRuntimeVersion(String runtimeVersion);

    public abstract String runtimeVersion();

    public abstract Builder setConfiguration(
        ApplicationEnvironment.RuntimeConfiguration configuration);

    public abstract ApplicationEnvironment.RuntimeConfiguration configuration();

    public abstract Builder setDeadlineOracle(ApiDeadlineOracle deadlineOracle);

    public abstract ApiDeadlineOracle deadlineOracle();

    public abstract Builder setCoordinator(BackgroundRequestCoordinator coordinator);

    public abstract Builder setForceUrlfetchUrlStreamHandler(boolean forceUrlfetchUrlStreamHandler);

    public abstract boolean forceUrlfetchUrlStreamHandler();

    public abstract Builder setFixedApplicationPath(String fixedApplicationPath);

    public abstract String fixedApplicationPath();

    public abstract Builder setClearLogHandlers(boolean log);

    public abstract Builder setJsonLogDir(Path path);

    public abstract Path jsonLogDir();

    public abstract JavaRuntime build();
  }

  JavaRuntime(
      ServletEngineAdapter servletEngine,
      NullSandboxPlugin sandboxPlugin,
      File sharedDirectory,
      RequestManager requestManager,
      String runtimeVersion,
      ApplicationEnvironment.RuntimeConfiguration configuration,
      ApiDeadlineOracle deadlineOracle,
      BackgroundRequestCoordinator coordinator,
      boolean forceUrlfetchUrlStreamHandler,
      @Nullable String fixedApplicationPath,
      boolean clearLogHandlers,
      Path jsonLogDir) {
    this.servletEngine = servletEngine;
    this.sandboxPlugin = sandboxPlugin;
    this.requestManager = requestManager;
    this.appVersionFactory =
        AppVersionFactory.builder()
            .setSandboxPlugin(sandboxPlugin)
            .setSharedDirectory(sharedDirectory)
            .setRuntimeVersion(runtimeVersion)
            .setForceUrlfetchUrlStreamHandler(forceUrlfetchUrlStreamHandler)
            .setFixedApplicationPath(fixedApplicationPath)
            .build();
    this.runtimeVersion = runtimeVersion;
    this.templateConfiguration = configuration;
    this.coordinator = coordinator;
    this.clearLogHandlers = clearLogHandlers;
    this.jsonLogDir = jsonLogDir;
  }

  /**
   * Starts the Stubby service, and then perform any initialization that the servlet engine
   * requires.
   */
  public void start(ServletEngineAdapter.Config runtimeOptions) {
    logger.atInfo().log("JavaRuntime starting...");

    // Wait for the servlet engine to start up.
    servletEngine.start("Google App Engine/" + runtimeVersion, runtimeOptions);
  }

  /** Perform a graceful shutdown of our RPC service, and then shut down our servlet engine. */
  public void stop() {
    logger.atInfo().log("JavaRuntime stopped.");
    servletEngine.stop();
  }

  /**
   * Translate the specified UPRequest from Prometheus into a {@link
   * javax.servlet.http.HttpServletRequest}, invoke the specified servlet, and translate the
   * response back into an UPResponse.
   */
  @Override
  public void handleRequest(AnyRpcServerContext rpc, UPRequest upRequest) {
    try {
      MutableUpResponse upResponse = new MutableUpResponse();
      AppVersionKey appVersionKey = AppVersionKey.fromUpRequest(upRequest);
      logger.atFine().log("Received handleRequest for %s", appVersionKey);

      if (appVersion == null) {
        RequestRunner.setFailure(
            upResponse, UPResponse.ERROR.UNKNOWN_APP, "AddAppVersion not called");
        rpc.finishWithResponse(upResponse.build());
        return;
      }
      if (!appVersion.getKey().equals(appVersionKey)) {
        String message =
            String.format("App version %s should be %s", appVersionKey, appVersion.getKey());
        RequestRunner.setFailure(upResponse, UPResponse.ERROR.UNKNOWN_APP, message);
        rpc.finishWithResponse(upResponse.build());
        return;
      }

      try {
        RequestRunner requestRunner =
            RequestRunner.builder()
                .setAppVersion(appVersion)
                .setRpc(rpc)
                .setUpRequest(upRequest)
                .setUpResponse(upResponse)
                .setRequestManager(requestManager)
                .setCoordinator(coordinator)
                .setUpRequestHandler(servletEngine)
                .build();
        appVersion
            .getThreadGroupPool()
            .start(
                "Request" + upRequest.getEventIdHash(),
                requestRunner);
      } catch (InterruptedException ex) {
        RequestRunner.setFailure(
            upResponse,
            UPResponse.ERROR.APP_FAILURE,
            "Interrupted while starting request thread: " + ex);
        rpc.finishWithResponse(upResponse.build());
        // We're running directly on the Stubby network thread, and it
        // doesn't respect interrupts.  If we let the network thread die
        // due to this interruption we will lose any ability to respond
        // to the runtime, so continue on.  If we re-set the interrupt
        // bit (as is generally suggested if swallowing an
        // InterruptedException), some random activity in the next
        // request will fail, so we just log it and move on.
      }
    } catch (Throwable th) {
      // If a serious exception has occurred outside of the scope of RequestRunner#run()
      // This may be in preparation of the request or it may even being in the handling and/or
      // logging of a previously detected serious exception.  It is unlikely that sending a
      // response will succeed, so it is best to attempt to log and then exit.
      killCloneIfSeriousException(th);
      throw th;
    }
  }

  /** Adds the specified application version so that it can be used for future requests. */
  @Override
  public synchronized void addAppVersion(AnyRpcServerContext rpc, AppInfo appInfo) {
    if (appVersion != null) {
      rpc.finishWithAppError(
          UPAddDelete.ERROR.FAILURE_VALUE,
          "AddAppVersion already called with version " + appVersion);
      return;
    }
    try {
      AppEngineWebXml appEngineWebXml = appVersionFactory.readAppEngineWebXml(appInfo);
      appVersion =
          appVersionFactory.createAppVersion(appInfo, appEngineWebXml, templateConfiguration);

      ApplicationEnvironment env = appVersion.getEnvironment();

      System.getProperties().putAll(env.getSystemProperties());
      // NOTE: This string should be kept in sync with the one used by the
      // Logger_.getPrivateContextName(String) method.
      String userLogConfigFilePath = env.getSystemProperties().get("java.util.logging.config.file");
      if (userLogConfigFilePath != null) {
        userLogConfigFilePath =
            env.getRootDirectory().getAbsolutePath() + "/" + userLogConfigFilePath;
      }
      System.setIn(new ByteArrayInputStream(new byte[0]));
      logging.logJsonToFile(
          System.getenv(GOOGLE_CLOUD_PROJECT_ENV_VAR),
          jsonLogDir.resolve(JSON_LOG_OUTPUT_FILE),
          clearLogHandlers);
      logging.applyLogProperties(userLogConfigFilePath, sandboxPlugin.getApplicationClassLoader());
      // Now notify the servlet engine, so it can do any setup it
      // has to do.
      servletEngine.addAppVersion(appVersion);
    } catch (Exception ex) {
      logger.atWarning().withCause(ex).log("Error adding app version:");
      rpc.finishWithAppError(UPAddDelete.ERROR.FAILURE_VALUE, ex.toString());
      return;
    }
    // Do not put this in a finally block.  If we propagate an
    // exception the callback will be invoked automatically.
    rpc.finishWithResponse(EmptyMessage.getDefaultInstance());
  }

  synchronized AppVersion findAppVersion(String appId, String versionId) {
    AppVersionKey key = AppVersionKey.of(appId, versionId);
    if (key.equals(appVersion.getKey())) {
      return appVersion;
    }
    return null;
  }

  @SuppressWarnings("SystemExitOutsideMain")
  public static void killCloneIfSeriousException(Throwable th) {
    if (RequestRunner.shouldKillCloneAfterException(th)) {
      try {
        // Try to log, but this may fail.
        System.err.println("Killing clone due to serious exception");
        th.printStackTrace();
        logger.atSevere().withCause(th).log("Killing clone due to serious exception");
      } finally {
        System.exit(1);
      }
    }
  }
}
