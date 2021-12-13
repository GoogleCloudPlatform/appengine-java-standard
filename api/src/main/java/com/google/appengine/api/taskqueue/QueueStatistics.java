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

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * {@link QueueStatistics} allow observation of the rate that tasks on a given queue are being
 * executed. Note that statistics provided are only approximate, and some statistics may be delayed
 * or transiently unavailable.
 *
 */
public final class QueueStatistics {
  private final String queueName;
  private final int numTasks;
  private final Long oldestEtaUsec;
  private final long executedLastMinute;
  private final long executedLastHour;
  private final int requestsInFlight;
  private final double enforcedRate;

  /**
   * Constructs QueueStatistics data object.
   *
   * @throws IllegalArgumentException Complete queue statistics were not provided.
   */
  QueueStatistics(String queueName, TaskQueueFetchQueueStatsResponse.QueueStats stats) {
    this.queueName = queueName;
    this.numTasks = stats.getNumTasks();
    if (stats.getOldestEtaUsec() != -1) {
      this.oldestEtaUsec = stats.getOldestEtaUsec();
    } else {
      this.oldestEtaUsec = null;
    }

    if (stats.hasScannerInfo()) {
      TaskQueueScannerQueueInfo scannerInfo = stats.getScannerInfo();

      this.executedLastMinute = scannerInfo.getExecutedLastMinute();
      this.executedLastHour = scannerInfo.getExecutedLastHour();
      if (scannerInfo.hasRequestsInFlight()) {
        this.requestsInFlight = scannerInfo.getRequestsInFlight();
      } else {
        throw new IllegalArgumentException("Queue statistics not supplied");
      }
      if (scannerInfo.hasEnforcedRate()) {
        this.enforcedRate = scannerInfo.getEnforcedRate();
      } else {
        throw new IllegalArgumentException("Queue statistics not supplied");
      }
    } else {
      throw new IllegalArgumentException("Queue statistics not supplied");
    }
  }

  /** Returns the name of the {@link Queue}. */
  public String getQueueName() {
    return queueName;
  }

  /** Returns the approximate number of non-completed tasks in the queue. */
  public int getNumTasks() {
    return numTasks;
  }

  /**
   * Returns a recent estimate of the eta of the oldest non-completed task in the queue.
   *
   * @return The eta of the oldest non-completed task for the queue, or {@code null} if there were
   *     no non-completed tasks found in the queue.
   */
  public Long getOldestEtaUsec() {
    return oldestEtaUsec;
  }

  /** Returns the number of tasks executed in the last minute. */
  public long getExecutedLastMinute() {
    return executedLastMinute;
  }

  /** @return The number of tasks executed in the last hour. */
  long getExecutedLastHour() {
    return executedLastHour;
  }

  /**
   * The number of requests that the queue has sent but not yet received a reply for.
   *
   * @return The number of tasks currently in flight.
   */
  public int getRequestsInFlight() {
    return requestsInFlight;
  }

  /**
   * Returns the maximum number of tasks per second being run by this queue.
   *
   * @return The enforced rate in tasks per second.
   */
  public double getEnforcedRate() {
    return enforcedRate;
  }

  /** See {@link Queue#fetchStatistics()}. */
  static Future<List<QueueStatistics>> fetchForQueuesAsync(
      final List<Queue> queues, QueueApiHelper helper, double deadlineInSeconds) {
    TaskQueueFetchQueueStatsRequest.Builder statsRequest =
        TaskQueueFetchQueueStatsRequest.newBuilder();

    for (Queue queue : queues) {
      statsRequest.addQueueName(ByteString.copyFromUtf8(queue.getQueueName()));
    }

    statsRequest.setMaxNumTasks(0);

    ApiConfig apiConfig = new ApiConfig();
    apiConfig.setDeadlineInSeconds(deadlineInSeconds);

    Future<TaskQueueFetchQueueStatsResponse> responseFuture =
        helper.makeAsyncCall(
            "FetchQueueStats",
            statsRequest.build(),
            TaskQueueFetchQueueStatsResponse.getDefaultInstance(),
            apiConfig);
    return new FutureAdapter<TaskQueueFetchQueueStatsResponse, List<QueueStatistics>>(
        responseFuture) {
      @Override
      protected List<QueueStatistics> wrap(TaskQueueFetchQueueStatsResponse statsResponse) {
        if (statsResponse.getQueueStatsCount() != queues.size()) {
          throw new QueueFailureException("Unable to obtain queue statistics");
        }

        List<QueueStatistics> resultList = new ArrayList<QueueStatistics>();
        for (int i = 0; i < statsResponse.getQueueStatsCount(); ++i) {
          TaskQueueFetchQueueStatsResponse.QueueStats stats = statsResponse.getQueueStats(i);

          if (!stats.hasScannerInfo()) {
            throw new TransientFailureException("Queue statistics temporarily unavailable");
          }
          TaskQueueScannerQueueInfo scannerInfo = stats.getScannerInfo();
          if (!scannerInfo.hasRequestsInFlight()) {
            throw new TransientFailureException("Queue statistics temporarily unavailable");
          }
          if (!scannerInfo.hasEnforcedRate()) {
            throw new TransientFailureException("Queue statistics temporarily unavailable");
          }

          resultList.add(new QueueStatistics(queues.get(i).getQueueName(), stats));
        }
        return resultList;
      }
    };
  }
}
