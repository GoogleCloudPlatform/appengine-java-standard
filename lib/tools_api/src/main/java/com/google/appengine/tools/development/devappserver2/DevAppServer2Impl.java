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

package com.google.appengine.tools.development.devappserver2;

import com.google.appengine.tools.development.AppContext;
import com.google.appengine.tools.development.ApplicationConfigurationManager;
import com.google.appengine.tools.development.ContainerService;
import com.google.appengine.tools.development.ContainerUtils;
import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerDatastorePropertyHelper;
import com.google.appengine.tools.development.DevAppServerPortPropertyHelper;
import com.google.appengine.tools.development.EnvironmentVariableChecker.MismatchReportingPolicy;
import com.google.appengine.tools.development.Modules;
import com.google.appengine.tools.development.StreamHandlerFactory;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.EarHelper;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.BindException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code DevAppServer} launches a local Jetty server (by default) with a single
 * hosted web application.  It can be invoked from the command-line by
 * providing the path to the directory in which the application resides as the
 * only argument.
 *
 */
class DevAppServer2Impl implements DevAppServer {
  private static final Logger logger = Logger.getLogger(DevAppServer2Impl.class.getName());
  private final ApplicationConfigurationManager applicationConfigurationManager;
  private final Modules modules;
  private Map<String, String> serviceProperties = new HashMap<String, String>();
  private final Map<String, Object> containerConfigProperties;
  private final int requestedPort;
  private final RemoteApiOptions remoteApiOptions;
  private final String webDefaultXml;

  enum ServerState { INITIALIZING, RUNNING, STOPPING, SHUTDOWN }

  /**
   * The current state of the server.
   */
  private ServerState serverState = ServerState.INITIALIZING;

  /**
   * We defer reporting construction time configuration exceptions until
   * {@link #start()} for compatibility.
   */
  private final AppEngineConfigException configurationException;

  /**
   * Used to schedule the graceful shutdown of the server.
   */
  private final ScheduledExecutorService shutdownScheduler = Executors.newScheduledThreadPool(1);

  /**
   * Latch that we decrement when the server is shutdown or restarted.
   * Will be {@code null} until the server is started.
   */
  private CountDownLatch shutdownLatch = null;

  /**
   * The {@Link ApiProxy.Delegate}.
   */
  private DevAppServer2Delegate devAppServer2Delegate;

  /**
   * Constructs a development application server that runs the application located in the given
   * WAR or EAR directory.
   *
   * @param appDir The location of the application to run.
   * @param externalResourceDir If not {@code null}, a resource directory external to the appDir.
   *     This will be searched before appDir when looking for resources.
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  If {@code null},
   *     defaults to {@literal <appDir>/WEB-INF/web.xml}
   * @param appEngineWebXmlLocation The name of the app engine config file.  If
   *     {@code null}, defaults to {@literal <appDir>/WEB-INF/appengine-web.xml}
   * @param address The address on which to run
   * @param port The port on which to run
   * @param useCustomStreamHandler If {@code true} (typical), install {@link StreamHandlerFactory}.
   * @param containerConfigProperties Additional properties used in the
   *     configuration of the specific container implementation.
   */
  DevAppServer2Impl(File appDir, File externalResourceDir, File webXmlLocation,
      File appEngineWebXmlLocation, String address, int port, boolean useCustomStreamHandler,
      Map<String, ?> containerConfigProperties) {
      webDefaultXml =
          "com/google/appengine/tools/development/devappserver2/webdefault/jetty9/webdefault.xml";

    String serverInfo = ContainerUtils.getServerInfo();
    if (useCustomStreamHandler) {
      StreamHandlerFactory.install();
    }

    String remoteApiHost = (String) containerConfigProperties.get("com.google.appengine.apiHost");
    int remoteApiPort = (Integer) containerConfigProperties.get("com.google.appengine.apiPort");
    this.remoteApiOptions = new RemoteApiOptions()
        .server(remoteApiHost, remoteApiPort)
        .credentials("test@example.com", "ignoredpassword");

    requestedPort = port;
    ApplicationConfigurationManager tempManager;
    AppengineSdk sdk = AppengineSdk.getSdk();
    File schemaFile = new File(sdk.getResourcesDirectory(), "appengine-application.xsd");

    try {
      if (EarHelper.isEar(appDir.getAbsolutePath())) {
        tempManager =
            ApplicationConfigurationManager.newEarConfigurationManager(
                appDir, sdk.getLocalVersion().getRelease(), schemaFile, "dev~");
       String contextRootWarning =
            "Ignoring application.xml context-root element, for details see "
             + "https://developers.google.com/appengine/docs/java/modules/#config";
        logger.info(contextRootWarning);
      } else {
        tempManager =
            ApplicationConfigurationManager.newWarConfigurationManager(
                appDir,
                appEngineWebXmlLocation,
                webXmlLocation,
                externalResourceDir,
                sdk.getLocalVersion().getRelease(),
                "dev~");
      }
    } catch (AppEngineConfigException configurationException) {
      modules = null;
      applicationConfigurationManager = null;
      this.containerConfigProperties = null;
      this.configurationException = configurationException;
      return;
    }
    this.applicationConfigurationManager = tempManager;
    this.modules = Modules.createModules(applicationConfigurationManager, serverInfo,
        externalResourceDir, address, this);
    this.containerConfigProperties = ImmutableMap.copyOf(containerConfigProperties);
    configurationException = null;
  }

  /**
   * Sets the properties that will be used by the local services to
   * configure themselves. This method must be called before the server
   * has been started.
   *
   * @param properties a, maybe {@code null}, set of properties.
   *
   * @throws IllegalStateException if the server has already been started.
   */
  @Override
  public void setServiceProperties(Map<String, String> properties) {
    if (serverState != ServerState.INITIALIZING) {
      String msg = "Cannot set service properties after the server has been started.";
      throw new IllegalStateException(msg);
    }

    if (configurationException == null) {
      // Copy the new properties into our own so that we know our map is mutable.
      serviceProperties = new ConcurrentHashMap<String, String>(properties);
      serviceProperties.put("appengine.webdefault.xml", webDefaultXml);
      if (requestedPort != 0) {
        DevAppServerPortPropertyHelper.setPort(modules.getMainModule().getModuleName(),
            requestedPort, serviceProperties);
      }
      DevAppServerDatastorePropertyHelper.setDefaultProperties(serviceProperties);
    }
  }

  @Override
  public Map<String, String> getServiceProperties() {
    return serviceProperties;
  }

  /**
   * Starts the server.
   *
   * @throws IllegalStateException If the server has already been started or
   *     shutdown.
   * @throws AppEngineConfigException If no WEB-INF directory can be found or
   *     WEB-INF/appengine-web.xml does not exist.
   * @return a latch that will be decremented to zero when the server is shutdown.
   */
  @Override
  public CountDownLatch start() throws Exception {
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<CountDownLatch>() {
        @Override public CountDownLatch run() throws Exception {
          return doStart();
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  private CountDownLatch doStart() throws Exception {
    if (serverState != ServerState.INITIALIZING) {
      throw new IllegalStateException("Cannot start a server that has already been started.");
    }

    reportDeferredConfigurationException();

    initializeLogging();
    modules.configure(containerConfigProperties);
    try {
      modules.createConnections();
    } catch (BindException ex) {
      System.err.println();
      System.err.println("************************************************");
      System.err.println("Could not open the requested socket: " + ex.getMessage());
      System.err.println("Try overriding --address and/or --port.");
      System.exit(2);
    }

    devAppServer2Delegate = new DevAppServer2Delegate(remoteApiOptions);
    ApiProxy.setDelegate(devAppServer2Delegate);

    TimeZone currentTimeZone = null;
    try {
      currentTimeZone = setServerTimeZone();
      modules.setApiProxyDelegate(devAppServer2Delegate);
      modules.startup();
    } finally {
      restoreLocalTimeZone(currentTimeZone);
    }
    shutdownLatch = new CountDownLatch(1);
    serverState = ServerState.RUNNING;
    // If you change this please also update
    // com.google.watr.client.deployment.DevAppServerDeployment.DevAppServerMonitor.
    logger.info("Dev App Server is now running");
    return shutdownLatch;
  }

  /**
   * Sets the default TimeZone to UTC if no time zone is given by the user via the
   * "appengine.user.timezone.impl" property. By calling this method before
   * {@link ContainerService#startup()} start}, we set the default TimeZone for the
   * DevAppServer and all of its related services.
   *
   * @return the previous TimeZone
   */
  private TimeZone setServerTimeZone() {
    // Don't set the TimeZone if the user explicitly set it
    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return null;
    }
    TimeZone utc = TimeZone.getTimeZone("UTC");
    assert utc.getID().equals("UTC") : "Unable to retrieve the UTC TimeZone";
    TimeZone previousZone = TimeZone.getDefault();
    TimeZone.setDefault(utc);
    return previousZone;
  }

  /**
   * Restores the TimeZone to {@code timeZone}.
   */
  private void restoreLocalTimeZone(TimeZone timeZone) {
    // Don't set the TimeZone if the user explicitly set it
    String sysTimeZone = serviceProperties.get("appengine.user.timezone.impl");
    if (sysTimeZone != null && sysTimeZone.trim().length() > 0) {
      return;
    }
    TimeZone.setDefault(timeZone);
  }

  @Override
  public CountDownLatch restart() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot restart a server that is not currently running.");
    }
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<CountDownLatch>() {
        @Override public CountDownLatch run() throws Exception {
          modules.shutdown();
          shutdownLatch.countDown();
          modules.createConnections();
          modules.setApiProxyDelegate(devAppServer2Delegate);
          modules.startup();
          shutdownLatch = new CountDownLatch(1);
          return shutdownLatch;
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  @Override
  public void shutdown() throws Exception {
    if (serverState != ServerState.RUNNING) {
      throw new IllegalStateException("Cannot shutdown a server that is not currently running.");
    }
    try {
      AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
        @Override public Void run() throws Exception {
          modules.shutdown();
          ApiProxy.setDelegate(null);
          serverState = ServerState.SHUTDOWN;
          shutdownLatch.countDown();
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }

  @Override
  public void gracefulShutdown() throws IllegalStateException {
    // TODO: Do an actual graceful shutdown rather than just delaying.

    // Requires a privileged block since this may be invoked from a servlet
    // that lives in the user's classloader and may result in the creation of
    // a thread.
    AccessController.doPrivileged(new PrivilegedAction<Future<Void>>() {
      @Override
      public Future<Void> run() {
        return shutdownScheduler.schedule(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            shutdown();
            return null;
          }
        }, 1000, TimeUnit.MILLISECONDS);
      }
    });
  }

  @Override
  public int getPort() {
    reportDeferredConfigurationException();
    return modules.getMainModule().getMainContainer().getPort();
  }

  protected void reportDeferredConfigurationException() {
    if (configurationException != null) {
      throw new AppEngineConfigException("Invalid configuration", configurationException);
    }
  }

  @Override
  public AppContext getAppContext() {
    reportDeferredConfigurationException();
    return modules.getMainModule().getMainContainer().getAppContext();
  }

  @Override
  public AppContext getCurrentAppContext() {
    AppContext result = null;
    Environment env = ApiProxy.getCurrentEnvironment();
    //Some tests create environments with null version id's
    if (env != null && env.getVersionId() != null) {
      String moduleName = env.getModuleId();
      result = modules.getModule(moduleName).getMainContainer().getAppContext();
    }
    return result;
  }

  @Override
  public void setThrowOnEnvironmentVariableMismatch(boolean throwOnMismatch) {
    if (configurationException == null) {
      applicationConfigurationManager.setEnvironmentVariableMismatchReportingPolicy(
          throwOnMismatch ? MismatchReportingPolicy.EXCEPTION : MismatchReportingPolicy.LOG);
    }
  }

  /**
   * We're happy with the default logging behavior, which is to
   * install a {@link ConsoleHandler} at the root level.  The only
   * issue is that we want its level to be FINEST to be consistent
   * with our runtime environment.
   *
   * <p>Note that this does not mean that any fine messages will be
   * logged by default -- each Logger still defaults to INFO.
   * However, it is sufficient to call {@link Logger#setLevel(Level)}
   * to adjust the level.
   */
  private void initializeLogging() {
    for (Handler handler : Logger.getLogger("").getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        handler.setLevel(Level.FINEST);
      }
    }
  }
}
