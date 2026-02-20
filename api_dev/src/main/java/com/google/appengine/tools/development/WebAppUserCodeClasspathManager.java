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

import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link UserCodeClasspathManager} that derives the classpath from a WEB-INF
 * directory relative to the application's root.
 *
 */
class WebAppUserCodeClasspathManager implements UserCodeClasspathManager {

  private static final GoogleLogger log = GoogleLogger.forEnclosingClass();

  @Override
  public Collection<URL> getUserCodeClasspath(File root) {
    List<URL> appUrls = new ArrayList<URL>();
    // From the servlet spec, SRV.9.5 "The Web application class
    // loader must load classes from the WEB-INF/ classes directory
    // first, and then from library JARs in the WEB-INF/lib
    // directory."
    try {
      File classes = new File(new File(root, "WEB-INF"), "classes");
      if (classes.exists()) {
        appUrls.add(classes.toURI().toURL());
      }
    } catch (MalformedURLException ex) {
      log.atWarning().withCause(ex).log("Could not add WEB-INF/classes");
    }

    File libDir = new File(new File(root, "WEB-INF"), "lib");
    if (libDir.isDirectory()) {
      for (File file : libDir.listFiles()) {
        try {
          appUrls.add(file.toURI().toURL());
        } catch (MalformedURLException ex) {
          log.atWarning().withCause(ex).log("Could not get URL for file: %s", file);
        }
      }
    }
    return appUrls;
  }

  @Override
  public boolean requiresWebInf() {
    return true;
  }
}
