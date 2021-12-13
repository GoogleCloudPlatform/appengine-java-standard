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

package com.google.appengine.api.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.Reflection;
import com.google.common.truth.Expect;
import com.google.protobuf.MessageLite;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;

/**
 * Base class for JUnit4 tests that want to verify backwards compatibility of
 * {@link Serializable} classes.  Extend this class and implement
 * {@link #getCanonicalObjects()}.  You probably also want to create a main
 * method that invokes {@link #writeCanonicalObjects()} so you can easily
 * regenerate the golden files.  An example:
 *
 *  public static void main(String[] args) throws IOException {
 *    SerializationTest st = new SerializationTest();
 *    st.writeCanonicalObjects();
 *  }
 *
 */
public abstract class SerializationTestBase {
  @Rule public Expect expect = Expect.create();

  // avoid filenames that contain a dollar sign
  private static final String DOLLAR_REPLACEMENT = "__dollar__";

  private static final StackTraceElement[] FIXED_STACK_TRACE = {
    new StackTraceElement("foo", "bar", "whap", 88),
  };

  protected abstract Iterable<Serializable> getCanonicalObjects();

  /**
   * Returns serializable objects to be tested in addition to the objects returned by {@link
   * #getCanonicalObjects()}. This is a workaround for the fact that the golden file naming scheme
   * allows for at most one canonical file per type. The objects returned by this method will not be
   * compared to golden files, but they are checked for the presence of "non-standard" classes.
   */
  protected Iterable<Serializable> getAdditionalObjects() {
    return ImmutableList.of();
  }

  protected abstract Class<?> getClassInApiJar();

  protected String getGoldenFilenameForClass(Class<? extends Serializable> serClass) {
    return serClass.getName().replace("$", DOLLAR_REPLACEMENT) + ".golden";
  }

  protected boolean goldenFileExistsForClass(Class<? extends Serializable> serClass) {
    return getGoldenFileForClass(serClass) != null;
  }

  protected InputStream getGoldenFileForClass(Class<? extends Serializable> serClass) {
    return getClass()
        .getResourceAsStream("serialization_test_data/" + getGoldenFilenameForClass(serClass));
  }

  @Test
  public void testSerializationBackwardsCompatibility() throws Exception {
    if (System.getenv("JAVA_COVERAGE_FILE") != null) {
      // Coverage instrumentation interferes with this test.
      return;
    }
    for (Serializable ser : getCanonicalObjects()) {
      try (InputStream is = getGoldenFileForClass(ser.getClass());
          ObjectInputStream ois = new ObjectInputStream(is)) {
        Object o = ois.readObject();
        // only check equality if we've defined an equals method
        Method equalsMethod = o.getClass().getMethod("equals", Object.class);
        if (!equalsMethod.getDeclaringClass().equals(Object.class)) {
          assertWithMessage(ser.getClass().getName()).that(o).isEqualTo(ser);
        }
      } catch (Exception e) {
        throw new Exception(
            "Non-backwards compatible changes to the serialized form of " + ser.getClass().getName()
                + " have been made.  If you need to update the golden files please follow the"
                + " instructions in the BUILD file in the same directory as the test that failed.",
            e);
      }
    }
  }

  private static class NonStandardClassException extends ClassNotFoundException {
    NonStandardClassException(String message) {
      super(message);
    }
  }

  /**
   * An ObjectInputStream which fails if any of the serialized objects is of a nonstandard class,
   * meaning neither a JRE class nor an App Engine API class.
   */
  private static class StandardObjectInputStream extends ObjectInputStream {
    StandardObjectInputStream(InputStream in) throws IOException {
      super(in);
      enableResolveObject(true);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
        throws IOException, ClassNotFoundException {
      // Load the class in order to resolve array classes. Otherwise we'd have to fiddle with
      // names like "[Ljava.lang.StackTraceElement;".
      Class<?> c = Class.forName(desc.getName());
      while (c.isArray()) {
        c = c.getComponentType();
      }
      if (c.getClassLoader() == null
          || c.getName().startsWith("com.google.appengine.api.")) {
        return super.resolveClass(desc);
      }
      throw new NonStandardClassException(desc.getName());
    }
  }

  /**
   * Tests that none of the canonical objects references "non-standard" classes. The reason is
   * that classes such as com.google.common.collect.ImmutableList will get repackaged in
   * appengine-api.jar, and we don't guarantee that the repackaged name will always be the same.
   * So the implementation of these objects should not include ImmutableList or the like in what's
   * serialized.
   */
  @Test
  public void testSerializedObjectsContainOnlyStandardClasses() throws Exception {
    for (Serializable ser : Iterables.concat(getCanonicalObjects(), getAdditionalObjects())) {
      if (!ser.getClass().getName().startsWith("com.google.appengine.api.")) {
        // Could be com.google.appengine.tools.cloudstorage. Something from the GCS client,
        // for example. Since that's not in appengine-api.jar it doesn't concern us.
        continue;
      }
      byte[] bytes;
      try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
          ObjectOutputStream oout = new ObjectOutputStream(bout)) {
        oout.writeObject(ser);
        oout.flush();
        bytes = bout.toByteArray();
      }
      try (ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
          StandardObjectInputStream oin = new StandardObjectInputStream(bin)) {
        try {
          Serializable deser = (Serializable) oin.readObject();
          assertThat(deser).isNotNull();
        } catch (NonStandardClassException e) {
          expect
              .withMessage(
                  "Non-standard class encountered when serializing an object of type %s: %s: %s",
                  ser.getClass().getName(), ser, e.getMessage())
              .fail();
        }
      }
    }
  }

  /**
   * Load every class in the jar that contains the provided class. If that class is in the same
   * package and is {@link Serializable}, make sure we have a golden file for it.
   *
   * <p>Sometimes we will have 0 serializable classes, but that's OK. We have a presubmit that
   * requires each package to have a SerializationTest, so we might have one just for the sake of
   * that.
   */
  @Test
  public void testSerializableClassesHaveGoldenFiles() throws Exception {
    Class<?> exampleClass = getClassInApiJar();
    String apiPackage = Reflection.getPackageName(exampleClass);
    URL apiJarFile = getClassInApiJar().getProtectionDomain().getCodeSource().getLocation();
    JarFile jar = new JarFile(apiJarFile.getFile());
    for (JarEntry entry : Collections.list(jar.entries())) {
      int dotClassIndex = entry.getName().indexOf(".class");
      if (dotClassIndex == -1) {
        continue;
      }
      String className = entry.getName().substring(0, dotClassIndex).replace('/', '.');
      int lastDot = className.lastIndexOf('.');
      if (lastDot < 0 || !className.substring(0, lastDot).equals(apiPackage)) {
        continue;
      }
      Class<?> clazz = Class.forName(className);
      int uninstantiableMask = Modifier.ABSTRACT | Modifier.INTERFACE;
      boolean isInstantiable = (clazz.getModifiers() & uninstantiableMask) == 0;
      if (isInstantiable
          && Serializable.class.isAssignableFrom(clazz)
          && !MessageLite.class.isAssignableFrom(clazz)
          && !Enum.class.isAssignableFrom(clazz)) {
        Class<? extends Serializable> clazzSer = clazz.asSubclass(Serializable.class);
        expect
            .withMessage(
                "Missing a golden file for %s. Make sure you are returning an instance of this"
                    + " class in your overload of %s.getCanonicalObjects()",
                clazz.getName(), SerializationTestBase.class.getSimpleName())
            .that(goldenFileExistsForClass(clazzSer))
            .isTrue();
      }
    }
  }

  protected void writeCanonicalObjects() throws IOException {
    for (Serializable ser : getCanonicalObjects()) {
      Class<? extends Serializable> serClass = ser.getClass();
      // We need to test abstract classes, but since we
      // generate the golden filename based on the runtime type
      // of the object which will never be an abstract class,
      // we look to see if the object implements a special
      // interface that will tell us what class we should use
      // to generate the golden file.
      if (ser instanceof HasOverriddenClass) {
        serClass = ((HasOverriddenClass) ser).getOverriddenClass();
      }

      if (ser instanceof Throwable) {
        // The stack trace is part of Throwable's serialized form, which means
        // that the golden file for every exception changes whenever the class
        // that generates the golden file changes (due to line num changes).
        // To avoid this we set a hard-coded stacktrace so we don't see
        // differences in the golden files we generate with each release.
        addStackTrace((Throwable) ser);
      }

      // Generate the golden files in a directory in the current working
      // directory named "golden_serialization/xxx" where "xxx" is the name of
      // the api.
      List<String> splitResult = Splitter.on('.').splitToList(serClass.getPackage().getName());
      String api = Iterables.getLast(splitResult);
      File dir = new File("golden_serialization/" + api);
      if (!dir.exists()) {
        dir.mkdirs();
      }
      String goldenFilename = dir.getAbsolutePath() + "/" + getGoldenFilenameForClass(serClass);
      try (FileOutputStream fos = new FileOutputStream(goldenFilename);
          ObjectOutputStream oos = new ObjectOutputStream(fos)) {
        oos.writeObject(ser);
        Logger.getLogger(getClass().getName()).info("Wrote " + goldenFilename);
      }
    }
  }

  /** Implemented by abstract classes to indicate the corresponding concrete implementation. */
  protected interface HasOverriddenClass {
    Class<? extends Serializable> getOverriddenClass();
  }

  /**
   * Adds a hard-coded stacktrace to the provided {@link Throwable}.
   */
  private void addStackTrace(Throwable t) {
    t.setStackTrace(FIXED_STACK_TRACE);
  }
}
