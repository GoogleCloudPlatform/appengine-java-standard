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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin for the "null" Java sandbox.
 *
 * <p>This sandbox does not enforce any special checks and uses a plain
 * {@link URLClassLoader} to load the runtime classes.
 */
public class NullSandboxPlugin {
  private static final Logger rootLogger = Logger.getLogger("");

  static final String ALWAYS_SCAN_CLASS_DIRS_PROPERTY =
      "com.google.appengine.always.scan.class.dirs";

  private volatile ClassLoader runtimeLoader;
  private volatile ClassLoader applicationLoader;
  private volatile ClassPathUtils classPathUtils;

  /**
   * Class loader used to load classes that are shared between the runtime
   * and the application, e.g. the java.servlet API and the ApiProxy class.
   */
  private ClassLoader sharedClassLoader;

  public NullSandboxPlugin() {
  }

  /**
   * For unit tests only. Use of this constructor precludes calling
   * {@code createRuntimeClassLoader}.
   */
  public NullSandboxPlugin(
      ClassLoader runtimeLoader, ClassLoader sharedLoader, ClassPathUtils classPathUtils) {
    this.sharedClassLoader = sharedLoader;
    this.classPathUtils = classPathUtils;
    this.runtimeLoader = runtimeLoader;
  }

  /**
   * Creates a {@code ClassLoader} suitable to load the runtime from.
   *
   * @param classPathUtils a ClassPathUtils object configured according to the command-line
   *     arguments passed to the main class
   * @param appsRoot path to the base directory for applications
   * @return a runtime ClassLoader
   * @throws IllegalStateException if this method has been called once already
   */
  public ClassLoader createRuntimeClassLoader(ClassPathUtils classPathUtils, String appsRoot) {
    if (runtimeLoader != null) {
      throw new IllegalStateException("createRuntimeClassLoader already called");
    }
    ClassLoader loader = doCreateRuntimeClassLoader(classPathUtils, appsRoot);
    this.classPathUtils = classPathUtils;
    this.runtimeLoader = loader;
    return loader;
  }

  /**
   * Creates a {@code URLClassLoader} suitable to load the runtime from.
   *
   * @param classPathUtils a ClassPathUtils object configured according to the command-line
   *     arguments passed to the main class
   * @param appsRoot path to the base directory for applications
   * @return a runtime ClassLoader
   */
  protected ClassLoader doCreateRuntimeClassLoader(ClassPathUtils classPathUtils, String appsRoot) {
    sharedClassLoader = new URLClassLoader(classPathUtils.getRuntimeSharedUrls());
    return new URLClassLoader(classPathUtils.getRuntimeImplUrls(), sharedClassLoader);
  }

  /** Returns the app classloader. */
  public ClassLoader getApplicationClassLoader() {
    checkApplicationClassLoader();
    return applicationLoader;
  }

  public void setApplicationClassLoader(ClassLoader applicationLoader) {
    this.applicationLoader = applicationLoader;
  }

  /**
   * Creates a {@code ClassLoader} for application code.
   *
   * @param userUrls the URLs comprising all user application code
   * @param contextRoot the root for the user's application
   * @param environment the process environment for the application
   * @return a user URLClassLoader
   * @throws IllegalStateException if createRuntimeClassLoader wasn't called or didn't
   *      complete successfully
   */
  public ClassLoader createApplicationClassLoader(
      URL[] userUrls, File contextRoot, ApplicationEnvironment environment) {
    checkRuntimeClassLoader();
    ClassLoader loader = doCreateApplicationClassLoader(userUrls, contextRoot, environment);
    this.applicationLoader = loader;
    return loader;
  }

  /**
   * Creates a {@code ClassLoader} for application code.
   *
   * @param userUrls the URLs comprising all user application code
   * @param contextRoot the root for the user's application
   * @param environment the process environment for the application
   * @return a user ClassLoader
   */
  protected ClassLoader doCreateApplicationClassLoader(
      URL[] userUrls, File contextRoot, ApplicationEnvironment environment) {
      // Add prebundled jars at the end of the application class path. This code and its helper
      // methods were copied from UserClassLoaderFactory.createClassLoader.
      URL[] prebundledUrls = getClassPathUtils().getPrebundledUrls();
      userUrls = append(userUrls, prebundledUrls);
      // Add the j-connector class path at the front. Also copied from
      // UserClassLoaderFactory.createClassLoader.
      if (environment.getRuntimeConfiguration().getCloudSqlJdbcConnectivityEnabled()
          && environment.getUseGoogleConnectorJ()) {
        URL[] urls = getClassPathUtils().getConnectorJUrls();
        userUrls = append(urls, userUrls);
    }
    URL[] legacyUrls = getClassPathUtils().getLegacyJarUrls();
    boolean alwaysScanClassDirs = "true".equalsIgnoreCase(
        environment.getSystemProperties().get(ALWAYS_SCAN_CLASS_DIRS_PROPERTY));
    return new ApplicationClassLoader(
        userUrls, legacyUrls, sharedClassLoader, alwaysScanClassDirs);
  }

  /**
   * Returns the {@code ClassPathUtils} object used to create the runtime class loader.
   *
   * <p>This method MUST be called after {@link #createRuntimeClassLoader}.
   *
   * @return the ClassPathUtils associated with the runtime class loader
   * @throws IllegalStateException if createRuntimeClassLoader wasn't called or didn't
   *      complete successfully
   */
  public ClassPathUtils getClassPathUtils() {
    checkRuntimeClassLoader();
    return classPathUtils;
  }

  /**
   * Verifies that the runtime classloader was created.
   * @throws IllegalStateException otherwise
   */
  private void checkRuntimeClassLoader() {
    if (runtimeLoader == null) {
      throw new IllegalStateException("must call createRuntimeClassLoader first");
    }
  }

  /**
   * Verifies that the application classloader was created.
   * @throws IllegalStateException otherwise
   */
  private void checkApplicationClassLoader() {
    if (applicationLoader == null) {
      throw new IllegalStateException("must call createApplicationClassLoader first");
    }
  }

  private static <T> T[] append(T[] first, T[] second) {
    T[] newArray = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, newArray, first.length, second.length);
    return newArray;
  }

  /** Starts re-routing application logs through the ApiProxy. */
  public void startCapturingApplicationLogs() {
    // We need to load the appropriate log handler using the runtime class loader
    // because it depends on the ApiProxy class, which is not accessible to code loaded
    // by the system class loader.
    try {
      Class<?> handlerClass =
          Class.forName("com.google.apphosting.runtime.NullSandboxLogHandler", true, runtimeLoader);
      Object logHandler = handlerClass.getDeclaredConstructor().newInstance();
      Method m = handlerClass.getMethod("init", Logger.class);
      m.invoke(logHandler, rootLogger);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("failed to install the LogHandler for application logs", e);
    }
  }

  /** Changes the current directory. */
  public static void chdir(String path) throws IOException {
    try {
      chdir0(path);
    } catch (UnsatisfiedLinkError e) {
      Logger.getLogger(NullSandboxPlugin.class.getName())
          .log(Level.WARNING, "Could not call native chdir", e);
    }
  }

  private static native void chdir0(String path) throws IOException;
}
