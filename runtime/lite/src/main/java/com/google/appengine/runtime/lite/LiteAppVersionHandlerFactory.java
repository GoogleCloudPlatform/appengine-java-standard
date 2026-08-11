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

package com.google.appengine.runtime.lite;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.runtime.AppEngineConstants;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.SessionsConfig;
import com.google.apphosting.runtime.jetty.AppVersionHandlerFactory;
import com.google.apphosting.runtime.jetty.SessionManagerHandler;
import com.google.apphosting.runtime.jetty.ee8.AppEngineWebAppContext;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.EventListener;
import javax.servlet.jsp.JspFactory;
import org.eclipse.jetty.ee8.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee8.nested.ContextHandler;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.quickstart.QuickStartConfiguration;
import org.eclipse.jetty.ee8.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee8.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee8.webapp.WebInfConfiguration;
import org.eclipse.jetty.ee8.webapp.WebXmlConfiguration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

/**
 * A specialized implementation of {@link AppVersionHandlerFactory} for the Lite runtime.
 */
class LiteAppVersionHandlerFactory implements AppVersionHandlerFactory {
  private final Server server;
  private final String serverInfo;
  private final ImmutableList<EventListener> listeners;

  static final String WEB_DEFAULTS_XML =
      "com/google/apphosting/runtime/jetty/ee8/webdefault.xml";
  private static final String TOMCAT_SIMPLE_INSTANCE_MANAGER =
      "org.apache.tomcat.SimpleInstanceManager";
  private static final String TOMCAT_INSTANCE_MANAGER = "org.apache.tomcat.InstanceManager";
  private static final String TOMCAT_JSP_FACTORY = "org.apache.jasper.runtime.JspFactoryImpl";

  LiteAppVersionHandlerFactory(
      Server server, String serverInfo, ImmutableList<EventListener> listeners) {
    this.server = server;
    this.serverInfo = serverInfo;
    this.listeners = listeners;
  }

  @Override
  public Handler createHandler(AppVersion appVersion) throws Exception {
    ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    try {
      File contextRoot = appVersion.getRootDirectory();

      final AppEngineWebAppContext context =
          new AppEngineWebAppContext(
              appVersion.getRootDirectory(), serverInfo, /* extractWar= */ false);
      listeners.forEach(
          listener -> {
            var _ = context.addEventListener(listener);
          });

      context.getCoreContextHandler().setServer(server);
      context.setServer(server);
      context.setDefaultsDescriptor(WEB_DEFAULTS_XML);
      ClassLoader classLoader = appVersion.getClassLoader();
      context.setClassLoader(classLoader);

      context.getErrorHandler().setShowStacks(false);

      context.setConfigurationClasses(
          new String[] {
            WebInfConfiguration.class.getCanonicalName(),
            WebXmlConfiguration.class.getCanonicalName(),
            MetaInfConfiguration.class.getCanonicalName(),
            FragmentConfiguration.class.getCanonicalName()
          });

      if (Boolean.getBoolean("use.annotationscanning")) {
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

      context.setPersistTempDirectory(true);
      context.setExtractWAR(false);
      context.setThrowUnavailableOnStartupException(true);

      try {
        Class<?> klass = classLoader.loadClass(TOMCAT_SIMPLE_INSTANCE_MANAGER);
        Object sim = klass.getConstructor().newInstance();
        context.getServletContext().setAttribute(TOMCAT_INSTANCE_MANAGER, sim);
      } catch (ReflectiveOperationException | LinkageError e) {
        // SimpleInstanceManager not available or failed to load.
      }

      try {
        Class<?> klass = classLoader.loadClass(TOMCAT_JSP_FACTORY);
        JspFactory jspf = (JspFactory) klass.getConstructor().newInstance();
        JspFactory.setDefaultFactory(jspf);
      } catch (ReflectiveOperationException | LinkageError e) {
        // tomcat JspFactory not available or failed to load.
      }

      try {
        Class.forName(
            "org.apache.jasper.compiler.JspRuntimeContext",
            /* initialize= */ true,
            classLoader);
      } catch (ClassNotFoundException | LinkageError e) {
        // JspRuntimeContext not available.
      }

      SessionsConfig sessionsConfig = appVersion.getSessionsConfig();
      SessionManagerHandler.Config.Builder builder = SessionManagerHandler.Config.builder();
      if (sessionsConfig.asyncPersistenceQueueName() != null) {
        builder.setAsyncPersistenceQueueName(sessionsConfig.asyncPersistenceQueueName());
      }
      builder
          .setEnableSession(sessionsConfig.enabled())
          .setAsyncPersistence(sessionsConfig.asyncPersistence())
          .setServletContextHandler(context);

      var _ = SessionManagerHandler.create(builder.build());
      context.setAttribute(AppEngineConstants.APP_VERSION_CONTEXT_ATTR, appVersion);

      if (Boolean.getBoolean(AppEngineConstants.HTTP_CONNECTOR_MODE)) {
        var unusedListener =
            context.addEventListener(
                new ContextHandler.ContextScopeListener() {
                  @Override
                  public void enterScope(
                      ContextHandler.APIContext context, Request request, Object reason) {
                    if (request == null) {
                      return;
                    }
                    Environment environment =
                        (Environment) request.getAttribute(AppEngineConstants.ENVIRONMENT_ATTR);
                    if (environment == null) {
                      return;
                    }
                    ApiProxy.setEnvironmentForCurrentThread(environment);
                  }

                  @Override
                  public void exitScope(ContextHandler.APIContext context, Request request) {
                    ApiProxy.clearEnvironmentForCurrentThread();
                  }
                });
      }

      context.start();
      return context.get();
    } finally {
      Thread.currentThread().setContextClassLoader(oldContextClassLoader);
    }
  }
}
