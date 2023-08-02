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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import org.eclipse.jetty.util.Jetty;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * A Maven-based GAE SDK files and resources provider.
 *
 */
public class MavenSdk extends AppengineSdk {

  private final ListMultimap<String, File> profileToFiles;
  private final String resourceFile;
  private boolean includeTestingJarOnSharedPath = false;

  /**
   * This constructor is invoked via reflection when setting the AppengineSdk to use the MavenSdk
   * rather than the default ClassicSdk (see
   * com.google.appengine.tools.admin.staging.DependencyFetcher).
   *
   * @param profileToFiles a multimap that lists artifacts by profile, where each profile
   *     corresponds to a class of dependencies for the SDK.
   */
  public MavenSdk(ListMultimap<String, File> profileToFiles) {
    this.profileToFiles = profileToFiles;
    resourceFile = getSingletonFile("Resources").toString();
  }

  @Override
  public void includeTestingJarOnSharedPath(boolean val) {
    includeTestingJarOnSharedPath = val;
  }

  @Override
  public File getToolsApiJarFile() {
    return getSingletonFile("ToolsApi");
  }

  @Override
  public List<File> getUserJspLibFiles() {
    return profileToFiles.get("UserJsp" + getJettySuffix());
  }

  @Override
  public List<File> getUserLibFiles() {
    return Collections.emptyList();
  }

  @Override
  public String getQuickStartClasspath() {
    List<String> classes =
        Lists.transform(
            profileToFiles.get("QuickStart"),
            new Function<File, String>() {
              @Override
              public String apply(File file) {
                return file.getAbsolutePath();
              }
            });
    return Joiner.on(System.getProperty("path.separator")).join(classes);
  }

  @Override
  public List<File> getSharedJspLibFiles() {
    return profileToFiles.get("SharedJsp" + getJettySuffix());
  }

  @Override
  public List<URL> getImplLibs() {
    return toURLs(profileToFiles.get("Impl" + getJettySuffix()));
  }

  @Override
  public List<File> getSharedLibFiles() {
    List<File> sharedLibFiles = profileToFiles.get("Shared" + getJettySuffix());
    if (includeTestingJarOnSharedPath) {
      sharedLibFiles.addAll(profileToFiles.get("Testing"));
    }
    return sharedLibFiles;
  }

  @Override
  public List<URL> getDatanucleusLibs(String version) {
    if (!version.equals("v2")) {
      // v1 is so old that it is not available as Maven artifact, so we emit an error.
      // v2 is only used by internal apps, and should not be exposed in the Maven based SDK.
      throw new IllegalArgumentException(
          "Only Datanucleus v2 is supported. Invalid version: " + version);
    }
    return toURLs(profileToFiles.get("Datanucleus"));
  }

  @Override
  public String getWebDefaultXml() {
    return new File(resourceFile, "aaaaaaa" + "/webdefault.xml").toString();
  }

  @Override
  public File getResourcesDirectory() {
    return new File(resourceFile, "docs");
  }

  @Override
  public File getAgentJarFile() {
    return getSingletonFile("Agent");
  }

  @Override
  public List<URL> getSharedLibs() {
    return toURLs(getSharedLibFiles());
  }

  @Override
  public List<URL> getWebApiToolsLibs() {
    return toURLs(profileToFiles.get("WebApiTool"));
  }

  @Override
  public File getLoggingProperties() {
    return Paths.get(resourceFile, "config", "sdk", "logging.properties").toFile();
  }

  private static String getJettySuffix() {
    return Jetty.VERSION;
  }

  private File getSingletonFile(String profile) {
    try {
      return profileToFiles.get(profile).get(0);
    } catch (Exception ex) {
      throw new IllegalStateException(
          "The profiles map must include a \""
              + profile
              + "\" key corresponding a singleton file list.");
    }
  }
}
