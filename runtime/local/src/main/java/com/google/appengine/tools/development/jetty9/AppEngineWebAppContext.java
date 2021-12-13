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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.runtime.jetty9.AppEngineAuthentication;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.RoleInfo;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserDataConstraint;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * {@code AppEngineWebAppContext} is a customization of Jetty's {@link WebAppContext} that is aware
 * of the {@link ApiProxy} and can provide custom logging and authentication.
 *
 */
public class AppEngineWebAppContext extends WebAppContext {

  // TODO: This should be some sort of Prometheus-wide
  // constant.  If it's much larger than this we may need to
  // restructure the code a bit.
  private static final int MAX_RESPONSE_SIZE = 32 * 1024 * 1024;

  private final String serverInfo;

  public AppEngineWebAppContext(File appDir, String serverInfo) {
    // We set the contextPath to / for all applications.
    super(appDir.getPath(), URIUtil.SLASH);
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

    this.serverInfo = serverInfo;

    // Override the default HttpServletContext implementation.
    _scontext = new AppEngineServletContext();

    // Configure the Jetty SecurityHandler to understand our method of
    // authentication (via the UserService).
    AppEngineAuthentication.configureSecurityHandler(
        (ConstraintSecurityHandler) getSecurityHandler());

    setMaxFormContentSize(MAX_RESPONSE_SIZE);
  }

  private static File createTempDir() {
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    String baseName = System.currentTimeMillis() + "-";

    for (int counter = 0; counter < 10; counter++) {
      File tempDir = new File(baseDir, baseName + counter);
      if (tempDir.mkdir()) {
        return tempDir;
      }
    }
    throw new IllegalStateException("Failed to create directory ");
  }

  @Override
  protected SecurityHandler newSecurityHandler() {
    return new AppEngineContraintSecurityHandler();
  }

  /**
   * Override to make sure all RoleInfos do not have security constraints to avoid a Jetty failure
   * when not running with https.
   */
  private static class AppEngineContraintSecurityHandler extends ConstraintSecurityHandler {
    @Override
    protected RoleInfo prepareConstraintInfo(String pathInContext, Request request) {
      RoleInfo ri = super.prepareConstraintInfo(pathInContext, request);
      // Remove constraints so that we can emulate HTTPS locally.
      ri.setUserDataConstraint(UserDataConstraint.None);
      return ri;
    }
  }

  // N.B.(schwardo): Yuck.  Jetty hardcodes all of this logic into an
  // inner class of ContextHandler.  We need to subclass WebAppContext
  // (which extends ContextHandler) and then subclass the SContext
  // inner class to modify its behavior.

  /**
   * Context extension that allows logs to be written to the App Engine log APIs.
   */
  public class AppEngineServletContext extends Context {

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
     * @param throwable an exception associated with this log message,
     * or {@code null}.
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
}
