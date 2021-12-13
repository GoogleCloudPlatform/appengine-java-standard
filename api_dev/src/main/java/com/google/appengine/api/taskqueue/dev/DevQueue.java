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

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueRetryParameters;
import com.google.apphosting.utils.config.QueueXml.Entry;
import com.google.apphosting.utils.config.QueueXml.RetryParameters;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Base class for dev server task queue.
 *
 * <p>This class provides common methods and interface for both PUSH and PULL queues.
 *
 * <p>
 *
 */
abstract class DevQueue {
  protected static final Logger logger = Logger.getLogger(DevQueue.class.getName());

  protected final Entry queueXmlEntry;
  // Only for testing.
  static AtomicInteger taskNameGenerator = null;

  DevQueue(Entry queueXmlEntry) {
    this.queueXmlEntry = queueXmlEntry;
  }

  static String genTaskName() {
    if (taskNameGenerator != null) {
      return "task" + taskNameGenerator.incrementAndGet();
    }
    return "task-" + UUID.randomUUID();
  }

  /** Adds tasks to a queue. We still need a Builder as we might need to generate the task name. */
  abstract TaskQueueAddResponse add(TaskQueueAddRequest.Builder addRequest);

  protected String getQueueName() {
    return queueXmlEntry.getName();
  }

  protected TaskQueueRetryParameters getRetryParameters(TaskQueueAddRequest.Builder addRequest) {
    // The add request's retry parameters take precedence over the queue's.
    if (addRequest.hasRetryParameters()) {
      return addRequest.getRetryParameters();
    }
    RetryParameters retryParams = queueXmlEntry.getRetryParameters();
    if (retryParams == null) {
      return null;
    }

    TaskQueueRetryParameters.Builder paramsPb = TaskQueueRetryParameters.newBuilder();
    if (retryParams.getRetryLimit() != null) {
      paramsPb.setRetryLimit(retryParams.getRetryLimit());
    }
    if (retryParams.getAgeLimitSec() != null) {
      paramsPb.setAgeLimitSec(retryParams.getAgeLimitSec());
    }
    if (retryParams.getMinBackoffSec() != null) {
      paramsPb.setMinBackoffSec(retryParams.getMinBackoffSec());
    }
    if (retryParams.getMaxBackoffSec() != null) {
      paramsPb.setMaxBackoffSec(retryParams.getMaxBackoffSec());
    }
    if (retryParams.getMaxDoublings() != null) {
      paramsPb.setMaxDoublings(retryParams.getMaxDoublings());
    }
    return paramsPb.build();
  }

  /** Returns a QueueStateInfo describing the current state of this queue. */
  abstract QueueStateInfo getStateInfo();

  /**
   * Delete a task by name.
   *
   * @return false if task was not found.
   */
  abstract boolean deleteTask(String taskName);

  /** Deletes all tasks in the queue (group) */
  abstract void flush();

  /**
   * Gets mode of a queue. This is used to distinguish whether a DevQueue object is pull or push.
   */
  abstract Mode getMode();

  /**
   * Run a task by name.
   *
   * @return false if task was not found or could not be executed. Note that the return value is not
   *     in any way related to the success or failure of the task. If we can find the task and we
   *     can initiate its execution we will return true even if the execution yields an exception.
   */
  abstract boolean runTask(String taskName);
}
