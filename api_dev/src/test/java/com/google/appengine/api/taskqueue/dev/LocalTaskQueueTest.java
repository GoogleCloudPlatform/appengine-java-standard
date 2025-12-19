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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.taskqueue.QueueConstants;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueDeleteRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueDeleteResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueFetchQueueStatsRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueuePurgeQueueRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueQueryAndOwnTasksRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueQueryAndOwnTasksResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalRpcService.Status;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.utils.config.QueueXml;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test for local dev queue service.
 *
 */
@RunWith(JUnit4.class)
public class LocalTaskQueueTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private LocalTaskQueue localService;
  private TaskQueueBulkAddRequest.Builder bulkAddRequest;
  private TaskQueueAddRequest.Builder addRequest1;
  private TaskQueueAddRequest.Builder addRequest2;
  private TaskQueueAddRequest.Builder addRequest3;
  private TaskQueueBulkAddRequest.Builder bulkAddPullRequest;
  private TaskQueueAddRequest.Builder addPullRequest1;
  private TaskQueueAddRequest.Builder addPullRequest2;
  private TaskQueueAddRequest.Builder addPullRequest3;
  private TaskQueuePurgeQueueRequest.Builder purgeQueueRequest;
  private TaskQueuePurgeQueueRequest.Builder purgeNonExistentQueueRequest;

  private QueueXml.Entry entry;
  private TaskQueueBulkAddResponse.Builder expectedBulkAddResponse;
  private TaskQueueBulkAddResponse.TaskResult.Builder expectedTaskResult1;
  private TaskQueueBulkAddResponse.TaskResult.Builder expectedTaskResult2;
  private TaskQueueBulkAddResponse.TaskResult.Builder expectedTaskResult3;

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> mockDelegate;

  // Let the taskqueue read our custom queue config file, so that we will have a pull queue
  // for testing.
  private static final String QUEUE_XML_PATH = "custom_queue.xml";

  private LocalServiceTestHelper helper;

  private void initLocalTaskQueue(Clock clock) {
    LocalServerEnvironment localServerEnvironment = mock(LocalServerEnvironment.class);
    when(localServerEnvironment.getAddress()).thenReturn("localhost");
    when(localServerEnvironment.getPort()).thenReturn(8080);
    when(localServerEnvironment.getAppDir()).thenReturn(new File("."));
    LocalServiceContext lsc = mock(LocalServiceContext.class);
    when(lsc.getLocalServerEnvironment()).thenReturn(localServerEnvironment);
    when(lsc.getClock()).thenReturn(clock);

    localService.init(lsc, new HashMap<String, String>());
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    Path queueXmlPath = temporaryFolder.newFile(QUEUE_XML_PATH).toPath();
    try (InputStream in = getClass().getResourceAsStream(QUEUE_XML_PATH)) {
      Files.copy(in, queueXmlPath, REPLACE_EXISTING);
    }
    helper =
        new LocalServiceTestHelper(
            new LocalTaskQueueTestConfig()
                .setQueueXmlPath(queueXmlPath.toAbsolutePath().toString()));

    // Sets up helper first, so that it won't overwrite the environment.
    helper.setUp();

    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    initLocalTaskQueue(Clock.DEFAULT);
    localService.start();

    entry = QueueXml.defaultEntry();
    bulkAddRequest = TaskQueueBulkAddRequest.newBuilder();
    bulkAddPullRequest = TaskQueueBulkAddRequest.newBuilder();

    addRequest1 = bulkAddRequest.addAddRequestBuilder();
    addRequest1.setQueueName(ByteString.copyFromUtf8(entry.getName()));
    addRequest1.setTaskName(ByteString.copyFromUtf8("a-task-1"));
    addRequest1.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addRequest1.setMethod(TaskQueueAddRequest.RequestMethod.GET);
    addRequest1.setUrl(ByteString.copyFromUtf8("/my/url1"));

    addRequest2 = bulkAddRequest.addAddRequestBuilder();
    addRequest2.setQueueName(ByteString.copyFromUtf8(entry.getName()));
    addRequest2.setTaskName(ByteString.copyFromUtf8("a-task-2"));
    addRequest2.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addRequest2.setMethod(TaskQueueAddRequest.RequestMethod.GET);
    addRequest2.setUrl(ByteString.copyFromUtf8("/my/url2"));

    addRequest3 = bulkAddRequest.addAddRequestBuilder();
    addRequest3.setQueueName(ByteString.copyFromUtf8(entry.getName()));
    addRequest3.setTaskName(ByteString.copyFromUtf8("a-task-3"));
    addRequest3.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addRequest3.setMethod(TaskQueueAddRequest.RequestMethod.GET);
    addRequest3.setUrl(ByteString.copyFromUtf8("/my/url3"));

    addPullRequest1 = bulkAddPullRequest.addAddRequestBuilder();
    addPullRequest1.setQueueName(ByteString.copyFromUtf8("pull-queue"));
    addPullRequest1.setTaskName(ByteString.copyFromUtf8("a-task-0"));
    addPullRequest1.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addPullRequest1.setMode(TaskQueueMode.Mode.PULL);
    addPullRequest1.setBody(ByteString.copyFromUtf8("payload0"));
    addPullRequest1.setTag(ByteString.copyFromUtf8("tag"));

    addPullRequest2 = bulkAddPullRequest.addAddRequestBuilder();
    addPullRequest2.setQueueName(ByteString.copyFromUtf8("pull-queue"));
    addPullRequest2.setTaskName(ByteString.copyFromUtf8("a-task-1"));
    addPullRequest2.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addPullRequest2.setMode(TaskQueueMode.Mode.PULL);
    addPullRequest2.setBody(ByteString.copyFromUtf8("payload1"));

    addPullRequest3 = bulkAddPullRequest.addAddRequestBuilder();
    addPullRequest3.setQueueName(ByteString.copyFromUtf8("pull-queue"));
    addPullRequest3.setTaskName(ByteString.copyFromUtf8("a-task-2"));
    addPullRequest3.setEtaUsec(Clock.DEFAULT.getCurrentTime() * 1000);
    addPullRequest3.setMode(TaskQueueMode.Mode.PULL);
    addPullRequest3.setBody(ByteString.copyFromUtf8("payload2"));
    addPullRequest3.setTag(ByteString.copyFromUtf8("tag"));

    expectedBulkAddResponse = TaskQueueBulkAddResponse.newBuilder();
    expectedTaskResult1 = expectedBulkAddResponse.addTaskResultBuilder();
    expectedTaskResult1.setResult(ErrorCode.OK);
    expectedTaskResult2 = expectedBulkAddResponse.addTaskResultBuilder();
    expectedTaskResult2.setResult(ErrorCode.OK);
    expectedTaskResult3 = expectedBulkAddResponse.addTaskResultBuilder();
    expectedTaskResult3.setResult(ErrorCode.OK);

    purgeQueueRequest = TaskQueuePurgeQueueRequest.newBuilder();
    purgeQueueRequest.setQueueName(ByteString.copyFromUtf8(entry.getName()));

    purgeNonExistentQueueRequest = TaskQueuePurgeQueueRequest.newBuilder();
    purgeNonExistentQueueRequest.setQueueName(ByteString.copyFromUtf8("NonExistentQueue"));

    Thread.sleep(2); // Ensure bulkAddPullRequest tasks were added in the past.
  }

  @After
  public void tearDown() throws Exception {
    DevQueue.taskNameGenerator = null;
    localService.stop();
    helper.tearDown();
  }

  @Test
  public void testAddNamedTask() throws Exception {
    TaskQueueAddRequest.Builder request = bulkAddRequest.getAddRequestBuilder(0);
    TaskQueueAddResponse expectedResponse = TaskQueueAddResponse.getDefaultInstance();

    TaskQueueAddResponse response = localService.add(new Status(), request.build());
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void testAddUnNamedTask() throws Exception {
    TaskQueueAddRequest.Builder request = bulkAddRequest.getAddRequestBuilder(0);
    TaskQueueAddResponse.Builder expectedResponse = TaskQueueAddResponse.newBuilder();

    request.setTaskName(ByteString.copyFromUtf8(""));

    TaskQueueAddResponse response = localService.add(new Status(), request.buildPartial());
    assertThat(response.getChosenTaskName().toStringUtf8()).startsWith("task-");
    expectedResponse.setChosenTaskName(response.getChosenTaskName());
    assertThat(response).isEqualTo(expectedResponse.build());
  }

  @Test
  public void testAddTaskWithNegativeEta() throws Exception {
    TaskQueueAddRequest.Builder request = bulkAddRequest.getAddRequestBuilder(0).setEtaUsec(-1);
    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.add(new Status(), request.build()));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.INVALID_ETA_VALUE);
  }

  @Test
  public void testBulkAdd() throws Exception {
    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testBulkAddPullTasks() throws Exception {
    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testBulkAddPushTasksToPullQueue() throws Exception {
    for (TaskQueueAddRequest.Builder addRequest : bulkAddRequest.getAddRequestBuilderList()) {
      addRequest.setQueueName(ByteString.copyFromUtf8("pull-queue"));
    }

    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());

    assertThat(response.getTaskResult(0).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    assertThat(response.getTaskResult(1).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    assertThat(response.getTaskResult(2).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
  }

  @Test
  public void testBulkAddPullTasksToPushQueue() throws Exception {
    for (TaskQueueAddRequest.Builder addRequest : bulkAddPullRequest.getAddRequestBuilderList()) {
      addRequest.setQueueName(ByteString.copyFromUtf8(entry.getName()));
    }
    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(response.getTaskResult(0).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    assertThat(response.getTaskResult(1).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    assertThat(response.getTaskResult(2).getResult().getNumber())
        .isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
  }

  @Test
  public void testBulkAddNoName() throws Exception {
    addRequest1.setTaskName(ByteString.copyFromUtf8(""));
    addRequest2.setTaskName(ByteString.copyFromUtf8(""));
    addRequest3.setTaskName(ByteString.copyFromUtf8(""));

    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());

    assertThat(response.getTaskResult(0).getChosenTaskName().toStringUtf8()).startsWith("task-");
    assertThat(response.getTaskResult(1).getChosenTaskName().toStringUtf8()).startsWith("task-");
    assertThat(response.getTaskResult(2).getChosenTaskName().toStringUtf8()).startsWith("task-");

    expectedTaskResult1.setChosenTaskName(response.getTaskResult(0).getChosenTaskName());
    expectedTaskResult2.setChosenTaskName(response.getTaskResult(1).getChosenTaskName());
    expectedTaskResult3.setChosenTaskName(response.getTaskResult(2).getChosenTaskName());

    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testBulkAddPullNoName() throws Exception {
    addPullRequest1.clearTaskName();
    addPullRequest2.clearTaskName();
    addPullRequest3.clearTaskName();

    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddPullRequest.buildPartial());

    assertThat(response.getTaskResult(0).getChosenTaskName().toStringUtf8()).startsWith("task-");
    assertThat(response.getTaskResult(1).getChosenTaskName().toStringUtf8()).startsWith("task-");
    assertThat(response.getTaskResult(2).getChosenTaskName().toStringUtf8()).startsWith("task-");

    expectedTaskResult1.setChosenTaskName(response.getTaskResult(0).getChosenTaskName());
    expectedTaskResult2.setChosenTaskName(response.getTaskResult(1).getChosenTaskName());
    expectedTaskResult3.setChosenTaskName(response.getTaskResult(2).getChosenTaskName());

    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testBulkAddEmptyRequest() throws Exception {
    bulkAddRequest.clear();
    expectedBulkAddResponse.clear();

    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testBulkAddPullEmptyRequest() throws Exception {
    bulkAddPullRequest.clear();
    expectedBulkAddResponse.clear();

    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testMultipleQueuesNoDefault() throws Exception {
    localService.stop();
    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    initLocalTaskQueue(Clock.DEFAULT);
    QueueXml queueXml = makeQueueXml();
    localService.setQueueXml(queueXml);
    localService.start();

    // add an entry in the default queue, this makes sure that the default
    // queue is automagically added.
    Status status = new Status();
    TaskQueueBulkAddResponse response = localService.bulkAdd(status, bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());

    Map<String, QueueStateInfo> queueInfo = localService.getQueueStateInfo();
    assertThat(queueInfo).hasSize(queueXml.getEntries().size() + 1);
  }

  @Test
  public void testMultipleQueuesWithDefault() throws Exception {
    QueueXml queueXml = makeQueueXml();
    QueueXml.Entry tmpEntry = queueXml.addNewEntry();
    tmpEntry.setBucketSize(1);
    tmpEntry.setName(QueueXml.defaultEntry().getName());
    tmpEntry.setRate("2/d");
    queueXml.validateLastEntry();

    localService.stop();
    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    initLocalTaskQueue(Clock.DEFAULT);
    localService.setQueueXml(queueXml);
    localService.start();

    Status status = new Status();
    TaskQueueBulkAddResponse response = localService.bulkAdd(status, bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
    Map<String, QueueStateInfo> queueInfo = localService.getQueueStateInfo();
    assertThat(queueInfo).hasSize(queueXml.getEntries().size());
  }

  @Test
  public void testAddExistingTask() throws Exception {
    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());

    addRequest1.setTaskName(ByteString.copyFromUtf8("Unique1"));
    expectedTaskResult2.setResult(ErrorCode.TASK_ALREADY_EXISTS);
    addRequest3.setTaskName(ByteString.copyFromUtf8("Unique3"));

    TaskQueueBulkAddResponse existingTaskresponse =
        localService.bulkAdd(new Status(), bulkAddRequest.build());
    assertThat(existingTaskresponse).isEqualTo(expectedBulkAddResponse.build());
  }

  @Test
  public void testTransactionalTasks() throws Exception {
    DevQueue.taskNameGenerator = new AtomicInteger();
    final int tx = 321;

    localService.stop();
    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    initLocalTaskQueue(Clock.DEFAULT);
    QueueXml queueXml = makeQueueXml();
    localService.setQueueXml(queueXml);
    localService.start();

    addRequest1.getTransactionBuilder().setHandle(tx).setApp("foo");
    addRequest2.getTransactionBuilder().setHandle(tx).setApp("foo");
    addRequest3.getTransactionBuilder().setHandle(tx).setApp("foo");

    addRequest1.setTaskName(ByteString.copyFromUtf8(""));
    addRequest2.setTaskName(ByteString.copyFromUtf8(""));
    addRequest3.setTaskName(ByteString.copyFromUtf8(""));

    expectedTaskResult1.setChosenTaskName(ByteString.copyFromUtf8("task1"));
    expectedTaskResult2.setChosenTaskName(ByteString.copyFromUtf8("task2"));
    expectedTaskResult3.setChosenTaskName(ByteString.copyFromUtf8("task3"));

    Delegate<?> oldDelegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(mockDelegate);

    TaskQueueBulkAddRequest.Builder expectedAddActionsRequest = bulkAddRequest.clone();
    expectedAddActionsRequest.getAddRequestBuilder(0).setTaskName(ByteString.copyFromUtf8("task1"));
    expectedAddActionsRequest.getAddRequestBuilder(1).setTaskName(ByteString.copyFromUtf8("task2"));
    expectedAddActionsRequest.getAddRequestBuilder(2).setTaskName(ByteString.copyFromUtf8("task3"));

    when(
            mockDelegate.makeSyncCall(
                ApiProxy.getCurrentEnvironment(),
                "datastore_v3",
                "addActions",
                expectedAddActionsRequest.build().toByteArray()))
        .thenReturn(new byte[0]);

    // add an entry in the default queue, this makes sure that the default
    // queue is automagically added, but task is not (it's sent to datastore).
    TaskQueueBulkAddResponse response = localService.bulkAdd(new Status(), bulkAddRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.build());
    verify(mockDelegate).makeSyncCall(any(), any(), any(), any());

    Map<String, QueueStateInfo> queueInfo = localService.getQueueStateInfo();
    assertThat(queueInfo).hasSize(queueXml.getEntries().size() + 1);
    assertThat(queueInfo.get(addRequest1.getQueueName().toStringUtf8()).getCountTasks()).isEqualTo(0);

    // We have to restore the old delegate so that LocalServiceTestHelper won't break in tearDown
    ApiProxy.setDelegate(oldDelegate);
  }

  @Test
  public void testTransactionalTasksWithAddActionsFailure() throws Exception {
    DevQueue.taskNameGenerator = new AtomicInteger();
    final int tx = 321;
    final int error = 123;

    localService.stop();
    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    initLocalTaskQueue(Clock.DEFAULT);
    QueueXml queueXml = makeQueueXml();
    localService.setQueueXml(queueXml);
    localService.start();

    addRequest1.getTransactionBuilder().setHandle(tx).setApp("foo");
    addRequest2.getTransactionBuilder().setHandle(tx).setApp("foo");
    addRequest3.getTransactionBuilder().setHandle(tx).setApp("foo");

    addRequest1.setTaskName(ByteString.copyFromUtf8(""));
    addRequest2.setTaskName(ByteString.copyFromUtf8(""));
    addRequest3.setTaskName(ByteString.copyFromUtf8(""));

    expectedTaskResult1.setChosenTaskName(ByteString.copyFromUtf8("task1"));
    expectedTaskResult2.setChosenTaskName(ByteString.copyFromUtf8("task2"));
    expectedTaskResult3.setChosenTaskName(ByteString.copyFromUtf8("task3"));

    Delegate<?> oldDelegate = ApiProxy.getDelegate();
    ApiProxy.setDelegate(mockDelegate);

    TaskQueueBulkAddRequest.Builder expectedAddActionsRequest = bulkAddRequest.clone();
    expectedAddActionsRequest.getAddRequestBuilder(0).setTaskName(ByteString.copyFromUtf8("task1"));
    expectedAddActionsRequest.getAddRequestBuilder(1).setTaskName(ByteString.copyFromUtf8("task2"));
    expectedAddActionsRequest.getAddRequestBuilder(2).setTaskName(ByteString.copyFromUtf8("task3"));

    when(
            mockDelegate.makeSyncCall(
                ApiProxy.getCurrentEnvironment(),
                "datastore_v3",
                "addActions",
                expectedAddActionsRequest.build().toByteArray()))
        .thenThrow(new ApiProxy.ApplicationException(error, "mock"));

    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.bulkAdd(new Status(), bulkAddRequest.build()));
    assertThat(e.getApplicationError()).isEqualTo(error + ErrorCode.DATASTORE_ERROR_VALUE);
    verify(mockDelegate).makeSyncCall(any(), any(), any(), any());

    Map<String, QueueStateInfo> queueInfo = localService.getQueueStateInfo();
    assertThat(queueInfo).hasSize(queueXml.getEntries().size() + 1);
    assertThat(queueInfo.get(addRequest1.getQueueName().toStringUtf8()).getCountTasks())
        .isEqualTo(0);

    // We have to restore the old delegate so that LocalServiceTestHelper won't break in tearDown
    ApiProxy.setDelegate(oldDelegate);
  }

  private QueueXml makeQueueXml() {
    // add 2 entries not with the default name.
    QueueXml queueXML = new QueueXml();
    QueueXml.Entry tmpEntry = queueXML.addNewEntry();
    tmpEntry.setBucketSize(1);
    tmpEntry.setName("Aqueue");
    tmpEntry.setRate("2/d");
    tmpEntry = queueXML.addNewEntry();
    tmpEntry.setBucketSize(2);
    tmpEntry.setName("Bqueue");
    tmpEntry.setRate("2/m");
    queueXML.validateLastEntry();
    return queueXML;
  }

  @Test
  public void testBulkAddCreateTaskWithNoQueue() throws Exception {
    addRequest1.setQueueName(ByteString.copyFromUtf8("ThisIsNotAQueue"));
    addRequest2.setQueueName(ByteString.copyFromUtf8("ThisIsNotAQueue"));
    addRequest3.setQueueName(ByteString.copyFromUtf8("ThisIsNotAQueue"));

    ApiProxy.ApplicationException exception =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.bulkAdd(new Status(), bulkAddRequest.build()));
    assertThat(exception.getApplicationError()).isEqualTo(ErrorCode.UNKNOWN_QUEUE_VALUE);
  }

  @Test
  public void testInvalidTaskName() {
    addRequest2.setTaskName(ByteString.copyFromUtf8("invalid."));
    assertValidationException(ErrorCode.INVALID_TASK_NAME);
  }

  @Test
  public void testUnsetQueueName() {
    addRequest2.clearQueueName();
    assertValidationException(ErrorCode.INVALID_QUEUE_NAME);
  }

  @Test
  public void testEmptyQueueName() {
    addRequest2.setQueueName(ByteString.copyFromUtf8(""));
    assertValidationException(ErrorCode.INVALID_QUEUE_NAME);
  }

  @Test
  public void testInvalidQueueName() {
    addRequest2.setQueueName(ByteString.copyFromUtf8("invalid__"));
    assertValidationException(ErrorCode.INVALID_QUEUE_NAME);
  }

  @Test
  public void testUnsetUrl() {
    addRequest2.clearUrl();
    assertValidationException(ErrorCode.INVALID_URL);
  }

  @Test
  public void testNoPayload() {
    addPullRequest2.clearBody();
    assertValidationExceptionPull(ErrorCode.INVALID_REQUEST);
  }

  @Test
  public void testEmptyUrl() {
    addRequest2.setUrl(ByteString.copyFromUtf8(""));
    assertValidationException(ErrorCode.INVALID_URL);
  }

  @Test
  public void testUrlDoesNotStartWithSlash() {
    addRequest2.setUrl(ByteString.copyFromUtf8("does not start with /"));
    assertValidationException(ErrorCode.INVALID_URL);
  }

  @Test
  public void testUrlTooLong() {
    addRequest2.setUrl(
        ByteString.copyFromUtf8("/" + stringWithLength(QueueConstants.maxUrlLength())));
    assertValidationException(ErrorCode.INVALID_URL);
  }

  @Test
  public void testNegativeEta() {
    addRequest2.setEtaUsec(-1);
    assertValidationException(ErrorCode.INVALID_ETA);
  }

  @Test
  public void testEtaTooFarInTheFuture() {
    localService.stop();
    final long now = 100;
    long nowUsec = now * 1000;
    localService = LocalTaskQueueTestConfig.getLocalTaskQueue();
    Clock clock =
        new Clock() {
          @Override
          public long getCurrentTime() {
            return now;
          }
        };
    initLocalTaskQueue(clock);
    localService.start();

    addRequest1.setEtaUsec(LocalTaskQueue.getMaxEtaDeltaUsec()); // OK.
    addRequest2.setEtaUsec(LocalTaskQueue.getMaxEtaDeltaUsec() + nowUsec + 1); // Too distant.
    addRequest3.setEtaUsec(LocalTaskQueue.getMaxEtaDeltaUsec() + nowUsec); // OK.
    assertValidationException(ErrorCode.INVALID_ETA);
  }

  private String stringWithLength(int length) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      builder.append("y");
    }
    return builder.toString();
  }

  private void assertValidationException(ErrorCode ec) {
    expectedTaskResult1.setResult(ErrorCode.SKIPPED);
    expectedTaskResult2.setResult(ec);
    expectedTaskResult3.setResult(ErrorCode.SKIPPED);

    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddRequest.buildPartial());
    assertThat(response).isEqualTo(expectedBulkAddResponse.buildPartial());
  }

  private void assertValidationExceptionPull(ErrorCode ec) {
    expectedTaskResult1.setResult(ErrorCode.SKIPPED);
    expectedTaskResult2.setResult(ec);
    expectedTaskResult3.setResult(ErrorCode.SKIPPED);

    TaskQueueBulkAddResponse response =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(response).isEqualTo(expectedBulkAddResponse.buildPartial());
  }

  @Test
  public void testFetchQueueStats() throws Exception {
    TaskQueueFetchQueueStatsRequest request =
        TaskQueueFetchQueueStatsRequest.newBuilder()
            .addQueueName(ByteString.copyFromUtf8("default"))
            .build();
    TaskQueueFetchQueueStatsResponse response = localService.fetchQueueStats(new Status(), request);
    assertThat(response.getQueueStatsCount()).isEqualTo(1);
    TaskQueueFetchQueueStatsResponse.QueueStats stats = response.getQueueStats(0);
    assertThat(stats.hasScannerInfo()).isTrue();
    TaskQueueScannerQueueInfo scannerInfo = stats.getScannerInfo();
    assertThat(scannerInfo.hasRequestsInFlight()).isTrue();
    assertThat(scannerInfo.hasEnforcedRate()).isTrue();
    assertThat(
            (0 == stats.getNumTasks() && -1 == stats.getOldestEtaUsec())
                || (0 < stats.getNumTasks() && 0 <= stats.getOldestEtaUsec()))
        .isTrue();
    assertThat(scannerInfo.getEnforcedRate()).isAtLeast(0);
    assertThat(scannerInfo.getExecutedLastMinute()).isAtLeast(0);
    assertThat(scannerInfo.getRequestsInFlight()).isAtLeast(0);
  }

  @Test
  public void testFlushQueue() throws Exception {
    QueueStateInfo queueInfo;

    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(3);

    localService.flushQueue(entry.getName());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);

    localService.flushQueue(entry.getName());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testPurgeQueue() throws Exception {
    QueueStateInfo queueInfo;

    var unused1 = localService.bulkAdd(new Status(), bulkAddRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(3);

    var unused2 = localService.purgeQueue(new Status(), purgeQueueRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);

    var unused3 = localService.bulkAdd(new Status(), bulkAddRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(3);

    var unused4 = localService.purgeQueue(new Status(), purgeQueueRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testPurgeEmptyQueue() throws Exception {
    QueueStateInfo queueInfo;

    var unused1 = localService.purgeQueue(new Status(), purgeQueueRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);

    var unused2 = localService.bulkAdd(new Status(), bulkAddRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(3);

    var unused3 = localService.purgeQueue(new Status(), purgeQueueRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);

    var unused4 = localService.purgeQueue(new Status(), purgeQueueRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(0);
  }

  @Test
  public void testPurgeNonExistentQueue() throws Exception {
    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.purgeQueue(new Status(), purgeNonExistentQueueRequest.build()));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.UNKNOWN_QUEUE_VALUE);
  }

  @Test
  public void testDeleteTask() throws Exception {
    QueueStateInfo queueInfo;

    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(3);

    boolean success =
        localService.deleteTask(
            entry.getName(), bulkAddRequest.getAddRequest(1).getTaskName().toStringUtf8());
    assertThat(success).isTrue();
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(2);

    success =
        localService.deleteTask(
            entry.getName(), bulkAddRequest.getAddRequest(1).getTaskName().toStringUtf8());
    assertThat(success).isFalse();
    queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(2);
  }

  @Test
  public void testDelete() throws Exception {
    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());

    TaskQueueDeleteRequest deleteRequest =
        TaskQueueDeleteRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(entry.getName()))
            .addTaskName(bulkAddRequest.getAddRequest(1).getTaskName())
            .build();

    TaskQueueDeleteResponse deleteResponse = localService.delete(new Status(), deleteRequest);
    assertThat(deleteResponse.getResultCount()).isEqualTo(1);
    assertThat(deleteResponse.getResult(0)).isEqualTo(ErrorCode.OK);

    QueueStateInfo queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(2);
  }

  @Test
  public void testDeleteMultiple() throws Exception {
    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());

    TaskQueueDeleteRequest deleteRequest =
        TaskQueueDeleteRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(entry.getName()))
            .addTaskName(bulkAddRequest.getAddRequest(1).getTaskName())
            .addTaskName(bulkAddRequest.getAddRequest(0).getTaskName())
            .build();

    TaskQueueDeleteResponse deleteResponse = localService.delete(new Status(), deleteRequest);
    assertThat(deleteResponse.getResultCount()).isEqualTo(2);
    assertThat(deleteResponse.getResult(0)).isEqualTo(ErrorCode.OK);
    assertThat(deleteResponse.getResult(1)).isEqualTo(ErrorCode.OK);

    QueueStateInfo queueInfo = localService.getQueueStateInfo().get(entry.getName());
    assertThat(queueInfo.getCountTasks()).isEqualTo(1);
  }

  @Test
  public void testDeleteUnknownTask() throws Exception {
    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());

    TaskQueueDeleteRequest deleteRequest =
        TaskQueueDeleteRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(entry.getName()))
            .addTaskName(bulkAddRequest.getAddRequest(1).getTaskName())
            .build();

    TaskQueueDeleteResponse deleteResponse = localService.delete(new Status(), deleteRequest);
    assertThat(deleteResponse.getResultCount()).isEqualTo(1);
    assertThat(deleteResponse.getResult(0)).isEqualTo(ErrorCode.OK);

    deleteResponse = localService.delete(new Status(), deleteRequest);
    assertThat(deleteResponse.getResultCount()).isEqualTo(1);
    assertThat(deleteResponse.getResult(0)).isEqualTo(ErrorCode.UNKNOWN_TASK);
  }

  @Test
  public void testQueryAndOwnTasks() throws Exception {
    class MockClock implements Clock {
      @Override
      public long getCurrentTime() {
        // Force nowMillis of QueryAndOwnTasks is guaranteed to be bigger than task eta.
        return Clock.DEFAULT.getCurrentTime() + 1000;
      }
    }
    initLocalTaskQueue(new MockClock());

    TaskQueueBulkAddResponse sbresponse =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(sbresponse).isEqualTo(expectedBulkAddResponse.build());

    TaskQueueQueryAndOwnTasksRequest request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("pull-queue"))
            .setLeaseSeconds(10)
            .setMaxTasks(5)
            .build();

    TaskQueueQueryAndOwnTasksResponse response =
        localService.queryAndOwnTasks(new Status(), request);

    assertThat(response.getTaskCount()).isEqualTo(3);
    for (int i = 0; i < response.getTaskCount(); ++i) {
      assertThat(response.getTask(i).getTaskName().toStringUtf8()).isEqualTo("a-task-" + i);
      assertThat(response.getTask(i).getBody().toStringUtf8()).isEqualTo("payload" + i);
    }
  }

  @Test
  public void testQueryAndOwnTasksWithTags() throws Exception {
    class MockClock implements Clock {
      @Override
      public long getCurrentTime() {
        // Force nowMillis of QueryAndOwnTasks is guaranteed to be bigger than task eta.
        return Clock.DEFAULT.getCurrentTime() + 1000;
      }
    }
    initLocalTaskQueue(new MockClock());

    TaskQueueBulkAddResponse sbresponse =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(sbresponse).isEqualTo(expectedBulkAddResponse.build());

    TaskQueueQueryAndOwnTasksRequest request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("pull-queue"))
            .setLeaseSeconds(10)
            .setMaxTasks(5)
            .setGroupByTag(true)
            .setTag(ByteString.copyFromUtf8("tag"))
            .build();

    TaskQueueQueryAndOwnTasksResponse response =
        localService.queryAndOwnTasks(new Status(), request);

    assertThat(response.getTaskCount()).isEqualTo(2);
    assertThat(response.getTask(0).getTaskName().toStringUtf8()).isEqualTo("a-task-0");
    assertThat(response.getTask(0).getBody().toStringUtf8()).isEqualTo("payload0");
    assertThat(response.getTask(0).getTag().toStringUtf8()).isEqualTo("tag");
    assertThat(response.getTask(1).getTaskName().toStringUtf8()).isEqualTo("a-task-2");
    assertThat(response.getTask(1).getBody().toStringUtf8()).isEqualTo("payload2");
    assertThat(response.getTask(1).getTag().toStringUtf8()).isEqualTo("tag");
  }

  @Test
  public void testQueryAndOwnTasksWithUnspecifiedTag() throws Exception {
    class MockClock implements Clock {
      @Override
      public long getCurrentTime() {
        // Force nowMillis of QueryAndOwnTasks is guaranteed to be bigger than task eta.
        return Clock.DEFAULT.getCurrentTime() + 1000;
      }
    }
    initLocalTaskQueue(new MockClock());

    TaskQueueBulkAddResponse sbresponse =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(sbresponse).isEqualTo(expectedBulkAddResponse.build());

    TaskQueueQueryAndOwnTasksRequest request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("pull-queue"))
            .setLeaseSeconds(10)
            .setMaxTasks(5)
            .setGroupByTag(true)
            .build();

    TaskQueueQueryAndOwnTasksResponse response =
        localService.queryAndOwnTasks(new Status(), request);

    assertThat(response.getTaskCount()).isEqualTo(2);
    assertThat(response.getTask(0).getTaskName().toStringUtf8()).isEqualTo("a-task-0");
    assertThat(response.getTask(0).getBody().toStringUtf8()).isEqualTo("payload0");
    assertThat(response.getTask(0).getTag().toStringUtf8()).isEqualTo("tag");
    assertThat(response.getTask(1).getTaskName().toStringUtf8()).isEqualTo("a-task-2");
    assertThat(response.getTask(1).getBody().toStringUtf8()).isEqualTo("payload2");
    assertThat(response.getTask(1).getTag().toStringUtf8()).isEqualTo("tag");
  }

  @Test
  public void testQueryAndOwnTasksOnPushQueue() throws Exception {
    var unused = localService.bulkAdd(new Status(), bulkAddRequest.build());

    TaskQueueQueryAndOwnTasksRequest request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(entry.getName()))
            .setLeaseSeconds(10)
            .setMaxTasks(5)
            .build();

    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.queryAndOwnTasks(new Status(), request));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
  }

  @Test
  public void testExtendTaskLease() throws Exception {
    // QueryAndOwn tasks first, same as testQueryAndOwnTasks above
    class MockClock implements Clock {
      @Override
      public long getCurrentTime() {
        // So that nowMillis of QueryAndOwnTasks is guaranteed to be bigger than task eta.
        return Clock.DEFAULT.getCurrentTime() + 1000;
      }
    }
    initLocalTaskQueue(new MockClock());

    TaskQueueBulkAddResponse sbresponse =
        localService.bulkAdd(new Status(), bulkAddPullRequest.build());
    assertThat(sbresponse).isEqualTo(expectedBulkAddResponse.build());

    TaskQueueQueryAndOwnTasksRequest request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("pull-queue"))
            .setLeaseSeconds(60)
            .setMaxTasks(5)
            .build();

    TaskQueueQueryAndOwnTasksResponse response =
        localService.queryAndOwnTasks(new Status(), request);

    assertThat(response.getTaskCount()).isEqualTo(3);

    for (int i = 0; i < response.getTaskCount(); ++i) {
      TaskQueueModifyTaskLeaseRequest extendRequest =
          TaskQueueModifyTaskLeaseRequest.newBuilder()
              .setQueueName(ByteString.copyFromUtf8("pull-queue"))
              .setTaskName(response.getTask(i).getTaskName())
              .setEtaUsec(response.getTask(i).getEtaUsec())
              .setLeaseSeconds(300)
              .build();
      TaskQueueModifyTaskLeaseResponse extendResponse =
          localService.modifyTaskLease(new Status(), extendRequest);
      assertThat(extendResponse.getUpdatedEtaUsec())
          .isGreaterThan(response.getTask(i).getEtaUsec());
    }
  }

  @Test
  public void testExtendTaskLeaseOnPushQueue() throws Exception {
    TaskQueueModifyTaskLeaseRequest extendRequest =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(entry.getName()))
            .setTaskName(ByteString.copyFromUtf8("foo"))
            .setEtaUsec(0)
            .setLeaseSeconds(30)
            .build();
    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.modifyTaskLease(new Status(), extendRequest));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.INVALID_QUEUE_MODE_VALUE);
  }

  @Test
  public void testExtendTaskLeaseInvalidQueueName() throws Exception {
    TaskQueueModifyTaskLeaseRequest extendRequest =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("****invalid****"))
            .setTaskName(ByteString.copyFromUtf8("foo"))
            .setEtaUsec(0)
            .setLeaseSeconds(30)
            .build();
    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.modifyTaskLease(new Status(), extendRequest));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.INVALID_QUEUE_NAME_VALUE);
  }

  @Test
  public void testExtendTaskLeaseInvalidTaskName() throws Exception {
    TaskQueueModifyTaskLeaseRequest extendRequest =
        TaskQueueModifyTaskLeaseRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8("pull-queue"))
            .setTaskName(ByteString.copyFromUtf8("****invalid*****"))
            .setEtaUsec(0)
            .setLeaseSeconds(30)
            .build();
    ApiProxy.ApplicationException e =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () -> localService.modifyTaskLease(new Status(), extendRequest));
    assertThat(e.getApplicationError()).isEqualTo(ErrorCode.INVALID_TASK_NAME_VALUE);
  }
}
