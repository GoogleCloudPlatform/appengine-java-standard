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

import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;
import static org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals;

import com.google.appengine.api.taskqueue.dev.QueueStateInfo.TaskStateInfo;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest.Header;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

/**
 * Dev server push queue.
 *
 * <p>Manages a single, logical queue on top of Quartz. We do this by mapping the task name to the
 * Quartz job name and the queue name to the Quartz group name.
 *
 * <p>This class is thread-safe.
 *
 */
class DevPushQueue extends DevQueue {
  // If unspecified use this bucket size.
  // The XML specification may not specify a bucket size.
  static final int DEFAULT_BUCKET_SIZE = 5;

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final Scheduler scheduler;
  private final String baseUrl;
  private final Clock clock;
  private final LocalTaskQueueCallback callback;

  @Override
  Mode getMode() {
    return Mode.PUSH;
  }

  DevPushQueue(
      QueueXml.Entry queueXmlEntry,
      Scheduler scheduler,
      String baseUrl,
      Clock clock,
      LocalTaskQueueCallback callback) {
    super(queueXmlEntry);
    this.scheduler = scheduler;
    this.baseUrl = baseUrl;
    this.clock = clock;
    this.callback = callback;

    if (queueXmlEntry.getRate() != null) {
      if (queueXmlEntry.getRate() == 0.0) {
        // doesn't matter what the units are, 0 is 0
        try {
          // Pausing the job group is more intuitive, but, despite promises
          // to the contrary, pausing a job group only pauses jobs that already
          // exist.  We need to make sure all future jobs are paused, and that
          // works if we pause the trigger group.
          scheduler.pauseTriggers(triggerGroupEquals(getQueueName()));
        } catch (SchedulerException e) {
          throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE, e.getMessage());
        }
      }
    } else {
      throw new RuntimeException("Rate must be specified for push queue.");
    }
  }

  // synchronized to defend against a race condition where two tasks
  // with the same name are scheduled at the same time
  // TODO See if Quartz can catch this for us.
  private synchronized String scheduleTask(TaskQueueAddRequest.Builder addRequest) {
    String taskName;
    // If the task has no name, make one.
    if (addRequest.hasTaskName() && !addRequest.getTaskName().isEmpty()) {
      taskName = addRequest.getTaskName().toStringUtf8();
    } else {
      // Generate a unique task name if task name is not set.
      taskName = genTaskName();
    }
    try {
      if (scheduler.checkExists(jobKey(taskName, getQueueName()))) {
        throw new ApiProxy.ApplicationException(ErrorCode.TASK_ALREADY_EXISTS_VALUE);
      }
    } catch (SchedulerException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE, e.getMessage());
    }

    TaskQueueRetryParameters retryParams = getRetryParameters(addRequest);
    long etaMillis = addRequest.getEtaUsec() / 1000L;

    JobDataMap jobDataMap =
        newUrlFetchJobDataMap(taskName, getQueueName(), addRequest, retryParams);

    JobDetail job =
        newJob(UrlFetchJob.class)
            .withIdentity(taskName, getQueueName())
            .usingJobData(jobDataMap)
            .build();

    Trigger trigger =
        newTrigger()
            .withIdentity(taskName, getQueueName())
            .startAt(Date.from(Instant.ofEpochMilli(etaMillis)))
            .build();

    try {
      scheduler.scheduleJob(job, trigger);
    } catch (SchedulerException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE, e.getMessage());
    }
    return taskName;
  }

  // broken out to support testing
  JobDataMap newUrlFetchJobDataMap(
      String taskName,
      String queueName,
      TaskQueueAddRequest.Builder addRequest,
      TaskQueueRetryParameters retryParams) {
    for (Header header : addRequest.getHeaderList()) {
      if (header.getKey().toStringUtf8().equals("Host")) {
        String host = header.getValue().toStringUtf8();
        if (host.startsWith("localhost:")) {
          return new UrlFetchJobDetail(
                  taskName,
                  queueName,
                  addRequest,
                  "http://" + host,
                  callback,
                  queueXmlEntry,
                  retryParams)
              .getJobDataMap();
        }
      }
    }
    return new UrlFetchJobDetail(
            taskName, queueName, addRequest, baseUrl, callback, queueXmlEntry, retryParams)
        .getJobDataMap();
  }

  @Override
  TaskQueueAddResponse add(TaskQueueAddRequest.Builder addRequest) {
    if (addRequest.getMode() != Mode.PUSH) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    }
    if (!addRequest.getQueueName().toStringUtf8().equals(getQueueName())) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_REQUEST_VALUE);
    }
    String taskName = scheduleTask(addRequest);

    TaskQueueAddResponse.Builder addResponse = TaskQueueAddResponse.newBuilder();
    if (!addRequest.hasTaskName() || addRequest.getTaskName().isEmpty()) {
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      addResponse.setChosenTaskName(ByteString.copyFromUtf8(taskName));
    }

    return addResponse.build();
  }

  List<String> getSortedJobNames() throws SchedulerException {
    Set<JobKey> jobKeys = scheduler.getJobKeys(jobGroupEquals(getQueueName()));
    List<String> jobNameList = new ArrayList<>();
    for (JobKey jobKey : jobKeys) {
      jobNameList.add(jobKey.getName());
    }
    Collections.sort(jobNameList);
    return jobNameList;
  }

  /** Returns a QueueStateInfo describing the current state of this queue. */
  @Override
  QueueStateInfo getStateInfo() {
    ArrayList<TaskStateInfo> taskInfoList = new ArrayList<>();
    try {
      // Get the names of all jobs belonging to this queue (group).
      for (String jobName : getSortedJobNames()) {
        // Now get job details
        JobDetail jobDetail = scheduler.getJobDetail(jobKey(jobName, getQueueName()));
        if (jobDetail == null) {
          // oops, gone, must have already run
          continue;
        }

        UrlFetchJobDetail jd = new UrlFetchJobDetail(jobDetail.getJobDataMap());

        List<? extends Trigger> triggers =
            scheduler.getTriggersOfJob(jobKey(jobName, getQueueName()));
        if (triggers.isEmpty()) {
          // must have run in between the time we fetched the job detail and the time we fetched the
          // trigger
          continue;
        }
        if (triggers.size() != 1) {
          throw new IllegalStateException(
              "Multiple triggers for task " + jobName + " in queue " + getQueueName());
        }
        long execTime = triggers.get(0).getStartTime().toInstant().toEpochMilli();
        taskInfoList.add(new TaskStateInfo(jd.getTaskName(), execTime, jd.getAddRequest(), clock));
      }
    } catch (SchedulerException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE);
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
   * Delete a task by name.
   *
   * @return false if task was not found.
   */
  @Override
  boolean deleteTask(String taskName) {
    try {
      return scheduler.deleteJob(jobKey(taskName, getQueueName()));
    } catch (SchedulerException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE);
    }
  }

  /** Deletes all tasks in the queue (group) */
  @Override
  void flush() {
    try {
      Set<JobKey> jobKeys = scheduler.getJobKeys(jobGroupEquals(getQueueName()));
      scheduler.deleteJobs(new ArrayList<>(jobKeys));
    } catch (SchedulerException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR_VALUE);
    }
  }

  private JobExecutionContext getExecutionContext(JobDetail jobDetail, SimpleTrigger trigger) {
    TriggerFiredBundle bundle =
        new TriggerFiredBundle(
            jobDetail, (OperableTrigger) trigger, null, false, null, null, null, null);
    return new JobExecutionContextImpl(scheduler, bundle, null);
  }

  /**
   * Run a task by name.
   *
   * @return false if task was not found or could not be executed. Note that the return value is not
   *     in any way related to the success or failure of the task. If we can find the task and we
   *     can initiate its execution we will return true even if the execution yields an exception.
   */
  @Override
  boolean runTask(String taskName) {
    Job job;
    JobExecutionContext context;
    try {
      JobDetail jd = scheduler.getJobDetail(jobKey(taskName, getQueueName()));
      if (jd == null) {
        return false;
      }
      // Reconstruct trigger for execution context - just needs to hold data map
      SimpleTrigger trigger =
          (SimpleTrigger)
              newTrigger()
                  .withIdentity(taskName, getQueueName())
                  .usingJobData(jd.getJobDataMap())
                  .build();

      context = getExecutionContext(jd, trigger);
      job = (Job) jd.getJobClass().newInstance();
    } catch (SchedulerException e) {
      return false;
    } catch (IllegalAccessException e) {
      return false;
    } catch (InstantiationException e) {
      return false;
    }
    try {
      job.execute(context);
    } catch (JobExecutionException e) {
      logger.atSevere().withCause(e).log(
          "Exception executing task %s on queue %s", taskName, getQueueName());
    } catch (RuntimeException rte) {
      logger.atSevere().withCause(rte).log(
          "Exception executing task %s on queue %s", taskName, getQueueName());
    }

    // job.execute() above unschedules the task if it's successful
    // job.execute() will reschedule the task if it fails
    return true;
  }
}
