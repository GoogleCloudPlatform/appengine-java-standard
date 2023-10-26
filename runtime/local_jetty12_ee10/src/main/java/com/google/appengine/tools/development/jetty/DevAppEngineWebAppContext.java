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

package com.google.appengine.tools.development.jetty.ee10;

import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.io.IoUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.resource.Resource;

/**
 * An AppEngineWebAppContext for the DevAppServer.
 *
 */
public class DevAppEngineWebAppContext extends AppEngineWebAppContext {

  private static final Logger logger =
      Logger.getLogger(DevAppEngineWebAppContext.class.getName());

  // Copied from org.apache.jasper.Constants.SERVLET_CLASSPATH
  // to remove compile-time dependency on Jasper
  private static final String JASPER_SERVLET_CLASSPATH = "org.apache.catalina.jsp_classpath";

  // Header that allows arbitrary requests to bypass jetty's security
  // mechanisms.  Useful for things like the dev task queue, which needs
  // to hit secure urls without an authenticated user.
  private static final String X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK =
      "X-Google-DevAppserver-SkipAdminCheck";

  // Keep in sync with com.google.apphosting.utils.jetty.AppEngineAuthentication.
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  private final Object transportGuaranteeLock = new Object();
  private boolean transportGuaranteesDisabled = false;

  public DevAppEngineWebAppContext(File appDir, File externalResourceDir, String serverInfo,
      ApiProxy.Delegate<?> apiProxyDelegate, DevAppServer devAppServer) {
    super(appDir, serverInfo);

    // Set up the classpath required to compile JSPs. This is specific to Jasper.
    setAttribute(JASPER_SERVLET_CLASSPATH, buildClasspath());

    // Make ApiProxyLocal available via the servlet context.  This allows
    // servlets that are part of the dev appserver (like those that render the
    // dev console for example) to get access to this resource even in the
    // presence of libraries that install their own custom Delegates (like
    // Remote api and Appstats for example).
    getServletContext()
        .setAttribute("com.google.appengine.devappserver.ApiProxyLocal", apiProxyDelegate);

    // Make the dev appserver available via the servlet context as well.
    getServletContext().setAttribute("com.google.appengine.devappserver.Server", devAppServer);
  }

  /**
   * <p>By default, the context is created with alias checkers for symlinks:
   * {@link org.eclipse.jetty.server.SymlinkAllowedResourceAliasChecker}.</p>
   *
   * <p>Note: this is a dangerous configuration and should not be used in production.</p>
   */
  @Override
  public boolean checkAlias(String path, Resource resource) {
    return true;
  }

  @Override
  protected ClassLoader configureClassLoader(ClassLoader loader) {
    // Avoid wrapping the provided classloader with WebAppClassLoader.
    return loader;
  }

  @Override
  protected ClassLoader enterScope(Request contextRequest) {
    if ((contextRequest != null) && (hasSkipAdminCheck(contextRequest))) {
      contextRequest.setAttribute(SKIP_ADMIN_CHECK_ATTR, Boolean.TRUE);
    }

    disableTransportGuarantee();

    // TODO An extremely heinous way of helping the DevAppServer's
    // SecurityManager determine if a DevAppServer request thread is executing.
    // Find something better.
    // See DevAppServerFactory.CustomSecurityManager.

    // ludo remove entirely
    System.setProperty("devappserver-thread-" + Thread.currentThread().getName(), "true");
    return super.enterScope(contextRequest);
  }

  @Override
  protected void exitScope(Request request, Context lastContext, ClassLoader lastLoader) {
    super.exitScope(request, lastContext, lastLoader);
    System.clearProperty("devappserver-thread-" + Thread.currentThread().getName());
  }

  /**
   * Returns true if the X-Google-Internal-SkipAdminCheck header is present. There is nothing
   * preventing usercode from setting this header and circumventing dev appserver security, but the
   * dev appserver was not designed to be secure.
   */
  private boolean hasSkipAdminCheck(Request request) {
    for (HttpField field : request.getHeaders()) {
      // We don't care about the header value, its presence is sufficient.
      if (field.getName().equalsIgnoreCase(X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds a classpath up for the webapp for JSP compilation.
   */
  private String buildClasspath() {
    StringBuilder classpath = new StringBuilder();

    // Shared servlet container classes
    for (File f : AppengineSdk.getSdk().getSharedLibFiles()) {
      classpath.append(f.getAbsolutePath());
      classpath.append(File.pathSeparatorChar);
    }

    String webAppPath = getWar();

    // webapp classes
    classpath.append(webAppPath + File.separator + "classes" + File.pathSeparatorChar);

    List<File> files = IoUtil.getFilesAndDirectories(new File(webAppPath, "lib"));
    for (File f : files) {
      if (f.isFile() && f.getName().endsWith(".jar")) {
        classpath.append(f.getAbsolutePath());
        classpath.append(File.pathSeparatorChar);
      }
    }

    return classpath.toString();
  }

  /**
   * The first time this method is called it will walk through the
   * constraint mappings on the current SecurityHandler and disable
   * any transport guarantees that have been set.  This is required to
   * disable SSL requirements in the DevAppServer because it does not
   * support SSL.
   */
  private void disableTransportGuarantee() {
    synchronized (transportGuaranteeLock) {
      ConstraintSecurityHandler securityHandler = (ConstraintSecurityHandler) getSecurityHandler();
      if (!transportGuaranteesDisabled && securityHandler != null) {
        List<ConstraintMapping> mappings = new ArrayList<>();
        for (ConstraintMapping mapping : securityHandler.getConstraintMappings()) {
          Constraint constraint = mapping.getConstraint();
          if (constraint.getTransport() == Constraint.Transport.SECURE) {
            logger.info(
                "Ignoring <transport-guarantee> for "
                    + mapping.getPathSpec()
                    + " as the SDK does not support HTTPS.  It will still be used"
                    + " when you upload your application.");
          }

          mapping.setConstraint(
              Constraint.from(
                  constraint.getName(),
                  Constraint.Transport.ANY,
                  constraint.getAuthorization(),
                  constraint.getRoles()));
          mappings.add(mapping);
        }

        // TODO: do we need to call this with a new list or is modifying the ConstraintMapping
        // enough?
        securityHandler.setConstraintMappings(mappings);
      }
      transportGuaranteesDisabled = true;
    }
  }
}
