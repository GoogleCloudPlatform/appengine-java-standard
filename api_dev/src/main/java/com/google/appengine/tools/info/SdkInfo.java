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

package com.google.appengine.tools.info;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Retrieves installation information for the App Engine SDK. */
public class SdkInfo {

  public static final String SDK_ROOT_PROPERTY = "appengine.sdk.root";

  private static final String DEFAULT_SERVER = "appengine.google.com";

  // Relative path from SDK Root for the Jetty Home lib directory.
  static final String JETTY_HOME_LIB_PATH = "jetty/jetty-home/lib";

  private static boolean isInitialized = false;
  private static File sdkRoot = null;
  private static List<File> userLibFiles = null;
  private static List<URL> userLibs = null;
  private static SortedMap<String, OptionalLib> optionalUserLibsByName = null;
  private static SortedMap<String, OptionalLib> optionalToolsLibsByName = null;
  private static boolean isDevAppServerTest;

  private static final FileFilter NO_HIDDEN_FILES =
      new FileFilter() {
        @Override
        public boolean accept(File file) {
          return !file.isHidden();
        }
      };

  static List<URL> toURLs(List<File> files) {
    List<URL> urls = new ArrayList<URL>(files.size());
    for (File file : files) {
      urls.add(toURL(file));
    }
    return urls;
  }

  static URL toURL(File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException("Unable get a URL from " + file, e);
    }
  }

  static List<File> getLibs(File sdkRoot, String libSubDir) {
    return getLibs(sdkRoot, libSubDir, false);
  }

  static List<File> getLibsRecursive(File sdkRoot, String libSubDir) {
    return getLibs(sdkRoot, libSubDir, true);
  }

  private static List<File> getLibs(File sdkRoot, String libSubDir, boolean recursive) {
    File subDir = new File(sdkRoot, "lib" + File.separator + libSubDir);

    if (!subDir.exists()) {
      throw new IllegalArgumentException("Unable to find " + subDir.getAbsolutePath());
    }

    List<File> libs = new ArrayList<File>();
    getLibs(subDir, libs, recursive);
    return libs;
  }

  private static void getLibs(File dir, List<File> list, boolean recursive) {
    for (File f : listFiles(dir)) {
      if (f.isDirectory() && recursive) {
        getLibs(f, list, recursive);
      } else {
        if (f.getName().endsWith(".jar")) {
          list.add(f);
        }
      }
    }
  }

  private static File findSdkRoot() {
    String explicitRootString = System.getProperty(SDK_ROOT_PROPERTY);
    if (explicitRootString != null) {
      return new File(explicitRootString);
    }

    URL codeLocation = SdkInfo.class.getProtectionDomain().getCodeSource().getLocation();
    String msg =
        "Unable to discover the Google App Engine SDK root. This code should be loaded "
            + "from the SDK directory, but was instead loaded from "
            + codeLocation
            + ".  Specify "
            + "-Dappengine.sdk.root to override the SDK location.";
    File libDir;
    try {
      libDir = new File(codeLocation.toURI());
    } catch (URISyntaxException e) {
      libDir = new File(codeLocation.getFile());
    }
    while (!libDir.getName().equals("lib")) {
      libDir = libDir.getParentFile();
      if (libDir == null) {
        throw new RuntimeException(msg);
      }
    }
    return libDir.getParentFile();
  }

  /**
   * Returns the full paths of all shared libraries for the SDK. Users should compile against these
   * libraries, but <b>not</b> bundle them with their web application. These libraries are already
   * included as part of the App Engine runtime.
   */
  public static List<URL> getSharedLibs() {
    init();
    return Collections.unmodifiableList(toURLs(getSharedLibFiles()));
  }

  /** Returns the paths of all shared libraries for the SDK. */
  public static List<File> getSharedLibFiles() {
    init();
    return determineSharedLibFiles();
  }

  /** @deprecated Use {@link #getOptionalUserLibs()} instead. */
  @Deprecated
  public static List<URL> getUserLibs() {
    init();
    return userLibs;
  }

  /** @deprecated Use {@link #getOptionalUserLibs()} instead. */
  @Deprecated
  public static List<File> getUserLibFiles() {
    init();
    return userLibFiles;
  }

  /**
   * Returns all optional user libraries for the SDK. Users who opt to use these libraries should
   * both compile against and deploy them in the WEB-INF/lib folder of their web applications.
   */
  public static Collection<OptionalLib> getOptionalUserLibs() {
    init();
    return optionalUserLibsByName.values();
  }

  public static OptionalLib getOptionalUserLib(String name) {
    init();
    return optionalUserLibsByName.get(name);
  }

  /** Returns all optional tools libraries for the SDK. */
  public static Collection<OptionalLib> getOptionalToolsLibs() {
    init();
    return optionalToolsLibsByName.values();
  }

  public static OptionalLib getOptionalToolsLib(String name) {
    init();
    return optionalToolsLibsByName.get(name);
  }

  /** Returns the path to the root of the SDK. */
  public static File getSdkRoot() {
    init();
    return sdkRoot;
  }

  /**
   * Explicitly specifies the path to the root of the SDK. This takes precedence over the {@code
   * appengine.sdk.root} system property, but must be called before any other methods in this class.
   *
   * @throws IllegalStateException If any other methods have already been called.
   */
  public static synchronized void setSdkRoot(File root) {
    if (isInitialized && !sdkRoot.equals(root)) {
      throw new IllegalStateException("Cannot set SDK root after initialization has occurred.");
    }
    sdkRoot = root;
  }

  public static Version getLocalVersion() {
    return new LocalVersionFactory(getUserLibFiles()).getVersion();
  }

  public static String getDefaultServer() {
    return DEFAULT_SERVER;
  }

  /**
   * If {@code true}, the testing jar will be added to the shared libs. This is intended for use by
   * frameworks that want to run tests inside the isolated classloader.
   *
   * @param val Whether or the testing jar should be included on the shared path.
   */
  public static void includeTestingJarOnSharedPath(boolean val) {
    // This isn't pretty, but unless we want to move away from accessing all the
    // SDK info via static methods (which would break out tools customers),
    // this is the simplest way to adjust the jars that are considered shared
    // when we're running the dev appserver as part of a test.
    isDevAppServerTest = val;
  }

  private static synchronized void init() {
    if (!isInitialized) {
      if (sdkRoot == null) {
        sdkRoot = findSdkRoot();
      }
      if (new File(sdkRoot, "lib" + File.separator + "user").isDirectory()) {
        userLibFiles = Collections.unmodifiableList(getLibsRecursive(sdkRoot, "user"));
      } else {
        userLibFiles = Collections.emptyList();
      }
      userLibs = Collections.unmodifiableList(toURLs(userLibFiles));
      optionalUserLibsByName = Collections.unmodifiableSortedMap(determineOptionalUserLibs());
      optionalToolsLibsByName = Collections.unmodifiableSortedMap(determineOptionalToolsLibs());
      isInitialized = true;
    }
  }

  /**
   * Optional user libs reside under <sdk_root>/lib/opt/user. Each top-level directory under this
   * path identifies an optional user library, and each sub-directory for a specific library
   * represents a version of that library. So for example we could have:
   * lib/opt/user/mylib1/v1/mylib.jar lib/opt/user/mylib1/v2/mylib.jar
   * lib/opt/user/mylib2/v1/mylib.jar lib/opt/user/mylib2/v2/mylib.jar
   *
   * @return A {@link SortedMap} from the name of the library to an {@link OptionalLib} that
   *     describes the library. The map is sorted by library name.
   */
  private static SortedMap<String, OptionalLib> determineOptionalUserLibs() {
    return determineOptionalLibs(new File(sdkRoot, "lib/opt/user"));
  }

  /**
   * Optional tools libs reside under <sdk_root>/lib/opt/tools. Each top-level directory under this
   * path identifies an optional tools library, and each sub-directory for a specific library
   * represents a version of that library. So for example we could have:
   * lib/opt/tools/mylib1/v1/mylib.jar lib/opt/tools/mylib1/v2/mylib.jar
   * lib/opt/tools/mylib2/v1/mylib.jar lib/opt/tools/mylib2/v2/mylib.jar
   *
   * @return A {@link SortedMap} from the name of the library to an {@link OptionalLib} that
   *     describes the library. The map is sorted by library name.
   */
  private static SortedMap<String, OptionalLib> determineOptionalToolsLibs() {
    return determineOptionalLibs(new File(sdkRoot, "lib/opt/tools"));
  }

  private static SortedMap<String, OptionalLib> determineOptionalLibs(File root) {
    SortedMap<String, OptionalLib> map = new TreeMap<String, OptionalLib>();
    for (File libDir : listFiles(root)) {
      SortedMap<String, List<File>> filesByVersion = new TreeMap<String, List<File>>();
      for (File version : listFiles(libDir)) {
        List<File> filesForVersion = new ArrayList<File>();
        getLibs(version, filesForVersion, true);
        filesByVersion.put(version.getName(), filesForVersion);
      }
      // TODO: Read a description out of a README file.
      String description = "";
      OptionalLib userLib = new OptionalLib(libDir.getName(), description, filesByVersion);
      map.put(userLib.getName(), userLib);
    }
    return map;
  }

  private static List<File> getJettyJars(String subDir) {
    File path = new File(sdkRoot, JETTY_HOME_LIB_PATH + File.separator + subDir);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    List<File> jars = new ArrayList<>();
    for (File f : SdkInfo.listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (!(f.getName().contains("-cdi-") ||  f.getName().contains("ee9") || f.getName().contains("ee10"))) {
          jars.add(f);
        }
      }
    }
    return jars;
  }

  static List<File> getJettyJspJars() {
    List<File> lf = getJettyJars("ee8-apache-jsp");
    lf.addAll(getJettyJars("ee8-glassfish-jstl"));
    return lf;
  }

  static List<File> getImplJars() {
    List<File> lf = getJettyJars("");
    lf.addAll(getJettyJspJars());
    // We also want the devserver to be able to handle annotated servlet, via ASM:
    lf.addAll(getJettyJars("logging"));
    lf.addAll(getJettyJars("ee8-annotations"));
    lf.addAll(getLibs(sdkRoot, "impl"));
    return Collections.unmodifiableList(lf);
  }

  static List<File> getJettySharedLibFiles() {
    List<File> sharedLibs;
    sharedLibs = new ArrayList<>();
    sharedLibs.add(new File(sdkRoot, "lib/shared/appengine-local-runtime-shared.jar"));
    File jettyHomeLib = new File(sdkRoot, JETTY_HOME_LIB_PATH);

    sharedLibs.add(new File(jettyHomeLib, "jetty-servlet-api-4.0.6.jar"));
    File schemas = new File(jettyHomeLib, "servlet-schemas-3.1.jar");
    if (schemas.exists()) {
      sharedLibs.add(schemas);
    } else {
      schemas = new File(jettyHomeLib, "jetty-schemas-3.1.jar");
      if (schemas.exists()) {
        sharedLibs.add(schemas);
      }
    }

    // We want to match this file: "jetty-util-9.3.8.v20160314.jar"
    // but without hardcoding the Jetty version which is changing from time to time.
    class JettyVersionFilter implements FileFilter {
      @Override
      public boolean accept(File file) {
        return file.getName().startsWith("jetty-util-");
      }
    }
    File[] files = jettyHomeLib.listFiles(new JettyVersionFilter());
    sharedLibs.addAll(Arrays.asList(files));
    sharedLibs.addAll(getJettyJspJars());
    return sharedLibs;
  }

  private static List<File> determineSharedLibFiles() {
    List<File> sharedLibs = getJettySharedLibFiles();

    if (isDevAppServerTest) {
      // If we're running the dev appserver as part of a test, add the testing
      // jar to the shared classpath.  This will allow things like
      // ApiProxyLocalImpl to be on the application classpath (necessary
      // because the application classpath includes the test, and the test
      // uses LocalServiceTestHelper, which interacts directly with
      // ApiProxyLocalImpl) but to make privileged calls like accessing the
      // service loader.
      sharedLibs.addAll(getLibsRecursive(sdkRoot, "testing"));
    }
    return Collections.unmodifiableList(sharedLibs);
  }

  /**
   * A version of {@link File#listFiles()} that never returns {@code null}. Historically this has
   * been an issue, since listFiles() can return null if the parent directory does not exist or is
   * not readable.
   *
   * @param dir The directory whose files we want to list.
   * @return The contents of the provided directory.
   */
  static File[] listFiles(File dir) {
    // Some customers check the sdk into subversion, which puts hidden
    // directories into each sdk directory. We don't want these directories in
    // our list.
    File[] files = dir.listFiles(NO_HIDDEN_FILES);
    if (files == null) {
      return new File[0];
    }
    return files;
  }
}
