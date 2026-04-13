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

package com.google.appengine.api.taskqueue.dev;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.apphosting.utils.config.QueueXml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UrlFetchJobDetailTest {

  @Test
  public void testIncrementRetryDelay() {
    LocalTaskQueueCallback callback = mock(LocalTaskQueueCallback.class);
    UrlFetchJobDetail jd =
        new UrlFetchJobDetail(
            "task 1", "queue 1", null, null, callback, QueueXml.defaultEntry(), null);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(100);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(200);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(400);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(800);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(1600);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(3200);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(6400);
  }

  @Test
  public void testIncrementRetryDelayWithMaxBackoff() {
    LocalTaskQueueCallback callback = mock(LocalTaskQueueCallback.class);
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMaxBackoffSec(2).build();
    UrlFetchJobDetail jd =
        new UrlFetchJobDetail(
            "task 1", "queue 1", null, null, callback, QueueXml.defaultEntry(), retryParams);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(100);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(200);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(400);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(800);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(1600);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(2000);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(2000);
  }

  @Test
  public void testIncrementRetryDelayWithMinBackoff() {
    LocalTaskQueueCallback callback = mock(LocalTaskQueueCallback.class);
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMinBackoffSec(1).build();
    UrlFetchJobDetail jd =
        new UrlFetchJobDetail(
            "task 1", "queue 1", null, null, callback, QueueXml.defaultEntry(), retryParams);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(1000);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(2000);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(4000);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(8000);
  }

  @Test
  public void testIncrementRetryDelayWithMaxDoublings() {
    LocalTaskQueueCallback callback = mock(LocalTaskQueueCallback.class);
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMaxDoublings(3).build();
    UrlFetchJobDetail jd =
        new UrlFetchJobDetail(
            "task 1", "queue 1", null, null, callback, QueueXml.defaultEntry(), retryParams);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(100);
    // 3 doublings follow.
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(200);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(400);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(800);
    // From now on it's linear, so always adding 800.
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(1600);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(2400);
    assertThat(jd.incrementRetryDelayMs()).isEqualTo(3200);
  }
}
