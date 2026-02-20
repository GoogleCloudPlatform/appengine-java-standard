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

package com.google.appengine.tools.development;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.GoogleLogger;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Manager for a web application's logging configurations.
 * <p>
 * When the web application is read from an EAR directory this supports
 * combining the logging configurations from each contained WAR directory. In
 * the case two configurations define the logging level for the same logger the
 * more verbose level applies.
 */
class LoggingConfigurationManager {
  private static final GoogleLogger LOGGER = GoogleLogger.forEnclosingClass();
  public static final String LOGGING_CONFIG_FILE = "java.util.logging.config.file";
  private final Properties readProperties = new Properties();

  /**
   * Reads and remember the logging configuration for a single server. This
   * reads up to two files.
   * <ul>
   *
   * <li>
   * The file specified by the {@link #LOGGING_CONFIG_FILE}
   * property value from {@link #userSystemProperties} (from appengine-web.xml).
   * This interprets the file name in up to 3 ways looking for a valid
   * logging.properties file.
   * <ol>
   * <li> As an absolute file name.
   * <li> As a relative file name qualified by {@link #warDir}.
   * <li> As a relative file name qualified by {@link #externalResourceDir}.
   * </ol>
   * </li>
   *
   * <li>
   * The file specified by the {@link #LOGGING_CONFIG_FILE} property value
   * from  {@link SystemProperties} (from {@link System#getProperties}.
   * <li>The file specified by the {@link #LOGGING_CONFIG_FILE}
   * property value from {@link #userSystemProperties} (from appengine-web.xml).
   * This interprets the file name in up to 2 ways looking for a valid
   * logging.properties file.
   * <ol>
   * <li> As an absolute file name.
   * <li> As a relative file name qualified by {@link #warDir}.
   * </ol>
   * </li>
   *
   * </ul>
   * When the same multiple configurations specify the level for the same
   * logger, the more verbose level wins.
   * @param systemProperties
   * @param userSystemProperties
   */
  void read(Properties systemProperties,  Map<String, String> userSystemProperties,
      File warDir, File externalResourceDir) {
    String userConfigFile = userSystemProperties.get(LOGGING_CONFIG_FILE);

    // We merge both the user and SDK logging properties files,
    // because LogManager.readConfiguration resets the JVM-wide configuration.

    // Don't log a warning message if the file is not found but
    // we are going to try again with the external resource dir
    boolean shouldLogWarning = (externalResourceDir == null);
    Properties userProperties = loadPropertiesFile(userConfigFile, warDir, shouldLogWarning);
    if (userProperties == null && externalResourceDir != null) {
      // This time do log a warning if the file is not found
      userProperties = loadPropertiesFile(userConfigFile, externalResourceDir, true);
    }
    String sdkConfigFile = systemProperties.getProperty(LOGGING_CONFIG_FILE);
    Properties sdkProperties = loadPropertiesFile(sdkConfigFile, warDir, true);
    if (sdkProperties != null) {
      // Could happen in odd cases. We'll be fail-safe about it.
      mergeProperties(sdkProperties);
    }
    if (userProperties != null) {
      mergeProperties(userProperties);
    }
  }

  @VisibleForTesting
  Properties getReadProperties() {
    Properties result = new Properties();
    result.putAll(readProperties);
    return result;
  }

  /**
   * Updates the JVM's logging configuration based on the combined already read
   * logging configurations.
   */
  void updateLoggingConfiguration() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      readProperties.store(out, null);
      LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
    } catch (IOException e) {
      LOGGER.atWarning().withCause(e).log("Unable to configure logging properties.");
    }
  }

  private void mergeProperties(Properties additional) {
    for (Map.Entry<Object, Object> entry : additional.entrySet()){
      if (!(entry.getKey() instanceof String)) {
        continue;
      }
      String key = (String) entry.getKey();
      if (!(entry.getValue() instanceof String)) {
        continue;
      }
      String newValue = (String) entry.getValue();
      String oldValue = readProperties.getProperty(key);
      if (oldValue == null
          || !key.endsWith(".level")
          || compareLevels(newValue, oldValue) > 0) {
        readProperties.setProperty(key, newValue);
      }
    }
  }

  @VisibleForTesting
  int compareLevels(String levelName1, String levelName2) {
    if (levelName1.equals(levelName2)) {
      return 0;
    }
    Integer level1;
    try {
      level1 = Integer.valueOf(Level.parse(levelName1).intValue());
    } catch (IllegalArgumentException iae) {
      return -1;
    }
    Integer level2;
    try {
      level2 = Integer.valueOf(Level.parse(levelName2).intValue());
    } catch (IllegalArgumentException iae) {
      return 1;
    }
    //Reverse order so lower (more verbose) levels are higher.
    return level2.compareTo(level1);

  }

  private static Properties loadPropertiesFile(String file, File appDir, boolean logWarning) {
    if (file == null) {
      return null;
    }
    file = file.replace('/', File.separatorChar);
    File f = new File(file);
    if (!f.isAbsolute()) {
      // It's possible that our working directory is not equal to our app root.
      // If we always use an absolute reference to the app root, we'll be ok.
      f = new File(appDir + File.separator + f.getPath());
    }
    InputStream inputStream = null;
    try {
      inputStream = new BufferedInputStream(new FileInputStream(f));
      Properties props = new Properties();
      props.load(inputStream);
      return props;
    } catch (IOException e) {
      if (logWarning) {
        LOGGER.atWarning().withCause(e).log(
            "Unable to load properties file, %s", f.getAbsolutePath());
      }
      return null;
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          LOGGER.atWarning().withCause(e).log("Stream close failure");
        }
      }
    }
  }
}
