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

package com.google.appengine.tools.development;

import com.google.appengine.api.modules.dev.LocalModulesService;
import com.google.appengine.tools.development.EnvironmentVariableChecker.MismatchReportingPolicy;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.EarHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
class DevAppServerImpl implements DevAppServer {
  // Keep this in sync with
  // com.google.apphosting.tests.usercode.testservlets.LoadOnStartupServlet
  //     .MODULES_FILTER_HELPER_PROPERTY.
  public static final String MODULES_FILTER_HELPER_PROPERTY =
      "com.google.appengine.tools.development.modules_filter_helper";
  private static final Logger logger = Logger.getLogger(DevAppServerImpl.class.getName());

  private final ApplicationConfigurationManager applicationConfigurationManager;
  private final Modules modules;
  private Map<String, String> serviceProperties = new HashMap<String, String>();
  private final Map<String, Object> containerConfigProperties;
  private final int requestedPort;
  private final String customApplicationId;

  enum ServerState { INITIALIZING, RUNNING, STOPPING, SHUTDOWN }

  /**
   * The current state of the server.
   */
  private ServerState serverState = ServerState.INITIALIZING;

  /**
   * Contains the backend servers configured as part of the "Servers" feature.
   * Each backend server is started on a separate port and keep their own
   * internal state. Memcache, datastore, and other API services are shared by
   * all servers, including the "main" server.
   */
  private final BackendServers backendContainer;

  /**
   * The api proxy we created when we started the web containers. Not initialized until after
   * {@link #start()} is called.
   */
  private ApiProxyLocal apiProxyLocal;

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
   * Constructs a development application server that runs the application located in the given
   * WAR or EAR directory.
   *
   * @param appDir The location of the application to run.
   * @param externalResourceDir If not {@code null}, a resource directory external to the appDir.
   *        This will be searched before appDir when looking for resources.
   * @param webXmlLocation The location of a file whose format complies with
   * http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  If {@code null},
   * defaults to <appDir>/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file.  If
   * {@code null}, defaults to <appDir>/WEB-INF/appengine-web.xml
   * @param address The address on which to run
   * @param port The port on which to run
   * @param useCustomStreamHandler If {@code true} (typical), install {@link StreamHandlerFactory}.
   * @param requestedContainerConfigProperties Additional properties used in the
   * configuration of the specific container implementation.
   * @param applicationId The custom application ID. If {@code null}, defaults to use the primary
   * module's application ID.
   */
  public DevAppServerImpl(File appDir, File externalResourceDir, File webXmlLocation,
      File appEngineWebXmlLocation, String address, int port, boolean useCustomStreamHandler,
      Map<String, Object> requestedContainerConfigProperties, String applicationId) {
    //   String serverInfo = ContainerUtils.getServerInfo();
    if (useCustomStreamHandler) {
      StreamHandlerFactory.install();
    }
    
    backendContainer = BackendServers.getInstance();
    requestedPort = port;
    customApplicationId = applicationId;
    ApplicationConfigurationManager tempManager = null;
    File schemaFile =
        new File(AppengineSdk.getSdk().getResourcesDirectory(), "appengine-application.xsd");
    try {
      if (EarHelper.isEar(appDir.getAbsolutePath())) {
        tempManager =
            ApplicationConfigurationManager.newEarConfigurationManager(appDir, "dev", schemaFile);
       String contextRootWarning =
            "Ignoring application.xml context-root element, for details see "
             + "https://developers.google.com/appengine/docs/java/modules/#config";
        logger.info(contextRootWarning);
      } else {
        tempManager =
            ApplicationConfigurationManager.newWarConfigurationManager(
                appDir, appEngineWebXmlLocation, webXmlLocation, externalResourceDir, "dev");
      }
    } catch (AppEngineConfigException configurationException) {
      modules = null;
      applicationConfigurationManager = null;
      this.containerConfigProperties = null;
      this.configurationException = configurationException;
      return;
    }
    this.applicationConfigurationManager = tempManager;
    this.modules =
        Modules.createModules(
            applicationConfigurationManager, "dev", externalResourceDir, address, this);
    DelegatingModulesFilterHelper modulesFilterHelper =
        new DelegatingModulesFilterHelper(backendContainer, modules);
    this.containerConfigProperties =
        ImmutableMap.<String, Object>builder()
            .putAll(requestedContainerConfigProperties)
            .put(MODULES_FILTER_HELPER_PROPERTY, modulesFilterHelper)
            .put(AbstractContainerService.PORT_MAPPING_PROVIDER_PROP, backendContainer)
            .buildOrThrow();
    backendContainer.init(address,
        applicationConfigurationManager.getPrimaryModuleConfigurationHandle(),
        externalResourceDir, this.containerConfigProperties, this);
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
      if (requestedPort != 0) {
        DevAppServerPortPropertyHelper.setPort(modules.getMainModule().getModuleName(),
            requestedPort, serviceProperties);
      }
      backendContainer.setServiceProperties(properties);
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
   * shutdown.
   * @throws AppEngineConfigException If no WEB-INF directory can be found or
   * WEB-INF/appengine-web.xml does not exist.
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

    // We want to use local (stub) implementations of any API.  This
    // will search our classpath for services that contain the
    // @AutoService annotation and register them.
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

    ApiProxyLocalFactory factory = new ApiProxyLocalFactory();
    if (customApplicationId != null) {
      applicationConfigurationManager.getPrimaryModuleConfigurationHandle()
          .getModule().getAppEngineWebXml().setAppId(customApplicationId);
    }
    String applicationName = applicationConfigurationManager.getPrimaryModuleConfigurationHandle()
        .getModule().getAppEngineWebXml().getAppId();
    apiProxyLocal = factory.create(modules.getLocalServerEnvironment(), applicationName);
    setInboundServicesProperty();
    apiProxyLocal.setProperties(serviceProperties);
    ApiProxy.setDelegate(apiProxyLocal);
    LocalModulesService localModulesService =
        (LocalModulesService) apiProxyLocal.getService(LocalModulesService.PACKAGE);
    localModulesService.setModulesController(modules);
    installLoggingServiceHandler((DevServices) apiProxyLocal);
    TimeZone currentTimeZone = null;
    try {
      currentTimeZone = setServerTimeZone();
      backendContainer.configureAll(apiProxyLocal);
      modules.setApiProxyDelegate(apiProxyLocal);
      modules.startup();
      Module mainServer = modules.getMainModule();
      // Note that servers.startup calls ContainerService.startup which historically
      // installed an initialization Environment, overwriting the caller's Environment
      // which broke the admin console under some conditions. I am not sure if
      // backendContainer.startupAll depends on an initialization environment
      // but am installing one for compatibility purposes.
      Map<String, String> portMapping = backendContainer.getPortMapping();
      AbstractContainerService.installLocalInitializationEnvironment(
          mainServer.getMainContainer().getAppEngineWebXmlConfig(), LocalEnvironment.MAIN_INSTANCE,
          getPort(), getPort(), null, -1, portMapping);
      backendContainer.startupAll();
    } finally {
      ApiProxy.clearEnvironmentForCurrentThread();
      restoreLocalTimeZone(currentTimeZone);
    }
    shutdownLatch = new CountDownLatch(1);
    serverState = ServerState.RUNNING;
    // If you change this please also update
    // com.google.watr.client.deployment.DevAppServerDeployment.DevAppServerMonitor.
    logger.info("Dev App Server is now running");
    return shutdownLatch;
  }

  private void installLoggingServiceHandler(DevServices proxy) {
    // By default, the ConsoleHandler is set to INFO. Our closest match in production
    // is always set to FINEST. We set all handlers to FINEST to match. We also
    // log to the LogHandler for later use via the Logs API.
    Logger root = Logger.getLogger("");
    DevLogService logService = proxy.getLogService();
    root.addHandler(logService.getLogHandler());

    Handler[] handlers = root.getHandlers();
    if (handlers != null) {
      for (Handler handler : handlers) {
        handler.setLevel(Level.FINEST);
      }
    }
  }

  public void setInboundServicesProperty() {
    ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();
    for (ApplicationConfigurationManager.ModuleConfigurationHandle moduleConfigurationHandle :
      applicationConfigurationManager.getModuleConfigurationHandles()) {
      setBuilder.addAll(
          moduleConfigurationHandle.getModule().getAppEngineWebXml().getInboundServices());
    }

    serviceProperties.put("appengine.dev.inbound-services",
        Joiner.on(",").join(setBuilder.build()));
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
          backendContainer.shutdownAll();
          shutdownLatch.countDown();
          modules.createConnections();
          backendContainer.configureAll(apiProxyLocal);
          modules.setApiProxyDelegate(apiProxyLocal);
          modules.startup();
          backendContainer.startupAll();
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
          backendContainer.shutdownAll();
          ApiProxy.setDelegate(null);
          apiProxyLocal = null;
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
    AccessController.doPrivileged(
        new PrivilegedAction<Future<Void>>() {
          @Override
          public Future<Void> run() {
            return shutdownScheduler.schedule(
                new Callable<Void>() {
                  @Override
                  public Void call() throws Exception {
                    shutdown();
                    return null;
                  }
                },
                1000,
                TimeUnit.MILLISECONDS);
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

  ServerState getServerState() {
    return serverState;
  }
}
