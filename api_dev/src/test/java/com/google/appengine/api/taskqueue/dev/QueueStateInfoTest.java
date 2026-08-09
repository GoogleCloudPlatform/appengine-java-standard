/*
 * Copyright 2026 Google LLC
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

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest.RequestMethod;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.tools.development.Clock;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class QueueStateInfoTest {
  private static final Clock CLOCK = () -> 1000L;

  @Test
  public void testGetMethod_pullMode_returnsPull() {
    TaskQueueAddRequest.Builder addRequest = TaskQueueAddRequest.newBuilder().setMode(Mode.PULL);

    QueueStateInfo.TaskStateInfo taskInfo =
        new QueueStateInfo.TaskStateInfo("task1", 1000L, addRequest, CLOCK);

    assertThat(taskInfo.getMethod()).isEqualTo("PULL");
  }

  @Test
  public void testGetMethod_pushMode_returnsRequestMethodName(
      @TestParameter RequestMethod requestMethod) {
    TaskQueueAddRequest.Builder addRequest =
        TaskQueueAddRequest.newBuilder().setMode(Mode.PUSH).setMethod(requestMethod);

    QueueStateInfo.TaskStateInfo taskInfo =
        new QueueStateInfo.TaskStateInfo("task1", 1000L, addRequest, CLOCK);

    assertThat(taskInfo.getMethod()).isEqualTo(requestMethod.name());
  }

  @Test
  public void testGetMethod_modeUnset_returnsRequestMethodName(
      @TestParameter RequestMethod requestMethod) {
    TaskQueueAddRequest.Builder addRequest =
        TaskQueueAddRequest.newBuilder().setMethod(requestMethod);

    QueueStateInfo.TaskStateInfo taskInfo =
        new QueueStateInfo.TaskStateInfo("task1", 1000L, addRequest, CLOCK);

    assertThat(taskInfo.getMethod()).isEqualTo(requestMethod.name());
  }
}
