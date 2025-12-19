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

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb;
import com.google.apphosting.utils.config.QueueXml;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;

/**
 * An extension to {@link JobDetail} that adds some type-safety around required job attributes and
 * hard-codes the Job implementation to be {@link UrlFetchJob}.
 *
 */
class UrlFetchJobDetail extends JobDetail {

  private static final String TASK_NAME_PROP = "taskName";
  private static final String QUEUE_NAME_PROP = "queueName";
  private static final String ADD_REQUEST_PROP = "addRequest";
  private static final String SERVER_URL = "serverUrl";
  private static final String RETRY_COUNT = "retryCount";
  private static final String RETRY_DELAY_MS = "retryDelayMs";
  private static final String CALLBACK = "callback";
  private static final String QUEUE_XML_ENTRY = "queueXmlEntry";
  private static final String RETRY_PARAMETERS = "retryParameters";
  private static final String FIRST_TRY_MS = "firstTryMs";
  private static final String PREVIOUS_RESPONSE = "previousResponseCode";

  private static final TaskQueuePb.TaskQueueRetryParameters DEFAULT_RETRY_PARAMETERS =
      TaskQueuePb.TaskQueueRetryParameters.getDefaultInstance();

  UrlFetchJobDetail(
      String taskName,
      String queueName,
      TaskQueuePb.TaskQueueAddRequest.Builder addRequest,
      String url,
      LocalTaskQueueCallback callback,
      QueueXml.Entry queueXmlEntry,
      TaskQueuePb.TaskQueueRetryParameters retryParameters) {
    super(taskName, queueName, UrlFetchJob.class);
    JobDataMap dataMap = getJobDataMap();
    dataMap.put(TASK_NAME_PROP, taskName);
    dataMap.put(QUEUE_NAME_PROP, queueName);
    dataMap.put(ADD_REQUEST_PROP, addRequest);
    dataMap.put(SERVER_URL, url);
    dataMap.put(CALLBACK, callback);
    dataMap.put(RETRY_COUNT, 0);
    dataMap.put(QUEUE_XML_ENTRY, queueXmlEntry);
    if (retryParameters == null) {
      retryParameters = DEFAULT_RETRY_PARAMETERS;
    }
    dataMap.put(RETRY_PARAMETERS, retryParameters);
    dataMap.put(FIRST_TRY_MS, 0L);
    dataMap.put(PREVIOUS_RESPONSE, 0);
  }

  String getTaskName() {
    return (String) getJobDataMap().get(TASK_NAME_PROP);
  }

  String getQueueName() {
    return (String) getJobDataMap().get(QUEUE_NAME_PROP);
  }

  TaskQueuePb.TaskQueueAddRequest.Builder getAddRequest() {
    return (TaskQueuePb.TaskQueueAddRequest.Builder) getJobDataMap().get(ADD_REQUEST_PROP);
  }

  String getServerUrl() {
    return (String) getJobDataMap().get(SERVER_URL);
  }

  int getRetryCount() {
    return (Integer) getJobDataMap().get(RETRY_COUNT);
  }

  int getRetryDelayMs() {
    return (Integer) getJobDataMap().get(RETRY_DELAY_MS);
  }

  long getFirstTryMs() {
    return (Long) getJobDataMap().get(FIRST_TRY_MS);
  }

  int getPreviousResponse() {
    return (Integer) getJobDataMap().get(PREVIOUS_RESPONSE);
  }

  QueueXml.Entry getQueueXmlEntry() {
    return (QueueXml.Entry) getJobDataMap().get(QUEUE_XML_ENTRY);
  }

  TaskQueuePb.TaskQueueRetryParameters getRetryParameters() {
    return (TaskQueuePb.TaskQueueRetryParameters) getJobDataMap().get(RETRY_PARAMETERS);
  }

  UrlFetchJobDetail retry(long firstTryMs, int previousResponseCode) {
    UrlFetchJobDetail newJob =
        new UrlFetchJobDetail(
            getTaskName(),
            getQueueName(),
            getAddRequest(),
            getServerUrl(),
            getCallback(),
            getQueueXmlEntry(),
            getRetryParameters());
    JobDataMap newDataMap = newJob.getJobDataMap();
    newDataMap.put(RETRY_COUNT, getRetryCount());
    newDataMap.put(FIRST_TRY_MS, firstTryMs);
    newDataMap.put(PREVIOUS_RESPONSE, previousResponseCode);

    newJob.incrementRetryDelayMs();

    return newJob;
  }

  /**
   * Increment retry count, and update and return the next retry delay. Not threadsafe! Doesn't need
   * to be!
   */
  int incrementRetryDelayMs() {
    int retryCount = getRetryCount() + 1;
    getJobDataMap().put(RETRY_COUNT, retryCount);

    TaskQueuePb.TaskQueueRetryParameters params = getRetryParameters();
    int exponent = Math.min(retryCount - 1, params.getMaxDoublings());
    int linearSteps = retryCount - exponent;
    int minBackoffMs = (int) (params.getMinBackoffSec() * 1000);
    int maxBackoffMs = (int) (params.getMaxBackoffSec() * 1000);
    int backoffMs = minBackoffMs;
    if (exponent > 0) {
      backoffMs = (int) Math.scalb(backoffMs, Math.min(1023, exponent));
    }
    // There can be both exponential and linear components of the backoff.
    if (linearSteps > 1) {
      backoffMs *= linearSteps;
    }
    backoffMs = Math.min(maxBackoffMs, backoffMs);
    getJobDataMap().put(RETRY_DELAY_MS, backoffMs);
    return backoffMs;
  }

  LocalTaskQueueCallback getCallback() {
    return (LocalTaskQueueCallback) getJobDataMap().get(CALLBACK);
  }
}
