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

import static com.google.apphosting.runtime.AppEngineConstants.HTTP_CONNECTOR_MODE;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.SessionsConfig;
import com.google.apphosting.runtime.jetty.AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty.EE10SessionManagerHandler;
import com.google.common.flogger.GoogleLogger;
import com.google.common.html.HtmlEscapers;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.jsp.JspFactory;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.quickstart.QuickStartConfiguration;
import org.eclipse.jetty.ee10.servlet.Dispatcher;
import org.eclipse.jetty.ee10.servlet.ErrorHandler;
import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.ee10.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee10.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebXmlConfiguration;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Callback;

/**
 * {@code AppVersionHandlerFactory} implements a {@code Handler} for a given {@code AppVersionKey}.
 */
public class EE10AppVersionHandlerFactory implements AppVersionHandlerFactory {
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
          "com/google/apphosting/runtime/jetty/webdefault.xml";
  /**
   * This property will be used to enable/disable Annotation Scanning when quickstart-web.xml is not
   * present.
   */
  private static final String USE_ANNOTATION_SCANNING = "use.annotationscanning";
  /**
   * A "private" request attribute to indicate if the dispatch to a most recent error page has run
   * to completion. Note an error page itself may generate errors.
   */
  static final String ERROR_PAGE_HANDLED = ErrorHandler.ERROR_PAGE + ".handled";
  private final Server server;
  private final String serverInfo;
  private final boolean useJettyErrorPageHandler;
  public EE10AppVersionHandlerFactory(Server server, String serverInfo) {
    this(server, serverInfo, false);
  }
  public EE10AppVersionHandlerFactory(
      Server server, String serverInfo, boolean useJettyErrorPageHandler) {
    this.server = server;
    this.serverInfo = serverInfo;
    this.useJettyErrorPageHandler = useJettyErrorPageHandler;
  }
  /**
   * Returns the {@code Handler} that will handle requests for the specified application version.
   */
  @Override
  public org.eclipse.jetty.server.Handler createHandler(AppVersion appVersion) throws ServletException {
    // Need to set thread context classloader for the duration of the scope.
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    try {
      return doCreateHandler(appVersion);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
    }
  }
  private org.eclipse.jetty.server.Handler doCreateHandler(AppVersion appVersion) throws ServletException {
    try {
      File contextRoot = appVersion.getRootDirectory();
      final AppEngineWebAppContext context =
          new AppEngineWebAppContext(
              appVersion.getRootDirectory(), serverInfo, /* extractWar=*/ false);
      context.setServer(server);
      context.setDefaultsDescriptor(WEB_DEFAULTS_XML);
      ClassLoader classLoader = appVersion.getClassLoader();
      context.setClassLoader(classLoader);
      if (useJettyErrorPageHandler) {
        ((ErrorHandler) context.getErrorHandler()).setShowStacks(false);
      } else {
        context.setErrorHandler(new NullErrorHandler());
      }
      // TODO: because of the shading we do not have a correct
      // org.eclipse.jetty.ee10.webapp.Configuration file from
      //  the runtime-impl jar. It failed to merge content from various modules and only contains
      // quickstart.
      //  Because of this the default configurations are not able to be found by WebAppContext with
      // ServiceLoader.
      context.setConfigurationClasses(
          new String[] {
            WebInfConfiguration.class.getCanonicalName(),
            WebXmlConfiguration.class.getCanonicalName(),
            MetaInfConfiguration.class.getCanonicalName(),
            FragmentConfiguration.class.getCanonicalName()
          });
      /*
       * Remove JettyWebXmlConfiguration which allows users to use jetty-web.xml files.
       * We definitely do not want to allow these files, as they allow for arbitrary method invocation.
       */
      // TODO: uncomment when shaded org.eclipse.jetty.ee10.webapp.Configuration is fixed.
      // context.removeConfiguration(new JettyWebXmlConfiguration());
      if (Boolean.getBoolean(USE_ANNOTATION_SCANNING)) {
        context.addConfiguration(new AnnotationConfiguration());
      }
      else {
        context.removeConfiguration(new AnnotationConfiguration());
      }
      File quickstartXml = new File(contextRoot, "WEB-INF/quickstart-web.xml");
      if (quickstartXml.exists()) {
        context.addConfiguration(new QuickStartConfiguration());
      }
      else {
        context.removeConfiguration(new QuickStartConfiguration());
      }
      // TODO: review which configurations are added by default.
      // prevent jetty from trying to delete the temp dir
      context.setTempDirectoryPersistent(true);
      // ensure jetty does not unpack, probably not necessary because the unpacking
      // is done by AppEngineWebAppContext
      context.setExtractWAR(false);
      // ensure exception is thrown if context startup fails
      context.setThrowUnavailableOnStartupException(true);
      // for JSP 2.2
      try {
        // Use the App Class loader to try to initialize the JSP machinery.
        // Not an issue if it fails: it means the app does not contain the JSP jars in WEB-INF/lib.
        Class<?> klass = classLoader.loadClass(TOMCAT_SIMPLE_INSTANCE_MANAGER);
        Object sim = klass.getConstructor().newInstance();
        context.getServletContext().setAttribute(TOMCAT_INSTANCE_MANAGER, sim);
        // Set JSP factory equivalent for:
        // JspFactory jspf = new JspFactoryImpl();
        klass = classLoader.loadClass(TOMCAT_JSP_FACTORY);
        JspFactory jspf = (JspFactory) klass.getConstructor().newInstance();
        JspFactory.setDefaultFactory(jspf);
        Class.forName(
            "org.apache.jasper.compiler.JspRuntimeContext", true, classLoader);
      } catch (Throwable t) {
        // No big deal, there are no JSPs in the App since the jsp libraries are not inside the
        // web app classloader.
      }
      SessionsConfig sessionsConfig = appVersion.getSessionsConfig();
      EE10SessionManagerHandler.Config.Builder builder = EE10SessionManagerHandler.Config.builder();
      if (sessionsConfig.getAsyncPersistenceQueueName() != null) {
        builder.setAsyncPersistenceQueueName(sessionsConfig.getAsyncPersistenceQueueName());
      }
      builder
          .setEnableSession(sessionsConfig.isEnabled())
          .setAsyncPersistence(sessionsConfig.isAsyncPersistence())
          .setServletContextHandler(context);
      EE10SessionManagerHandler.create(builder.build());
      // Pass the AppVersion on to any of our servlets (e.g. ResourceFileServlet).
      context.setAttribute(AppEngineConstants.APP_VERSION_CONTEXT_ATTR, appVersion);

      if (Boolean.getBoolean(HTTP_CONNECTOR_MODE)) {
        context.addEventListener(
          new ContextHandler.ContextScopeListener() {
            @Override
            public void enterScope(Context context, Request request) {
              if (request != null) {
                ApiProxy.Environment environment =
                        (ApiProxy.Environment)
                                request.getAttribute(AppEngineConstants.ENVIRONMENT_ATTR);
                if (environment != null) ApiProxy.setEnvironmentForCurrentThread(environment);
              }
            }

            @Override
            public void exitScope(Context context, Request request) {
              if (request != null) {
                ApiProxy.clearEnvironmentForCurrentThread();
              }
            }
          });
      }
      return context;
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
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      logger.atFine().log("Custom Jetty ErrorHandler received an error notification.");
      mayHandleByErrorPage(request, response, callback);
      // We don't want Jetty to do anything further.
      return true;
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
    private void mayHandleByErrorPage(Request request, Response response, Callback callback)
        throws IOException {
      ServletContextRequest contextRequest = Request.as(request, ServletContextRequest.class);
      HttpServletRequest httpServletRequest = contextRequest.getServletApiRequest();
      HttpServletResponse httpServletResponse = contextRequest.getHttpServletResponse();
      // Extract some error handling info from Jetty's proprietary attributes.
      Throwable error = (Throwable) contextRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
      Integer code = (Integer) contextRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
      String message = (String) contextRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE);
      // Now try to find an error handler...
      String errorPage = getErrorPage(httpServletRequest);
      // If we found an error handler, dispatch to it.
      if (errorPage != null) {
        // Check for reentry into the same error page.
        String oldErrorPage = (String) request.getAttribute(ErrorHandler.ERROR_PAGE);
        if (oldErrorPage == null || !oldErrorPage.equals(errorPage)) {
          request.setAttribute(ErrorHandler.ERROR_PAGE, errorPage);
          ServletContext servletContext = httpServletRequest.getServletContext();
          Dispatcher dispatcher = (Dispatcher) servletContext.getRequestDispatcher(errorPage);
          try {
            if (dispatcher != null) {
              dispatcher.error(httpServletRequest, httpServletResponse);
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
      // If we got an error code (e.g. this is a call to HttpServletResponse#sendError),
      // then render our own HTML.  XFE has logic to do this, but the PFE only invokes it
      // for error conditions that it or the AppServer detect.
      if (code != null && message != null) {
        // This template is based on the default XFE error response.
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=UTF-8");
        String messageEscaped = HtmlEscapers.htmlEscaper().escape(message);
        try (PrintWriter writer = new PrintWriter(Content.Sink.asOutputStream(response))) {
          writer.println("<html><head>");
          writer.println("<meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\">");
          writer.println("<title>" + code + " " + messageEscaped + "</title>");
          writer.println("</head>");
          writer.println("<body text=#000000 bgcolor=#ffffff>");
          writer.println("<h1>Error: " + messageEscaped + "</h1>");
          writer.println("</body></html>");
          writer.close();
          callback.succeeded();
        } catch (Throwable t) {
          callback.failed(t);
        }
        return;
      }
      // If we got this far and *did* have an exception, it will be
      // retrieved and thrown at the end of JettyServletEngineAdapter#serviceRequest.
      throw new IllegalStateException(error);
    }
  }
}
