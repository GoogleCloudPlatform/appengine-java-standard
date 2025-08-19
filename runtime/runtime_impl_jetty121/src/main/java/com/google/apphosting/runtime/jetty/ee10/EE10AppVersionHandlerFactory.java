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
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.PrintWriter;
import javax.servlet.jsp.JspFactory;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.quickstart.QuickStartConfiguration;
import org.eclipse.jetty.ee10.servlet.ErrorHandler;
import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;
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
  public org.eclipse.jetty.server.Handler createHandler(AppVersion appVersion)
      throws ServletException {
    // Need to set thread context classloader for the duration of the scope.
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    try {
      return doCreateHandler(appVersion);
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
    }
  }

  private org.eclipse.jetty.server.Handler doCreateHandler(AppVersion appVersion)
      throws ServletException {
    try {
      File contextRoot = appVersion.getRootDirectory();
      final AppEngineWebAppContext context =
          new AppEngineWebAppContext(
              appVersion.getRootDirectory(), serverInfo, /* extractWar= */ false);
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
      } else {
        context.removeConfiguration(new AnnotationConfiguration());
      }
      File quickstartXml = new File(contextRoot, "WEB-INF/quickstart-web.xml");
      if (quickstartXml.exists()) {
        context.addConfiguration(new QuickStartConfiguration());
      } else {
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
        Class.forName("org.apache.jasper.compiler.JspRuntimeContext", true, classLoader);
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
                ApiProxy.clearEnvironmentForCurrentThread();
              }
            });
      }
      return context;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  private static class NullErrorHandler extends ErrorPageErrorHandler {

    /**
     * Override the response generation when not mapped to a servlet error page.
     */
    @Override
    protected void generateResponse(
        Request request,
        Response response,
        int code,
        String message,
        Throwable cause,
        Callback callback) {
      // If we got an error code (e.g. this is a call to HttpServletResponse#sendError),
      // then render our own HTML.  XFE has logic to do this, but the PFE only invokes it
      // for error conditions that it or the AppServer detect.
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
    }
  }
}
