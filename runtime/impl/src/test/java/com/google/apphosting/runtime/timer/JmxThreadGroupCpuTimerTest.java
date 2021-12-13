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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for JmxThreadGroupCpuTimer.
 *
 */
@RunWith(JUnit4.class)
public class JmxThreadGroupCpuTimerTest {
  private static final long MILLIS_TO_NANOS = 1000000;

  /**
   * Both sleeping and busy tests will last this number of milliseconds;
   */
  private static final long TEST_TIME = 2000;

  /**
   * The sleep loop test should spend no more than 1% of its time CPU-bound.
   */
  private static final double MAX_SLEEP_CPU_RATIO = 0.01;

  /**
   * The busy loop test should spend at least 10% of its time
   * CPU-bound.  Don't make this too high -- we don't know what else
   * is running on this machine during tests.
   */
  private static final double MIN_BUSY_CPU_RATIO = 0.10;

  private ThreadGroup threadGroup;

  @Before
  public void setUp() throws Exception {
    threadGroup = new ThreadGroup("test");
  }

  @Test
  public void testAvailable() {
    // Just check that JMX is available.  If it's not, this test
    // isn't very useful.
    assertThat(JmxThreadGroupCpuTimer.isAvailable()).isTrue();
  }

  @Test
  @SuppressWarnings("CatchAndPrintStackTrace")
  public void testSleep() throws Exception {
    final JmxThreadGroupCpuTimer cpuTimer = new JmxThreadGroupCpuTimer(threadGroup);
    assertThat(cpuTimer.getNanoseconds()).isEqualTo(0L);
    cpuTimer.start();
    runAndWait(
        threadGroup,
        () -> {
          try {
            Thread.sleep(TEST_TIME);
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
          cpuTimer.update();
        });
    cpuTimer.stop();
    assertWithMessage("Spent %s ns while sleeping.", cpuTimer.getNanoseconds())
        .that((double) cpuTimer.getNanoseconds())
        .isAtMost(TEST_TIME * MILLIS_TO_NANOS * MAX_SLEEP_CPU_RATIO);
  }

  @Test
  public void testBusyLoop() throws Exception {
    final JmxThreadGroupCpuTimer cpuTimer = new JmxThreadGroupCpuTimer(threadGroup);
    cpuTimer.start();
    runAndWait(
        threadGroup,
        () -> {
          busyLoop();
          cpuTimer.update(); // Required!
        });
    cpuTimer.stop();
    assertWithMessage("Only spent %s ns while looping.", cpuTimer.getNanoseconds())
        .that((double) cpuTimer.getNanoseconds())
        .isAtLeast(TEST_TIME * MILLIS_TO_NANOS * MIN_BUSY_CPU_RATIO);
  }

  private void busyLoop() {
    long stop = System.nanoTime() + TEST_TIME * 1_000_000;
    int pow = 2;
    while (System.nanoTime() < stop) {
      double unusedPiPower = Math.pow(Math.PI, pow++);
    }
  }

  @SuppressWarnings("CatchAndPrintStackTrace")
  private void runAndWait(ThreadGroup group, Runnable runnable) {
    Thread thread = new Thread(group, runnable);
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }
  }
}
