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

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.apphosting.utils.config.QueueXml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UrlFetchJobDetailTest {

  private UrlFetchJobDetail createJobData(TaskQueueRetryParameters retryParams) {
    LocalTaskQueueCallback callback = mock(LocalTaskQueueCallback.class);
    TaskQueueAddRequest.Builder addRequest = TaskQueueAddRequest.newBuilder();
    if (retryParams != null) {
      addRequest.setRetryParameters(retryParams);
    }

    return new UrlFetchJobDetail(
        "task 1",
        "queue 1",
        addRequest,
        "http://localhost:8080",
        callback,
        QueueXml.defaultEntry(),
        retryParams);
  }

  @Test
  public void testIncrementRetryDelay() {
    UrlFetchJobDetail jd = createJobData(null);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(100);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(200);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(400);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(800);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(1600);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(3200);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(6400);
  }

  @Test
  public void testIncrementRetryDelayWithMaxBackoff() {
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMaxBackoffSec(2).build();
    UrlFetchJobDetail jd = createJobData(retryParams);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(100);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(200);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(400);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(800);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(1600);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(2000);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(2000);
  }

  @Test
  public void testIncrementRetryDelayWithMinBackoff() {
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMinBackoffSec(1).build();
    UrlFetchJobDetail jd = createJobData(retryParams);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(1000);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(2000);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(4000);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(8000);
  }

  @Test
  public void testIncrementRetryDelayWithMaxDoublings() {
    TaskQueueRetryParameters retryParams =
        TaskQueueRetryParameters.newBuilder().setMaxDoublings(3).build();
    UrlFetchJobDetail jd = createJobData(retryParams);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(100);
    // 3 doublings follow.
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(200);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(400);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(800);
    // From now on it's linear, so always adding 800.
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(1600);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(2400);
    jd.incrementRetryDelayMs();
    assertThat(jd.getRetryDelayMs()).isEqualTo(3200);
  }
}
