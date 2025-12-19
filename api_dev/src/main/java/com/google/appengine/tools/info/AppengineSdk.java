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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * GAE SDK files and resources provider. It defines a set of abstract methods that are implemented
 * by the GAE classic SDK, but also an optional Maven repository based SDK, which can be used in the
 * Cloud SDK to mimimize jar artifact downloads thanks to a Maven local cache.
 */
public abstract class AppengineSdk {

  /** Default deployment admin server name. */
  public static final String DEFAULT_SERVER = "appengine.google.com";

  private static AppengineSdk currentSdk;
  public static final String SDK_ROOT_PROPERTY = "appengine.sdk.root";

  static File sdkRoot = null;

  static boolean isDevAppServerTest;

  private static final FileFilter NO_HIDDEN_FILES =
      new FileFilter() {
        @Override
        public boolean accept(File file) {
          return !file.isHidden();
        }
      };

  AppengineSdk() {
    sdkRoot = findSdkRoot();
  }

  List<File> getLibs(File sdkRoot, String libSubDir) {
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

    List<File> libs = new ArrayList<>();
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

    URL codeLocation = AppengineSdk.class.getProtectionDomain().getCodeSource().getLocation();
    File libDir;
    try {
      libDir = new File(codeLocation.toURI());
    } catch (URISyntaxException e) {
      libDir = new File(codeLocation.getFile());
    }
    while (!libDir.getName().equals("lib")) {
      libDir = libDir.getParentFile();
      if (libDir == null) {
        ///  throw new RuntimeException(msg);
      }
    }
    return libDir.getParentFile();
  }

  /**
   * Returns the full paths of all shared libraries for the SDK. Users should compile against these
   * libraries, but <b>not</b> bundle them with their web application. These libraries are already
   * included as part of the App Engine runtime.
   */
  public abstract List<URL> getSharedLibs();

  /**
   * @deprecated Use {@link #getOptionalUserLibs()} instead.
   */
  @Deprecated
  public List<URL> getUserLibs() {
    return Collections.unmodifiableList(toURLs(getUserLibFiles()));
  }

  /**
   * @deprecated Use {@link #getOptionalUserLibs()} instead.
   */
  @Deprecated
  public List<File> getUserLibFiles() {
    List<File> userLibFiles;
    if (new File(sdkRoot, "lib" + File.separator + "user").isDirectory()) {
      userLibFiles = Collections.unmodifiableList(getLibsRecursive(sdkRoot, "user"));
    } else {
      userLibFiles = Collections.emptyList();
    }
    return userLibFiles;
  }

  /** Returns the path to the root of the SDK. */
  public static File getSdkRoot() {
    return sdkRoot;
  }

  /** Reset current SDK to null, to trigger re init */
  public static void resetSdk() {
    currentSdk = null;
  }

  /**
   * Explicitly specifies the path to the root of the SDK. This takes precedence over the {@code
   * appengine.sdk.root} system property, but must be called before any other methods in this class.
   *
   * @throws IllegalStateException If any other methods have already been called.
   */
  public synchronized void setSdkRoot(File root) {
    if (!sdkRoot.equals(root)) {
      throw new IllegalStateException("Cannot set SDK root after initialization has occurred.");
    }
    sdkRoot = root;
    currentSdk = null;
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
  private SortedMap<String, OptionalLib> determineOptionalToolsLibs() {
    return determineOptionalLibs(new File(sdkRoot, "lib/opt/tools"));
  }

  private SortedMap<String, OptionalLib> determineOptionalLibs(File root) {
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

  /** Returns the File containing the SDK logging properties. */
  public File getLoggingProperties() {
    return new File(
        getSdkRoot()
            + File.separator
            + "config"
            + File.separator
            + "sdk"
            + File.separator
            + "logging.properties");
  }

  public File getToolsApiJarFile() {
    return new File(getSdkRoot() + "/lib/appengine-tools-api.jar");
  }

  /** Returns the URL to the tools API jar. */
  public URL getToolsApiJar() {
    File f =
        new File(
            getSdkRoot() + File.separator + "lib" + File.separator + "appengine-tools-api.jar");
    return toURL(f);
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

  /** Returns an SDK implementation to use for access jar files and resources. */
  public static AppengineSdk getSdk() {
    if (currentSdk != null) {
      return currentSdk;
    }
    currentSdk = SdkFactory.getSdk();
    return currentSdk;
  }

  /**
   * Modifies the SDK implementation (Classic or Maven-based). This method is invoked via reflection
   * when setting the AppengineSdk to use the MavenSdk rather than the default ClassicSdk (see
   * com.google.appengine.tools.admin.staging.DependencyFetcher).
   */
  public static void setSdk(AppengineSdk sdk) {
    currentSdk = checkNotNull(sdk);
  }

  /**
   * Returns the list of URLs of the Datanucleus jar libraries for the SDK.
   *
   * @param version Datanucleus version. Maybe be v1 or v2 but not all implementations need to
   *     support v1 and it is old and not available in Maven central.
   */
  public List<URL> getDatanucleusLibs(String version) {
    validateDatanucleusVersions(version);
    return determineOptionalToolsLibs().get("datanucleus").getURLsForVersion(version);
  }

  /**
   * Returns the list of URLs of all JSP jar libraries that do not need any special privileges in
   * the SDK.
   */
  public abstract List<URL> getUserJspLibs();

  /** Returns the list of URLs of all implementation jar libraries for the SDK. */
  public abstract List<URL> getImplLibs();

  /** Returns the classpath of the quickstart process. */
  public abstract String getQuickStartClasspath();

  /** Returns the webdefault.xml for the corresponding Jetty version. */
  public abstract String getWebDefaultXml();

  /** Returns the devappserver BackendServers FQN class for the corresponding Jetty version. */
  public abstract String getBackendServersClassName();

  /** Returns the devappserver Modules FQN class for the corresponding Jetty version. */
  public abstract String getModulesClassName();

  /** Returns the JettyContainerService FQN class for the corresponding Jetty version. */
  public abstract String getJettyContainerService();

  /** Returns the DelegatingModulesFilterHelper FQN class for the corresponding Jetty version. */
  public abstract String getDelegatingModulesFilterHelperClassName();

  /** Returns the path to SDK resource files like xml or schemas files. */
  public abstract File getResourcesDirectory();

  /** Returns the path in a jar file of the jetty server webdefault.xml file. */
  public abstract String getWebDefaultLocation();

  /** Returns the JSP compiler class name. */
  public abstract String getJSPCompilerClassName();

  /** Returns the default admin server to talk to for deployments. */
  public String getDefaultServer() {
    return DEFAULT_SERVER;
  }

  /** Throws IllegalArgumentException if the incorrect Datanucleus version is specified. */
  public void validateDatanucleusVersions(String version) {
    if (!version.equals("v1") && !version.equals("v2")) {
      throw new IllegalArgumentException("Invalid Datanucleus version: " + version);
    }
  }

  static List<URL> toURLs(List<File> files) {
    List<URL> urls = new ArrayList<>(files.size());
    for (File file : files) {
      urls.add(toURL(file));
    }
    return urls;
  }

  private static URL toURL(File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException("Unable get a URL from " + file, e);
    }
  }

  public abstract Iterable<File> getUserJspLibFiles();

  public abstract Iterable<File> getSharedJspLibFiles();

  public abstract Iterable<File> getSharedLibFiles();
}
