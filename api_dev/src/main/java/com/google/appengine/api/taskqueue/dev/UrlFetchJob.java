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
import static org.quartz.TriggerBuilder.newTrigger;

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.flogger.GoogleLogger;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Date;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

/**
 * Quartz {@link Job} implementation that hits a url. The url to hit, the http method to invoke,
 * headers, and any data that should be sent as part of the request are all determined by the {@link
 * TaskQueueAddRequest} contained in the job data. We delegate to {@link LocalURLFetchService} for
 * the actual fetching.
 *
 * <p>{@link #initialize(LocalServerEnvironment, Clock)} must be called before the first invocation
 * of {@link #execute(JobExecutionContext)}.
 *
 */
public class UrlFetchJob implements Job {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // This should be kept in sync with
  // com.google.apphosting.utils.jetty.DevAppEngineWebAppContext
  static final String X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK =
      "X-Google-DevAppserver-SkipAdminCheck";

  // keep these in sync with apphosting/base/http_proto.cc
  static final String X_APPENGINE_QUEUE_NAME = "X-AppEngine-QueueName";
  static final String X_APPENGINE_TASK_NAME = "X-AppEngine-TaskName";
  static final String X_APPENGINE_TASK_RETRY_COUNT = "X-AppEngine-TaskRetryCount";
  static final String X_APPENGINE_TASK_EXECUTION_COUNT = "X-AppEngine-TaskExecutionCount";
  static final String X_APPENGINE_TASK_ETA = "X-AppEngine-TaskETA";
  static final String X_APPENGINE_SERVER_NAME = "X-AppEngine-ServerName";
  static final String X_APPENGINE_TASK_PREVIOUS_RESPONSE = "X-AppEngine-TaskPreviousResponse";

  private static LocalServerEnvironment localServerEnvironment;
  private static Clock clock;

  static URLFetchRequest.RequestMethod translateRequestMethod(
      TaskQueueAddRequest.RequestMethod rm) {
    // Relies on the two RequestMethod enums having the same
    // names.  Brittle, but we have a unit test that locks it down.
    return URLFetchRequest.RequestMethod.valueOf(rm.name());
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    // block until the local server is ready to accept requests.  this can
    // matter when tasks are enqueued as part of servlet initialization, which
    // happens before the server is actually running.  quartz jobs execute in a
    // thread that is managed by the quartz threadpool so we're not going to be
    // blocking anything other than task execution
    try {
      localServerEnvironment.waitForServerToStart();
    } catch (InterruptedException e) {
      throw new JobExecutionException(
          "Interrupted while waiting for server to initialize.", e, false);
    }

    UrlFetchJobDetail jd = new UrlFetchJobDetail(context.getJobDetail().getJobDataMap());

    URLFetchRequest fetchReq =
        newFetchRequest(
            jd.getTaskName(),
            jd.getAddRequest(),
            jd.getServerUrl(),
            jd.getRetryCount(),
            jd.getQueueXmlEntry(),
            jd.getPreviousResponse());
    long firstTryMs = jd.getFirstTryMs();
    if (firstTryMs == 0) {
      firstTryMs = clock.getCurrentTime();
    }
    int status = jd.getCallback().execute(fetchReq);
    // Anything other than [200,299] is a failure
    if ((status < 200 || status > 299) && canRetry(jd, firstTryMs)) {
      logger.atInfo().log(
          "Web hook at %s returned status code %d.  Rescheduling...", fetchReq.getUrl(), status);
      reschedule(context.getScheduler(), context.getTrigger(), jd, firstTryMs, status);
    } else {
      try {
        Trigger trigger = context.getTrigger();
        context.getScheduler().unscheduleJob(trigger.getKey());
      } catch (SchedulerException e) {
        logger.atSevere().withCause(e).log("Unsubscription of task %s failed.", jd.getAddRequest());
      }
    }
  }

  private boolean canRetry(UrlFetchJobDetail jd, long firstTryMs) {
    TaskQueueRetryParameters retryParams = jd.getRetryParameters();
    if (retryParams != null) {
      int newRetryCount = jd.getRetryCount() + 1;
      long ageMs = clock.getCurrentTime() - firstTryMs;

      if (retryParams.hasRetryLimit() && retryParams.hasAgeLimitSec()) {
        return (retryParams.getRetryLimit() >= newRetryCount)
            || ((retryParams.getAgeLimitSec() * 1000) >= ageMs);
      }
      if (retryParams.hasRetryLimit()) {
        return (retryParams.getRetryLimit() >= newRetryCount);
      }
      if (retryParams.hasAgeLimitSec()) {
        return ((retryParams.getAgeLimitSec() * 1000) >= ageMs);
      }
    }
    return true;
  }

  private void reschedule(
      Scheduler scheduler,
      Trigger trigger,
      UrlFetchJobDetail jd,
      long firstTryMs,
      int previousResponse) {
    // Builds a new job.
    UrlFetchJobDetail newJobData = jd.retry(firstTryMs, previousResponse);
    JobDataMap newJobDataMap = newJobData.getJobDataMap();

    // Build the new trigger from the old trigger
    TriggerKey triggerKey = trigger.getKey();
    Instant newStartTime =
        Instant.ofEpochMilli(clock.getCurrentTime() + newJobData.getRetryDelayMs());

    SimpleTrigger newTrigger =
        (SimpleTrigger)
            newTrigger().withIdentity(triggerKey).startAt(Date.from(newStartTime)).build();

    JobDetail newJob =
        newJob(UrlFetchJob.class)
            .withIdentity(jd.getTaskName(), jd.getQueueName())
            .usingJobData(newJobDataMap)
            .build();

    try {
      // Quartz 2.x doesn't allow 2 jobs with the same name so we need to first
      // unschedule the currently executing job before we reschedule
      scheduler.unscheduleJob(triggerKey);
      scheduler.scheduleJob(newJob, newTrigger);
    } catch (SchedulerException e) {
      logger.atSevere().withCause(e).log("Reschedule of task %s failed.", jd.getAddRequest());
    }
  }

  /**
   * Transforms the provided {@link TaskQueueAddRequest} and {@code serverUrl} into a {@link
   * URLFetchRequest}.
   */
  URLFetchRequest newFetchRequest(
      String taskName,
      TaskQueueAddRequest.Builder addReq,
      String serverUrl,
      int retryCount,
      QueueXml.Entry queueXmlEntry,
      int previousResponse) {
    URLFetchRequest.Builder requestProto =
        URLFetchRequest.newBuilder().setUrl(serverUrl + addReq.getUrl().toStringUtf8());

    if (addReq.hasBody()) {
      requestProto.setPayload(addReq.getBody());
    }
    requestProto.setMethod(translateRequestMethod(addReq.getMethod()));

    addHeadersToFetchRequest(
        requestProto, taskName, addReq, retryCount, queueXmlEntry, previousResponse);

    if (requestProto.getMethod() == URLFetchRequest.RequestMethod.PUT) {
      // HttpClient blows up if method == PUT and followRedirects is true
      requestProto.setFollowRedirects(false);
    }
    // TODO Figure out what to do about following redirects in the
    // general case.

    return requestProto.build();
  }

  private void addHeadersToFetchRequest(
      URLFetchRequest.Builder requestProto,
      String taskName,
      TaskQueueAddRequest.Builder addReq,
      int retryCount,
      QueueXml.Entry queueXmlEntry,
      int previousResponse) {
    for (TaskQueueAddRequest.Header header : addReq.getHeaderList()) {
      requestProto.addHeader(
          buildHeader(header.getKey().toStringUtf8(), header.getValue().toStringUtf8()));
    }

    // set the magic header that tells the dev appserver to skip
    // authentication - this lets us hit protected urls
    requestProto
        .addHeader(buildHeader(X_GOOGLE_DEV_APPSERVER_SKIPADMINCHECK, "true"))
        .addHeader(buildHeader(X_APPENGINE_QUEUE_NAME, addReq.getQueueName().toStringUtf8()))
        .addHeader(buildHeader(X_APPENGINE_TASK_NAME, taskName))
        .addHeader(buildHeader(X_APPENGINE_TASK_RETRY_COUNT, Integer.toString(retryCount)))
        .addHeader(
            buildHeader(
                X_APPENGINE_TASK_ETA,
                new DecimalFormat("0.000000").format(addReq.getEtaUsec() / 1.0E6)));
    if (queueXmlEntry.getTarget() != null) {
      requestProto.addHeader(buildHeader(X_APPENGINE_SERVER_NAME, queueXmlEntry.getTarget()));
    }
    requestProto.addHeader(
        buildHeader(X_APPENGINE_TASK_EXECUTION_COUNT, Integer.toString(retryCount)));
    if (previousResponse > 0) {
      requestProto.addHeader(
          buildHeader(X_APPENGINE_TASK_PREVIOUS_RESPONSE, Integer.toString(previousResponse)));
    }
  }

  private URLFetchRequest.Header.Builder buildHeader(String key, String value) {
    return URLFetchRequest.Header.newBuilder().setKey(key).setValue(value);
  }

  static void initialize(LocalServerEnvironment localServerEnvironment, Clock clock) {
    UrlFetchJob.localServerEnvironment = localServerEnvironment;
    UrlFetchJob.clock = clock;
  }
}
