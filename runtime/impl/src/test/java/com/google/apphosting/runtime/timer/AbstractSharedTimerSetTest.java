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
 * Unit tests for AbstractSharedTimerSet.
 *
 */
@RunWith(JUnit4.class)
public class AbstractSharedTimerSetTest {
  private long current1;
  private long current2;
  private AbstractSharedTimerSet set1;
  private AbstractSharedTimerSet set2;
  private Timer timer1a;
  private Timer timer1b;
  private Timer timer2a;

  @Before
  public void setUp() {
    current1 = current2 = 0L;
    set1 =
        new AbstractSharedTimerSet() {
          @Override
          public long getCurrentShared() {
            return current1;
          }

          @Override
          public String getTitle() {
            return "set1";
          }
        };
    timer1a = set1.createTimer();
    timer1b = set1.createTimer();
    set2 =
        new AbstractSharedTimerSet() {
          @Override
          public long getCurrentShared() {
            return current2;
          }

          @Override
          public String getTitle() {
            return "set2";
          }
        };
    timer2a = set2.createTimer();
  }

  @Test
  public void testNotStarted() throws Exception {
    assertThat(timer1a.getNanoseconds()).isEqualTo(0L);
  }

  @Test
  public void testStartStop() throws Exception {
    timer1a.start();
    current1 = 5L;
    timer1a.stop();
    assertThat(timer1a.getNanoseconds()).isEqualTo(5L);
  }

  @Test
  public void testStartStopTwo() throws Exception {
    timer1a.start();
    timer1b.start();
    current1 = 6L;
    timer1a.stop();
    timer1b.stop();
    assertThat(timer1a.getNanoseconds()).isEqualTo(3L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(3L);
  }

  @Test
  public void testInterleaved() throws Exception {
    current1 = 3L;
    assertThat(timer1a.getNanoseconds()).isEqualTo(0L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(0L);
    timer1a.start();
    current1 = 4L;
    assertThat(timer1a.getNanoseconds()).isEqualTo(1L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(0L);
    timer1b.start();
    current1 = 8L;
    assertThat(timer1a.getNanoseconds()).isEqualTo(3L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(2L);
    timer1b.stop();
    current1 = 10L;
    assertThat(timer1a.getNanoseconds()).isEqualTo(5L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(2L);
    timer1a.stop();
    assertThat(timer1a.getNanoseconds()).isEqualTo(5L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(2L);
  }

  @Test
  public void testStartStopTwoDifferentSets() throws Exception {
    timer1a.start();
    timer2a.start();
    current1 = 6L;
    current2 = 10L;
    timer1a.stop();
    timer2a.stop();
    assertThat(timer1a.getNanoseconds()).isEqualTo(6L);
    assertThat(timer1b.getNanoseconds()).isEqualTo(0L);
    assertThat(timer2a.getNanoseconds()).isEqualTo(10L);
  }
}
