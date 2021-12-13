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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for AbstractIntervalTimer.
 *
 */
@RunWith(JUnit4.class)
public class AbstractIntervalTimerTest {
  private long currentTime;
  private AbstractIntervalTimer timer;

  @Before
  public void setUp() {
    currentTime = 0L;
    timer =
        new AbstractIntervalTimer() {
          @Override
          public long getCurrent() {
            return currentTime;
          }
        };
  }

  @Test
  public void testNotStarted() throws Exception {
    assertThat(timer.getNanoseconds()).isEqualTo(0L);
  }

  @Test
  public void testStartStop() throws Exception {
    timer.start();
    currentTime = 3L;
    timer.stop();
    assertThat(timer.getNanoseconds()).isEqualTo(3L);
  }

  @Test
  public void testStartedLate() throws Exception {
    currentTime = 5L;
    timer.start();
    currentTime = 6L;
    timer.stop();
    assertThat(timer.getNanoseconds()).isEqualTo(1L);
  }

  @Test
  public void testStillRunning() throws Exception {
    timer.start();
    currentTime = 2L;
    assertThat(timer.getNanoseconds()).isEqualTo(2L);
  }

  @Test
  public void testStartStopTwice() throws Exception {
    timer.start();
    currentTime = 3L;
    timer.stop();
    currentTime = 5L;
    timer.start();
    currentTime = 10L;
    timer.stop();
    assertThat(timer.getNanoseconds()).isEqualTo(8L);
  }
}
