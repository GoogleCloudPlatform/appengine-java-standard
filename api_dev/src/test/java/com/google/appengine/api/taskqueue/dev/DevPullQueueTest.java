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
import static junit.framework.TestCase.assertTrue;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests for local dev pull queue.
 *
 */
public class DevPullQueueTest extends TestCase {
  private final MockClock clock = new MockClock();
  private static final String PULL_QUEUE_NAME = "pullQ";
  private DevPullQueue queue;

  private static class MockClock implements Clock {
    private long mutableTimeMillis = 0;

    @Override
    public long getCurrentTime() {
      return mutableTimeMillis;
    }

    public void setTimeMillis(long timeMillis) {
      mutableTimeMillis = timeMillis;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    QueueXml.Entry entry = new QueueXml.Entry();
    entry.setName(PULL_QUEUE_NAME);
    entry.setMode("pull");
    queue = new DevPullQueue(entry, clock);
  }

  private TaskQueueAddRequest.Builder newAddRequest(long etaMillis) {
    return TaskQueueAddRequest.newBuilder()
        .setMode(Mode.PULL)
        .setQueueName(ByteString.copyFromUtf8(PULL_QUEUE_NAME))
        .setEtaUsec(etaMillis * 1000L)
        .setBody(ByteString.copyFromUtf8("payload-data"));
  }

  public void testAdd_NamedTask() throws Exception {
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    TaskQueueAddResponse resp = queue.add(add);
    assertThat(resp.getChosenTaskName().toStringUtf8()).isEmpty();
  }

  public void testAdd_UnnamedTask() throws Exception {
    TaskQueueAddRequest.Builder add = newAddRequest(1000);
    TaskQueueAddResponse resp = queue.add(add);
    assertThat(resp.getChosenTaskName().toStringUtf8()).startsWith("task-");
  }

  public void testAddDupe() throws Exception {
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("foo"));
    queue.add(addRequest);

    try {
      queue.add(addRequest);
      fail();
    } catch (ApiProxy.ApplicationException exception) {
      // expected.
      assertEquals(ErrorCode.TASK_ALREADY_EXISTS_VALUE, exception.getApplicationError());
    }
  }

  public void testDelete() throws Exception {
    TaskQueueAddRequest.Builder addRequest = newAddRequest(1000);
    TaskQueueAddResponse response = queue.add(addRequest);
    boolean deleted = queue.deleteTask(response.getChosenTaskName().toStringUtf8());
    assertTrue(deleted);
    deleted = queue.deleteTask(response.getChosenTaskName().toStringUtf8());
    assertEquals(false, deleted);
  }

  public void testGetStateInfo() throws Exception {
    List<String> jobNames = Lists.newArrayList();
    long eta = 1000000;
    for (int i = 0; i < 10; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(eta - (i * 1000));
      String taskName = "task" + i;
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      jobNames.add(taskName);
      queue.add(addRequest);
    }

    QueueStateInfo info = queue.getStateInfo();
    assertEquals(10, info.getCountTasks());
    Iterator<String> reversedJobNames = Lists.reverse(jobNames).iterator();
    for (QueueStateInfo.TaskStateInfo taskInfo : info.getTaskInfo()) {
      assertEquals(reversedJobNames.next(), taskInfo.getTaskName());
    }
  }

  public void testFlush() {
    for (int i = 0; i < 16; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(i * 1000);
      String taskName = "task" + i;
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      queue.add(addRequest);
    }
    assertEquals(16, queue.getStateInfo().getCountTasks());
    queue.flush();
    assertEquals(0, queue.getStateInfo().getCountTasks());
  }

  private long secToMillis(double seconds) {
    return (long) (seconds * 1000);
  }

  private void assertLeaseTasksEqualsNoOrder(
      List<TaskQueueAddRequest.Builder> expected,
      List<TaskQueueAddRequest.Builder> result,
      long nowMillis,
      long leaseMillis) {
    // Sort all tasks by name
    Collections.sort(
        expected,
        new Comparator<TaskQueueAddRequest.Builder>() {
          @Override
          public int compare(TaskQueueAddRequest.Builder o1, TaskQueueAddRequest.Builder o2) {
            return o1.getTaskName().toStringUtf8().compareTo(o2.getTaskName().toStringUtf8());
          }
        });
    Collections.sort(
        result,
        new Comparator<TaskQueueAddRequest.Builder>() {
          @Override
          public int compare(TaskQueueAddRequest.Builder o1, TaskQueueAddRequest.Builder o2) {
            return o1.getTaskName().toStringUtf8().compareTo(o2.getTaskName().toStringUtf8());
          }
        });

    assertEquals(expected.size(), result.size());
    for (int i = 0; i < result.size(); ++i) {
      assertEquals(expected.get(i).getTaskName(), result.get(i).getTaskName());
      assertEquals(expected.get(i).getBody(), result.get(i).getBody());
      assertEquals(nowMillis + leaseMillis, result.get(i).getEtaUsec() / 1000L);
    }
  }

  public void testQueryAndOwnTasks() {
    List<TaskQueueAddRequest.Builder> all = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(i * 1000);
      queue.add(addRequest);
      all.add(addRequest);
    }

    clock.setTimeMillis(16000);
    double leaseSeconds = 10.0;
    long maxTasks = 100;
    List<TaskQueueAddRequest.Builder> results =
        queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all, results, 16000, secToMillis(10.0));
  }

  public void testQueryAndOwnTasksByTag() {
    List<TaskQueueAddRequest.Builder> all = new ArrayList<>();
    List<TaskQueueAddRequest.Builder> tagged = new ArrayList<>();
    List<TaskQueueAddRequest.Builder> untagged = new ArrayList<>();
    final String tag = "foo";
    for (int i = 0; i < 16; i++) {
      TaskQueueAddRequest.Builder addRequest =
          newAddRequest(i * 1000).setTaskName(ByteString.copyFromUtf8("foo" + i));
      // Even numbered tasks have tags.
      if (i % 2 == 1) {
        addRequest.setTag(ByteString.copyFromUtf8(tag));
        tagged.add(addRequest);
      } else {
        untagged.add(addRequest);
      }
      queue.add(addRequest);
      all.add(addRequest);
    }

    clock.setTimeMillis(16000);

    double leaseSeconds = 10.0;
    long maxTasks = 100;
    List<TaskQueueAddRequest.Builder> results =
        queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all, results, 16000, secToMillis(leaseSeconds));

    // Let the leases expire.
    clock.setTimeMillis(16000 + secToMillis(11.0));

    // Now specify tag.
    results = queue.queryAndOwnTasks(leaseSeconds, maxTasks, true, tag.getBytes());
    assertLeaseTasksEqualsNoOrder(tagged, results, 27000, secToMillis(leaseSeconds));

    // Tagged, but with no specific tag should now get the untagged tasks, since the
    // tagged tasks are already under lease.
    results = queue.queryAndOwnTasks(leaseSeconds, maxTasks, true, null);
    assertLeaseTasksEqualsNoOrder(untagged, results, 27000, secToMillis(leaseSeconds));
  }

  public void testQueryAndOwnLessTasks() {
    List<TaskQueueAddRequest.Builder> all = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(i * 1000);
      queue.add(addRequest);
      all.add(addRequest);
    }

    clock.setTimeMillis(16000);
    double leaseSeconds = 10.0;
    long maxTasks = 5;
    List<TaskQueueAddRequest.Builder> results =
        queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(0, 5), results, 16000, secToMillis(10.0));
  }

  public void testQueryAndOwnTasksTimeConstraints() {
    List<TaskQueueAddRequest.Builder> all = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(i * 1000);
      queue.add(addRequest);
      all.add(addRequest);
    }

    clock.setTimeMillis(5500);
    double leaseSeconds = 10.0;
    long maxTasks = 100;
    List<TaskQueueAddRequest.Builder> results =
        queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(0, 6), results, 5500, secToMillis(10.0));
  }

  public void testQueryAndOwnTasksExpireAndLeaseAgain() {
    List<TaskQueueAddRequest.Builder> all = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      TaskQueueAddRequest.Builder addRequest =
          newAddRequest(i * 1000).setTaskName(ByteString.copyFromUtf8("foo" + i));
      queue.add(addRequest);
      all.add(addRequest);
    }

    // Lease 2 tasks for 50 seconds.
    clock.setTimeMillis(10000);
    double leaseSeconds = 50.0;
    long maxTasks = 2;
    List<TaskQueueAddRequest.Builder> results =
        queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(0, 2), results, 10000, secToMillis(50.0));

    // Only 6 tasks are available before previous lease expire
    clock.setTimeMillis(20000);
    leaseSeconds = 50.0;
    maxTasks = 8;
    results = queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(2, 8), results, 20000, secToMillis(50.0));

    // Those 2 tasks are available again after expire
    clock.setTimeMillis(61000);
    leaseSeconds = 1.0;
    maxTasks = 5;
    results = queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(0, 2), results, 61000, secToMillis(1.0));

    // 8 tasks are available after all lease expired.
    clock.setTimeMillis(200000);
    leaseSeconds = 1.0;
    maxTasks = 10;
    results = queue.queryAndOwnTasks(leaseSeconds, maxTasks, false, null);
    assertLeaseTasksEqualsNoOrder(all.subList(0, 8), results, 200000, secToMillis(1.0));
  }

  public void testExtendTaskLease() {
    TaskQueueAddRequest.Builder addRequest = newAddRequest(0);
    queue.add(addRequest);

    clock.setTimeMillis(10000);
    List<TaskQueueAddRequest.Builder> response = queue.queryAndOwnTasks(30, 1, false, null);

    clock.setTimeMillis(15000);
    TaskQueueModifyTaskLeaseRequest request =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(PULL_QUEUE_NAME))
            .setTaskName(response.get(0).getTaskName())
            .setEtaUsec(response.get(0).getEtaUsec())
            .setLeaseSeconds(60)
            .build();

    long expectedEtaUsec = (long) (75 * 1e6);
    assertEquals(expectedEtaUsec, queue.modifyTaskLease(request).getUpdatedEtaUsec());
  }

  public void testExtendTaskLeaseLeaseExpiredByClock() {
    TaskQueueAddRequest.Builder addRequest = newAddRequest(0);
    queue.add(addRequest);

    clock.setTimeMillis(10000);
    List<TaskQueueAddRequest.Builder> response = queue.queryAndOwnTasks(30, 1, false, null);

    clock.setTimeMillis(60000);
    TaskQueueModifyTaskLeaseRequest request =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(PULL_QUEUE_NAME))
            .setTaskName(response.get(0).getTaskName())
            .setEtaUsec(response.get(0).getEtaUsec())
            .setLeaseSeconds(60)
            .build();

    try {
      queue.modifyTaskLease(request);
      fail();
    } catch (ApiProxy.ApplicationException exception) {
      // expected.
      assertEquals(ErrorCode.TASK_LEASE_EXPIRED_VALUE, exception.getApplicationError());
    }
  }

  public void testExtendTaskLeaseLeaseExpiredByReLease() {
    TaskQueueAddRequest.Builder addRequest = newAddRequest(0);
    queue.add(addRequest);

    clock.setTimeMillis(10000);
    List<TaskQueueAddRequest.Builder> response = queue.queryAndOwnTasks(30, 1, false, null);

    clock.setTimeMillis(15000);
    TaskQueueModifyTaskLeaseRequest request =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(PULL_QUEUE_NAME))
            .setTaskName(response.get(0).getTaskName())
            .setEtaUsec(response.get(0).getEtaUsec() - 1)
            .setLeaseSeconds(60)
            .build();

    try {
      queue.modifyTaskLease(request);
      fail();
    } catch (ApiProxy.ApplicationException exception) {
      // expected.
      assertEquals(ErrorCode.TASK_LEASE_EXPIRED_VALUE, exception.getApplicationError());
    }
  }

  public void testExtendTaskLeaseUnknownTaskName() {
    TaskQueueAddRequest.Builder addRequest = newAddRequest(0);
    queue.add(addRequest);

    clock.setTimeMillis(10000);
    List<TaskQueueAddRequest.Builder> response = queue.queryAndOwnTasks(30, 1, false, null);

    clock.setTimeMillis(15000);
    TaskQueueModifyTaskLeaseRequest request =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(PULL_QUEUE_NAME))
            .setTaskName(ByteString.copyFromUtf8("unknowntask"))
            .setEtaUsec(response.get(0).getEtaUsec())
            .setLeaseSeconds(60)
            .build();

    try {
      queue.modifyTaskLease(request);
      fail();
    } catch (ApiProxy.ApplicationException exception) {
      // expected.
      assertEquals(ErrorCode.UNKNOWN_TASK_VALUE, exception.getApplicationError());
    }
  }
}
