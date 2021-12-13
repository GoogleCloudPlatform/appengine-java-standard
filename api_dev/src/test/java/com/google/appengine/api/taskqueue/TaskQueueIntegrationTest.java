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

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withDefaults;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withMethod;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withRetryOptions;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withTaskName;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TaskQueueIntegrationTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  public static final String PULL_QUEUE_NAME = "pull-queue";

  private LocalServiceTestHelper helper;

  private Queue defaultQueue;
  private Queue pullQueue;
  private Queue unknownQueue;

  private JavaFakeClock clock;

  @Before
  public void setUp() throws Exception {
    QueueApiHelper apiHelper = new QueueApiHelper();
    defaultQueue = new QueueImpl(Queue.DEFAULT_QUEUE, apiHelper);
    pullQueue = new QueueImpl(PULL_QUEUE_NAME, apiHelper);
    unknownQueue = new QueueImpl("unknown-queue", apiHelper);
    File defaultXml = temporaryFolder.newFile("default.xml");
    try (FileOutputStream fout = new FileOutputStream(defaultXml)) {
      Resources.copy(getClass().getResource("default.xml"), fout);
    }
    helper =
        new LocalServiceTestHelper(
            new LocalTaskQueueTestConfig().setQueueXmlPath(defaultXml.getAbsolutePath()),
            new LocalDatastoreServiceTestConfig());
    clock = new JavaFakeClock();
    helper.setClock(clock);
    helper.setUp();
  }

  @After
  public void tearDownHelper() throws Exception {
    helper.tearDown();
    // See b/158563773 To avoid flake remaining async api call from a previous tests.
    Thread.sleep(300);
  }

  /** Sets value of fake clock for currently executing task queue stub. */
  void setCurrentMillis(long currentMillis) {
    clock.setCurrentMillis(currentMillis);
  }

  @Test
  public void testPurge() throws Exception {
    defaultQueue.purge();
  }

  @Test
  public void testLeaseTasks() throws Exception {
    pullQueue.add(withTaskName("task1").etaMillis(10000L).method(TaskOptions.Method.PULL));
    pullQueue.add(withTaskName("task2").etaMillis(10000L).method(TaskOptions.Method.PULL));
    List<TaskHandle> result = pullQueue.leaseTasks(12340, MILLISECONDS, 100);
    assertThat(result).hasSize(2);
  }

  @Test
  public void testLeaseTasksNoResult() throws Exception {
    List<TaskHandle> result = pullQueue.leaseTasks(12340, MILLISECONDS, 100);
    assertThat(result).isEmpty();
  }

  @Test
  public void testLeaseTasksNoResultAsync() throws Exception {
    List<TaskHandle> result = pullQueue.leaseTasksAsync(12340, MILLISECONDS, 100).get();
    assertThat(result).isEmpty();
  }

  @Test
  public void testLeaseTasksByTagSpecified() throws Exception {
    String tag = "tag";

    pullQueue.add(withTaskName("task1").tag(tag).method(TaskOptions.Method.PULL));
    pullQueue.add(withTaskName("task2").tag(tag).method(TaskOptions.Method.PULL));

    List<TaskHandle> result = pullQueue.leaseTasksByTag(12340, MILLISECONDS, 100, tag);
    assertThat(result).hasSize(2);

    TaskHandle task1 = result.get(0);
    assertThat(task1.getName()).isEqualTo("task1");
    assertThat(task1.getTag()).isEqualTo(tag);

    TaskHandle task2 = result.get(1);
    assertThat(task2.getName()).isEqualTo("task2");
    assertThat(task2.getTag()).isEqualTo(tag);
  }

  @Test
  public void testLeaseTasksByTagUnpecified() throws Exception {
    String tag = "tag";

    pullQueue.add(withTaskName("task1").tag(tag).method(TaskOptions.Method.PULL));
    pullQueue.add(withTaskName("task2").tag(tag).method(TaskOptions.Method.PULL));

    List<TaskHandle> result = pullQueue.leaseTasksByTag(12340, MILLISECONDS, 100, null);
    assertThat(result).hasSize(2);

    TaskHandle task1 = result.get(0);
    assertThat(task1.getName()).isEqualTo("task1");
    assertThat(task1.getTag()).isEqualTo(tag);

    TaskHandle task2 = result.get(1);
    assertThat(task2.getName()).isEqualTo("task2");
    assertThat(task2.getTag()).isEqualTo(tag);
  }

  @Test
  public void testModifyTaskLease() throws Exception {
    TaskHandle handle =
        pullQueue.add(withTaskName("task1").etaMillis(1000000L).method(TaskOptions.Method.PULL));
    assertThat(handle.getEtaMillis()).isEqualTo(1000000L);
    setCurrentMillis(13337);
    TaskHandle modifiedHandle = pullQueue.modifyTaskLease(handle, 30L, SECONDS);
    assertThat(modifiedHandle.getEtaMillis()).isEqualTo(43337);
  }

  @Test
  public void testModifyTaskLeaseExpired() throws Exception {
    TaskHandle handle =
        pullQueue.add(withTaskName("task1").etaMillis(1L).method(TaskOptions.Method.PULL));
    assertThat(handle.getEtaMillis()).isEqualTo(1L);
    setCurrentMillis(13337);
    assertThrows(
        IllegalStateException.class, () -> pullQueue.modifyTaskLease(handle, 30L, SECONDS));
  }

  @Test
  public void testDeleteTask() throws Exception {
    LocalTaskQueue taskQueue = LocalTaskQueueTestConfig.getLocalTaskQueue();
    TaskOptions options = withTaskName("Task1").etaMillis(1);

    assertThat(taskQueue.getQueueStateInfo().get(Queue.DEFAULT_QUEUE).getCountTasks()).isEqualTo(0);
    defaultQueue.add(options);
    assertThat(taskQueue.getQueueStateInfo().get(Queue.DEFAULT_QUEUE).getCountTasks()).isEqualTo(1);
    defaultQueue.deleteTask("Task1");
    assertThat(taskQueue.getQueueStateInfo().get(Queue.DEFAULT_QUEUE).getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testDeleteTaskList() throws Exception {
    LocalTaskQueue taskQueue = LocalTaskQueueTestConfig.getLocalTaskQueue();

    List<TaskOptions> tasksToAdd = new ArrayList<>(3);
    tasksToAdd.add(withTaskName("Task1"));
    tasksToAdd.add(withTaskName("Task2"));
    tasksToAdd.add(withTaskName("Task3"));

    defaultQueue.add(tasksToAdd);
    assertThat(taskQueue.getQueueStateInfo().get("default").getCountTasks()).isEqualTo(3);

    List<TaskHandle> tasksToDelete = new ArrayList<>(3);
    for (TaskOptions options : tasksToAdd) {
      tasksToDelete.add(new TaskHandle(options, defaultQueue.getQueueName()));
    }

    defaultQueue.deleteTask(tasksToDelete);
    assertThat(taskQueue.getQueueStateInfo().get("default").getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testDeleteTaskListAsync() throws Exception {
    LocalTaskQueue taskQueue = LocalTaskQueueTestConfig.getLocalTaskQueue();

    List<TaskOptions> tasksToAdd = new ArrayList<>(3);
    tasksToAdd.add(withTaskName("Task1"));
    tasksToAdd.add(withTaskName("Task2"));
    tasksToAdd.add(withTaskName("Task3"));

    defaultQueue.add(tasksToAdd);
    assertThat(taskQueue.getQueueStateInfo().get("default").getCountTasks()).isEqualTo(3);

    List<TaskHandle> tasksToDelete = new ArrayList<>(3);
    for (TaskOptions options : tasksToAdd) {
      tasksToDelete.add(new TaskHandle(options, defaultQueue.getQueueName()));
    }

    defaultQueue.deleteTaskAsync(tasksToDelete).get();
    assertThat(taskQueue.getQueueStateInfo().get("default").getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testDeleteQueueNameMismatch() throws Exception {
    defaultQueue.add(withTaskName("Task1"));
    assertThrows(
        QueueNameMismatchException.class,
        () -> defaultQueue.deleteTask(new TaskHandle(withTaskName("Task1"), "InvalidQueueName")));
  }

  @Test
  public void testQueueFactory() throws Exception {
    assertThat(QueueFactory.getDefaultQueue().getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
    assertThat(QueueFactory.getDefaultQueue().getQueueName())
        .isEqualTo(QueueFactory.getQueue(Queue.DEFAULT_QUEUE).getQueueName());
    assertThat(QueueFactory.getQueue("AQueueName").getQueueName()).isEqualTo("AQueueName");
    assertThat(QueueFactory.getQueue("AQueueName").getQueueName())
        .isEqualTo(QueueFactory.getQueue("AQueueName").getQueueName());
  }

  @Test
  public void testBulkAddPullMultipleTasks() throws Exception {
    List<TaskOptions> taskOptionsList = new ArrayList<>();
    TaskOptions task1 =
        withTaskName("T1")
            .payload(new byte[] {1, 2, 3})
            .etaMillis(123456L)
            .method(TaskOptions.Method.PULL);
    TaskOptions task2 =
        withTaskName("T2")
            .payload(new byte[] {5, 6, 7})
            .etaMillis(123456L)
            .method(TaskOptions.Method.PULL);
    TaskOptions task3 =
        withTaskName("T3")
            .payload(new byte[] {9, 0, 1})
            .etaMillis(123456L)
            .method(TaskOptions.Method.PULL);
    taskOptionsList.add(task1);
    taskOptionsList.add(task2);
    taskOptionsList.add(task3);

    List<TaskHandle> addResult = pullQueue.add(taskOptionsList);
    assertThat(addResult).hasSize(3);
  }

  @Test
  public void testBulkAddDefaultTask() throws Exception {
    defaultQueue.add();
  }

  @Test
  public void testBulkAddDefaultTaskVarArguments() throws Exception {
    List<TaskHandle> tasks =
        defaultQueue.add(Arrays.asList(withDefaults(), withDefaults(), withDefaults()));

    for (TaskHandle task : tasks) {
      assertThat(task.getName()).startsWith("task");
      assertThat(task.getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
    }
  }

  @Test
  public void testBulkAddGetTask() throws Exception {
    defaultQueue.add(withMethod(TaskOptions.Method.GET));
  }

  static byte[] makeLongString(int length) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) 'A');
    return bytes;
  }

  @Test
  public void testBulkTxTaskTooLarge() throws Exception {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    TaskOptions[] smallerTasks = {
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 4)),
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 4)),
    };

    Transaction txn1 = datastore.beginTransaction();
    pullQueue.add(txn1, Arrays.asList(smallerTasks));
    txn1.commit();
    TaskOptions[] biggerTasks = {
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 2 + 1)),
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 2 + 1)),
    };

    Transaction txn2 = datastore.beginTransaction();
    assertThrows(
        IllegalArgumentException.class, () -> defaultQueue.add(txn2, Arrays.asList(biggerTasks)));
    txn2.commit();
  }

  @Test
  public void testBulkAddEmptyName() throws Exception {
    TaskHandle handle = defaultQueue.add(withTaskName(""));
    assertThat(handle.getName()).startsWith("task");
    assertThat(handle.getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
  }

  @Test
  public void testBulkAddNullName() throws Exception {
    TaskHandle handle = defaultQueue.add(withTaskName(null));
    assertThat(handle.getName()).startsWith("task");
    assertThat(handle.getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
  }

  @Test
  public void testBulkAddSetName() throws Exception {
    TaskHandle handle = defaultQueue.add(withTaskName("task1"));
    assertThat(handle.getName()).isEqualTo("task1");
    assertThat(handle.getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
  }

  @Test
  public void testBulkAddContentTypeForPostWithParamsIgnored() throws Exception {
    // The user-provided content-type should be ignored.
    defaultQueue.add(
        withMethod(TaskOptions.Method.POST)
            .header("Content-Type", "text/plain")
            .param("Foo", "Bar"));
  }

  @Test
  public void testBulkAddTransactionalTaskNullTaskName() throws Exception {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    Transaction txn = datastore.beginTransaction();
    TaskHandle task = defaultQueue.add(txn, withDefaults());
    txn.commit();

    assertThat(task.getName()).startsWith("task");
    assertThat(task.getQueueName()).isEqualTo(defaultQueue.getQueueName());
  }

  @Test
  public void testBulkAddTransactionalTaskEmptyTaskName() throws Exception {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    Transaction txn = datastore.beginTransaction();
    TaskHandle task = defaultQueue.add(txn, withTaskName(""));
    txn.commit();

    assertThat(task.getName()).startsWith("task");
    assertThat(task.getQueueName()).isEqualTo(defaultQueue.getQueueName());
  }

  @Test
  public void testBulkAddTransactionalVarArguments() throws Exception {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    Transaction txn = datastore.beginTransaction();
    List<TaskHandle> tasks =
        defaultQueue.add(txn, Arrays.asList(withTaskName(""), withTaskName(""), withTaskName("")));
    txn.commit();

    for (TaskHandle task : tasks) {
      assertThat(task.getName()).startsWith("task");
      assertThat(task.getQueueName()).isEqualTo(defaultQueue.getQueueName());
    }
  }

  @Test
  public void testBulkAddUnknownQueueException() throws Exception {
    assertThrows(IllegalStateException.class, () -> unknownQueue.add());
  }

  @Test
  public void testPurgeQueueUnknownQueueException() throws Exception {
    assertThrows(IllegalStateException.class, () -> unknownQueue.purge());
  }

  @Test
  public void testDeleteTaskUnknownQueueException() throws Exception {
    assertThrows(IllegalStateException.class, () -> unknownQueue.deleteTask("task"));
  }

  @Test
  public void testLeaseTasksUnknownQueueException() throws Exception {
    assertThrows(
        IllegalStateException.class, () -> unknownQueue.leaseTasks(60000, MILLISECONDS, 100));
  }

  @Test
  public void testBulkAddTaskResultUnknownQueueException() throws Exception {
    assertThrows(
        IllegalStateException.class,
        () -> unknownQueue.add(Arrays.asList(withTaskName("a"), withTaskName("b"))));
  }

  @Test
  public void testBulkAddRequestWithRetryParameters() {
    LocalTaskQueue taskQueue = LocalTaskQueueTestConfig.getLocalTaskQueue();

    defaultQueue.add(
        withRetryOptions(
            withTaskRetryLimit(10)
                .taskAgeLimitSeconds(86400)
                .minBackoffSeconds(0.1)
                .maxBackoffSeconds(1000)
                .maxDoublings(10)));
    TaskQueueRetryParameters retryParameters =
        taskQueue
            .getQueueStateInfo()
            .get(Queue.DEFAULT_QUEUE)
            .getTaskInfo()
            .get(0)
            .getRetryParameters();
    assertThat(retryParameters.getRetryLimit()).isEqualTo(10);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void testAsyncDeleteNeverWaits() {
    TaskHandle handle =
        new TaskHandle(TaskOptions.Builder.withTaskName("taskName"), "unknown-queue");
    List<TaskHandle> handleList = Lists.newArrayList(handle);

    // We never wait for any of the these futures.
    unknownQueue.deleteTaskAsync("taskName");
    unknownQueue.deleteTaskAsync(handle);
    unknownQueue.deleteTaskAsync(handleList);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void testAsyncLeaseNeverWaits() {
    // We never wait for any of the these futures.
    unknownQueue.leaseTasksAsync(
        LeaseOptions.Builder.withTag("tag").countLimit(10).leasePeriod(4, DAYS));
    unknownQueue.leaseTasksAsync(4, DAYS, 10);
    unknownQueue.leaseTasksByTagAsync(4, DAYS, 10, "tag");
    unknownQueue.leaseTasksByTagBytesAsync(4, DAYS, 10, "tag".getBytes(Charset.defaultCharset()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void testAsyncAddNeverWaits() {
    TaskOptions opts = TaskOptions.Builder.withDefaults();
    Transaction txn = null;
    Iterable<TaskOptions> optsIter = Lists.newArrayList(opts);

    // We never wait for any of the these futures.
    unknownQueue.addAsync();
    unknownQueue.addAsync(opts);
    unknownQueue.addAsync(txn, opts);
    unknownQueue.addAsync(optsIter);
    unknownQueue.addAsync(txn, optsIter);
  }

  private static class JavaFakeClock implements Clock {
    private long currentMillis = 1508362410000000L;

    @Override
    public long getCurrentTime() {
      return currentMillis;
    }

    public void setCurrentMillis(long currentMillis) {
      this.currentMillis = currentMillis;
    }
  }
}
