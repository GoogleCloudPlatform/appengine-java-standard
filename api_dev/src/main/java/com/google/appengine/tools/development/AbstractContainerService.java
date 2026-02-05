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

import static com.google.appengine.tools.development.LocalEnvironment.DEFAULT_VERSION_HOSTNAME;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.ClassPathBuilder;
import com.google.apphosting.utils.config.WebModule;
import com.google.apphosting.utils.config.WebXml;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Permissions;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common implementation for the {@link ContainerService} interface.
 *
 * <p>There should be no reference to any third-party servlet container from here.
 *
 */
public abstract class AbstractContainerService implements ContainerService {

  private static final Logger log = Logger.getLogger(AbstractContainerService.class.getName());

  protected static final String AH_URL_RELOAD = "/_ah/reloadwebapp";

  private static final String USER_CODE_CLASSPATH_MANAGER_PROP =
      "devappserver.userCodeClasspathManager";

  private static final String USER_CODE_CLASSPATH = USER_CODE_CLASSPATH_MANAGER_PROP + ".classpath";
  private static final String USER_CODE_REQUIRES_WEB_INF =
      USER_CODE_CLASSPATH_MANAGER_PROP + ".requiresWebInf";

  public static final String PORT_MAPPING_PROVIDER_PROP = "devappserver.portMappingProvider";

  // Begin members that are set via configure()
  protected ModuleConfigurationHandle moduleConfigurationHandle;
  protected String devAppServerVersion;
  protected File appDir;
  protected File externalResourceDir;

  /**
   * The location of web.xml.  If not provided, defaults to
   * {@code <appDir>/WEB-INF/web.xml}
   */
  protected File webXmlLocation;

  /**
   * The hostname on which the module instance is listening for http requests.
   */
  protected String hostName;

  /**
   * The network address on which the module instance is listening for http requests.
   */
  protected String address;

  /**
   * The port on which the module instance is listening for http requests.
   */
  protected int port;

  /**
   * The 0 based index for this instance or {@link LocalEnvironment#MAIN_INSTANCE}.
   */
  protected int instance;

  /**
   * A reference to the parent DevAppServer that configured this container.
   */
  protected DevAppServer devAppServer;

  // End members that are set via configure()

  protected AppEngineWebXml appEngineWebXml;

  protected WebXml webXml;

  // The backend name if this container is for a back end and otherwise null.
  protected String backendName;

  // The backend instance id if this container is for a back end instance and
  // otherwise null.
  protected int backendInstance;

  // Provider for the port mapping required by backend service. Note we use a provider
  // because this is passed to us in configure which is called before the full port
  // mapping is available. The advantage to this approach is that it avoids changing
  // the public ContainerService interface and hence avoids exposing our management
  // of port mappings to users.
  protected PortMappingProvider portMappingProvider = () -> ImmutableMap.of();

  /**
   * Latch that will open once the module instance is fully initialized. TODO: This is used by some
   * services but only for the default instance of the default module. Investigate. Does module
   * start/stop cause issues? There is some issue with tasks during Servlet initialization.
   */
  private CountDownLatch moduleInitLatch;

  /**
   * Not initialized until {@link #startup()} has been called.
   */
  protected ApiProxy.Delegate<?> apiProxyDelegate;

  protected UserCodeClasspathManager userCodeClasspathManager;

  protected ModulesFilterHelper modulesFilterHelper;

  @Override
  public final LocalServerEnvironment configure(String devAppServerVersion, final String address,
      int port, final ModuleConfigurationHandle moduleConfigurationHandle, File externalResourceDir,
      Map<String, Object> containerConfigProperties, int instance, DevAppServer devAppServer) {
    this.devAppServerVersion = devAppServerVersion;
    this.moduleConfigurationHandle = moduleConfigurationHandle;
    extractFieldsFromWebModule(moduleConfigurationHandle.getModule());
    this.externalResourceDir = externalResourceDir;
    this.address = address;
    this.port = port;
    this.moduleInitLatch = new CountDownLatch(1);
    this.hostName = "localhost";
    this.devAppServer = devAppServer;
    if ("0.0.0.0".equals(address)) {
      try {
        InetAddress localhost = InetAddress.getLocalHost();
        this.hostName = localhost.getHostName();
      } catch (UnknownHostException ex) {
        log.log(Level.WARNING,
            "Unable to determine hostname - defaulting to localhost.");
      }
    }

    this.userCodeClasspathManager = newUserCodeClasspathProvider(containerConfigProperties);
    this.modulesFilterHelper = (ModulesFilterHelper)
        containerConfigProperties.get(DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY);
    this.backendName =
        (String) containerConfigProperties.get(BackendService.BACKEND_ID_ENV_ATTRIBUTE);
    Object rawBackendInstance =
        containerConfigProperties.get(BackendService.INSTANCE_ID_ENV_ATTRIBUTE);
    this.backendInstance =
        rawBackendInstance == null ? -1 : ((Integer) rawBackendInstance).intValue();
    PortMappingProvider callersPortMappingProvider =
        (PortMappingProvider) containerConfigProperties.get(PORT_MAPPING_PROVIDER_PROP);
    if (callersPortMappingProvider == null) {
      log.warning("Null value for containerConfigProperties.get("
          + PORT_MAPPING_PROVIDER_PROP + ")");
    } else {
      this.portMappingProvider = callersPortMappingProvider;
    }

    this.instance = instance;

    return new LocalServerEnvironment() {
      @Override
      public File getAppDir() {
        return moduleConfigurationHandle.getModule().getApplicationDirectory();
      }

      @Override
      public String getAddress() {
        return address;
      }

      @Override
      public String getHostName() {
        return hostName;
      }

      @Override
      public int getPort() {
        // It's important that we return the value of the member rather than
        // the value of the param because the param value may not reflect the
        // actual port to which the module instance is bound if the module
        // instance is selecting its own port.  The member is guaranteed to
        // contain the actual port after startup() has been called.
        return AbstractContainerService.this.port;
      }

      @Override
      public void waitForServerToStart() throws InterruptedException {
        moduleInitLatch.await();
      }

      @Override
      public boolean simulateProductionLatencies() {
        return false;
      }

      @Override
      public boolean enforceApiDeadlines() {
        return !Boolean.getBoolean("com.google.appengine.disable_api_deadlines");
      }
    };
  }

  @Override
  public void setApiProxyDelegate(ApiProxy.Delegate<?> apiProxyDelegate) {
    this.apiProxyDelegate = apiProxyDelegate;
  }

  /**
   * @param webModule
   */
  protected void extractFieldsFromWebModule(WebModule webModule) {
    this.appDir = webModule.getApplicationDirectory();
    this.webXml = webModule.getWebXml();
    this.webXmlLocation = webModule.getWebXmlFile();
    this.appEngineWebXml = webModule.getAppEngineWebXml();
  }

  /**
   * Constructs a {@link UserCodeClasspathManager} from the given properties.
   */
  private static UserCodeClasspathManager newUserCodeClasspathProvider(
      Map<String, Object> containerConfigProperties) {
    // TODO: Support a mode where we combine this classpath with the
    // classpath generated from the war.
    if (containerConfigProperties.containsKey(USER_CODE_CLASSPATH_MANAGER_PROP)) {
      // If this key exists then the caller wants to customize the classpath
      // manager.
      @SuppressWarnings("unchecked")
      final Map<String, Object> userCodeClasspathManagerProps =
          (Map<String, Object>) containerConfigProperties.get(USER_CODE_CLASSPATH_MANAGER_PROP);
      return new UserCodeClasspathManager() {
        @SuppressWarnings("unchecked")
        @Override
        public Collection<URL> getUserCodeClasspath(File root) {
          return (Collection<URL>) userCodeClasspathManagerProps.get(USER_CODE_CLASSPATH);
        }

        @Override
        public boolean requiresWebInf() {
          return (Boolean) userCodeClasspathManagerProps.get(USER_CODE_REQUIRES_WEB_INF);
        }
      };
    }
    // No customization, just use the default implementation.
    return new WebAppUserCodeClasspathManager();
  }

  @Override
  public final void createConnection() throws Exception {
    connectContainer();
  }

  @Override
  public final void startup() throws Exception {
    // InitContext installs an Environment for initializing the
    // container. We preserve and restore the caller's
    // Environment in case this is called from an HTTP request.
    Environment prevEnvironment = ApiProxy.getCurrentEnvironment();
    try {
      initContext();
      if (appEngineWebXml == null) {
        throw new IllegalStateException("initContext failed to initialize appEngineWebXml.");
      }

      startContainer();
      startHotDeployScanner();
      moduleInitLatch.countDown();
    } catch (Exception e) {
      throw e;
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(prevEnvironment);
    }
  }

  @Override
  public final void shutdown() throws Exception {
    stopHotDeployScanner();
    stopContainer();
    // TODO: shutdown is generally called for application level shutdown.
    //                The exception is AbstractBackendServers.stopBackend which
    //                stops a single back end. In that case clearing the system
    //                properties for all modules seems wrong.
    moduleConfigurationHandle.restoreSystemProperties();
  }

  @Override
  public Map<String, String> getServiceProperties() {
    return ImmutableMap.of("appengine.dev.inbound-services",
        Joiner.on(",").useForNull("null").join(appEngineWebXml.getInboundServices()));
  }

  // the actual "API" for a concrete implementation

  /**
   * Set up the webapp context in a container specific way.
   * <p>Note that {@link #initContext()} is required to call
   * {@link #installLocalInitializationEnvironment()} for the service to be correctly
   * initialized.
   *
   * @return the effective webapp directory.
   */
  protected abstract File initContext() throws IOException;

  /**
   * Creates the servlet container's network connections.
   */
  protected abstract void connectContainer() throws Exception;

  /**
   * Start up the servlet container runtime.
   */
  protected abstract void startContainer() throws Exception;

  /**
   * Stop the servlet container runtime.
   */
  protected abstract void stopContainer() throws Exception;

  /** Start up the hot-deployment scanner. */
  // TODO: we may want to make this configurable.
  protected abstract void startHotDeployScanner() throws Exception;

  /**
   * Stop the hot-deployment scanner.
   */
  protected abstract void stopHotDeployScanner() throws Exception;

  /**
   * Re-deploy the current webapp context in a container specific way,
   * while taking into account possible appengine-web.xml change too,
   * without restarting the module instance.
   */
  protected abstract void reloadWebApp() throws Exception;

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public AppEngineWebXml getAppEngineWebXmlConfig(){
    return appEngineWebXml;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getHostName() {
    return hostName;
  }

  protected Permissions getUserPermissions() {
    return appEngineWebXml.getUserPermissions();
  }

  // common utils

  /**
   * Installs a {@link LocalInitializationEnvironment} with
   * {@link ApiProxy#setEnvironmentForCurrentThread}.
   * <p>
   * Filters and servlets get initialized when we call server.start(). If any of
   * those filters and servlets need access to the current execution environment
   * they'll call ApiProxy.getCurrentEnvironment(). We set a special initialization
   * environment so that there is an environment available when this happens.
   * <p>
   * This depends on port which may not be set to its final value until {@link #connectContainer()}
   * is called.
   */
  protected void installLocalInitializationEnvironment() {
    installLocalInitializationEnvironment(appEngineWebXml, instance, port, devAppServer.getPort(),
        backendName, backendInstance, portMappingProvider.getPortMapping());
  }

  /** Returns {@code true} if appengine-web.xml {@code <sessions-enabled>} is true. */
  protected boolean isSessionsEnabled() {
    return appEngineWebXml.getSessionsEnabled();
  }

  /**
   * Gets all of the URLs that should be added to the classpath for an
   * application located at {@code root}.
   */
  protected URL[] getClassPathForApp(File root) {
    // N.B.: Do not use File.toURI().toURL() here, as that
    // will cause the file to be URL quoted (e.g. spaces replaced with
    // %20's).  URLClassLoader seems to cope with this okay, but
    // Jasper's JSP compiler uses its own classpath to populate the
    // -cp argument for the javac command it launches, and -cp doesn't
    // understand URL-encoded classpaths.  File.toURL() is deprecated,
    // but it does not URL-encode the returned file.
    ClassPathBuilder classPathBuilder =
        new ClassPathBuilder(appEngineWebXml.getClassLoaderConfig());

    classPathBuilder.addUrls(userCodeClasspathManager.getUserCodeClasspath(root));
    classPathBuilder.addUrls(AppengineSdk.getSdk().getUserJspLibs());

    return getUrls(classPathBuilder);
  }

  // Returns the urls from classPathBuilder and logs a message if needed.
  private static URL[] getUrls(ClassPathBuilder classPathBuilder) {
    URL[] urls = classPathBuilder.getUrls();
    String message = classPathBuilder.getLogMessage();
    if (!message.isEmpty()) {
      log.warning(message);
    }
    return urls;
  }

  /**
   * Sets up an {@link com.google.apphosting.api.ApiProxy.Environment} for container
   * initialization.
   */
  public static void installLocalInitializationEnvironment(AppEngineWebXml appEngineWebXml,
                                                           int instance, int port,
                                                           int defaultModuleMainPort,
                                                           String backendName,
                                                           int backendInstance,
                                                           Map<String, String> portMapping) {
    Environment environment = new LocalInitializationEnvironment(
        appEngineWebXml.getAppId(), WebModule.getModuleName(appEngineWebXml),
        appEngineWebXml.getMajorVersionId(), instance, port);
    environment.getAttributes().put(DEFAULT_VERSION_HOSTNAME, "localhost:"
        + defaultModuleMainPort);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    DevAppServerModulesCommon.injectBackendServiceCurrentApiInfo(
        backendName, backendInstance, portMapping);
  }

  /**
   * A fake {@link LocalEnvironment} implementation that is used during the
   * initialization of the Development AppServer.
   */
  public static class LocalInitializationEnvironment extends LocalEnvironment {
    public LocalInitializationEnvironment(String appId, String moduleName, String majorVersionId,
        int instance, int port) {
      super(appId, moduleName, majorVersionId, instance, port, null);
    }

    @Override
    public String getEmail() {
      // No user
      return null;
    }

    @Override
    public boolean isLoggedIn() {
      // No user
      return false;
    }

    @Override
    public boolean isAdmin() {
      // No user
      return false;
    }
  }

  /**
   * Provider for the 'portMapping'.
   * <p>
   * The provided map contains an entry for every backend instance.
   * For the main instance the key is the backend name and the value is
   * the hostname:port for sending http requests to the instance (i.e.
   * bob->127.0.0.1:1234). For other instances the key is
   * instance-id.hostname and the value is again the hostname:port for
   * sending http requests to the instance (i.e. 2.bob->127.0.0.1:1234).
   */
  public interface PortMappingProvider {
    Map<String, String> getPortMapping();
  }
}
