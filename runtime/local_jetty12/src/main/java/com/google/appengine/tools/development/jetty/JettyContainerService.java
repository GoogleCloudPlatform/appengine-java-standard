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

package com.google.appengine.tools.development.jetty;

import static com.google.appengine.tools.development.LocalEnvironment.DEFAULT_VERSION_HOSTNAME;

import com.google.appengine.api.log.dev.DevLogHandler;
import com.google.appengine.api.log.dev.LocalLogService;
import com.google.appengine.tools.development.AbstractContainerService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.AppContext;
import com.google.appengine.tools.development.ContainerService;
import com.google.appengine.tools.development.ContainerServiceEE8;
import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerModulesFilter;
import com.google.appengine.tools.development.IsolatedAppClassLoader;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.LocalHttpRequestEnvironment;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.jetty.SessionManagerHandler;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.ScopedHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.webapp.Configuration;
import org.eclipse.jetty.ee8.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Scanner;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/** Implements a Jetty backed {@link ContainerService}. */
public class JettyContainerService extends AbstractContainerService implements ContainerServiceEE8 {

  private static final Logger log = Logger.getLogger(JettyContainerService.class.getName());

  private static final String JETTY_TAG_LIB_JAR_PREFIX = "org.apache.taglibs.taglibs-";
  private static final Pattern JSP_REGEX = Pattern.compile(".*\\.jspx?");

  public static final String WEB_DEFAULTS_XML =
          "com/google/appengine/tools/development/jetty/webdefault.xml";

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
   * <p>Specifically, we've removed {@link JettyWebXmlConfiguration} which
   * allows users to use {@code jetty-web.xml} files.
   */
  private static final String[] CONFIG_CLASSES =
      new String[] {
        org.eclipse.jetty.ee8.webapp.WebInfConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee8.webapp.WebXmlConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee8.webapp.MetaInfConfiguration.class.getCanonicalName(),
        org.eclipse.jetty.ee8.webapp.FragmentConfiguration.class.getCanonicalName(),
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
        new ContextHandler.ContextScopeListener() {
          @Override
          public void enterScope(ContextHandler.APIContext context, Request request, Object reason) {
            JettyContainerService.this.enterScope(request);
          }

          @Override
          public void exitScope(ContextHandler.APIContext context, Request request) {
            JettyContainerService.this.exitScope(null);
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
    }
    finally {
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

    IsolatedAppClassLoader isolatedClassLoader = new IsolatedAppClassLoader(
            appRoot, externalResourceDir, classPath, JettyContainerService.class.getClassLoader());
    context.setClassLoader(isolatedClassLoader);
    if (Boolean.parseBoolean(System.getProperty("appengine.allowRemoteShutdown"))) {
      context.addServlet(new ServletHolder(new ServerShutdownServlet()), "/_ah/admin/quit");
    }

    return appRoot;
  }

  private ApiProxy.Environment enterScope(HttpServletRequest request)
  {
    ApiProxy.Environment oldEnv = ApiProxy.getCurrentEnvironment();

    // We should have a request that use its associated environment, if there is no request
    // we cannot select a local environment as picking the wrong one could result in
    // waiting on the LocalEnvironment API call semaphore forever.
    LocalEnvironment env = request == null ? null
                    : (LocalEnvironment) request.getAttribute(LocalEnvironment.class.getName());
    if (env != null) {
      ApiProxy.setEnvironmentForCurrentThread(env);
      DevAppServerModulesFilter.injectBackendServiceCurrentApiInfo(
              backendName, backendInstance, portMappingProvider.getPortMapping());
    }

    return oldEnv;
  }

  private void exitScope(ApiProxy.Environment environment)
  {
    ApiProxy.setEnvironmentForCurrentThread(environment);
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
              Runtime.getRuntime().availableProcessors(),
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
      // Wrap context in a handler that manages the ApiProxy ThreadLocal.
      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      context.insertHandler(apiHandler);
      server.setHandler(context);
      SessionManagerHandler unused = SessionManagerHandler.create(
          SessionManagerHandler.Config.builder()
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
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            try {
              if (name.equals(getScanTarget().getName())) {
                return true;
              }
              return false;
            } catch (Exception e) {
              return false;
            }
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
      return new File(
          context.getWebInf().getPath() + File.separator + "appengine-web.xml");
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

    scanner.addListener((Scanner.BulkListener) filenames -> {
      log.info("A file has changed, reloading the web application.");
      reloadWebApp();
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
      ApiProxyHandler apiHandler = new ApiProxyHandler(appEngineWebXml);
      context.insertHandler(apiHandler);
      server.setHandler(context);
      SessionManagerHandler unused = SessionManagerHandler.create(
          SessionManagerHandler.Config.builder()
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

  /**
   * {@code ApiProxyHandler} wraps around an existing {@link Handler} and creates a {@link
   * com.google.apphosting.api.ApiProxy.Environment} which is stored as a request Attribute and then
   * set/cleared on a ThreadLocal by the ContextScopeListener {@link ThreadLocal}.
   */
  private class ApiProxyHandler extends ScopedHandler {
    @SuppressWarnings("hiding") // Hides AbstractContainerService.appEngineWebXml
    private final AppEngineWebXml appEngineWebXml;

    public ApiProxyHandler(AppEngineWebXml appEngineWebXml) {
      this.appEngineWebXml = appEngineWebXml;
    }

    @Override
    public void doHandle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {
      nextHandle(target, baseRequest, request, response);
    }

    @Override
    public void doScope(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

      if (baseRequest.getDispatcherType() == DispatcherType.REQUEST) {
        org.eclipse.jetty.server.Request.addCompletionListener(
                baseRequest.getCoreRequest(),
                t -> {
                  try {
                    // a special hook with direct access to the container instance
                    // we invoke this only after the normal request processing,
                    // in order to generate a valid response
                    if (request.getRequestURI().startsWith(AH_URL_RELOAD)) {
                      try {
                        reloadWebApp();
                        log.info("Reloaded the webapp context: " + request.getParameter("info"));
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
                        log.log(
                                Level.WARNING, "Interrupted while waiting for API calls to complete:", ex);
                      }

                      try {
                        ApiProxy.setEnvironmentForCurrentThread(env);

                        // Invoke all of the registered RequestEndListeners.
                        env.callRequestEndListeners();

                        if (apiProxyDelegate instanceof ApiProxyLocal) {
                          // If apiProxyDelegate is not instanceof ApiProxyLocal, we are presumably
                          // running in
                          // the devappserver2 environment, where the master web server in Python will
                          // take care
                          // of logging requests.
                          ApiProxyLocal apiProxyLocal = (ApiProxyLocal) apiProxyDelegate;
                          String appId = env.getAppId();
                          String versionId = env.getVersionId();
                          String requestId = DevLogHandler.getRequestId();

                          LocalLogService logService =
                                  (LocalLogService) apiProxyLocal.getService(LocalLogService.PACKAGE);

                          @SuppressWarnings("NowMillis")
                          long nowMillis = System.currentTimeMillis();
                          logService.addRequestInfo(
                                  appId,
                                  versionId,
                                  requestId,
                                  request.getRemoteAddr(),
                                  request.getRemoteUser(),
                                  baseRequest.getTimeStamp() * 1000,
                                  nowMillis * 1000,
                                  request.getMethod(),
                                  request.getRequestURI(),
                                  request.getProtocol(),
                                  request.getHeader("User-Agent"),
                                  true,
                                  response.getStatus(),
                                  request.getHeader("Referrer"));
                          logService.clearResponseSize();
                        }
                      } finally {
                        ApiProxy.clearEnvironmentForCurrentThread();
                      }
                    }
                  }
                });

        Semaphore semaphore = new Semaphore(MAX_SIMULTANEOUS_API_CALLS);

        LocalEnvironment env =
            new LocalHttpRequestEnvironment(
                appEngineWebXml.getAppId(),
                WebModule.getModuleName(appEngineWebXml),
                appEngineWebXml.getMajorVersionId(),
                instance,
                getPort(),
                request,
                SOFT_DEADLINE_DELAY_MS,
                modulesFilterHelper);
        env.getAttributes().put(LocalEnvironment.API_CALL_SEMAPHORE, semaphore);
        env.getAttributes().put(DEFAULT_VERSION_HOSTNAME, "localhost:" + devAppServer.getPort());

        request.setAttribute(LocalEnvironment.class.getName(), env);
        environments.add(env);
      }

      // We need this here because the ContextScopeListener is invoked before
      // this and so the Environment has not yet been created.
      ApiProxy.Environment oldEnv = enterScope(request);
      try {
        super.doScope(target, baseRequest, request, response);
      } finally {
        exitScope(oldEnv);
      }
    }
  }
}
