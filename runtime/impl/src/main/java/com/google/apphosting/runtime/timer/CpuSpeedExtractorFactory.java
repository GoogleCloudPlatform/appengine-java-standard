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
 * Creates a {@link CpuSpeedExtractor} that can be used to
 * determine the speed of the CPUs on the current machine.
 *
 */
public class CpuSpeedExtractorFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * If we can't extract the CPU speed, simply assume that all
   * processors are running at 2 GHz.
   */
  private static final long DEFAULT_CPU_SPEED = 2000000000L;

  private final CpuSpeedExtractor extractor;

  public CpuSpeedExtractorFactory(long cyclesPerSecond) {
    if (cyclesPerSecond > 0) {
      this.extractor = new StaticCpuSpeedExtractor(cyclesPerSecond);
    } else {
      // TODO: If this happens in production we should
      // probably kill the server.  Add a flag that will make this
      // error fatal.
      logger.atWarning().log(
          "Flag --cycles_per_second not set, assuming a default CPU speed of %d",
          DEFAULT_CPU_SPEED);
      this.extractor = new StaticCpuSpeedExtractor(DEFAULT_CPU_SPEED);
    }
  }

  /**
   * Returns the {@code CpuSpeedExtractor}.
   */
  public CpuSpeedExtractor getExtractor() {
    return extractor;
  }

  /**
   * Always returns a specific CPU speed.
   */
  private static class StaticCpuSpeedExtractor implements CpuSpeedExtractor {
    private final long cpuSpeed;

    public StaticCpuSpeedExtractor(long cpuSpeed) {
      this.cpuSpeed = cpuSpeed;
    }

    @Override
    public long getCyclesPerSecond() {
      return cpuSpeed;
    }
  }
}
