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
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * {@code ClassPathUtils} provides utility functions that are useful in dealing with class paths.
 *
 */
public class ClassPathUtils {
  // Note: we should not depend on Guava or Flogger in the small bootstap Main.
  private static final Logger logger = Logger.getLogger(ClassPathUtils.class.getName());

  private static final String RUNTIME_BASE_PROPERTY = "classpath.runtimebase";
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
  private File frozenApiJarFile;

  public ClassPathUtils() {
    this(null);
  }

  public ClassPathUtils(File root) {

    String runtimeBase = System.getProperty(RUNTIME_BASE_PROPERTY);
    if (runtimeBase == null) {
      throw new RuntimeException("System property not defined: " + RUNTIME_BASE_PROPERTY);
    }
    this.root = root;

    if (!new File(runtimeBase, "java_runtime_launcher").exists()) {
      initForJava11OrAbove(runtimeBase);
      return;
    }

    boolean useMavenJars = Boolean.getBoolean(USE_MAVENJARS);
    String runtimeImplJar = null;
    String cloudDebuggerJar = null;
    String profilerJar = null;
    if (System.getenv("GAE_PROFILER_MODE") != null) {
      profilerJar = "profiler.jar"; // Close source, not in Maven.;
      logger.log(Level.INFO, "AppEngine profiler enabled.");
    }
    // This is only for Java11 or later runtime:
    if (Boolean.getBoolean("use.java11")) {
      runtimeImplJar = "runtime-impl.jar";
      // Java11: No need for Cloud Debugger special treatement, we rely on pure open source agent.
    } else {
      runtimeImplJar = "runtime-impl.jar";
      cloudDebuggerJar = "frozen_debugger.jar";
    }
    List<String> runtimeClasspathEntries =
        useMavenJars
            ? Arrays.asList("jars/runtime-impl.jar", cloudDebuggerJar, profilerJar)
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

    // The frozen API jar we must use for ancient customers still relying on the obsolete feature
    // that when deploying with api_version: 1.0 in generated app.yaml
    // we need to add our own legacy jar.
    frozenApiJarFile = new File(new File(root, runtimeBase), "/appengine-api.jar");
    if (useMavenJars) {
      System.setProperty(RUNTIME_SHARED_PROPERTY, runtimeBase + "/jars/runtime-shared.jar");
      System.setProperty(API_PROPERTY, "1.0=" + runtimeBase + "/jars/appengine-api-1.0-sdk.jar");
      System.setProperty(
          APPENGINE_API_LEGACY_PROPERTY, runtimeBase + "/jars/appengine-api-legacy.jar");
    } else {
      System.setProperty(RUNTIME_SHARED_PROPERTY, runtimeBase + "/runtime-shared.jar");
      System.setProperty(API_PROPERTY, "1.0=" + runtimeBase + "/appengine-api.jar");
    }
    System.setProperty(CONNECTOR_J_PROPERTY, runtimeBase + "/jdbc-mysql-connector.jar");
    System.setProperty(PREBUNDLED_PROPERTY, runtimeBase + "/conscrypt.jar");
    System.setProperty(LEGACY_PROPERTY, runtimeBase + "/legacy.jar");
  }

  private void initForJava11OrAbove(String runtimeBase) {
    // No native launcher means gen2 java11 or java17, not java8.
    /*
        New content is very simple now (from maven jars):
        ls blaze-bin/java/com/google/apphosting/runtime_java11/deployment_java11
        appengine-api-1.0-sdk.jar
        runtime-impl.jar
        runtime-main.jar
        runtime-shared.jar
    */
    List<String> runtimeClasspathEntries = Arrays.asList("runtime-impl.jar");

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

    // Keep old properties for absolute compatibility if ever some public apps depend on them:
    System.setProperty(RUNTIME_IMPL_PROPERTY, runtimeClasspath);
    logger.log(Level.INFO, "Using runtime classpath: " + runtimeClasspath);

    System.setProperty(RUNTIME_SHARED_PROPERTY, runtimeBase + "/runtime-shared.jar");
    frozenApiJarFile = new File(runtimeBase, "/appengine-api-1.0-sdk.jar");
  }

  public URL[] getRuntimeImplUrls() {
    return parseClasspath(System.getProperty(RUNTIME_IMPL_PROPERTY));
  }

  public URL[] getRuntimeSharedUrls() {
    return parseClasspath(System.getProperty(RUNTIME_SHARED_PROPERTY));
  }

  public URL[] getPrebundledUrls() {
    String path = System.getProperty(PREBUNDLED_PROPERTY);
    if (path == null) {
      return new URL[0];
    } else {
      return parseClasspath(path);
    }
  }

  public URL[] getConnectorJUrls() {
    String path = System.getProperty(CONNECTOR_J_PROPERTY);
    if (path == null) {
      return new URL[0];
    } else {
      return parseClasspath(path);
    }
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
   * Returns a {@link File} for the frozen old API jar,
   */
  public File getFrozenApiJar() {
    return frozenApiJarFile;
  }

  @Nullable
  public File getAppengineApiLegacyJar() {
    String filename = System.getProperty(APPENGINE_API_LEGACY_PROPERTY);
    return filename == null ? null : new File(root, filename);
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
}
