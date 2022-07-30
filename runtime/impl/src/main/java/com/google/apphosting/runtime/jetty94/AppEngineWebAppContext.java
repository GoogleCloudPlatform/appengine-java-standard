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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.runtime.jetty9.AppEngineAuthentication;
import com.google.apphosting.runtime.jetty9.ParseBlobUploadHandler;
import com.google.apphosting.runtime.jetty9.RequestListener;
import com.google.apphosting.runtime.jetty9.TransactionCleanupListener;
import com.google.apphosting.utils.servlet.DeferredTaskServlet;
import com.google.apphosting.utils.servlet.JdbcMySqlConnectionCleanupFilter;
import com.google.apphosting.utils.servlet.SessionCleanupServlet;
import com.google.apphosting.utils.servlet.SnapshotServlet;
import com.google.apphosting.utils.servlet.WarmupServlet;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.Holder;
import org.eclipse.jetty.servlet.ListenerHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.webapp.WebAppContext;

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
  private static final boolean APP_IS_ASYNC =
      Boolean.getBoolean(RpcConnection.ASYNC_ENABLE_PPROPERTY);

  private static final String JETTY_PACKAGE = "org.eclipse.jetty.";

  // The optional file path that contains AppIds that need to ignore content length for response.
  private static final String IGNORE_CONTENT_LENGTH =
      "/base/java8_runtime/appengine.ignore-content-length";

  private final String serverInfo;
  private final boolean extractWar;
  private final List<RequestListener> requestListeners = new CopyOnWriteArrayList<>();
  private final boolean ignoreContentLength;

  // These are deprecated filters and servlets
  private static final ImmutableSet<HolderMatcher> DEPRECATED_SERVLETS_FILTERS =
      ImmutableSet.of(
          // Remove unused filters that may still be instantiated by
          // deprecated webdefault.xml in old SDKs
          new HolderMatcher(
              "AbandonedTransactionDetector",
              "com.google.apphosting.utils.servlet.TransactionCleanupFilter"),
          new HolderMatcher(
              "SaveSessionFilter", "com.google.apphosting.runtime.jetty9.SaveSessionFilter"),
          new HolderMatcher(
              "_ah_ParseBlobUploadFilter",
              "com.google.apphosting.utils.servlet.ParseBlobUploadFilter"),
          new HolderMatcher(
              "_ah_default", "com.google.apphosting.runtime.jetty9.ResourceFileServlet"),
          new HolderMatcher("default", "com.google.apphosting.runtime.jetty9.NamedDefaultServlet"),
          new HolderMatcher("jsp", "com.google.apphosting.runtime.jetty9.NoJspSerlvet"),

          // remove application filters and servlets that are known to only be applicable to
          // the java 7 runtime
          new HolderMatcher(null, "com.google.appengine.tools.appstats.AppstatsFilter"),
          new HolderMatcher(null, "com.google.appengine.tools.appstats.AppstatsServlet"));

  @Override
  public boolean checkAlias(String path, Resource resource) {
    return true;
  }

  public AppEngineWebAppContext(File appDir, String serverInfo) {
    this(appDir, serverInfo, /*extractWar=*/ true);
  }

  public AppEngineWebAppContext(File appDir, String serverInfo, boolean extractWar) {
    // We set the contextPath to / for all applications.
    super(appDir.getPath(), URIUtil.SLASH);

    this.extractWar = extractWar;

    // If the application fails to start, we throw so the JVM can exit.
    setThrowUnavailableOnStartupException(true);

    if (extractWar) {
      Resource webApp = null;
      try {
        webApp = Resource.newResource(appDir.getAbsolutePath());

        if (appDir.isDirectory()) {
          setWar(appDir.getPath());
          setBaseResource(webApp);
        } else {
          // Real war file, not exploded , so we explode it in tmp area.
          File extractedWebAppDir = createTempDir();
          extractedWebAppDir.mkdir();
          extractedWebAppDir.deleteOnExit();
          Resource jarWebWpp = JarResource.newJarResource(webApp);
          jarWebWpp.copyTo(extractedWebAppDir);
          setBaseResource(Resource.newResource(extractedWebAppDir.getAbsolutePath()));
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

    // Override the default HttpServletContext implementation.
    // TODO: maybe not needed when there is no securrity manager.
    // see
    // https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime/commit/43c37fd039fb619608cfffdc5461ecddb4d90ebc
    _scontext = new AppEngineServletContext();

    // Configure the Jetty SecurityHandler to understand our method of
    // authentication (via the UserService).
    AppEngineAuthentication.configureSecurityHandler(
        (ConstraintSecurityHandler) getSecurityHandler());

    setMaxFormContentSize(MAX_RESPONSE_SIZE);

    insertHandler(new ParseBlobUploadHandler());
    ignoreContentLength = isAppIdForNonContentLength();
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
  public void addEventListener(EventListener listener) {
    super.addEventListener(listener);
    if (listener instanceof RequestListener) {
      requestListeners.add((RequestListener) listener);
    }
  }

  @Override
  public void removeEventListener(EventListener listener) {
    super.removeEventListener(listener);
    if (listener instanceof RequestListener) {
      requestListeners.remove((RequestListener) listener);
    }
  }

  @Override
  public void doStart() throws Exception {
    super.doStart();
    addEventListener(new TransactionCleanupListener(getClassLoader()));
  }

  @Override
  protected void startWebapp() throws Exception {

    // startWebapp is called after the web.xml meta data has been resolved, so we can
    // clean configuration here:
    //  - Removed deprecated filters and servlets
    //  - Ensure known runtime filters/servlets are instantiated from this classloader
    //  - Ensure known runtime mappings exist.
    ServletHandler servletHandler = getServletHandler();
    TrimmedFilters trimmedFilters =
        new TrimmedFilters(
            servletHandler.getFilters(),
            servletHandler.getFilterMappings(),
            DEPRECATED_SERVLETS_FILTERS);
    trimmedFilters.ensure(
        "CloudSqlConnectionCleanupFilter", JdbcMySqlConnectionCleanupFilter.class, "/*");

    TrimmedServlets trimmedServlets =
        new TrimmedServlets(
            servletHandler.getServlets(),
            servletHandler.getServletMappings(),
            DEPRECATED_SERVLETS_FILTERS);
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
    ConstraintSecurityHandler security = getChildHandlerByClass(ConstraintSecurityHandler.class);
    Constraint c = new Constraint("deferred_queue", "admin");
    c.setAuthenticate(true);
    ConstraintMapping cm = new ConstraintMapping();
    cm.setConstraint(c);
    cm.setPathSpec("/_ah/queue/__deferred__");
    security.addConstraintMapping(cm);

    // continue starting the webapp
    super.startWebapp();
  }

  @Override
  public void doHandle(
      String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    ListIterator<RequestListener> iter = requestListeners.listIterator();
    while (iter.hasNext()) {
      iter.next().requestReceived(this, baseRequest);
    }
    try {
      if (ignoreContentLength) {
        response = new IgnoreContentLengthResponseWrapper(response);
      }
      super.doHandle(target, baseRequest, request, response);
    } finally {
      // TODO: this finally approach is ok until async request handling is supported
      while (iter.hasPrevious()) {
        iter.previous().requestComplete(this, baseRequest);
      }
    }
  }

  @Override
  protected ServletHandler newServletHandler() {
    ServletHandler handler = new ServletHandler();
    handler.setAllowDuplicateMappings(true);
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

  private static File createTempDir() {
    File baseDir = new File(JAVA_IO_TMPDIR.value());
    String baseName = System.currentTimeMillis() + "-";

    for (int counter = 0; counter < 10; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        return tempDir;
      }
    }
    throw new IllegalStateException("Failed to create directory ");
  }

  /**
   * Jetty needs a temp directory that already exists, so we point it to the directory of the war.
   * Since we don't allow Jetty to do any actual writes, this isn't a problem. It'd be nice to just
   * use setTempDirectory, but Jetty tests to see if it's writable.
   */
  @Override
  public File getTempDirectory() {
    if (extractWar) {
      return new File(getWar());
    }

    return super.getTempDirectory();
  }

  /**
   * Set temporary directory for context. The javax.servlet.context.tempdir attribute is also set.
   *
   * @param dir Writable temporary directory.
   */
  @Override
  public void setTempDirectory(File dir) {

    if (dir != null && !dir.exists()) {
      dir.mkdir();
    }
    super.setTempDirectory(dir);
  }

  // N.B.: Yuck.  Jetty hardcodes all of this logic into an
  // inner class of ContextHandler.  We need to subclass WebAppContext
  // (which extends ContextHandler) and then subclass the SContext
  // inner class to modify its behavior.

  /** A context that uses our logs API to log messages. */
  public class AppEngineServletContext extends WebAppContext.Context {

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

    @Override
    public void log(Exception exception, String msg) {
      log(msg, exception);
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

  /**
   * TrimmedServlets is in charge of handling web applications that got deployed previously with the
   * previous webdefault.xml content(prior to this CL changing it). We still need to be able to load
   * old apps defined with the previous webdefault.xml file that generated an obsolete
   * quickstart.xml having the servlets defined in webdefault.xml.
   *
   * <p>New deployements would not need this processing (no-op), but we need to handle all apps,
   * deployed now or in the past.
   */
  private static class TrimmedServlets {
    private final Map<String, ServletHolder> holders = new HashMap<>();
    private final List<ServletMapping> mappings = new ArrayList<>();

    TrimmedServlets(
        ServletHolder[] holders, ServletMapping[] mappings, Set<HolderMatcher> deprecations) {
      for (ServletHolder servletHolder : holders) {
        boolean deprecated = false;
        servletHolder.setAsyncSupported(APP_IS_ASYNC);
        for (HolderMatcher holderMatcher : deprecations) {
          deprecated |= holderMatcher.appliesTo(servletHolder);
        }

        if (!deprecated) {
          this.holders.put(servletHolder.getName(), servletHolder);
        }
      }

      for (ServletMapping m : mappings) {
        this.mappings.add(m);
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
            mapping.setDefault(true);
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

    TrimmedFilters(
        FilterHolder[] holders, FilterMapping[] mappings, Set<HolderMatcher> deprecations) {
      for (FilterHolder h : holders) {
        boolean deprecated = false;
        h.setAsyncSupported(APP_IS_ASYNC);
        for (HolderMatcher m : deprecations) {
          deprecated |= m.appliesTo(h);
        }

        if (!deprecated) {
          this.holders.put(h.getName(), h);
        }
      }

      for (FilterMapping m : mappings) {
        this.mappings.add(m);
      }
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
