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

import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Creates new {@link DevAppServer DevAppServers} which can be used to launch web applications. */
// TODO: Describe the difference between standalone and testing servers.
public class DevAppServerFactory {

  static final String DEV_APP_SERVER_CLASS =
      "com.google.appengine.tools.development.DevAppServerImpl";

  private static final Class<?>[] DEV_APPSERVER_CTOR_ARG_TYPES = {File.class, File.class,
    File.class, File.class, String.class, Integer.TYPE, Boolean.TYPE, Map.class, String.class};

  private static final String USER_CODE_CLASSPATH_MANAGER_PROP =
      "devappserver.userCodeClasspathManager";
  private static final String USER_CODE_CLASSPATH = USER_CODE_CLASSPATH_MANAGER_PROP + ".classpath";
  private static final String USER_CODE_REQUIRES_WEB_INF =
      USER_CODE_CLASSPATH_MANAGER_PROP + ".requiresWebInf";

  /**
   * Creates a new {@link DevAppServer} ready to start serving.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param address Address to bind to
   * @param port Port to bind to
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(File appDir, String address, int port) {
    return createDevAppServer(appDir, null, address, port);
  }

  /**
   * Creates a new {@link DevAppServer} ready to start serving.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param externalResourceDir If not {@code null}, a resource directory external to the appDir.
   *     This parameter is now ignored.
   * @param address Address to bind to
   * @param port Port to bind to
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir, File externalResourceDir, String address, int port) {
    // externalResourceDir not used anymore.
    return createDevAppServer(
        appDir,
        new File(new File(appDir, "WEB-INF"), "web.xml"),
        new File(new File(appDir, "WEB-INF"), "appengine-web.xml"),
        address,
        port,
        true,
        /* installSecurityManager*/ false,
        new HashMap<String, Object>(),
        false);
  }

  /**
   * Creates a new {@link DevAppServer} ready to start serving.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param externalResourceDir If not {@code null}, a resource directory external to the appDir.
   *     This parameter is now ignored.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param noJavaAgent whether to disable detection of the Java agent or not
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir, File externalResourceDir, String address, int port, boolean noJavaAgent) {
    // externalResourceDir not used anymore.
    return createDevAppServer(
        appDir, null, null, address, port, true, true, new HashMap<String, Object>(), noJavaAgent);
  }

  /**
   * Creates a new {@link DevAppServer} with a custom classpath for the web app.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd. If {@code null}, defaults to
   *     <appDir>/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file. If {@code null},
   *     defaults to <appDir>/WEB-INF/appengine-web.xml.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install {@link StreamHandlerFactory}. This is
   *     "normal" behavior for the dev app server but tests may want to disable this since there are
   *     some compatibility issues with our custom handler and Selenium.
   * @param installSecurityManager Whether or not to install the dev appserver security manager. It
   *     is strongly recommended you pass {@code true} unless there is something in your test
   *     environment that prevents you from installing a security manager.
   * @param classpath The classpath of the test and all its dependencies (possibly the entire app).
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      boolean installSecurityManager,
      Collection<URL> classpath) {
    Map<String, Object> containerConfigProps = newContainerConfigPropertiesForTest(classpath);
    return createDevAppServer(
        appDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        installSecurityManager,
        containerConfigProps,
        false);
  }

  /**
   * Creates a new {@link DevAppServer} with a custom classpath for the web app.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd. If {@code null}, defaults to
   *     <appDir>/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file. If {@code null},
   *     defaults to <appDir>/WEB-INF/appengine-web.xml.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install {@link StreamHandlerFactory}. This is
   *     "normal" behavior for the dev app server but tests may want to disable this since there are
   *     some compatibility issues with our custom handler and Selenium.
   * @param installSecurityManager Whether or not to install the dev appserver security manager. It
   *     is strongly recommended you pass {@code true} unless there is something in your test
   *     environment that prevents you from installing a security manager.
   * @param classpath The classpath of the test and all its dependencies (possibly the entire app).
   * @param noJavaAgent whether to disable detection of the Java agent or not
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      boolean installSecurityManager,
      Collection<URL> classpath,
      boolean noJavaAgent) {
    Map<String, Object> containerConfigProps = newContainerConfigPropertiesForTest(classpath);
    return createDevAppServer(
        appDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        installSecurityManager,
        containerConfigProps,
        noJavaAgent);
  }

  // Note For some strange reason, code from AppMaker in google3 is accessing this
  // method by reflection. When I added the externalResourceDir parameter I was forced to
  // add yet another override of this method below so as to avoid fixing up all the google3
  // code that does this.
  // See java/com/google/apps/appmaker/webdriver/inject/AppMakerDevServer.java
  // TODO change AppMakerDevServer after a push of the SDK.

  /**
   * Creates a new {@link DevAppServer} ready to start serving.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd. If {@code null}, defaults to
   *     <appDir>/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file. If {@code null},
   *     defaults to <appDir>/WEB-INF/appengine-web.xml.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install {@link StreamHandlerFactory}. This is
   *     "normal" behavior for the dev app server but tests may want to disable this since there are
   *     some compatibility issues with our custom handler and Selenium.
   * @param installSecurityManager Whether or not to install the dev appserver security manager. It
   *     is strongly recommended you pass {@code true} unless there is something in your test
   *     environment that prevents you from installing a security manager.
   * @param containerConfigProperties {@link Map} that contains settings that will allow to inject a
   *     classpath and to not require a WEB-INF directory. (Only needed for testing).
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      boolean installSecurityManager,
      Map<String, Object> containerConfigProperties) {
    return createDevAppServer(
        appDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        installSecurityManager,
        containerConfigProperties,
        false);
  }

  /**
   * Creates a new {@link DevAppServer} with a custom classpath for the web app.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd. If {@code null}, defaults to
   *     {appDir}/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file. If {@code null},
   *     defaults to {appDir}/WEB-INF/appengine-web.xml.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install {@link StreamHandlerFactory}. This is
   *     "normal" behavior for the dev app server but tests may want to disable this since there are
   *     some compatibility issues with our custom handler and Selenium.
   * @param installSecurityManager Whether or not to install the dev appserver security manager. For
   *     the java8 runtime, you do not need a security manager.
   * @param containerConfigProperties Extra container configurations.
   * @param noJavaAgent whether to disable detection of the Java agent or not.
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      File appDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      boolean installSecurityManager,
      Map<String, Object> containerConfigProperties,
      boolean noJavaAgent) {
    return createDevAppServer(
        appDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        installSecurityManager,
        containerConfigProperties,
        noJavaAgent,
        null);
  }

  /**
   * Creates a new {@link DevAppServer} with a custom classpath and application ID for the web app.
   *
   * @param appDir The top-level directory of the web application to be run
   * @param webXmlLocation The location of a file whose format complies with
   *     http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd. If {@code null}, defaults to
   *     {appDir}/WEB-INF/web.xml
   * @param appEngineWebXmlLocation The name of the app engine config file. If {@code null},
   *     defaults to {appDir}/WEB-INF/appengine-web.xml.
   * @param address Address to bind to
   * @param port Port to bind to
   * @param useCustomStreamHandler If {@code true}, install {@link StreamHandlerFactory}. This is
   *     "normal" behavior for the dev app server but tests may want to disable this since there are
   *     some compatibility issues with our custom handler and Selenium.
   * @param installSecurityManager Whether or not to install the dev appserver security manager. For
   *     the java8 runtime, you do not need a security manager.
   * @param containerConfigProperties Extra container configurations.
   * @param noJavaAgent whether to disable detection of the Java agent or not.
   * @param applicationId Custom application ID. If {@code null}, defaults to use the primary
   *     module's application ID.
   * @return a {@code DevAppServer}
   */
  public DevAppServer createDevAppServer(
      final File appDir,
      final File webXmlLocation,
      final File appEngineWebXmlLocation,
      final String address,
      final int port,
      final boolean useCustomStreamHandler,
      final boolean installSecurityManager,
      final Map<String, Object> containerConfigProperties,
      final boolean noJavaAgent,
      final String applicationId) {

    return doCreateDevAppServer(
        appDir,
        webXmlLocation,
        appEngineWebXmlLocation,
        address,
        port,
        useCustomStreamHandler,
        containerConfigProperties,
        applicationId);
  }

  /**
   * Build a {@link Map} that contains settings that will allow us to inject our own classpath and
   * to not require a WEB-INF directory.
   */
  private Map<String, Object> newContainerConfigPropertiesForTest(Collection<URL> classpath) {
    Map<String, Object> containerConfigProps = new HashMap<>();
    Map<String, Object> userCodeClasspathManagerProps = new HashMap<>();
    userCodeClasspathManagerProps.put(USER_CODE_CLASSPATH, classpath);
    userCodeClasspathManagerProps.put(USER_CODE_REQUIRES_WEB_INF, false);
    containerConfigProps.put(USER_CODE_CLASSPATH_MANAGER_PROP, userCodeClasspathManagerProps);
    return containerConfigProps;
  }

  private DevAppServer doCreateDevAppServer(
      File appDir,
      File webXmlLocation,
      File appEngineWebXmlLocation,
      String address,
      int port,
      boolean useCustomStreamHandler,
      Map<String, Object> containerConfigProperties,
      String applicationId) {
    if (webXmlLocation == null) {
      webXmlLocation = new File(appDir, "WEB-INF/web.xml");
    }
    if (appEngineWebXmlLocation == null) {
      appEngineWebXmlLocation = new File(appDir, "WEB-INF/appengine-web.xml");
    }
    if (webXmlLocation.exists()) {
      WebXmlReader webXmlReader = new WebXmlReader(appDir.getAbsolutePath());

      WebXml webXml = webXmlReader.readWebXml();
      webXml.validate();
      String servletVersion = webXmlReader.getServletVersion();

      if (Double.parseDouble(servletVersion) >= 4.0) {
        // Jetty12 starts at version 4.0, EE8.
        System.setProperty("appengine.use.jetty12", "true");
        AppengineSdk.resetSdk();
      }
      if (Double.parseDouble(servletVersion) >= 6.0) {
        // Jakarta Servlet start at version 6.0, we force EE 10 for it.
        System.setProperty("appengine.use.EE10", "true");
        AppengineSdk.resetSdk();
      }
    }
    DevAppServerClassLoader loader = DevAppServerClassLoader.newClassLoader(
        DevAppServerFactory.class.getClassLoader());
    DevAppServer devAppServer;

    try {
      Class<?> devAppServerClass = Class.forName(DEV_APP_SERVER_CLASS, false, loader);


      Constructor<?> cons = devAppServerClass.getConstructor(DEV_APPSERVER_CTOR_ARG_TYPES);
      cons.setAccessible(true);
      devAppServer =
          (DevAppServer)
              cons.newInstance(
                  appDir,
                  null,
                  webXmlLocation,
                  appEngineWebXmlLocation,
                  address,
                  port,
                  useCustomStreamHandler,
                  containerConfigProperties,
                  applicationId);
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
