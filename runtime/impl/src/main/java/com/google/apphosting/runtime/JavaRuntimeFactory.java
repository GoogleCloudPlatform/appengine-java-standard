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

import static com.google.apphosting.runtime.AppEngineConstants.BYTE_COUNT_BEFORE_FLUSHING;
import static com.google.apphosting.runtime.AppEngineConstants.CYCLES_PER_SECOND;
import static com.google.apphosting.runtime.AppEngineConstants.FORCE_URLFETCH_URL_STREAM_HANDLER;
import static com.google.apphosting.runtime.AppEngineConstants.JETTY_REQUEST_HEADER_SIZE;
import static com.google.apphosting.runtime.AppEngineConstants.JETTY_RESPONSE_HEADER_SIZE;
import static com.google.apphosting.runtime.AppEngineConstants.MAX_LOG_FLUSH_TIME;
import static com.google.apphosting.runtime.AppEngineConstants.MAX_LOG_LINE_SIZE;
import static com.google.apphosting.runtime.AppEngineConstants.MAX_RUNTIME_LOG_PER_REQUEST;
import static com.google.apphosting.runtime.AppEngineConstants.SOFT_DEADLINE_DELAY_MS;
import static com.google.apphosting.runtime.AppEngineConstants.THREAD_STOP_TERMINATES_CLONE;

import com.google.apphosting.api.ApiProxy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.VerifyException;
import com.google.common.flogger.GoogleLogger;
import com.google.common.net.HostAndPort;
import java.io.File;
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

  @VisibleForTesting
  JavaRuntimeFactory() {}

  public static void startRuntime(NullSandboxPlugin sandboxPlugin, String[] args) {
    JavaRuntimeFactory factory = new JavaRuntimeFactory();
    factory.getStartedRuntime(sandboxPlugin, args);
  }

  public JavaRuntime getStartedRuntime(NullSandboxPlugin sandboxPlugin, String[] args) {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    List<String> unknownParams = params.unknownParams();
    if (!unknownParams.isEmpty()) {
      logger.atWarning().log("Unknown command line arguments: %s", unknownParams);
    }

    RuntimeLogSink logSink = new RuntimeLogSink(MAX_RUNTIME_LOG_PER_REQUEST);
    logSink.addHandlerToRootLogger();

    // Cloud Profiler is now set up externally, typically at the application entry point.
    // See:
    // https://github.com/GoogleCloudPlatform/appengine-java-standard?tab=readme-ov-file#entry-point-features

    // This system property is checked by JettyLogger, which is
    // instantiated via reflection by Jetty and therefore we cannot
    // pass options into it directly.
    //
    // TODO: This logic should really move down to
    // JettyServletAdapter.
    System.setProperty("appengine.jetty.also_log_to_apiproxy", "true");

    // This system property is always set to true and is checked by URLFetch's Connection class,
    // which is directly instantiated by the Java API and cannot take constructor arguments.
    System.setProperty("appengine.urlfetch.deriveResponseMessage", "true");

    // This system property is checked by GMTransport, which is directly
    // registered with JavaMail and cannot take additional constructor arguments.
    System.setProperty("appengine.mail.supportExtendedAttachmentEncodings", "true");

    System.setProperty("appengine.jdbc.forceReadaheadOnCloudsqlSocket", "true");

    // This system property is checked by GMTransport, which is directly
    // registered with JavaMail and cannot take additional constructor arguments.
    System.setProperty("appengine.mail.filenamePreventsInlining", "true");

    ServletEngineAdapter servletEngine = createServletEngine();
    ApiDeadlineOracle deadlineOracle = new ApiDeadlineOracle.Builder().initDeadlineMap().build();

    ApiHostClientFactory apiHostFactory = new ApiHostClientFactory();

    BackgroundRequestCoordinator coordinator = new BackgroundRequestCoordinator();

    // The disableApiCallLogging setting is controlled by a system property,
    // which is consistent with other runtime settings configured via system properties.
    // If command-line parameter control is also needed, this logic should be updated
    // to potentially check a value parsed from 'args' via JavaRuntimeParams.
    boolean disableApiCallLogging = Boolean.getBoolean("disable_api_call_logging_in_apiproxy");

    ApiProxyImpl apiProxyImpl =
        ApiProxyImpl.builder()
            .setApiHost(
                apiHostFactory.newAPIHost(
                    params.trustedHost(), OptionalInt.of(params.maxOutstandingApiRpcs())))
            .setDeadlineOracle(deadlineOracle)
            .setExternalDatacenterName("MARS")
            .setByteCountBeforeFlushing(BYTE_COUNT_BEFORE_FLUSHING)
            .setMaxLogLineSize(MAX_LOG_LINE_SIZE)
            .setMaxLogFlushTime(MAX_LOG_FLUSH_TIME)
            .setCoordinator(coordinator)
            .setDisableApiCallLogging(disableApiCallLogging)
            .build();

    RequestManager.Builder requestManagerBuilder =
        RequestManager.builder()
            .setSoftDeadlineDelay(SOFT_DEADLINE_DELAY_MS)
            .setRuntimeLogSink(Optional.of(logSink))
            .setApiProxyImpl(apiProxyImpl)
            .setMaxOutstandingApiRpcs(params.maxOutstandingApiRpcs())
            .setThreadStopTerminatesClone(THREAD_STOP_TERMINATES_CLONE)
            .setCyclesPerSecond(CYCLES_PER_SECOND);

    RequestManager requestManager = makeRequestManager(requestManagerBuilder);
    apiProxyImpl.setRequestManager(requestManager);

    ApplicationEnvironment.RuntimeConfiguration configuration =
        ApplicationEnvironment.RuntimeConfiguration.builder().build();

    JavaRuntime.Builder runtimeBuilder =
        JavaRuntime.builder()
            .setServletEngine(servletEngine)
            .setSandboxPlugin(sandboxPlugin)
            .setSharedDirectory(new File("notused"))
            .setRequestManager(requestManager)
            .setRuntimeVersion("Google App Engine/" + "mainwithdefaults")
            .setConfiguration(configuration)
            .setDeadlineOracle(deadlineOracle)
            .setCoordinator(coordinator)
            .setForceUrlfetchUrlStreamHandler(FORCE_URLFETCH_URL_STREAM_HANDLER)
            .setFixedApplicationPath(params.fixedApplicationPath());

    JavaRuntime runtime = makeRuntime(runtimeBuilder);

    ApiProxy.setDelegate(apiProxyImpl);
    ServletEngineAdapter.Config runtimeOptions =
        ServletEngineAdapter.Config.builder()
            .setApplicationRoot("notused")
            .setFixedApplicationPath(params.fixedApplicationPath())
            .setJettyHttpAddress(HostAndPort.fromParts("0.0.0.0", params.jettyHttpPort()))
            .setJettyRequestHeaderSize(JETTY_REQUEST_HEADER_SIZE)
            .setJettyResponseHeaderSize(JETTY_RESPONSE_HEADER_SIZE)
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

  /** Creates the ServletEngineAdapter. */
  private static ServletEngineAdapter createServletEngine() {
    String servletEngine;
    if (Boolean.getBoolean("appengine.use.EE8")
        || Boolean.getBoolean("appengine.use.EE10")
        || Boolean.getBoolean("appengine.use.EE11")) {
      servletEngine = "com.google.apphosting.runtime.jetty.JettyServletEngineAdapter";
    } else {
      servletEngine = "com.google.apphosting.runtime.jetty9.JettyServletEngineAdapter";
    }

    try {
      Class<? extends ServletEngineAdapter> engineClazz =
          Class.forName(servletEngine).asSubclass(ServletEngineAdapter.class);
      return engineClazz.getConstructor().newInstance();
    } catch (ReflectiveOperationException ex) {
      throw new VerifyException("Failed to instantiate " + servletEngine, ex);
    }
  }
}
