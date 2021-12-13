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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Use the JMX {@link ThreadMXBean} to retrieve CPU timing information
 * about a particular {@link Thread}.
 *
 * <p>Counts the number of nanoseconds that this thread spent on a CPU.
 * The JVM implements this with a fast path and a slow path.  The fast
 * path involves calling {@code pthread_getcpuclockid} and passing the
 * resulting clock to {@code clock_gettime}, and is only used if
 * {@code -XX:+UseLinuxPosixThreadCPUClocks} is set and if
 * {@code clock_getres} on an arbitrary CPU clock returns < 1 second.
 * The slow path involves reading from
 * {@code /proc/self/task/<tid>/stat}.
 *
 */
class JmxCpuTimer extends AbstractIntervalTimer {
  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

  private final long threadId;

  /**
   * Creates a new {@code JmxCpuTimer} initialized with the specified
   * {@code Thread}.  As a side-effect, also ensures that CPU thread
   * timing is enabled.
   */
  public JmxCpuTimer(Thread thread) {
    this.threadId = thread.getId();
    THREAD_MX.setThreadCpuTimeEnabled(true);
  }

  /**
   * Returns true if the current JVM supports thread cpu timing via
   * {@code ThreadMXBean}.
   */
  public static boolean isAvailable() {
    return THREAD_MX.isThreadCpuTimeSupported();
  }

  @Override
  protected long getCurrent() {
    // This returns the combined user and system time.
    return THREAD_MX.getThreadCpuTime(threadId);
  }
}
