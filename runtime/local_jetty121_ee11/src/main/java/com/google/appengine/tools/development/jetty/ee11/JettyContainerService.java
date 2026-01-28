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

package com.google.appengine.tools.development.jetty.ee11;

import static com.google.appengine.tools.development.LocalEnvironment.DEFAULT_VERSION_HOSTNAME;

import com.google.appengine.api.log.dev.DevLogHandler;
import com.google.appengine.api.log.dev.LocalLogService;
import com.google.appengine.tools.development.AbstractContainerService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.AppContext;
import com.google.appengine.tools.development.ContainerService;
import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerModulesFilter;
import com.google.appengine.tools.development.IsolatedAppClassLoader;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.jakarta.LocalHttpRequestEnvironment;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.jetty.EE11SessionManagerHandler;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.eclipse.jetty.ee11.servlet.ServletApiRequest;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletContextRequest;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.ee11.webapp.Configuration;
import org.eclipse.jetty.ee11.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.ee11.webapp.WebAppContext;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Scanner;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/** Implements a Jetty backed {@link ContainerService}. */
public class JettyContainerService extends AbstractContainerService
    implements com.google.appengine.tools.development.jakarta.ContainerService {

  private static final Logger log = Logger.getLogger(JettyContainerService.class.getName());

  private static final String JETTY_TAG_LIB_JAR_PREFIX = "org.apache.taglibs.taglibs-";
  private static final Pattern JSP_REGEX = Pattern.compile(".*\\.jspx?");

  public static final String WEB_DEFAULTS_XML =
      "com/google/appengine/tools/development/jetty/ee11/webdefault.xml";

  // This should match the value of the --clone_max_outstanding_api_rpcs flag.
  private static final int MAX_SIMULTANEOUS_API_CALLS = 100;

  // The soft deadline for requests.  It is defined here, as the normal way to
  // get this deadline is through JavaRuntimeFactory, which is part of the
  // runtime and not really part of the devappserver.
  private static final Long SOFT_DEADLINE_DELAY_MS = 60000L;

  /**
   * Specify which {@link Configuration} objects should be invoked when configuring a web
   * application.
   *
   * <p>This is a subset of: org.mortbay.jetty.webapp.WebAppContext.__dftConfigurationClasses
   *
   * <p>Specifically, we've removed {@link JettyWebXmlConfiguration} which allows users to use
   * {@code jetty-web.xml} files.
   */
  private static final String[] CONFIG_CLASSES =
      new String[] {
        org.eclipse.jetty.ee11.webapp.WebInfConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee11.webapp.WebXmlConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee11.webapp.MetaInfConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee11.webapp.FragmentConfiguration.class.getCanonicalName(),
        // Special annotationConfiguration to deal with Jasper ServletContainerInitializer.
        AppEngineAnnotationConfiguration.class.getCanonicalName()
      };

  private static final String WEB_XML_ATTR = "com.google.appengine.tools.development.webXml";
  private static final String APPENGINE_WEB_XML_ATTR =
      "com.google.appengine.tools.development.appEngineWebXml";

  private static final int SCAN_INTERVAL_SECONDS = 5;

  /** Jetty webapp context. */
  private WebAppContext context;

  /** Our webapp context. */
  private AppContext appContext;

  /** The Jetty server. */
  private Server server;

  /** Hot deployment support. */
  private Scanner scanner;

  /** Collection of current LocalEnvironments */
  private final Set<LocalEnvironment> environments = ConcurrentHashMap.newKeySet();

  private class JettyAppContext implements AppContext {
    @Override
    public ClassLoader getClassLoader() {
      return context.getClassLoader();
    }

    @Override
    public Permissions getUserPermissions() {
      return JettyContainerService.this.getUserPermissions();
    }

    @Override
    public Permissions getApplicationPermissions() {
      // Should not be called in Java8/Jetty9.
      throw new RuntimeException("No permissions needed for this runtime.");
    }

    @Override
    public Object getContainerContext() {
      return context;
    }
  }

  public JettyContainerService() {}

  @Override
  protected File initContext() throws IOException {
    // Register our own slight modification of Jetty's WebAppContext,
    // which maintains ApiProxy's environment ThreadLocal.
    this.context =
        new DevAppEngineWebAppContext(
            appDir, externalResourceDir, devAppServerVersion, apiProxyDelegate, devAppServer);

    context.addEventListener(
        new ServletContextHandler.ServletContextScopeListener() {

          @Override
          public void enterScope(
              ServletContextHandler.ServletScopedContext context, ServletContextRequest request) {
            JettyContainerService.this.enterScope(request);
          }

          @Override
          public void exitScope(
              ServletContextHandler.ServletScopedContext context, ServletContextRequest request) {
            ApiProxy.setEnvironmentForCurrentThread(null);
          }
        });

    this.appContext = new JettyAppContext();

    // Set the location of deployment descriptor.  This value might be null,
    // which is fine, it just means Jetty will look for it in the default
    // location (WEB-INF/web.xml).
    context.setDescriptor(webXmlLocation == null ? null : webXmlLocation.getAbsolutePath());

    // Override the web.xml that Jetty automatically prepends to other
    // web.xml files.  This is where the DefaultServlet is registered,
    // which serves static files.  We override it to disable some
    // other magic (e.g. JSP compilation), and to turn off some static
    // file functionality that Prometheus won't support
    // (e.g. directory listings) and turn on others (e.g. symlinks).
    String webDefaultXml =
        devAppServer
            .getServiceProperties()
            .getOrDefault("appengine.webdefault.xml", WEB_DEFAULTS_XML);
    context.setDefaultsDescriptor(webDefaultXml);

    // Disable support for jetty-web.xml.
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(WebAppContext.class.getClassLoader());
      context.setConfigurationClasses(CONFIG_CLASSES);
    } finally {
      Thread.currentThread().setContextClassLoader(contextClassLoader);
    }
    // Create the webapp ClassLoader.
    // We need to load appengine-web.xml to initialize the class loader.
    File appRoot = determineAppRoot();
    installLocalInitializationEnvironment();

    // Create the webapp ClassLoader.
    // ADD TLDs that must be under WEB-INF for Jetty9.
    // We make it non fatal, and emit a warning when it fails, as the user can add this dependency
    // in the application itself.
    if (applicationContainsJSP(appDir, JSP_REGEX)) {
      for (File file : AppengineSdk.getSdk().getUserJspLibFiles()) {
        if (file.getName().startsWith(JETTY_TAG_LIB_JAR_PREFIX)) {
          // Jetty provided tag lib jars are currently
          // org.apache.taglibs.taglibs-standard-spec-1.2.5.jar and
          // org.apache.taglibs.taglibs-standard-impl-1.2.5.jar.
          // For jars provided by a Maven or Gradle builder, the prefix org.apache.taglibs.taglibs-
          // is not present, so the jar names are:
          // standard-spec-1.2.5.jar and
          // standard-impl-1.2.5.jar.
          // We check if these jars are provided by the web app, or we copy them from Jetty distro.
          File jettyProvidedDestination = new File(appDir + "/WEB-INF/lib/" + file.getName());
          if (!jettyProvidedDestination.exists()) {
            File mavenProvidedDestination =
                new File(
                    appDir
                        + "/WEB-INF/lib/"
                        + file.getName().substring(JETTY_TAG_LIB_JAR_PREFIX.length()));
            if (!mavenProvidedDestination.exists()) {
              log.log(
                  Level.WARNING,
                  "Adding jar "
                      + file.getName()
                      + " to WEB-INF/lib."
                      + " You might want to add a dependency in your project build system to avoid"
                      + " this warning.");
              try {
                Files.copy(file, jettyProvidedDestination);
              } catch (IOException e) {
                log.log(
                    Level.WARNING,
                    "Cannot copy org.apache.taglibs.taglibs jar file to WEB-INF/lib.",
                    e);
              }
            }
          }
        }
      }
    }

    URL[] classPath = getClassPathForApp(appRoot);

    IsolatedAppClassLoader isolatedClassLoader =
        new IsolatedAppClassLoader(
            appRoot, externalResourceDir, classPath, JettyContainerService.class.getClassLoader());
    context.setClassLoader(isolatedClassLoader);
    if (Boolean.parseBoolean(System.getProperty("appengine.allowRemoteShutdown"))) {
      context.addServlet(new ServletHolder(new ServerShutdownServlet()), "/_ah/admin/quit");
    }

    return appRoot;
  }

    private void enterScope(ServletContextRequest request) {

        // We should have a request that use its associated environment, if there is no request
        // we cannot select a local environment as picking the wrong one could result in
        // waiting on the LocalEnvironment API call semaphore forever.
        if (request == null) {
            return;
        }

        LocalEnvironment env =
            (LocalEnvironment) request.getAttribute(LocalEnvironment.class.getName());
        if (env == null) {
            env =
                new LocalHttpRequestEnvironment(
                    appEngineWebXml.getAppId(),
                    WebModule.getModuleName(appEngineWebXml),
                    appEngineWebXml.getMajorVersionId(),
                    instance,
                    getPort(),
                    request.getServletApiRequest(),
                    SOFT_DEADLINE_DELAY_MS,
                    modulesFilterHelper);
            env.getAttributes()
                .put(LocalEnvironment.API_CALL_SEMAPHORE, new Semaphore(MAX_SIMULTANEOUS_API_CALLS));
            env.getAttributes().put(DEFAULT_VERSION_HOSTNAME, "localhost:" + devAppServer.getPort());

            request.setAttribute(LocalEnvironment.class.getName(), env);
            environments.add(env);
            addCompletionListener(request);
        }

        ApiProxy.setEnvironmentForCurrentThread(env);
        DevAppServerModulesFilter.injectBackendServiceCurrentApiInfo(
            backendName, backendInstance, portMappingProvider.getPortMapping());
    }

  /** Check if the application contains a JSP file. */
  private static boolean applicationContainsJSP(File dir, Pattern jspPattern) {
    for (File file :
        FluentIterable.from(Files.fileTraverser().depthFirstPreOrder(dir))
            .filter(Predicates.not(Files.isDirectory()))) {
      if (jspPattern.matcher(file.getName()).matches()) {
        return true;
      }
    }
    return false;
  }

  static class ServerShutdownServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.getWriter().println("Shutting down local server.");
      resp.flushBuffer();
      DevAppServer server =
          (DevAppServer)
              getServletContext().getAttribute("com.google.appengine.devappserver.Server");
      // don't shut down until outstanding requests (like this one) have finished
      server.gracefulShutdown();
    }
  }

  @Override
  protected void connectContainer() throws Exception {
    moduleConfigurationHandle.checkEnvironmentVariables();

    // Jetty uses the thread context ClassLoader to find things
    // This needs to be null for the DevAppClassLoader to
    // work correctly. There have been clients that set this to
    // something else.
    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();

    HttpConfiguration configuration = new HttpConfiguration();
    configuration.setSendDateHeader(false);
    configuration.setSendServerVersion(false);
    configuration.setSendXPoweredBy(false);
    // Try to enable virtual threads if requested on java21:
    if (Boolean.getBoolean("appengine.use.virtualthreads")) {
      QueuedThreadPool threadPool = new QueuedThreadPool();
      threadPool.setVirtualThreadsExecutor(VirtualThreads.getDefaultVirtualThreadsExecutor());
      server = new Server(threadPool);
    } else {
      server = new Server();
    }
    try {
      NetworkTrafficServerConnector connector =
          new NetworkTrafficServerConnector(
              server,
              null,
              null,
              null,
              0,
              Math.min(Runtime.getRuntime().availableProcessors(), 150),
              new HttpConnectionFactory(configuration));
      connector.setHost(address);
      connector.setPort(port);
      // Linux keeps the port blocked after shutdown if we don't disable this.
      // TODO: WHAT IS THIS connector.setSoLingerTime(0);
      connector.open();

      server.addConnector(connector);

      port = connector.getLocalPort();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  protected void startContainer() throws Exception {
    context.setAttribute(WEB_XML_ATTR, webXml);
    context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

    // Jetty uses the thread context ClassLoader to find things
    // This needs to be null for the DevAppClassLoader to
    // work correctly. There have been clients that set this to
    // something else.
    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);

    try {
      server.setHandler(context);
      EE11SessionManagerHandler ignored =
          EE11SessionManagerHandler.create(
              EE11SessionManagerHandler.Config.builder()
                  .setEnableSession(isSessionsEnabled())
                  .setServletContextHandler(context)
                  .build());

      server.start();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  protected void stopContainer() throws Exception {
    server.stop();
  }

  /**
   * If the property "appengine.fullscan.seconds" is set to a positive integer, the web app content
   * (deployment descriptors, classes/ and lib/) is scanned for changes that will trigger the
   * reloading of the application. If the property is not set (default), we monitor the webapp war
   * file or the appengine-web.xml in case of a pre-exploded webapp directory, and reload the webapp
   * whenever an update is detected, i.e. a newer timestamp for the monitored file. As a
   * single-context deployment, add/delete is not applicable here.
   *
   * <p>appengine-web.xml will be reloaded too. However, changes that require a module instance
   * restart, e.g. address/port, will not be part of the reload.
   */
  @Override
  protected void startHotDeployScanner() throws Exception {
    String fullScanInterval = System.getProperty("appengine.fullscan.seconds");
    if (fullScanInterval != null) {
      try {
        int interval = Integer.parseInt(fullScanInterval);
        if (interval < 1) {
          log.info("Full scan of the web app for changes is disabled.");
          return;
        }
        log.info("Full scan of the web app in place every " + interval + "s.");
        fullWebAppScanner(interval);
        return;
      } catch (NumberFormatException ex) {
        log.log(Level.WARNING, "appengine.fullscan.seconds property is not an integer:", ex);
        log.log(Level.WARNING, "Using the default scanning method.");
      }
    }
    scanner = new Scanner();
    scanner.setReportExistingFilesOnStartup(false);
    scanner.setScanInterval(SCAN_INTERVAL_SECONDS);
    scanner.setScanDirs(ImmutableList.of(getScanTarget().toPath()));
    scanner.setFilenameFilter(
        (dir, name) -> {
          try {
            return name.equals(getScanTarget().getName());
          } catch (Exception e) {
            return false;
          }
        });
    scanner.addListener(new ScannerListener());
    scanner.start();
  }

  @Override
  protected void stopHotDeployScanner() throws Exception {
    if (scanner != null) {
      scanner.stop();
    }
    scanner = null;
  }

  private class ScannerListener implements Scanner.DiscreteListener {
    @Override
    public void fileAdded(String filename) throws Exception {
      // trigger a reload
      fileChanged(filename);
    }

    @Override
    public void fileChanged(String filename) throws Exception {
      log.info(filename + " updated, reloading the webapp!");
      reloadWebApp();
    }

    @Override
    public void fileRemoved(String filename) throws Exception {
      // ignored
    }
  }

  /** To minimize the overhead, we point the scanner right to the single file in question. */
  private File getScanTarget() throws Exception {
    if (appDir.isFile() || context.getWebInf() == null) {
      // war or running without a WEB-INF
      return appDir;
    } else {
      // by this point, we know the WEB-INF must exist
      // TODO: consider scanning the whole web-inf
      return new File(context.getWebInf().getPath() + File.separator + "appengine-web.xml");
    }
  }

  private void fullWebAppScanner(int interval) throws IOException {
    String webInf = context.getWebInf().getPath().toString();
    List<Path> scanList = new ArrayList<>();
    Collections.addAll(
        scanList,
        new File(webInf, "classes").toPath(),
        new File(webInf, "lib").toPath(),
        new File(webInf, "web.xml").toPath(),
        new File(webInf, "appengine-web.xml").toPath());

    scanner = new Scanner();
    scanner.setScanInterval(interval);
    scanner.setScanDirs(scanList);
    scanner.setReportExistingFilesOnStartup(false);
    scanner.setScanDepth(3);

    scanner.addListener(
        new Scanner.BulkListener() {
          @Override
          public void pathsChanged(Map<Path, Scanner.Notification> changeSet) throws Exception {
            log.info("A file has changed, reloading the web application.");
            reloadWebApp();
          }
        });

    LifeCycle.start(scanner);
  }

  /**
   * Assuming Jetty handles race conditions nicely, as this is how Jetty handles a hot deploy too.
   */
  @Override
  protected void reloadWebApp() throws Exception {
    // Tell Jetty to stop caching jar files, because the changed app may invalidate that
    // caching.
    // TODO: Resource.setDefaultUseCaches(false);

    // stop the context
    server.getHandler().stop();
    server.stop();
    moduleConfigurationHandle.restoreSystemProperties();
    moduleConfigurationHandle.readConfiguration();
    moduleConfigurationHandle.checkEnvironmentVariables();
    extractFieldsFromWebModule(moduleConfigurationHandle.getModule());

    /** same as what's in startContainer, we need suppress the ContextClassLoader here. */
    Thread currentThread = Thread.currentThread();
    ClassLoader previousCcl = currentThread.getContextClassLoader();
    currentThread.setContextClassLoader(null);
    try {
      // reinit the context
      initContext();
      installLocalInitializationEnvironment();
      context.setAttribute(WEB_XML_ATTR, webXml);
      context.setAttribute(APPENGINE_WEB_XML_ATTR, appEngineWebXml);

      // reset the handler
      server.setHandler(context);
      EE11SessionManagerHandler ignored =
          EE11SessionManagerHandler.create(
              EE11SessionManagerHandler.Config.builder()
                  .setEnableSession(isSessionsEnabled())
                  .setServletContextHandler(context)
                  .build());
      // restart the context (on the same module instance)
      server.start();
    } finally {
      currentThread.setContextClassLoader(previousCcl);
    }
  }

  @Override
  public AppContext getAppContext() {
    return appContext;
  }

  @Override
  public void forwardToServer(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException, ServletException {
    log.finest("forwarding request to module: " + appEngineWebXml.getModule() + "." + instance);
    RequestDispatcher requestDispatcher =
        context.getServletContext().getRequestDispatcher(hrequest.getRequestURI());
    requestDispatcher.forward(hrequest, hresponse);
  }

  private File determineAppRoot() throws IOException {
    // Use the context's WEB-INF location instead of appDir since the latter
    // might refer to a WAR whereas the former gets updated by Jetty when it
    // extracts a WAR to a temporary directory.
    Resource webInf = context.getWebInf();
    if (webInf == null) {
      if (userCodeClasspathManager.requiresWebInf()) {
        throw new AppEngineConfigException(
            "Supplied application has to contain WEB-INF directory.");
      }
      return appDir;
    }
    return webInf.getPath().toFile().getParentFile();
  }

  private void addCompletionListener(ServletContextRequest request) {
    org.eclipse.jetty.server.Request.addCompletionListener(
        request,
        t -> {
          try {
            // a special hook with direct access to the container instance
            // we invoke this only after the normal request processing,
            // in order to generate a valid response
            if (request.getHttpURI().getPath().startsWith(AH_URL_RELOAD)) {
              try {
                reloadWebApp();
                Fields parameters = Request.getParameters(request);
                log.info("Reloaded the webapp context: " + parameters.get("info"));
              } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to reload the current webapp context.", ex);
              }
            }
          } finally {

            LocalEnvironment env =
                (LocalEnvironment) request.getAttribute(LocalEnvironment.class.getName());
            if (env != null) {
              environments.remove(env);

              // Acquire all of the semaphores back, which will block if any are outstanding.
              Semaphore semaphore =
                  (Semaphore) env.getAttributes().get(LocalEnvironment.API_CALL_SEMAPHORE);
              try {
                semaphore.acquire(MAX_SIMULTANEOUS_API_CALLS);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.log(Level.WARNING, "Interrupted while waiting for API calls to complete:", ex);
              }

              try {
                ApiProxy.setEnvironmentForCurrentThread(env);

                // Invoke all of the registered RequestEndListeners.
                env.callRequestEndListeners();

                if (apiProxyDelegate instanceof ApiProxyLocal) {
                  // If apiProxyDelegate is not instanceof ApiProxyLocal, we are presumably
                  // running in the devappserver2 environment, where the master web server in Python
                  // will take care of logging requests.
                  ApiProxyLocal apiProxyLocal = (ApiProxyLocal) apiProxyDelegate;
                  String appId = env.getAppId();
                  String versionId = env.getVersionId();
                  String requestId = DevLogHandler.getRequestId();

                  LocalLogService logService =
                      (LocalLogService) apiProxyLocal.getService(LocalLogService.PACKAGE);

                  ServletApiRequest httpServletRequest = request.getServletApiRequest();
                  @SuppressWarnings("NowMillis")
                  long nowMillis = System.currentTimeMillis();
                  logService.addRequestInfo(
                      appId,
                      versionId,
                      requestId,
                      httpServletRequest.getRemoteAddr(),
                      httpServletRequest.getRemoteUser(),
                      Request.getTimeStamp(request) * 1000,
                      nowMillis * 1000,
                      request.getMethod(),
                      httpServletRequest.getRequestURI(),
                      httpServletRequest.getProtocol(),
                      httpServletRequest.getHeader("User-Agent"),
                      true,
                      request.getHttpServletResponse().getStatus(),
                      request.getHeaders().get("Referrer"));
                  logService.clearResponseSize();
                }
              } finally {
                ApiProxy.clearEnvironmentForCurrentThread();
              }
            }
          }
        });
  }
}
