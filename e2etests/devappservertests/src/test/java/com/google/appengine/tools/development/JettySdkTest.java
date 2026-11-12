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
package com.google.appengine.tools.development;

import static com.google.appengine.tools.development.DevAppServerTestBase.getSdkRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.tools.info.AppengineSdk;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JettySdkTest {

  private void assertFilesExist(Iterable<File> files) {
    for (File f : files) {
      System.out.println(f.getAbsolutePath());
      assertThat(f.exists()).isTrue();
    }
  }

  private void assertUrlsExist(List<URL> urls) throws URISyntaxException {
    for (URL url : urls) {
     System.out.println(new File(url.toURI()).getAbsolutePath());
     assertThat(new File(url.toURI()).exists()).isTrue();
    }
  }

  @Before
  public void before() {
    System.setProperty("appengine.sdk.root", getSdkRoot().getAbsolutePath());
  }

  @After
  public void after() {

    System.clearProperty("appengine.use.EE8");
    System.clearProperty("appengine.use.EE10");
    System.clearProperty("appengine.use.EE11");
    System.clearProperty("appengine.use.jetty121");
    AppengineSdk.resetSdk();
  }

  @Test
  public void testDefaultSdk() throws Exception {
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE8Sdk");
  }

  @Test
  public void testJettyEE8() throws Exception {
    System.setProperty("appengine.use.EE8", "true");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "false");
    System.out.println("Jetty 12 EE8");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE8Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJettyEE10() throws Exception {
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE10", "true");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "false");
    System.out.println("Jetty 12 EE10");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE11Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJettyEE11() throws Exception {
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "true");
    System.setProperty("appengine.use.jetty121", "true");
    System.out.println("Jetty 12.1 EE11");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE11Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  @Test
  public void testJetty121EE8() throws Exception {
    System.setProperty("appengine.use.EE8", "true");
    System.setProperty("appengine.use.EE10", "false");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "true");
    System.out.println("Jetty 12.1 EE8");
    AppengineSdk sdk = AppengineSdk.getSdk();
    assertThat(sdk.getClass().getSimpleName()).isEqualTo("Jetty121EE8Sdk");
    System.out.println("getUserLibFiles");
    assertFilesExist(sdk.getUserLibFiles());
    System.out.println("getUserJspLibFiles");
    assertFilesExist(sdk.getUserJspLibFiles());
    System.out.println("getSharedLibFiles");
    assertFilesExist(sdk.getSharedLibFiles());
    System.out.println("getSharedJspLibFiles");
    assertFilesExist(sdk.getSharedJspLibFiles());
    System.out.println("getUserJspLibs");
    assertUrlsExist(sdk.getUserJspLibs());
    System.out.println("getImplLibs");
    assertUrlsExist(sdk.getImplLibs());
  }

  /**
   * Verifies that the ContainerIncludeJarPattern configured on DevAppEngineWebAppContext
   * successfully matches the shared container jars (e.g. jakarta.servlet-api and JSTL) in the SDK.
   * This ensures that TLDs and web fragments in shared libraries are scanned and available to webapps.
   */
  @Test
  public void testContainerIncludeJarPatternMatchesSharedJars() {
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE11", "true");
    System.setProperty("appengine.use.jetty121", "true");
    AppengineSdk.resetSdk();
    AppengineSdk sdk = AppengineSdk.getSdk();
    List<File> sharedLibs = ImmutableList.copyOf(sdk.getSharedLibFiles());
    Pattern pattern =
        Pattern.compile(".*jakarta.servlet-api-[^/]*\\.jar$|.*jakarta.servlet.jsp.jstl-.*\\.jar$");

    boolean matchedServletApi =
        sharedLibs.stream()
            .map(File::toURI)
            .map(URI::toString)
            .anyMatch(uri -> pattern.matcher(uri).matches());
    assertWithMessage(
            "Pattern %s must match a servlet-api jar in sharedLibs: %s", pattern, sharedLibs)
        .that(matchedServletApi)
        .isTrue();

    boolean matchedJstlApi =
        sharedLibs.stream()
            .map(File::toURI)
            .map(URI::toString)
            .anyMatch(uri -> pattern.matcher(uri).matches());
    assertWithMessage("Pattern %s must match a JSTL jar in sharedLibs: %s", pattern, sharedLibs)
        .that(matchedJstlApi)
        .isTrue();
  }

  /**
   * Verifies that no jar file exposed in shared or JSP libraries contains the Tomcat JULI Log
   * ServiceLoader provider (META-INF/services/org.apache.juli.logging.Log).
   * The presence of this SPI provider can hijack Apache Commons Logging and cause deadlocks.
   */
  @Test
  public void testNoJuliLoggingSpiInSharedOrJspLibs() throws Exception {
    for (String ee : new String[] {"EE8", "EE11"}) {
      System.setProperty("appengine.use.EE8", String.valueOf("EE8".equals(ee)));
      System.setProperty("appengine.use.EE11", String.valueOf("EE11".equals(ee)));
      System.setProperty("appengine.use.jetty121", "true");
      AppengineSdk.resetSdk();
      AppengineSdk sdk = AppengineSdk.getSdk();
      for (File jar : sdk.getSharedLibFiles()) {
        try (JarFile jarFile = new JarFile(jar)) {
          ZipEntry entry = jarFile.getEntry("META-INF/services/org.apache.juli.logging.Log");
          assertWithMessage(
                  "Jar %s in %s shared libs must not contain JULI Log SPI", jar.getName(), ee)
              .that(entry)
              .isNull();
        }
      }
      for (File jar : sdk.getUserJspLibFiles()) {
        try (JarFile jarFile = new JarFile(jar)) {
          ZipEntry entry = jarFile.getEntry("META-INF/services/org.apache.juli.logging.Log");
          assertWithMessage(
                  "Jar %s in %s user JSP libs must not contain JULI Log SPI", jar.getName(), ee)
              .that(entry)
              .isNull();
        }
      }
    }
  }

  /**
   * Verifies that all classes required for JSP compilation and execution (e.g. Jasper servlet,
   * JspPage, RecordELResolver, and TldScanner) are resolvable from the combined user JSP libs
   * and shared libs classpath on both EE8 and EE11.
   */
  @Test
  public void testJspClasspathContainsRequiredClasses() throws Exception {
    // EE8 JSP classpath check
    System.setProperty("appengine.use.EE8", "true");
    System.setProperty("appengine.use.EE11", "false");
    System.setProperty("appengine.use.jetty121", "true");
    AppengineSdk.resetSdk();
    AppengineSdk ee8Sdk = AppengineSdk.getSdk();
    List<URL> ee8Urls = new ArrayList<>(ee8Sdk.getUserJspLibs());
    ee8Urls.addAll(ee8Sdk.getSharedLibs());
    try (URLClassLoader cl = new URLClassLoader(ee8Urls.toArray(new URL[0]), null)) {
      assertThat(cl.loadClass("javax.servlet.jsp.JspPage")).isNotNull();
      assertThat(cl.loadClass("org.apache.jasper.servlet.JspServlet")).isNotNull();
    }

    // EE11 JSP classpath check
    System.setProperty("appengine.use.EE8", "false");
    System.setProperty("appengine.use.EE11", "true");
    System.setProperty("appengine.use.jetty121", "true");
    AppengineSdk.resetSdk();
    AppengineSdk ee11Sdk = AppengineSdk.getSdk();
    List<URL> ee11Urls = new ArrayList<>(ee11Sdk.getUserJspLibs());
    ee11Urls.addAll(ee11Sdk.getSharedLibs());
    try (URLClassLoader cl = new URLClassLoader(ee11Urls.toArray(new URL[0]), null)) {
      assertThat(cl.loadClass("jakarta.servlet.jsp.JspPage")).isNotNull();
      assertThat(cl.loadClass("jakarta.el.RecordELResolver")).isNotNull();
      assertThat(cl.loadClass("org.glassfish.wasp.runtime.TldScanner")).isNotNull();
    }
  }

  /**
   * Verifies that optional, clustering, or experimental Jetty modules (such as CDI, ALPN,
   * WebSocket, Hazelcast, Infinispan, and Ethereum) are excluded from the shared and JSP classpaths
   * to prevent accidental classloader contamination or conflicts.
   */
  @Test
  public void testForbiddenModulesNotExposed() {
    List<String> forbidden =
        List.of(
            "-cdi-",
            "-alpn-",
            "-demo-",
            "-websocket-",
            "-gcloud-",
            "-infinispan-",
            "-hazelcast-",
            "-ethereum-");

    for (String ee : new String[] {"EE8", "EE11"}) {
      System.setProperty("appengine.use.EE8", String.valueOf("EE8".equals(ee)));
      System.setProperty("appengine.use.EE11", String.valueOf("EE11".equals(ee)));
      System.setProperty("appengine.use.jetty121", "true");
      AppengineSdk.resetSdk();
      AppengineSdk sdk = AppengineSdk.getSdk();

      for (File jar : sdk.getSharedLibFiles()) {
        for (String f : forbidden) {
          assertWithMessage("Shared jar %s must not contain %s", jar.getName(), f)
              .that(jar.getName().contains(f))
              .isFalse();
        }
      }
      for (File jar : sdk.getUserJspLibFiles()) {
        for (String f : forbidden) {
          assertWithMessage("JSP jar %s must not contain %s", jar.getName(), f)
              .that(jar.getName().contains(f))
              .isFalse();
        }
      }
    }
  }
}
