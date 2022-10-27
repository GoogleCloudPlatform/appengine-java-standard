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

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.SessionsConfig;
import com.google.apphosting.runtime.jetty9.AppEngineWebInfConfiguration;
import com.google.apphosting.runtime.jetty9.JettyConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.html.HtmlEscapers;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspFactory;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * {@code AppVersionHandlerFactory} implements a {@code Handler} for a given {@code AppVersionKey}.
 */
public class AppVersionHandlerFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final String TOMCAT_SIMPLE_INSTANCE_MANAGER =
      "org.apache.tomcat.SimpleInstanceManager";
  private static final String TOMCAT_INSTANCE_MANAGER = "org.apache.tomcat.InstanceManager";
  private static final String TOMCAT_JSP_FACTORY = "org.apache.jasper.runtime.JspFactoryImpl";

  /**
   * Any settings in this webdefault.xml file will be inherited by all applications. We don't want
   * to use Jetty's built-in webdefault.xml because we want to disable some of their functionality,
   * and because we want to be explicit about what functionality we are supporting.
   */
  public static final String WEB_DEFAULTS_XML =
      "com/google/apphosting/runtime/jetty94/webdefault.xml";

  /**
   * This property will be used to enable/disable Annotation Scanning when quickstart-web.xml is not
   * present.
   */
  private static final String USE_ANNOTATION_SCANNING = "use.annotationscanning";

  /**
   * Specify which {@link org.eclipse.jetty.webapp.Configuration} objects should be invoked when
   * configuring a web application.
   *
   * <p>This is a subset of: org.mortbay.jetty.webapp.WebAppContext.__dftConfigurationClasses
   *
   * <p>Specifically, we've removed {@link org.mortbay.jetty.webapp.JettyWebXmlConfiguration} which
   * allows users to use {@code jetty-web.xml} files. We definitely do not want to allow these
   * files, as they allow for arbitrary method invocation.
   */

  // List of all the standard Jetty configurations that need to be executed when there
  // is no WEB-INF/quickstart-web.xml file.
  private final String[] preconfigurationClasses = getPreconfigurationClasses();

  // List of Jetty configurations only needed if the quickstart process has been
  // executed, so we do not need the webinf, webxml, fragment and annotation configurations
  // because they have been executed via the GAE SDK Jetty staging phase that creates the
  // WEB-INF/quickstart-web.xml file.
  // You can read more at https://webtide.com/jetty-9-quick-start.
  private static final String[] quickstartConfigurationClasses = {
    com.google.apphosting.runtime.jetty9.AppEngineQuickStartConfiguration.class.getCanonicalName(),
  };

  /**
   * A "private" request attribute to indicate if the dispatch to a most recent error page has run
   * to completion. Note an error page itself may generate errors.
   */
  static final String ERROR_PAGE_HANDLED = WebAppContext.ERROR_PAGE + ".handled";

  private final Server server;
  private final String serverInfo;
  private final WebAppContextFactory contextFactory;
  private final boolean useJettyErrorPageHandler;

  public AppVersionHandlerFactory(
      Server server,
      String serverInfo,
      WebAppContextFactory contextFactory,
      boolean useJettyErrorPageHandler) {
    this.server = server;
    this.serverInfo = serverInfo;
    this.contextFactory = contextFactory;
    this.useJettyErrorPageHandler = useJettyErrorPageHandler;
  }

  /**
   * Returns the {@code Handler} that will handle requests for the specified application version.
   */
  public Handler createHandler(AppVersion appVersion) throws ServletException {
    // Need to set thread context classloader for the duration of the scope.
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    try {
      Handler handler = doCreateHandler(appVersion);
      server.addBean(handler);
      return handler;
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
    }
  }

  private final String[] getPreconfigurationClasses() {
    ImmutableList.Builder<String> list = new ImmutableList.Builder<>();
    list.add(AppEngineWebInfConfiguration.class.getCanonicalName());
    list.add(WebXmlConfiguration.class.getCanonicalName());
    list.add(MetaInfConfiguration.class.getCanonicalName());
    list.add(FragmentConfiguration.class.getCanonicalName());
    if (Boolean.getBoolean(USE_ANNOTATION_SCANNING)) {
      list.add(AnnotationConfiguration.class.getCanonicalName());
    }
    return list.build().stream().toArray(String[]::new);
  }

  private Handler doCreateHandler(AppVersion appVersion) throws ServletException {
    AppVersionKey appVersionKey = appVersion.getKey();
    try {
      File contextRoot = appVersion.getRootDirectory();

      final AppEngineWebAppContext context = contextFactory.createContext(appVersion, serverInfo);
      context.setServer(server);
      context.setDefaultsDescriptor(WEB_DEFAULTS_XML);
      context.setClassLoader(appVersion.getClassLoader());
      if (useJettyErrorPageHandler) {
        context.getErrorHandler().setShowStacks(false);
      } else {
        context.setErrorHandler(new NullErrorHandler());
      }
      File qswebxml = new File(contextRoot, "WEB-INF/quickstart-web.xml");
      if (qswebxml.exists()) {
        context.setConfigurationClasses(quickstartConfigurationClasses);
      } else {
        context.setConfigurationClasses(preconfigurationClasses);
      }

      // prevent jetty from trying to delete the temp dir
      context.setPersistTempDirectory(true);
      // ensure jetty does not unpack, probably not necessary because the unpacking
      // is done by AppEngineWebAppContext
      context.setExtractWAR(false);
      // ensure exception is thrown if context startup fails
      context.setThrowUnavailableOnStartupException(true);
      // for JSP 2.2

      try {
        // Use the App Class loader to try to initialize the JSP machinery.
        // Not an issue if it fails: it means the app does not contain the JSP jars in WEB-INF/lib.
        Class<?> klass = appVersion.getClassLoader().loadClass(TOMCAT_SIMPLE_INSTANCE_MANAGER);
        Object sim = klass.getConstructor().newInstance();
        context.getServletContext().setAttribute(TOMCAT_INSTANCE_MANAGER, sim);
        // Set JSP factory equivalent for:
        // JspFactory jspf = new JspFactoryImpl();
        klass = appVersion.getClassLoader().loadClass(TOMCAT_JSP_FACTORY);
        JspFactory jspf = (JspFactory) klass.getConstructor().newInstance();
        JspFactory.setDefaultFactory(jspf);
        Class.forName(
            "org.apache.jasper.compiler.JspRuntimeContext", true, appVersion.getClassLoader());
      } catch (Throwable t) {
        // No big deal, there are no JSPs in the App since the jsp libraries are not inside the
        // web app classloader.
      }

      SessionsConfig sessionsConfig = appVersion.getSessionsConfig();
      SessionManagerHandler.Config.Builder builder = SessionManagerHandler.Config.builder();
      if (sessionsConfig.getAsyncPersistenceQueueName() != null) {
        builder.setAsyncPersistenceQueueName(sessionsConfig.getAsyncPersistenceQueueName());
      }
      builder
          .setEnableSession(sessionsConfig.isEnabled())
          .setAsyncPersistence(sessionsConfig.isAsyncPersistence())
          .setServletContextHandler(context);

      SessionManagerHandler.create(builder.build());
      // Pass the AppVersion on to any of our servlets (e.g. ResourceFileServlet).
      context.setAttribute(JettyConstants.APP_VERSION_CONTEXT_ATTR, appVersion);

      context.start();
      // Check to see if servlet filter initialization failed.
      Throwable unavailableCause = context.getUnavailableException();
      if (unavailableCause != null) {
        if (unavailableCause instanceof ServletException) {
          throw (ServletException) unavailableCause;
        } else {
          UnavailableException unavailableException =
              new UnavailableException("Initialization failed.");
          unavailableException.initCause(unavailableCause);
          throw unavailableException;
        }
      }

      return context;
    } catch (ServletException ex) {
      logger.atWarning().withCause(ex).log("Exception adding %s", appVersionKey);
      throw ex;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  /**
   * {@code NullErrorHandler} does nothing when an error occurs. The exception is already stored in
   * an attribute of {@code request}, but we don't do any rendering of it into the response, UNLESS
   * the webapp has a designated error page (servlet, jsp, or static html) for the current error
   * condition (exception type or error code).
   */
  private static class NullErrorHandler extends ErrorPageErrorHandler {

    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException {

      logger.atFine().log("Custom Jetty ErrorHandler received an error notification.");
      mayHandleByErrorPage(request, response);
      // We don't want Jetty to do anything further.
      baseRequest.setHandled(true);
    }

    /**
     * Try to invoke a custom error page if a handler is available. If not, render a simple HTML
     * response for {@link HttpServletResponse#sendError} calls, but do nothing for unhandled
     * exceptions.
     *
     * <p>This is loosely based on {@link ErrorPageErrorHandler#handle} but has been modified to add
     * a fallback simple HTML response (because Jetty's default response is not satisfactory) and to
     * set a special {@code ERROR_PAGE_HANDLED} attribute that disables our default behavior of
     * returning the exception to the appserver for rendering.
     */
    private void mayHandleByErrorPage(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      // Extract some error handling info from Jetty's proprietary attributes.
      Class<?> exClass = (Class<?>) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
      Integer code = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
      String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

      // Now try to find an error handler...
      String errorPage = getErrorPage(request);

      // If we found an error handler, dispatch to it.
      if (errorPage != null) {
        // Check for reentry into the same error page.
        String oldErrorPage = (String) request.getAttribute(WebAppContext.ERROR_PAGE);
        if (oldErrorPage == null || !oldErrorPage.equals(errorPage)) {
          request.setAttribute(WebAppContext.ERROR_PAGE, errorPage);
          Dispatcher dispatcher = (Dispatcher) _servletContext.getRequestDispatcher(errorPage);
          try {
            if (dispatcher != null) {
              dispatcher.error(request, response);
              // Set this special attribute iff the dispatch actually works!
              // We use this attribute to decide if we want to keep the response content
              // or let the Runtime generate the default error page
              // TODO: an invalid html dispatch (404) will mask the exception
              request.setAttribute(ERROR_PAGE_HANDLED, errorPage);
              return;
            } else {
              logger.atWarning().log("No error page %s", errorPage);
            }
          } catch (ServletException e) {
            logger.atWarning().withCause(e).log("Failed to handle error page.");
          }
        }
      }

      // If we got an error code but not an exception (e.g. this is a
      // call to HttpServletResponse#sendError), then render our own
      // HTML.  XFE has logic to do this, but the PFE only invokes it
      // for error conditions that it or the AppServer detect.
      if (exClass == null && code != null && message != null) {
        // This template is based on the default XFE error response.
        response.setContentType("text/html; charset=UTF-8");

        String messageEscaped = HtmlEscapers.htmlEscaper().escape(message);

        PrintWriter writer = response.getWriter();
        writer.println("<html><head>");
        writer.println("<meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\">");
        writer.println("<title>" + code + " " + messageEscaped + "</title>");
        writer.println("</head>");
        writer.println("<body text=#000000 bgcolor=#ffffff>");
        writer.println("<h1>Error: " + messageEscaped + "</h1>");
        writer.println("</body></html>");
      }

      // If we got this far and *did* have an exception, it will be
      // retrieved and thrown at the end of JettyServletEngineAdapter#serviceRequest.
    }
  }
}
