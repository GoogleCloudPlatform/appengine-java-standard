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

package com.google.appengine.tools.util;

import com.google.appengine.tools.info.AppengineSdk;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.util.logging.LogManager;

/**
 * A utility class for working with Logging.
 *
 */
public class Logging {

  public static final String LOGGING_CONFIG_FILE = "java.util.logging.config.file";

  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();

  /**
   * Initializes logging if the system property, {@link #LOGGING_CONFIG_FILE},
   * has not been set. Also sets the system property to the default SDK
   * logging config file. 
   * <p>
   * Since this property re-initializes the entire logging system, 
   * it should generally only be used from the entry point of an application
   * rather than from arbitrary code.
   */
  public static void initializeLogging() {
    String logConfig = System.getProperty(LOGGING_CONFIG_FILE);
    if (logConfig == null) {
      File config = AppengineSdk.getSdk().getLoggingProperties();
      System.setProperty(LOGGING_CONFIG_FILE, config.getAbsolutePath());
      try {
        LogManager.getLogManager().readConfiguration();
      } catch (Exception e) {
        // Might be an IOException, might be a SecurityException
        // Regardless of what it is, we're always going to do the same thing.
        log.atInfo().withCause(e).log("Failed to read the default logging configuration");
      }
    }
  }
  
  private Logging() {    
  }
}
