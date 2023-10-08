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
class Jetty12Sdk extends AppengineSdk {

  // Relative path from SDK Root for the Jetty 12 Home lib directory.
  static final String JETTY12_HOME_LIB_PATH = "jetty12/jetty-home/lib";

  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12 =
      "com/google/appengine/tools/development/jetty/webdefault.xml"; 

  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12EE10 =
      "com/google/appengine/tools/development/jetty/ee10/webdefault.xml"; 
  
  @Override
  public List<File> getUserJspLibFiles() {
    return Collections.unmodifiableList(getJetty12JspJars());
  }

  @Override
  public String getWebDefaultLocation() {
    if (Boolean.getBoolean("appengine.use.EE10")) {
    return WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12EE10;
    }
    else {
     return WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12;  
    }
  }
 
  @Override
  public String getJettyContainerService() {
    if (Boolean.getBoolean("appengine.use.EE10")) {
    return "com.google.appengine.tools.development.jetty.ee10.JettyContainerService";
    }
    else {
     return "com.google.appengine.tools.development.jetty.JettyContainerService";  
    }
  }

   @Override
   public String getBackendServersClassName() {
     if (Boolean.getBoolean("appengine.use.EE10")) {
    return "com.google.appengine.tools.development.ee10.BackendServersEE10";
    }
    else {
     return "com.google.appengine.tools.development.BackendServersEE8";  
    }
   }

   @Override
   public String getModulesClassName() {
     if (Boolean.getBoolean("appengine.use.EE10")) {
    return "com.google.appengine.tools.development.ee10.ModulesEE10";
    }
    else {
     return "com.google.appengine.tools.development.ModulesEE8";  
    }
   }
   
  @Override
  public String getWebDefaultXml() {
    if (Boolean.getBoolean("appengine.use.EE10")) {
    return getSdkRoot() + "/docs/jetty12/webdefault.xml";
    } else {
     return getSdkRoot() + "/docs/jetty12EE10/webdefault.xml";  
    }
  }
  
  @Override
  public List<File> getSharedJspLibFiles() {
    return Collections.unmodifiableList(getJetty12JspJars());
  }

  @Override
  public List<URL> getImplLibs() {
    return Collections.unmodifiableList(toURLs(getImplLibFiles()));
  }

  @Override
  public String getQuickStartClasspath() {
    List<String> list = new ArrayList<>();
    File quickstart =
        new File(getSdkRoot(), "lib/tools/quickstart/quickstartgenerator-jetty12.jar");
    File jettyDir = new File(getSdkRoot(), JETTY12_HOME_LIB_PATH);
    for (File f : jettyDir.listFiles()) {
      if (!f.isDirectory()
          && !(f.getName().contains("cdi-")
              || f.getName().contains("ee9"))) {
        list.add(f.getAbsolutePath());
      }
    }
    // Add the API jar, in case it is needed (b/120480580).
    list.add(getSdkRoot() + "/lib/impl/appengine-api.jar");

    // Note: Do not put the Apache JSP files in the classpath. If needed, they should be part of
    // the application itself under WEB-INF/lib.
    if (Boolean.getBoolean("appengine.use.EE10")) {
          for (String subdir : new String[]{"ee10-annotations"}) {
              for (File f : new File(jettyDir, subdir).listFiles()) {
                  list.add(f.getAbsolutePath());
              }
          }
          for (String subdir : new String[]{"ee10-jaspi"}) {
              for (File f : new File(jettyDir, subdir).listFiles()) {
                  list.add(f.getAbsolutePath());
              }
          }
      } else {
          for (String subdir : new String[]{"ee8-annotations"}) { // TODO: "ee8-jaspi" for Jetty12
              for (File f : new File(jettyDir, subdir).listFiles()) {
                  list.add(f.getAbsolutePath());
              }
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
    List<File> lf = getJetty12Jars("");
    lf.addAll(getJetty12JspJars());
    // We also want the devserver to be able to handle annotated servlet, via ASM:
    lf.addAll(getJetty12Jars("logging"));
      if (Boolean.getBoolean("appengine.use.EE10")) {
          lf.addAll(getJetty12Jars("ee10-annotations"));
      } else {
          lf.addAll(getJetty12Jars("ee8-annotations"));
      }
    lf.addAll(getLibs(sdkRoot, "impl"));
    lf.addAll(getLibs(sdkRoot, "impl/jetty12"));
    return Collections.unmodifiableList(lf);
  }

  /**
   * Returns the full paths of all JSP libraries that need to be treated as shared libraries in the
   * SDK.
   */
  public List<URL> getSharedJspLibs() {
    return Collections.unmodifiableList(toURLs(getSharedJspLibFiles()));
  }

  private List<File> getJetty12Jars(String subDir) {
    File path = new File(sdkRoot, JETTY12_HOME_LIB_PATH + File.separator + subDir);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    List<File> jars = new ArrayList<>();
    for (File f : listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (!(f.getName().contains("-cdi-")
            || f.getName().contains("ee9")
            || f.getName().contains("ee10"))) {
          jars.add(f);
        }
      }
    }
    return jars;
  }

  List<File> getJetty12JspJars() {
      
      if (Boolean.getBoolean("appengine.use.EE10")) {
          List<File> lf = getJetty12Jars("ee10-apache-jsp");
          lf.addAll(getJetty12Jars("ee10-glassfish-jstl"));
          return lf;
      }
      List<File> lf = getJetty12Jars("ee8-apache-jsp");
      lf.addAll(getJetty12Jars("ee8-glassfish-jstl"));
      return lf;
}

  List<File> getJetty12SharedLibFiles() {
    List<File> sharedLibs;
    sharedLibs = new ArrayList<>();
    sharedLibs.add(new File(sdkRoot, "lib/shared/jetty12/appengine-local-runtime-shared.jar"));
    File jettyHomeLib = new File(sdkRoot, JETTY12_HOME_LIB_PATH);

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
    sharedLibs.addAll(getJetty12JspJars());
    return sharedLibs;
  }

  @Override
  public List<URL> getSharedLibs() {
    return Collections.unmodifiableList(toURLs(getSharedLibFiles()));
  }

  @Override
  public List<URL> getUserJspLibs() {
    return Collections.unmodifiableList(toURLs(getJetty12JspJars()));
  }

  /** Returns the paths of all shared libraries for the SDK. */
  @Override
  public List<File> getSharedLibFiles() {
    List<File> sharedLibs = getJetty12SharedLibFiles();

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
    return "com.google.appengine.tools.development.jetty.LocalJspC";
  }
}
