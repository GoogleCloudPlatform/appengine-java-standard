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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for WallclockTimer.
 *
 */
@RunWith(JUnit4.class)
public class WallclockTimerTest {
  private static final long TEST_TIME = 2000;

  @Test
  public void testSleep() throws Exception {
    WallclockTimer timer = new WallclockTimer();
    assertThat(timer.getNanoseconds()).isEqualTo(0L);
    timer.start();
    Thread.sleep(TEST_TIME);
    timer.stop();

    // Test that we counted the amount of time we slept, to within 100ms.
    assertThat(timer.getNanoseconds() / 1000000.0).isWithin(100).of(TEST_TIME);
  }
}
