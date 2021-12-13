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

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Config for a dev appserver that launches as part of a test.
 *
 */
public interface DevAppServerTestConfig {

  /**
   * @return The root of the sdk installation
   */
  File getSdkRoot();

  /**
   * @return The top-level directory of the web application to run. 
   */
  File getAppDir();

  /**
   * @return The location of web.xml.  If {@code null},
   * {@link #getAppDir()}/WEB-INF/web.xml will be used.
   */
  File getWebXml();

  /**
   * @return The location of appengine-web.xml.  If {@code null},
   * {@link #getAppDir()}/WEB-INF/appengine-web.xml will be used.
   */
  File getAppEngineWebXml();

  /**
   * @return If {@code true}, the dev appserver will be installed with the
   * local app engine {@link SecurityManager}.  It is strongly recommended you
   * install the SecurityManager unless your testing environment does not
   * allow SecurityManager to be installed.
   */
  boolean installSecurityManager();

  /**
   * @return The classpath for all application and test code
   */
  List<URL> getClasspath();

  /**
   * @return The name of the system property that can be consulted to retrieve
   * the port on which the dev appserver is running.
   */
  String getPortSystemProperty();
}