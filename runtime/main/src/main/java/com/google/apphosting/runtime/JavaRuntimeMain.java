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

package com.google.apphosting.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The primary entry point for starting up a {@link JavaRuntime}. This class creates a new {@link
 * RuntimeClassLoader} with an appropriate classpath and launches a new {@code JavaRuntime} in that
 * {@code ClassLoader} via {@link JavaRuntimeFactory}.
 *
 * <p>This class specifically minimizes dependencies on google3 such that they will be loaded within
 * the {@code RuntimeClassLoader} instead of the launching {@code SystemClassLoader}.
 *
 */
public class JavaRuntimeMain {

  private static final String FACTORY_CLASS = "com.google.apphosting.runtime.JavaRuntimeFactory";
  private static final Logger logger = Logger.getLogger(JavaRuntimeMain.class.getName());
  private static final String PROPERTIES_LOCATION = "WEB-INF/appengine_optional.properties";

  /**
   * This property will be used in ClassPathUtils processing to determine the correct classpath.
   * Property must now be true for the Java8 runtime, and is ignored for Java11/17 runtimes which
   * can only use maven jars.
   */
  private static final String USE_MAVEN_JARS = "use.mavenjars";

  /**
   * This property will be used to enable/disable Annotation Scanning when quickstart-web.xml is not
   * present.
   */
  private static final String USE_ANNOTATION_SCANNING = "use.annotationscanning";

  /** Disable logging in ApiProxy */
  private static final String DISABLE_API_CALL_LOGGING_IN_APIPROXY =
      "disable_api_call_logging_in_apiproxy";

  /** Allow non resident session access in AppEngineSession */
  private static final String ALLOW_NON_RESIDENT_SESSION_ACCESS =
      "gae.allow_non_resident_session_access";

  public static void main(String[] args) {
    new JavaRuntimeMain().load(args);
  }

  public JavaRuntimeMain() {}

  public void load(String[] args) {
    try {
      // Set this property as early as possible, to catch all possible uses of streamz.
      System.setProperty("com.google.appengine.runtime.environment", "Production");

      // Process user defined properties as soon as possible, in the simple main Classpath.
      processOptionalProperties(args);
      String appsRoot = getApplicationRoot(args);
      NullSandboxPlugin plugin = new NullSandboxPlugin();
      ClassPathUtils classPathUtils = new ClassPathUtils();
      ClassLoader runtimeLoader = plugin.createRuntimeClassLoader(classPathUtils, appsRoot);
      Class<?> runtimeFactory = runtimeLoader.loadClass(FACTORY_CLASS);
      Method mainMethod =
          runtimeFactory.getMethod("startRuntime", NullSandboxPlugin.class, String[].class);
      mainMethod.invoke(null, plugin, args);
    } catch (ReflectiveOperationException e) {
      String msg = "Unexpected failure creating RuntimeClassLoader";
      logger.log(Level.SEVERE, msg, e);
      throw new RuntimeException(msg, e);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Unexpected failure creating RuntimeClassLoader", t);
      throw t;
    }
  }

  /** Parse the value of the --application_root flag. */
  private String getApplicationRoot(String[] args) {
    return getFlag(args, "application_root", null);
  }

  /**
   * Parse the value of the given flag. Unfortunately we cannot rely on the usual flag parsing code
   * because it is loaded in the {@link RuntimeClassLoader} and we need the value of the flag before
   * the {@link RuntimeClassLoader} is created.
   *
   * @param args the command line to scan
   * @param flagName the name of the flag to look for (without "--" at the beginning)
   * @param warningMsgIfAbsent warning message to print if the flag is missing
   * @return the flag value, if found, otherwise null
   */
  /* @VisibleForTesting */
  @SuppressWarnings("ReturnMissingNullable")
  String getFlag(String[] args, String flagName, String warningMsgIfAbsent) {
    String target = "--" + flagName;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals(target)) {
        // TODO: This syntax ("-foo bar" instead of "-foo=bar") doesn't appear to be used
        // anywhere. We should probably stop supporting it.
        if (i + 1 < args.length) {
          return args[i + 1];
        }
      } else if (args[i].startsWith(target + "=")) {
        return args[i].substring((target + "=").length());
      }
    }
    if (warningMsgIfAbsent != null) {
      logger.warning(warningMsgIfAbsent);
    }
    return null;
  }

  /**
   * Handles an undocumented property file that could be use by select customers to change flags.
   */
  void processOptionalProperties(String[] args) {
    File optionalPropFile = new File(getApplicationPath(args), PROPERTIES_LOCATION);
    if (!optionalPropFile.exists()) {
      // nothing to process.
      return;
    }
    Properties optionalProperties = new Properties();
    try (InputStream in = new FileInputStream(optionalPropFile)) {
      optionalProperties.load(in);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Cannot read optional properties file.", e);
      return;
    }

    for (String flag :
        new String[] {
          USE_MAVEN_JARS,
          DISABLE_API_CALL_LOGGING_IN_APIPROXY,
          ALLOW_NON_RESIDENT_SESSION_ACCESS,
          USE_ANNOTATION_SCANNING
        }) {
      if ("true".equalsIgnoreCase(optionalProperties.getProperty(flag))) {
        System.setProperty(flag, "true");
      }
    }
  }

  // The app should be either under under fixedapplicationpath (new way) or
  // /base/data/home/apps/APPID/VERSION.DEPLPOYEMENTID/
  // We cannot pass it as a parameter from the launcher as (legacy) appId and appVersion
  // are passed via a RPC in Java8 runtime, so we calculate the value here.
  String getApplicationPath(String[] args) {
    String fixedPath = getFlag(args, "fixed_application_path", null);
    if (fixedPath != null) {
      return fixedPath;
    }
    return getApplicationRoot(args)
        + "/"
        + System.getenv("GAE_APPLICATION")
        + "/"
        + System.getenv("GAE_VERSION")
        + "."
        + System.getenv("GAE_DEPLOYMENT_ID");
  }
}
