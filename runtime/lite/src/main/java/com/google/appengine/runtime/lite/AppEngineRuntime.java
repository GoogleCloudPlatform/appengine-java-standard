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

package com.google.appengine.runtime.lite;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppId;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.runtime.ApiDeadlineOracle;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.ApplicationEnvironment;
import com.google.apphosting.runtime.RequestManager;
import com.google.apphosting.runtime.SessionsConfig;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.http.HttpApiHostClientFactory;
import com.google.apphosting.runtime.jetty94.AppEngineWebAppContext;
import com.google.apphosting.runtime.jetty94.AppInfoFactory;
import com.google.apphosting.runtime.jetty94.AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty94.JettyServerConnectorWithReusePort;
import com.google.apphosting.runtime.jetty94.WebAppContextFactory;
import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.net.HostAndPort;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

/**
 * AppEngineRuntime is a simplified fork of {@link com.google.apphosting.runtime.JavaRuntime}.
 *
 * <p>This class dispenses with some backwards compatibility features of the main JavaRuntime for
 * simplicity and efficiency.
 */
public class AppEngineRuntime {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final String ENV_PORT = "PORT";
  private static final String ENV_API_HOST = "API_HOST";
  private static final String ENV_API_PORT = "API_PORT";
  private static final String RUNTIME_VERSION = "lite";
  private static final int JETTY_HTTP_REQUEST_HEADER_SIZE = 262144;
  private static final int JETTY_HTTP_RESPONSE_HEADER_SIZE = 262144;
  private static final int HARD_DEADLINE_MS = 10200;
  private static final int SOFT_DEADLINE_MS = 10600;
  private static final String EXTERNAL_DATACENTER_NAME = "MARS";
  private static final int CLONE_MAX_OUTSTANDING_API_RPCS = 100;
  private static final long DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT = 100 * 1024L;
  private static final Duration MAX_LOG_FLUSH_TIME = Duration.ofMinutes(1);
  private static final long CYCLES_PER_SECOND = 1000000000L;

  private final ApiProxyImpl apiProxyImpl;

  private final AppinfoPb.AppInfo appInfo;

  private final AppVersion appVersion;

  private final Server server;

  private final boolean allowWebInfJars;

  /** Builder for AppEngineRuntime. */
  @AutoBuilder
  public abstract static class Builder {
    /**
     * The location of the web app. This could be a WAR file, or an unzipped version of the same.
     * (Generally, this is a zip file which contains a WEB-INF/ directory, or the parent directory
     * of WEB-INF/.)
     */
    public abstract Builder setServletWebappPath(Path x);

    /** The local TCP address at which the app's HTTP server will listen. */
    public abstract Builder setListenAddress(HostAndPort x);

    /** Whether the HTTP server opens its connection using SO_REUSEPORT. */
    public abstract Builder setReusePort(boolean x);

    /** The address of the App Engine API host HTTP server. */
    public abstract Builder setApiHostAddress(HostAndPort x);

    /**
     * Listeners which will be added to the {@link org.eclipse.jetty.webapp.WebAppContext}.
     *
     * <p>This can be used to programmatically add ServletContextListeners (instead of configuring
     * them in web.xml).
     */
    abstract ImmutableList.Builder<EventListener> listenersBuilder();

    @CanIgnoreReturnValue
    public final Builder addListener(EventListener listener) {
      listenersBuilder().add(listener);
      return this;
    }

    /**
     * Configures App Engine's optional implementation of {@link javax.servlet.http.HttpSession}
     * support. Defaults to no session support.
     */
    public abstract Builder setSessionsConfig(SessionsConfig x);

    /** Configures the location of static files within the web archive. Defaults to /. */
    public abstract Builder setPublicRoot(String x);

    /**
     * If false, we fail if any jars are found in WEB-INF/lib or any content is found in
     * WEB-INF/classes. If true, we ignore them.
     *
     * <p>This class does not itself add those locations to the Java classpath, so presence of any
     * code in those locations is almost certainly a configuration error.
     */
    public abstract Builder setAllowWebInfJars(boolean x);

    public abstract AppEngineRuntime build();
  }

  public static Builder builder() {
    return builderFromEnv(System.getenv());
  }

  public static Builder builderFromEnv(Map<String, String> env) {
    return new AutoBuilder_AppEngineRuntime_Builder()
        .setAllowWebInfJars(false)
        .setListenAddress(
            HostAndPort.fromParts(
                "::", Integer.parseInt(Optional.ofNullable(env.get(ENV_PORT)).orElse("8080"))))
        .setReusePort(false) // Whether to set SO_REUSEPORT when binding to the above listen port
        .setApiHostAddress(
            HostAndPort.fromParts(
                // Host to connect to for API calls:
                Optional.ofNullable(env.get(ENV_API_HOST)).orElse("appengine.googleapis.internal"),
                // Port to connect to for API calls:
                Integer.parseInt(Optional.ofNullable(env.get(ENV_API_PORT)).orElse("10001"))));
  }

  AppEngineRuntime(
      Path servletWebappPath,
      HostAndPort listenAddress,
      boolean reusePort,
      HostAndPort apiHostAddress,
      ImmutableList<EventListener> listeners,
      Optional<SessionsConfig> sessionsConfig,
      Optional<String> publicRoot,
      boolean allowWebInfJars) {
    this.allowWebInfJars = allowWebInfJars;

    BackgroundRequestDispatcher backgroundRequestDispatcher = new BackgroundRequestDispatcher();

    apiProxyImpl = makeApiProxyImplBuilder(apiHostAddress, backgroundRequestDispatcher).build();

    RequestManager requestManager = makeRequestManagerBuilder(apiProxyImpl).build();

    apiProxyImpl.setRequestManager(requestManager);

    AppInfoFactory appInfoFactory = new AppInfoFactory(System.getenv());
    appInfo = appInfoFactory.getAppInfoWithApiVersion("user_defined");

    this.appVersion = createAppVersion(servletWebappPath, appInfo, sessionsConfig, publicRoot);

    WebAppContextFactory webAppContextFactory =
        (AppVersion appVersion, String serverInfo) -> {
          AppEngineWebAppContext context =
              new AppEngineWebAppContext(
                  appVersion.getRootDirectory(), serverInfo, /*extractWar=*/ false);
          listeners.forEach(context::addEventListener);

          return context;
        };

    // Construct the Jetty server:
    server = new Server();

    // Create a factory which instantiates the app:
    AppVersionHandlerFactory handlerFactory =
        new AppVersionHandlerFactory(
            server,
            "Google App Engine/" + RUNTIME_VERSION,
            webAppContextFactory,
            /*useJettyErrorPageHandler=*/ true);

    RequestHandler requestHandler =
        new RequestHandler(
            this.appVersion,
            handlerFactory,
            requestManager,
            appInfoFactory,
            backgroundRequestDispatcher.createHandler());

    ServerConnector c = new JettyServerConnectorWithReusePort(server, reusePort);
    c.setHost(listenAddress.getHost());
    c.setPort(listenAddress.getPort());
    server.setConnectors(new Connector[] {c});

    HttpConfiguration config =
        c.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
    config.setRequestHeaderSize(JETTY_HTTP_REQUEST_HEADER_SIZE);
    config.setResponseHeaderSize(JETTY_HTTP_RESPONSE_HEADER_SIZE);
    config.setSendServerVersion(false);

    GzipHandler gzip = new GzipHandler();
    gzip.setIncludedMethods("GET", "POST");
    gzip.setInflateBufferSize(8 * 1024);
    server.setHandler(gzip);
    gzip.setHandler(requestHandler);

    logger.atInfo().log("Starting Jetty http server for Java runtime proxy.");
  }

  /** Start the runtime. Has various side effects on System properties and thread-local state. */
  public RunningRuntime run() throws Exception {
    return new RunningRuntime();
  }

  /** Represents a running (actively serving) App Engine runtime. */
  public class RunningRuntime implements AutoCloseable {
    private RunningRuntime() throws Exception {
      ApplicationEnvironment env = appVersion.getEnvironment();

      // Any apps putting things into WEB-INF/lib or WEB-INF/classes will have a bad time since
      // those directories are ignored in production, so it's best to proactively warn the app
      // owner.
      // As a pragmatic convenience, verify WEB-INF/lib and WEB-INF/classes are empty:
      if (!allowWebInfJars) {
        WebappJarBanner.checkWebappPath(env.getRootDirectory().toPath());
      }

      AppId appId = AppId.parse(appInfo.getAppId());
      System.setProperty(SystemProperty.applicationId.key(), appId.getLongAppId());
      System.setProperty(SystemProperty.applicationVersion.key(), appInfo.getVersionId());

      System.setProperty("appengine.api.urlfetch.defaultTlsValidation", "true");
      System.setProperty("java.awt.headless", "true");
      System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
      System.setProperty("user.timezone", "UTC");
      if (System.getProperty(SystemProperty.environment.key()) == null) {
        String val;
        if ("localdev".equals(System.getenv("GAE_ENV"))) {
          // GAE_ENV=localdev is the documented flag for emulated environments, and best matches
          // legacy expectations of application test code written for DevAppServer (v1) and the
          // AppLauncher.
          // Such code expects this system property to say "Development" (which is an ambiguous name
          // since most applications *also* do development in an on-GCP project; this should really
          // be called "Emulation", but use of "Development" is far too prevalent to change this
          // now).
          // Of course it's generally best practice to not rely on this flag at all. We'd advise
          // instead using explicit configuration options and code construction to enable
          // development-only behaviors.
          val = SystemProperty.Environment.Value.Development.value();
        } else {
          val = SystemProperty.Environment.Value.Production.value();
        }
        System.setProperty(SystemProperty.environment.key(), val);
      }
      System.setProperty(SystemProperty.version.key(), "lite");

      // This system property is checked by URLFetch's Connection class, which
      // is directly instantiated by the Java API and cannot take constructor
      // arguments.
      System.setProperty("appengine.urlfetch.deriveResponseMessage", "true");

      // This system property is checked by GMTransport, which is directly
      // registered with JavaMail and cannot take additional constructor arguments.
      System.setProperty("appengine.mail.supportExtendedAttachmentEncodings", "true");

      // This system property is checked by GMTransport, which is directly
      // registered with JavaMail and cannot take additional constructor arguments.
      System.setProperty("appengine.mail.filenamePreventsInlining", "true");

      ApiProxy.setDelegate(apiProxyImpl);

      logger.atInfo().log("AppEngineRuntime starting...");

      try {
        server.start();
      } catch (Throwable ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt(); // Restore the interrupted status
        }
        close();
        throw new IOException("Failed to start AppEngineRuntime", ex);
      }
    }

    @Override
    public void close() throws Exception {
      server.stop();
      server.join();
    }

    public void join() throws InterruptedException {
      server.join();
    }
  }

  static ApiProxyImpl.Builder makeApiProxyImplBuilder(
      HostAndPort apiHostAddress, BackgroundRequestDispatcher dispatcher) {
    ApiDeadlineOracle deadlineOracle = DeadlineOracleFactory.create();

    APIHostClientInterface apiHost =
        HttpApiHostClientFactory.create(
            apiHostAddress, OptionalInt.of(CLONE_MAX_OUTSTANDING_API_RPCS));

    return ApiProxyImpl.builder()
        .setDeadlineOracle(deadlineOracle)
        .setExternalDatacenterName(EXTERNAL_DATACENTER_NAME)
        .setByteCountBeforeFlushing(DEFAULT_FLUSH_APP_LOGS_EVERY_BYTE_COUNT)
        .setMaxLogFlushTime(MAX_LOG_FLUSH_TIME)
        .setCoordinator(dispatcher)
        .setCloudSqlJdbcConnectivityEnabled(true)
        .setLogToLogservice(false)
        .setDisableApiCallLogging(true)
        .setApiHost(apiHost);
  }

  static RequestManager.Builder makeRequestManagerBuilder(ApiProxyImpl apiProxyImpl) {
    return RequestManager.builder()
        .setSoftDeadlineDelay(SOFT_DEADLINE_MS)
        .setHardDeadlineDelay(HARD_DEADLINE_MS)
        .setDisableDeadlineTimers(true)
        .setRuntimeLogSink(Optional.empty())
        .setApiProxyImpl(apiProxyImpl)
        .setMaxOutstandingApiRpcs(CLONE_MAX_OUTSTANDING_API_RPCS)
        .setThreadStopTerminatesClone(true)
        .setInterruptFirstOnSoftDeadline(true)
        .setCloudDebuggerAgent(null)
        .setEnableCloudDebugger(false)
        .setCyclesPerSecond(CYCLES_PER_SECOND)
        .setWaitForDaemonRequestThreads(false);
  }

  static AppVersion createAppVersion(
      Path webappPath,
      AppinfoPb.AppInfo appInfo,
      Optional<SessionsConfig> sessionsConfig,
      Optional<String> publicRoot) {

    File rootDirectory = webappPath.toFile();

    ApplicationEnvironment.RuntimeConfiguration runtimeConfiguration =
        ApplicationEnvironment.RuntimeConfiguration.builder()
            .setCloudSqlJdbcConnectivityEnabled(true)
            .setUseGoogleConnectorJ(true)
            .build();

    ApplicationEnvironment environment =
        new ApplicationEnvironment(
            appInfo.getAppId(),
            appInfo.getVersionId(),
            // In the "lite" runtime, we deal with system properties mostly on our own:
            /* extraSystemProperties=*/ new HashMap<>(),
            // In the "lite" runtime, env vars are taken from appengine-web.xml, converted to
            // deploy.yaml, and set by the App Engine servinf before the container even starts. We
            // can ignore them here:
            /* environmentVariables=*/ new HashMap<>(),
            rootDirectory,
            runtimeConfiguration);

    AppVersionKey appVersionKey = AppVersionKey.fromAppInfo(appInfo);

    return AppVersion.builder()
        .setAppVersionKey(appVersionKey)
        .setAppInfo(appInfo)
        .setRootDirectory(rootDirectory)
        .setClassLoader(ClassLoader.getSystemClassLoader())
        .setEnvironment(environment)
        .setSessionsConfig(sessionsConfig.orElseGet(() -> new SessionsConfig(false, false, null)))
        .setPublicRoot(publicRoot.orElse(""))
        .build();
  }

  /** For use with {@link Threading.setDefaultUncaughtExceptionHandler}. */
  public static void handleUncaughtException(Thread thread, Throwable ex) {
    logger.atWarning().withCause(ex).log("Uncaught exception from %s", thread);
  }
}
