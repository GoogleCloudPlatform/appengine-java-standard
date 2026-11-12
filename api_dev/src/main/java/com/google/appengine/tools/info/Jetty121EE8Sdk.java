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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the SDK abstraction by the existing GAE SDK distribution, which is composed of
 * multiple jar directories for both local execution and deployment of applications.
 */
class Jetty121EE8Sdk extends AppengineSdk {

  // Relative path from SDK Root for the Jetty 12 Home lib directory.
  static final String JETTY121_HOME_LIB_PATH = "jetty121/jetty-home/lib";

  private static final String WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12 =
      "com/google/appengine/tools/development/jetty/webdefault.xml";

  @Override
  public List<File> getUserJspLibFiles() {
    return Collections.unmodifiableList(getJetty121JspJars());
  }

  @Override
  public String getWebDefaultLocation() {

    return WEB_DEFAULT_LOCATION_DEVAPPSERVERJETTY12;
  }

  @Override
  public String getJettyContainerService() {

    return "com.google.appengine.tools.development.jetty.JettyContainerService";
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
  public String getWebDefaultXml() {

    return getSdkRoot() + "/docs/jetty121ee8/webdefault.xml";
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
        new File(getSdkRoot(), "lib/tools/quickstart/quickstartgenerator-jetty121-ee8.jar");

    File jettyDir = new File(getSdkRoot(), JETTY121_HOME_LIB_PATH);
    for (File f : listFiles(jettyDir)) {
      if (!f.isDirectory()
          && !(f.getName().contains("cdi-")
              || f.getName().contains("-alpn-")
              || f.getName().contains("-demo-")
              || f.getName().contains("-websocket-")
              || f.getName().contains("-gcloud-")
              || f.getName().contains("-infinispan-")
              || f.getName().contains("-hazelcast-")
              || f.getName().contains("-ethereum-")
              || f.getName().contains("jakarta.enterprise")
              || f.getName().contains("jakarta.inject")
              || f.getName().contains("jakarta.interceptor")
              || f.getName().contains("jakarta.servlet-api-6")
              || f.getName().contains("jakarta.transaction")
              || f.getName().contains("jetty-jakarta-servlet-api") // No jakarta for EE8
              || f.getName().contains("ee9")
              || f.getName().contains("ee10")
              || f.getName().contains("ee11"))) {
        list.add(f.getAbsolutePath());
      }
    }
    // Add the API jar, in case it is needed (b/120480580).
    list.add(getSdkRoot() + "/lib/impl/appengine-api.jar");

    // Note: Do not put the Apache JSP files in the classpath. If needed, they should be part of
    // the application itself under WEB-INF/lib.

    File ee8Dir = new File(jettyDir, "ee8");
    if (ee8Dir.isDirectory()) {
      // Jetty 12.1.15+ layout: EE8 jars are in lib/ee8 and lib/ee8/support
      for (File f : listFiles(ee8Dir)) {
        if (!f.isDirectory()
            && !(f.getName().contains("apache-jsp")
                || f.getName().contains("-cdi-")
                || f.getName().contains("-alpn-")
                || f.getName().contains("-demo-")
                || f.getName().contains("-websocket-")
                || f.getName().contains("-gcloud-")
                || f.getName().contains("-infinispan-")
                || f.getName().contains("-hazelcast-")
                || f.getName().contains("-ethereum-"))) {
          list.add(f.getAbsolutePath());
        }
      }
      File ee8SupportDir = new File(ee8Dir, "support");
      if (ee8SupportDir.isDirectory()) {
        for (File f : listFiles(ee8SupportDir)) {
          if (!f.isDirectory()
              && (f.getName().contains("jakarta.annotation")
                  || f.getName().contains("jakarta.security.auth.message"))) {
            list.add(f.getAbsolutePath());
          }
        }
      }
      File supportDir = new File(jettyDir, "support");
      if (supportDir.isDirectory()) {
        for (File f : listFiles(supportDir)) {
          if (!f.isDirectory()
              && (f.getName().contains("asm-") || f.getName().contains("slf4j-api"))) {
            list.add(f.getAbsolutePath());
          }
        }
      }
    } else {
      for (String subdir : new String[] {"ee8-annotations"}) { // ? "ee8-jaspi" for Jetty12
        File sub = new File(jettyDir, subdir);
        if (sub.isDirectory()) {
          for (File f : listFiles(sub)) {
            list.add(f.getAbsolutePath());
          }
        }
      }
      File logDir = new File(jettyDir, "logging");
      if (logDir.isDirectory()) {
        for (File f : listFiles(logDir)) {
          list.add(f.getAbsolutePath());
        }
      }
    }

    list.add(quickstart.getAbsolutePath());
    return Joiner.on(System.getProperty("path.separator")).join(list);
  }

  @Override
  public File getResourcesDirectory() {
    return new File(getSdkRoot(), "docs");
  }

  private List<File> getImplLibFiles() {
    Set<File> lf = new LinkedHashSet<>();
    lf.addAll(getJetty121Jars(""));
    lf.addAll(getJetty121JspJars());
    lf.addAll(getJetty121Jars("logging"));
    // We also want the devserver to be able to handle annotated servlet, via ASM:
    lf.addAll(getJetty121Jars("ee8-annotations"));
    lf.addAll(getJetty121Jars("ee8-apache-jsp"));
    lf.addAll(getJetty121Jars("ee8-glassfish-jstl"));
    // Jetty 12.1.15+ layout support:
    lf.addAll(getJetty121Jars("ee8"));
    lf.addAll(getJetty121Jars("ee8/support"));
    lf.addAll(getJetty121Jars("support"));

    lf.addAll(getLibs(sdkRoot, "impl"));
    lf.add(new File(sdkRoot, "lib/impl/jetty121/appengine-local-runtime-jetty121.jar"));
    return Collections.unmodifiableList(new ArrayList<>(lf));
  }

  /**
   * Returns the full paths of all JSP libraries that need to be treated as shared libraries in the
   * SDK.
   */
  public List<URL> getSharedJspLibs() {
    return Collections.unmodifiableList(toURLs(getSharedJspLibFiles()));
  }

  protected File getJetty121Jar(String fileNamePattern) {
    File path = new File(sdkRoot, JETTY121_HOME_LIB_PATH);

    if (!path.exists()) {
      throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
    }
    for (File f : listFiles(path)) {
      if (!f.isDirectory() && f.getName().endsWith(".jar") && f.getName().contains(fileNamePattern)) {
        return f;
      }
    }
    File found = findJarInSubdirs(path, fileNamePattern);
    if (found != null) {
      return found;
    }
    throw new IllegalArgumentException(
        "Unable to find " + fileNamePattern + " at " + path.getAbsolutePath());
  }

  private static File findJarInSubdirs(File dir, String fileNamePattern) {
    for (File f : listFiles(dir)) {
      if (f.isDirectory()) {
        File found = findJarInSubdirs(f, fileNamePattern);
        if (found != null) {
          return found;
        }
      } else if (f.getName().endsWith(".jar") && f.getName().contains(fileNamePattern)) {
        return f;
      }
    }
    return null;
  }

  protected List<File> getJetty121Jars(String subDir) {
    File path = subDir.isEmpty()
        ? new File(sdkRoot, JETTY121_HOME_LIB_PATH)
        : new File(sdkRoot, JETTY121_HOME_LIB_PATH + File.separator + subDir);

    if (!path.exists()) {
      if (subDir.isEmpty()) {
        throw new IllegalArgumentException("Unable to find " + path.getAbsolutePath());
      }
      return Collections.emptyList();
    }
    List<File> jars = new ArrayList<>();
    for (File f : listFiles(path)) {
      if (f.getName().endsWith(".jar")) {
        // All but CDI jar. All the tests are still passing without CDI that should not be exposed
        // in our runtime (private Jetty dependency we do not want to expose to the customer).
        if (!(f.getName().contains("-cdi-")
            || f.getName().contains("-alpn-")
            || f.getName().contains("-demo-")
            || f.getName().contains("-websocket-")
            || f.getName().contains("-gcloud-")
            || f.getName().contains("-infinispan-")
            || f.getName().contains("-hazelcast-")
            || f.getName().contains("jakarta.enterprise")
            || f.getName().contains("jakarta.inject")
            || f.getName().contains("jakarta.interceptor")
            || f.getName().contains("jakarta.servlet-api-6")
            || f.getName().contains("jakarta.transaction")
            || f.getName().contains("jetty-servlet-api-") // no javax. if needed should be in shared
            || f.getName().contains("ee9")
            || f.getName().contains("ee10")
            || f.getName().contains("ee11")
            || f.getName().contains("jetty-jakarta-servlet-api") // No jakarta for EE8
            || (subDir.equals("ee8") && f.getName().contains("apache-jsp")) // apache-jsp comes from nolog in ee8-apache-jsp
        )) {
          jars.add(f);
        }
      }
    }
    return jars;
  }

  List<File> getJetty121JspJars() {
    Set<File> lf = new LinkedHashSet<>();
    File ee8Dir = new File(sdkRoot, JETTY121_HOME_LIB_PATH + File.separator + "ee8");
    if (ee8Dir.isDirectory()) {
      for (File f : listFiles(ee8Dir)) {
        if (f.getName().endsWith(".jar") && f.getName().contains("glassfish-jstl")) {
          lf.add(f);
        }
      }
      File supportDir = new File(ee8Dir, "support");
      if (supportDir.isDirectory()) {
        for (File f : listFiles(supportDir)) {
          if (f.getName().endsWith(".jar")
              && (f.getName().contains("apache-el")
                  || f.getName().contains("apache-jsp")
                  || f.getName().contains("jstl"))) {
            lf.add(f);
          }
        }
      }
      lf.addAll(getJetty121Jars("ee8-apache-jsp"));
    } else {
      lf.addAll(getJetty121Jars("ee8-apache-jsp"));
      lf.addAll(getJetty121Jars("ee8-glassfish-jstl"));
    }
    lf.add(getJetty121Jar("ee8-servlet-"));
    lf.add(new File(
            sdkRoot,
            "lib/shared/jetty12/appengine-local-runtime-shared.jar")); // contains DTD and XSD
                                                                       // schemas
    return new ArrayList<>(lf);
  }

  List<File> getJetty121SharedLibFiles() {
    List<File> sharedLibs = new ArrayList<>();
    sharedLibs.add(new File(sdkRoot, "lib/shared/jetty12/appengine-local-runtime-shared.jar"));
    File jettyHomeLib = new File(sdkRoot, JETTY121_HOME_LIB_PATH);

    File servletApi = new File(jettyHomeLib, "jetty-servlet-api-4.0.9.jar");
    if (!servletApi.exists()) {
      servletApi = new File(jettyHomeLib, "ee8/jetty-servlet-api-4.0.9.jar");
    }
    if (!servletApi.exists()) {
      servletApi = getJetty121Jar("jetty-servlet-api-");
    }
    sharedLibs.add(servletApi); // this is javax.servlet

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

    return "com.google.appengine.tools.development.jetty.LocalJspC";
  }
}
