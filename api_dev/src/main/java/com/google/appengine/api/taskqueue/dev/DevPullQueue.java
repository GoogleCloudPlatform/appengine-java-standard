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

import com.google.appengine.api.taskqueue.QueueConstants;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo.TaskStateInfo;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.QueueXml.Entry;
import com.google.common.collect.Ordering;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dev server implementation of pull queue.
 *
 */
public class DevPullQueue extends DevQueue {

  private final Map<String, TaskQueueAddRequest.Builder> taskMap =
      Collections.synchronizedMap(new HashMap<>());
  private final Clock clock;

  @Override
  Mode getMode() {
    return Mode.PULL;
  }

  /**
   * @param queueXmlEntry
   * @param clock
   */
  DevPullQueue(Entry queueXmlEntry, Clock clock) {
    super(queueXmlEntry);
    this.clock = clock;
  }

  /** Adds pull tasks into the queue. */
  @Override
  synchronized TaskQueueAddResponse add(TaskQueueAddRequest.Builder addRequest) {
    if (addRequest.getMode() != Mode.PULL) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    }
    if (!addRequest.getQueueName().toStringUtf8().equals(getQueueName())) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_REQUEST_VALUE);
    }

    String taskName;
    // If the task has no name, make one.
    if (addRequest.hasTaskName() && !addRequest.getTaskName().isEmpty()) {
      taskName = addRequest.getTaskName().toStringUtf8();
    } else {
      // Generate a unique task name if task name is not set.
      taskName = genTaskName();
    }
    if (taskMap.containsKey(taskName)) {
      throw new ApiProxy.ApplicationException(ErrorCode.TASK_ALREADY_EXISTS_VALUE);
    }
    taskMap.put(taskName, addRequest);

    TaskQueueAddResponse.Builder addResponse = TaskQueueAddResponse.newBuilder();
    if (!addRequest.hasTaskName() || addRequest.getTaskName().isEmpty()) {
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      addResponse.setChosenTaskName(ByteString.copyFromUtf8(taskName));
    }

    return addResponse.build();
  }

  /** Delete task by name. */
  @Override
  boolean deleteTask(String taskName) {
    return taskMap.remove(taskName) != null;
  }

  /** Clears the queue. */
  @Override
  void flush() {
    taskMap.clear();
  }

  /** Get tasks as a list of {@link TaskStateInfo} sorted by eta. */
  @Override
  QueueStateInfo getStateInfo() {
    ArrayList<TaskStateInfo> taskInfoList = new ArrayList<>();
    // Get the names of all jobs belonging to this queue (group).
    for (String taskName : getSortedTaskNames()) {
      TaskQueueAddRequest.Builder addRequest = taskMap.get(taskName);
      if (addRequest == null) {
        // Task has just been deleted when fetching task info.
        continue;
      }
      long etaMillis = addRequest.getEtaUsec() / 1000L;
      taskInfoList.add(new TaskStateInfo(taskName, etaMillis, addRequest, clock));
    }

    Collections.sort(
        taskInfoList,
        (t1, t2) -> {
          // Order by ascending ETA.
          return Long.compare(t1.getEtaMillis(), t2.getEtaMillis());
        });

    return new QueueStateInfo(queueXmlEntry, taskInfoList);
  }

  /**
   * Gets tasks by specified tag. If tag is null, finds the tag of the task of minimum eta and
   * returns tasks by that tag, further sorted by eta.
   */
  QueueStateInfo getStateInfoByTag(byte[] tag) {
    ArrayList<TaskStateInfo> taskInfoList = new ArrayList<>();
    // Get the names of all jobs belonging to this queue (group).
    for (String taskName : getSortedTaskNames()) {
      TaskQueueAddRequest.Builder addRequest = taskMap.get(taskName);
      if (addRequest == null) {
        // Task has just been deleted when fetching task info.
        continue;
      }
      long etaMillis = addRequest.getEtaUsec() / 1000L;
      taskInfoList.add(new TaskStateInfo(taskName, etaMillis, addRequest, clock));
    }
    if (tag == null || tag.length == 0) {
      // Find the tag of the task with minimum eta.
      TaskStateInfo firstTask =
          Collections.min(
              taskInfoList,
              (t1, t2) -> {
                // Order by ascending ETA.
                return Long.compare(t1.getEtaMillis(), t2.getEtaMillis());
              });
      if (firstTask != null) {
        tag = firstTask.getTagAsBytes();
      }
    }
    final byte[] chosenTag = tag == null ? null : tag.clone();
    // Sort so that tasks of our tag come first.
    Collections.sort(
        taskInfoList,
        (t1, t2) -> {
          byte[] tag1 = t1.getTagAsBytes();
          byte[] tag2 = t2.getTagAsBytes();
          if (Arrays.equals(tag1, tag2)) {
            // Order by ascending eta when tags are identical.
            return Long.compare(t1.getEtaMillis(), t2.getEtaMillis());
          }
          // Make sure our special tag comes first.
          if (Arrays.equals(tag1, chosenTag)) {
            return -1;
          }
          if (Arrays.equals(tag2, chosenTag)) {
            return 1;
          }
          // Keep the rest sorted by eta.
          return Long.compare(t1.getEtaMillis(), t2.getEtaMillis());
        });
    // Just keep the tasks with our tag.
    ArrayList<TaskStateInfo> taggedTaskInfoList = new ArrayList<>();
    for (TaskStateInfo t : taskInfoList) {
      byte[] taskTag = t.getTagAsBytes();
      if (Arrays.equals(taskTag, chosenTag)) {
        taggedTaskInfoList.add(t);
      } else {
        // The tasks of our chosenTag are at the start of taskInfoList, so we can bail now.
        break;
      }
    }
    return new QueueStateInfo(queueXmlEntry, taggedTaskInfoList);
  }

  List<String> getSortedTaskNames() {
    List<String> taskNameList = Ordering.natural().sortedCopy(taskMap.keySet());
    return taskNameList;
  }

  @Override
  boolean runTask(String taskName) {
    return false;
  }

  long currentTimeMillis() {
    if (clock != null) {
      return clock.getCurrentTime();
    } else {
      return System.currentTimeMillis();
    }
  }

  /**
   * Helper function for queryAndOwnTasks. Limit available tasks to tasks with ETA earlier than now.
   */
  int availableTaskCount(List<TaskStateInfo> tasks, long nowMillis) {
    int index =
        Collections.binarySearch(
            tasks,
            new TaskStateInfo(null, nowMillis, null, null),
            (t1, t2) -> Long.compare(t1.getEtaMillis(), t2.getEtaMillis()));
    // if no exact match of etaMillis, index will be negative.
    if (index < 0) {
      index = -index - 1;
    }
    return index;
  }

  /** QueryAndOwnTasks RPC implememntation. */
  synchronized List<TaskQueueAddRequest.Builder> queryAndOwnTasks(
      double leaseSeconds, long maxTasks, boolean groupByTag, byte[] tag) {
    if (leaseSeconds < 0 || leaseSeconds > QueueConstants.maxLease(TimeUnit.SECONDS)) {
      throw new IllegalArgumentException("Invalid value for lease time.");
    }
    if (maxTasks <= 0 || maxTasks > QueueConstants.maxLeaseCount()) {
      throw new IllegalArgumentException("Invalid value for lease count.");
    }

    // Get all available tasks in a list (note result is sorted in ascending
    // order by task eta automatically in the dev pull queue).
    List<TaskStateInfo> tasks =
        groupByTag ? getStateInfoByTag(tag).getTaskInfo() : getStateInfo().getTaskInfo();

    long nowMillis = currentTimeMillis();
    int available = availableTaskCount(tasks, nowMillis);
    int resultSize = (int) Math.min(tasks.size(), Math.min(available, maxTasks));
    tasks = tasks.subList(0, resultSize);

    List<TaskQueueAddRequest.Builder> result = new ArrayList<>();
    for (TaskStateInfo task : tasks) {
      // For each task, update eta and add it into result.
      TaskQueueAddRequest.Builder addRequest =
          task.getAddRequest().setEtaUsec((long) (nowMillis * 1e3 + leaseSeconds * 1e6));

      result.add(addRequest);
    }

    return result;
  }

  /** ModifyTaskLease RPC implementation */
  synchronized TaskQueueModifyTaskLeaseResponse modifyTaskLease(
      TaskQueueModifyTaskLeaseRequest request) {
    TaskQueueModifyTaskLeaseResponse.Builder response =
        TaskQueueModifyTaskLeaseResponse.newBuilder();

    TaskQueueAddRequest.Builder task = taskMap.get(request.getTaskName().toStringUtf8());

    if (task == null) {
      throw new ApiProxy.ApplicationException(ErrorCode.UNKNOWN_TASK_VALUE);
    }

    if (task.getEtaUsec() != request.getEtaUsec()) {
      throw new ApiProxy.ApplicationException(ErrorCode.TASK_LEASE_EXPIRED_VALUE);
    }

    long timeNowUsec = (long) (currentTimeMillis() * 1e3);
    if (task.getEtaUsec() < timeNowUsec) {
      throw new ApiProxy.ApplicationException(ErrorCode.TASK_LEASE_EXPIRED_VALUE);
    }

    long requestLeaseUsec = (long) (request.getLeaseSeconds() * 1e6);
    long etaUsec = timeNowUsec + requestLeaseUsec;
    task.setEtaUsec(etaUsec);
    return response.setUpdatedEtaUsec(etaUsec).build();
  }
}
