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

import java.lang.reflect.Method;

/** Utility to handle JDK 9 specific ClassLoader method for getPlatformClassLoader() */
public class ClassLoaderUtil {

  private static final ClassLoader PLATFORM_CLASSLOADER = getPlatformClassLoaderViaReflection();

  private static ClassLoader getPlatformClassLoaderViaReflection() {
    try {
      Method getPlatformClassLoader = ClassLoader.class.getMethod("getPlatformClassLoader");
      return (ClassLoader) getPlatformClassLoader.invoke(null);
    } catch (ReflectiveOperationException ignore) {
      return null;
    }
  }

  public static ClassLoader getPlatformClassLoader() {
    return PLATFORM_CLASSLOADER;
  }

  private ClassLoaderUtil() {}
}
