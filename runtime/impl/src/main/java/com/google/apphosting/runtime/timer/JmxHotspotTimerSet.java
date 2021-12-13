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

package com.google.apphosting.runtime.timer;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

/**
 * {@code JmxHotspotTimerSet} creates timers that measures the amount
 * of time spent in Hotspot compilation while each timer was running.
 *
 */
public class JmxHotspotTimerSet extends AbstractSharedTimerSet {
  private static final CompilationMXBean COMPILATION_MBEAN =
      ManagementFactory.getCompilationMXBean();

  @Override
  protected long getCurrentShared() {
    if (COMPILATION_MBEAN == null) {
      // Per the OpenJDK docs, getCompilationMXBean() "returns null if the Java virtual machine has
      // no compilation system". This currently occurs when running under the thread sanitizer.

      return 0;
    }

    return COMPILATION_MBEAN.getTotalCompilationTime() * 1000000;
  }

  @Override
  protected String getTitle() {
    return "hotspot";
  }
}
