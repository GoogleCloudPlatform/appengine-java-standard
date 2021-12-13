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
 * Native methods exposed by Java Cloud Debugger agent.
 *
 * <p>These methods should only be called if the Cloud Debugger is enabled. Otherwise these calls
 * will throw exception. The debugger agent is loaded very early, so these methods can be used at
 * any time.
 */
public final class CloudDebuggerAgent {
  /**
   * Starts the initialization sequence of the Java Cloud Debugger agent.
   *
   * @param debuggerInternalsClassLoader ClassLoader object to use to load internal debugger Java
   *                                     classes.
   */
  public static native void bind(ClassLoader debuggerInternalsClassLoader);

  /**
   * Hooks up Cloud Debugger to the application.
   *
   * <p>Multiple paths in {@code classPath} are separated with {@code File.pathSeparator}. The
   * debugger is exploring all the paths recursively opening .JAR files as they are encountered.
   *
   * <p>This function must be called after {@code bind} but before the first breakpoint is set.
   * Setting breakpoints will fail until this method is called.
   *
   * @param classPath paths to application classes
   * @param callback Callback interface used by Cloud Debugger to query AppEngine Classic runtime
   */
  public static native void setApplication(String[] classPath, CloudDebuggerCallback callback);

  /**
   * Updates the list of active breakpoints in the debugger.
   *
   * @param breakpoints list of currently active breakpoints, where each breakpoint is a serialized
   * "com.google.protos.google.cloud.debugger.v1.CloudDebuggerData.Breakpoint" message.
   */
  public static native void setActiveBreakpoints(byte[][] breakpoints);

  /**
   * Verifies if there are breakpoint updates to send to the Cloud Debugger service.
   *
   * @return true if there are breakpoint updates available, false otherwise.
   */
  public static native boolean hasBreakpointUpdates();

  /**
   * Pulls breakpoint updates to send to the Cloud Debugger service through AppServer.
   *
   * @return serialized {@code
   * com.google.protos.google.cloud.debugger.v1.CloudDebuggerData.Breakpoint} messages or null if
   * no breakpoint updates available.
   */
  public static native byte[][] dequeueBreakpointUpdates();

  private CloudDebuggerAgent() {}
}
