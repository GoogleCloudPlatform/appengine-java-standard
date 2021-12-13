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

package com.google.appengine.tools.development.testing;

import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerFactory;
import com.google.appengine.tools.info.AppengineSdk;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper that manages a dev appserver.
 * The dev appserver can be configured to run against an exploded war or
 * an explicitly provided classpath, web.xml, and appengine-web.xml.
 *
 * You can only start one dev appserver per classloader.
 *
 */
class DevAppServerTestHelper {

  private static final String SDK_ROOT_PROP = "appengine.sdk.root";
  private static final String USER_CODE_CLASSPATH_MANAGER_PROP =
      "devappserver.userCodeClasspathManager";
  private static final String USER_CODE_CLASSPATH = USER_CODE_CLASSPATH_MANAGER_PROP + ".classpath";
  private static final String USER_CODE_REQUIRES_WEB_INF =
      USER_CODE_CLASSPATH_MANAGER_PROP + ".requiresWebInf";

  static DevAppServer server;
  static String originalSdkRoot;
  static boolean running = false;

  /**
   * Run the app in the dev appserver with the provided configuration.  All
   * classes required by the application and the test must be available on the
   * provided classpath.  This method ignores wars.
   *
   * @param testConfig the config
   * @return the dev appserver we started
   *
   * @throws IllegalStateException If a dev appserver started by this class is
   * already running.
   */
  public static DevAppServer startServer(DevAppServerTestConfig testConfig) {
    if (running) {
      throw new IllegalStateException("Dev Appserver is already running.");
    }
    originalSdkRoot = System.getProperty(SDK_ROOT_PROP);
    System.setProperty(SDK_ROOT_PROP, testConfig.getSdkRoot().getAbsolutePath());

    String address = "localhost";
    // Tells SdkInfo to treat the testing jar as shared.  See SdkInfo for an
    // explanation of why this is necessary.
    AppengineSdk sdk = AppengineSdk.getSdk();
    sdk.includeTestingJarOnSharedPath(true);

    Map<String, Object> containerConfigProps =
        newContainerConfigPropertiesForTest(testConfig.getClasspath());
    server =
        new DevAppServerFactory()
            .createDevAppServer(
                testConfig.getAppDir(),
                testConfig.getWebXml(),
                testConfig.getAppEngineWebXml(),
                address,
                0,
                true,
                testConfig.installSecurityManager(),
                containerConfigProps,
                false /*noagent*/);
    try {
      server.start();
      System.setProperty(testConfig.getPortSystemProperty(), Integer.toString(server.getPort()));
      running = true;
      return server;
    } catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    } finally {
      if (!running) {
        // nothing to clean up
        server = null;
        sdk.includeTestingJarOnSharedPath(false);
      }
    }
  }

  /**
   * Shut down the dev appserver.
   */
  public static void stopServer() {
    AppengineSdk.getSdk().includeTestingJarOnSharedPath(false);
    running = false;
    if (server != null) {
      try {
        server.shutdown();
        server = null;
      } catch (Exception e) {
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
      }
    }
    if (originalSdkRoot == null) {
      System.clearProperty(SDK_ROOT_PROP);
    } else {
      System.setProperty(SDK_ROOT_PROP, originalSdkRoot);
    }
  }

  /**
   * Build a {@link Map} that contains settings that will allow us to inject our own classpath and
   * to not require a WEB-INF directory.
   */
  private static Map<String, Object> newContainerConfigPropertiesForTest(
      Collection<URL> classpath) {
    // Sorry, do not want to depend on Guava for this appengine-testing public library.
    Map<String, Object> props = new HashMap<>();
    props.put(USER_CODE_CLASSPATH, classpath);
    props.put(USER_CODE_REQUIRES_WEB_INF, false);
    Map<String, Object> userProps = new HashMap<>();
    userProps.put(USER_CODE_CLASSPATH_MANAGER_PROP, props);

    return Collections.unmodifiableMap(userProps);
  }
}
