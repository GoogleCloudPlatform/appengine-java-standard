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

package com.google.appengine.apicompat;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * A {@link ClassLoader} that instruments the classes it loads with method calls that track the
 * invocation of certain methods and references of certain fields.
 *
 */
class UsageTrackingClassLoader extends URLClassLoader {

  /**
   * The api class whose usage we are tracking.
   */
  private final Class<?> apiClass;

  private final Predicate<String> isExhaustiveUsageClass;

  /**
   * Cache of classes loaded by this classloader. The key is the fully-qualified classname.
   */
  private final Map<String, Class<?>> classesLoadedByTrackingClassLoader = Maps.newConcurrentMap();

  /**
   * Constructor
   *
   * @param apiClass The api class whose usage we will track.
   * @param isExhaustiveUsageClass Predicate that tells us whether or not a class loaded by this
   * classloader should be treated as a class that is responsible for the exhaustive usage of an
   * api.
   */
  public UsageTrackingClassLoader(Class<?> apiClass, Predicate<String> isExhaustiveUsageClass) {
    super(new URL[0]);
    this.apiClass = apiClass;
    this.isExhaustiveUsageClass = isExhaustiveUsageClass;
  }

  @Override
  public Class<?> loadClass(String clsName) throws ClassNotFoundException {
    if (isExhaustiveUsageClass.apply(clsName)) {
      return findClass(clsName);
    }
    return super.loadClass(clsName);
  }

  @Override
  protected Class<?> findClass(String clsName) throws ClassNotFoundException {
    if (isExhaustiveUsageClass.apply(clsName)) {
      if (!classesLoadedByTrackingClassLoader.containsKey(clsName)) {
        byte[] classBytes;
        try {
          classBytes = getClassBytes(clsName);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        classBytes = instrument(classBytes);
        classesLoadedByTrackingClassLoader.put(
            clsName, defineClass(clsName, classBytes, 0, classBytes.length));
      }
      return classesLoadedByTrackingClassLoader.get(clsName);
    }
    return super.findClass(clsName);
  }

  /**
   * Retrieve the bytes that define the given class.
   *
   * @param clsName The fully-qualified name of the class whose bytes we are retrieving.
   * @return The bytes of the given class.
   * @throws IOException            If we cannot retrieve the class bytes.
   * @throws ClassNotFoundException If the given class cannot be found.
   */
  private byte[] getClassBytes(String clsName) throws IOException, ClassNotFoundException {
      try {
    ClassReader reader = new ClassReader(clsName);
    StringWriter sw = new StringWriter();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(os));
    reader.accept(tcv, ClassReader.EXPAND_FRAMES);
   return os.toByteArray();

} catch (IOException e) {
    e.printStackTrace();
    return null;
}
  }
  /*ludo ludo
    URL jarFile = Class.forName(clsName, true, getParent()).getProtectionDomain()
        .getCodeSource().getLocation();
    URL apiJar = new URL("jar:file:" + jarFile.getFile() + "!/");
    JarURLConnection conn = (JarURLConnection) apiJar.openConnection();
    JarFile jar = conn.getJarFile();
    ZipEntry entry = jar.getEntry(clsName.replace('.', '/') + ".class");
    InputStream is = jar.getInputStream(entry);
    try {
      return ByteStreams.toByteArray(is);
    } finally {
      is.close();
    }
  }*/

  /**
   * Instrument the given bytecode with method invocations that track method calls and field
   * references.
   *
   * @param bytecode The bytecode to instrument.
   * @return The instrumented bytecode.
   */
  private byte[] instrument(byte[] bytecode) {
    ClassReader reader = new ClassReader(bytecode);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    ClassVisitor visitor = new ApiVisitor(writer, apiClass);
    reader.accept(visitor, ClassReader.SKIP_FRAMES);
    return writer.toByteArray();
  }
}
