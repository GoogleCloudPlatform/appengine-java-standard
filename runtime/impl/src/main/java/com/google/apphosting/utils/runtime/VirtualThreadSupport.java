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

package com.google.apphosting.utils.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Utility class to support virtual threads using reflection, enabling compatibility with JDK 17.
 */
public class VirtualThreadSupport {
  private static final MethodHandle OF_VIRTUAL;
  private static final MethodHandle UNSTARTED;
  private static final MethodHandle IS_VIRTUAL;

  static {
    MethodHandle ofVirtual = null;
    MethodHandle unstarted = null;
    MethodHandle isVirtual = null;
    try {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      Class<?> threadClass = Thread.class;
      // In JDK 21+, Thread.ofVirtual() returns a Thread.Builder.OfVirtual
      Class<?> builderClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
      
      ofVirtual = lookup.findStatic(threadClass, "ofVirtual", MethodType.methodType(builderClass));
      unstarted = lookup.findVirtual(builderClass, "unstarted", MethodType.methodType(threadClass, Runnable.class));
      isVirtual = lookup.findVirtual(threadClass, "isVirtual", MethodType.methodType(boolean.class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignored) {
      // Not on JDK 21+ or virtual threads not available
    }
    OF_VIRTUAL = ofVirtual;
    UNSTARTED = unstarted;
    IS_VIRTUAL = isVirtual;
  }

  /**
   * Returns true if virtual threads are supported by the current JVM.
   */
  public static boolean isSupported() {
    return OF_VIRTUAL != null;
  }

  /**
   * Creates an unstarted virtual thread if supported.
   *
   * @param runnable the runnable to execute
   * @return an unstarted virtual thread, or null if not supported
   */
  public static Thread createVirtualThread(Runnable runnable) {
    if (OF_VIRTUAL == null || UNSTARTED == null) {
      return null;
    }
    try {
      Object builder = OF_VIRTUAL.invoke();
      return (Thread) UNSTARTED.invoke(builder, runnable);
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Checks if the given thread is a virtual thread.
   *
   * @param thread the thread to check
   * @return true if the thread is virtual, false otherwise
   */
  public static boolean isVirtual(Thread thread) {
    if (IS_VIRTUAL == null) {
      return false;
    }
    try {
      return (boolean) IS_VIRTUAL.invoke(thread);
    } catch (Throwable t) {
      return false;
    }
  }
}
