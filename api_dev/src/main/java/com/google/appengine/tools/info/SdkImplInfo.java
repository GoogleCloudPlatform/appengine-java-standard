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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains implementation information about the SDK.
 *
 * <p>Although this class is public, it is not intended for public consumption.
 *
 */
public class SdkImplInfo {

  private static List<File> getImplLibFiles() {
    return SdkInfo.getImplJars();
  }

  /** @deprecated Call {@code SdkInfo.getOptionalToolsLib("datanucleus")} instead. */
  @Deprecated
  public static List<URL> getOrmToolLibs() {

    List<File> toolOrmFiles = SdkInfo.getLibsRecursive(SdkInfo.getSdkRoot(), "tools/orm");

    return Collections.unmodifiableList(SdkInfo.toURLs(toolOrmFiles));
  }

  /** Returns the full paths of all implementation libraries for the SDK. */
  public static List<URL> getImplLibs() {
    return Collections.unmodifiableList(SdkInfo.toURLs(getImplLibFiles()));
  }

  /**
   * Returns the full paths of all JSP libraries that do not need any special privileges in the SDK.
   */
  public static List<URL> getUserJspLibs() {
      return Collections.unmodifiableList(SdkInfo.toURLs(getjsp22LibFiles()));
  }

  private static List<File> getjsp22LibFiles() {
    return Collections.unmodifiableList(SdkInfo.getJetty9JspJars());
  }

  /** Returns the paths of all JSP libraries that do not need any special privileges in the SDK. */
  public static List<File> getUserJspLibFiles() {
      return getjsp22LibFiles();

  }
  /**
   * Returns the full paths of all JSP libraries that need to be treated as shared libraries in the
   * SDK.
   */
  public static List<URL> getSharedJspLibs() {
    return Collections.unmodifiableList(SdkInfo.toURLs(getSharedJspLibFiles()));
  }

  /**
   * Returns the paths of all JSP libraries that need to be treated as shared libraries in the SDK.
   */
  public static List<File> getSharedJspLibFiles() {
      return getjsp22LibFiles();
  }

  public static List<URL> getWebApiToolLibs() {
    return Collections.unmodifiableList(
        SdkInfo.toURLs(
            Collections.unmodifiableList(
                SdkInfo.getLibsRecursive(
                    SdkInfo.getSdkRoot(), "opt/tools/appengine-local-endpoints/v1"))));
  }

  /** Returns the File containing the SDK logging properties. */
  public static File getLoggingProperties() {
    return new File(
        SdkInfo.getSdkRoot()
            + File.separator
            + "config"
            + File.separator
            + "sdk"
            + File.separator
            + "logging.properties");
  }

  /** Returns the URL to the tools API jar. */
  public static URL getToolsApiJar() {
    File f =
        new File(
            SdkInfo.getSdkRoot()
                + File.separator
                + "lib"
                + File.separator
                + "appengine-tools-api.jar");
    return SdkInfo.toURL(f);
  }

  /** Returns all jar files under the lib directory. */
  public static List<File> getAllLibFiles() {
    List<File> libs = new ArrayList<>(SdkInfo.getLibsRecursive(SdkInfo.getSdkRoot(), ""));
    libs.add(new File(SdkInfo.getSdkRoot(), "jetty94/jetty-home/lib/servlet-api-3.1.jar"));
    return Collections.unmodifiableList(libs);
  }
}
