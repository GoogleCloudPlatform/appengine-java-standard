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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.anyrpc.AnyRpcPlugin;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.GoogleLogger;
import com.google.common.net.HostAndPort;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Creates a new {@link JavaRuntime}. This class parses command-line arguments, instantiates a
 * {@code JavaRuntime} and a {@link ServletEngineAdapter}, and starts the {@code JavaRuntime}.
 *
 * <p>{@code JavaRuntimeFactory} also configures {@code ApiProxy} with a delegate that can make RPC
 * calls back to the trusted process.
 *
 */
public class JavaRuntimeFactory {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Real implementation of {@code CloudDebuggerAgentWrapper} interface that forwards all the calls
   * to the actual Cloud Debugger agent.
   */
  static class CloudDebuggerAgentWrapperImpl implements CloudDebuggerAgentWrapper {
    @Override
    public void bind(ClassLoader debuggerInternalsClassLoader) {
      CloudDebuggerAgent.bind(debuggerInternalsClassLoader);
    }

    @Override
    public void setApplication(String[] classPath, CloudDebuggerCallback callback) {
      CloudDebuggerAgent.setApplication(classPath, callback);
    }

    @Override
    public void setActiveBreakpoints(byte[][] breakpoints) {
      CloudDebuggerAgent.setActiveBreakpoints(breakpoints);
    }

    @Override
    public boolean hasBreakpointUpdates() {
      return CloudDebuggerAgent.hasBreakpointUpdates();
    }

    @Override
    public byte[][] dequeueBreakpointUpdates() {
      return CloudDebuggerAgent.dequeueBreakpointUpdates();
    }
  }

  @VisibleForTesting
  JavaRuntimeFactory() {}

  public static void startRuntime(NullSandboxPlugin sandboxPlugin, String[] args) {
    JavaRuntimeFactory factory = new JavaRuntimeFactory();
    factory.getStartedRuntime(sandboxPlugin, args);
  }

  public JavaRuntime getStartedRuntime(NullSandboxPlugin sandboxPlugin, String[] args) {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    List<String> unknownParams = params.getUnknownParams();
    if (!unknownParams.isEmpty()) {
      logger.atWarning().log("Unknown command line arguments: %s", unknownParams);
    }

    RuntimeLogSink logSink = new RuntimeLogSink(params.getMaxRuntimeLogPerRequest());
    logSink.addHandlerToRootLogger();

    maybeConfigureProfiler(params);

    if (params.getLogJettyExceptionsToAppLogs()) {
      // This system property is checked by JettyLogger, which is
      // instantiated via reflection by Jetty and therefore we cannot
      // pass options into it directly.
      //
      // TODO: This logic should really move down to
      // JettyServletAdapter.
      System.setProperty("appengine.jetty.also_log_to_apiproxy", "true");
    }

    if (params.getUrlfetchDeriveResponseMessage()) {
      // This system property is checked by URLFetch's Connection class, which
      // is directly instantiated by the Java API and cannot take constructor
      // arguments.
      System.setProperty("appengine.urlfetch.deriveResponseMessage", "true");
    }

    if (params.getMailSupportExtendedAttachmentEncodings()) {
      // This system property is checked by GMTransport, which is directly
      // registered with JavaMail and cannot take additional constructor arguments.
      System.setProperty("appengine.mail.supportExtendedAttachmentEncodings", "true");
    }

    if (params.getForceReadaheadOnCloudsqlSocket()) {
      System.setProperty("appengine.jdbc.forceReadaheadOnCloudsqlSocket", "true");
    }

    if (params.getMailFilenamePreventsInlining()) {
      // This system property is checked by GMTransport, which is directly
      // registered with JavaMail and cannot take additional constructor arguments.
      System.setProperty("appengine.mail.filenamePreventsInlining", "true");
    }

    ServletEngineAdapter servletEngine = createServletEngine(params);
    ApiDeadlineOracle deadlineOracle =
        new ApiDeadlineOracle.Builder()
            .initDeadlineMap(
                params.getApiCallDeadline(),
                params.getApiCallDeadlineMap(),
                params.getMaxApiCallDeadline(),
                params.getMaxApiCallDeadlineMap())
            .initOfflineDeadlineMap(
                params.getOfflineApiCallDeadline(),
                params.getOfflineApiCallDeadlineMap(),
                params.getMaxOfflineApiCallDeadline(),
                params.getMaxOfflineApiCallDeadlineMap())
            .build();

    AnyRpcPlugin rpcPlugin = loadRpcPlugin(params);
    rpcPlugin.initialize(params.getPort());
    ApiHostClientFactory apiHostFactory = new ApiHostClientFactory();

    BackgroundRequestCoordinator coordinator = new BackgroundRequestCoordinator();

    CloudDebuggerAgentWrapper cloudDebuggerAgent = new CloudDebuggerAgentWrapperImpl();
    boolean cloudDebuggerEnabled = false;
    if (params.getEnableCloudDebugger()) {
      // Initialize Cloud Debugger (if enabled). This initialization is very lightweight:
      // it only loads a few classes. The bulk of a more expensive initialization is deferred by
      // the debugger to the moment the first breakpoint is set.
      try {
        cloudDebuggerAgent.bind(JavaRuntimeFactory.class.getClassLoader());
        cloudDebuggerEnabled = true;
      } catch (Throwable ex) {
        logger.atWarning().log("Failed to bind to Cloud Debugger agent");
      }
    }

    ApiProxyImpl apiProxyImpl =
        ApiProxyImpl.builder()
            .setApiHost(
                apiHostFactory.newAPIHost(
                    params.getTrustedHost(),
                    OptionalInt.of(params.getCloneMaxOutstandingApiRpcs())))
            .setDeadlineOracle(deadlineOracle)
            .setExternalDatacenterName(params.getExternalDatacenterName())
            .setByteCountBeforeFlushing(params.getByteCountBeforeFlushing())
            .setMaxLogLineSize(params.getMaxLogLineSize())
            .setMaxLogFlushTime(Duration.ofSeconds(params.getMaxLogFlushSeconds()))
            .setCoordinator(coordinator)
            .setCloudSqlJdbcConnectivityEnabled(params.getEnableGaeCloudSqlJdbcConnectivity())
            .setDisableApiCallLogging(params.getDisableApiCallLogging())
            .build();

    RequestManager.Builder requestManagerBuilder =
        RequestManager.builder()
            .setSoftDeadlineDelay(params.getJavaSoftDeadlineMs())
            .setHardDeadlineDelay(params.getJavaHardDeadlineMs())
            .setDisableDeadlineTimers(params.getUseCloneControllerForDeadlines())
            .setRuntimeLogSink(Optional.of(logSink))
            .setApiProxyImpl(apiProxyImpl)
            .setMaxOutstandingApiRpcs(params.getCloneMaxOutstandingApiRpcs())
            .setThreadStopTerminatesClone(params.getThreadStopTerminatesClone())
            .setInterruptFirstOnSoftDeadline(params.getInterruptThreadsFirstOnSoftDeadline())
            .setCloudDebuggerAgent(cloudDebuggerAgent)
            .setEnableCloudDebugger(cloudDebuggerEnabled)
            .setCyclesPerSecond(params.getCyclesPerSecond())
            .setWaitForDaemonRequestThreads(params.getWaitForDaemonRequestThreads());

    RequestManager requestManager = makeRequestManager(requestManagerBuilder);
    apiProxyImpl.setRequestManager(requestManager);

    ApplicationEnvironment.RuntimeConfiguration configuration =
        ApplicationEnvironment.RuntimeConfiguration.builder()
            .setCloudSqlJdbcConnectivityEnabled(params.getEnableGaeCloudSqlJdbcConnectivity())
            .setUseGoogleConnectorJ(params.getDefaultUseGoogleConnectorj())
            .build();

    JavaRuntime.Builder runtimeBuilder =
        JavaRuntime.builder()
            .setServletEngine(servletEngine)
            .setSandboxPlugin(sandboxPlugin)
            .setRpcPlugin(rpcPlugin)
            .setSharedDirectory(new File(params.getApplicationRoot()))
            .setRequestManager(requestManager)
            .setRuntimeVersion("Google App Engine/" + params.getAppengineReleaseName())
            .setConfiguration(configuration)
            .setDeadlineOracle(deadlineOracle)
            .setCoordinator(coordinator)
            .setCompressResponse(params.getRuntimeHttpCompression())
            .setEnableHotspotPerformanceMetrics(params.getEnableHotspotPerformanceMetrics())
            .setCloudDebuggerAgent(cloudDebuggerAgent)
            .setCloudDebuggerEnabled(cloudDebuggerEnabled)
            .setPollForNetwork(params.getPollForNetwork())
            .setDefaultToNativeUrlStreamHandler(params.getDefaultToNativeUrlStreamHandler())
            .setForceUrlfetchUrlStreamHandler(params.getForceUrlfetchUrlStreamHandler())
            .setIgnoreDaemonThreads(!params.getWaitForDaemonRequestThreads())
            .setUseEnvVarsFromAppInfo(params.getUseEnvVarsFromAppInfo())
            .setFixedApplicationPath(params.getFixedApplicationPath())
            .setRedirectStdoutStderr(!params.getUseJettyHttpProxy())
            .setLogJsonToFile(params.getLogJsonToVarLog());

    JavaRuntime runtime = makeRuntime(runtimeBuilder);

    ApiProxy.setDelegate(apiProxyImpl);
    ServletEngineAdapter.Config runtimeOptions =
        ServletEngineAdapter.Config.builder()
            .setUseJettyHttpProxy(params.getUseJettyHttpProxy())
            .setApplicationRoot(params.getApplicationRoot())
            .setFixedApplicationPath(params.getFixedApplicationPath())
            .setJettyHttpAddress(HostAndPort.fromParts("0.0.0.0", params.getJettyHttpPort()))
            .setJettyRequestHeaderSize(params.getJettyRequestHeaderSize())
            .setJettyResponseHeaderSize(params.getJettyResponseHeaderSize())
            .setEvaluationRuntimeServerInterface(runtime)
            .build();
    try {
      runtime.start(runtimeOptions);
    } catch (Exception e) {
      try {
        runtime.stop();
      } catch (Throwable th) {
        // Swallow this exception -- the other one is what matters.
      }
      throw new RuntimeException("Could not start server", e);
    }
    return runtime;
  }

  public JavaRuntime makeRuntime(JavaRuntime.Builder runtimeBuilder) {
    return runtimeBuilder.build();
  }

  public RequestManager makeRequestManager(RequestManager.Builder builder) {
    return builder.build();
  }

  private static AnyRpcPlugin loadRpcPlugin(JavaRuntimeParams params) {
    if (params.getUseJettyHttpProxy()) {
      return new NullRpcPlugin();
    }
    try {
      Class<? extends AnyRpcPlugin> pluginClass =
          Class.forName("com.google.apphosting.runtime.grpc.GrpcPlugin")
              .asSubclass(AnyRpcPlugin.class);
      Constructor<? extends AnyRpcPlugin> pluginConstructor = pluginClass.getConstructor();
      return pluginConstructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to load RPC plugin", e);
    }
  }

  /** Creates the ServletEngineAdapter specifies by the --servlet_engine flag. */
  private static ServletEngineAdapter createServletEngine(JavaRuntimeParams params) {
    Class<? extends ServletEngineAdapter> engineClazz = params.getServletEngine();
    if (engineClazz == null) {
      throw new RuntimeException("No servlet engine (--servlet_engine) defined in the parameters.");
    }
    try {
      return engineClazz.getConstructor().newInstance();
    } catch (ReflectiveOperationException ex) {
      throw new RuntimeException("Failed to instantiate " + engineClazz, ex);
    }
  }

  private static void maybeConfigureProfiler(JavaRuntimeParams params) {
    if (params.getEnableCloudCpuProfiler() || params.getEnableCloudHeapProfiler()) {
      try {
        Class<?> profilerClass = Class.forName("com.google.cloud.profiler.agent.Profiler");
        Class<?> profilerConfigClass =
            Class.forName("com.google.cloud.profiler.agent.Profiler$Config");
        Object profilerConfig = profilerConfigClass.getConstructor().newInstance();
        Method setCpuProfilerEnabled =
            profilerConfigClass.getMethod("setCpuProfilerEnabled", boolean.class);
        Method setHeapProfilerEnabled =
            profilerConfigClass.getMethod("setHeapProfilerEnabled", boolean.class);
        setCpuProfilerEnabled.invoke(profilerConfig, params.getEnableCloudCpuProfiler());
        setHeapProfilerEnabled.invoke(profilerConfig, params.getEnableCloudHeapProfiler());
        Method start = profilerClass.getMethod("start", profilerConfigClass);
        start.invoke(null, profilerConfig);
      } catch (Exception e) {
        logger.atWarning().withCause(e).log("Failed to start the profiler");
      }
    }
  }
}
