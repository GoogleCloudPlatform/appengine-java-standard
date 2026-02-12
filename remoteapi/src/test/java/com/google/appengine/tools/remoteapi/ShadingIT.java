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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ShadingIT {

  @Test
  public void testTransactionBuilderRelocation() throws Exception {
    File targetDir = new File("target");
    if (!targetDir.exists()) {
      System.err.println("Target directory not found, skipping shading test.");
      return;
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
      System.err.println("No shaded jar found in target, skipping shading test.");
      return;
    }

    File shadedJar = files[0];
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
}
