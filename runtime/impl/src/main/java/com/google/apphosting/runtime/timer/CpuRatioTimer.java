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

import java.util.Arrays;

/**
 * {@code CpuRatioTimer} is a composite {@code Timer} that is backed
 * by two other {@code Timer} objects -- e.g. one that measures CPU
 * time and one that measures wallclock time.  When started or
 * stopped, it will start and stop both underlying {@code Timer}
 * objects.
 *
 * It also exposes additional methods to calculate a CPU usage ratio
 * and cycle count (calculated from the CPU time and a {@code
 * CpuSpeedExtractor}).
 *
 */
public class CpuRatioTimer implements Timer {
  private final Timer cpuUsageTimer;
  private final Timer wallclockTimer;
  private final CpuSpeedExtractor cpuSpeedExtractor;
  private final Timer[] extraTimers;

  /**
   * Create a new {@link CpuRatioTimer}.
   *
   * @param cpuUsageTimer Tracks the CPU time used by the request.
   * @param wallclockTimer Tracks the total elapsed time of the request.
   * @param cpuSpeedExtractor converts CPU time to CPU cycles
   * @param extraTimers includes timers for background work that is
   * added to the total return value of this timer (but do not affect
   * the CPU ratio).
   */
  public CpuRatioTimer(
      Timer cpuUsageTimer,
      Timer wallclockTimer,
      CpuSpeedExtractor cpuSpeedExtractor,
      Timer[] extraTimers) {
    this.cpuUsageTimer = cpuUsageTimer;
    this.wallclockTimer = wallclockTimer;
    this.cpuSpeedExtractor = cpuSpeedExtractor;
    this.extraTimers = extraTimers;
  }

  /**
   * Start both timers.
   */
  @Override
  public void start() {
    cpuUsageTimer.start();
    wallclockTimer.start();
    for (Timer timer : extraTimers) {
      timer.start();
    }
  }

  /**
   * Stop both timers.
   */
  @Override
  public void stop() {
    cpuUsageTimer.stop();
    wallclockTimer.stop();
    for (Timer timer : extraTimers) {
      timer.stop();
    }
  }

  @Override
  public void update() {
    cpuUsageTimer.update();
    wallclockTimer.update();
    for (Timer timer : extraTimers) {
      timer.update();
    }
  }

  /**
   * Returns the underlying CPU usage {@code Timer}.
   */
  public Timer getCpuUsageTimer() {
    return cpuUsageTimer;
  }

  /**
   * Returns the underlying wallclock {@code Timer}.
   */
  public Timer getWallclockTimer() {
    return wallclockTimer;
  }

  /**
   * Returns a ratio (between 0 and 1) that represents the percentage
   * of elapsed wallclock time which was spent executing CPU
   * instructions.
   */
  public double getCpuRatio() {
    double cpuNanos = cpuUsageTimer.getNanoseconds();
    double wallclockNanos = wallclockTimer.getNanoseconds();
    return cpuNanos / wallclockNanos;
  }

  /**
   * Convert the number of CPU seconds elapsed into a CPU cycle count
   * using the CPU speed reported by the {@code CpuSpeedExtractor}.
   * This value also includes a fraction of hotspot and GC times.
   */
  public long getCycleCount() {
    double seconds = getNanoseconds() / 1000000000.0;
    return (long) (seconds * cpuSpeedExtractor.getCyclesPerSecond());
  }

  /**
   * Returns the number of CPU-nanoseconds used by the current
   * request, plus a fraction of any background work (e.g.  hotspot,
   * GC) done by the JVM while this request was executing.
   */
  @Override
  public long getNanoseconds() {
    long total = cpuUsageTimer.getNanoseconds();
    for (Timer timer : extraTimers) {
      total += timer.getNanoseconds();
    }
    return total;
  }

  @Override
  public String toString() {
    double cpuSeconds = cpuUsageTimer.getNanoseconds() / 1000000000.0;
    double wallclockSeconds = wallclockTimer.getNanoseconds() / 1000000000.0;
    StringBuilder builder =
        new StringBuilder(
            String.format(
                "%.3f CPU / %.3f wallclock (%.1f%%), %.3f Mcycles@%.1fGHz",
                cpuSeconds,
                wallclockSeconds,
                100.0 * getCpuRatio(),
                getCycleCount() / 1000000.0,
                cpuSpeedExtractor.getCyclesPerSecond() / 1000000000.0));
    if (extraTimers.length > 0) {
      builder.append(Arrays.toString(extraTimers));
    }
    return builder.toString();
  }
}
