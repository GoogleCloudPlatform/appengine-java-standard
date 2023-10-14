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

package com.google.appengine.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class AppengineOptionalProperties {
  private static final Logger logger = Logger.getLogger(AppengineOptionalProperties.class.getName());
  private static final String PROPERTIES_LOCATION = "WEB-INF/appengine_optional.properties";
  /**
   * This property will be used in ClassPathUtils processing to determine the correct classpath.
   * Property must now be true for the Java8 runtime, and is ignored for Java11/17/21 runtimes which
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

  private static final String USE_JETTY12 = "appengine.use.jetty12";
  private static final String USE_EE10 = "appengine.use.EE10";

  /**
   * Handles an undocumented property file that could be use by select customers to change flags.
     * @param applicationPath  Root directory of the Web Application (exploded war directory)
   */
  public void processOptionalProperties(String applicationPath) {
    File optionalPropFile = new File(applicationPath, PROPERTIES_LOCATION);
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
          USE_JETTY12,
          USE_EE10,
          DISABLE_API_CALL_LOGGING_IN_APIPROXY,
          ALLOW_NON_RESIDENT_SESSION_ACCESS,
          USE_ANNOTATION_SCANNING
        }) {
      if ("true".equalsIgnoreCase(optionalProperties.getProperty(flag))) {
        System.setProperty(flag, "true");
      }
      // Force Jetty12 for EE10
      if (Boolean.getBoolean(USE_EE10)) {
          System.setProperty(USE_JETTY12, "true");
      }
    }
  }   
}
