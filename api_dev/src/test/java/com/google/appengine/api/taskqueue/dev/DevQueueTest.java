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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.QueueXml;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * Tests for local dev queue. These tests mock out all calls to the Quartz {@link Scheduler}.
 *
 */
@RunWith(JUnit4.class)
public class DevQueueTest {

  private DevPushQueue queue;
  private Scheduler schedulerMock;
  private LocalTaskQueueCallback callbackMock;

  @Before
  public void setUp() throws Exception {
    schedulerMock = mock(Scheduler.class);
    callbackMock = mock(LocalTaskQueueCallback.class);
    queue =
        new DevPushQueue(
            QueueXml.defaultEntry(),
            schedulerMock,
            "http://localhost:8080",
            Clock.DEFAULT,
            callbackMock);
  }

  private TaskQueueAddRequest.Builder newAddRequest(long execTime) {
    return TaskQueueAddRequest.newBuilder()
        .setQueueName(ByteString.copyFromUtf8(QueueXml.defaultEntry().getName()))
        .setEtaUsec(execTime)
        .setMethod(TaskQueueAddRequest.RequestMethod.GET)
        .setUrl(ByteString.copyFromUtf8("/my/url"));
  }

  @Test
  public void testAdd_NamedTask() throws Exception {
    TaskQueueAddRequest.Builder add =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("the name"));

    when(schedulerMock.getJobDetail("the name", "default")).thenReturn(null);
    when(schedulerMock.scheduleJob(isA(JobDetail.class), isA(SimpleTrigger.class)))
        .thenReturn(null);
    TaskQueueAddResponse resp = queue.add(add);
    assertThat(resp.getChosenTaskName().toStringUtf8()).isEmpty();
  }

  @Test
  public void testAdd_UnnamedTask() throws Exception {
    TaskQueueAddRequest.Builder add = newAddRequest(1000);

    when(schedulerMock.getJobDetail(any(), eq("default"))).thenReturn(null);
    when(schedulerMock.scheduleJob(isA(JobDetail.class), isA(SimpleTrigger.class)))
        .thenReturn(null);
    TaskQueueAddResponse resp = queue.add(add);
    ArgumentCaptor<String> taskName = ArgumentCaptor.forClass(String.class);
    verify(schedulerMock).getJobDetail(taskName.capture(), eq("default"));
    assertThat(resp.getChosenTaskName().toStringUtf8()).isEqualTo(taskName.getValue());
  }

  @Test
  public void testAddDupe() throws Exception {
    TaskQueueAddRequest.Builder addRequest =
        newAddRequest(1000).setTaskName(ByteString.copyFromUtf8("task1"));

    when(schedulerMock.getJobDetail("task1", "default"))
        .thenReturn(new JobDetail("name", "group", UrlFetchJob.class));

    ApiProxy.ApplicationException exception =
        assertThrows(ApiProxy.ApplicationException.class, () -> queue.add(addRequest));
    assertThat(exception.getApplicationError()).isEqualTo(ErrorCode.TASK_ALREADY_EXISTS_VALUE);
  }

  @Test
  public void testDelete() throws Exception {
    when(schedulerMock.deleteJob("task1", "default")).thenReturn(true).thenReturn(false);

    boolean deleted = queue.deleteTask("task1");
    assertThat(deleted).isTrue();
    deleted = queue.deleteTask("task1");
    assertThat(deleted).isFalse();
    verify(schedulerMock, times(2)).deleteJob("task1", "default");
  }

  @Test
  public void testGetStateInfo() throws Exception {
    List<JobDetail> jobDetails = Lists.newArrayList();
    List<SimpleTrigger> triggers = Lists.newArrayList();
    List<String> jobNames = Lists.newArrayList();
    long eta = 1000000;
    for (int i = 0; i < 10; i++) {
      TaskQueueAddRequest.Builder addRequest = newAddRequest(eta - (i * 1000));
      String taskName = "task" + i;
      addRequest.setTaskName(ByteString.copyFromUtf8(taskName));
      jobNames.add(taskName);
      JobDetail jd =
          new UrlFetchJobDetail(
              taskName,
              "default",
              addRequest,
              "http://localhost:8080",
              callbackMock,
              QueueXml.defaultEntry(),
              null);
      jobDetails.add(jd);
      SimpleTrigger trig =
          new SimpleTrigger(taskName, "default", new Date(addRequest.getEtaUsec() / 1000));
      triggers.add(trig);
    }
    when(schedulerMock.getJobNames("default"))
        .thenReturn(jobNames.toArray(new String[jobNames.size()]));
    Iterator<JobDetail> jdIter = jobDetails.iterator();
    Iterator<SimpleTrigger> trigIter = triggers.iterator();
    for (String jobName : jobNames) {
      when(schedulerMock.getJobDetail(jobName, "default")).thenReturn(jdIter.next());
      when(schedulerMock.getTriggersOfJob(jobName, "default"))
          .thenReturn(new Trigger[] {trigIter.next()});
    }

    QueueStateInfo info = queue.getStateInfo();
    assertThat(info.getCountTasks()).isEqualTo(10);
    Iterator<String> reversedJobNames = Lists.reverse(jobNames).iterator();
    for (QueueStateInfo.TaskStateInfo taskInfo : info.getTaskInfo()) {
      assertThat(taskInfo.getTaskName()).isEqualTo(reversedJobNames.next());
    }
  }

  @Test
  public void testFlush() throws SchedulerException {
    when(schedulerMock.getJobNames("default")).thenReturn(new String[] {"job1", "job2"});
    when(schedulerMock.deleteJob("job1", "default")).thenReturn(true);
    when(schedulerMock.deleteJob("job2", "default")).thenReturn(true);

    queue.flush();
    verify(schedulerMock).deleteJob("job1", "default");
    verify(schedulerMock).deleteJob("job2", "default");
  }

  public static final class MyJob implements Job {
    private static JobExecutionContext calledWith = null;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      calledWith = context;
    }
  }

  @Test
  public void testRunTask_Success() throws Exception {
    MyJob.calledWith = null;
    TaskQueueAddRequest.Builder addRequest = newAddRequest(1000);
    JobDetail jd =
        new UrlFetchJobDetail(
            "task1",
            "default",
            addRequest,
            "http://localhost:8080",
            callbackMock,
            QueueXml.defaultEntry(),
            null) {
          @Override
          public Class<?> getJobClass() {
            return MyJob.class;
          }
        };

    when(schedulerMock.getJobDetail("task1", "default")).thenReturn(jd);

    assertThat(queue.runTask("task1")).isTrue();
    verify(schedulerMock).getJobDetail("task1", "default");
    UrlFetchJobDetail jobDetail = (UrlFetchJobDetail) MyJob.calledWith.getJobDetail();
    assertThat(addRequest).isSameInstanceAs(jobDetail.getAddRequest());
  }

  @Test
  public void testRunTask_Failure() throws Exception {
    MyJob.calledWith = null;

    when(schedulerMock.getJobDetail("task1", "default")).thenThrow(new SchedulerException("boom"));

    assertThat(queue.runTask("task1")).isFalse();
    verify(schedulerMock).getJobDetail("task1", "default");
    assertThat(MyJob.calledWith).isNull();
  }
}
