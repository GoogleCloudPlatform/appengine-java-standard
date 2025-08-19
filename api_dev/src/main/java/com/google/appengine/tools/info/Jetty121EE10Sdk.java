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
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the SDK abstraction by the existing GAE SDK distribution, which is composed of
 * multiple jar directories for both local execution and deployment of applications.
 */
class Jetty121EE10Sdk extends Jetty121EE8Sdk {

  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12EE10 =
      "com/google/appengine/tools/development/jetty/ee10/webdefault.xml";

  @Override
  public List<File> getUserJspLibFiles() {
    return Collections.unmodifiableList(getJetty121JspJars());
  }

  @Override
  public String getWebDefaultLocation() {
    return WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12EE10;
  }

  @Override
  public String getJettyContainerService() {
    return "com.google.appengine.tools.development.jetty.ee10.JettyContainerService";
  }

  @Override
  public String getBackendServersClassName() {
    return "com.google.appengine.tools.development.ee10.BackendServersEE10";
  }

  @Override
  public String getModulesClassName() {
    return "com.google.appengine.tools.development.ee10.ModulesEE10";
  }

  @Override
  public String getDelegatingModulesFilterHelperClassName() {
    return "com.google.appengine.tools.development.ee10.DelegatingModulesFilterHelperEE10";
  }

  @Override
  public String getWebDefaultXml() {
    return getSdkRoot() + "/docs/jetty12EE10/webdefault.xml";
  }

  @Override
  public List<File> getSharedJspLibFiles() {
    return Collections.unmodifiableList(getJetty121JspJars());
  }

  @Override
  public List<URL> getImplLibs() {
    return Collections.unmodifiableList(toURLs(getImplLibFiles()));
  }

  @Override
  public String getQuickStartClasspath() {
    List<String> list = new ArrayList<>();
    File quickstart =
        new File(getSdkRoot(), "lib/tools/quickstart/quickstartgenerator-jetty121-ee10.jar");

    File jettyDir = new File(getSdkRoot(), JETTY121_HOME_LIB_PATH);
    for (File f : jettyDir.listFiles()) {
      if (!f.isDirectory()
          && !(f.getName().contains("cdi-")
              || f.getName().contains("ee9")
              || f.getName().contains("ee11")
              || f.getName().contains("-demo-")
              || f.getName().contains("websocket")
              || f.getName().contains("ee8"))) {
        list.add(f.getAbsolutePath());
      }
    }
    // Add the API jar, in case it is needed (b/120480580).
    list.add(getSdkRoot() + "/lib/impl/appengine-api.jar");

    // Note: Do not put the Apache JSP files in the classpath. If needed, they should be part of
    // the application itself under WEB-INF/lib.
    for (String subdir : new String[] {"ee10-annotations"}) {
      for (File f : new File(jettyDir, subdir).listFiles()) {
        list.add(f.getAbsolutePath());
      }
    }
    for (String subdir : new String[] {"ee10-jaspi"}) {
      for (File f : new File(jettyDir, subdir).listFiles()) {
        list.add(f.getAbsolutePath());
      }
    }

    list.add(quickstart.getAbsolutePath());
    // Add Jars for logging.
    for (File f : new File(jettyDir, "logging").listFiles()) {
      list.add(f.getAbsolutePath());
    }

    return Joiner.on(System.getProperty("path.separator")).join(list);
  }

  @Override
  public File getResourcesDirectory() {
    return new File(getSdkRoot(), "docs");
  }

  private List<File> getImplLibFiles() {
    List<File> lf = getJetty121Jars("");
    lf.addAll(getJetty121JspJars());
    lf.addAll(getJetty121Jars("logging"));
    // We also want the devserver to be able to handle annotated servlet, via ASM:
    lf.addAll(getJetty121Jars("ee10-annotations"));
    lf.addAll(getJetty121Jars("ee10-apache-jsp"));
    lf.addAll(getJetty121Jars("ee10-glassfish-jstl"));

    lf.addAll(getLibs(sdkRoot, "impl"));
    lf.addAll(getLibs(sdkRoot, "impl/jetty121"));
    return Collections.unmodifiableList(lf);
  }

  /**
   * Returns the full paths of all JSP libraries that need to be treated as shared libraries in the
   * SDK.
   */
  public List<URL> getSharedJspLibs() {
    return Collections.unmodifiableList(toURLs(getSharedJspLibFiles()));
  }

  protected File getJetty121Jar(String fileNamePattern) {
    File path = new File(sdkRoot, JETTY121_HOME_LIB_PATH + File.separator);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    for (File f : listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (f.getName().contains(fileNamePattern)) {
          return f;
        }
      }
    }
    throw new IllegalArgumentException(
        "Unable to find " + fileNamePattern + " at " + path.getAbsolutePath());
  }

  protected List<File> getJetty121Jars(String subDir) {
    File path = new File(sdkRoot, JETTY121_HOME_LIB_PATH + File.separator + subDir);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    List<File> jars = new ArrayList<>();
    for (File f : listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (!(f.getName().contains("-cdi-")
            || f.getName().contains("jetty-servlet-api-") // no javax. if needed should be in shared
            || f.getName().contains("ee9") // we want ee10 only. jakarta apis should be in shared
            || f.getName().contains("jetty-jakarta-servlet-api") // old
        )) {
          jars.add(f);
        }
      }
    }
    return jars;
  }

  List<File> getJetty121JspJars() {

    List<File> lf = getJetty121Jars("ee10-apache-jsp");
    lf.addAll(getJetty121Jars("ee10-glassfish-jstl"));
    lf.add(getJetty121Jar("ee10-servlet-"));
    return lf;
  }

  List<File> getJetty121SharedLibFiles() {
    List<File> sharedLibs;
    sharedLibs = new ArrayList<>();
    sharedLibs.add(new File(sdkRoot, "lib/shared/jetty12/appengine-local-runtime-shared.jar"));// keep 12 not 121
    File jettyHomeLib = new File(sdkRoot, JETTY121_HOME_LIB_PATH);

    sharedLibs.add(new File(jettyHomeLib, "jetty-servlet-api-4.0.6.jar")); // this is javax.servlet
    sharedLibs.add(new File(jettyHomeLib, "jakarta.servlet-api-6.0.0.jar")); // contains schemas.
    /////////

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
    sharedLibs.addAll(getJetty121JspJars());
    return sharedLibs;
  }

  @Override
  public List<URL> getSharedLibs() {
    return Collections.unmodifiableList(toURLs(getSharedLibFiles()));
  }

  @Override
  public List<URL> getUserJspLibs() {
    return Collections.unmodifiableList(toURLs(getJetty121JspJars()));
  }

  /** Returns the paths of all shared libraries for the SDK. */
  @Override
  public List<File> getSharedLibFiles() {
    List<File> sharedLibs = getJetty121SharedLibFiles();

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

  @Override
  public String getJSPCompilerClassName() {
    return "com.google.appengine.tools.development.jetty.ee10.LocalJspC";
  }
}
