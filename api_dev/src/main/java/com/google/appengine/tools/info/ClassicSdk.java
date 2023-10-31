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
 * Implementation of the SDK abstraction by the existing GAE SDK distribution, which is composed
 * of multiple jar directories for both local execution and deployment of applications.
 */
class ClassicSdk extends AppengineSdk {

  // Relative path from SDK Root for the Jetty 9.4 Home lib directory.
  static final String JETTY9_HOME_LIB_PATH = "jetty94/jetty-home/lib";
  // Web-default.xml files for Jetty9 based devappserver1.
  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY9 =
      "com/google/appengine/tools/development/jetty9/webdefault.xml";

  @Override
  public String getWebDefaultLocation() {
    return WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY9;
  }

  @Override
  public List<File> getUserJspLibFiles() {
    return Collections.unmodifiableList(getJetty9JspJars());
  }

  @Override
  public List<File> getSharedJspLibFiles() {
    return Collections.unmodifiableList(getJetty9JspJars());
  }

  @Override
  public List<URL> getImplLibs() {
    return Collections.unmodifiableList(toURLs(getImplLibFiles()));
  }


  @Override
  public String getQuickStartClasspath() {
    List<String> list = new ArrayList<>();
    File quickstart = new File(getSdkRoot(), "lib/tools/quickstart/quickstartgenerator.jar");
    File jettyDir = new File(getSdkRoot(), JETTY9_HOME_LIB_PATH);
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

  @Override
  public String getJettyContainerService() {
    return "com.google.appengine.tools.development.jetty9.JettyContainerService";
  }

  @Override
  public String getBackendServersClassName() {
    return "com.google.appengine.tools.development.BackendServersEE8";
  }

  @Override
  public String getModulesClassName() {
    return "com.google.appengine.tools.development.ModulesEE8";
  }

  @Override
  public String getDelegatingModulesFilterHelperClassName() {
    return "com.google.appengine.tools.development.DelegatingModulesFilterHelperEE8";
  }

  @Override
  public File getResourcesDirectory() {
    return new File(getSdkRoot(), "docs");
  }

  private List<File> getImplLibFiles() {
    List<File> lf = getJetty9Jars("");
    lf.addAll(getJetty9JspJars());
    // We also want the devserver to be able to handle annotated servlet, via ASM:
    lf.addAll(getJetty9Jars("annotations"));
    lf.addAll(getLibs(sdkRoot, "impl"));
    lf.addAll(getLibs(sdkRoot, "impl/jetty9"));
   return Collections.unmodifiableList(lf);
  }

  /**
   * Returns the full paths of all JSP libraries that need to be treated as shared libraries in the
   * SDK.
   */
  public List<URL> getSharedJspLibs() {
    return Collections.unmodifiableList(toURLs(getSharedJspLibFiles()));
  }

  private List<File> getJetty9Jars(String subDir) {
    File path = new File(sdkRoot, JETTY9_HOME_LIB_PATH + File.separator + subDir);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    List<File> jars = new ArrayList<>();
    for (File f : listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (!(f.getName().startsWith("jetty-cdi") || f.getName().startsWith("cdi"))) {
          jars.add(f);
        }
      }
    }
    return jars;
  }

  List<File> getJetty9JspJars() {
    List<File> lf = getJetty9Jars("apache-jsp");
    lf.addAll(getJetty9Jars("apache-jstl"));
    return lf;
  }

  List<File> getJetty9SharedLibFiles() {
    List<File> sharedLibs;
    sharedLibs = new ArrayList<>();
    sharedLibs.add(new File(sdkRoot, "lib/shared/jetty9/appengine-local-runtime-shared.jar"));
    File jettyHomeLib = new File(sdkRoot, JETTY9_HOME_LIB_PATH);

    sharedLibs.add(new File(jettyHomeLib, "servlet-api-3.1.jar"));
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
    sharedLibs.addAll(getJetty9JspJars());
    return sharedLibs;
  }

  /**
   * Returns the full paths of all shared libraries for the SDK. Users should compile against these
   * libraries, but <b>not</b> bundle them with their web application. These libraries are already
   * included as part of the App Engine runtime.
   */
  @Override
  public List<URL> getSharedLibs() {
    return Collections.unmodifiableList(toURLs(getSharedLibFiles()));
  }

  @Override
  public List<URL> getUserJspLibs() {
    return Collections.unmodifiableList(toURLs(getJetty9JspJars()));
  }

  /** Returns the paths of all shared libraries for the SDK. */
  @Override
  public List<File> getSharedLibFiles() {
    List<File> sharedLibs = getJetty9SharedLibFiles();

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
    return "com.google.appengine.tools.development.jetty9.LocalJspC";
  }
}
