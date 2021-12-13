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

package com.google.apphosting.runtime;

import com.google.appengine.api.ApiJarPath;
import com.google.common.base.Throwables;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that no serializable class in the API jar has a field whose type is a repackaged class.
 * Since the repackaging scheme changed between Java 7 and Java 8, and might change again,
 * serializing values of repackaged type, like
 * com.google.appengine.repackaged.com.google.common.collect.$ImmutableList, will cause problems.
 * The mere fact that there are no fields of those types doesn't mean that no <i>value</i> that we
 * put in those fields has a repackaged type, but it is a start.
 */
@RunWith(JUnit4.class)
public class RepackagedSerializationTest {
  @Rule public Expect expect = Expect.create();

  @Test
  public void noSerializableFieldsOfRepackagedType() throws Exception {
    URLClassLoader apiJarLoader = getUrlClassLoader();
    Class<?> messageLite =
        apiJarLoader.loadClass("com.google.appengine.repackaged.com.google.protobuf.MessageLite");
    ClassPath classPath = ClassPath.from(apiJarLoader);
    for (ClassInfo classInfo : classPath.getAllClasses()) {
      if (classInfo.getName().startsWith("com.google.appengine.api")) {
        Class<?> c;
        try {
          c = classInfo.load();
        } catch (Throwable t) {
          expect
              .withMessage(
                  "Could not load %s: %s", classInfo.getName(), Throwables.getStackTraceAsString(t))
              .fail();
          continue;
        }
        if (Serializable.class.isAssignableFrom(c) && !messageLite.isAssignableFrom(c)) {
          // If c instanceof MessageLite, then its repackaged name is unchanging, as are the
          // repackaged names of its field, so we don't need to check it.
          checkAncestorsAreNotRepackaged(c);
          checkSerializableClass(c);
        }
      }
    }
  }

  private URLClassLoader getUrlClassLoader() throws MalformedURLException, URISyntaxException {
    File apiJar = ApiJarPath.getFile();
    @SuppressWarnings("deprecation")
    URL apiJarUrl = apiJar.toURL();
    // pass in a 'null' parent to disable the default behavior of adding the SystemClassLoader
    // to the chain
    URLClassLoader apiJarLoader;
    if (isGoogleBuildSystem(apiJar)) {
      // the google internal build system has some additional runtime dependencies that come in
      // via the system class loader
      apiJarLoader = new URLClassLoader(new URL[] {apiJarUrl});
    } else {
      // pass in a 'null' parent to disable the default behavior of adding the system class loader
      // to the chain to prevent additional class locations from leaking into the classpath
      // Also, activation jar is needed when using a JDK11 to run this test.
      try {
        URL activationUrl =
            javax.activation.DataSource.class.getProtectionDomain().getCodeSource().getLocation();
        URL activationJar = Paths.get(activationUrl.toURI()).toFile().toURL();
        apiJarLoader = new URLClassLoader(new URL[] {apiJarUrl, activationJar}, null, null);
      } catch (NullPointerException e) {
        apiJarLoader = new URLClassLoader(new URL[] {apiJarUrl}, null, null);
      }
    }
    return apiJarLoader;
  }

  private boolean isGoogleBuildSystem(File apiJarFile) {
    return apiJarFile.getName().equals("appengine-api.jar");
  }

  private void checkAncestorsAreNotRepackaged(Class<?> c) {
    for (Class<?> sup = c.getSuperclass(); sup != null; sup = sup.getSuperclass()) {
      expect.withMessage(c.getName()).that(sup.getName()).doesNotContain("repackaged");
    }
  }

  private void checkSerializableClass(Class<?> c) {
    Class<?> sup = c.getSuperclass();
    if (sup != null && Serializable.class.isAssignableFrom(sup)) {
      checkSerializableClass(c.getSuperclass());
    }
    ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(c);
    for (ObjectStreamField objectStreamField : objectStreamClass.getFields()) {
      Class<?> type = objectStreamField.getType();
      while (type.isArray()) {
        type = type.getComponentType();
      }
      expect
          .withMessage("Field %s.%s", c.getName(), objectStreamField.getName())
          .that(type.getName())
          .doesNotContain("repackaged");
    }
  }
}
