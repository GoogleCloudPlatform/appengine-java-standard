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
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.jetty.EE10AppEngineAuthentication;
import com.google.apphosting.utils.servlet.ee10.DeferredTaskServlet;
import com.google.apphosting.utils.servlet.ee10.JdbcMySqlConnectionCleanupFilter;
import com.google.apphosting.utils.servlet.ee10.SessionCleanupServlet;
import com.google.apphosting.utils.servlet.ee10.SnapshotServlet;
import com.google.apphosting.utils.servlet.ee10.WarmupServlet;
import com.google.common.collect.ImmutableMap;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.FilterMapping;
import org.eclipse.jetty.ee10.servlet.Holder;
import org.eclipse.jetty.ee10.servlet.ListenerHolder;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.ServletMapping;
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

  // Map of deprecated package names to their replacements.
  private static final Map<String, String> DEPRECATED_PACKAGE_NAMES = ImmutableMap.of(
          "org.eclipse.jetty.servlets", "org.eclipse.jetty.ee10.servlets"
  );

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
    //  - Ensure known runtime filters/servlets are instantiated from this classloader
    //  - Ensure known runtime mappings exist.
    ServletHandler servletHandler = getServletHandler();
    TrimmedFilters trimmedFilters =
        new TrimmedFilters(servletHandler.getFilters(), servletHandler.getFilterMappings());
    trimmedFilters.ensure(
        "CloudSqlConnectionCleanupFilter", JdbcMySqlConnectionCleanupFilter.class, "/*");

    TrimmedServlets trimmedServlets =
        new TrimmedServlets(servletHandler.getServlets(), servletHandler.getServletMappings());
    trimmedServlets.ensure("_ah_warmup", WarmupServlet.class, "/_ah/warmup");
    trimmedServlets.ensure(
        "_ah_sessioncleanup", SessionCleanupServlet.class, "/_ah/sessioncleanup");
    trimmedServlets.ensure(
        "_ah_queue_deferred", DeferredTaskServlet.class, "/_ah/queue/__deferred__");
    trimmedServlets.ensure("_ah_snapshot", SnapshotServlet.class, "/_ah/snapshot");
    trimmedServlets.ensure("_ah_default", ResourceFileServlet.class, "/");
    trimmedServlets.ensure("default", NamedDefaultServlet.class);
    trimmedServlets.ensure("jsp", NamedJspServlet.class);

    trimmedServlets.instantiateJettyServlets();
    trimmedFilters.instantiateJettyFilters();
    instantiateJettyListeners();

    servletHandler.setFilters(trimmedFilters.getHolders());
    servletHandler.setFilterMappings(trimmedFilters.getMappings());
    servletHandler.setServlets(trimmedServlets.getHolders());
    servletHandler.setServletMappings(trimmedServlets.getMappings());
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
    if (AppEngineConstants.LEGACY_MODE) {
      handler.setDecodeAmbiguousURIs(true);
    }
    return handler;
  }

  /* Instantiate any jetty listeners from the container classloader */
  private void instantiateJettyListeners() throws ReflectiveOperationException {
    ListenerHolder[] listeners = getServletHandler().getListeners();
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

  /** A class to hold a Holder name and/or className and/or source location for matching. */
  private static class HolderMatcher {
    final String name;
    final String className;

    /**
     * @param name The name of a filter/servlet to match, or null if not matching on name.
     * @param className The class name of a filter/servlet to match, or null if not matching on
     *     className
     */
    HolderMatcher(String name, String className) {
      this.name = name;
      this.className = className;
    }

    /**
     * @param holder The holder to match
     * @return true IFF this matcher matches the holder.
     */
    boolean appliesTo(Holder<?> holder) {
      if (name != null && !name.equals(holder.getName())) {
        return false;
      }

      if (className != null && !className.equals(holder.getClassName())) {
        return false;
      }

      return true;
    }
  }

  private static class TrimmedServlets {
    private final Map<String, ServletHolder> holders = new HashMap<>();
    private final List<ServletMapping> mappings = new ArrayList<>();

    TrimmedServlets(ServletHolder[] holders, ServletMapping[] mappings) {
      for (ServletHolder h : holders) {

        // Replace deprecated package names.
        String className = h.getClassName();
        if (className != null)
        {
          DEPRECATED_PACKAGE_NAMES.forEach((deprecated, replacement) ->
                  h.setClassName(className.replace(deprecated, replacement)));
        }

        h.setAsyncSupported(APP_IS_ASYNC);
        this.holders.put(h.getName(), h);
      }
      this.mappings.addAll(Arrays.asList(mappings));
    }

    /**
     * Ensure the registration of a container provided servlet:
     *
     * <ul>
     *   <li>If any existing servlet registrations are for the passed servlet class, then their
     *       holder is updated with a new instance created on the containers classpath.
     *   <li>If a servlet registration for the passed servlet name does not exist, one is created to
     *       the passed servlet class.
     * </ul>
     *
     * @param name The servlet name
     * @param servlet The servlet class
     * @throws ReflectiveOperationException If a new instance of the servlet cannot be instantiated
     */
    void ensure(String name, Class<? extends Servlet> servlet) throws ReflectiveOperationException {
      // Instantiate any holders referencing this servlet (may be application instances)
      for (ServletHolder h : holders.values()) {
        if (servlet.getName().equals(h.getClassName())) {
          h.setServlet(servlet.getConstructor().newInstance());
          h.setAsyncSupported(APP_IS_ASYNC);
        }
      }

      // Look for (or instantiate) our named instance
      ServletHolder holder = holders.get(name);
      if (holder == null) {
        holder = new ServletHolder(servlet.getConstructor().newInstance());
        holder.setInitOrder(1);
        holder.setName(name);
        holder.setAsyncSupported(APP_IS_ASYNC);
        holders.put(name, holder);
      }
    }

    /**
     * Ensure the registration of a container provided servlet:
     *
     * <ul>
     *   <li>If any existing servlet registrations are for the passed servlet class, then their
     *       holder is updated with a new instance created on the containers classpath.
     *   <li>If a servlet registration for the passed servlet name does not exist, one is created to
     *       the passed servlet class.
     *   <li>If a servlet mapping for the passed servlet name and pathSpec does not exist, one is
     *       created.
     * </ul>
     *
     * @param name The servlet name
     * @param servlet The servlet class
     * @param pathSpec The servlet pathspec
     * @throws ReflectiveOperationException If a new instance of the servlet cannot be instantiated
     */
    void ensure(String name, Class<? extends Servlet> servlet, String pathSpec)
        throws ReflectiveOperationException {
      // Ensure Servlet
      ensure(name, servlet);

      // Ensure mapping
      if (pathSpec != null) {
        boolean mapped = false;
        for (ServletMapping mapping : mappings) {
          if (mapping.containsPathSpec(pathSpec)) {
            mapped = true;
            break;
          }
        }
        if (!mapped) {
          ServletMapping mapping = new ServletMapping();
          mapping.setServletName(name);
          mapping.setPathSpec(pathSpec);
          if (pathSpec.equals("/")) {
            mapping.setFromDefaultDescriptor(true);
          }
          mappings.add(mapping);
        }
      }
    }

    /**
     * Instantiate any registrations of a jetty provided servlet
     *
     * @throws ReflectiveOperationException If a new instance of the servlet cannot be instantiated
     */
    void instantiateJettyServlets() throws ReflectiveOperationException {
      for (ServletHolder h : holders.values()) {
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

    ServletHolder[] getHolders() {
      return holders.values().toArray(new ServletHolder[0]);
    }

    ServletMapping[] getMappings() {
      List<ServletMapping> trimmed = new ArrayList<>(mappings.size());
      for (ServletMapping m : mappings) {
        if (this.holders.containsKey(m.getServletName())) {
          trimmed.add(m);
        }
      }
      return trimmed.toArray(new ServletMapping[0]);
    }
  }

  private static class TrimmedFilters {
    private final Map<String, FilterHolder> holders = new HashMap<>();
    private final List<FilterMapping> mappings = new ArrayList<>();

    TrimmedFilters(FilterHolder[] holders, FilterMapping[] mappings) {
      for (FilterHolder h : holders) {

        // Replace deprecated package names.
        String className = h.getClassName();
        if (className != null)
        {
          DEPRECATED_PACKAGE_NAMES.forEach((deprecated, replacement) ->
                  h.setClassName(className.replace(deprecated, replacement)));
        }

        h.setAsyncSupported(APP_IS_ASYNC);
        this.holders.put(h.getName(), h);
      }
      this.mappings.addAll(Arrays.asList(mappings));
    }

    /**
     * Ensure the registration of a container provided filter:
     *
     * <ul>
     *   <li>If any existing filter registrations are for the passed filter class, then their holder
     *       is updated with a new instance created on the containers classpath.
     *   <li>If a filter registration for the passed filter name does not exist, one is created to
     *       the passed filter class.
     *   <li>If a filter mapping for the passed filter name and pathSpec does not exist, one is
     *       created.
     * </ul>
     *
     * @param name The filter name
     * @param filter The filter class
     * @param pathSpec The servlet pathspec
     * @throws ReflectiveOperationException If a new instance of the servlet cannot be instantiated
     */
    void ensure(String name, Class<? extends Filter> filter, String pathSpec) throws Exception {

      // Instantiate any holders referencing this filter (may be application instances)
      for (FilterHolder h : holders.values()) {
        if (filter.getName().equals(h.getClassName())) {
          h.setFilter(filter.getConstructor().newInstance());
          h.setAsyncSupported(APP_IS_ASYNC);
        }
      }

      // Look for (or instantiate) our named instance
      FilterHolder holder = holders.get(name);
      if (holder == null) {
        holder = new FilterHolder(filter.getConstructor().newInstance());
        holder.setName(name);
        holders.put(name, holder);
        holder.setAsyncSupported(APP_IS_ASYNC);
      }

      // Ensure mapping
      boolean mapped = false;
      for (FilterMapping mapping : mappings) {

        for (String ps : mapping.getPathSpecs()) {
          if (pathSpec.equals(ps) && name.equals(mapping.getFilterName())) {
            mapped = true;
            break;
          }
        }
      }
      if (!mapped) {
        FilterMapping mapping = new FilterMapping();
        mapping.setFilterName(name);
        mapping.setPathSpec(pathSpec);
        mapping.setDispatches(FilterMapping.REQUEST);
        mappings.add(mapping);
      }
    }

    /**
     * Instantiate any registrations of a jetty provided filter
     *
     * @throws ReflectiveOperationException If a new instance of the filter cannot be instantiated
     */
    void instantiateJettyFilters() throws ReflectiveOperationException {
      for (FilterHolder h : holders.values()) {
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

    FilterHolder[] getHolders() {
      return holders.values().toArray(new FilterHolder[0]);
    }

    FilterMapping[] getMappings() {
      List<FilterMapping> trimmed = new ArrayList<>(mappings.size());
      for (FilterMapping m : mappings) {
        if (this.holders.containsKey(m.getFilterName())) {
          trimmed.add(m);
        }
      }
      return trimmed.toArray(new FilterMapping[0]);
    }
  }
}
