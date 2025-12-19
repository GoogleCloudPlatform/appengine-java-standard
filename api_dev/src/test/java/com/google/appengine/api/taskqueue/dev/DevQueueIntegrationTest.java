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
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest.Header;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

/**
 * Integration tests for local dev queue. These tests use a real Quartz {@link Scheduler}.
 *
 */
@RunWith(JUnit4.class)
public class DevQueueIntegrationTest {

  private static final String SERVER_NAME = "server1";

  private DevQueue queue;
  private Scheduler scheduler;

  private CountDownLatch latch;
  private final List<URLFetchRequest> providedFetchReqs =
      Collections.synchronizedList(new ArrayList<URLFetchRequest>());

  private Integer success() throws Exception {
    latch.countDown();
    return 200;
  }

  private Integer doFail() throws Exception {
    latch.countDown();
    return 301;
  }

  private Callable<Integer> onExecute = this::success;

  class TestLocalTaskQueueCallback implements LocalTaskQueueCallback {
    @Override
    public int execute(URLFetchRequest fetchReq) {
      providedFetchReqs.add(fetchReq);
      try {
        return onExecute.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void initialize(Map<String, String> properties) {
      // no initialization necessary
    }
  }

  @Before
  public void setUp() throws Exception {
    setUp(false);
  }

  private void setUp(boolean disableAutoTaskExecution) throws Exception {
    LocalServerEnvironment lse = mock(LocalServerEnvironment.class);
    UrlFetchJob.initialize(lse, Clock.DEFAULT);
    scheduler = LocalTaskQueue.startScheduler(disableAutoTaskExecution);
    latch = new CountDownLatch(1);
    queue =
        new DevPushQueue(
            getDefaultQueueXmlEntry(),
            scheduler,
            "http://localhost:8080",
            Clock.DEFAULT,
            new TestLocalTaskQueueCallback());
    providedFetchReqs.clear();
    onExecute = this::success;
  }

  private static QueueXml.Entry getDefaultQueueXmlEntry() {
    QueueXml.Entry queueXmlEntry = QueueXml.defaultEntry();
    queueXmlEntry.setRate(32.2);
    queueXmlEntry.setTarget(SERVER_NAME);
    return queueXmlEntry;
  }

  @After
  public void tearDown() throws Exception {
    queue.flush();
    LocalTaskQueue.stopScheduler(scheduler);
    latch = null;
  }

  private TaskQueueAddRequest.Builder newAddRequest(long execTime) {
    return TaskQueueAddRequest.newBuilder()
        .setQueueName(ByteString.copyFromUtf8(QueueXml.defaultEntry().getName()))
        .setEtaUsec(execTime)
        .setMethod(TaskQueueAddRequest.RequestMethod.GET)
        .setUrl(ByteString.copyFromUtf8("/my/url"));
  }

  private long getNowMilliseconds(int addSeconds) {
    Instant i = Instant.now();
    return (i.getEpochSecond() + addSeconds) * 1000000 + i.getNano() / 1000;
  }

  @Test
  public void testAdd_NamedTask() throws Exception {
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));

    var unused = queue.add(add);
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(1);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
  }

  @Test
  public void testAdd_UnnamedTask() throws Exception {
    // schedule way in the past
    TaskQueueAddRequest.Builder add = newAddRequest(1000);

    TaskQueueAddResponse response = queue.add(add);
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(1);

    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        response.getChosenTaskName().toStringUtf8(),
        0);
  }

  @Test
  public void testAddTaskRespectsHostHeader() throws Exception {
    // schedule way in the past
    TaskQueueAddRequest.Builder add = newAddRequest(1000);
    Header header =
        TaskQueueAddRequest.Header.newBuilder()
            .setKey(ByteString.copyFromUtf8("Host"))
            .setValue(ByteString.copyFromUtf8("localhost:24358"))
            .build();
    add.addHeader(header);

    TaskQueueAddResponse response = queue.add(add);
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(1);

    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:24358/my/url",
        false,
        0,
        response.getChosenTaskName().toStringUtf8(),
        0);
  }

  @Test
  public void testAddTaskIgnoresNonLocalhostHostHeader() throws Exception {
    // schedule way in the past
    TaskQueueAddRequest.Builder add = newAddRequest(1000);
    Header header =
        TaskQueueAddRequest.Header.newBuilder()
            .setKey(ByteString.copyFromUtf8("Host"))
            .setValue(ByteString.copyFromUtf8("foo:8085"))
            .build();
    add.addHeader(header);

    TaskQueueAddResponse response = queue.add(add);
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(1);

    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        response.getChosenTaskName().toStringUtf8(),
        0);
  }

  @Test
  public void testAddDupe() throws Exception {
    // schedule way out in the future to make sure the task doesn't execute
    // before we add the dupe
    TaskQueueAddRequest.Builder a =
        newAddRequest(getNowMilliseconds(10000000))
            .setTaskName(ByteString.copyFromUtf8("task 9"));
    var unused = queue.add(a);
    ApiProxy.ApplicationException exception =
        assertThrows(ApiProxy.ApplicationException.class, () -> queue.add(a));
    // expected.
    assertThat(exception.getApplicationError()).isEqualTo(ErrorCode.TASK_ALREADY_EXISTS_VALUE);
    assertThat(providedFetchReqs).isEmpty();
  }

  @Test
  public void testDelete() throws Exception {
    assertFalse(queue.deleteTask("task 9"));

    // schedule way out in the future to make sure the task doesn't execute
    // before we delete
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(getNowMilliseconds(10000000))
            .setTaskName(ByteString.copyFromUtf8("task 9"));
    var unused = queue.add(addRequest);
    assertThat(queue.deleteTask("task 9")).isTrue();
    assertFalse(queue.deleteTask("task 9"));

    assertThat(providedFetchReqs).isEmpty();
  }

  @Test
  public void testRun() throws Exception {
    assertThat(providedFetchReqs).isEmpty();
    assertFalse(queue.deleteTask("task 9"));

    // schedule way out in the future to make sure the task doesn't execute
    // before we execute
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(getNowMilliseconds(10000000))
            .setTaskName(ByteString.copyFromUtf8("task 9"));
    var unused = queue.add(addRequest);
    assertThat(queue.runTask("task 9")).isTrue();
    assertThat(providedFetchReqs).isNotEmpty();
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "task 9",
        0);

    providedFetchReqs.clear();
    assertFalse(queue.runTask("task 9"));
    assertFalse(queue.deleteTask("task 9"));
    assertThat(providedFetchReqs).isEmpty();
  }

  @Test
  public void testGetStateInfo() throws Exception {
    List<String> jobNames = Lists.newArrayList();
    List<Long> etas = Lists.newArrayList();
    // In micro seconds. Adding buffer of 200 micro second so that task are not scheduled
    // before test run is completed.
    long startingEta = (System.currentTimeMillis() + 200) * 1000;
    for (int i = 0; i < 10; i++) {
      // Add task in reverse order so that task 10 is scheduled before task 9.
      TaskQueueAddRequest.Builder addRequest = newAddRequest(startingEta - (i * 1000));
      String taskName = "task" + i;
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      jobNames.add(taskName);
      SimpleTrigger trig =
          new SimpleTrigger(taskName, "default", new Date(addRequest.getEtaUsec() / 1000));
      etas.add(trig.getStartTime().getTime());
      var unused = queue.add(addRequest);

      assertThat(providedFetchReqs).isEmpty();
    }

    QueueStateInfo info = queue.getStateInfo();
    assertThat(info.getCountTasks()).isEqualTo(10);
    Iterator<String> reversedJobNames = Lists.reverse(jobNames).iterator();
    Iterator<Long> reversedEtas = Lists.reverse(etas).iterator();
    for (QueueStateInfo.TaskStateInfo taskInfo : info.getTaskInfo()) {
      assertThat(taskInfo.getTaskName()).isEqualTo(reversedJobNames.next());
      assertThat(taskInfo.getEtaMillis()).isEqualTo(reversedEtas.next().longValue());
    }
  }

  @Test
  public void testFlush() throws SchedulerException {
    // schedule way out in the future to make sure the task doesn't execute
    // before we flush
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(getNowMilliseconds(10000000));
    addRequest.setTaskName(ByteString.copyFromUtf8("task 10"));

    queue.flush();
    assertFalse(queue.deleteTask("task 9"));
    assertFalse(queue.deleteTask("task 10"));

    assertThat(providedFetchReqs).isEmpty();
  }

  @Test
  public void testTaskDoesNotAutoRunWhenAutoExecDisabled() throws Exception {
    tearDown();
    // disable task execution
    setUp(true);
    assertThat(scheduler.isInStandbyMode()).isTrue();
    // schedule way in the past
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("task 9"));
    var unused = queue.add(addRequest);
    // we'll give the task 3 seconds to execute but it shouldn't ever happen
    boolean success = latch.await(3, SECONDS);
    assertWithMessage("Job was automatically executed").that(success).isFalse();
    assertThat(providedFetchReqs).isEmpty();

    // make sure we can still manually invoke the task even though we're
    // in standby mode
    assertThat(queue.runTask("task 9")).isTrue();
    assertThat(providedFetchReqs).isNotNull();
    assertThat(providedFetchReqs).hasSize(1);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "task 9",
        0);
    providedFetchReqs.clear();

    // now we'll add another task and delete it
    unused = queue.add(addRequest);
    assertThat(queue.deleteTask("task 9")).isTrue();
  }

  @Test
  public void testTaskDoesNotAutoRunWhenQueueRateIsZero() throws Exception {
    QueueXml.Entry entry = getDefaultQueueXmlEntry();
    entry.setRate(0.00);
    queue =
        new DevPushQueue(
            entry,
            scheduler,
            "http://localhost:8080",
            Clock.DEFAULT,
            new TestLocalTaskQueueCallback());
    assertFalse(scheduler.isInStandbyMode());
    // schedule way in the past
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("task 9"));
    var unused = queue.add(addRequest);
    Set<?> triggerGroups = scheduler.getPausedTriggerGroups();
    assertThat(triggerGroups).containsExactly(entry.getName());

    // we'll give the task 3 seconds to execute but it shouldn't ever happen
    boolean executed = latch.await(3, SECONDS);
    assertWithMessage("Job was automatically executed").that(executed).isFalse();
    assertThat(providedFetchReqs).isEmpty();

    // make sure we can still manually invoke the task even though the job
    // group is paused
    assertThat(queue.runTask("task 9")).isTrue();
    assertThat(providedFetchReqs).isNotNull();
    assertThat(providedFetchReqs).hasSize(1);

    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "task 9",
        0);

    providedFetchReqs.clear();

    // now we'll add another task and delete it
    unused = queue.add(addRequest);
    assertThat(queue.deleteTask("task 9")).isTrue();
  }

  @Test
  public void testTaskDoesRunWhenQueueRateIsNearZero() throws Exception {
    QueueXml.Entry entry = getDefaultQueueXmlEntry();
    entry.setRate(0.4);
    queue =
        new DevPushQueue(
            entry,
            scheduler,
            "http://localhost:8080",
            Clock.DEFAULT,
            new TestLocalTaskQueueCallback());

    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));

    var unused = queue.add(add);
    Set<?> triggerGroups = scheduler.getPausedTriggerGroups();
    assertThat(triggerGroups).isEmpty();
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(1);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
  }

  @Test
  public void testSuccessCodes() throws InterruptedException {
    // All codes [200-299] are considered to be successful.
    for (int code = 200; code < 300; code += 1) {
      final int httpResponseCode = code;
      onExecute =
          () -> {
            success();
            return httpResponseCode;
          };
      // schedule way in the past
      TaskQueueAddRequest.Builder add =
          newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name" + httpResponseCode));
      var unused = queue.add(add);
      waitForJobExecution();
    }
  }

  @Test
  public void testRetries() throws InterruptedException {
    // install a handler that fails twice and succeeds on the third
    // attempt
    onExecute =
        new Callable<Integer>() {
          int attempt = 0;

          @Override
          public Integer call() throws Exception {
            if (++attempt == 3) {
              return success();
            }
            return 301;
          }
        };
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    var unused = queue.add(add);
    waitForJobExecution();
    assertThat(providedFetchReqs).hasSize(3);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
    assertFetchReqEquals(
        providedFetchReqs.get(1),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        1,
        "the name",
        301);
    assertFetchReqEquals(
        providedFetchReqs.get(2),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        2,
        "the name",
        301);
  }

  @Test
  public void testRetryLimitNoRetries() throws InterruptedException {
    onExecute = this::doFail;
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    TaskQueueRetryParameters.Builder retryParams = add.getRetryParametersBuilder();
    // Schedule no retries, so only the initial task execution should occur.
    retryParams.setRetryLimit(0);

    var unused = queue.add(add);
    waitForJobExecution();
    // Give the scheduler a chance to run additional fetches (which of course it
    // shouldn't).
    Thread.sleep(500);
    assertThat(providedFetchReqs).hasSize(1);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
  }

  @Test
  public void testRetryLimitOneRetry() throws InterruptedException {
    onExecute = this::doFail;
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    add.getRetryParametersBuilder().setRetryLimit(1);
    // We expect the handler to be called twice.
    latch = new CountDownLatch(2);

    var unused = queue.add(add);
    waitForJobExecution();
    // Give the scheduler a chance to run additional fetches (which of course it
    // shouldn't).
    Thread.sleep(1000);
    assertThat(providedFetchReqs).hasSize(2);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
    assertFetchReqEquals(
        providedFetchReqs.get(1),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        1,
        "the name",
        301);
  }

  @Test
  public void testAgeLimitSec() throws InterruptedException {
    onExecute = this::doFail;
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    add.getRetryParametersBuilder().setAgeLimitSec(1);
    ;

    var unused = queue.add(add);
    // Give the scheduler a chance to run plenty of fetches.
    Thread.sleep(5000);
    // The expectations here are somewhat subtle.  Ideally the task would be
    // executed precisely 5 times: the initial execution, plus at deltas of
    // 100, 300, 700, 1500 milliseconds. The last one is run because it is
    // scheduled before the first second is over.  Note however that we accept
    // any result between 2 and 5 executions, because contention on the machine
    // may slow things down.
    assertThat(providedFetchReqs.size()).isAtLeast(2);
    assertThat(providedFetchReqs.size()).isAtMost(5);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
    assertFetchReqEquals(
        providedFetchReqs.get(1),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        1,
        "the name",
        301);
  }

  @Test
  public void testRetryLimitWithAgeLimitSec() throws InterruptedException {
    onExecute = this::doFail;
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    TaskQueueRetryParameters.Builder retryParams = add.getRetryParametersBuilder();
    // Both of the following limits must apply before retries are stopped.
    retryParams.setAgeLimitSec(0);
    retryParams.setRetryLimit(0);

    var unused = queue.add(add);
    // Give the scheduler a chance to run plenty of fetches.
    Thread.sleep(500);
    // See comment in testAgeLimitSec(). Note that retries weren't limited to zero retries.
    assertThat(providedFetchReqs).isNotEmpty();
    assertThat(providedFetchReqs.size()).isAtMost(2);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
    // The age limit is only measured to ms resolution. That means in most cases, the scheduler
    // checks the task age before 1 ms has passed from the first try. In slower situations, the
    // more than 1 ms passes between these events and the retry code thinks the task is now
    // too old.
    if (providedFetchReqs.size() == 2) {
      assertFetchReqEquals(
          providedFetchReqs.get(1),
          URLFetchRequest.RequestMethod.GET,
          "http://localhost:8080/my/url",
          false,
          1,
          "the name",
          301);
    }
  }

  @Test
  public void testMinBackoffSec() throws InterruptedException {
    onExecute = this::doFail;
    // schedule way in the past
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));
    add.getRetryParametersBuilder().setMinBackoffSec(10.0);
    ;

    var unused = queue.add(add);
    // Give the scheduler a chance to run plenty of fetches.
    Thread.sleep(5000);
    // No retries should be run.
    assertThat(providedFetchReqs).hasSize(1);
    assertFetchReqEquals(
        providedFetchReqs.get(0),
        URLFetchRequest.RequestMethod.GET,
        "http://localhost:8080/my/url",
        false,
        0,
        "the name",
        0);
  }

  private void assertFetchReqEquals(
      URLFetchRequest req,
      URLFetchRequest.RequestMethod reqMethod,
      String url,
      boolean hasPayload,
      int retryCount,
      String taskName,
      int previousResponse) {
    assertThat(req.getMethod()).isEqualTo(reqMethod);
    assertThat(req.getUrl()).isEqualTo(url);
    assertThat(req.hasPayload()).isEqualTo(hasPayload);
    assertRetryCountEquals(retryCount, req);
    assertExecutionCountEquals(retryCount, req);
    assertPreviousResponseEquals(previousResponse, req);
    assertQueueNameEquals("default", req);
    assertTaskNameEquals(taskName, req);
    assertTargetEquals(SERVER_NAME, req);
  }

  private void waitForJobExecution() throws InterruptedException {
    int secondsToWait = 10;
    boolean executed = latch.await(secondsToWait, SECONDS);
    assertWithMessage("Job was not executed after %s seconds", secondsToWait)
        .that(executed)
        .isTrue();
    assertThat(providedFetchReqs).isNotEmpty();
  }

  private void assertRetryCountEquals(int expected, URLFetchRequest req) {
    assertHeaderEquals(UrlFetchJob.X_APPENGINE_TASK_RETRY_COUNT, Integer.toString(expected), req);
  }

  private void assertExecutionCountEquals(int expected, URLFetchRequest req) {
    assertHeaderEquals(
        UrlFetchJob.X_APPENGINE_TASK_EXECUTION_COUNT, Integer.toString(expected), req);
  }

  private void assertPreviousResponseEquals(int expected, URLFetchRequest req) {
    // If zero, we assert that it's totally missing.
    if (expected == 0) {
      assertHeaderMissing(UrlFetchJob.X_APPENGINE_TASK_PREVIOUS_RESPONSE, req);
    } else {
      assertHeaderEquals(
          UrlFetchJob.X_APPENGINE_TASK_PREVIOUS_RESPONSE, Integer.toString(expected), req);
    }
  }

  private void assertQueueNameEquals(String expected, URLFetchRequest req) {
    assertHeaderEquals(UrlFetchJob.X_APPENGINE_QUEUE_NAME, expected, req);
  }

  private void assertTaskNameEquals(String expected, URLFetchRequest req) {
    assertHeaderEquals(UrlFetchJob.X_APPENGINE_TASK_NAME, expected, req);
  }

  private void assertTargetEquals(String expected, URLFetchRequest req) {
    assertHeaderEquals(UrlFetchJob.X_APPENGINE_SERVER_NAME, expected, req);
  }

  private void assertHeaderEquals(String header, String expected, URLFetchRequest req) {
    boolean found = false;
    for (URLFetchRequest.Header h : req.getHeaderList()) {
      if (h.getKey().equals(header)) {
        assertThat(h.getValue()).isEqualTo(expected);
        found = true;
        break;
      }
    }
    assertWithMessage("header %s not found", header).that(found).isTrue();
  }

  private void assertHeaderMissing(String header, URLFetchRequest req) {
    for (URLFetchRequest.Header h : req.getHeaderList()) {
      assertWithMessage("header %s found with value %s", header, h.getValue())
          .that(h.getKey())
          .isNotEqualTo(header);
    }
  }
}
