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

import static java.util.stream.Collectors.joining;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code ClassPathUtils} provides utility functions that are useful in dealing with class paths.
 *
 */
public class ClassPathUtils {
  // Note: we should not depend on Guava or Flogger in the small bootstap Main.
  private static final Logger logger = Logger.getLogger(ClassPathUtils.class.getName());

  private static final String RUNTIME_BASE_PROPERTY = "classpath.runtimebase";
  private static final String USE_JETTY93 = "use.jetty93";
  private static final String USE_JETTY94 = "use.jetty94";
  private static final String USE_MAVENJARS = "use.mavenjars";
  private static final String RUNTIME_IMPL_PROPERTY = "classpath.runtime-impl";
  private static final String RUNTIME_SHARED_PROPERTY = "classpath.runtime-shared";
  private static final String PREBUNDLED_PROPERTY = "classpath.prebundled";
  private static final String API_PROPERTY = "classpath.api-map";
  private static final String CONNECTOR_J_PROPERTY = "classpath.connector-j";
  private static final String APPENGINE_API_LEGACY_PROPERTY = "classpath.appengine-api-legacy";
  private static final String LEGACY_PROPERTY = "classpath.legacy";
  // Cannot use Guava library in this classloader.
  private static final String PATH_SEPARATOR = System.getProperty("path.separator");

  private final File root;
  private final Map<String, File> apiVersionMap;
  private Collection<File> runtimeProvidedFiles;

  public ClassPathUtils() {
    this(null);
  }

  public ClassPathUtils(File root) {

    boolean useJetty93 = Boolean.getBoolean(USE_JETTY93);
    boolean useJetty94 = Boolean.getBoolean(USE_JETTY94);
    // The jetty9.4 boolean is now set via the native launcher, the only way to undo this flag
    // at this level of the code is to test if the customer is now using the use.jetty93 as true in
    // their app, so we can overwrite the default instance definition flag.
    // The jetty9.3 boolean should override any value set for jetty9.4.
    // TODO remove when we are %100 on Jetty9.4 in prod.
    if (useJetty93) {
      useJetty94 = false;
      System.setProperty(USE_JETTY94, "false");
    }
    boolean useMavenJars = Boolean.getBoolean(USE_MAVENJARS);
    String runtimeBase = System.getProperty(RUNTIME_BASE_PROPERTY);
    if (runtimeBase == null) {
      throw new RuntimeException("System property not defined: " + RUNTIME_BASE_PROPERTY);
    }
    String runtimeImplJar = null;
    String cloudDebuggerJar = null;
    // This is only for Java11 or later runtime:
    if (new File(runtimeBase, "runtime-impl11.jar").exists() || Boolean.getBoolean("use.java11")) {
      runtimeImplJar = "runtime-impl11.jar";
      // Java11: No need for Cloud Debugger special treatement, we rely on pure open source agent.
    } else {
      runtimeImplJar = "runtime-impl.jar";
      cloudDebuggerJar = "frozen_debugger.jar";
    }
    List<String> runtimeClasspathEntries =
        useJetty94
            ? (useMavenJars
                ? Arrays.asList(
                    "jars/runtime-impl.jar", "jars/appengine-api-1.0-sdk.jar", cloudDebuggerJar)
                : Arrays.asList(
                    runtimeImplJar,
                    "runtime-impl-jetty94.jar",
                    cloudDebuggerJar,
                    "runtime-impl-third-party-jetty94.jar",
                    "appengine-api-1.0-sdk.jar",
                    "runtime-appengine-api.jar"))
            : Arrays.asList(
                runtimeImplJar,
                cloudDebuggerJar,
                "runtime-impl-third-party.jar",
                "runtime-appengine-api.jar");

    String runtimeClasspath =
        runtimeClasspathEntries.stream()
            .filter(t -> t != null)
            .map(s -> runtimeBase + "/" + s)
            .collect(joining(PATH_SEPARATOR));

    if (System.getProperty(RUNTIME_IMPL_PROPERTY) != null) {
      // Prepend existing value, only used in our tests.
      runtimeClasspath =
          System.getProperty(RUNTIME_IMPL_PROPERTY) + PATH_SEPARATOR + runtimeClasspath;
    }
    // Only for Java8g, TODO(b/122040046)
    if (new File(runtimeBase, "runtime-rpc-plugins.jar").exists()) {
      runtimeClasspath += ":" + runtimeBase + "/runtime-rpc-plugins.jar";
    }
    // Keep old properties for absolute compatibility if ever some public apps depend on them:
    System.setProperty(RUNTIME_IMPL_PROPERTY, runtimeClasspath);
    logger.log(Level.INFO, "Using runtime classpath: " + runtimeClasspath);

    if (useJetty94 && useMavenJars) {
      System.setProperty(RUNTIME_SHARED_PROPERTY, runtimeBase + "/jars/runtime-shared.jar");
      System.setProperty(API_PROPERTY, "1.0=" + runtimeBase + "/jars/appengine-api-1.0-sdk.jar");
      System.setProperty(APPENGINE_API_LEGACY_PROPERTY, runtimeBase + "/jars/appengine-api-legacy.jar");
    } else {
      System.setProperty(RUNTIME_SHARED_PROPERTY, runtimeBase + "/runtime-shared.jar");
      System.setProperty(API_PROPERTY, "1.0=" + runtimeBase + "/appengine-api.jar");
    }
    System.setProperty(CONNECTOR_J_PROPERTY, runtimeBase + "/jdbc-mysql-connector.jar");
    System.setProperty(PREBUNDLED_PROPERTY, runtimeBase + "/conscrypt.jar");
    System.setProperty(LEGACY_PROPERTY, runtimeBase + "/legacy.jar");

    this.root = root;
    apiVersionMap = new HashMap<String, File>();
    initRuntimeProvidedFiles();
  }

  public URL[] getRuntimeImplUrls() {
    return parseClasspath(System.getProperty(RUNTIME_IMPL_PROPERTY));
  }

  public URL[] getRuntimeSharedUrls() {
    return parseClasspath(System.getProperty(RUNTIME_SHARED_PROPERTY));
  }

  public URL[] getPrebundledUrls() {
    return parseClasspath(System.getProperty(PREBUNDLED_PROPERTY));
  }

  public URL[] getConnectorJUrls() {
    return parseClasspath(System.getProperty(CONNECTOR_J_PROPERTY));
  }

  /**
   * Returns the URLs for legacy jars. This may be empty or it may be one or more jars that contain
   * classes like {@code com.google.appengine.repackaged.org.joda.Instant}, the old form of
   * repackaging. We've switched to classes like {@code
   * com.google.appengine.repackaged.org.joda.$Instant}, with a {@code $}, but this jar can
   * optionally be added to an app's classpath if it is referencing the old names. Other legacy
   * classes, unrelated to repackaging, may also appear in these jars.
   */
  public URL[] getLegacyJarUrls() {
    String path = System.getProperty(LEGACY_PROPERTY);
    if (path == null) {
      return new URL[0];
    } else {
      return parseClasspath(path);
    }
  }

  /**
   * Returns a {@link File} for the API jar that corresponds to the specified version, or {@code
   * null} if no jar for this version is available.
   */
  public File getApiJarForVersion(String apiVersion) {
    return apiVersionMap.get(apiVersion);
  }


  public File getAppengineApiLegacyJar() {
     String filename = System.getProperty(APPENGINE_API_LEGACY_PROPERTY);
     return filename == null ? null : new File(root, filename);
  }

  /**
   * Returns all runtime-provided files which are loaded in the UserClassLoader as unprivileged user
   * code. This includes code like the appengine API as well as bits of the JRE that we implement at
   * the user-level.
   */
  public Collection<File> getRuntimeProvidedFiles() {
    return Collections.unmodifiableCollection(runtimeProvidedFiles);
  }

  /**
   * Parse the specified string into individual files (using the machine's path separator) and
   * return an array containing a {@link URL} object representing each file.
   */
  public URL[] parseClasspath(String classpath) {
    List<URL> urls = new ArrayList<URL>();

    StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      try {
        // Avoid File.toURI() and File.toURL() here as they do an
        // unnecessary stat call.
        urls.add(new URL("file", "", new File(root, token).getAbsolutePath().replace('\\', '/')));
      } catch (MalformedURLException ex) {
        logger.log(Level.WARNING, "Could not parse " + token + " as a URL, ignoring.", ex);
      }
    }

    return urls.toArray(new URL[0]);
  }

  private void initRuntimeProvidedFiles() {
    runtimeProvidedFiles = new ArrayList<File>();
    addJars(runtimeProvidedFiles, getPrebundledUrls());
    addJars(runtimeProvidedFiles, getConnectorJUrls());

    // We consider API jars to also be prebundled.
    addApiJars(runtimeProvidedFiles);
  }

  private void addJars(Collection<File> files, URL[] urls) {
    for (URL url : urls) {
      File f = new File(url.getPath());
      files.add(f);
    }
  }

  private void addApiJars(Collection<File> files) {
    // The string for the api mapping follows the grammar:
    // <single-mapping>     is <version>=<path>
    // <additional-mapping> is :<version>=<path>
    // <mappings>           is <single-mapping> <additional-mapping>+
    String apiMapping = System.getProperty(API_PROPERTY);

    if (apiMapping != null && !apiMapping.isEmpty()) {
      StringTokenizer tokenizer = new StringTokenizer(apiMapping, File.pathSeparator);
      while (tokenizer.hasMoreTokens()) {
        String token = tokenizer.nextToken();
        int equals = token.indexOf('=');
        if (equals != -1) {
          String apiVersion = token.substring(0, equals);
          String filename = token.substring(equals + 1);
          File file = new File(root, filename);
          apiVersionMap.put(apiVersion, file);
          files.add(file);
        } else {
          logger.warning("Could not parse " + token + " as api-version=jar, ignoring.");
        }
      }
    } else {
      logger.severe("Property " + API_PROPERTY + " not set, no API versions available.");
    }
  }
}
