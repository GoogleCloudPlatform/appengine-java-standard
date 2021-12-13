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

package com.google.appengine.api.taskqueue;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Describes various taskqueue limits.
 *
 */
// see apphosting/api/taskqueue/taskqueue_limits.h
public final class QueueConstants {
  // TODO These should not be constants.  These values
  // are specified as flags in apphosting/api/taskqueue/taskqueue_limits.h
  // hence making those flags useless if they're hard coded here.
  // Best to initialize these values with the settings in TaskQueueLimits.

  // com.google.apphosting.utils.config.QueueXml may contain a copy of some of
  // these constants.  Keep those in sync.
  private static final int MAX_QUEUE_NAME_LENGTH = 100;
  private static final int MAX_TASK_NAME_LENGTH = 500;
  private static final int MAX_TASK_TAG_LENGTH = 500;
  private static final int MAX_PUSH_TASK_SIZE_BYTES = 100 * (1 << 10); // 100KB
  private static final int MAX_PULL_TASK_SIZE_BYTES = (1 << 20); // 1MB
  private static final int MAX_TRANSACTIONAL_REQUEST_SIZE_BYTES = (1 << 20); // 1MB
  private static final int MAX_TASKS_PER_ADD = 100;
  private static final int MAX_URL_LENGTH = 2083;
  private static final long MAX_ETA_DELTA_MILLIS = 30L * 24 * 60 * 60 * 1000;

  // The following constants are defined in apphosting/executor/server/server.cc as flags.
  private static final long MAX_LEASE_MILLIS = 3600L * 24 * 7 * 1000; // 1 week
  private static final long MAX_TASKS_PER_LEASE = 1000;

  // These are also used in the dev taskqueue implementation so they're public.
  /** Regular expression that matches all valid task names. */
  public static final String TASK_NAME_REGEX = "[a-zA-Z\\d_-]{1," + maxTaskNameLength() + "}";

  public static final Pattern TASK_NAME_PATTERN = Pattern.compile(TASK_NAME_REGEX);
  /** Regular expression that matches all valid queue names. */
  public static final String QUEUE_NAME_REGEX = "[a-zA-Z\\d-]{1," + maxQueueNameLength() + "}";

  public static final Pattern QUEUE_NAME_PATTERN = Pattern.compile(QUEUE_NAME_REGEX);

  /** Returns the maximum length of period to lease a task. */
  public static long maxLease(TimeUnit unit) {
    return unit.convert(MAX_LEASE_MILLIS, TimeUnit.MILLISECONDS);
  }

  /** Returns the maximum number of tasks to lease in one call. */
  public static long maxLeaseCount() {
    return MAX_TASKS_PER_LEASE;
  }

  /** Returns the maximum length of a queue name. */
  public static int maxQueueNameLength() {
    return MAX_QUEUE_NAME_LENGTH;
  }

  /** Returns the maximum length of a task name. */
  public static int maxTaskNameLength() {
    return MAX_TASK_NAME_LENGTH;
  }

  /** Returns the maximum length of a task tag. */
  public static int maxTaskTagLength() {
    return MAX_TASK_TAG_LENGTH;
  }

  /**
   * Returns the maximum push task size.
   *
   * @deprecated Use {@link #maxPushTaskSizeBytes()}
   */
  @Deprecated
  public static int maxTaskSizeBytes() {
    return maxPushTaskSizeBytes();
  }

  /** Returns the maximum push task size. */
  public static int maxPushTaskSizeBytes() {
    return MAX_PUSH_TASK_SIZE_BYTES;
  }

  /** Returns the maximum pull task size. */
  public static int maxPullTaskSizeBytes() {
    return MAX_PULL_TASK_SIZE_BYTES;
  }

  // Max size of a bulk add transactional request.
  static int maxTransactionalRequestSizeBytes() {
    return MAX_TRANSACTIONAL_REQUEST_SIZE_BYTES;
  }

  /** Returns the maximum number of tasks that may be passed to a single add call. */
  public static int maxTasksPerAdd() {
    return MAX_TASKS_PER_ADD;
  }

  /** Returns the maximum URL length. */
  public static int maxUrlLength() {
    return MAX_URL_LENGTH;
  }

  /** Returns the maximum time into the future that a task may be scheduled. */
  public static long getMaxEtaDeltaMillis() {
    return MAX_ETA_DELTA_MILLIS;
  }

  private QueueConstants() {}
}
