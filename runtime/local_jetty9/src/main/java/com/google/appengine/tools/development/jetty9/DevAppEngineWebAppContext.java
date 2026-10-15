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

package com.google.appengine.tools.development.jetty9;

import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.io.IoUtil;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.resource.Resource;

/**
 * An AppEngineWebAppContext for the DevAppServer.
 *
 */
public class DevAppEngineWebAppContext extends AppEngineWebAppContext {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

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
    _scontext.setAttribute("com.google.appengine.devappserver.ApiProxyLocal", apiProxyDelegate);

    // Make the dev appserver available via the servlet context as well.
    _scontext.setAttribute("com.google.appengine.devappserver.Server", devAppServer);
  }

  /**
   * By default, the context is created with alias checkers for symlinks:
   * {@link org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker}.
   */
  @Override
  public boolean checkAlias(String path, Resource resource) {
    return true;
  }

  @Override
  public void doScope(
      String target,
      Request baseRequest,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse)
      throws IOException, ServletException {

    if (hasSkipAdminCheck(baseRequest)) {
      baseRequest.setAttribute(SKIP_ADMIN_CHECK_ATTR, Boolean.TRUE);
    }

    disableTransportGuarantee();

    // TODO An extremely heinous way of helping the DevAppServer's
    // SecurityManager determine if a DevAppServer request thread is executing.
    // Find something better.
    // See DevAppServerFactory.CustomSecurityManager.
    System.setProperty("devappserver-thread-" + Thread.currentThread().getName(), "true");
    try {
      super.doScope(target, baseRequest, httpServletRequest, httpServletResponse);
    } finally {
      System.clearProperty("devappserver-thread-" + Thread.currentThread().getName());
    }
  }

  /**
   * Returns true if the X-Google-Internal-SkipAdminCheck header is
   * present.  There is nothing preventing usercode from setting this header
   * and circumventing dev appserver security, but the dev appserver was not
   * designed to be secure.
   */
  private boolean hasSkipAdminCheck(HttpServletRequest request) {
    // wow, old school java
    for (Enumeration<?> headerNames = request.getHeaderNames(); headerNames.hasMoreElements(); ) {
      String name = (String) headerNames.nextElement();
      // We don't care about the header value, its presence is sufficient.
      if (name.equalsIgnoreCase(X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK)) {
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
      if (!transportGuaranteesDisabled && getSecurityHandler() != null) {
        List<ConstraintMapping> mappings =
            ((ConstraintSecurityHandler) getSecurityHandler()).getConstraintMappings();
        if (mappings != null) {
          for (ConstraintMapping mapping : mappings) {
            if (mapping.getConstraint().getDataConstraint() > 0) {
              logger.atInfo().log(
                  "Ignoring <transport-guarantee> for %s as the SDK does not support HTTPS.  It"
                      + " will still be used when you upload your application.",
                  mapping.getPathSpec());
              mapping.getConstraint().setDataConstraint(0);
            }
          }
        }
      }
      transportGuaranteesDisabled = true;
    }
  }
}
