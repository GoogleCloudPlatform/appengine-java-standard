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

/**
 * Callback interface used by Cloud Debugger to query AppEngine Classic runtime.
 */
public interface CloudDebuggerCallback {
  /**
   * Classification of a Java class.
   *
   * <p>This enum must be synced with its C++ counterpart in gae_callback.h.
   */
  public enum ClassType {
    JDK, // Obsolete
    SAFE_RUNTIME,
    RUNTIME,
    APPLICATION,
    UNKNOWN
  }

  /**
   * Classifies Java class.
   *
   * <p>The debugger hides or shows local variables and fields based on the class classification.
   */
  ClassType getClassType(Class<?> cls);
}
