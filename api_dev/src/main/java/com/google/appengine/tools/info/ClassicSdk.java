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

import com.google.common.base.Joiner;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the SDK abstraction by the existing GAE SDK distribution, which is composed
 * of multiple jar directories for both local execution and deployment of applications.
 */
class ClassicSdk extends AppengineSdk {

  @Override
  public void includeTestingJarOnSharedPath(boolean val) {
    SdkInfo.includeTestingJarOnSharedPath(val);
  }

  @Override
  public File getToolsApiJarFile() {
    return new File(getSdkRoot() + "/lib/appengine-tools-api.jar");
  }

  @Override
  public List<File> getUserJspLibFiles() {
    return SdkImplInfo.getUserJspLibFiles();
  }

  @Override
  public List<File> getUserLibFiles() {
    return SdkInfo.getUserLibFiles();
  }

  @Override
  public List<URL> getWebApiToolsLibs() {
    return SdkImplInfo.getWebApiToolLibs();
  }

  @Override
  public List<File> getSharedJspLibFiles() {
    return SdkImplInfo.getSharedJspLibFiles();
  }

  @Override
  public List<URL> getImplLibs() {
    return SdkImplInfo.getImplLibs();
  }

  @Override
  public List<File> getSharedLibFiles() {
    return SdkInfo.getSharedLibFiles();
  }

  @Override
  public List<URL> getDatanucleusLibs(String version) {
    validateDatanucleusVersions(version);
    return SdkInfo.getOptionalToolsLib("datanucleus").getURLsForVersion(version);
  }

  @Override
  public String getQuickStartClasspath() {
    List<String> list = new ArrayList<>();
    File quickstart = new File(getSdkRoot(), "lib/tools/quickstart/quickstartgenerator.jar");
    File jettyDir = new File(getSdkRoot(), SdkInfo.JETTY9_HOME_LIB_PATH);
    for (File f : jettyDir.listFiles()) {
      if (!f.isDirectory()
          && !(f.getName().startsWith("cdi-") || f.getName().startsWith("jetty-cdi-"))) {
        list.add(f.getAbsolutePath());
      }
    }
    // Add the API jar, in case it is needed (b/120480580).
    list.add(getSdkRoot() + "/lib/impl/appengine-api.jar");

    // Note: Do not put the Apache JSP files in the classpath. If needed, they should be part of
    // the application itself under WEB-INF/lib.
    for (String subdir : new String[] {"annotations", "jaspi"}) {
      for (File f : new File(jettyDir, subdir).listFiles()) {
        list.add(f.getAbsolutePath());
      }
    }
    list.add(quickstart.getAbsolutePath());

    return Joiner.on(System.getProperty("path.separator")).join(list);
  }

  @Override
  public String getWebDefaultXml() {
        return getSdkRoot() + "/docs/webdefault.xml";
  }

  public String getSdkRoot() {
    return SdkInfo.getSdkRoot().getAbsolutePath();
  }

  @Override
  public File getResourcesDirectory() {
    return new File(getSdkRoot(), "docs");
  }

  @Override
  public File getAgentJarFile() {
    return new File(getSdkRoot() + "/lib/agent/appengine-agent.jar");
  }

  @Override
  public List<URL> getSharedLibs() {
    return SdkInfo.getSharedLibs();
  }

  @Override
  public File getLoggingProperties() {
    return SdkImplInfo.getLoggingProperties();
  }
}
