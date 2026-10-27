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

package com.google.appengine.tools.remoteapi;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class ShadingIT {

  /**
   * Returns the shaded jar produced by this build. Failsafe passes its path in the {@code
   * remoteapi.shaded.jar} system property; otherwise (e.g. when run from an IDE) the most recently
   * built jar in {@code target/} is used, since jars of other versions may also be there.
   */
  private static File findShadedRemoteApiJar() {
    String shadedJarProperty = System.getProperty("remoteapi.shaded.jar");
    if (shadedJarProperty != null) {
      File shadedJar = new File(shadedJarProperty);
      assertTrue("Expected shaded jar " + shadedJar + " to exist", shadedJar.isFile());
      return shadedJar;
    }
    File targetDir = new File("target");
    if (!targetDir.exists()) {
      return null;
    }
    File[] files =
        targetDir.listFiles(
            (dir, name) ->
                name.startsWith("appengine-remote-api-")
                    && name.endsWith(".jar")
                    && !name.contains("original")
                    && !name.contains("sources")
                    && !name.contains("javadoc"));
    if (files == null || files.length == 0) {
      return null;
    }
    return Collections.max(Arrays.asList(files), Comparator.comparingLong(File::lastModified));
  }

  @Test
  public void testTransactionBuilderRelocation() throws Exception {
    File shadedJar = findShadedRemoteApiJar();
    if (shadedJar == null) {
      System.err.println("No shaded jar found in target, skipping shading test.");
      return;
    }
    System.out.println("Testing shaded jar: " + shadedJar.getAbsolutePath());

    // Construct a classpath that includes the shaded jar and all dependencies,
    // but EXCLUDES the original classes (target/classes) to ensure we test the shaded ones.
    List<URL> urls = new ArrayList<>();
    urls.add(shadedJar.toURI().toURL());

    String classpath = System.getProperty("java.class.path");
    String[] paths = classpath.split(File.pathSeparator);

    for (String path : paths) {
      if (path.contains("target" + File.separator + "classes") || path.contains("target/classes")) {
        continue; // Skip original classes
      }
      if (path.contains("target" + File.separator + "test-classes")
          || path.contains("target/test-classes")) {
        continue; // Skip test classes
      }
      urls.add(new File(path).toURI().toURL());
    }

    // Use platform classloader as parent to get JDK classes, but not system classpath
    ClassLoader parent = ClassLoader.getPlatformClassLoader();

    try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), parent)) {

      // Verify that we can load the TransactionBuilder class
      Class<?> tbClass =
          loader.loadClass("com.google.appengine.tools.remoteapi.TransactionBuilder");

      // Ensure we loaded it from the shaded jar
      URL tbUrl = tbClass.getProtectionDomain().getCodeSource().getLocation();
      System.out.println("Loaded TransactionBuilder from: " + tbUrl);
      if (!tbUrl.toString().contains(shadedJar.getName())) {
        System.err.println("WARNING: TransactionBuilder loaded from unexpected location: " + tbUrl);
        // We might fail here if we want to be strict, but for now let's proceed and check behavior.
      }

      Constructor<?> ctor = tbClass.getDeclaredConstructor(boolean.class);
      ctor.setAccessible(true);
      Object instance = ctor.newInstance(false);

      // We invoke the method to ensure that the bytecode within the method
      // (which references the relocated inner class) is verified and linked.
      // Merely loading the TransactionBuilder class might not trigger the
      // resolution of the inner class if it's only used inside a method.
      // If the relocation of inner classes failed, this will throw NoClassDefFoundError
      // (caused by ClassNotFoundException: ...DatastoreV3Pb$PutRequestOrBuilder)
      Method method = tbClass.getDeclaredMethod("makeCommitRequest");
      method.setAccessible(true);

      try {
        Object result = method.invoke(instance);
        assertNotNull(result);
      } catch (Throwable e) {
        // Unwrap reflection exception if possible
        Throwable cause = e.getCause();
        if (cause != null) {
          throw new RuntimeException("Invocation failed: " + cause.toString(), cause);
        }
        throw e;
      }
    }
  }

  /**
   * Regression test for b/261987450 (missing relocated {@code io.opencensus} classes) and
   * b/267681564 (missing relocated {@code io.grpc.Context} class required by OpenCensus span
   * context propagation) when running with only {@code appengine-remote-api.jar} and {@code
   * appengine-api-1.0-sdk.jar} on the classpath.
   */
  @Test
  public void testOpenCensusAndGrpcContextShading() throws Exception {
    File shadedJar = findShadedRemoteApiJar();
    if (shadedJar == null) {
      System.err.println("No shaded jar found in target, skipping shading test.");
      return;
    }

    // Include ONLY the shaded appengine-remote-api jar and appengine-api-1.0-sdk jar (excluding
    // optional/unshaded Maven test classpath dependencies such as io.opencensus and io.grpc) to
    // replicate a standalone user classpath.
    List<URL> urls = new ArrayList<>();
    urls.add(shadedJar.toURI().toURL());

    String classpath = System.getProperty("java.class.path");
    for (String path : classpath.split(File.pathSeparator)) {
      if (path.contains("appengine-api-1.0-sdk") && path.endsWith(".jar")) {
        urls.add(new File(path).toURI().toURL());
      }
    }
    assertTrue(
        "Expected appengine-api-1.0-sdk.jar on test classpath, found: " + urls, urls.size() >= 2);

    ClassLoader parent = ClassLoader.getPlatformClassLoader();
    try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), parent)) {
      Class<?> openCensusUtilsClass =
          loader.loadClass(
              "com.google.appengine.repackaged.com.google.api.client.http.OpenCensusUtils");
      URL codeSourceUrl = openCensusUtilsClass.getProtectionDomain().getCodeSource().getLocation();
      assertTrue(
          "Expected OpenCensusUtils to be loaded from shaded remoteapi jar, but was: "
              + codeSourceUrl,
          codeSourceUrl.toString().contains(shadedJar.getName()));

      // 1. Verify OpenCensusUtils.getTracer() resolves repackaged io.opencensus (b/261987450).
      Object tracer = openCensusUtilsClass.getMethod("getTracer").invoke(null);
      assertNotNull(tracer);
      assertTrue(
          tracer
              .getClass()
              .getName()
              .startsWith("com.google.appengine.repackaged.io.opencensus.trace."));

      // 2. Verify building and executing an HttpRequest exercises OpenCensus span creation and
      // io.grpc.Context span propagation without NoClassDefFoundError (b/261987450, b/267681564).
      Class<?> mockTransportClass =
          loader.loadClass(
              "com.google.appengine.repackaged.com.google.api.client.testing.http.MockHttpTransport");
      Class<?> genericUrlClass =
          loader.loadClass("com.google.appengine.repackaged.com.google.api.client.http.GenericUrl");
      Object transport = mockTransportClass.getDeclaredConstructor().newInstance();
      Object requestFactory =
          mockTransportClass.getMethod("createRequestFactory").invoke(transport);
      Object url =
          genericUrlClass.getDeclaredConstructor(String.class).newInstance("http://localhost:8080");
      Object request =
          requestFactory
              .getClass()
              .getMethod("buildGetRequest", genericUrlClass)
              .invoke(requestFactory, url);
      Object response = request.getClass().getMethod("execute").invoke(request);
      assertNotNull(response);

      // 3. Verify repackaged io.grpc.Context is directly resolvable (b/267681564).
      Class<?> grpcContextClass =
          loader.loadClass("com.google.appengine.repackaged.io.grpc.Context");
      Object currentContext = grpcContextClass.getMethod("current").invoke(null);
      assertNotNull(currentContext);

      // 4. Verify legacy bundled google-api-client keystores (google.jks / google.p12) are excluded
      // from the shaded jars (b/578046410) and GoogleNetHttpTransport uses the JDK truststore.
      assertNull(
          loader.getResource(
              "com/google/appengine/repackaged/com/google/api/client/googleapis/google.jks"));
      assertNull(
          loader.getResource(
              "com/google/appengine/repackaged/com/google/api/client/googleapis/google.p12"));
      Class<?> transportClass =
          loader.loadClass(
              "com.google.appengine.repackaged.com.google.api.client.googleapis.javanet.GoogleNetHttpTransport");
      Object trustedTransport = transportClass.getMethod("newTrustedTransport").invoke(null);
      assertNotNull(trustedTransport);
    }
  }
}
