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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.cloud.location.ListLocationsRequest;
import com.google.cloud.location.ListLocationsResponse;
import com.google.cloud.location.Location;
import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.BatchCreateTasksMetadata;
import com.google.cloud.tasks.v2.BatchCreateTasksRequest;
import com.google.cloud.tasks.v2.BatchCreateTasksResponse;
import com.google.cloud.tasks.v2.BatchDeleteTasksMetadata;
import com.google.cloud.tasks.v2.BatchDeleteTasksRequest;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.DeleteTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.PurgeQueueRequest;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.RetryConfig;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.stub.CloudTasksStub;
import com.google.cloud.tasks.v2beta3.GetQueueRequest;
import com.google.cloud.tasks.v2beta3.QueueStats;
import com.google.cloud.tasks.v2beta3.RateLimits;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CloudTasksClientWrapper} and {@link TaskProcessor} v2 gRPC paths. */
@RunWith(JUnit4.class)
public class CloudTasksClientWrapperTest {
  private static final String TEST_PROJECT = "test-proj";
  private static final String TEST_LOCATION = "us-central1";
  private static final String TEST_TOKEN = "fake-oauth-token";

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig().setNoIndexAutoGen(true));

  private HttpServer server;
  private String baseUrl;

  private final List<CreateTaskRequest> createTaskRequests = new CopyOnWriteArrayList<>();
  private final List<BatchCreateTasksRequest> batchCreateRequests = new CopyOnWriteArrayList<>();
  private final List<DeleteTaskRequest> deleteTaskRequests = new CopyOnWriteArrayList<>();
  private final List<BatchDeleteTasksRequest> batchDeleteRequests = new CopyOnWriteArrayList<>();
  private final List<PurgeQueueRequest> purgeQueueRequests = new CopyOnWriteArrayList<>();
  private final List<GetQueueRequest> getQueueRequests = new CopyOnWriteArrayList<>();
  private final List<ListLocationsRequest> listLocationsRequests = new CopyOnWriteArrayList<>();

  private volatile Function<CreateTaskRequest, ApiFuture<Task>> createTaskHandler;
  private volatile Function<BatchCreateTasksRequest, ApiFuture<Operation>> batchCreateHandler;
  private volatile Function<DeleteTaskRequest, ApiFuture<Empty>> deleteTaskHandler;
  private volatile Function<BatchDeleteTasksRequest, ApiFuture<Operation>> batchDeleteHandler;
  private volatile Function<PurgeQueueRequest, ApiFuture<com.google.cloud.tasks.v2.Queue>>
      purgeQueueHandler;
  private volatile Function<GetQueueRequest, ApiFuture<com.google.cloud.tasks.v2beta3.Queue>>
      getQueueHandler;
  private volatile Function<ListLocationsRequest, ApiFuture<ListLocationsResponse>>
      listLocationsHandler;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    createTaskRequests.clear();
    batchCreateRequests.clear();
    deleteTaskRequests.clear();
    batchDeleteRequests.clear();
    purgeQueueRequests.clear();
    getQueueRequests.clear();
    listLocationsRequests.clear();

    InetAddress loopback = InetAddress.getLoopbackAddress();
    server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    server.start();
    String host =
        loopback instanceof Inet6Address
            ? "[" + loopback.getHostAddress() + "]"
            : loopback.getHostAddress();
    baseUrl = "http://" + host + ":" + server.getAddress().getPort();

    createTaskHandler =
        req -> {
          String name =
              req.getTask().getName().isEmpty()
                  ? req.getParent() + "/tasks/generated-1"
                  : req.getTask().getName();
          return ApiFutures.immediateFuture(
              Task.newBuilder()
                  .setName(name)
                  .setScheduleTime(
                      req.getTask().hasScheduleTime()
                          ? req.getTask().getScheduleTime()
                          : Timestamp.newBuilder().setSeconds(1790812805L).build())
                  .build());
        };
    batchCreateHandler =
        req -> {
          BatchCreateTasksResponse.Builder respBuilder = BatchCreateTasksResponse.newBuilder();
          int idx = 0;
          for (CreateTaskRequest subReq : req.getRequestsList()) {
            String name =
                subReq.getTask().getName().isEmpty()
                    ? subReq.getParent() + "/tasks/generated-" + idx
                    : subReq.getTask().getName();
            respBuilder.addTasks(Task.newBuilder().setName(name).build());
            idx++;
          }
          return ApiFutures.immediateFuture(
              Operation.newBuilder()
                  .setDone(true)
                  .setResponse(Any.pack(respBuilder.build()))
                  .build());
        };
    deleteTaskHandler = req -> ApiFutures.immediateFuture(Empty.getDefaultInstance());
    batchDeleteHandler =
        req -> ApiFutures.immediateFuture(Operation.newBuilder().setDone(true).build());
    purgeQueueHandler =
        req ->
            ApiFutures.immediateFuture(
                com.google.cloud.tasks.v2.Queue.newBuilder().setName(req.getName()).build());
    getQueueHandler =
        req ->
            ApiFutures.immediateFuture(
                com.google.cloud.tasks.v2beta3.Queue.newBuilder()
                    .setName(req.getName())
                    .setRateLimits(RateLimits.newBuilder().setMaxDispatchesPerSecond(12.5).build())
                    .build());
    listLocationsHandler =
        req -> ApiFutures.immediateFuture(ListLocationsResponse.getDefaultInstance());

    CloudTasksStub stub =
        new CloudTasksStub() {
          @Override
          public UnaryCallable<CreateTaskRequest, Task> createTaskCallable() {
            return new UnaryCallable<CreateTaskRequest, Task>() {
              @Override
              public ApiFuture<Task> futureCall(CreateTaskRequest request, ApiCallContext context) {
                createTaskRequests.add(request);
                return createTaskHandler.apply(request);
              }
            };
          }

          @Override
          public UnaryCallable<BatchCreateTasksRequest, Operation> batchCreateTasksCallable() {
            return new UnaryCallable<BatchCreateTasksRequest, Operation>() {
              @Override
              public ApiFuture<Operation> futureCall(
                  BatchCreateTasksRequest request, ApiCallContext context) {
                batchCreateRequests.add(request);
                return batchCreateHandler.apply(request);
              }
            };
          }

          @Override
          public UnaryCallable<DeleteTaskRequest, Empty> deleteTaskCallable() {
            return new UnaryCallable<DeleteTaskRequest, Empty>() {
              @Override
              public ApiFuture<Empty> futureCall(
                  DeleteTaskRequest request, ApiCallContext context) {
                deleteTaskRequests.add(request);
                return deleteTaskHandler.apply(request);
              }
            };
          }

          @Override
          public UnaryCallable<BatchDeleteTasksRequest, Operation> batchDeleteTasksCallable() {
            return new UnaryCallable<BatchDeleteTasksRequest, Operation>() {
              @Override
              public ApiFuture<Operation> futureCall(
                  BatchDeleteTasksRequest request, ApiCallContext context) {
                batchDeleteRequests.add(request);
                return batchDeleteHandler.apply(request);
              }
            };
          }

          @Override
          public UnaryCallable<PurgeQueueRequest, com.google.cloud.tasks.v2.Queue>
              purgeQueueCallable() {
            return new UnaryCallable<PurgeQueueRequest, com.google.cloud.tasks.v2.Queue>() {
              @Override
              public ApiFuture<com.google.cloud.tasks.v2.Queue> futureCall(
                  PurgeQueueRequest request, ApiCallContext context) {
                purgeQueueRequests.add(request);
                return purgeQueueHandler.apply(request);
              }
            };
          }

          @Override
          public UnaryCallable<ListLocationsRequest, ListLocationsResponse>
              listLocationsCallable() {
            return new UnaryCallable<ListLocationsRequest, ListLocationsResponse>() {
              @Override
              public ApiFuture<ListLocationsResponse> futureCall(
                  ListLocationsRequest request, ApiCallContext context) {
                listLocationsRequests.add(request);
                return listLocationsHandler.apply(request);
              }
            };
          }

          @Override
          public void close() {}

          @Override
          public void shutdown() {}

          @Override
          public boolean isShutdown() {
            return false;
          }

          @Override
          public boolean isTerminated() {
            return false;
          }

          @Override
          public void shutdownNow() {}

          @Override
          public boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
          }
        };

    com.google.cloud.tasks.v2beta3.stub.CloudTasksStub v2beta3Stub =
        new com.google.cloud.tasks.v2beta3.stub.CloudTasksStub() {
          @Override
          public UnaryCallable<GetQueueRequest, com.google.cloud.tasks.v2beta3.Queue>
              getQueueCallable() {
            return new UnaryCallable<GetQueueRequest, com.google.cloud.tasks.v2beta3.Queue>() {
              @Override
              public ApiFuture<com.google.cloud.tasks.v2beta3.Queue> futureCall(
                  GetQueueRequest request, ApiCallContext context) {
                getQueueRequests.add(request);
                return getQueueHandler.apply(request);
              }
            };
          }

          @Override
          public void close() {}

          @Override
          public void shutdown() {}

          @Override
          public boolean isShutdown() {
            return false;
          }

          @Override
          public boolean isTerminated() {
            return false;
          }

          @Override
          public void shutdownNow() {}

          @Override
          public boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
          }
        };

    long futureExpiry = Instant.now().plusSeconds(3600).toEpochMilli();
    CloudTasksClientWrapper.setEnabledForTesting(true);
    CloudTasksClientWrapper.setSharedClientForTesting(CloudTasksClient.create(stub));
    CloudTasksClientWrapper.setSharedV2Beta3ClientForTesting(
        com.google.cloud.tasks.v2beta3.CloudTasksClient.create(v2beta3Stub));
    CloudTasksClientWrapper.setProjectIdForTesting(TEST_PROJECT);
    CloudTasksClientWrapper.setLocationForTesting(TEST_LOCATION);
    CloudTasksClientWrapper.setAccessTokenForTesting(TEST_TOKEN, futureExpiry);
    CloudTasksClientWrapper.setMetadataBaseUrlForTesting(null);
    CloudTasksClientWrapper.setCredentialsTokenForTesting(null);
  }

  @After
  public void tearDown() {
    CloudTasksClientWrapper.setEnabledForTesting(null);
    CloudTasksClientWrapper.setSharedClientForTesting(null);
    CloudTasksClientWrapper.setSharedV2Beta3ClientForTesting(null);
    CloudTasksClientWrapper.setProjectIdForTesting(null);
    CloudTasksClientWrapper.setLocationForTesting(null);
    CloudTasksClientWrapper.setAccessTokenForTesting(null, 0);
    CloudTasksClientWrapper.setMetadataBaseUrlForTesting(null);
    CloudTasksClientWrapper.setCredentialsTokenForTesting(null);

    if (server != null) {
      server.stop(0);
    }
    helper.tearDown();
  }

  @Test
  public void testAddSinglePushTask_encodesParamsHostRoutingAndRetryConfig() throws Exception {
    Queue queue = QueueFactory.getDefaultQueue();
    TaskOptions options =
        TaskOptions.Builder.withUrl("/_ah/queue/deferred")
            .method(TaskOptions.Method.POST)
            .taskName("task-1")
            .param("key1", "val 1")
            .param("key2", "a&b")
            .header("Host", "2.worker.test-proj.appspot.com")
            .header("X-Custom-Header", "custom-val")
            .header("X-AppEngine-QueueName", "should-be-stripped")
            .header("X-Google-Internal", "should-be-stripped")
            .countdownMillis(5000)
            .retryOptions(
                RetryOptions.Builder.withTaskRetryLimit(5)
                    .taskAgeLimitSeconds(3600)
                    .minBackoffSeconds(2.5)
                    .maxBackoffSeconds(60)
                    .maxDoublings(4));

    TaskHandle handle = queue.add(options);

    assertThat(handle.getName()).isEqualTo("task-1");
    assertThat(handle.getQueueName()).isEqualTo("default");
    assertThat(handle.getEtaMillis()).isGreaterThan(0L);
    List<Map.Entry<String, String>> extractedParams = handle.extractParams();
    assertThat(extractedParams).hasSize(2);
    assertThat(extractedParams.get(0).getKey()).isEqualTo("key1");
    assertThat(extractedParams.get(0).getValue()).isEqualTo("val 1");
    assertThat(extractedParams.get(1).getKey()).isEqualTo("key2");
    assertThat(extractedParams.get(1).getValue()).isEqualTo("a&b");

    assertThat(createTaskRequests).hasSize(1);
    Task task = createTaskRequests.get(0).getTask();
    assertThat(task.getName())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default/tasks/task-1");
    assertThat(task.hasScheduleTime()).isTrue();

    AppEngineHttpRequest appEngineReq = task.getAppEngineHttpRequest();
    assertThat(appEngineReq.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(appEngineReq.getRelativeUri()).isEqualTo("/_ah/queue/deferred");

    assertThat(appEngineReq.getAppEngineRouting().getService()).isEqualTo("worker");
    assertThat(appEngineReq.getAppEngineRouting().getVersion()).isEqualTo("2");

    Map<String, String> headers = appEngineReq.getHeadersMap();
    assertThat(headers).containsEntry("X-Custom-Header", "custom-val");
    assertThat(headers).containsEntry("Content-Type", "application/x-www-form-urlencoded");
    assertThat(headers).containsEntry("X-Task-Retry-Limit", "5");
    assertThat(headers).containsEntry("X-Task-Age-Limit-Seconds", "3600");
    assertThat(headers).containsEntry("X-Task-Min-Backoff-Seconds", "2.5");
    assertThat(headers).containsEntry("X-Task-Max-Backoff-Seconds", "60.0");
    assertThat(headers).containsEntry("X-Task-Max-Doublings", "4");
    assertThat(headers).doesNotContainKey("Host");
    assertThat(headers).doesNotContainKey("X-AppEngine-QueueName");
    assertThat(headers).doesNotContainKey("X-Google-Internal");

    assertThat(appEngineReq.getBody().toStringUtf8()).isEqualTo("key1=val+1&key2=a%26b");

    RetryConfig retryConfig = task.getRetryConfig();
    assertThat(retryConfig.getMaxAttempts()).isEqualTo(6);
    assertThat(retryConfig.getMaxRetryDuration().getSeconds()).isEqualTo(3600L);
    assertThat(retryConfig.getMinBackoff().getSeconds()).isEqualTo(2L);
    assertThat(retryConfig.getMinBackoff().getNanos()).isEqualTo(500_000_000);
    assertThat(retryConfig.getMaxBackoff().getSeconds()).isEqualTo(60L);
    assertThat(retryConfig.getMaxDoublings()).isEqualTo(4);
  }

  @Test
  public void testAddSingleGetTask_appendsParamsToQueryString() {
    createTaskHandler =
        req ->
            ApiFutures.immediateFuture(
                Task.newBuilder()
                    .setName(
                        "projects/test-proj/locations/us-central1/queues/default/tasks/get-task-1")
                    .build());

    Queue queue = QueueFactory.getDefaultQueue();
    TaskHandle handle =
        queue.add(
            TaskOptions.Builder.withUrl("/worker/get")
                .method(TaskOptions.Method.GET)
                .param("foo", "bar baz")
                .param("num", "42"));

    assertThat(handle.getName()).isEqualTo("get-task-1");
    assertThat(createTaskRequests).hasSize(1);
    AppEngineHttpRequest appEngineReq = createTaskRequests.get(0).getTask().getAppEngineHttpRequest();
    assertThat(appEngineReq.getRelativeUri()).isEqualTo("/worker/get?foo=bar+baz&num=42");
    assertThat(appEngineReq.getBody().isEmpty()).isTrue();
  }

  @Test
  public void testAddSingleTask_conflictThrowsTaskAlreadyExistsException() {
    createTaskHandler =
        req -> ApiFutures.immediateFailedFuture(new IllegalStateException("ALREADY_EXISTS"));

    Queue queue = QueueFactory.getDefaultQueue();
    TaskOptions dupOptions = TaskOptions.Builder.withUrl("/work").taskName("dup-task");
    TaskAlreadyExistsException ex =
        assertThrows(TaskAlreadyExistsException.class, () -> queue.add(dupOptions));
    assertThat(ex.getTaskNames()).containsExactly("dup-task");
  }

  @Test
  public void testAddBatchPushTasks_handlesPartialFailureFromOperationMetadata() {
    batchCreateHandler =
        req -> {
          BatchCreateTasksMetadata metadata =
              BatchCreateTasksMetadata.newBuilder()
                  .putFailedRequests(
                      1,
                      Status.newBuilder()
                          .setCode(6)
                          .setMessage("Task already exists")
                          .build())
                  .build();
          BatchCreateTasksResponse resp =
              BatchCreateTasksResponse.newBuilder()
                  .addTasks(
                      Task.newBuilder()
                          .setName(
                              "projects/test-proj/locations/us-central1/queues/default/tasks/t1")
                          .build())
                  .addTasks(
                      Task.newBuilder()
                          .setName(
                              "projects/test-proj/locations/us-central1/queues/default/tasks/t3")
                          .build())
                  .build();
          return ApiFutures.immediateFuture(
              Operation.newBuilder()
                  .setDone(true)
                  .setMetadata(Any.pack(metadata))
                  .setResponse(Any.pack(resp))
                  .build());
        };

    Queue queue = QueueFactory.getDefaultQueue();
    List<TaskOptions> batchOptions =
        Arrays.asList(
            TaskOptions.Builder.withUrl("/work")
                .taskName("t1")
                .retryOptions(RetryOptions.Builder.withTaskRetryLimit(2)),
            TaskOptions.Builder.withUrl("/work")
                .taskName("t2")
                .retryOptions(RetryOptions.Builder.withMaxDoublings(2)),
            TaskOptions.Builder.withUrl("/work").taskName("t3"));
    TaskAlreadyExistsException ex =
        assertThrows(TaskAlreadyExistsException.class, () -> queue.add(batchOptions));

    assertThat(ex.getTaskNames()).containsExactly("t2");
    assertThat(batchCreateRequests).hasSize(1);
    assertThat(batchCreateRequests.get(0).getRequestsList()).hasSize(3);
    assertThat(batchCreateRequests.get(0).getRequests(0).getParent())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default");
  }

  @Test
  public void testDeleteTaskBatch_returnsPerTaskStatusFromOperationMetadata() {
    batchDeleteHandler =
        req -> {
          BatchDeleteTasksMetadata metadata =
              BatchDeleteTasksMetadata.newBuilder()
                  .putFailedRequests(1, Status.newBuilder().setCode(5).setMessage("NOT_FOUND").build())
                  .build();
          return ApiFutures.immediateFuture(
              Operation.newBuilder().setDone(true).setMetadata(Any.pack(metadata)).build());
        };

    Queue queue = QueueFactory.getDefaultQueue();
    List<TaskHandle> handles =
        Arrays.asList(
            new TaskHandle(TaskOptions.Builder.withTaskName("del-1"), "default"),
            new TaskHandle(TaskOptions.Builder.withTaskName("del-2"), "default"),
            new TaskHandle(TaskOptions.Builder.withTaskName("del-3"), "default"));

    List<Boolean> results = queue.deleteTask(handles);
    assertThat(results).containsExactly(true, false, true).inOrder();
    assertThat(batchDeleteRequests).hasSize(1);
    assertThat(batchDeleteRequests.get(0).getNamesList())
        .containsExactly(
            "projects/test-proj/locations/us-central1/queues/default/tasks/del-1",
            "projects/test-proj/locations/us-central1/queues/default/tasks/del-2",
            "projects/test-proj/locations/us-central1/queues/default/tasks/del-3")
        .inOrder();
  }

  @Test
  public void testDeleteTaskBatch_queueNameMismatchThrowsBeforeRpc() {
    Queue queue = QueueFactory.getDefaultQueue();
    List<TaskHandle> handles =
        Collections.singletonList(
            new TaskHandle(TaskOptions.Builder.withTaskName("del-1"), "other-queue"));

    assertThrows(QueueNameMismatchException.class, () -> queue.deleteTask(handles));
    assertThat(batchDeleteRequests).isEmpty();
    assertThat(deleteTaskRequests).isEmpty();
  }

  @Test
  public void testDeleteTask_errorMapping() {
    deleteTaskHandler =
        req -> {
          if (req.getName().endsWith("/missing-task")) {
            return ApiFutures.immediateFailedFuture(
                new IllegalStateException("NOT_FOUND: task missing"));
          }
          if (req.getName().contains("/queues/missing-queue/")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("NOT_FOUND: Queue does not exist"));
          }
          if (req.getName().endsWith("/invalid-arg")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("INVALID_ARGUMENT: bad task"));
          }
          if (req.getName().endsWith("/perm-denied")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException(new IllegalStateException("PERMISSION_DENIED")));
          }
          return ApiFutures.immediateFuture(Empty.getDefaultInstance());
        };

    Queue defaultQueue = QueueFactory.getDefaultQueue();
    assertThat(defaultQueue.deleteTask("existing-task")).isTrue();
    assertThat(defaultQueue.deleteTask("missing-task")).isFalse();
    assertThrows(IllegalArgumentException.class, () -> defaultQueue.deleteTask("invalid-arg"));
    assertThrows(SecurityException.class, () -> defaultQueue.deleteTask("perm-denied"));

    Queue missingQueue = QueueFactory.getQueue("missing-queue");
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> missingQueue.deleteTask("task-1"));
    assertThat(ex).hasMessageThat().contains("missing-queue");
  }

  @Test
  public void testTransactionalAdd_stagesInDatastoreAndDispatchesAsync() throws Exception {
    List<String> createdTasks = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    createTaskHandler =
        req -> {
          String name = req.getTask().getName();
          createdTasks.add(name);
          latch.countDown();
          return ApiFutures.immediateFuture(Task.newBuilder().setName(name).build());
        };

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();

    Transaction txn = ds.beginTransaction();
    TaskHandle handle =
        queue.add(
            txn,
            TaskOptions.Builder.withUrl("/txn-handler")
                .method(TaskOptions.Method.POST)
                .payload("txn-payload"));
    txn.commit();

    assertThat(handle.getName()).isNotEmpty();
    assertThat(handle.getEtaMillis()).isGreaterThan(0L);
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

    assertThat(createdTasks)
        .contains(
            "projects/test-proj/locations/us-central1/queues/default/tasks/" + handle.getName());

    List<Entity> remaining = Collections.emptyList();
    for (int i = 0; i < 20; i++) {
      remaining =
          ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
              .asList(FetchOptions.Builder.withDefaults());
      if (remaining.isEmpty()) {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(remaining).isEmpty();
  }

  @Test
  public void testSweep_recoversStalePendingTasks() throws Exception {
    List<String> createdTasks = new CopyOnWriteArrayList<>();
    createTaskHandler =
        req -> {
          String name = req.getTask().getName();
          createdTasks.add(name);
          return ApiFutures.immediateFuture(Task.newBuilder().setName(name).build());
        };

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity pending = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    pending.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    pending.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "swept-task-1");
    pending.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/swept-task-1\","
            + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/sweep-handler\"}}");
    pending.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(Instant.now().minusSeconds(120)));
    pending.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    ds.put(pending);

    int swept = TaskProcessor.sweep();
    assertThat(swept).isEqualTo(1);
    assertThat(createdTasks)
        .contains("projects/test-proj/locations/us-central1/queues/default/tasks/swept-task-1");
    List<Entity> remaining =
        ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
            .asList(FetchOptions.Builder.withDefaults());
    assertThat(remaining).isEmpty();
  }

  @Test
  public void testTransactionalAdd_rollbackDiscardsPendingEntity() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();

    Transaction txn = ds.beginTransaction();
    TaskHandle unused =
        queue.add(
            txn,
            TaskOptions.Builder.withUrl("/txn-rollback")
                .method(TaskOptions.Method.POST)
                .payload("rollback-payload"));
    txn.rollback();

    List<Entity> entities =
        ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
            .asList(FetchOptions.Builder.withDefaults());
    assertThat(entities).isEmpty();
  }

  @Test
  public void testFetchStatisticsAndPurge() {
    getQueueHandler =
        req ->
            ApiFutures.immediateFuture(
                com.google.cloud.tasks.v2beta3.Queue.newBuilder()
                    .setName(req.getName())
                    .setRateLimits(RateLimits.newBuilder().setMaxDispatchesPerSecond(12.5).build())
                    .setStats(
                        QueueStats.newBuilder()
                            .setTasksCount(12L)
                            .setOldestEstimatedArrivalTime(
                                Timestamp.newBuilder().setSeconds(1790812810L).build())
                            .setExecutedLastMinuteCount(5L)
                            .setConcurrentDispatchesCount(2L)
                            .setEffectiveExecutionRate(3.5)
                            .build())
                    .build());

    Queue queue = QueueFactory.getDefaultQueue();
    QueueStatistics stats = queue.fetchStatistics();
    assertThat(stats.getQueueName()).isEqualTo("default");
    assertThat(stats.getNumTasks()).isEqualTo(12);
    assertThat(stats.getOldestEtaUsec()).isEqualTo(1790812810000000L);
    assertThat(stats.getExecutedLastMinute()).isEqualTo(5L);
    assertThat(stats.getRequestsInFlight()).isEqualTo(2);
    assertThat(stats.getEnforcedRate()).isWithin(0.001).of(3.5);
    assertThat(getQueueRequests).hasSize(1);
    assertThat(getQueueRequests.get(0).getReadMask().getPathsList())
        .containsExactly("stats", "rate_limits");

    assertThrows(IllegalArgumentException.class, () -> queue.fetchStatisticsAsync(0.0));

    queue.purge();
    assertThat(purgeQueueRequests).hasSize(1);
    assertThat(purgeQueueRequests.get(0).getName())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default");

    purgeQueueHandler =
        req -> {
          if (req.getName().endsWith("/missing-q")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("NOT_FOUND: Queue does not exist"));
          }
          if (req.getName().endsWith("/forbidden-q")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("PERMISSION_DENIED"));
          }
          return ApiFutures.immediateFuture(com.google.cloud.tasks.v2.Queue.getDefaultInstance());
        };
    getQueueHandler =
        req -> {
          if (req.getName().endsWith("/missing-q")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("NOT_FOUND: Queue does not exist"));
          }
          if (req.getName().endsWith("/forbidden-q")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("PERMISSION_DENIED"));
          }
          return ApiFutures.immediateFuture(
              com.google.cloud.tasks.v2beta3.Queue.getDefaultInstance());
        };
    Queue missingQueue = QueueFactory.getQueue("missing-q");
    Queue forbiddenQueue = QueueFactory.getQueue("forbidden-q");
    assertThrows(IllegalStateException.class, missingQueue::fetchStatistics);
    assertThrows(SecurityException.class, forbiddenQueue::fetchStatistics);
    assertThrows(IllegalStateException.class, missingQueue::purge);
    assertThrows(SecurityException.class, forbiddenQueue::purge);
  }

  @Test
  public void testTransactionalTaskOversizedThrowsIllegalArgumentException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    createTaskHandler =
        req -> {
          latch.countDown();
          return ApiFutures.immediateFuture(
              Task.newBuilder().setName(req.getTask().getName()).build());
        };

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();

    // 1. Verify a 60KB payload (>1500 bytes Datastore String limit) succeeds via Datastore Text
    Transaction txn1 = ds.beginTransaction();
    byte[] largePayload = new byte[60 * 1024];
    TaskHandle handle =
        queue.add(
            txn1,
            TaskOptions.Builder.withUrl("/large-txn")
                .payload(largePayload, "application/octet-stream"));
    txn1.commit();
    assertThat(handle.getName()).isNotEmpty();
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

    // 2. Verify a transactional batch exceeding 1MB throws IllegalArgumentException
    Transaction txn2 = ds.beginTransaction();
    try {
      byte[] chunkPayload = new byte[95 * 1024];
      List<TaskOptions> oversizedBatch = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        oversizedBatch.add(
            TaskOptions.Builder.withUrl("/oversized" + i)
                .payload(chunkPayload, "application/octet-stream"));
      }
      IllegalArgumentException ex =
          assertThrows(IllegalArgumentException.class, () -> queue.addAsync(txn2, oversizedBatch));
      assertThat(ex).hasMessageThat().contains("Transactional add may not be larger than");
    } finally {
      if (txn2.isActive()) {
        txn2.rollback();
      }
    }
  }

  @Test
  public void testBatchCreateTasks_fallbackIdempotencyForUnnamedTasks() throws Exception {
    List<String> batchTaskNames = new ArrayList<>();
    List<String> fallbackTaskNames = new ArrayList<>();

    batchCreateHandler =
        req -> {
          for (CreateTaskRequest subReq : req.getRequestsList()) {
            batchTaskNames.add(subReq.getTask().getName());
          }
          return ApiFutures.immediateFailedFuture(
              new RuntimeException("UNAVAILABLE: Service Unavailable"));
        };
    createTaskHandler =
        req -> {
          String name = req.getTask().getName();
          fallbackTaskNames.add(name);
          if (fallbackTaskNames.size() == 1) {
            return ApiFutures.immediateFailedFuture(new IllegalStateException("ALREADY_EXISTS"));
          }
          return ApiFutures.immediateFuture(Task.newBuilder().setName(name).build());
        };

    Queue queue = QueueFactory.getDefaultQueue();
    TaskOptions opt1 = TaskOptions.Builder.withUrl("/unnamed-1");
    TaskOptions opt2 = TaskOptions.Builder.withUrl("/unnamed-2");

    List<TaskHandle> handles = queue.add(Arrays.asList(opt1, opt2));

    assertThat(opt1.getTaskName()).isNull();
    assertThat(opt2.getTaskName()).isNull();

    assertThat(handles).hasSize(2);
    assertThat(handles.get(0).getName()).startsWith("task-");
    assertThat(handles.get(0).getEtaMillis()).isGreaterThan(0L);
    assertThat(handles.get(1).getName()).startsWith("task-");
    assertThat(handles.get(1).getEtaMillis()).isGreaterThan(0L);
    assertThat(batchTaskNames).hasSize(2);
    assertThat(fallbackTaskNames).containsExactlyElementsIn(batchTaskNames).inOrder();
  }

  @Test
  public void testSweep_multilineJsonPayloadIsNotDoubleWrapped() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity pending = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    pending.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    pending.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "swept-1");
    pending.setUnindexedProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            """
            {
              "task": {
                "appEngineHttpRequest": {
                  "httpMethod": "POST",
                  "relativeUri": "/formatted"
                }
              }
            }\
            """));
    pending.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(Instant.now().minusSeconds(120)));
    pending.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    ds.put(pending);

    int swept = TaskProcessor.sweep();
    assertThat(swept).isEqualTo(1);
    assertThat(createTaskRequests).hasSize(1);
    assertThat(createTaskRequests.get(0).getTask().getAppEngineHttpRequest().getRelativeUri())
        .isEqualTo("/formatted");
    List<Entity> remaining =
        ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
            .asList(FetchOptions.Builder.withDefaults());
    assertThat(remaining).isEmpty();
  }

  @Test
  public void testGetTask_existingQueryParamsAppendsWithAmpersand() throws Exception {
    createTaskHandler =
        req ->
            ApiFutures.immediateFuture(
                Task.newBuilder()
                    .setName("projects/test-proj/locations/us-central1/queues/default/tasks/t-get")
                    .build());

    List<TaskHandle> handles =
        CloudTasksClientWrapper.addAsync(
                "default",
                null,
                Collections.singletonList(
                    TaskOptions.Builder.withUrl("/worker?type=async")
                        .method(TaskOptions.Method.GET)
                        .param("foo", "bar")
                        .param("baz", "qux")))
            .get();
    assertThat(handles).hasSize(1);
    assertThat(handles.get(0).getName()).isEqualTo("t-get");
    assertThat(createTaskRequests.get(0).getTask().getAppEngineHttpRequest().getRelativeUri())
        .isEqualTo("/worker?type=async&foo=bar&baz=qux");
  }

  @Test
  public void testAddSingleTask_transparentRetryAlreadyExistsSucceedsIdempotently() {
    createTaskHandler =
        req -> ApiFutures.immediateFailedFuture(new IllegalStateException("ALREADY_EXISTS"));

    Queue queue = QueueFactory.getDefaultQueue();
    TaskOptions unnamed = TaskOptions.Builder.withUrl("/single-unnamed");
    TaskHandle handle = queue.add(unnamed);

    assertThat(unnamed.getTaskName()).isNull();
    assertThat(handle.getName()).startsWith("task-");
  }

  @Test
  public void testTransactionalAdd_multiTenantNamespaceIsolatedInDefaultNamespace()
      throws Exception {
    List<String> createdTasks = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    createTaskHandler =
        req -> {
          String name = req.getTask().getName();
          createdTasks.add(name);
          latch.countDown();
          return ApiFutures.immediateFuture(Task.newBuilder().setName(name).build());
        };

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();

    NamespaceManager.set("tenant-alpha");
    try {
      Transaction txn = ds.beginTransaction();
      TaskHandle handle =
          queue.add(
              txn,
              TaskOptions.Builder.withUrl("/tenant-task")
                  .method(TaskOptions.Method.POST)
                  .payload("tenant-data"));
      assertThat(NamespaceManager.get()).isEqualTo("tenant-alpha");
      txn.commit();
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(createdTasks)
          .contains(
              "projects/test-proj/locations/us-central1/queues/default/tasks/" + handle.getName());
    } finally {
      NamespaceManager.set("");
    }
  }

  @Test
  public void testBuildCreateTaskRequest_populatesNativeRetryConfigAndHeaders() {
    TaskOptions opts =
        TaskOptions.Builder.withUrl("/retry-sdk")
            .taskName("retry-task-1")
            .etaMillis(1700000000000L)
            .retryOptions(
                RetryOptions.Builder.withTaskRetryLimit(4)
                    .taskAgeLimitSeconds(120)
                    .minBackoffSeconds(1.5)
                    .maxBackoffSeconds(30.0)
                    .maxDoublings(3));
    long[] scheduleTimeHolder = new long[1];
    CreateTaskRequest req =
        CloudTasksClientWrapper.buildCreateTaskRequest(
            QueueName.of("test-proj", "us-central1", "default"),
            "test-proj",
            "us-central1",
            "default",
            "default",
            opts,
            scheduleTimeHolder);

    assertThat(scheduleTimeHolder[0]).isEqualTo(1700000000000L);
    assertThat(req.getTask().getName())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default/tasks/retry-task-1");
    assertThat(req.getTask().hasRetryConfig()).isTrue();
    RetryConfig retryConfig = req.getTask().getRetryConfig();
    assertThat(retryConfig.getMaxAttempts()).isEqualTo(5);
    assertThat(retryConfig.getMaxRetryDuration().getSeconds()).isEqualTo(120L);
    assertThat(retryConfig.getMinBackoff().getSeconds()).isEqualTo(1L);
    assertThat(retryConfig.getMinBackoff().getNanos()).isEqualTo(500_000_000);
    assertThat(retryConfig.getMaxBackoff().getSeconds()).isEqualTo(30L);
    assertThat(retryConfig.getMaxBackoff().getNanos()).isEqualTo(0);
    assertThat(retryConfig.getMaxDoublings()).isEqualTo(3);

    Map<String, String> headers = req.getTask().getAppEngineHttpRequest().getHeadersMap();
    assertThat(headers).containsEntry("X-Task-Retry-Limit", "4");
    assertThat(headers).containsEntry("X-Task-Age-Limit-Seconds", "120");
    assertThat(headers).containsEntry("X-Task-Min-Backoff-Seconds", "1.5");
    assertThat(headers).containsEntry("X-Task-Max-Backoff-Seconds", "30.0");
    assertThat(headers).containsEntry("X-Task-Max-Doublings", "3");

    CreateTaskRequest limitOnlyReq =
        CloudTasksClientWrapper.buildCreateTaskRequest(
            QueueName.of("test-proj", "us-central1", "default"),
            "test-proj",
            "us-central1",
            "default",
            "default",
            TaskOptions.Builder.withUrl("/limit-only")
                .retryOptions(RetryOptions.Builder.withTaskRetryLimit(2)),
            scheduleTimeHolder);
    assertThat(limitOnlyReq.getTask().getRetryConfig())
        .isEqualTo(RetryConfig.newBuilder().setMaxAttempts(3).build());
    assertThat(limitOnlyReq.getTask().getAppEngineHttpRequest().getHeadersMap())
        .containsExactly("X-Task-Retry-Limit", "2");

    CreateTaskRequest doublingsOnlyReq =
        CloudTasksClientWrapper.buildCreateTaskRequest(
            QueueName.of("test-proj", "us-central1", "default"),
            "test-proj",
            "us-central1",
            "default",
            "default",
            TaskOptions.Builder.withUrl("/doublings-only")
                .retryOptions(RetryOptions.Builder.withMaxDoublings(2)),
            scheduleTimeHolder);
    assertThat(doublingsOnlyReq.getTask().getRetryConfig())
        .isEqualTo(RetryConfig.newBuilder().setMaxDoublings(2).build());
    assertThat(doublingsOnlyReq.getTask().getAppEngineHttpRequest().getHeadersMap())
        .containsExactly("X-Task-Max-Doublings", "2");

    CreateTaskRequest getReq =
        CloudTasksClientWrapper.buildCreateTaskRequest(
            QueueName.of("test-proj", "us-central1", "default"),
            "test-proj",
            "us-central1",
            "default",
            "default",
            TaskOptions.Builder.withUrl("/get-sdk").method(TaskOptions.Method.GET),
            scheduleTimeHolder);
    assertThat(getReq.getTask().getAppEngineHttpRequest().getHttpMethodValue()).isEqualTo(2);

    CreateTaskRequest pullReq =
        CloudTasksClientWrapper.buildCreateTaskRequest(
            QueueName.of("test-proj", "us-central1", "default"),
            "test-proj",
            "us-central1",
            "default",
            "default",
            TaskOptions.Builder.withUrl("/pull-sdk").method(TaskOptions.Method.PULL),
            scheduleTimeHolder);
    assertThat(pullReq.getTask().getAppEngineHttpRequest().getHttpMethodValue()).isEqualTo(1);
  }

  @Test
  public void testBatchDeleteTasks_allTasksNotFoundWithOperationErrorAbortedReturnsFalseList()
      throws Exception {
    batchDeleteHandler =
        req -> {
          BatchDeleteTasksMetadata metadata =
              BatchDeleteTasksMetadata.newBuilder()
                  .putFailedRequests(
                      0,
                      Status.newBuilder()
                          .setCode(5)
                          .setMessage("Requested entity was not found.")
                          .build())
                  .putFailedRequests(
                      1,
                      Status.newBuilder()
                          .setCode(5)
                          .setMessage("Requested entity was not found.")
                          .build())
                  .build();
          return ApiFutures.immediateFuture(
              Operation.newBuilder()
                  .setDone(true)
                  .setError(
                      Status.newBuilder()
                          .setCode(10)
                          .setMessage("None of the requests succeeded")
                          .build())
                  .setMetadata(Any.pack(metadata))
                  .build());
        };

    List<Boolean> results =
        CloudTasksClientWrapper.deleteTaskAsync(
                "default",
                Arrays.asList(
                    new TaskHandle(TaskOptions.Builder.withTaskName("missing-1"), "default"),
                    new TaskHandle(TaskOptions.Builder.withTaskName("missing-2"), "default")))
            .get();
    assertThat(results).containsExactly(false, false).inOrder();
  }

  @Test
  public void testBatchDeleteTasks_rejectsNotDoneOperation() {
    batchDeleteHandler =
        req -> ApiFutures.immediateFuture(Operation.newBuilder().setName("op-1").build());
    List<TaskHandle> handles =
        Arrays.asList(
            new TaskHandle(TaskOptions.Builder.withTaskName("t-1"), "default"),
            new TaskHandle(TaskOptions.Builder.withTaskName("t-2"), "default"));

    Future<List<Boolean>> future = CloudTasksClientWrapper.deleteTaskAsync("default", handles);

    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    assertThat(e).hasCauseThat().hasMessageThat().contains("op-1");
  }

  @Test
  public void testSweep_processingAndFailedStatusBranches() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Environment testEnv = ApiProxy.getCurrentEnvironment();
    List<String> createdTasks = new CopyOnWriteArrayList<>();
    List<Boolean> inFlightHandledBySweeper = new CopyOnWriteArrayList<>();
    List<String> inFlightStatuses = new CopyOnWriteArrayList<>();
    List<Boolean> inFlightHasLockExpires = new CopyOnWriteArrayList<>();
    CountDownLatch emptyPayloadLatch = new CountDownLatch(2);

    createTaskHandler =
        req -> {
          ApiProxy.setEnvironmentForCurrentThread(testEnv);
          String name = req.getTask().getName();
          if (name.endsWith("/fail-500")) {
            return ApiFutures.immediateFailedFuture(new RuntimeException("INTERNAL"));
          }
          if (name.endsWith("/dup-409")) {
            return ApiFutures.immediateFailedFuture(new RuntimeException("ALREADY_EXISTS"));
          }
          if (req.getParent().endsWith("/missing-q")) {
            return ApiFutures.immediateFailedFuture(
                new RuntimeException("NOT_FOUND: Queue does not exist"));
          }
          if (req.getParent().endsWith("/empty-payload-q")) {
            emptyPayloadLatch.countDown();
            return ApiFutures.immediateFuture(Task.newBuilder().setName("ok").build());
          }
          for (Entity e :
              ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
                  .asList(FetchOptions.Builder.withDefaults())) {
            if (name.endsWith("/" + e.getProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME))) {
              inFlightStatuses.add((String) e.getProperty(TaskProcessor.PROPERTY_STATUS));
              inFlightHasLockExpires.add(
                  e.getProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES) != null);
              inFlightHandledBySweeper.add(
                  (Boolean) e.getProperty(TaskProcessor.PROPERTY_HANDLED_BY_SWEEPER));
            }
          }
          createdTasks.add(name);
          return ApiFutures.immediateFuture(Task.newBuilder().setName(name).build());
        };

    Instant now = Instant.now();

    // 1. PROCESSING with active lock -> skipped
    Entity activeLock = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    activeLock.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    activeLock.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "proc-active");
    activeLock.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/proc-active\","
                + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}"));
    activeLock.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(600)));
    activeLock.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PROCESSING);
    activeLock.setProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES, Date.from(now.plusSeconds(60)));
    ds.put(activeLock);

    // 2. PROCESSING with expired lock -> swept
    Entity expiredLock = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    expiredLock.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    expiredLock.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "proc-expired");
    expiredLock.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/proc-expired\","
                + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}"));
    expiredLock.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(600)));
    expiredLock.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PROCESSING);
    expiredLock.setProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES, Date.from(now.minusSeconds(10)));
    ds.put(expiredLock);

    // 3. PROCESSING without lock_expires and recent created -> skipped
    Entity recentProc = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    recentProc.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    recentProc.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "proc-recent");
    recentProc.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/proc-recent\","
                + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}"));
    recentProc.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(5)));
    recentProc.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PROCESSING);
    ds.put(recentProc);

    // 4. FAILED with retry_count >= MAX_RETRY_COUNT (5) -> skipped
    Entity exhaustedFailed = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    exhaustedFailed.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    exhaustedFailed.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "failed-exhausted");
    exhaustedFailed.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/failed-exhausted\","
                + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}"));
    exhaustedFailed.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(600)));
    exhaustedFailed.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_FAILED);
    exhaustedFailed.setProperty(TaskProcessor.PROPERTY_RETRY_COUNT, 5L);
    ds.put(exhaustedFailed);

    // 5. FAILED is terminal and excluded by the sweeper's status filter -> skipped
    Entity retryableFailed = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    retryableFailed.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    retryableFailed.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "failed-retryable");
    retryableFailed.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        new Text(
            "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/failed-retryable\","
                + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}"));
    retryableFailed.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(600)));
    retryableFailed.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_FAILED);
    retryableFailed.setProperty(TaskProcessor.PROPERTY_RETRY_COUNT, 1L);
    ds.put(retryableFailed);

    // 6. DONE -> skipped
    Entity doneEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    doneEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    doneEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "done-task");
    doneEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(600)));
    doneEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_DONE);
    ds.put(doneEntity);

    NamespaceManager.set("custom-ns");
    try {
      int swept = TaskProcessor.sweep();
      assertThat(swept).isEqualTo(1);
      assertThat(NamespaceManager.get()).isEqualTo("custom-ns");
    } finally {
      NamespaceManager.set("");
    }
    assertThat(createdTasks)
        .containsExactly(
            "projects/test-proj/locations/us-central1/queues/default/tasks/proc-expired");
    assertThat(inFlightStatuses).containsExactly(TaskProcessor.STATUS_PROCESSING);
    assertThat(inFlightHasLockExpires).containsExactly(true);
    assertThat(inFlightHandledBySweeper).containsExactly(true);

    Entity failEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    failEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    failEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "fail-500");
    failEntity.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/fail-500\","
            + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}");
    failEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    failEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key failKey = ds.put(failEntity);

    Entity dupEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    dupEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    dupEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "dup-409");
    dupEntity.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/dup-409\","
            + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}");
    dupEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    dupEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key dupKey = ds.put(dupEntity);

    TaskProcessor.processPendingTaskKeysAsync(Arrays.asList(failKey, dupKey), 0L);
    for (int i = 0; i < 30; i++) {
      Entity updatedFail = ds.get(failKey);
      boolean dupStillExists = true;
      try {
        ds.get(dupKey);
      } catch (EntityNotFoundException expectedWhenDeleted) {
        dupStillExists = false;
      }
      if (Objects.equals(updatedFail.getProperty(TaskProcessor.PROPERTY_RETRY_COUNT), 1L)
          && !dupStillExists) {
        break;
      }
      Thread.sleep(50);
    }
    Entity updatedFail = ds.get(failKey);
    assertThat(updatedFail.getProperty(TaskProcessor.PROPERTY_STATUS))
        .isEqualTo(TaskProcessor.STATUS_PENDING);
    assertThat(updatedFail.getProperty(TaskProcessor.PROPERTY_HANDLED_BY_SWEEPER)).isEqualTo(false);
    assertThat(updatedFail.getProperty(TaskProcessor.PROPERTY_RETRY_COUNT)).isEqualTo(1L);
    assertThat(updatedFail.getProperty(TaskProcessor.PROPERTY_LAST_ERROR))
        .isEqualTo("Cloud Tasks Client SDK call failed with INTERNAL_ERROR");
    assertThrows(EntityNotFoundException.class, () -> ds.get(dupKey));

    Entity missingQueueEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    missingQueueEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "missing-q");
    missingQueueEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "t-404");
    missingQueueEntity.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}");
    missingQueueEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    missingQueueEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key missingQueueKey = ds.put(missingQueueEntity);
    TaskProcessor.processPendingTaskKeysAsync(Collections.singletonList(missingQueueKey), 0L);
    for (int i = 0; i < 30; i++) {
      Entity updatedMissing = ds.get(missingQueueKey);
      if (Objects.equals(updatedMissing.getProperty(TaskProcessor.PROPERTY_RETRY_COUNT), 1L)) {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(ds.get(missingQueueKey).getProperty(TaskProcessor.PROPERTY_LAST_ERROR))
        .isEqualTo("Cloud Tasks Client SDK call failed with UNKNOWN_QUEUE");

    Entity nullPayloadEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    nullPayloadEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "empty-payload-q");
    nullPayloadEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "t-null");
    nullPayloadEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    nullPayloadEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key nullPayloadKey = ds.put(nullPayloadEntity);

    Entity blankPayloadEntity = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    blankPayloadEntity.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "empty-payload-q");
    blankPayloadEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "t-blank");
    blankPayloadEntity.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD, "   ");
    blankPayloadEntity.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    blankPayloadEntity.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key blankPayloadKey = ds.put(blankPayloadEntity);

    TaskProcessor.processPendingTaskKeysAsync(
        Arrays.asList(nullPayloadKey, blankPayloadKey), 0L);
    assertThat(emptyPayloadLatch.await(5, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i < 30; i++) {
      if (ds.get(Arrays.asList(nullPayloadKey, blankPayloadKey)).isEmpty()) {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(ds.get(Arrays.asList(nullPayloadKey, blankPayloadKey))).isEmpty();
  }

  @Test
  public void testQueueDeleteTaskList_withPullMethodSkipsCloudTasks() {
    Queue queue = QueueFactory.getDefaultQueue();
    TaskHandle pullHandle =
        new TaskHandle(
            TaskOptions.Builder.withTaskName("pull-1").method(TaskOptions.Method.PULL), "default");
    try {
      List<Boolean> unused = queue.deleteTask(Collections.singletonList(pullHandle));
    } catch (RuntimeException expectedFromUnconfiguredLegacyDelegate) {
      // Expected when local legacy taskqueue delegate is not registered
    }
    assertThat(batchDeleteRequests).isEmpty();
    assertThat(deleteTaskRequests).isEmpty();
  }

  @Test
  public void testMetadataLocationAndTokenFallbacks() throws Exception {
    assertThat(
            CloudTasksClientWrapper.supplyAsyncWithEnv(() -> Thread.currentThread().isDaemon())
                .get())
        .isTrue();

    CloudTasksClientWrapper.setMetadataBaseUrlForTesting(baseUrl);
    CloudTasksClientWrapper.setLocationForTesting(null);

    // 0a. System property gae.location
    System.setProperty("gae.location", "us-west1");
    try {
      assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("us-west1");
    } finally {
      System.clearProperty("gae.location");
      CloudTasksClientWrapper.setLocationForTesting(null);
    }

    // 0b. Region metadata endpoint (with Metadata-Flavor: Google header verification)
    server.createContext(
        "/computeMetadata/v1/instance/region",
        exchange -> {
          if (!Objects.equals(exchange.getRequestHeaders().getFirst("Metadata-Flavor"), "Google")) {
            respondJson(exchange, 403, "");
            return;
          }
          respondJson(exchange, 200, "projects/123/regions/us-east4");
        });
    assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("us-east4");
    CloudTasksClientWrapper.setLocationForTesting(null);
    server.removeContext("/computeMetadata/v1/instance/region");

    // 1. Zone metadata fallback when region returns non-200
    server.createContext(
        "/computeMetadata/v1/instance/region",
        exchange -> {
          if (!Objects.equals(exchange.getRequestHeaders().getFirst("Metadata-Flavor"), "Google")) {
            respondJson(exchange, 403, "");
            return;
          }
          respondJson(exchange, 202, "projects/123/regions/wrong-region");
        });
    server.createContext(
        "/computeMetadata/v1/instance/zone",
        exchange -> {
          if (!Objects.equals(exchange.getRequestHeaders().getFirst("Metadata-Flavor"), "Google")) {
            respondJson(exchange, 403, "");
            return;
          }
          respondJson(exchange, 200, "projects/123/zones/europe-west1-b");
        });
    assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("europe-west1");
    server.removeContext("/computeMetadata/v1/instance/zone");
    server.createContext(
        "/computeMetadata/v1/instance/zone",
        exchange -> respondJson(exchange, 202, "projects/123/zones/wrong-zone-b"));
    assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("europe-west1");

    // 2. Locations gRPC API fallback when both region and zone return non-200
    listLocationsHandler =
        req ->
            ApiFutures.immediateFuture(
                ListLocationsResponse.newBuilder()
                    .addLocations(Location.newBuilder().setLocationId("asia-east1").build())
                    .build());
    CloudTasksClientWrapper.setLocationForTesting(null);
    assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("asia-east1");
    assertThat(listLocationsRequests).hasSize(1);
    assertThat(listLocationsRequests.get(0).getName()).isEqualTo("projects/test-proj");

    // Verify failure from listLocationsCallable falls back to us-central1
    listLocationsHandler =
        req -> ApiFutures.immediateFailedFuture(new RuntimeException("UNAVAILABLE"));
    CloudTasksClientWrapper.setLocationForTesting(null);
    assertThat(CloudTasksClientWrapper.getLocation()).isEqualTo("us-central1");

    // 3. Metadata server & ADC token fallbacks when AppIdentityService environment is absent
    Environment savedEnv = ApiProxy.getCurrentEnvironment();
    CloudTasksClientWrapper.setProjectIdForTesting(null);
    assertThat(CloudTasksClientWrapper.getProjectId()).isEqualTo("test");
    CloudTasksClientWrapper.setProjectIdForTesting(TEST_PROJECT);

    CloudTasksClientWrapper.runAsyncWithEnv(() -> ApiProxy.setEnvironmentForCurrentThread(null))
        .get();
    assertThat(CloudTasksClientWrapper.supplyAsyncWithEnv(ApiProxy::getCurrentEnvironment).get())
        .isSameInstanceAs(savedEnv);
    AtomicReference<Environment> runEnvRef = new AtomicReference<>();
    CloudTasksClientWrapper.runAsyncWithEnv(() -> runEnvRef.set(ApiProxy.getCurrentEnvironment()))
        .get();
    assertThat(runEnvRef.get()).isSameInstanceAs(savedEnv);

    ApiProxy.setEnvironmentForCurrentThread(null);
    try {
      server.createContext(
          "/computeMetadata/v1/instance/service-accounts/default/token",
          exchange -> {
            if (!Objects.equals(
                exchange.getRequestHeaders().getFirst("Metadata-Flavor"), "Google")) {
              respondJson(exchange, 403, "");
              return;
            }
            respondJson(exchange, 200, "{\"access_token\":\"meta-token-xyz\",\"expires_in\":1800}");
          });
      CloudTasksClientWrapper.setAccessTokenForTesting(null, 0);
      assertThat(CloudTasksClientWrapper.getValidAccessToken()).isEqualTo("meta-token-xyz");

      server.removeContext("/computeMetadata/v1/instance/service-accounts/default/token");
      server.createContext(
          "/computeMetadata/v1/instance/service-accounts/default/token",
          exchange ->
              respondJson(exchange, 202, "{\"access_token\":\"wrong-token\",\"expires_in\":1800}"));
      CloudTasksClientWrapper.setAccessTokenForTesting(null, 0);
      CloudTasksClientWrapper.setCredentialsTokenForTesting("adc-token-789");
      assertThat(CloudTasksClientWrapper.getValidAccessToken()).isEqualTo("adc-token-789");
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(savedEnv);
    }
  }

  @Test
  public void testFetchStatistics_usesRateLimitsWhenStatsUnpopulated() {
    Queue queue = QueueFactory.getDefaultQueue();
    QueueStatistics stats = queue.fetchStatistics();
    assertThat(stats.getNumTasks()).isEqualTo(0);
    assertThat(stats.getOldestEtaUsec()).isNull();
    assertThat(stats.getExecutedLastMinute()).isEqualTo(0L);
    assertThat(stats.getRequestsInFlight()).isEqualTo(0);
    assertThat(stats.getEnforcedRate()).isWithin(0.001).of(12.5);
    assertThat(getQueueRequests).hasSize(1);
    assertThat(getQueueRequests.get(0).getName())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default");
    assertThat(getQueueRequests.get(0).getReadMask().getPathsList())
        .containsExactly("stats", "rate_limits");
  }

  @Test
  public void testSweep_sortsByCreatedAscendingSoOldTasksAreNotStarved() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant now = Instant.now();

    Entity recent = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    recent.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    recent.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "recent-within-grace");
    recent.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now));
    recent.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key recentKey = ds.put(recent);

    Entity old = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    old.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    old.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "old-orphaned");
    old.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(now.minusSeconds(120)));
    old.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key oldKey = ds.put(old);

    int swept = TaskProcessor.sweep();
    assertThat(swept).isEqualTo(1);
    assertThrows(EntityNotFoundException.class, () -> ds.get(oldKey));
    assertThat(ds.get(Arrays.asList(recentKey))).containsKey(recentKey);
  }

  @Test
  public void testBoundedIoExecutor_hasCappedPoolSizeAndCoreThreadTimeout() {
    assertThat(CloudTasksClientWrapper.IO_EXECUTOR).isInstanceOf(ThreadPoolExecutor.class);
    ThreadPoolExecutor pool = (ThreadPoolExecutor) CloudTasksClientWrapper.IO_EXECUTOR;
    assertThat(pool.getMaximumPoolSize()).isEqualTo(CloudTasksClientWrapper.IO_EXECUTOR_MAX_THREADS);
    assertThat(pool.allowsCoreThreadTimeOut()).isTrue();
  }

  @Test
  public void testTaskProcessor_doesNotOverwriteHijackedLockOnFailure() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Environment testEnv = ApiProxy.getCurrentEnvironment();
    Entity pending = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    pending.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    pending.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "hijacked-task");
    pending.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/hijacked-task\","
            + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}");
    pending.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(Instant.now().minusSeconds(120)));
    pending.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    Key key = ds.put(pending);

    Date hijackedLockDate = Date.from(Instant.now().plusSeconds(300));
    createTaskHandler =
        req -> {
          ApiProxy.setEnvironmentForCurrentThread(testEnv);
          try {
            // Simulate a second worker acquiring a new lock while the first RPC is in flight
            Entity hijacked = ds.get(key);
            hijacked.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PROCESSING);
            hijacked.setProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES, hijackedLockDate);
            ds.put(hijacked);
          } catch (EntityNotFoundException e) {
            throw new RuntimeException(e);
          }
          return ApiFutures.immediateFailedFuture(new RuntimeException("INTERNAL"));
        };

    TaskProcessor.processPendingTaskKeys(Collections.singletonList(key), false);

    Entity after = ds.get(key);
    assertThat(after.getProperty(TaskProcessor.PROPERTY_STATUS))
        .isEqualTo(TaskProcessor.STATUS_PROCESSING);
    assertThat(((Date) after.getProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES)).toInstant())
        .isEqualTo(hijackedLockDate.toInstant());
    assertThat(after.getProperty(TaskProcessor.PROPERTY_RETRY_COUNT)).isNull();
  }

  @Test
  public void testBatchOperations_propagateTopLevelErrorWhenSubRequestsUnaccounted() {
    batchDeleteHandler =
        req -> {
          BatchDeleteTasksMetadata metadata =
              BatchDeleteTasksMetadata.newBuilder()
                  .putFailedRequests(
                      0,
                      Status.newBuilder()
                          .setCode(5)
                          .setMessage("Requested entity was not found.")
                          .build())
                  .build();
          return ApiFutures.immediateFuture(
              Operation.newBuilder()
                  .setDone(true)
                  .setError(
                      Status.newBuilder()
                          .setCode(13)
                          .setMessage("INTERNAL: batch aborted after first item")
                          .build())
                  .setMetadata(Any.pack(metadata))
                  .build());
        };

    Queue queue = QueueFactory.getDefaultQueue();
    List<TaskHandle> handles =
        Arrays.asList(
            new TaskHandle(TaskOptions.Builder.withTaskName("del-a"), "default"),
            new TaskHandle(TaskOptions.Builder.withTaskName("del-b"), "default"));
    IllegalStateException deleteEx =
        assertThrows(IllegalStateException.class, () -> queue.deleteTask(handles));
    assertThat(deleteEx).hasMessageThat().contains("INTERNAL");

    batchCreateHandler =
        req -> {
          BatchCreateTasksMetadata metadata =
              BatchCreateTasksMetadata.newBuilder()
                  .putFailedRequests(
                      0,
                      Status.newBuilder()
                          .setCode(6)
                          .setMessage("ALREADY_EXISTS")
                          .build())
                  .build();
          return ApiFutures.immediateFuture(
              Operation.newBuilder()
                  .setDone(true)
                  .setError(
                      Status.newBuilder()
                          .setCode(7)
                          .setMessage("PERMISSION_DENIED")
                          .build())
                  .setMetadata(Any.pack(metadata))
                  .build());
        };
    List<TaskOptions> addBatch =
        Arrays.asList(TaskOptions.Builder.withUrl("/w1"), TaskOptions.Builder.withUrl("/w2"));
    assertThrows(SecurityException.class, () -> queue.add(addBatch));
  }

  @Test
  public void testSweep_terminalFailedTasksDoNotStarvePendingTasks() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    List<Entity> failed = new ArrayList<>();
    for (int i = 0; i < TaskProcessor.SWEEP_BATCH_LIMIT; i++) {
      Entity dead = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
      dead.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
      dead.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "dead-" + i);
      dead.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(Instant.now().minusSeconds(600)));
      dead.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_FAILED);
      dead.setProperty(TaskProcessor.PROPERTY_RETRY_COUNT, TaskProcessor.MAX_RETRY_COUNT);
      failed.add(dead);
    }
    ds.put(failed);
    Entity pending = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    pending.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    pending.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "live-task");
    pending.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/live-task\","
            + "\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/live\"}}");
    pending.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(Instant.now().minusSeconds(120)));
    pending.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    ds.put(pending);

    assertThat(TaskProcessor.sweep()).isEqualTo(1);
    assertThat(createTaskRequests).hasSize(1);
    assertThat(createTaskRequests.get(0).getTask().getName())
        .isEqualTo("projects/test-proj/locations/us-central1/queues/default/tasks/live-task");
  }

  private static Entity newPendingTaskEntity(
      String batchId, String taskName, String status, Instant created, Date lockExpires) {
    Key parent =
        KeyFactory.createKey(CloudTasksClientWrapper.ENTITY_KIND_PENDING_TASK_BATCH, batchId);
    Entity e = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK, parent);
    e.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    e.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, taskName);
    e.setProperty(
        TaskProcessor.PROPERTY_CLOUD_TASK_PAYLOAD,
        "{\"name\":\"projects/test-proj/locations/us-central1/queues/default/tasks/"
            + taskName
            + "\",\"appEngineHttpRequest\":{\"httpMethod\":\"POST\",\"relativeUri\":\"/w\"}}");
    e.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(created));
    e.setProperty(TaskProcessor.PROPERTY_STATUS, status);
    e.setProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES, lockExpires);
    e.setProperty(TaskProcessor.PROPERTY_RETRY_COUNT, 0L);
    return e;
  }

  private static String batchIdAt(Instant created) {
    return String.format(Locale.ROOT, "z%019d-%s", created.toEpochMilli(), UUID.randomUUID());
  }

  private static String taskNameOf(Key key, DatastoreService ds) throws EntityNotFoundException {
    return (String) ds.get(key).getProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME);
  }

  private List<String> dispatchedTaskNames() {
    List<String> names = new ArrayList<>();
    for (CreateTaskRequest req : createTaskRequests) {
      String full = req.getTask().getName();
      names.add(full.substring(full.lastIndexOf('/') + 1));
    }
    return names;
  }

  @Test
  public void testSweep_sweepsOldestBatchFirstWithoutCompositeIndex() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    // Insert in reverse chronological order so insertion order cannot mask key ordering.
    Instant base = Instant.now().minusSeconds(3600);
    List<Entity> backlog = new ArrayList<>();
    int total = TaskProcessor.SWEEP_BATCH_LIMIT + 50;
    for (int i = total - 1; i >= 0; i--) {
      Instant created = base.plusMillis(i);
      backlog.add(
          newPendingTaskEntity(
              batchIdAt(created), "pending-" + i, TaskProcessor.STATUS_PENDING, created, null));
    }
    ds.put(backlog);

    // Selection is strictly oldest-first and capped at SWEEP_BATCH_LIMIT.
    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(TaskProcessor.SWEEP_BATCH_LIMIT);
    for (int i = 0; i < selected.size(); i++) {
      assertThat(taskNameOf(selected.get(i), ds)).isEqualTo("pending-" + i);
    }

    // End to end: exactly the oldest SWEEP_BATCH_LIMIT tasks are dispatched (concurrently).
    assertThat(TaskProcessor.sweep()).isEqualTo(TaskProcessor.SWEEP_BATCH_LIMIT);
    List<String> dispatched = dispatchedTaskNames();
    assertThat(dispatched).hasSize(TaskProcessor.SWEEP_BATCH_LIMIT);
    assertThat(dispatched).contains("pending-0");
    assertThat(dispatched).contains("pending-" + (TaskProcessor.SWEEP_BATCH_LIMIT - 1));
    assertThat(dispatched).doesNotContain("pending-" + TaskProcessor.SWEEP_BATCH_LIMIT);
    assertThat(dispatched).doesNotContain("pending-" + (total - 1));
    assertThat(ds.getIndexes()).isEmpty();
  }

  @Test
  public void testSweep_legacyAndTimestampedPendingMergedByCreated() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant old = Instant.now().minusSeconds(600);
    // Legacy bare-UUID key ("f..." is the largest legacy prefix) created AFTER the timestamped
    // task: __key__ order would put legacy first, but created order must win.
    ds.put(
        newPendingTaskEntity(
            "ffffffff-ffff-4fff-8fff-ffffffffffff",
            "legacy-newer",
            TaskProcessor.STATUS_PENDING,
            old,
            null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old.minusSeconds(60)),
            "timestamped-older",
            TaskProcessor.STATUS_PENDING,
            old.minusSeconds(60),
            null));
    // Legacy key created before both.
    ds.put(
        newPendingTaskEntity(
            "00000000-0000-4000-8000-000000000000",
            "legacy-oldest",
            TaskProcessor.STATUS_PENDING,
            old.minusSeconds(120),
            null));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    List<String> names = new ArrayList<>();
    for (Key k : selected) {
      names.add(taskNameOf(k, ds));
    }
    assertThat(names).containsExactly("legacy-oldest", "timestamped-older", "legacy-newer")
        .inOrder();
    assertThat(ds.getIndexes()).isEmpty();
  }

  @Test
  public void testSweep_expiredProcessingTaskNotStarvedByPendingBacklog() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant base = Instant.now().minusSeconds(3600);
    List<Entity> backlog = new ArrayList<>();
    for (int i = 0; i < TaskProcessor.SWEEP_BATCH_LIMIT; i++) {
      Instant created = base.plusMillis(i);
      backlog.add(
          newPendingTaskEntity(
              batchIdAt(created), "pending-" + i, TaskProcessor.STATUS_PENDING, created, null));
    }
    ds.put(backlog);
    // A crashed dispatch: PROCESSING with an expired lock, created after every PENDING task so it
    // would sort last if PENDING and PROCESSING shared one SWEEP_BATCH_LIMIT window.
    Instant crashedAt = base.plusSeconds(10);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(crashedAt),
            "crashed",
            TaskProcessor.STATUS_PROCESSING,
            crashedAt,
            Date.from(Instant.now().minusSeconds(120))));
    // An actively locked PROCESSING task must be left alone.
    ds.put(
        newPendingTaskEntity(
            batchIdAt(crashedAt),
            "in-flight",
            TaskProcessor.STATUS_PROCESSING,
            crashedAt,
            Date.from(Instant.now().plusSeconds(30))));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(TaskProcessor.SWEEP_BATCH_LIMIT);
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("crashed");
    assertThat(taskNameOf(selected.get(1), ds)).isEqualTo("pending-0");

    assertThat(TaskProcessor.sweep()).isEqualTo(TaskProcessor.SWEEP_BATCH_LIMIT);
    List<String> dispatched = dispatchedTaskNames();
    assertThat(dispatched).contains("crashed");
    assertThat(dispatched).contains("pending-0");
    assertThat(dispatched).doesNotContain("in-flight");
    assertThat(dispatched).doesNotContain("pending-" + (TaskProcessor.SWEEP_BATCH_LIMIT - 1));
  }

  @Test
  public void testSweep_crashedProcessingTaskNotMaskedByOlderActivelyLockedTasks() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant base = Instant.now().minusSeconds(3600);
    Date activeLock = Date.from(Instant.now().plusSeconds(45));
    // More than SWEEP_SCAN_LIMIT older tasks are mid-dispatch with valid locks. Expired locks are
    // found by a lock_expires range query, so no number of actively locked tasks can mask them.
    int activeCount = TaskProcessor.SWEEP_SCAN_LIMIT + 1;
    List<Entity> batch = new ArrayList<>();
    for (int i = 0; i < activeCount; i++) {
      Instant created = base.plusMillis(i);
      batch.add(
          newPendingTaskEntity(
              batchIdAt(created),
              "active-" + i,
              TaskProcessor.STATUS_PROCESSING,
              created,
              activeLock));
      if (batch.size() == 500) {
        ds.put(batch);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      ds.put(batch);
    }
    Instant crashedAt = base.plusSeconds(10);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(crashedAt),
            "crashed",
            TaskProcessor.STATUS_PROCESSING,
            crashedAt,
            Date.from(Instant.now().minusSeconds(120))));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(1);
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("crashed");
    assertThat(ds.getIndexes()).isEmpty();

    assertThat(TaskProcessor.sweep()).isEqualTo(1);
    assertThat(dispatchedTaskNames()).containsExactly("crashed");
  }

  @Test
  public void testSweep_expiredLocksSelectedOldestLockFirstAndCappedAtBatchLimit() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant base = Instant.now().minusSeconds(3600);
    int crashedCount = TaskProcessor.SWEEP_BATCH_LIMIT + 10;
    List<Entity> batch = new ArrayList<>();
    // Newer keys get older lock expiries so lock order and key order disagree.
    for (int i = 0; i < crashedCount; i++) {
      Instant created = base.plusMillis(i);
      Date lockExpires = Date.from(Instant.now().minusSeconds(1000 - i));
      batch.add(
          newPendingTaskEntity(
              batchIdAt(created),
              "crashed-" + i,
              TaskProcessor.STATUS_PROCESSING,
              created,
              lockExpires));
      if (batch.size() == 500) {
        ds.put(batch);
        batch.clear();
      }
    }
    ds.put(batch);
    // A PENDING task past the grace period must not be selected while the budget is full.
    ds.put(
        newPendingTaskEntity(
            batchIdAt(base), "pending-old", TaskProcessor.STATUS_PENDING, base, null));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(TaskProcessor.SWEEP_BATCH_LIMIT);
    // Oldest lock first: crashed-0 has the earliest lock_expires.
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("crashed-0");
    assertThat(taskNameOf(selected.get(selected.size() - 1), ds))
        .isEqualTo("crashed-" + (TaskProcessor.SWEEP_BATCH_LIMIT - 1));
  }

  @Test
  public void testSweep_actionableTaskNotMaskedByYoungLegacyKeyedTasks() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    // Rolling deployment: older instances keep writing bare-UUID batch keys, which sort before
    // every "z"-prefixed key. More than SWEEP_SCAN_LIMIT of them are inside the grace period.
    // Timestamped PENDING tasks are read by a __key__ range that starts above all legacy keys,
    // so no amount of legacy traffic can mask them.
    Instant young = Instant.now().minusSeconds(5);
    List<Entity> batch = new ArrayList<>();
    for (int i = 0; i < TaskProcessor.SWEEP_SCAN_LIMIT + 1; i++) {
      batch.add(
          newPendingTaskEntity(
              UUID.randomUUID().toString(),
              "legacy-young-" + i,
              TaskProcessor.STATUS_PENDING,
              young,
              null));
      if (batch.size() == 500) {
        ds.put(batch);
        batch.clear();
      }
    }
    ds.put(batch);
    Instant old = Instant.now().minusSeconds(600);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "timestamped-old", TaskProcessor.STATUS_PENDING, old, null));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(1);
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("timestamped-old");
  }

  @Test
  public void testSweep_timestampedKeyRangeExcludesInGraceTasksAndIncludesCutoff()
      throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant nowInstant = Instant.now();
    long now = nowInstant.toEpochMilli();
    long cutoff = now - TaskProcessor.REALTIME_DISPATCH_GRACE_PERIOD_MILLIS;
    // Exactly at the cutoff: eligible. One millisecond after: not eligible.
    ds.put(
        newPendingTaskEntity(
            batchIdAt(Instant.ofEpochMilli(cutoff)),
            "at-cutoff",
            TaskProcessor.STATUS_PENDING,
            Instant.ofEpochMilli(cutoff),
            null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(Instant.ofEpochMilli(cutoff + 1)),
            "after-cutoff",
            TaskProcessor.STATUS_PENDING,
            Instant.ofEpochMilli(cutoff + 1),
            null));
    // Same batch millis as the cutoff but FAILED: must be ignored.
    ds.put(
        newPendingTaskEntity(
            batchIdAt(Instant.ofEpochMilli(cutoff)),
            "failed",
            TaskProcessor.STATUS_FAILED,
            Instant.ofEpochMilli(cutoff),
            null));
    // Corrupt data: a terminal FAILED entity that still carries an expired lock. The lock range
    // query returns it, but it must not be dispatched.
    ds.put(
        newPendingTaskEntity(
            batchIdAt(Instant.ofEpochMilli(cutoff)),
            "failed-stale-lock",
            TaskProcessor.STATUS_FAILED,
            Instant.ofEpochMilli(cutoff),
            Date.from(Instant.ofEpochMilli(now - 120_000))));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, nowInstant);
    assertThat(selected).hasSize(1);
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("at-cutoff");
  }

  @Test
  public void testSweep_staleLocksOnTerminalTasksAreClearedAndCannotCrowdOutCrashedTasks()
      throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant old = Instant.now().minusSeconds(3600);
    // SWEEP_BATCH_LIMIT FAILED entities that (corruptly) still carry locks that expired long ago,
    // so they sort ahead of every real expired lock.
    List<Entity> poison = new ArrayList<>();
    List<Key> poisonKeys = new ArrayList<>();
    for (int i = 0; i < TaskProcessor.SWEEP_BATCH_LIMIT; i++) {
      Entity e =
          newPendingTaskEntity(
              batchIdAt(old.plusMillis(i)),
              "poison-" + i,
              TaskProcessor.STATUS_FAILED,
              old,
              Date.from(Instant.now().minusSeconds(7200 - i)));
      poison.add(e);
    }
    poisonKeys.addAll(ds.put(poison));
    Instant crashedAt = old.plusSeconds(10);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(crashedAt),
            "crashed",
            TaskProcessor.STATUS_PROCESSING,
            crashedAt,
            Date.from(Instant.now().minusSeconds(120))));

    // First sweep: the poison entities fill the expired-lock scan, but get healed.
    List<Key> first = TaskProcessor.findSweepCandidates(ds, Instant.now());
    for (Key k : first) {
      assertThat(taskNameOf(k, ds)).doesNotContainMatch("^poison-");
    }
    for (Key k : poisonKeys) {
      Entity healed = ds.get(k);
      assertThat(healed.getProperty(TaskProcessor.PROPERTY_LOCK_EXPIRES)).isNull();
      assertThat(healed.getProperty(TaskProcessor.PROPERTY_STATUS))
          .isEqualTo(TaskProcessor.STATUS_FAILED);
    }

    // Second sweep: nothing stands in front of the crashed task any more.
    List<Key> second = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(second).hasSize(1);
    assertThat(taskNameOf(second.get(0), ds)).isEqualTo("crashed");
    assertThat(ds.getIndexes()).isEmpty();
  }

  @Test
  public void testSweep_processingWithoutLockExpiresSweptAfterLockDuration() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant old = Instant.now().minus(TaskProcessor.LOCK_DURATION).minusSeconds(1);
    Instant recent = Instant.now().minusSeconds(5);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "unlocked-old", TaskProcessor.STATUS_PROCESSING, old, null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(recent), "unlocked-recent", TaskProcessor.STATUS_PROCESSING, recent, null));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(1);
    assertThat(taskNameOf(selected.get(0), ds)).isEqualTo("unlocked-old");
    assertThat(ds.getIndexes()).isEmpty();
  }

  @Test
  public void testSweep_processingTasksFillBudgetBeforePendingIsConsidered() throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant old = Instant.now().minusSeconds(3600);
    Date expiredLock = Date.from(Instant.now().minusSeconds(120));
    List<Entity> batch = new ArrayList<>();
    // SWEEP_BATCH_LIMIT - 1 expired locks, then two lock-less PROCESSING tasks older than
    // LOCK_DURATION: together they exceed the budget, so PENDING must not be queried at all.
    for (int i = 0; i < TaskProcessor.SWEEP_BATCH_LIMIT - 1; i++) {
      Instant created = old.plusMillis(i);
      batch.add(
          newPendingTaskEntity(
              batchIdAt(created),
              "expired-" + i,
              TaskProcessor.STATUS_PROCESSING,
              created,
              expiredLock));
    }
    ds.put(batch);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "unlocked-a", TaskProcessor.STATUS_PROCESSING, old, null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "unlocked-b", TaskProcessor.STATUS_PROCESSING, old, null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "pending-old", TaskProcessor.STATUS_PENDING, old, null));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    assertThat(selected).hasSize(TaskProcessor.SWEEP_BATCH_LIMIT);
    List<String> names = new ArrayList<>();
    for (Key k : selected) {
      names.add(taskNameOf(k, ds));
    }
    assertThat(names).doesNotContain("pending-old");
    assertThat(names).contains("expired-0");
    assertThat(names).contains("expired-" + (TaskProcessor.SWEEP_BATCH_LIMIT - 2));
    long unlockedSelected = names.stream().filter(n -> n.startsWith("unlocked-")).count();
    assertThat(unlockedSelected).isEqualTo(1);
  }

  @Test
  public void testSweep_inGraceTimestampedPendingTaskSkippedUntilGracePeriodElapses()
      throws Exception {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Instant old = Instant.now().minusSeconds(600);
    Instant young = Instant.now().minusSeconds(5);
    // A preview-SDK root entity (no parent key) must not NPE and must still be selected.
    Entity root = new Entity(TaskProcessor.ENTITY_KIND_PENDING_TASK);
    root.setProperty(TaskProcessor.PROPERTY_QUEUE_NAME, "default");
    root.setProperty(TaskProcessor.PROPERTY_CLOUD_TASK_NAME, "root-old");
    root.setProperty(TaskProcessor.PROPERTY_CREATED, Date.from(old));
    root.setProperty(TaskProcessor.PROPERTY_STATUS, TaskProcessor.STATUS_PENDING);
    ds.put(root);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(old), "timestamped-old", TaskProcessor.STATUS_PENDING, old, null));
    ds.put(
        newPendingTaskEntity(
            batchIdAt(young), "timestamped-young", TaskProcessor.STATUS_PENDING, young, null));
    // Keyed after the young PENDING task; must still be found because expired locks are queried
    // independently of the PENDING key range.
    Instant crashedAt = young.plusSeconds(1);
    ds.put(
        newPendingTaskEntity(
            batchIdAt(crashedAt),
            "crashed",
            TaskProcessor.STATUS_PROCESSING,
            crashedAt,
            Date.from(Instant.now().minusSeconds(120))));

    List<Key> selected = TaskProcessor.findSweepCandidates(ds, Instant.now());
    List<String> names = new ArrayList<>();
    for (Key k : selected) {
      names.add(taskNameOf(k, ds));
    }
    assertThat(names).containsExactly("crashed", "root-old", "timestamped-old").inOrder();

    // A later sweep, once the young task has aged past the grace period, picks it up.
    selected =
        TaskProcessor.findSweepCandidates(ds, Instant.now().plusMillis(TaskProcessor.REALTIME_DISPATCH_GRACE_PERIOD_MILLIS));
    names.clear();
    for (Key k : selected) {
      names.add(taskNameOf(k, ds));
    }
    assertThat(names).contains("timestamped-young");
  }

  @Test
  public void testTransactionalAdd_batchSharesSingleEntityGroup() throws Exception {
    createTaskHandler =
        req -> ApiFutures.immediateFailedFuture(new RuntimeException("UNAVAILABLE"));
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();
    List<TaskOptions> batch = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      batch.add(TaskOptions.Builder.withUrl("/eg-" + i).payload("p" + i));
    }

    Transaction txn = ds.beginTransaction();
    List<TaskHandle> handles = queue.add(txn, batch);
    txn.commit();
    assertThat(handles).hasSize(30);

    List<Entity> pending =
        ds.prepare(new Query(TaskProcessor.ENTITY_KIND_PENDING_TASK))
            .asList(FetchOptions.Builder.withDefaults());
    assertThat(pending).hasSize(30);
    Key parent = pending.get(0).getKey().getParent();
    assertThat(parent).isNotNull();
    assertThat(parent.getKind()).isEqualTo(CloudTasksClientWrapper.ENTITY_KIND_PENDING_TASK_BATCH);
    assertThat(parent.getName()).matches("z\\d{19}-[0-9a-f-]{36}");
    // The sweeper's __key__ range assumes the batch key embeds the entities' creation time.
    long keyMillis = Long.parseLong(parent.getName().substring(1, 20));
    for (Entity e : pending) {
      assertThat(e.getKey().getParent()).isEqualTo(parent);
      Object created = e.getProperty(TaskProcessor.PROPERTY_CREATED);
      assertThat(created).isInstanceOf(Date.class);
      assertThat(((Date) created).toInstant().toEpochMilli()).isEqualTo(keyMillis);
    }
  }

  @Test
  public void testTransactionalAdd_wrappedTransactionStillDispatchesPostCommit() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    createTaskHandler =
        req -> {
          latch.countDown();
          return ApiFutures.immediateFuture(
              Task.newBuilder().setName(req.getTask().getName()).build());
        };
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();

    Transaction wrapped = new DelegatingTransaction(ds.beginTransaction());
    TaskHandle unused = queue.add(wrapped, TaskOptions.Builder.withUrl("/wrapped"));
    wrapped.commit();

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void testTransactionalAddAsync_returnsPendingFutureAndWrapsDatastoreErrors() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Queue queue = QueueFactory.getDefaultQueue();
    Transaction txn = ds.beginTransaction();
    try {
      Future<TaskHandle> future = queue.addAsync(txn, TaskOptions.Builder.withUrl("/async"));
      assertThat(future).isNotInstanceOf(CompletableFuture.class);
      assertThat(QueueApiHelper.getInternal(future).getName()).startsWith("task-");
    } finally {
      txn.rollback();
    }

    IllegalArgumentException dsError = new IllegalArgumentException("too many entity groups");
    RuntimeException translated = CloudTasksClientWrapper.toTransactionalTaskException(dsError);
    assertThat(translated).isInstanceOf(TransactionalTaskException.class);
    assertThat(translated).hasCauseThat().isSameInstanceAs(dsError);
  }

  /**
   * Regression test for b/577037411: verifies that when {@code APPENGINE_USE_CLOUDTASK_PUSH_QUEUE}
   * is disabled, {@link QueueImpl} links, initializes, and checks {@code
   * isCloudTaskBackendEnabled()} without loading {@link CloudTasksClientWrapper} or any {@code
   * com.google.auth.*} / {@code com.google.cloud.tasks.*} classes.
   */
  @Test
  public void testQueueImpl_whenCloudTasksDisabled_doesNotLoadCloudTasksClientWrapperOrAuth()
      throws Exception {
    List<String> blockedLoadAttempts = new ArrayList<>();
    URL taskqueueJarUrl = QueueImpl.class.getProtectionDomain().getCodeSource().getLocation();
    ClassLoader parentLoader = QueueImpl.class.getClassLoader();
    try (URLClassLoader isolatedLoader =
        new URLClassLoader(new URL[] {taskqueueJarUrl}, parentLoader) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
              Class<?> loaded = findLoadedClass(name);
              if (loaded != null) {
                return loaded;
              }
              if (name.equals("com.google.appengine.api.taskqueue.CloudTasksClientWrapper")
                  || name.startsWith("com.google.auth.")
                  || name.startsWith("com.google.cloud.tasks.")
                  || name.startsWith("com.google.api.gax.")) {
                blockedLoadAttempts.add(name);
                throw new ClassNotFoundException("Simulated missing class: " + name);
              }
              if (name.startsWith("com.google.appengine.api.taskqueue.")) {
                Class<?> found = findClass(name);
                if (resolve) {
                  resolveClass(found);
                }
                return found;
              }
              return super.loadClass(name, resolve);
            }
          }
        }) {
      // 1. Load, link, and initialize QueueImpl in the isolated classloader.
      Class<?> isolatedQueueImplClass =
          Class.forName("com.google.appengine.api.taskqueue.QueueImpl", true, isolatedLoader);
      var isEnabledMethod = isolatedQueueImplClass.getDeclaredMethod("isCloudTaskBackendEnabled");
      isEnabledMethod.setAccessible(true);
      var setEnabledMethod =
          isolatedQueueImplClass.getDeclaredMethod("setCloudTaskEnabledForTesting", Boolean.class);
      setEnabledMethod.setAccessible(true);

      // 2. Verify default (when APPENGINE_USE_CLOUDTASK_PUSH_QUEUE is not set to "true" in the
      // environment) and explicit false override never load CloudTasksClientWrapper, even when
      // executing Queue.purge() (whose bytecode contains invokestatic CloudTasksClientWrapper).
      if (!Boolean.parseBoolean(
          System.getenv(QueueImpl.ENV_APPENGINE_USE_CLOUDTASK_PUSH_QUEUE))) {
        assertThat((Boolean) isEnabledMethod.invoke(null)).isFalse();
      }
      // ApiProxy.getDelegate() returns a raw ApiProxy.Delegate.
      @SuppressWarnings("unchecked")
      ApiProxy.Delegate<Environment> originalDelegate = ApiProxy.getDelegate();
      List<String> apiProxyCalls = new ArrayList<>();
      try {
        ApiProxy.setDelegate(
            new ApiProxy.Delegate<Environment>() {
              @Override
              public byte[] makeSyncCall(
                  Environment environment, String packageName, String methodName, byte[] request) {
                apiProxyCalls.add(packageName + "." + methodName);
                if (Objects.equals(packageName, "taskqueue")
                    && Objects.equals(methodName, "PurgeQueue")) {
                  return new byte[0];
                }
                return originalDelegate.makeSyncCall(environment, packageName, methodName, request);
              }

              @Override
              public Future<byte[]> makeAsyncCall(
                  Environment environment,
                  String packageName,
                  String methodName,
                  byte[] request,
                  ApiProxy.ApiConfig apiConfig) {
                return originalDelegate.makeAsyncCall(
                    environment, packageName, methodName, request, apiConfig);
              }

              @Override
              public void log(Environment environment, ApiProxy.LogRecord record) {
                originalDelegate.log(environment, record);
              }

              @Override
              public void flushLogs(Environment environment) {
                originalDelegate.flushLogs(environment);
              }

              @Override
              public List<Thread> getRequestThreads(Environment environment) {
                return originalDelegate.getRequestThreads(environment);
              }
            });

        setEnabledMethod.invoke(null, false);
        assertThat((Boolean) isEnabledMethod.invoke(null)).isFalse();
        assertThat(blockedLoadAttempts).isEmpty();

        // 3. Instantiate QueueImpl in the isolated classloader, execute purge() while disabled
        // (proving JVM lazy symbolic resolution skips CloudTasksClientWrapper), and then verify
        // that enabling Cloud Tasks attempts to load CloudTasksClientWrapper.
        Class<?> isolatedHelperClass =
            Class.forName("com.google.appengine.api.taskqueue.QueueApiHelper", true, isolatedLoader);
        var helperCtor = isolatedHelperClass.getDeclaredConstructor();
        helperCtor.setAccessible(true);
        Object isolatedHelper = helperCtor.newInstance();
        var ctor =
            isolatedQueueImplClass.getDeclaredConstructor(String.class, isolatedHelperClass);
        ctor.setAccessible(true);
        Object isolatedQueue = ctor.newInstance("default", isolatedHelper);
        Class<?> isolatedQueueInterface =
            Class.forName("com.google.appengine.api.taskqueue.Queue", true, isolatedLoader);
        assertThat(isolatedQueueInterface.getMethod("getQueueName").invoke(isolatedQueue))
            .isEqualTo("default");

        var purgeMethod = isolatedQueueInterface.getMethod("purge");
        purgeMethod.invoke(isolatedQueue);
        assertThat(apiProxyCalls).containsExactly("taskqueue.PurgeQueue");
        assertThat(blockedLoadAttempts).isEmpty();

        setEnabledMethod.invoke(null, true);
        var error =
            assertThrows(
                InvocationTargetException.class,
                () -> purgeMethod.invoke(isolatedQueue));
        assertThat(error).hasCauseThat().isInstanceOf(NoClassDefFoundError.class);
        assertThat(error).hasCauseThat().hasMessageThat().contains("CloudTasksClientWrapper");
        assertThat(blockedLoadAttempts)
            .containsExactly("com.google.appengine.api.taskqueue.CloudTasksClientWrapper");
      } finally {
        ApiProxy.setDelegate(originalDelegate);
        setEnabledMethod.invoke(null, (Object) null);
      }
    }
  }

  /** Minimal framework-style wrapper around a native {@link Transaction}. */
  private static final class DelegatingTransaction implements Transaction {
    private final Transaction delegate;

    DelegatingTransaction(Transaction delegate) {
      this.delegate = delegate;
    }

    @Override
    public void commit() {
      delegate.commit();
    }

    @Override
    public Future<Void> commitAsync() {
      return delegate.commitAsync();
    }

    @Override
    public void rollback() {
      delegate.rollback();
    }

    @Override
    public Future<Void> rollbackAsync() {
      return delegate.rollbackAsync();
    }

    @Override
    public String getId() {
      return delegate.getId();
    }

    @Override
    public String getApp() {
      return delegate.getApp();
    }

    @Override
    public boolean isActive() {
      return delegate.isActive();
    }
  }

  private static void respondJson(HttpExchange exchange, int status, String json)
      throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
