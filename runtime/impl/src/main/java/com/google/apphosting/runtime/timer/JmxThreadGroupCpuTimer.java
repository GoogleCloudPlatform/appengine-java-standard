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
import java.util.HashMap;
import java.util.Map;

/**
 * Use the JMX {@link ThreadMXBean} to retrieve CPU timing information
 * about all of the threads in a particular {@link ThreadGroup}.
 *
 * As there is no API for retrieving per-{@link ThreadGroup} CPU
 * directly, this class is implemented by storing the last-known CPU
 * usage for each {@link Thread} in a {@link ThreadGroup} and
 * performing a comparison each time it is updated.  See the caveat in
 * {@link #update} for an important warning.
 *
 */
class JmxThreadGroupCpuTimer implements Timer {
  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

  private static final int MAX_THREADS = 64;

  protected volatile boolean running = false;
  private final ThreadGroup threadGroup;
  private final Map<Thread, Long> lastCounterMap;
  private long currentSum;

  /**
   * Creates a new {@code JmxThreadGroupCpuTimer} initialized with the
   * specified
   * {@link As a side-effect, also ensures that CPU thread timing is enabled.
   */
  public JmxThreadGroupCpuTimer(ThreadGroup threadGroup) {
    this.threadGroup = threadGroup;
    this.lastCounterMap = new HashMap<>();
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
  public synchronized void start() {
    if (running) {
      throw new IllegalStateException("already running");
    }
    // Before we start the timer, perform an update with !running to
    // get a baseline for all running threads.  This allows us to
    // notice any newly-started threads and include all of their CPU
    // time in subsequent updates.
    update();
    running = true;
  }

  @Override
  public synchronized void stop() {
    if (!running) {
      throw new IllegalStateException("not running");
    }
    update();
    running = false;
  }

  /**
   * Updates the internal snapshot of each thread's CPU usage.
   * Unfortunately, once a thread exits it is no longer possible to
   * retrieve its CPU timing.  Thus, this method should be called
   * periodically -- if possible, right before each thread exits -- to
   * record the last known CPU usage for accurate accouting.
   */
  @Override
  public synchronized void update() {
    Thread[] threads = new Thread[MAX_THREADS];
    int activeThreads = threadGroup.enumerate(threads);
    for (int i = 0; i < activeThreads; i++) {
      Thread thread = threads[i];
      long counter = THREAD_MX.getThreadCpuTime(thread.getId());
      if (counter != -1) {
        Long last = lastCounterMap.get(thread);
        System.err.println(thread + ": " + counter + " (vs. " + last + "), running = " + running);
        if (last != null) {
          long diff = counter - last;
          currentSum += diff;
        } else {
          if (running) {
            // We assume here that the thread was started after the
            // timer was started, since we would otherwise have seen
            // the thread in the update() call that was made at the
            // beginning of start().
            currentSum += counter;
            System.err.println("currentSum = " + currentSum);
          }
        }
        lastCounterMap.put(thread, counter);
      }
    }
  }

  @Override
  public synchronized long getNanoseconds() {
    update();
    return currentSum;
  }

  @Override
  public String toString() {
    return String.format("%.3f", getNanoseconds() / 1000000000.0);
  }
}
