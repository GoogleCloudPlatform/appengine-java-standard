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

package com.google.apphosting.runtime.jetty.ee10;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.runtime.jetty.EE10AppEngineAuthentication;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ListenerHolder;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * {@code AppEngineWebAppContext} is a customization of Jetty's {@link WebAppContext} that is aware
 * of the {@link ApiProxy} and can provide custom logging and authentication.
 */
// This class is different than the one for Jetty 9.3 as it the new way we want to use only
// for Jetty 9.4 to define the default servlets and filters, outside of webdefault.xml. Doing so
// will allow to enable Servlet Async capabilities later, controlled programmatically instead of
// declaratively in webdefault.xml.
public class AppEngineWebAppContext extends WebAppContext {

  // TODO: This should be some sort of Prometheus-wide
  // constant.  If it's much larger than this we may need to
  // restructure the code a bit.
  private static final int MAX_RESPONSE_SIZE = 32 * 1024 * 1024;
  private static final String ASYNC_ENABLE_PROPERTY = "enable_async_PROPERTY"; // TODO
  private static final boolean APP_IS_ASYNC = Boolean.getBoolean(ASYNC_ENABLE_PROPERTY);

  private static final String JETTY_PACKAGE = "org.eclipse.jetty.";

  // The optional file path that contains AppIds that need to ignore content length for response.
  private static final String IGNORE_CONTENT_LENGTH =
      "/base/java8_runtime/appengine.ignore-content-length";

  private final String serverInfo;
  private final List<RequestListener> requestListeners = new CopyOnWriteArrayList<>();
  private final boolean ignoreContentLength;

  @Override
  public boolean checkAlias(String path, Resource resource) {
    return true;
  }

  public AppEngineWebAppContext(File appDir, String serverInfo) {
    this(appDir, serverInfo, /* extractWar= */ true);
  }

  public AppEngineWebAppContext(File appDir, String serverInfo, boolean extractWar) {
    // We set the contextPath to / for all applications.
    super(appDir.getPath(), "/");

    // If the application fails to start, we throw so the JVM can exit.
    setThrowUnavailableOnStartupException(true);

    if (extractWar) {
      Resource webApp;
      try {
        ResourceFactory resourceFactory = ResourceFactory.of(this);
        webApp = resourceFactory.newResource(appDir.getAbsolutePath());

        if (appDir.isDirectory()) {
          setWar(appDir.getPath());
          setBaseResource(webApp);
        } else {
          // Real war file, not exploded , so we explode it in tmp area.
          createTempDirectory();
          File extractedWebAppDir = getTempDirectory();
          Resource jarWebWpp = resourceFactory.newJarFileResource(webApp.getURI());
          jarWebWpp.copyTo(extractedWebAppDir.toPath());
          setBaseResource(resourceFactory.newResource(extractedWebAppDir.getAbsolutePath()));
          setWar(extractedWebAppDir.getPath());
        }
      } catch (Exception e) {
        throw new IllegalStateException("cannot create AppEngineWebAppContext:", e);
      }
    } else {
      // Let Jetty serve directly from the war file (or directory, if it's already extracted):
      setWar(appDir.getPath());
    }

    this.serverInfo = serverInfo;

    // Configure the Jetty SecurityHandler to understand our method of
    // authentication (via the UserService).
    setSecurityHandler(EE10AppEngineAuthentication.newSecurityHandler());

    setMaxFormContentSize(MAX_RESPONSE_SIZE);

    // TODO: Can we change to a jetty-core handler? what to do on ASYNC?
    addFilter(new ParseBlobUploadFilter(), "/*", EnumSet.of(DispatcherType.REQUEST));
    ignoreContentLength = isAppIdForNonContentLength();
  }

  @Override
  protected ClassLoader configureClassLoader(ClassLoader loader) {
    // Avoid wrapping the provided classloader with WebAppClassLoader.
    return loader;
  }

  @Override
  public ServletContextApi newServletContextApi() {
    /* TODO only does this for logging?
    // Override the default HttpServletContext implementation.
    // TODO: maybe not needed when there is no securrity manager.
    // see
    // https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime/commit/43c37fd039fb619608cfffdc5461ecddb4d90ebc
    _scontext = new AppEngineServletContext();
    */

    return super.newServletContextApi();
  }

  private static boolean isAppIdForNonContentLength() {
    String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
    if (projectId == null) {
      return false;
    }
    try (Scanner s = new Scanner(new File(IGNORE_CONTENT_LENGTH), UTF_8.name())) {
      while (s.hasNext()) {
        if (projectId.equals(s.next())) {
          return true;
        }
      }
    } catch (FileNotFoundException ignore) {
      return false;
    }
    return false;
  }

  @Override
  public boolean addEventListener(EventListener listener) {
    if (super.addEventListener(listener)) {
      if (listener instanceof RequestListener) {
        requestListeners.add((RequestListener) listener);
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean removeEventListener(EventListener listener) {
    if (super.removeEventListener(listener)) {
      if (listener instanceof RequestListener) {
        requestListeners.remove((RequestListener) listener);
      }
      return true;
    }
    return false;
  }

  @Override
  public void doStart() throws Exception {
    super.doStart();
    addEventListener(new TransactionCleanupListener(getClassLoader()));
  }

  @Override
  protected void startWebapp() throws Exception {
    // startWebapp is called after the web.xml metadata has been resolved, so we can
    // clean configuration here:
    //  - Set AsyncSupported to the value defined by the system property.
    //  - Ensure known runtime filters/servlets are instantiated from this classloader
    ServletHandler servletHandler = getServletHandler();
    for (ServletHolder holder : servletHandler.getServlets()) {
      holder.setAsyncSupported(APP_IS_ASYNC);
    }
    for (FilterHolder holder : servletHandler.getFilters()) {
      holder.setAsyncSupported(APP_IS_ASYNC);
    }
    instantiateJettyServlets(servletHandler);
    instantiateJettyFilters(servletHandler);
    instantiateJettyListeners(servletHandler);
    servletHandler.setAllowDuplicateMappings(true);

    // Protect deferred task queue with constraint
    ConstraintSecurityHandler security = (ConstraintSecurityHandler) getSecurityHandler();
    ConstraintMapping cm = new ConstraintMapping();
    cm.setConstraint(
        Constraint.from("deferred_queue", Constraint.Authorization.SPECIFIC_ROLE, "admin"));
    cm.setPathSpec("/_ah/queue/__deferred__");
    security.addConstraintMapping(cm);

    // continue starting the webapp
    super.startWebapp();
  }

  /**
   * Instantiate any registrations of a jetty provided servlet
   *
   * @throws ReflectiveOperationException If a new instance of the servlet cannot be instantiated
   */
  private static void instantiateJettyServlets(ServletHandler servletHandler)
      throws ReflectiveOperationException {
    for (ServletHolder h : servletHandler.getServlets()) {
      if (h.getClassName() != null && h.getClassName().startsWith(JETTY_PACKAGE)) {
        Class<? extends Servlet> servlet =
            ServletHolder.class
                .getClassLoader()
                .loadClass(h.getClassName())
                .asSubclass(Servlet.class);
        h.setServlet(servlet.getConstructor().newInstance());
      }
    }
  }

  /**
   * Instantiate any registrations of a jetty provided filter
   *
   * @throws ReflectiveOperationException If a new instance of the filter cannot be instantiated
   */
  private static void instantiateJettyFilters(ServletHandler servletHandler)
      throws ReflectiveOperationException {
    for (FilterHolder h : servletHandler.getFilters()) {
      if (h.getClassName().startsWith(JETTY_PACKAGE)) {
        Class<? extends Filter> filter =
            ServletHolder.class
                .getClassLoader()
                .loadClass(h.getClassName())
                .asSubclass(Filter.class);
        h.setFilter(filter.getConstructor().newInstance());
      }
    }
  }

  /* Instantiate any jetty listeners from the container classloader */
  private static void instantiateJettyListeners(ServletHandler servletHandler) throws ReflectiveOperationException {
    ListenerHolder[] listeners = servletHandler.getListeners();
    if (listeners != null) {
      for (ListenerHolder h : listeners) {
        if (h.getClassName().startsWith(JETTY_PACKAGE)) {
          Class<? extends EventListener> listener =
                  ServletHandler.class
                          .getClassLoader()
                          .loadClass(h.getClassName())
                          .asSubclass(EventListener.class);
          h.setListener(listener.getConstructor().newInstance());
        }
      }
    }
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    ListIterator<RequestListener> iter = requestListeners.listIterator();
    while (iter.hasNext()) {
      iter.next().requestReceived(this, request);
    }
    try {
      if (ignoreContentLength) {
        response = new IgnoreContentLengthResponseWrapper(request, response);
      }

      return super.handle(request, response, callback);
    } finally {
      // TODO: this finally approach is ok until async request handling is supported
      while (iter.hasPrevious()) {
        iter.previous().requestComplete(this, request);
      }
    }
  }

  @Override
  protected ServletHandler newServletHandler() {
    ServletHandler handler = new ServletHandler();
    handler.setAllowDuplicateMappings(true);
    return handler;
  }

  @Override
  protected void createTempDirectory() {
    File tempDir = getTempDirectory();
    if (tempDir != null) {
      // Someone has already set the temp directory.
      super.createTempDirectory();
      return;
    }

    File baseDir = new File(Objects.requireNonNull(JAVA_IO_TMPDIR.value()));
    String baseName = System.currentTimeMillis() + "-";

    for (int counter = 0; counter < 10; counter++) {
      tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        if (!isTempDirectoryPersistent()) {
          tempDir.deleteOnExit();
        }

        setTempDirectory(tempDir);
        return;
      }
    }
    throw new IllegalStateException("Failed to create directory ");
  }

  // N.B.: Yuck.  Jetty hardcodes all of this logic into an
  // inner class of ContextHandler.  We need to subclass WebAppContext
  // (which extends ContextHandler) and then subclass the SContext
  // inner class to modify its behavior.

  /** A context that uses our logs API to log messages. */
  public class AppEngineServletContext extends ServletContextApi {

    @Override
    public ClassLoader getClassLoader() {
      return AppEngineWebAppContext.this.getClassLoader();
    }

    @Override
    public String getServerInfo() {
      return serverInfo;
    }

    @Override
    public void log(String message) {
      log(message, null);
    }

    /**
     * {@inheritDoc}
     *
     * @param throwable an exception associated with this log message, or {@code null}.
     */
    @Override
    public void log(String message, Throwable throwable) {
      StringWriter writer = new StringWriter();
      writer.append("javax.servlet.ServletContext log: ");
      writer.append(message);

      if (throwable != null) {
        writer.append("\n");
        throwable.printStackTrace(new PrintWriter(writer));
      }

      LogRecord.Level logLevel = throwable == null ? LogRecord.Level.info : LogRecord.Level.error;
      ApiProxy.log(
          new ApiProxy.LogRecord(logLevel, System.currentTimeMillis() * 1000L, writer.toString()));
    }
  }
}
