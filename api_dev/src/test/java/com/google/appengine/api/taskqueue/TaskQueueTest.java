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

import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withCountLimit;
import static com.google.appengine.api.taskqueue.LeaseOptions.Builder.withLeasePeriod;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMaxBackoffSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMaxDoublings;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withMinBackoffSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskAgeLimitSeconds;
import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withDefaults;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withEtaMillis;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withHeader;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withMethod;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withParam;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withRetryOptions;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withTaskName;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest.Header;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueDeleteRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueDeleteResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueModifyTaskLeaseResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueuePurgeQueueRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueuePurgeQueueResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueQueryAndOwnTasksRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueQueryAndOwnTasksResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.truth.Correspondence;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@code com.google.appengine.api.taskqueue} API.
 *
 */
@RunWith(JUnit4.class)
public class TaskQueueTest {

  private static final String APP = "app";
  private static final ByteString DEFAULT_NAMESPACE_HEADER =
      ByteString.copyFromUtf8(QueueImpl.DEFAULT_NAMESPACE_HEADER);
  private static final ByteString CURRENT_NAMESPACE_HEADER =
      ByteString.copyFromUtf8(QueueImpl.CURRENT_NAMESPACE_HEADER);
  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  private Environment environment;
  private String mockRequestNamespace = "";
  private String mockCurrentNamespace = null;

  // A mock environment for handling namespaces.
  final class MockEnvironment implements ApiProxy.Environment {

    @Override
    public String getAppId() {
      return "app";
    }

    @Override
    public Map<String, Object> getAttributes() {
      HashMap<String, Object> map = new HashMap<>();
      if (mockCurrentNamespace == null) {
        map.remove(CURRENT_NAMESPACE_KEY);
      } else {
        map.put(CURRENT_NAMESPACE_KEY, mockCurrentNamespace);
      }
      if (mockRequestNamespace == null) {
        map.remove(APPS_NAMESPACE_KEY);
      } else {
        map.put(APPS_NAMESPACE_KEY, mockRequestNamespace);
      }
      return map;
    }

    @Override
    public String getAuthDomain() {
      return "auth.domain.com";
    }

    @Override
    public String getEmail() {
      return "user@auth.domain.com";
    }

    @Override
    @Deprecated
    public String getRequestNamespace() {
      throw new IllegalArgumentException("Calling deprecated getRequestNamespace().");
    }

    @Override
    public String getModuleId() {
      return "default";
    }

    @Override
    public String getVersionId() {
      return "v1";
    }

    @Override
    public boolean isAdmin() {
      return false;
    }

    @Override
    public boolean isLoggedIn() {
      return true;
    }

    @Override
    public long getRemainingMillis() {
      return Long.MAX_VALUE;
    }
  }

  @Before
  public void setUp() throws Exception {
    environment = ApiProxy.getCurrentEnvironment();
    MockEnvironment mockEnvironment = new MockEnvironment();
    ApiProxy.setEnvironmentForCurrentThread(mockEnvironment);
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setEnvironmentForCurrentThread(environment);
  }

  static class TrapQueueApiHelper extends QueueApiHelper {

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      throw new AssertionError("Not expecting to get here.");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      throw new AssertionError("Not expecting to get here.");
    }

    Queue getQueue() {
      return new QueueImpl(Queue.DEFAULT_QUEUE, this);
    }
  }

  Queue trapQueue = new TrapQueueApiHelper().getQueue();

  static class SilentFailureQueueApiHelper extends QueueApiHelper {

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {}

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      return immediateFuture(responseProto);
    }

    Queue getQueue() {
      return new QueueImpl(Queue.DEFAULT_QUEUE, this);
    }
  }

  Queue silentFailureQueue = new SilentFailureQueueApiHelper().getQueue();

  static class ApplicationErrorQueueApiHelper extends QueueApiHelper {

    private final int exceptionNo;
    private boolean madeSyncCall = false;
    private boolean madeAsyncCall = false;
    private Double deadlineInSeconds = null;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      madeSyncCall = true;
      throw QueueApiHelper.translateError(new ApiProxy.ApplicationException(exceptionNo));
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      madeAsyncCall = true;
      deadlineInSeconds = apiConfig.getDeadlineInSeconds();
      return immediateFailedFuture(
          QueueApiHelper.translateError(new ApiProxy.ApplicationException(exceptionNo)));
    }

    Queue getQueue() {
      return new QueueImpl(Queue.DEFAULT_QUEUE, this);
    }

    ApplicationErrorQueueApiHelper(int exceptionNo) {
      this.exceptionNo = exceptionNo;
    }

    boolean getMadeSyncCall() {
      return madeSyncCall;
    }

    boolean getMadeAsyncCall() {
      return madeAsyncCall;
    }

    Double getDeadlineInSeconds() {
      return deadlineInSeconds;
    }
  }

  static class BulkAddTaskResultErrorQueueApiHelper extends QueueApiHelper {

    private final int exceptionNo;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      throw new AssertionError("Not expecting to get here.");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      TaskQueueBulkAddResponse.Builder bulkAddResponse =
          ((TaskQueueBulkAddResponse) responseProto).toBuilder();
      // We order these results to verify that the first error other than TASK_ALREADY_EXISTS is
      // being reported, and TASK_ALREADY_EXISTS is being reported if there are no other errors.
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.OK);
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.SKIPPED);
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.TASK_ALREADY_EXISTS);
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.OK);
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.forNumber(exceptionNo));
      bulkAddResponse.addTaskResultBuilder().setResult(ErrorCode.TASK_ALREADY_EXISTS);
      @SuppressWarnings("unchecked")
      T response = (T) bulkAddResponse.build();
      return immediateFuture(response);
    }

    Queue getQueue() {
      return new QueueImpl(Queue.DEFAULT_QUEUE, this);
    }

    BulkAddTaskResultErrorQueueApiHelper(int exceptionNo) {
      this.exceptionNo = exceptionNo;
    }
  }

  static class MockQueueBulkAddApiHelper extends QueueApiHelper {

    static final String MOCK_QUEUE_NAME = "MockQueue";
    TaskQueueBulkAddRequest.Builder expectedRequest;
    TaskQueueBulkAddResponse.Builder designatedResponse;

    // Helper ordering to put expectedRequest's headers in the order the actual request will use.
    private static final Ordering<Header> HEADER_ORDERING =
        Ordering.natural()
            .onResultOf(
                header -> {
                  if (DEFAULT_NAMESPACE_HEADER.equals(header.getKey())
                      || CURRENT_NAMESPACE_HEADER.equals(header.getKey())) {
                    return 1; // Sort namespace headers after user-specified headers.
                  } else if ("content-type".equals(header.getKey().toStringUtf8())
                      && "application/x-www-form-urlencoded"
                          .equals(header.getValue().toStringUtf8())) {
                    return 2; // Sort default content-type header last.
                  } else {
                    return 0; // Let everything else remain in the original order (given a stable
                    // sort).
                  }
                });

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      throw new AssertionError("Not expecting to get here.");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      assertThat(method).isEqualTo("BulkAdd");

      assertThat(request).isInstanceOf(TaskQueueBulkAddRequest.class);
      assertThat(responseProto).isInstanceOf(TaskQueueBulkAddResponse.class);

      TaskQueueBulkAddRequest addRequest = (TaskQueueBulkAddRequest) request;

      if (expectedRequest != null) {
        for (int i = 0; i < expectedRequest.getAddRequestCount(); ++i) {
          TaskQueueAddRequest.Builder expectedReq = expectedRequest.getAddRequestBuilder(i);
          TaskQueueAddRequest.Builder addedReq = addRequest.getAddRequest(i).toBuilder();
          assertThat(addedReq.getEtaUsec()).isAtLeast(expectedReq.getEtaUsec());
          addedReq.setEtaUsec(expectedReq.getEtaUsec());
          // Stably sort the expected headers so that those added by the queue are at the end (which
          // is where the implementation puts them) and other headers are in the original order.
          // This allows checking that header order is preserved for user-specified headers.
          List<Header> orderedHeaders = HEADER_ORDERING.sortedCopy(expectedReq.getHeaderList());
          expectedReq.clearHeader().addAllHeader(orderedHeaders);

          assertThat(addedReq.toString()).isEqualTo(expectedReq.toString());
        }
      }
      TaskQueueBulkAddResponse addResponse =
          ((TaskQueueBulkAddResponse) responseProto)
              .toBuilder().mergeFrom(designatedResponse.build()).build();
      @SuppressWarnings("unchecked")
      T response = (T) addResponse;
      return immediateFuture(response);
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    Queue getQueue(final DatastoreService ds) {
      return new QueueImpl(MOCK_QUEUE_NAME, this) {
        @Override
        DatastoreService getDatastoreService() {
          return ds;
        }
      };
    }

    MockQueueBulkAddApiHelper(
        TaskQueueBulkAddRequest.Builder bulkAddRequest,
        TaskQueueBulkAddResponse.Builder bulkAddResponse) {
      this.expectedRequest = bulkAddRequest;
      this.designatedResponse = bulkAddResponse;
    }
  }

  static class MockQueuePurgeQueueApiHelper extends QueueApiHelper {

    static final String MOCK_QUEUE_NAME = "MockQueuePurge";
    TaskQueuePurgeQueueRequest.Builder expectedRequest;
    TaskQueuePurgeQueueResponse.Builder designatedResponse;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {
      assertThat(method).isEqualTo("PurgeQueue");
      assertThat(request).isInstanceOf(TaskQueuePurgeQueueRequest.class);
      assertThat(response).isInstanceOf(TaskQueuePurgeQueueResponse.Builder.class);

      TaskQueuePurgeQueueRequest purgeRequest = (TaskQueuePurgeQueueRequest) request;

      assertThat(purgeRequest.getQueueName()).isEqualTo(expectedRequest.getQueueName());
      assertThat(purgeRequest.hasAppId()).isEqualTo(expectedRequest.hasAppId());
      if (expectedRequest.hasAppId()) {
        assertThat(purgeRequest.getAppId()).isEqualTo(expectedRequest.getAppId());
      }
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      throw new AssertionError("Not expecting to get here.");
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    MockQueuePurgeQueueApiHelper(
        TaskQueuePurgeQueueRequest.Builder purgeQueueRequest,
        TaskQueuePurgeQueueResponse.Builder purgeQueueResponse) {
      this.expectedRequest = purgeQueueRequest;
      this.designatedResponse = purgeQueueResponse;
    }
  }

  static class MockQueueLeaseTasksApiHelper extends QueueApiHelper {

    static final String MOCK_QUEUE_NAME = "MockQueueLease";
    TaskQueueQueryAndOwnTasksRequest.Builder expectedRequest;
    TaskQueueQueryAndOwnTasksResponse.Builder designatedResponse;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      throw new AssertionError("Not expecting to get here.");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {

      assertThat(method).isEqualTo("QueryAndOwnTasks");
      assertThat(request).isInstanceOf(TaskQueueQueryAndOwnTasksRequest.class);
      assertThat(responseProto).isInstanceOf(TaskQueueQueryAndOwnTasksResponse.class);

      TaskQueueQueryAndOwnTasksRequest leaseRequest = (TaskQueueQueryAndOwnTasksRequest) request;

      assertThat(leaseRequest.getQueueName()).isEqualTo(expectedRequest.getQueueName());
      assertThat(leaseRequest.getLeaseSeconds()).isEqualTo(expectedRequest.getLeaseSeconds());
      assertThat(leaseRequest.getMaxTasks()).isEqualTo(expectedRequest.getMaxTasks());
      assertThat(leaseRequest.getGroupByTag()).isEqualTo(expectedRequest.getGroupByTag());
      assertThat(leaseRequest.getTag().toByteArray())
          .isEqualTo(expectedRequest.getTag().toByteArray());

      TaskQueueQueryAndOwnTasksResponse queryAndOwnTasksResponse =
          ((TaskQueueQueryAndOwnTasksResponse) responseProto)
              .toBuilder().mergeFrom(designatedResponse.build()).build();
      @SuppressWarnings("unchecked")
      T response = (T) queryAndOwnTasksResponse;
      return immediateFuture(response);
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    MockQueueLeaseTasksApiHelper(
        TaskQueueQueryAndOwnTasksRequest.Builder queryAndOwnTasksRequest,
        TaskQueueQueryAndOwnTasksResponse.Builder queryAndOwnTasksResponse) {
      this.expectedRequest = queryAndOwnTasksRequest;
      this.designatedResponse = queryAndOwnTasksResponse;
    }
  }

  static class MockQueueModifyTaskLeaseApiHelper extends QueueApiHelper {

    static final String MOCK_QUEUE_NAME = "MockQueueModifyLease";
    static final String MOCK_TASK_NAME = "MockTaskModifyLease";
    static final double MOCK_LEASE_SECONDS = 30.0;
    static final long MOCK_ETA_USEC = 11111001L;
    static final long MOCK_UPDATED_ETA_USEC = 3000011111L;

    TaskQueueModifyTaskLeaseRequest.Builder expectedRequest;
    TaskQueueModifyTaskLeaseResponse.Builder designatedResponse;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      assertThat(method).isEqualTo("ModifyTaskLease");
      assertThat(request).isInstanceOf(TaskQueueModifyTaskLeaseRequest.class);
      assertThat(response).isInstanceOf(TaskQueueModifyTaskLeaseResponse.Builder.class);

      TaskQueueModifyTaskLeaseRequest extendRequest = (TaskQueueModifyTaskLeaseRequest) request;

      assertThat(extendRequest.getQueueName()).isEqualTo(this.expectedRequest.getQueueName());
      assertThat(extendRequest.getTaskName()).isEqualTo(this.expectedRequest.getTaskName());
      assertThat(extendRequest.getEtaUsec()).isEqualTo(this.expectedRequest.getEtaUsec());
      assertThat(extendRequest.getLeaseSeconds()).isEqualTo(this.expectedRequest.getLeaseSeconds());
      TaskQueueModifyTaskLeaseResponse.Builder leaseResponse =
          (TaskQueueModifyTaskLeaseResponse.Builder) response;

      leaseResponse.mergeFrom(this.designatedResponse.build());
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      throw new AssertionError("Not expecting to get here.");
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    MockQueueModifyTaskLeaseApiHelper(
        TaskQueueModifyTaskLeaseRequest.Builder request,
        TaskQueueModifyTaskLeaseResponse.Builder response) {
      this.expectedRequest = request;
      this.designatedResponse = response;
    }
  }

  static class MockQueueDeleteTasksApiHelper extends QueueApiHelper {

    static final String MOCK_QUEUE_NAME = "MockQueueDelete";
    TaskQueueDeleteRequest.Builder expectedRequest;
    TaskQueueDeleteResponse.Builder designatedResponse;

    @Override
    void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {

      throw new AssertionError("Not expecting to get here.");
    }

    private static <T> Correspondence<T, T> correspondenceFromComparator(Comparator<T> comparator) {
      return Correspondence.from(
          (a, e) -> comparator.compare(a, e) == 0, "corresponds using the Comparator to");
    }

    @Override
    <T extends MessageLite> Future<T> makeAsyncCall(
        String method, MessageLite request, final T responseProto, ApiConfig apiConfig) {
      assertThat(method).isEqualTo("Delete");
      assertThat(request).isInstanceOf(TaskQueueDeleteRequest.class);
      assertThat(responseProto).isInstanceOf(TaskQueueDeleteResponse.class);

      TaskQueueDeleteRequest deleteRequest = (TaskQueueDeleteRequest) request;

      assertThat(deleteRequest.getQueueName()).isEqualTo(expectedRequest.getQueueName());
      assertThat(deleteRequest.hasAppId()).isEqualTo(expectedRequest.hasAppId());
      if (expectedRequest.hasAppId()) {
        assertThat(deleteRequest.getAppId()).isEqualTo(expectedRequest.getAppId());
      }
      assertThat(deleteRequest.getTaskNameCount()).isEqualTo(expectedRequest.getTaskNameCount());
      List<String> expectedNames =
          expectedRequest.getTaskNameList().stream()
              .map(ByteString::toStringUtf8)
              .collect(toList());

      List<String> actualNames =
          deleteRequest.getTaskNameList().stream().map(ByteString::toStringUtf8).collect(toList());
      assertWithMessage("Tasks are not the same.")
          .that(actualNames)
          .comparingElementsUsing(correspondenceFromComparator(Collator.getInstance()))
          .containsExactlyElementsIn(expectedNames);
      TaskQueueDeleteResponse deleteResponse =
          ((TaskQueueDeleteResponse) responseProto)
              .toBuilder().mergeFrom(designatedResponse.build()).build();
      @SuppressWarnings("unchecked")
      T response = (T) deleteResponse;
      return immediateFuture(response);
    }

    Queue getQueue() {
      return new QueueImpl(MOCK_QUEUE_NAME, this);
    }

    MockQueueDeleteTasksApiHelper(
        TaskQueueDeleteRequest.Builder deleteRequest,
        TaskQueueDeleteResponse.Builder deleteResponse) {
      this.expectedRequest = deleteRequest;
      this.designatedResponse = deleteResponse;
    }
  }

  void addCurrentNamespace(TaskQueueAddRequest.Builder addRequest) {
    Header.Builder currentNamespaceHeader =
        addRequest.addHeaderBuilder().setKey(CURRENT_NAMESPACE_HEADER);
    String namespace = NamespaceManager.get();
    namespace = nullToEmpty(namespace);
    currentNamespaceHeader.setValue(ByteString.copyFromUtf8(namespace));
  }

  MockQueueBulkAddApiHelper newEmptyAddRequest() {
    TaskQueueBulkAddRequest.Builder request = TaskQueueBulkAddRequest.newBuilder();
    TaskQueueBulkAddResponse.Builder response = TaskQueueBulkAddResponse.newBuilder();
    return new MockQueueBulkAddApiHelper(request, response);
  }

  MockQueueBulkAddApiHelper newDefaultAddRequest() {
    TaskQueueBulkAddRequest.Builder request = TaskQueueBulkAddRequest.newBuilder();
    TaskQueueAddRequest.Builder addRequest = request.addAddRequestBuilder();
    addRequest.setQueueName(ByteString.copyFromUtf8(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME));
    addRequest.setTaskName(ByteString.copyFromUtf8(""));
    addRequest.setEtaUsec(System.currentTimeMillis() * 1000);
    addRequest.setMethod(TaskOptions.Method.POST.getPbMethod());
    addRequest.setMode(Mode.PUSH);
    addRequest.setUrl(
        ByteString.copyFromUtf8(
            Queue.DEFAULT_QUEUE_PATH + "/" + MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME));
    TaskQueueBulkAddResponse.Builder response = TaskQueueBulkAddResponse.newBuilder();
    response
        .addTaskResultBuilder()
        .setResult(ErrorCode.OK)
        .setChosenTaskName(ByteString.copyFromUtf8("AUTOMATICALLY-PICKED-NAME"));
    return new MockQueueBulkAddApiHelper(request, response);
  }

  // Returns mock for "x-www-form-urlencoded" content-type payloads.
  MockQueueBulkAddApiHelper newBasicAddRequestNoNamespaceHeaders() {
    MockQueueBulkAddApiHelper queueHelper = newDefaultAddRequest();
    TaskQueueAddRequest.Builder request =
        queueHelper.expectedRequest.getAddRequestBuilder(0).setBody(ByteString.copyFromUtf8(""));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("content-type"))
        .setValue(ByteString.copyFromUtf8("application/x-www-form-urlencoded"));
    return queueHelper;
  }

  MockQueueBulkAddApiHelper newBasicAddRequest() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequestNoNamespaceHeaders();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    addCurrentNamespace(request);
    return queueHelper;
  }

  // Identical to newBasicAddRequest except the request contains a task with a name "task1" and
  // the response is empty.
  MockQueueBulkAddApiHelper newBasicNamedAddRequest() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();
    queueHelper
        .expectedRequest
        .getAddRequestBuilder(0)
        .setTaskName(ByteString.copyFromUtf8("task1"));
    queueHelper.designatedResponse.getTaskResultBuilder(0).clear();
    queueHelper.designatedResponse.getTaskResultBuilder(0).setResult(ErrorCode.OK);
    return queueHelper;
  }

  // Returns mock for HTTP GET requests.
  MockQueueBulkAddApiHelper newGetAddRequest() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();
    queueHelper
        .expectedRequest
        .getAddRequestBuilder(0)
        .setMethod(TaskOptions.Method.GET.getPbMethod())
        .removeHeader(0)
        .clearBody();
    return queueHelper;
  }

  MockQueueBulkAddApiHelper newTransactionalAddRequest(long handle) {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request.getTransactionBuilder().setApp(APP).setHandle(handle);
    return queueHelper;
  }

  MockQueuePurgeQueueApiHelper newDefaultPurgeRequest() {
    TaskQueuePurgeQueueRequest.Builder request = TaskQueuePurgeQueueRequest.newBuilder();
    TaskQueuePurgeQueueResponse.Builder response = TaskQueuePurgeQueueResponse.newBuilder();
    request.setQueueName(ByteString.copyFromUtf8(MockQueuePurgeQueueApiHelper.MOCK_QUEUE_NAME));
    return new MockQueuePurgeQueueApiHelper(request, response);
  }

  MockQueueLeaseTasksApiHelper newDefaultLeaseTasksRequest() {
    TaskQueueQueryAndOwnTasksRequest.Builder request =
        TaskQueueQueryAndOwnTasksRequest.newBuilder();
    TaskQueueQueryAndOwnTasksResponse.Builder response =
        TaskQueueQueryAndOwnTasksResponse.newBuilder();
    request.setQueueName(ByteString.copyFromUtf8(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME));
    return new MockQueueLeaseTasksApiHelper(request, response);
  }

  MockQueueModifyTaskLeaseApiHelper newDefaultModifyTaskLeaseRequest() {
    TaskQueueModifyTaskLeaseRequest.Builder request = TaskQueueModifyTaskLeaseRequest.newBuilder();
    TaskQueueModifyTaskLeaseResponse.Builder response =
        TaskQueueModifyTaskLeaseResponse.newBuilder();
    request
        .setQueueName(ByteString.copyFromUtf8(MockQueueModifyTaskLeaseApiHelper.MOCK_QUEUE_NAME))
        .setTaskName(ByteString.copyFromUtf8(MockQueueModifyTaskLeaseApiHelper.MOCK_TASK_NAME))
        .setEtaUsec(MockQueueModifyTaskLeaseApiHelper.MOCK_ETA_USEC)
        .setLeaseSeconds(MockQueueModifyTaskLeaseApiHelper.MOCK_LEASE_SECONDS);
    response.setUpdatedEtaUsec(MockQueueModifyTaskLeaseApiHelper.MOCK_UPDATED_ETA_USEC);
    return new MockQueueModifyTaskLeaseApiHelper(request, response);
  }

  MockQueueDeleteTasksApiHelper newDefaultDeleteRequest() {
    TaskQueueDeleteRequest.Builder request = TaskQueueDeleteRequest.newBuilder();
    TaskQueueDeleteResponse.Builder response = TaskQueueDeleteResponse.newBuilder();
    request.setQueueName(ByteString.copyFromUtf8(MockQueueDeleteTasksApiHelper.MOCK_QUEUE_NAME));
    return new MockQueueDeleteTasksApiHelper(request, response);
  }

  MockQueueBulkAddApiHelper newNoCheckRequest(int size) {
    TaskQueueBulkAddResponse.Builder response = TaskQueueBulkAddResponse.newBuilder();
    while (size-- > 0) {
      response
          .addTaskResultBuilder()
          .setChosenTaskName(ByteString.copyFromUtf8("task" + size))
          .setResult(ErrorCode.OK);
    }
    return new MockQueueBulkAddApiHelper(null, response);
  }

  @Test
  public void testPurge() throws Exception {
    MockQueuePurgeQueueApiHelper helper = newDefaultPurgeRequest();
    helper.getQueue().purge();
  }

  @Test
  public void testLeaseTasks() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    final double leaseSeconds = 12.34;
    final long maxTasks = 100;
    TaskQueueQueryAndOwnTasksRequest.Builder request = helper.expectedRequest;
    request.setLeaseSeconds(leaseSeconds);
    request.setMaxTasks(maxTasks);
    TaskQueueQueryAndOwnTasksResponse.Builder response = helper.designatedResponse;
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task1 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8("body"))
            .setTaskName(ByteString.copyFromUtf8("task1"))
            .setEtaUsec(1234567890L)
            .setRetryCount(2)
            .setTag(ByteString.copyFromUtf8("tag"));
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task2 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8(""))
            .setTaskName(ByteString.copyFromUtf8("task2"))
            .setEtaUsec(2345678901L)
            .setRetryCount(3);

    List<TaskHandle> result = helper.getQueue().leaseTasks(12340, MILLISECONDS, 100);

    assertThat(result).hasSize(2);

    assertThat(result.get(0).getQueueName())
        .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
    assertThat(task1.getTaskName().toStringUtf8()).isEqualTo(result.get(0).getName());
    assertThat(task1.getRetryCount()).isSameInstanceAs(result.get(0).getRetryCount());
    assertThat(task1.getBody().toByteArray()).isEqualTo(result.get(0).getPayload());
    assertThat(task1.getEtaUsec() / 1000).isEqualTo(result.get(0).getEtaMillis());
    assertThat(task1.getEtaUsec()).isEqualTo(result.get(0).getEtaUsec());
    assertThat(task1.getTag().toByteArray()).isEqualTo(result.get(0).getTagAsBytes());

    assertThat(result.get(1).getQueueName())
        .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
    assertThat(task2.getTaskName().toStringUtf8()).isEqualTo(result.get(1).getName());
    assertThat(task2.getRetryCount()).isSameInstanceAs(result.get(1).getRetryCount());
    assertThat(task2.getBody().toByteArray()).isEqualTo(result.get(1).getPayload());
    assertThat(task2.getEtaUsec() / 1000).isEqualTo(result.get(1).getEtaMillis());
    assertThat(task2.getEtaUsec()).isEqualTo(result.get(1).getEtaUsec());
  }

  @Test
  public void testLeaseTasksNoResult() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    final double leaseSeconds = 12.34;
    final long maxTasks = 100;
    helper.expectedRequest.setLeaseSeconds(leaseSeconds).setMaxTasks(maxTasks);

    List<TaskHandle> result = helper.getQueue().leaseTasks(12340, MILLISECONDS, 100);

    assertThat(result).isEmpty();
  }

  @Test
  public void testLeaseTasksNoResultAsync() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    final double leaseSeconds = 56.78;
    final long maxTasks = 10;
    helper.expectedRequest.setLeaseSeconds(leaseSeconds).setMaxTasks(maxTasks);

    List<TaskHandle> result = helper.getQueue().leaseTasksAsync(56780, MILLISECONDS, 10).get();

    assertThat(result).isEmpty();
  }

  @Test
  public void testLeaseTasksLeaseTimeOutOfRange() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            helper
                .getQueue()
                .leaseTasks(QueueConstants.maxLease(MILLISECONDS) + 1, MILLISECONDS, 10));

    assertThrows(
        IllegalArgumentException.class, () -> helper.getQueue().leaseTasks(-1, MILLISECONDS, 10));

    assertThrows(
        IllegalArgumentException.class,
        () -> helper.getQueue().leaseTasksAsync(-1, MILLISECONDS, 10));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            helper
                .getQueue()
                .leaseTasks(
                    withCountLimit(10)
                        .leasePeriod(QueueConstants.maxLease(MILLISECONDS) + 1, MILLISECONDS)));

    // lease period unspecified
    assertThrows(
        IllegalArgumentException.class, () -> helper.getQueue().leaseTasks(withCountLimit(10)));
  }

  @Test
  public void testLeaseTasksMaxTasksOutOfRange() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.getQueue().leaseTasks(100000, MILLISECONDS, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            helper.getQueue().leaseTasks(100000, MILLISECONDS, QueueConstants.maxLeaseCount() + 1));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            helper
                .getQueue()
                .leaseTasks(
                    withLeasePeriod(100000, MILLISECONDS)
                        .countLimit(QueueConstants.maxLeaseCount() + 1)));

    // count limit unspecified
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.getQueue().leaseTasks(withLeasePeriod(100000, MILLISECONDS)));

    // count limit unspecified
    assertThrows(
        IllegalArgumentException.class,
        () -> helper.getQueue().leaseTasksAsync(withLeasePeriod(100000, MILLISECONDS)));
  }

  @Test
  public void testLeaseTasksByTagSpecified() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    final double leaseSeconds = 12.34;
    final long maxTasks = 100;
    ByteString tag = ByteString.copyFromUtf8("tag");
    TaskQueueQueryAndOwnTasksRequest.Builder request = helper.expectedRequest;
    request.setLeaseSeconds(leaseSeconds);
    request.setMaxTasks(maxTasks);
    request.setTag(tag);
    request.setGroupByTag(true);
    TaskQueueQueryAndOwnTasksResponse.Builder response = helper.designatedResponse;
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task1 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8("body"))
            .setTaskName(ByteString.copyFromUtf8("task1"))
            .setEtaUsec(1234567890L)
            .setRetryCount(2)
            .setTag(tag);
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task2 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8(""))
            .setTaskName(ByteString.copyFromUtf8("task2"))
            .setEtaUsec(2345678901L)
            .setRetryCount(3)
            .setTag(tag);

    // The lease calls in the switch should be equivalent.
    for (int i = 0; i < 2; i++) {
      List<TaskHandle> result = null;

      switch (i) {
        case 0:
          result = helper.getQueue().leaseTasksByTag(12340, MILLISECONDS, 100, tag.toStringUtf8());
          break;
        case 1:
          result =
              helper.getQueue().leaseTasksByTagBytes(12340, MILLISECONDS, 100, tag.toByteArray());
          break;
        default: // fall out
      }
      assertThat(result).hasSize(2);

      assertThat(result.get(0).getQueueName())
          .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
      assertThat(task1.getTaskName().toStringUtf8()).isEqualTo(result.get(0).getName());
      assertThat(task1.getRetryCount()).isSameInstanceAs(result.get(0).getRetryCount());
      assertThat(task1.getBody().toByteArray()).isEqualTo(result.get(0).getPayload());
      assertThat(task1.getEtaUsec() / 1000).isEqualTo(result.get(0).getEtaMillis());
      assertThat(task1.getEtaUsec()).isEqualTo(result.get(0).getEtaUsec());
      assertThat(task1.getTag().toByteArray()).isEqualTo(result.get(0).getTagAsBytes());

      assertThat(result.get(1).getQueueName())
          .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
      assertThat(task2.getTaskName().toStringUtf8()).isEqualTo(result.get(1).getName());
      assertThat(task2.getRetryCount()).isSameInstanceAs(result.get(1).getRetryCount());
      assertThat(task2.getBody().toByteArray()).isEqualTo(result.get(1).getPayload());
      assertThat(task2.getEtaUsec() / 1000).isEqualTo(result.get(1).getEtaMillis());
      assertThat(task2.getEtaUsec()).isEqualTo(result.get(1).getEtaUsec());
      assertThat(task2.getTag().toByteArray()).isEqualTo(result.get(1).getTagAsBytes());
    }
  }

  @Test
  public void testLeaseTasksByTagUnspecified() throws Exception {
    MockQueueLeaseTasksApiHelper helper = newDefaultLeaseTasksRequest();
    final double leaseSeconds = 12.34;
    final long maxTasks = 100;
    ByteString tag = ByteString.copyFromUtf8("tag");
    TaskQueueQueryAndOwnTasksRequest.Builder request = helper.expectedRequest;
    request.setLeaseSeconds(leaseSeconds);
    request.setMaxTasks(maxTasks);
    // No tag specified, but we are grouping by tag.
    request.setGroupByTag(true);
    TaskQueueQueryAndOwnTasksResponse.Builder response = helper.designatedResponse;
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task1 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8("body"))
            .setTaskName(ByteString.copyFromUtf8("task1"))
            .setEtaUsec(1234567890L)
            .setRetryCount(2)
            .setTag(tag);
    TaskQueueQueryAndOwnTasksResponse.Task.Builder task2 =
        response
            .addTaskBuilder()
            .setBody(ByteString.copyFromUtf8(""))
            .setTaskName(ByteString.copyFromUtf8("task2"))
            .setEtaUsec(2345678901L)
            .setRetryCount(3)
            .setTag(tag);

    // The lease calls in the switch should be equivalent.
    for (int i = 0; i < 2; i++) {
      List<TaskHandle> result = null;

      switch (i) {
        case 0:
          result = helper.getQueue().leaseTasksByTag(12340, MILLISECONDS, 100, null);
          break;
        case 1:
          result = helper.getQueue().leaseTasksByTagBytes(12340, MILLISECONDS, 100, null);
          break;
        default: // fall out
      }
      assertThat(result).hasSize(2);

      assertThat(result.get(0).getQueueName())
          .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
      assertThat(task1.getTaskName().toStringUtf8()).isEqualTo(result.get(0).getName());
      assertThat(task1.getRetryCount()).isSameInstanceAs(result.get(0).getRetryCount());
      assertThat(task1.getBody().toByteArray()).isEqualTo(result.get(0).getPayload());
      assertThat(task1.getEtaUsec() / 1000).isEqualTo(result.get(0).getEtaMillis());
      assertThat(task1.getEtaUsec()).isEqualTo(result.get(0).getEtaUsec());
      assertThat(task1.getTag().toByteArray()).isEqualTo(result.get(0).getTagAsBytes());

      assertThat(result.get(1).getQueueName())
          .isEqualTo(MockQueueLeaseTasksApiHelper.MOCK_QUEUE_NAME);
      assertThat(task2.getTaskName().toStringUtf8()).isEqualTo(result.get(1).getName());
      assertThat(task2.getRetryCount()).isSameInstanceAs(result.get(1).getRetryCount());
      assertThat(task2.getBody().toByteArray()).isEqualTo(result.get(1).getPayload());
      assertThat(task2.getEtaUsec() / 1000).isEqualTo(result.get(1).getEtaMillis());
      assertThat(task2.getEtaUsec()).isEqualTo(result.get(1).getEtaUsec());
      assertThat(task2.getTag().toByteArray()).isEqualTo(result.get(1).getTagAsBytes());
    }
  }

  @Test
  public void testLeaseTasksCustomDeadline() throws Exception {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.UNKNOWN_QUEUE_VALUE);
    Queue unknownQueue = helper.getQueue();
    LeaseOptions options = LeaseOptions.Builder.withDeadlineInSeconds(1.7);
    options.leasePeriod(2, HOURS);
    options.countLimit(1);
    assertThrows(IllegalStateException.class, () -> unknownQueue.leaseTasks(options));
    assertThat(helper.getMadeAsyncCall()).isTrue();
    assertThat(helper.getDeadlineInSeconds()).isEqualTo(1.7);
  }

  @Test
  public void testModifyTaskLease() throws Exception {
    MockQueueModifyTaskLeaseApiHelper helper = newDefaultModifyTaskLeaseRequest();
    TaskOptions options =
        TaskOptions.Builder.withTaskName(MockQueueModifyTaskLeaseApiHelper.MOCK_TASK_NAME);
    TaskHandle tmpTask = new TaskHandle(options, MockQueueModifyTaskLeaseApiHelper.MOCK_QUEUE_NAME);
    tmpTask.etaUsec(MockQueueModifyTaskLeaseApiHelper.MOCK_ETA_USEC);

    helper
        .getQueue()
        .modifyTaskLease(
            tmpTask, (long) MockQueueModifyTaskLeaseApiHelper.MOCK_LEASE_SECONDS, SECONDS);

    assertThat(tmpTask.getEtaMillis())
        .isEqualTo((long) (MockQueueModifyTaskLeaseApiHelper.MOCK_UPDATED_ETA_USEC / 1e3));
    assertThat(tmpTask.getEtaUsec())
        .isEqualTo(MockQueueModifyTaskLeaseApiHelper.MOCK_UPDATED_ETA_USEC);
  }

  @Test
  public void testModifyTaskLeaseTimeOutOfRange() throws Exception {
    MockQueueModifyTaskLeaseApiHelper helper = newDefaultModifyTaskLeaseRequest();
    TaskOptions options = TaskOptions.Builder.withTaskName("task");
    TaskHandle tmpTask = new TaskHandle(options, "queue");

    IllegalArgumentException e1 =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                helper
                    .getQueue()
                    .modifyTaskLease(
                        tmpTask, QueueConstants.maxLease(MILLISECONDS) + 1, MILLISECONDS));
    String expected1 =
        "The lease time specified (604800.001 seconds) is too large. "
            + "Lease period can be no longer than 604800 seconds.";
    assertThat(e1.getMessage()).isEqualTo(expected1);

    IllegalArgumentException e2 =
        assertThrows(
            IllegalArgumentException.class,
            () -> helper.getQueue().modifyTaskLease(tmpTask, -1, MILLISECONDS));
    String expected2 =
        "The lease time must not be negative. Specified lease time was -0.001 seconds.";
    assertThat(e2.getMessage()).isEqualTo(expected2);

    IllegalArgumentException e3 =
        assertThrows(
            IllegalArgumentException.class,
            () -> helper.getQueue().modifyTaskLease(tmpTask, -10000, MILLISECONDS));
    String expected3 =
        "The lease time must not be negative. Specified lease time was -10.000 seconds.";
    assertThat(e3.getMessage()).isEqualTo(expected3);
  }

  @Test
  public void testModifyTaskLeaseLeaseExpired() throws Exception {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.TASK_LEASE_EXPIRED_VALUE);
    TaskOptions options = TaskOptions.Builder.withTaskName("task");
    TaskHandle tmpTask = new TaskHandle(options, "queue");

    assertThrows(
        IllegalStateException.class, () -> helper.getQueue().modifyTaskLease(tmpTask, 30, SECONDS));
  }

  @Test
  public void testModifyTaskLeaseQueuePaused() throws Exception {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.QUEUE_PAUSED_VALUE);
    TaskOptions options = TaskOptions.Builder.withTaskName("task");
    TaskHandle tmpTask = new TaskHandle(options, "queue");

    assertThrows(
        IllegalStateException.class, () -> helper.getQueue().modifyTaskLease(tmpTask, 30, SECONDS));
  }

  // TODO Add test for TaskHandle.delete()

  @Test
  public void testDeleteTask() throws Exception {
    MockQueueDeleteTasksApiHelper helper = newDefaultDeleteRequest();

    helper.expectedRequest.addTaskName(ByteString.copyFromUtf8("Task1"));

    helper.designatedResponse.addResult(ErrorCode.OK);

    helper.getQueue().deleteTask("Task1");
  }

  @Test
  public void testDeleteTaskList() throws Exception {
    MockQueueDeleteTasksApiHelper helper = newDefaultDeleteRequest();

    helper
        .expectedRequest
        .addTaskName(ByteString.copyFromUtf8("Task1"))
        .addTaskName(ByteString.copyFromUtf8("Task2"))
        .addTaskName(ByteString.copyFromUtf8("Task3"));

    helper
        .designatedResponse
        .addResult(ErrorCode.OK)
        .addResult(ErrorCode.TOMBSTONED_TASK)
        .addResult(ErrorCode.OK);

    String qName = MockQueueDeleteTasksApiHelper.MOCK_QUEUE_NAME;

    List<TaskHandle> tasks = new ArrayList<>(3);
    tasks.add(new TaskHandle(TaskOptions.Builder.withTaskName("Task1"), qName));
    tasks.add(new TaskHandle(TaskOptions.Builder.withTaskName("Task2"), qName));
    tasks.add(new TaskHandle(TaskOptions.Builder.withTaskName("Task3"), qName));

    List<Boolean> result = helper.getQueue().deleteTask(tasks);

    assertThat(result.get(0)).isTrue();
    assertThat(result.get(1)).isFalse();
    assertThat(result.get(2)).isTrue();
  }

  @Test
  public void testDeleteTaskListAsync() throws Exception {
    MockQueueDeleteTasksApiHelper helper = newDefaultDeleteRequest();

    helper
        .expectedRequest
        .addTaskName(ByteString.copyFromUtf8("Task1"))
        .addTaskName(ByteString.copyFromUtf8("Task2"));

    helper
        .designatedResponse
        .addResult(ErrorCode.OK)
        .addResult(ErrorCode.TOMBSTONED_TASK)
        .addResult(ErrorCode.OK);

    String qName = MockQueueDeleteTasksApiHelper.MOCK_QUEUE_NAME;

    List<TaskHandle> tasks = new ArrayList<>(3);
    tasks.add(new TaskHandle(TaskOptions.Builder.withTaskName("Task1"), qName));
    tasks.add(new TaskHandle(TaskOptions.Builder.withTaskName("Task2"), qName));

    List<Boolean> result = helper.getQueue().deleteTaskAsync(tasks).get();

    assertThat(result.get(0)).isTrue();
    assertThat(result.get(1)).isFalse();
    assertThat(result.get(2)).isTrue();
  }

  @Test
  public void testDeleteQueueNameMismatch() throws Exception {
    MockQueueDeleteTasksApiHelper helper = newDefaultDeleteRequest();

    helper.expectedRequest.addTaskName(ByteString.copyFromUtf8("Task1"));

    helper.designatedResponse.addResult(ErrorCode.OK);

    List<TaskHandle> tasks = new ArrayList<>(3);
    TaskOptions options = TaskOptions.Builder.withTaskName("task");
    tasks.add(new TaskHandle(options, "InvalidQueueName"));

    assertThrows(QueueNameMismatchException.class, () -> helper.getQueue().deleteTask(tasks));
  }

  @Test
  public void testDeleteUnpopulatedResponse() throws Exception {
    assertThrows(InternalFailureException.class, () -> silentFailureQueue.deleteTask("foo"));
  }

  @Test
  public void testQueueFactory() throws Exception {
    assertThat(QueueFactory.getDefaultQueue().getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
    assertThat(QueueFactory.getQueue(Queue.DEFAULT_QUEUE).getQueueName())
        .isEqualTo(QueueFactory.getDefaultQueue().getQueueName());

    assertThat(QueueFactory.getQueue("AQueueName").getQueueName()).isEqualTo("AQueueName");
    assertThat(QueueFactory.getQueue("AQueueName").getQueueName())
        .isEqualTo(QueueFactory.getQueue("AQueueName").getQueueName());
  }

  @Test
  public void testInvalidQueueName() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> QueueFactory.getQueue("Not a valid queue name"));
    // queue name must not be empty
    assertThrows(IllegalArgumentException.class, () -> QueueFactory.getQueue(""));
    // queue name must not be null
    assertThrows(IllegalArgumentException.class, () -> QueueFactory.getQueue(null));
  }

  @Test
  public void testBulkAddInvalidDispatchDeadline() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withUrl("A/B").dispatchDeadline(Duration.ofSeconds(10))));
  }

  @Test
  public void testBulkAddInvalidUrl() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> trapQueue.add(withUrl("Not a valid URL")));
  }

  @Test
  public void testBulkAddInvalidTaskName() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> withTaskName("Not a valid task name"));

    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(mock(Transaction.class), withTaskName("a")));
  }

  @Test
  public void testBulkAddInvalidOptions() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withEtaMillis(100L).countdownMillis(100L)));

    assertThrows(
        IllegalArgumentException.class, () -> trapQueue.add(withParam("X", "Y").url("/url?A=B")));

    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withMethod(TaskOptions.Method.POST).param("X", "Y").payload("AAA")));
  }

  @Test
  public void testBulkAddInvalidRetryOptions() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withTaskRetryLimit(-1))));
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withTaskAgeLimitSeconds(-1L))));
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withMinBackoffSeconds(-1))));
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withMaxBackoffSeconds(-1))));
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withMinBackoffSeconds(2).maxBackoffSeconds(1))));
    assertThrows(
        IllegalArgumentException.class,
        () -> trapQueue.add(withRetryOptions(withMaxDoublings(-1))));
  }

  @Test
  public void testBulkAddDuplicateTaskNames() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            trapQueue.add(
                Arrays.asList(
                    withTaskName("Task1"), withTaskName("Task2"), withTaskName("Task2"))));
  }

  @Test
  public void testBulkAddTooManyTasks() throws Exception {
    List<TaskOptions> taskOptionsList = new ArrayList<>();
    for (int i = 0; i < QueueConstants.maxTasksPerAdd() + 1; ++i) {
      taskOptionsList.add(withDefaults());
    }
    assertThrows(IllegalArgumentException.class, () -> trapQueue.add(taskOptionsList));
  }

  @Test
  public void testBulkAddPullMultipleTasks() throws Exception {
    MockQueueBulkAddApiHelper helper = newEmptyAddRequest();
    long etaMillis = 123456L;
    long etaUsec = etaMillis * 1000;

    TaskQueueBulkAddRequest.Builder request = helper.expectedRequest;
    TaskQueueAddRequest.Builder addRequest1 = request.addAddRequestBuilder();
    addCurrentNamespace(addRequest1);
    addRequest1.setQueueName(ByteString.copyFromUtf8(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME));
    addRequest1.setTaskName(ByteString.copyFromUtf8("T1"));
    addRequest1.setEtaUsec(etaUsec);
    addRequest1.setBody(ByteString.copyFrom(new byte[] {1, 2, 3}));
    addRequest1.setMode(Mode.PULL);
    TaskQueueAddRequest.Builder addRequest2 = request.addAddRequestBuilder();
    addCurrentNamespace(addRequest2);
    addRequest2.setQueueName(ByteString.copyFromUtf8(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME));
    addRequest2.setTaskName(ByteString.copyFromUtf8("T2"));
    addRequest2.setEtaUsec(etaUsec);
    addRequest2.setBody(ByteString.copyFrom(new byte[] {5, 6, 7}));
    addRequest2.setMode(Mode.PULL);
    TaskQueueAddRequest.Builder addRequest3 = request.addAddRequestBuilder();
    addCurrentNamespace(addRequest3);
    addRequest3
        .setQueueName(ByteString.copyFromUtf8(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME))
        .setTaskName(ByteString.copyFromUtf8("T3"))
        .setEtaUsec(etaUsec)
        .setBody(ByteString.copyFrom(new byte[] {9, 0, 1}))
        .setMode(Mode.PULL);

    List<TaskOptions> taskOptionsList = new ArrayList<>();
    TaskOptions task1 =
        TaskOptions.Builder.withTaskName("T1")
            .payload(new byte[] {1, 2, 3})
            .etaMillis(etaMillis)
            .method(TaskOptions.Method.PULL);
    TaskOptions task2 =
        TaskOptions.Builder.withTaskName("T2")
            .payload(new byte[] {5, 6, 7})
            .etaMillis(etaMillis)
            .method(TaskOptions.Method.PULL);
    TaskOptions task3 =
        TaskOptions.Builder.withTaskName("T3")
            .payload(new byte[] {9, 0, 1})
            .etaMillis(etaMillis)
            .method(TaskOptions.Method.PULL);
    taskOptionsList.add(task1);
    taskOptionsList.add(task2);
    taskOptionsList.add(task3);

    TaskQueueBulkAddResponse.Builder response = helper.designatedResponse;
    response.addTaskResultBuilder().setResult(ErrorCode.OK);
    response.addTaskResultBuilder().setResult(ErrorCode.OK);
    response.addTaskResultBuilder().setResult(ErrorCode.OK);

    List<TaskHandle> addResult = helper.getQueue().add(taskOptionsList);
    assertThat(addResult).hasSize(3);
  }

  @Test
  public void testBulkAddPullWithUrl() throws Exception {
    TaskOptions task1 =
        TaskOptions.Builder.withTaskName("T1")
            .etaMillis(123456L)
            .method(TaskOptions.Method.PULL)
            .payload("123".getBytes(UTF_8))
            .url("/broke");

    assertThrows(IllegalArgumentException.class, () -> trapQueue.add(task1));
  }

  // TODO Check that the return values of the calls to Queue.add() are correct.
  @Test
  public void testBulkAddDefaultTask() throws Exception {
    newBasicAddRequest().getQueue().add();
  }

  @Test
  public void testBulkAddDefaultTaskVarArguments() throws Exception {
    MockQueueBulkAddApiHelper helper = newBasicAddRequest();

    TaskQueueBulkAddRequest.Builder request = helper.expectedRequest;
    request.addAddRequest(request.getAddRequest(0));
    request.addAddRequest(request.getAddRequest(0));

    TaskQueueBulkAddResponse.Builder response = helper.designatedResponse;
    // response already has the first response populated.
    response
        .addTaskResultBuilder()
        .setResult(ErrorCode.OK)
        .setChosenTaskName(ByteString.copyFromUtf8("AUTOMATICALLY-PICKED-NAME-2"));
    response
        .addTaskResultBuilder()
        .setResult(ErrorCode.OK)
        .setChosenTaskName(ByteString.copyFromUtf8("AUTOMATICALLY-PICKED-NAME-3"));

    List<TaskHandle> tasks =
        helper.getQueue().add(Arrays.asList(withDefaults(), withDefaults(), withDefaults()));

    assertThat(tasks.get(0).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(tasks.get(0).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
    assertThat(tasks.get(1).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME-2");
    assertThat(tasks.get(1).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
    assertThat(tasks.get(2).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME-3");
    assertThat(tasks.get(2).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddGetTask() throws Exception {
    newGetAddRequest().getQueue().add(withMethod(TaskOptions.Method.GET));
  }

  @Test
  public void testBulkAddUnpopulatedResponse() throws Exception {
    assertThrows(InternalFailureException.class, () -> silentFailureQueue.add());
  }

  static byte[] makeLongString(int length) {
    StringBuilder builder = new StringBuilder();
    while (length-- > 0) {
      builder.append("A");
    }
    return builder.toString().getBytes(UTF_8);
  }

  @Test
  public void testBulkPullTaskTooLarge() throws Exception {
    newNoCheckRequest(1)
        .getQueue()
        .add(
            withMethod(TaskOptions.Method.PULL)
                .payload(makeLongString(QueueConstants.maxPullTaskSizeBytes() / 2)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            newNoCheckRequest(1)
                .getQueue()
                .add(
                    withMethod(TaskOptions.Method.PULL)
                        .payload(makeLongString(QueueConstants.maxPullTaskSizeBytes() + 1))));
  }

  @Test
  public void testBulkPushTaskTooLarge() throws Exception {
    newNoCheckRequest(1)
        .getQueue()
        .add(
            withMethod(TaskOptions.Method.POST)
                .payload(makeLongString(QueueConstants.maxPushTaskSizeBytes() / 2)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            newNoCheckRequest(1)
                .getQueue()
                .add(
                    withMethod(TaskOptions.Method.POST)
                        .payload(makeLongString(QueueConstants.maxPushTaskSizeBytes() + 1))));
  }

  @Test
  public void testBulkTxTaskTooLarge() throws Exception {
    TaskOptions[] smallerTasks = {
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 4)),
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 4)),
    };
    long handle = 1L;
    Transaction txn1 = mock(Transaction.class);
    when(txn1.getApp()).thenReturn(APP);
    when(txn1.getId()).thenReturn(Long.toString(handle));
    newNoCheckRequest(2).getQueue().add(txn1, Arrays.asList(smallerTasks));
    verify(txn1, atLeastOnce()).getApp();
    verify(txn1, atLeastOnce()).getId();

    TaskOptions[] biggerTasks = {
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 2 + 1)),
      withMethod(TaskOptions.Method.PULL)
          .payload(makeLongString(QueueConstants.maxTransactionalRequestSizeBytes() / 2 + 1)),
    };

    Transaction txn2 = mock(Transaction.class);
    when(txn2.getApp()).thenReturn(APP);
    when(txn2.getId()).thenReturn(Long.toString(handle));
    assertThrows(
        IllegalArgumentException.class,
        () -> newNoCheckRequest(2).getQueue().add(txn2, Arrays.asList(biggerTasks)));
  }

  @Test
  public void testBulkAddEmptyName() throws Exception {
    TaskHandle handle = newBasicAddRequest().getQueue().add(withTaskName(""));
    assertThat(handle.getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(handle.getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddNullName() throws Exception {
    TaskHandle handle = newBasicAddRequest().getQueue().add(withTaskName(null));
    assertThat(handle.getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(handle.getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddSetName() throws Exception {
    TaskHandle handle = newBasicNamedAddRequest().getQueue().add(withTaskName("task1"));
    assertThat(handle.getName()).isEqualTo("task1");
    assertThat(handle.getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddContentTypeForPostWithPayloadAndParams() throws Exception {
    MockQueueBulkAddApiHelper queueHelper = newDefaultAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    Header.Builder header = request.addHeaderBuilder();
    request.setBody(ByteString.copyFromUtf8("Hi!"));
    header
        .setKey(ByteString.copyFromUtf8("content-type"))
        .setValue(ByteString.copyFromUtf8("text/plain"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            queueHelper
                .getQueue()
                .add(
                    withMethod(TaskOptions.Method.POST)
                        .header("Content-Type", "text/plain")
                        .param("Foo", "Bar")
                        .payload("Hi!")));
    // Message body and parameters can't both be present for POST methods
  }

  @Test
  public void testBulkAddContentTypeForPostWithParamsIgnored() throws Exception {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();

    queueHelper.expectedRequest.getAddRequestBuilder(0).setBody(ByteString.copyFromUtf8("Foo=Bar"));

    // The user-provided content-type should be ignored.
    queueHelper
        .getQueue()
        .add(
            withMethod(TaskOptions.Method.POST)
                .header("Content-Type", "text/plain")
                .param("Foo", "Bar"));
  }

  @Test
  public void testBulkAddTransactionalTaskNullTaskName() throws Exception {
    long handle = 1L;
    Transaction txn = mock(Transaction.class);
    when(txn.getApp()).thenReturn(APP);
    when(txn.getId()).thenReturn(Long.toString(handle));
    TaskHandle task = newTransactionalAddRequest(handle).getQueue().add(txn, withDefaults());

    assertThat(task.getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(task.getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddTransactionalTaskEmptyTaskName() throws Exception {
    long handle = 1L;
    Transaction txn = mock(Transaction.class);
    when(txn.getApp()).thenReturn(APP);
    when(txn.getId()).thenReturn(Long.toString(handle));
    TaskHandle task = newTransactionalAddRequest(handle).getQueue().add(txn, withTaskName(""));

    assertThat(task.getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(task.getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddTransactionalVarArguments() throws Exception {
    long handle = 11L;
    MockQueueBulkAddApiHelper helper = newTransactionalAddRequest(handle);

    TaskQueueBulkAddRequest.Builder request = helper.expectedRequest;
    request.addAddRequest(request.getAddRequest(0));
    request.addAddRequest(request.getAddRequest(0));

    TaskQueueBulkAddResponse.Builder response = helper.designatedResponse;
    // response already has the first response populated.
    response
        .addTaskResultBuilder()
        .setResult(ErrorCode.OK)
        .setChosenTaskName(ByteString.copyFromUtf8("AUTOMATICALLY-PICKED-NAME-2"));
    response
        .addTaskResultBuilder()
        .setResult(ErrorCode.OK)
        .setChosenTaskName(ByteString.copyFromUtf8("AUTOMATICALLY-PICKED-NAME-3"));

    Transaction txn = mock(Transaction.class);
    when(txn.getApp()).thenReturn(APP);
    when(txn.getId()).thenReturn(Long.toString(handle));
    List<TaskHandle> tasks =
        helper
            .getQueue()
            .add(txn, Arrays.asList(withTaskName(""), withTaskName(""), withTaskName("")));

    assertThat(tasks.get(0).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME");
    assertThat(tasks.get(0).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
    assertThat(tasks.get(1).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME-2");
    assertThat(tasks.get(1).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
    assertThat(tasks.get(2).getName()).isEqualTo("AUTOMATICALLY-PICKED-NAME-3");
    assertThat(tasks.get(2).getQueueName()).isEqualTo(MockQueueBulkAddApiHelper.MOCK_QUEUE_NAME);
  }

  @Test
  public void testBulkAddGetTaskParam() throws Exception {
    MockQueueBulkAddApiHelper queueHelper = newGetAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request.setUrl(ByteString.copyFromUtf8(request.getUrl().toStringUtf8() + "?aaa=bbb"));
    queueHelper.getQueue().add(withMethod(TaskOptions.Method.GET).param("aaa", "bbb"));
  }

  @Test
  public void testBulkAddBinaryPayload() throws Exception {
    MockQueueBulkAddApiHelper queueHelper = newDefaultAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    byte[] bytes = new byte[] {0, 1, 2, 3, -127, -126, 0, 1, 2};
    request.setBody(ByteString.copyFrom(bytes));
    addCurrentNamespace(request);
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("content-type"))
        .setValue(ByteString.copyFromUtf8("application/x-binary-stuff"));
    queueHelper.getQueue().add(withPayload(bytes, "application/x-binary-stuff"));
  }

  private void doBulkAddApplicationErrorTest(ErrorCode code, Class<? extends Exception> class1) {
    ApplicationErrorQueueApiHelper helper = new ApplicationErrorQueueApiHelper(code.getNumber());
    Exception exception = assertThrows(Exception.class, () -> helper.getQueue().add());
    assertThat(exception.getClass()).isEqualTo(class1);
    assertThat(helper.getMadeAsyncCall()).isTrue();
  }

  @Test
  public void testBulkAddExceptions() throws Exception {
    doBulkAddApplicationErrorTest(ErrorCode.UNKNOWN_QUEUE, IllegalStateException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TRANSIENT_ERROR, TransientFailureException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INTERNAL_ERROR, InternalFailureException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TASK_TOO_LARGE, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_TASK_NAME, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_QUEUE_NAME, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_URL, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_QUEUE_RATE, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.PERMISSION_DENIED, SecurityException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TASK_ALREADY_EXISTS, TaskAlreadyExistsException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TOMBSTONED_TASK, TaskAlreadyExistsException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_ETA, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.INVALID_REQUEST, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.UNKNOWN_TASK, TaskNotFoundException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TOMBSTONED_QUEUE, IllegalStateException.class);
    doBulkAddApplicationErrorTest(ErrorCode.DUPLICATE_TASK_NAME, IllegalArgumentException.class);
    doBulkAddApplicationErrorTest(ErrorCode.TOO_MANY_TASKS, IllegalArgumentException.class);

    doBulkAddApplicationErrorTest(ErrorCode.DATASTORE_ERROR, TransactionalTaskException.class);
  }

  private void doPurgeQueueApplicationErrorTest(ErrorCode code, Class<? extends Exception> class1) {
    ApplicationErrorQueueApiHelper helper = new ApplicationErrorQueueApiHelper(code.getNumber());
    Exception exception = assertThrows(Exception.class, () -> helper.getQueue().purge());
    assertThat(exception.getClass()).isEqualTo(class1);
    assertThat(helper.getMadeSyncCall()).isTrue();
  }

  @Test
  public void testPurgeQueueExceptions() throws Exception {
    doPurgeQueueApplicationErrorTest(ErrorCode.UNKNOWN_QUEUE, IllegalStateException.class);
  }

  private void doDeleteTasksApplicationErrorTest(
      ErrorCode code, Class<? extends Exception> class1) {
    ApplicationErrorQueueApiHelper helper = new ApplicationErrorQueueApiHelper(code.getNumber());
    Exception exception = assertThrows(Exception.class, () -> helper.getQueue().deleteTask("task"));
    assertThat(helper.getMadeAsyncCall()).isTrue();
    assertThat(exception.getClass()).isEqualTo(class1);
  }

  @Test
  public void testDeleteTasksExceptions() throws Exception {
    doDeleteTasksApplicationErrorTest(ErrorCode.UNKNOWN_QUEUE, IllegalStateException.class);
    doDeleteTasksApplicationErrorTest(ErrorCode.TOMBSTONED_QUEUE, IllegalStateException.class);
    doDeleteTasksApplicationErrorTest(ErrorCode.UNKNOWN_TASK, TaskNotFoundException.class);
    doDeleteTasksApplicationErrorTest(ErrorCode.TOMBSTONED_TASK, TaskAlreadyExistsException.class);
    doDeleteTasksApplicationErrorTest(ErrorCode.TRANSIENT_ERROR, TransientFailureException.class);
  }

  private void doLeaseTasksApplicationErrorTest(ErrorCode code, Class<? extends Exception> class1) {
    ApplicationErrorQueueApiHelper helper = new ApplicationErrorQueueApiHelper(code.getNumber());
    Exception exception =
        assertThrows(Exception.class, () -> helper.getQueue().leaseTasks(60000, MILLISECONDS, 100));
    assertThat(exception.getClass()).isEqualTo(class1);
    assertThat(helper.getMadeAsyncCall()).isTrue();
    assertThat(helper.getDeadlineInSeconds())
        .isEqualTo(QueueImpl.DEFAULT_LEASE_TASKS_DEADLINE_SECONDS);
  }

  @Test
  public void testLeaseTasksExceptions() throws Exception {
    doLeaseTasksApplicationErrorTest(ErrorCode.UNKNOWN_QUEUE, IllegalStateException.class);
    doLeaseTasksApplicationErrorTest(ErrorCode.TOMBSTONED_QUEUE, IllegalStateException.class);
    doLeaseTasksApplicationErrorTest(ErrorCode.TRANSIENT_ERROR, TransientFailureException.class);
    doLeaseTasksApplicationErrorTest(ErrorCode.INTERNAL_ERROR, InternalFailureException.class);
  }

  private void doBulkAddTaskResultErrorTest(ErrorCode code, Class<? extends Exception> class1) {
    Exception exception =
        assertThrows(
            Exception.class,
            () ->
                new BulkAddTaskResultErrorQueueApiHelper(code.getNumber())
                    .getQueue()
                    .add(
                        Arrays.asList(
                            withTaskName("a"),
                            withTaskName("b"),
                            withTaskName("c"),
                            withTaskName("d"),
                            withTaskName("e"),
                            withTaskName("f"))));
    assertThat(exception.getClass()).isEqualTo(class1);
    if (exception instanceof TaskAlreadyExistsException) {
      TaskAlreadyExistsException taeException = (TaskAlreadyExistsException) exception;
      // In BulkAddTaskResultErrorQueueApiHelper we have the mock proto interface
      // return TASK_ALREADY_EXISTS in positions 3, 5 and 6.
      assertThat(taeException.getTaskNames()).containsExactly("c", "e", "f").inOrder();
    }
  }

  @Test
  public void testBulkAddTaskResultErrors() throws Exception {
    doBulkAddTaskResultErrorTest(ErrorCode.UNKNOWN_QUEUE, IllegalStateException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TRANSIENT_ERROR, TransientFailureException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INTERNAL_ERROR, InternalFailureException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TASK_TOO_LARGE, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_TASK_NAME, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_QUEUE_NAME, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_URL, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_QUEUE_RATE, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.PERMISSION_DENIED, SecurityException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TASK_ALREADY_EXISTS, TaskAlreadyExistsException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TOMBSTONED_TASK, TaskAlreadyExistsException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_ETA, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.INVALID_REQUEST, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.UNKNOWN_TASK, TaskNotFoundException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TOMBSTONED_QUEUE, IllegalStateException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.DUPLICATE_TASK_NAME, IllegalArgumentException.class);
    doBulkAddTaskResultErrorTest(ErrorCode.TOO_MANY_TASKS, IllegalArgumentException.class);

    doBulkAddTaskResultErrorTest(ErrorCode.DATASTORE_ERROR, TransactionalTaskException.class);
  }

  private void doBulkAddDatastoreApplicationErrorTest(
      DatastoreV3Pb.Error.ErrorCode code, Class<?> class1) {
    int errorCode = ErrorCode.DATASTORE_ERROR_VALUE + code.getNumber();
    TransactionalTaskException exception =
        assertThrows(
            TransactionalTaskException.class,
            () -> new ApplicationErrorQueueApiHelper(errorCode).getQueue().add());
    assertThat(exception.getCause().getClass()).isEqualTo(class1);
  }

  @Test
  public void testBulkAddDatastoreApplicationErrors() {
    doBulkAddDatastoreApplicationErrorTest(
        DatastoreV3Pb.Error.ErrorCode.BAD_REQUEST, IllegalArgumentException.class);
    doBulkAddDatastoreApplicationErrorTest(
        DatastoreV3Pb.Error.ErrorCode.CONCURRENT_TRANSACTION,
        ConcurrentModificationException.class);
    doBulkAddDatastoreApplicationErrorTest(
        DatastoreV3Pb.Error.ErrorCode.INTERNAL_ERROR, DatastoreFailureException.class);
    doBulkAddDatastoreApplicationErrorTest(
        DatastoreV3Pb.Error.ErrorCode.TIMEOUT, DatastoreTimeoutException.class);
    doBulkAddDatastoreApplicationErrorTest(
        DatastoreV3Pb.Error.ErrorCode.PERMISSION_DENIED, DatastoreFailureException.class);
  }

  private void doBulkAddDatastoreTaskResultErrorTest(
      DatastoreV3Pb.Error.ErrorCode code, Class<?> class1) {
    int errorCode = ErrorCode.DATASTORE_ERROR_VALUE + code.getNumber();
    TransactionalTaskException exception =
        assertThrows(
            TransactionalTaskException.class,
            () ->
                new BulkAddTaskResultErrorQueueApiHelper(errorCode)
                    .getQueue()
                    .add(
                        Arrays.asList(
                            withTaskName("a"),
                            withTaskName("b"),
                            withTaskName("c"),
                            withTaskName("d"),
                            withTaskName("e"),
                            withTaskName("f"))));
    assertThat(exception.getCause().getClass()).isEqualTo(class1);
  }

  @Test
  public void testBulkAddDatastoreTaskResultErrors() {
    doBulkAddDatastoreTaskResultErrorTest(
        DatastoreV3Pb.Error.ErrorCode.BAD_REQUEST, IllegalArgumentException.class);
    doBulkAddDatastoreTaskResultErrorTest(
        DatastoreV3Pb.Error.ErrorCode.CONCURRENT_TRANSACTION,
        ConcurrentModificationException.class);
    doBulkAddDatastoreTaskResultErrorTest(
        DatastoreV3Pb.Error.ErrorCode.INTERNAL_ERROR, DatastoreFailureException.class);
    doBulkAddDatastoreTaskResultErrorTest(
        DatastoreV3Pb.Error.ErrorCode.TIMEOUT, DatastoreTimeoutException.class);
    doBulkAddDatastoreTaskResultErrorTest(
        DatastoreV3Pb.Error.ErrorCode.PERMISSION_DENIED, DatastoreFailureException.class);
  }

  @Test
  public void testBulkAddImplicitTransactionEnlistment_NoTaskOptions() {
    MockQueueBulkAddApiHelper helper = newBasicAddRequest();
    com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Transaction pbTxn =
        com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Transaction.newBuilder()
            .setHandle(44)
            .setApp(APP)
            .build();
    helper.expectedRequest.getAddRequestBuilder(0).setTransaction(pbTxn);
    Transaction txn = mock(Transaction.class);
    when(txn.getId()).thenReturn("44");
    when(txn.getApp()).thenReturn(APP);
    DatastoreService ds = mock(DatastoreService.class);
    when(ds.getCurrentTransaction(null)).thenReturn(txn);
    Queue q = helper.getQueue(ds);
    q.add();
  }

  @Test
  public void testBulkAddImplicitTransactionEnlistment_TaskOptions() {
    MockQueueBulkAddApiHelper helper = newBasicAddRequest();
    com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Transaction pbTxn =
        com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Transaction.newBuilder()
            .setHandle(44)
            .setApp(APP)
            .build();
    helper.expectedRequest.getAddRequestBuilder(0).setTransaction(pbTxn);
    Transaction txn = mock(Transaction.class);
    when(txn.getId()).thenReturn("44");
    when(txn.getApp()).thenReturn(APP);
    DatastoreService ds = mock(DatastoreService.class);
    when(ds.getCurrentTransaction(null)).thenReturn(txn);
    Queue q = helper.getQueue(ds);
    q.add(TaskOptions.Builder.withDefaults());
  }

  @Test
  public void testBulkAddImplicitTransactionEnlistment_TaskOptions_ExplicitNullTxn() {
    MockQueueBulkAddApiHelper helper = newBasicAddRequest();
    Transaction txn = mock(Transaction.class);
    when(txn.getId()).thenReturn("44");
    when(txn.getApp()).thenReturn(APP);
    DatastoreService ds = mock(DatastoreService.class);
    when(ds.getCurrentTransaction(null)).thenReturn(txn);
    Queue q = helper.getQueue(ds);
    q.add(null, TaskOptions.Builder.withDefaults());
  }

  @Test
  public void testBulkAddRequestNamespaceHeaders() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequestNoNamespaceHeaders();
    mockRequestNamespace = "request-namespace";
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request
        .addHeaderBuilder()
        .setKey(DEFAULT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("request-namespace"));
    request
        .addHeaderBuilder()
        .setKey(CURRENT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8(""));
    queueHelper.getQueue().add();
  }

  @Test
  public void testBulkAddCurrentNamespaceHeaders() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequestNoNamespaceHeaders();
    mockCurrentNamespace = "current-namespace";
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request
        .addHeaderBuilder()
        .setKey(CURRENT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("current-namespace"));
    queueHelper.getQueue().add();
  }

  @Test
  public void testBulkAddCurrentAndRequestNamespaceHeaders() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequestNoNamespaceHeaders();
    mockCurrentNamespace = "current-namespace";
    mockRequestNamespace = "request-namespace";
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request
        .addHeaderBuilder()
        .setKey(DEFAULT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("request-namespace"));
    request
        .addHeaderBuilder()
        .setKey(CURRENT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("current-namespace"));
    queueHelper.getQueue().add();
  }

  @Test
  public void testBulkAddOverrideCurrentAndRequestNamespaceHeaders() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequestNoNamespaceHeaders();
    mockCurrentNamespace = "current-namespace";
    mockRequestNamespace = "request-namespace";
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request
        .addHeaderBuilder()
        .setKey(DEFAULT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("override-request"));
    request
        .addHeaderBuilder()
        .setKey(CURRENT_NAMESPACE_HEADER)
        .setValue(ByteString.copyFromUtf8("override-current"));
    queueHelper
        .getQueue()
        .add(
            withHeader(DEFAULT_NAMESPACE_HEADER.toStringUtf8(), "override-request")
                .header(CURRENT_NAMESPACE_HEADER.toStringUtf8(), "override-current"));
  }

  @Test
  public void testBulkAddRequestWithRetryParameters() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);

    request
        .getRetryParametersBuilder()
        .setRetryLimit(10)
        .setAgeLimitSec(86400)
        .setMinBackoffSec(0.1)
        .setMaxBackoffSec(1000)
        .setMaxDoublings(10);

    queueHelper
        .getQueue()
        .add(
            withRetryOptions(
                withTaskRetryLimit(10)
                    .taskAgeLimitSeconds(86400)
                    .minBackoffSeconds(0.1)
                    .maxBackoffSeconds(1000)
                    .maxDoublings(10)));
  }

  @Test
  public void testBulkAddHeaderOrder() {
    MockQueueBulkAddApiHelper queueHelper = newBasicAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("foo"))
        .setValue(ByteString.copyFromUtf8("1"));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("baz"))
        .setValue(ByteString.copyFromUtf8("2"));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("bar"))
        .setValue(ByteString.copyFromUtf8("3"));
    queueHelper.getQueue().add(withHeader("foo", "1").header("baz", "2").header("bar", "3"));
  }

  @Test
  public void testBulkAddHeaderOrderWithCustomContentType() {
    MockQueueBulkAddApiHelper queueHelper = newDefaultAddRequest();
    TaskQueueAddRequest.Builder request = queueHelper.expectedRequest.getAddRequestBuilder(0);
    addCurrentNamespace(request);
    byte[] bytes = new byte[] {2, 3, 5, 7, 11};
    request.setBody(ByteString.copyFrom(bytes));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("foo"))
        .setValue(ByteString.copyFromUtf8("1"));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("baz"))
        .setValue(ByteString.copyFromUtf8("2"));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("content-type"))
        .setValue(ByteString.copyFromUtf8("message/example"));
    request
        .addHeaderBuilder()
        .setKey(ByteString.copyFromUtf8("bar"))
        .setValue(ByteString.copyFromUtf8("3"));
    queueHelper
        .getQueue()
        .add(
            withDefaults()
                .payload(bytes)
                .header("foo", "1")
                .header("baz", "2")
                .header("content-type", "message/example")
                .header("bar", "3"));
  }

  @Test
  public void testAsyncDeleteNeverWaits() {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.UNKNOWN_QUEUE_VALUE);
    Queue unknownQueue = helper.getQueue();

    TaskHandle handle = new TaskHandle(TaskOptions.Builder.withTaskName("taskName"), "default");
    List<TaskHandle> handleList = Lists.newArrayList(handle);

    // We never wait for any of the these futures.
    Future<Boolean> unusedFutureBool = unknownQueue.deleteTaskAsync("taskName");
    unusedFutureBool = unknownQueue.deleteTaskAsync(handle);
    Future<List<Boolean>> unusedFutureBoolList = unknownQueue.deleteTaskAsync(handleList);
  }

  @Test
  public void testAsyncLeaseNeverWaits() {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.UNKNOWN_QUEUE_VALUE);
    Queue unknownQueue = helper.getQueue();

    // We never wait for any of the these futures.
    Future<List<TaskHandle>> unusedFutureHandleList =
        unknownQueue.leaseTasksAsync(
            LeaseOptions.Builder.withTag("tag").countLimit(10).leasePeriod(4, DAYS));
    unusedFutureHandleList = unknownQueue.leaseTasksAsync(4, DAYS, 10);
    unusedFutureHandleList = unknownQueue.leaseTasksByTagAsync(4, DAYS, 10, "tag");
    unusedFutureHandleList =
        unknownQueue.leaseTasksByTagBytesAsync(4, DAYS, 10, "tag".getBytes(UTF_8));
  }

  @Test
  public void testAsyncAddNeverWaits() {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.UNKNOWN_QUEUE_VALUE);
    Queue unknownQueue = helper.getQueue();

    TaskOptions opts = TaskOptions.Builder.withDefaults();
    Transaction txn = null;
    Iterable<TaskOptions> optsIter = Lists.newArrayList(opts);

    // We never wait for any of the these futures.
    Future<TaskHandle> unusedFutureHandle = unknownQueue.addAsync();
    unusedFutureHandle = unknownQueue.addAsync(opts);
    unusedFutureHandle = unknownQueue.addAsync(txn, opts);
    Future<List<TaskHandle>> unusedFutureHandleList = unknownQueue.addAsync(optsIter);
    unusedFutureHandleList = unknownQueue.addAsync(txn, optsIter);
  }

  @Test
  public void testFetchStatisticsUnpopulatedResponse() throws Exception {
    assertThrows(QueueFailureException.class, () -> silentFailureQueue.fetchStatistics());
  }

  @Test
  public void testAsyncFetchStatisticsNegativeDeadline() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> silentFailureQueue.fetchStatisticsAsync(-123.456));
  }

  @Test
  public void testAsyncFetchStatisticsZeroDeadline() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> silentFailureQueue.fetchStatisticsAsync(0.0));
  }

  @Test
  public void testAsyncFetchStatisticsPositiveDeadline() throws Exception {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.TOMBSTONED_QUEUE_VALUE);
    Queue tombstonedQueue = helper.getQueue();
    Future<QueueStatistics> futureHandle = tombstonedQueue.fetchStatisticsAsync(60.0);
    assertThrows(ExecutionException.class, futureHandle::get);
    assertThat(helper.getMadeAsyncCall()).isTrue();
    assertThat(helper.getDeadlineInSeconds()).isEqualTo(60.0);
  }

  @Test
  public void testAsyncFetchStatisticsNullDeadline() throws Exception {
    ApplicationErrorQueueApiHelper helper =
        new ApplicationErrorQueueApiHelper(ErrorCode.TOMBSTONED_QUEUE_VALUE);
    Queue tombstonedQueue = helper.getQueue();
    Future<QueueStatistics> futureHandle = tombstonedQueue.fetchStatisticsAsync(null);
    assertThrows(ExecutionException.class, futureHandle::get);
    assertThat(helper.getMadeAsyncCall()).isTrue();
    assertThat(helper.getDeadlineInSeconds())
        .isEqualTo(QueueImpl.DEFAULT_FETCH_STATISTICS_DEADLINE_SECONDS);
  }
}
