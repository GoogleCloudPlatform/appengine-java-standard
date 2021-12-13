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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GAE SDK files and resources provider.
 * It defines a set of abstract methods that are implemented by the GAE classic SDK, but also
 * an optional Maven repository based SDK, which can be used in the Cloud SDK to mimimize jar
 * artifact downloads thanks to a Maven local cache.
 */
public abstract class AppengineSdk {

  /**
   * For the quickstart annotation processing, we need different weddefault.xml files used by Jetty
   * to specify system default servlets and filters. For now, we need 3 of them: - the vm:true
   * Managed VM Java image (based on Jetty92), - the vm:true/flex Managed VM Java image developped
   * on github (based on Jetty93), - the App Engine standard Java8(g) runtime (based on Jetty93)
   */
  public enum WebDefaultXmlType {
    JETTY93_STANDARD {
      @Override
      public String toString() {
        return "9.3.standard";
      }
    }
  }

  /**
   * Default deployment admin server name.
   */
  public static final String DEFAULT_SERVER = "appengine.google.com";

  /**
   * If {@code true}, the testing jar will be added to the shared libs.  This
   * is intended for use by frameworks that want to run tests inside the
   * isolated classloader.
   *
   * @param val Whether or the testing jar should be included on the shared
   * path.
   */
  public abstract void includeTestingJarOnSharedPath(boolean val);

  private static AppengineSdk currentSdk;

  /**
   * Returns an SDK implementation to use for access jar files and resources.
   */
  public static AppengineSdk getSdk() {
    return currentSdk = firstNonNull(currentSdk, new ClassicSdk());
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
   * Returns the jar file that contains the appcfg command and its dependencies.
   */
  public abstract File getToolsApiJarFile();

  /**
   * Returns the jar file that contains the dev server agent.
   */
  public abstract File getAgentJarFile();

  /**
   * Returns the list of URLs of all shared libraries for the SDK. Users
   * should compile against these libraries, but <b>not</b> bundle them
   * with their web application. These libraries are already included
   * as part of the App Engine runtime.
   */
  public abstract List<URL> getSharedLibs();

  /**
   * Returns the list of URLs of the Datanucleus jar libraries for the SDK.
   *
   * @param version Datanucleus version. Maybe be v1 or v2 but not all implementations need to
   *     support v1 and it is old and not available in Maven central.
   */
  public abstract List<URL> getDatanucleusLibs(String version);

  /**
   * Returns the list of URLs of all JSP jar libraries that do not need any
   * special privileges in the SDK.
   */
  public List<URL> getUserJspLibs() {
    return Collections.unmodifiableList(toURLs(getUserJspLibFiles()));
  }

  /**
   * Returns the list of URLs of all implementation jar libraries for the SDK.
   */
  public abstract List<URL> getImplLibs();

  /**
   * Returns the list of all JSP library jar files that do not need any
   * special privileges in the SDK.
   */
  public abstract List<File> getUserJspLibFiles();

  /**
   * Returns the list of all JSP library jar files that need to be treated as shared libraries
   * in the SDK.
   */
  public abstract List<File> getSharedJspLibFiles();

  /**
   * Returns the list of all shared library jar files for the SDK.
   */
  public abstract List<File> getSharedLibFiles();

  /**
   * Returns the list of all user library jar files for the SDK.
   */
  public abstract List<File> getUserLibFiles();

  /** Returns the list of web api jar URLs for the SDK. */
  public abstract List<URL> getWebApiToolsLibs();

  /**
   * Returns the classpath of the quickstart process.
   */
  public abstract String getQuickStartClasspath(WebDefaultXmlType jettyVersion);

  /**
   * Returns the webdefault.xml for the corresponding Jetty version.
   * Valid versions for now are 9.2 and 9.3 (Standard or Flex).
   */
  public abstract String getWebDefaultXml(WebDefaultXmlType jettyVersion);

  /**
   * Returns the path to SDK resource files like xml or schemas files.
   */
  public abstract File getResourcesDirectory();

  /**
   * Returns the SDK version (for example, 1.9.38).
   */
  public Version getLocalVersion() {
    return new LocalVersionFactory(getUserLibFiles()).getVersion();
  }

  /**
   * Returns the File containing the SDK logging properties.
   */
  public abstract File getLoggingProperties();

  /**
   * Returns the default admin server to talk to for deployments.
   *
   */
  public String getDefaultServer() {
    return DEFAULT_SERVER;
  }

  /**
   * Throws IllegalArgumentException if the incorrect Datanucleus version is specified.
   */
  public void validateDatanucleusVersions(String version) {
    if (!version.equals("v1") && !version.equals("v2")) {
      throw new IllegalArgumentException("Invalid Datanucleus version: " + version);
    }
  }

  List<URL> toURLs(List<File> files) {
    List<URL> urls = new ArrayList<>(files.size());
    for (File file : files) {
      urls.add(toURL(file));
    }
    return urls;
  }

  private URL toURL(File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException("Unable get a URL from " + file, e);
    }
  }
}
