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

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static java.util.stream.Collectors.toList;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.RuntimePb.UPAddDelete;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcPlugin;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.auto.value.AutoBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.flogger.GoogleLogger;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import javax.annotation.Nullable;

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

  /** Environment Variable for logging messages to /var/log. */
  private static final String VAR_LOG_ENV_VAR = "WRITE_LOGS_TO_VAR_LOG";

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

  /** RPC-agnostic plugin. */
  private final AnyRpcPlugin rpcPlugin;

  /** {@code AppVersionFactory} can construct {@link AppVersion} instances. */
  private final AppVersionFactory appVersionFactory;

  /** Handles request setup and tear-down. */
  private final RequestManager requestManager;

  /** The string that should be returned by {@code ServletContext.getServerInfo()}. */
  private final String runtimeVersion;

  /** A template runtime configuration for applications. */
  private final ApplicationEnvironment.RuntimeConfiguration templateConfiguration;

  /** The object responsible for choosing API call deadlines. */
  private final ApiDeadlineOracle deadlineOracle;

  private final Logging logging = new Logging();

  private final BackgroundRequestCoordinator coordinator;

  private final boolean compressResponse;

  private final boolean enableHotspotPerformanceMetrics;

  private final CloudDebuggerAgentWrapper cloudDebuggerAgent;
  private boolean cloudDebuggerEnabled;

  private final boolean pollForNetwork;

  private final boolean redirectStdoutStderr;

  private final boolean logJsonToFile;

  private final boolean clearLogHandlers;

  private final Path jsonLogDir;

  /**
   * This will contain a reference to the ByteBuffer containing Hotspot performance data, exported
   * by the sun.misc.Perf api in Java 8 and by jdk.internal.perf.Perf in Java 9. It's set once and
   * for all when start() is called.
   */
  private ByteBuffer hotspotPerformanceData = null;

  /**
   * The app version that has been received by this runtime, or null if no version has been received
   * yet. We only ever receive one version.
   */
  private AppVersion appVersion;

  /** Get a partly-initialized builder. */
  public static Builder builder() {
    return new AutoBuilder_JavaRuntime_Builder()
        .setCompressResponse(true)
        .setEnableHotspotPerformanceMetrics(true)
        .setCloudDebuggerEnabled(false)
        .setPollForNetwork(false)
        .setDefaultToNativeUrlStreamHandler(false)
        .setForceUrlfetchUrlStreamHandler(false)
        .setIgnoreDaemonThreads(true)
        .setUseEnvVarsFromAppInfo(false)
        .setFixedApplicationPath(null)
        .setRedirectStdoutStderr(true)
        .setLogJsonToFile(false)
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

    public abstract Builder setRpcPlugin(AnyRpcPlugin rpcPlugin);

    public abstract AnyRpcPlugin rpcPlugin();

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

    public abstract Builder setCompressResponse(boolean compressResponse);

    public abstract boolean compressResponse();

    public abstract Builder setEnableHotspotPerformanceMetrics(
        boolean enableHotspotPerformanceMetrics);

    public abstract boolean enableHotspotPerformanceMetrics();

    public abstract Builder setCloudDebuggerAgent(CloudDebuggerAgentWrapper cloudDebuggerAgent);

    public abstract Builder setCloudDebuggerEnabled(boolean cloudDebuggerEnabled);

    public abstract boolean cloudDebuggerEnabled();

    public abstract Builder setPollForNetwork(boolean pollForNetwork);

    public abstract boolean pollForNetwork();

    public abstract Builder setDefaultToNativeUrlStreamHandler(
        boolean defaultToNativeUrlStreamHandler);

    public abstract boolean defaultToNativeUrlStreamHandler();

    public abstract Builder setForceUrlfetchUrlStreamHandler(boolean forceUrlfetchUrlStreamHandler);

    public abstract boolean forceUrlfetchUrlStreamHandler();

    public abstract Builder setIgnoreDaemonThreads(boolean ignoreDaemonThreads);

    public abstract boolean ignoreDaemonThreads();

    public abstract Builder setUseEnvVarsFromAppInfo(boolean useEnvVarsFromAppInfo);

    public abstract boolean useEnvVarsFromAppInfo();

    public abstract Builder setFixedApplicationPath(String fixedApplicationPath);

    public abstract String fixedApplicationPath();

    public abstract Builder setRedirectStdoutStderr(boolean redirect);

    public abstract boolean redirectStdoutStderr();

    public abstract Builder setLogJsonToFile(boolean log);

    public abstract boolean logJsonToFile();

    public abstract Builder setClearLogHandlers(boolean log);

    public abstract Builder setJsonLogDir(Path path);

    public abstract Path jsonLogDir();

    public abstract JavaRuntime build();
  }

  JavaRuntime(
      ServletEngineAdapter servletEngine,
      NullSandboxPlugin sandboxPlugin,
      AnyRpcPlugin rpcPlugin,
      File sharedDirectory,
      RequestManager requestManager,
      String runtimeVersion,
      ApplicationEnvironment.RuntimeConfiguration configuration,
      ApiDeadlineOracle deadlineOracle,
      BackgroundRequestCoordinator coordinator,
      boolean compressResponse,
      boolean enableHotspotPerformanceMetrics,
      CloudDebuggerAgentWrapper cloudDebuggerAgent,
      boolean cloudDebuggerEnabled,
      boolean pollForNetwork,
      boolean defaultToNativeUrlStreamHandler,
      boolean forceUrlfetchUrlStreamHandler,
      boolean ignoreDaemonThreads,
      boolean useEnvVarsFromAppInfo,
      @Nullable String fixedApplicationPath,
      boolean redirectStdoutStderr,
      boolean logJsonToFile,
      boolean clearLogHandlers,
      Path jsonLogDir) {
    this.servletEngine = servletEngine;
    this.sandboxPlugin = sandboxPlugin;
    this.rpcPlugin = rpcPlugin;
    this.requestManager = requestManager;
    this.appVersionFactory =
        AppVersionFactory.builder()
            .setSandboxPlugin(sandboxPlugin)
            .setSharedDirectory(sharedDirectory)
            .setRuntimeVersion(runtimeVersion)
            .setDefaultToNativeUrlStreamHandler(defaultToNativeUrlStreamHandler)
            .setForceUrlfetchUrlStreamHandler(forceUrlfetchUrlStreamHandler)
            .setIgnoreDaemonThreads(ignoreDaemonThreads)
            .setUseEnvVarsFromAppInfo(useEnvVarsFromAppInfo)
            .setFixedApplicationPath(fixedApplicationPath)
            .build();
    this.runtimeVersion = runtimeVersion;
    this.templateConfiguration = configuration;
    this.deadlineOracle = deadlineOracle;
    this.coordinator = coordinator;
    this.compressResponse = compressResponse;
    this.enableHotspotPerformanceMetrics = enableHotspotPerformanceMetrics;
    this.cloudDebuggerAgent = cloudDebuggerAgent;
    this.cloudDebuggerEnabled = cloudDebuggerEnabled;
    this.pollForNetwork = pollForNetwork;
    this.redirectStdoutStderr = redirectStdoutStderr;
    this.logJsonToFile = logJsonToFile;
    this.clearLogHandlers = clearLogHandlers;
    this.jsonLogDir = jsonLogDir;
  }

  /**
   * Starts the Stubby service, and then perform any initialization that the servlet engine
   * requires.
   */
  public void start(ServletEngineAdapter.Config runtimeOptions) {
    logger.atInfo().log("JavaRuntime starting...");

    if (enableHotspotPerformanceMetrics) {
      try {
        // The Perf class is in different packages in Java 8 and Java 9.
        try {
          hotspotPerformanceData = getPerformanceDataByteBuffer("sun.misc.Perf");
        } catch (ClassNotFoundException e) {
          hotspotPerformanceData = getPerformanceDataByteBuffer("jdk.internal.perf.Perf");
        }
      } catch (Exception e) {
        logger.atWarning().withCause(e).log("Failed to access Hotspot performance data");
      }
    }

    SynchronousQueue<Object> rpcStarted = new SynchronousQueue<>();

    new Thread(new RpcRunnable(rpcStarted), "Runtime Network Thread").start();

    // Wait for the servlet engine to start up.
    servletEngine.start("Google App Engine/" + runtimeVersion, runtimeOptions);

    // Wait for our rpc service to start up.
    Object response;
    try {
      response = rpcStarted.take();
    } catch (InterruptedException ex) {
      throw new RuntimeException("Interrupted while starting runtime", ex);
    }
    if (response instanceof Error) {
      throw (Error) response;
    } else if (response instanceof RuntimeException) {
      throw (RuntimeException) response;
    } else if (response instanceof Throwable) {
      throw new RuntimeException(((Throwable) response));
    } else if (response instanceof AnyRpcPlugin) {
      // Success. Ignore the result. When it comes time to stop the server,
      // we'll use the rpcPlugin we have
    } else {
      throw new RuntimeException("Unknown response: " + response);
    }
  }

  private ByteBuffer getPerformanceDataByteBuffer(String perfClassName)
      throws ReflectiveOperationException {
    // Attaching to the current process (lvmid == 0) returns a shared buffer valid
    // for the entire life of the JVM.
    // The following code is equivalent to:
    //   ByteBuffer buffer = Perf.getPerf().attach(0, "r");
    // We invoke it reflectively because this class has been renamed in Java 9.
    Class<?> perfClass = Class.forName(perfClassName);
    Method getPerfMethod = perfClass.getMethod("getPerf");
    Object perf = getPerfMethod.invoke(null);
    Method attachMethod = perf.getClass().getMethod("attach", int.class, String.class);
    ByteBuffer buffer = (ByteBuffer) attachMethod.invoke(perf, 0, "r");
    if (buffer.capacity() == 0) {
      throw new RuntimeException("JVM does not export Hotspot performance data");
    }
    return buffer;
  }

  /** Perform a graceful shutdown of our RPC service, and then shut down our servlet engine. */
  public void stop() {
    logger.atInfo().log("JavaRuntime stopping...");
    rpcPlugin.stopServer();
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
                .setCompressResponse(compressResponse)
                .setServletEngine(servletEngine)
                .build();
        appVersion
            .getThreadGroupPool()
            .start(
                "Request" + upRequest.getEventIdHash(),
                rpcPlugin.traceContextPropagating(requestRunner));
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
      if (cloudDebuggerEnabled && env.isCloudDebuggerDisabled()) {
        logger.atInfo().log("Cloud Debugger is disabled through appengine-web.xml");
        cloudDebuggerEnabled = false;
        requestManager.disableCloudDebugger();
      }

      if ("1.8".equals(JAVA_SPECIFICATION_VERSION.value())) {
        setEnvironmentVariables(env.getEnvironmentVariables());
      }
      System.getProperties().putAll(env.getSystemProperties());
      // NOTE: This string should be kept in sync with the one used by the
      // Logger_.getPrivateContextName(String) method.
      String identifier = env.getAppId() + "/" + env.getVersionId();
      String userLogConfigFilePath = env.getSystemProperties().get("java.util.logging.config.file");
      if (userLogConfigFilePath != null) {
        userLogConfigFilePath =
            env.getRootDirectory().getAbsolutePath() + "/" + userLogConfigFilePath;
      }
      System.setIn(new ByteArrayInputStream(new byte[0]));
      if (logJsonToFile || "1".equals(System.getenv(VAR_LOG_ENV_VAR))) {
        logging.logJsonToFile(
            System.getenv(GOOGLE_CLOUD_PROJECT_ENV_VAR),
            jsonLogDir.resolve(JSON_LOG_OUTPUT_FILE),
            clearLogHandlers);
      } else {
        sandboxPlugin.startCapturingApplicationLogs();
      }
      if (redirectStdoutStderr) {
        // Reassign the standard streams so that e.g. System.out.println works as intended,
        // i.e. it sends output to the application log.
        logging.redirectStdoutStderr(identifier);
      }
      logging.applyLogProperties(userLogConfigFilePath, sandboxPlugin.getApplicationClassLoader());
      // Now notify the servlet engine, so it can do any setup it
      // has to do.
      servletEngine.addAppVersion(appVersion);

      if (cloudDebuggerEnabled) {
        try {
          // Runtimes such as Java8/Java8g use gVisor instead of sandboxing (i.e., they use a null
          // sandbox). It is safe for the debugger to display the internals of these runtimes.
          // We expect this to always downcast successfully in java8:
          URLClassLoader urlLoader = (URLClassLoader) appVersion.getClassLoader();
          cloudDebuggerAgent.setApplication(
              Iterables.toArray(urlsToPaths(urlLoader.getURLs()), String.class), appVersion);
        } catch (RuntimeException ex) {
          // Don't fail the operation if we have a problem with Cloud Debugger.
          logger.atWarning().withCause(ex).log("Error setting class path for Cloud Debugger:");
        }
      }
    } catch (Exception ex) {
      logger.atWarning().withCause(ex).log("Error adding app version:");
      rpc.finishWithAppError(UPAddDelete.ERROR.FAILURE_VALUE, ex.toString());
      return;
    }
    // Do not put this in a finally block.  If we propagate an
    // exception the callback will be invoked automatically.
    rpc.finishWithResponse(EmptyMessage.getDefaultInstance());
  }

  /**
   * Obsolete operation. Deleting app versions has always been theoretically possible but never
   * actually implemented in App Engine.
   */
  @Override
  public synchronized void deleteAppVersion(AnyRpcServerContext rpc, AppInfo appInfo) {
    rpc.finishWithAppError(UPAddDelete.ERROR.FAILURE_VALUE, "Version deletion is unimplemented");
  }

  synchronized AppVersion findAppVersion(String appId, String versionId) {
    AppVersionKey key = AppVersionKey.of(appId, versionId);
    if (key.equals(appVersion.getKey())) {
      return appVersion;
    }
    return null;
  }

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

  private static String urlToPath(URL url) {
    try {
      return Paths.get(url.toURI()).toFile().getAbsolutePath();
    } catch (URISyntaxException ex) {
      logger.atWarning().withCause(ex).log("Failed to convert URL %s to string: ", url);
      return null;
    }
  }

  private static Iterable<String> urlsToPaths(URL[] urls) {
    return Arrays.stream(urls).map(JavaRuntime::urlToPath).filter(s -> s != null).collect(toList());
  }

  private static void setEnvironmentVariables(Map<String, String> vars) {
    // Setting the environment variables after the JVM has started requires a bit of a hack:
    // we reach into the package-private java.lang.ProcessEnvironment class, which incidentally
    // is platform-specific, and replace the map held in a static final field there,
    // using yet more reflection.
    Map<String, String> allVars = new HashMap<>(System.getenv());
    vars.forEach(
        (k, v) -> {
          if (v == null) {
            logger.atWarning().log("Null value for $%s", k);
          }
          allVars.put(k, v);
        });
    try {
      Class<?> pe = Class.forName("java.lang.ProcessEnvironment", true, null);
      Field f = pe.getDeclaredField("theUnmodifiableEnvironment");
      f.setAccessible(true);
      Field m = Field.class.getDeclaredField("modifiers");
      m.setAccessible(true);
      m.setInt(f, m.getInt(f) & ~Modifier.FINAL);
      f.set(null, Collections.unmodifiableMap(allVars));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("failed to set the environment variables", e);
    }
  }

  private class RpcRunnable implements Runnable {
    private final SynchronousQueue<Object> rpcStarted;

    RpcRunnable(SynchronousQueue<Object> rpcStarted) {
      this.rpcStarted = rpcStarted;
    }

    @Override
    public void run() {
      try {
        // NOTE: This method never returns -- this thread is now the
        // network thread and will be responsible for accepting socket
        // connections in a loop and handing off control to the
        // Executor created above.
        startServer();
      } catch (Throwable ex) {
        logger.atSevere().withCause(ex).log("JavaRuntime server could not start");
        try {
          // Something went wrong.  Pass the exception back.
          rpcStarted.put(ex);
        } catch (InterruptedException ex2) {
          throw new RuntimeException(ex2);
        }
      }
    }

    private void startServer() throws Exception {
      CloneControllerImplCallback callback = new CloneControllerImplCallback();
      CloneControllerImpl controller =
          new CloneControllerImpl(
              callback, deadlineOracle, requestManager, hotspotPerformanceData, cloudDebuggerAgent);
      rpcPlugin.startServer(JavaRuntime.this, controller);

      rpcStarted.put(rpcPlugin);

      try {
        logger.atInfo().log("Beginning accept loop.");
        // This must run in the same thread that created the EventDispatcher.
        rpcPlugin.blockUntilShutdown();
      } catch (Throwable ex) {
        // We've already called rpcStarted.put() so there's no
        // sense trying to pass the exception -- no one is waiting any
        // longer.  Instead, just print what we can and kill the
        // server.  Without a network thread we cannot send a response
        // back to the AppServer anyway.
        ex.printStackTrace();
        System.exit(1);
      }
    }
  }

  @VisibleForTesting
  class CloneControllerImplCallback implements CloneControllerImpl.Callback {
    @Override
    public void divertNetworkServices() {
      if (pollForNetwork) {
        pollNetworkingReady();
      }
    }

    @Override
    public AppVersion getAppVersion(String appId, String versionId) {
      return findAppVersion(appId, versionId);
    }

    // Swallow the checked exception that Thread.sleep() has.
    private void sleep(int time) {
      try {
        Thread.sleep(time);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    private void pollNetworkingReady() {
      logger.atInfo().log("Polling for if networking is ready.");
      long start = System.nanoTime();
      // TODO: The gateway client seems to require multiple seconds to be ready.
      for (int i = 0; i < 100; i++) {
        try {
          InetAddress.getByName("google.com");
          long finish = System.nanoTime();
          // If networking is NOT ready, then getByName should throw an exception.
          logger.atInfo().log("Networking ready. Polled for %.3f s.", (finish - start) / 1e9);
          return;
        } catch (UnknownHostException e) {
          // We expect to get exceptions when the gateway client is still
          // starting up.
          logger.atInfo().withCause(e).log("Couldn't connect");
          sleep(100);
        }
      }
      logger.atSevere().log("Could not verify that networking is ready.");
      throw new RuntimeException("Cannot verify that networking is ready");
    }
  }
}
