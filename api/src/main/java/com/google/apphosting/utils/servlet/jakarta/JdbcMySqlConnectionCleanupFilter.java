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

package com.google.apphosting.utils.servlet.jakarta;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Filter to cleanup any SQL connections that were opened but not closed during the
 * HTTP-request processing.
 */
public class JdbcMySqlConnectionCleanupFilter implements Filter {

  private static final Logger logger = Logger.getLogger(
      JdbcMySqlConnectionCleanupFilter.class.getCanonicalName());

  /**
   * The key for looking up the feature on/off flag.
   */
  static final String CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY =
      "com.google.appengine.runtime.new_database_connectivity";

  private final AppEngineApiWrapper appEngineApiWrapper;

  private final ConnectionsCleanupWrapper connectionsCleanupWrapper;

  private static final String THROW_ERROR_VARIABLE_NAME = "THROW_ERROR_ON_SQL_CLOSE_ERROR";
  private static final String ABANDONED_CONNECTIONS_CLASSNAME =
      "com.mysql.jdbc.AbandonedConnections";

  public JdbcMySqlConnectionCleanupFilter() {
    appEngineApiWrapper = new AppEngineApiWrapper();
    connectionsCleanupWrapper = new ConnectionsCleanupWrapper();
  }

  // Visible for testing.
  JdbcMySqlConnectionCleanupFilter(
      AppEngineApiWrapper appEngineApiWrapper,
      ConnectionsCleanupWrapper connectionsCleanupWrapper) {
    this.appEngineApiWrapper = appEngineApiWrapper;
    this.connectionsCleanupWrapper = connectionsCleanupWrapper;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // Do Nothing.
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } finally {
      cleanupConnections();
    }
  }

  /**
   * Cleanup any SQL connection that was opened but not closed during the HTTP-request processing.
   */
  void cleanupConnections() {
    Map<String, Object> attributes = appEngineApiWrapper.getRequestEnvironmentAttributes();
    if (attributes == null) {
      return;
    }

    Object cloudSqlJdbcConnectivityEnabledValue =
        attributes.get(CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY);
    if (!(cloudSqlJdbcConnectivityEnabledValue instanceof Boolean)) {
      return;
    }

    if (!((Boolean) cloudSqlJdbcConnectivityEnabledValue)) {
      // Act as no-op if the flag indicated by CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY is false.
      return;
    }

    try {
      connectionsCleanupWrapper.cleanup();
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to cleanup connections", e);
      if (Boolean.getBoolean(THROW_ERROR_VARIABLE_NAME)) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public void destroy() {
    // Do Nothing.
  }

  /**
   * Wrapper for ApiProxy static methods.
   * Refactored for testability.
   */
  static class AppEngineApiWrapper {
    /**
     * Utility method that fetches back the attributes map for the HTTP-request being processed.
     *
     * @return The environment attribute map for the current HTTP request, or null if unable to
     *     fetch the map
     */
    Map<String, Object> getRequestEnvironmentAttributes() {
      // Check for the current request environment.
      Environment environment = ApiProxy.getCurrentEnvironment();
      if (environment == null) {
        logger.warning("Unable to fetch the request environment.");
        return null;
      }

      // Get the environment attributes.
      Map<String, Object> attributes = environment.getAttributes();
      if (attributes == null) {
        logger.warning("Unable to fetch the request environment attributes.");
        return null;
      }

      return attributes;
    }
  }

  /**
   * Wrapper for the connections cleanup method.
   * Refactored for testability.
   */
  static class ConnectionsCleanupWrapper {
    /**
     * Abandoned connections cleanup method cache.
     */
    private static Method cleanupMethod;
    private static boolean cleanupMethodInitializationAttempted;

    void cleanup() throws Exception {
      synchronized (ConnectionsCleanupWrapper.class) {
        // Due to cr/50477083 the cleanup method was invoked by the applications that do
        // not have the native connectivity enabled. For such applications the filter raised
        // ClassNotFound exception when returning a class object associated with the
        // "com.mysql.jdbc.AbandonedConnections" class. By design this class is not loaded for
        // such applications. The exception was logged as warning and polluted the logs.
        //
        // As a quick fix; we ensure that the initialization for cleanupMethod is attempted
        // only once, avoiding exceptions being raised for every request in case of
        // applications mentioned above. We also suppress the ClassNotFound exception that
        // would be raised for such applications thereby not polluting the logs.
        // For the applications having native connectivity enabled the servlet filter would
        // work as expected.
        //
        // As a long term fix we need to use the "use-google-connector-j" flag that user sets
        // in the appengine-web.xml to decide if we should make an early return from the filter.
        if (!cleanupMethodInitializationAttempted) {
          try {
            if (cleanupMethod == null) {
              ClassLoader loader = Thread.currentThread().getContextClassLoader();
              cleanupMethod =
                  (loader == null
                          ? Class.forName(ABANDONED_CONNECTIONS_CLASSNAME)
                          : loader.loadClass(ABANDONED_CONNECTIONS_CLASSNAME))
                      .getDeclaredMethod("cleanup");
            }
          } catch (ClassNotFoundException e) {
            // Do nothing.
          } finally {
            cleanupMethodInitializationAttempted = true;
          }
        }
      }
      if (cleanupMethod != null) {
        cleanupMethod.invoke(null);
      }
    }
  }
}
