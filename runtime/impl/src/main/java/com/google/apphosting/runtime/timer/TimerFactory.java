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

import com.google.common.flogger.GoogleLogger;

/**
 * {@code TimerFactory} creates new {@code Timer} instances.
 *
 */
public class TimerFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final TimerSet[] timerSets;
  private final CpuSpeedExtractorFactory cpuSpeedExtractorFactory;
  private final boolean isCpuTimerAvailable;

  /**
   * Create a {@link TimerFactory}.
   *
   * @param cyclesPerSecond Speed of the processor in clock cycles per second
   * @param timerSets Zero or more {@link TimerSet} instances that
   * will become additional timers associated with the returned
   * {@link CpuRatioTimer}.  This is useful for tracking CPU usage
   * outside of a request thread, such as hotspot or GC.
   */
  public TimerFactory(long cyclesPerSecond, TimerSet... timerSets) {
    this.timerSets = timerSets;
    this.cpuSpeedExtractorFactory = new CpuSpeedExtractorFactory(cyclesPerSecond);

    if (JmxCpuTimer.isAvailable()) {
      isCpuTimerAvailable = true;
      logger.atInfo().log("JMX CPU timing information is available.");
    } else {
      isCpuTimerAvailable = false;
      logger.atWarning().log(
          "JMX CPU timing information is NOT available, wallclock time will be used instead!");
    }
  }

  /**
   * Return a {@code Timer} that counts wallclock time.
   */
  public Timer getWallclockTimer() {
    return new WallclockTimer();
  }

  /**
   * Return a {@code Timer} that counts CPU time for the specified
   * {@code Thread}.
   *
   * @throws UnsupportedOperationException If no CPU timer is available.
   */
  public Timer getCpuCycleTimer(Thread thread) {
    if (isCpuTimerAvailable) {
      return new JmxCpuTimer(thread);
    } else {
      throw new UnsupportedOperationException("JMX CPU timing not available.");
    }
  }

  public Timer getThreadGroupCpuTimer(ThreadGroup threadGroup) {
    if (isCpuTimerAvailable) {
      return new JmxThreadGroupCpuTimer(threadGroup);
    } else {
      throw new UnsupportedOperationException("JMX CPU timing not available.");
    }
  }

  /**
   * Returns a {@code CpuRatioTimer} that tracks both the CPU usage of
   * the specified {@code Thread} and the wallclock time.
   *
   * @throws UnsupportedOperationException If thread CPU timing is not
   * available.
   */
  public CpuRatioTimer getCpuRatioTimer(Thread thread) {
    Timer[] timers = new Timer[timerSets.length];
    for (int i = 0; i < timerSets.length; i++) {
      timers[i] = timerSets[i].createTimer();
    }
    return new CpuRatioTimer(
        getCpuCycleTimer(thread),
        getWallclockTimer(),
        cpuSpeedExtractorFactory.getExtractor(),
        timers);
  }

  /**
   * Returns a {@code CpuRatioTimer} that tracks both the CPU usage of
   * the specified {@code ThreadGroup} and the wallclock time.
   *
   * @throws UnsupportedOperationException If thread CPU timing is not
   * available.
   */
  public CpuRatioTimer getCpuRatioTimer(ThreadGroup threadGroup) {
    Timer[] timers = new Timer[timerSets.length];
    for (int i = 0; i < timerSets.length; i++) {
      timers[i] = timerSets[i].createTimer();
    }
    return new CpuRatioTimer(
        getThreadGroupCpuTimer(threadGroup),
        getWallclockTimer(),
        cpuSpeedExtractorFactory.getExtractor(),
        timers);
  }
}
