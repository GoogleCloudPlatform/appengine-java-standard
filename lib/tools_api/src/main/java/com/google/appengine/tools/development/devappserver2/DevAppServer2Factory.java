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

package com.google.appengine.tools.development.devappserver2;

import com.google.appengine.tools.development.DevAppServer;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Creates new {@link DevAppServer DevAppServers} which can be used to launch
 * web applications.
 *
 */
class DevAppServer2Factory {
  private static final Class<?>[] DEV_APPSERVER_CTOR_ARG_TYPES = {File.class, File.class,
    File.class, File.class, String.class, int.class, boolean.class, Map.class};

  DevAppServer createDevAppServer(final File appDir, final File externalResourceDir,
      final File webXmlLocation, final File appEngineWebXmlLocation, final String address,
      final int port, final boolean useCustomStreamHandler, final boolean installSecurityManager,
      final Map<String, ?> containerConfigProperties, final boolean noJavaAgent) {
    return doCreateDevAppServer(
        appDir,
        externalResourceDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        containerConfigProperties);
  }

  private DevAppServer doCreateDevAppServer(
      File appDir,
      File externalResourceDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      Map<String, ?> containerConfigProperties) {

    DevAppServer2ClassLoader loader = DevAppServer2ClassLoader.newClassLoader(
        getClass().getClassLoader());
    DevAppServer devAppServer;

    try {
      Class<?> devAppServerClass = Class.forName(DevAppServer2Impl.class.getName(), false, loader);

      Constructor<?> cons = devAppServerClass.getDeclaredConstructor(DEV_APPSERVER_CTOR_ARG_TYPES);
      cons.setAccessible(true);
      devAppServer = (DevAppServer) cons.newInstance(
          appDir, externalResourceDir, webXmlLocation, appEngineWebXmlLocation, address, port,
          useCustomStreamHandler, containerConfigProperties);
    } catch (Exception e) {
      Throwable t = e;
      if (e instanceof InvocationTargetException) {
        t = e.getCause();
      }
      throw new RuntimeException("Unable to create a DevAppServer", t);
    }
    return devAppServer;
  }
}
