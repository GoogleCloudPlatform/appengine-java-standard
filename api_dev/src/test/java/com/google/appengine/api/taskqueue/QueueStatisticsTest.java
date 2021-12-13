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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsResponse.QueueStats;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import java.util.concurrent.Future;
import junit.framework.TestCase;

/**
 * Unit tests for {@link QueueStatistics}.
 *
 */
public class QueueStatisticsTest extends TestCase {

  static class MockQueueFetchQueueStatsApiHelper extends QueueApiHelper {
    static final String MOCK_QUEUE_NAME = "MockQueueFetchQueueStats";
    TaskQueueFetchQueueStatsRequest expectedRequest;
    TaskQueueFetchQueueStatsResponse.Builder designatedResponse;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      fail("Not expecting to get here.");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {

      assertEquals("FetchQueueStats", method);
      assertThat(request).isInstanceOf(TaskQueueFetchQueueStatsRequest.class);
      assertThat(responseProto).isInstanceOf(TaskQueueFetchQueueStatsResponse.class);

      TaskQueueFetchQueueStatsRequest statsRequest =
          (TaskQueueFetchQueueStatsRequest) (Object) request;
      assertEquals(expectedRequest.getMaxNumTasks(), statsRequest.getMaxNumTasks());
      assertEquals(expectedRequest.getQueueNameCount(), statsRequest.getQueueNameCount());
      for (int i = 0; i < expectedRequest.getQueueNameCount(); ++i) {
        assertEquals(expectedRequest.getQueueName(i), statsRequest.getQueueName(i));
      }
      TaskQueueFetchQueueStatsResponse statsResponse =
          ((TaskQueueFetchQueueStatsResponse) responseProto)
              .toBuilder().mergeFrom(designatedResponse.build()).build();
      @SuppressWarnings("unchecked")
      T response = (T) statsResponse;
      return immediateFuture(response);
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    MockQueueFetchQueueStatsApiHelper(
        TaskQueueFetchQueueStatsRequest fetchQueueStatsRequest,
        TaskQueueFetchQueueStatsResponse.Builder fetchQueueStatsResponse) {
      this.expectedRequest = fetchQueueStatsRequest;
      this.designatedResponse = fetchQueueStatsResponse;
    }
  }

  MockQueueFetchQueueStatsApiHelper newCompleteFetchQueueStatsRequest() {
    TaskQueueFetchQueueStatsRequest.Builder request = TaskQueueFetchQueueStatsRequest.newBuilder();
    TaskQueueFetchQueueStatsResponse.Builder response =
        TaskQueueFetchQueueStatsResponse.newBuilder();
    TaskQueueFetchQueueStatsResponse.QueueStats.Builder stats =
        TaskQueueFetchQueueStatsResponse.QueueStats.newBuilder();
    TaskQueueScannerQueueInfo.Builder scannerInfo = TaskQueueScannerQueueInfo.newBuilder();

    stats.setNumTasks(123);
    stats.setOldestEtaUsec(4567890123456L);

    scannerInfo.setExecutedLastMinute(5678901234567L);
    scannerInfo.setExecutedLastHour(6789012345678L);
    scannerInfo.setRequestsInFlight(234);
    scannerInfo.setEnforcedRate(89.0123);
    scannerInfo.setSamplingDurationSeconds(0.0);
    stats.setScannerInfo(scannerInfo);

    request.addQueueName(
        ByteString.copyFromUtf8(MockQueueFetchQueueStatsApiHelper.MOCK_QUEUE_NAME));
    response.addQueueStats(stats);
    return new MockQueueFetchQueueStatsApiHelper(request.build(), response);
  }

  public void testCompleteFetchQueueStats() {
    MockQueueFetchQueueStatsApiHelper helper = newCompleteFetchQueueStatsRequest();

    QueueStatistics stats = helper.getQueue().fetchStatistics();

    assertEquals(MockQueueFetchQueueStatsApiHelper.MOCK_QUEUE_NAME, stats.getQueueName());
    assertEquals(123, stats.getNumTasks());
    assertEquals((Long) 4567890123456L, stats.getOldestEtaUsec());
    assertEquals(5678901234567L, stats.getExecutedLastMinute());
    assertEquals(6789012345678L, stats.getExecutedLastHour());
    assertEquals(234, stats.getRequestsInFlight());
    assertEquals(89.0123, stats.getEnforcedRate(), 0.0);
  }

  MockQueueFetchQueueStatsApiHelper newMinimalFetchQueueStatsRequest() {
    TaskQueueFetchQueueStatsRequest.Builder request = TaskQueueFetchQueueStatsRequest.newBuilder();
    TaskQueueFetchQueueStatsResponse.Builder response =
        TaskQueueFetchQueueStatsResponse.newBuilder();
    QueueStats stats =
        TaskQueueFetchQueueStatsResponse.QueueStats.newBuilder()
            .setNumTasks(1)
            .setOldestEtaUsec(4L)
            .build();

    request.addQueueName(
        ByteString.copyFromUtf8(MockQueueFetchQueueStatsApiHelper.MOCK_QUEUE_NAME));
    response.addQueueStats(stats);
    return new MockQueueFetchQueueStatsApiHelper(request.build(), response);
  }

  public void testMinimalFetchQueueStats() {
    MockQueueFetchQueueStatsApiHelper helper = newMinimalFetchQueueStatsRequest();

    try {
      helper.getQueue().fetchStatistics();
      fail("TransientFailureException should be thrown");
    } catch (TransientFailureException e) {
      // Expected
    }
  }
}
