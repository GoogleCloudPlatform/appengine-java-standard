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
import com.google.apphosting.utils.config.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A webapp {@code ClassLoader}. This {@code ClassLoader} isolates
 * webapps from the {@link DevAppServer} and anything else
 * that might happen to be on the system classpath.
 *
 */
public class IsolatedAppClassLoader extends URLClassLoader {

  private static final Logger logger = Logger.getLogger(IsolatedAppClassLoader.class.getName());

  // Web-default.xml files for Jetty9 based devappserver1.
  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVER1 =
      "com/google/appengine/tools/development/jetty9/webdefault.xml";

  // This task queue related servlet should be loaded by the application classloader when the
  // api jar is used by the application, and default to the runtime classloader when the application
  // does not have the API jar in the classpath so that the Jetty container can boot, even if the
  // servlet is not used by the application.
  // This change is required now with the new Jetty 9.4 classloader which is more strict.
  private static final String DEFERRED_TASK_SERVLET =
      "com.google.apphosting.utils.servlet.DeferredTaskServlet";

  // Session Data class must be loaded by the runtime classloader, as it is only used by the runtime
  // servlet session management. For Jetty9.4, the newer session management has a cleaner
  // classloading implementation.
  private static final String SESSION_DATA_CLASS = "com.google.apphosting.runtime.SessionData";

  private final ClassLoader devAppServerClassLoader;
  private final Set<URL> sharedCodeLibs;
  private final ImmutableSet<String> classesToBeLoadedByTheRuntimeClassLoader;

  @SuppressWarnings("URLEqualsHashCode")
  public IsolatedAppClassLoader(File appRoot, File externalResourceDir, URL[] urls,
      ClassLoader devAppServerClassLoader) {
    super(urls, ClassLoaderUtil.getPlatformClassLoader());
    checkWorkingDirectory(appRoot, externalResourceDir);
    this.devAppServerClassLoader = devAppServerClassLoader;
    this.sharedCodeLibs = new HashSet<>(AppengineSdk.getSdk().getSharedLibs());
    this.classesToBeLoadedByTheRuntimeClassLoader =
        new ImmutableSet.Builder<String>()
            .add(SESSION_DATA_CLASS)
            .addAll(
                getServletAndFilterClasses(
                    IsolatedAppClassLoader.class
                        .getClassLoader()
                        .getResourceAsStream(WEB_DEFAULT_LOCATION_DEVAPPSERVER1)))
            .build();
  }

  /**
   * Issues a warning if the current working directory != {@code appRoot},
   * or {@code externalResourceDir}.
   *
   * The working directory of remotely deployed apps always == appRoot.
   * For DevAppServer, We don't currently force users to set their working
   * directory equal to the appRoot. We also don't set it for them
   * (due to extent ramifications). The best we can do at the moment is to
   * warn them that they may experience permission problems in production
   * if they access files in a working directory != appRoot.
   *
   * If we are using an external resource directory, then it is also fine
   * for the working directory to point there.
   *
   * @param appRoot
   */
  private static void checkWorkingDirectory(File appRoot, File externalResourceDir) {
    File workingDir = new File(System.getProperty("user.dir"));

    String canonicalWorkingDir = null;
    String canonicalAppRoot = null;
    String canonicalExternalResourceDir = null;

    try {
      canonicalWorkingDir = workingDir.getCanonicalPath();
      canonicalAppRoot = appRoot.getCanonicalPath();
      if (externalResourceDir != null) {
        canonicalExternalResourceDir = externalResourceDir.getCanonicalPath();
      }
    } catch (IOException e) {
      logger.log(Level.FINE, "Unable to compare the working directory and app root.", e);
    }

    if (canonicalWorkingDir != null && !canonicalWorkingDir.equals(canonicalAppRoot)) {
      if (canonicalExternalResourceDir != null
          && canonicalWorkingDir.equals(canonicalExternalResourceDir)) {
        return;
      }
      String newLine = System.getProperty("line.separator");
      String workDir = workingDir.getAbsolutePath();
      String appDir = appRoot.getAbsolutePath();
      String msg = "Your working directory, (" + workDir + ") is not equal to your " + newLine
          + "web application root (" + appDir + ")" + newLine
          + "You will not be able to access files from your working directory on the "
          + "production server." + newLine;
      logger.warning(msg);
    }
  }

  @Override
  public URL getResource(String name) {
    // Check our shared jars first, similar to loadClass(String,boolean).
    URL resource = devAppServerClassLoader.getResource(name);
    if (resource != null) {
      // We found one, it should be a jar.  We need to parse the jar file out of it.
      if (resource.getProtocol().equals("jar")) {
        int bang = resource.getPath().indexOf('!');
        if (bang > 0) {
          try {
            URL url = new URL(resource.getPath().substring(0, bang));
            // Okay, now check if the file is shared.
            if (sharedCodeLibs.contains(url)) {
              // Yes, so return the original jar URL.
              return resource;
            }
          } catch (MalformedURLException ex) {
            logger.log(Level.WARNING, "Unexpected exception while loading " + name, ex);
          }
        }
      }
    }
    // Resource file was not a shared jar, so check if we have it (or the JRE).
    return super.getResource(name);
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {

    if (name.equals(DEFERRED_TASK_SERVLET)) {
      try {
        return super.loadClass(name, resolve);
      } catch (ClassNotFoundException ignore) {
        // Fall through for the case the application does not provide the appengine API jar.
        // We use the devappserver internal API jar to load this servlet defined in webdefault.xml.
        // We do not use the servlet, but Jetty can boot the application correctly.
      }
    }
    // Favor the DevAppServer's shared classes over our own
    try {
      final Class<?> c = devAppServerClassLoader.loadClass(name);

      // See where it came from.
      CodeSource source = AccessController.doPrivileged(
          new PrivilegedAction<CodeSource>() {
            @Override
            public CodeSource run() {
              return c.getProtectionDomain().getCodeSource();
            }
          });

      // Load classes from the JRE.
      // We can't just block non-allowlisted classes from being loaded. The JVM
      // eagerly loads classes before they're actually used. (App Engine
      // handles this with stubs). We handle allowlisting in the DevAppServer
      // with a JVM agent.
      if (source == null) {
        return c;
      }

      // A shared class that we can load
      String systemClassPrefix =
          System.getProperty(DevAppServerClassLoader.SYSTEM_CLASS_PREFIX_PROPERTY, "///");
      if (classCanBeLoadedByRuntimeClassLoader(source.getLocation(), name)
          || name.startsWith(systemClassPrefix)
          || name.startsWith("org.jacoco.agent.")) {
        if (resolve) {
          resolveClass(c);
        }
        return c;
      }
    } catch (ClassNotFoundException e) {
      // Fall through
    }

    // Return our own classes here (technically JRE classes too, but those
    // are already returned above).
    return super.loadClass(name, resolve);
  }

  /**
   * Returns the set of all servlet and filter class names from the given inputStream xml resource.
   * Used for example with the /com/google/appengine/tools/development/jetty9/webdefault.xml
   * resource.
   */
  @VisibleForTesting
  static Set<String> getServletAndFilterClasses(InputStream inputStream) {
    ImmutableSet.Builder<String> servletsAndFilters = ImmutableSet.builder();
    Element topElement = XmlUtils.parseXml(inputStream, null).getDocumentElement();
    NodeList nodeList = topElement.getElementsByTagName("filter-class");
    for (int i = 0; i < nodeList.getLength(); i++) {
      servletsAndFilters.add(nodeList.item(i).getTextContent().trim());
    }
    nodeList = topElement.getElementsByTagName("servlet-class");
    for (int i = 0; i < nodeList.getLength(); i++) {
      servletsAndFilters.add(nodeList.item(i).getTextContent().trim());
    }
    return servletsAndFilters.build();
  }

  /**
   * Returns true if the given classname from the location can be safely loaded by the AppEngine
   * runtime classloader.
   */
  private boolean classCanBeLoadedByRuntimeClassLoader(URL location, String name) {
    if (sharedCodeLibs.contains(location)) {
      return true;
    }
    return classesToBeLoadedByTheRuntimeClassLoader.contains(name);
  }
}
