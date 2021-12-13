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

/**
 * Base {@link DevAppServerTestConfig} implementation with common defaults:
 * Use <app dir>/WEB-INF/web.xml.
 * Use <app dir>/WEB-INF/appengine-web.xml.
 * Install the security manager.
 * Make the dev appserver port available in a system property named
 * {@link #DEFAULT_PORT_SYSTEM_PROPERTY}.
 *
 */
public abstract class BaseDevAppServerTestConfig implements DevAppServerTestConfig {

  public static final String DEFAULT_PORT_SYSTEM_PROPERTY = "appengine.devappserver.test.port";

  @Override
  public File getWebXml() {
    return null;
  }

  @Override
  public File getAppEngineWebXml() {
    return null;
  }

  @Override
  public boolean installSecurityManager() {
    // No security manager in Jetty9/Java8.
    return false;
  }

  @Override
  public String getPortSystemProperty() {
    return DEFAULT_PORT_SYSTEM_PROPERTY;
  }
}
