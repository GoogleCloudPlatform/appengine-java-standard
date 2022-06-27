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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.appengine.api.taskqueue.DeferredTaskCreationException;
import com.google.appengine.api.taskqueue.IQueueFactory;
import com.google.appengine.api.taskqueue.IQueueFactoryProvider;
import com.google.appengine.api.taskqueue.InternalFailureException;
import com.google.appengine.api.taskqueue.InvalidQueueModeException;
import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueConstants;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.QueueFailureException;
import com.google.appengine.api.taskqueue.QueueNameMismatchException;
import com.google.appengine.api.taskqueue.QueueStatistics;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TransactionalTaskException;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.google.appengine.api.taskqueue.UnsupportedTranslationException;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.easymock.EasyMock;

/** Exhaustive usage of the Task Queue Api. Used for backward compatibility checks. */
public final class TaskQueueApiUsage {

  public static class DeferredTaskApiUsage extends ExhaustiveApiInterfaceUsage<DeferredTask> {

    @Override
    protected Set<Class<?>> useApi(DeferredTask task) {
      return classes(Runnable.class, Serializable.class);
    }
  }

  public static class DeferredTaskContextApiUsage extends ExhaustiveApiUsage<DeferredTaskContext> {

    String ___apiConstant_DEFAULT_DEFERRED_URL;
    String ___apiConstant_RUNNABLE_TASK_CONTENT_TYPE;

    @Override
    public Set<Class<?>> useApi() {
      Map<String, Object> attributes = new HashMap<>();
      Environment env = EasyMock.createMock(Environment.class);
      EasyMock.expect(env.getAttributes()).andReturn(attributes).anyTimes();
      EasyMock.replay(env);
      ApiProxy.setEnvironmentForCurrentThread(env);

      HttpServletRequest request = DeferredTaskContext.getCurrentRequest();
      HttpServletResponse response = DeferredTaskContext.getCurrentResponse();
      HttpServlet servlet = DeferredTaskContext.getCurrentServlet();
      DeferredTaskContext.setDoNotRetry(true);
      DeferredTaskContext.markForRetry();

      ___apiConstant_DEFAULT_DEFERRED_URL = DeferredTaskContext.DEFAULT_DEFERRED_URL;
      ___apiConstant_RUNNABLE_TASK_CONTENT_TYPE = DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE;
      return classes(Object.class);
    }
  }

  public static class DeferredTaskCreationExceptionApiUsage
      extends ExhaustiveApiUsage<DeferredTaskCreationException> {

    @Override
    public Set<Class<?>> useApi() {
      DeferredTaskCreationException ex = new DeferredTaskCreationException(new Exception());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class InternalFailureExceptionApiUsage
      extends ExhaustiveApiUsage<InternalFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      InternalFailureException ex = new InternalFailureException("yar");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class InvalidQueueModeExceptionApiUsage
      extends ExhaustiveApiUsage<InvalidQueueModeException> {

    @Override
    public Set<Class<?>> useApi() {
      InvalidQueueModeException ex = new InvalidQueueModeException("yar");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class UnsupportedTranslationExceptionApiUsage
      extends ExhaustiveApiUsage<UnsupportedTranslationException> {

    @Override
    public Set<Class<?>> useApi() {
      UnsupportedTranslationException ex = new UnsupportedTranslationException(
          "yar", new UnsupportedEncodingException());
      ex = new UnsupportedTranslationException(new UnsupportedEncodingException());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class QueueFailureExceptionApiUsage
      extends ExhaustiveApiUsage<QueueFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      QueueFailureException ex = new QueueFailureException("yar", new Throwable());
      ex = new QueueFailureException("detail");
      ex = new QueueFailureException(new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class TransactionalTaskExceptionApiUsage
      extends ExhaustiveApiUsage<TransactionalTaskException> {

    @Override
    public Set<Class<?>> useApi() {
      TransactionalTaskException ex = new TransactionalTaskException();
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class TransientFailureExceptionApiUsage
      extends ExhaustiveApiUsage<TransientFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      TransientFailureException ex = new TransientFailureException("boom");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class QueueNameMismatchExceptionApiUsage
      extends ExhaustiveApiUsage<QueueNameMismatchException> {

    @Override
    public Set<Class<?>> useApi() {
      QueueNameMismatchException ex = new QueueNameMismatchException("boom");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class TaskAlreadyExistsExceptionApiUsage
      extends ExhaustiveApiUsage<TaskAlreadyExistsException> {

    @Override
    public Set<Class<?>> useApi() {
      TaskAlreadyExistsException ex = new TaskAlreadyExistsException("boom");
      ex.getTaskNames();
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  public static class LeaseOptionsApiUsage extends ExhaustiveApiUsage<LeaseOptions> {

    @Override
    public Set<Class<?>> useApi() {
      LeaseOptions opts = LeaseOptions.Builder.withCountLimit(23);
      opts = new LeaseOptions(opts);
      opts = opts.countLimit(23);
      opts = opts.deadlineInSeconds(23d);
      boolean boolVal = opts.equals(opts);
      opts = opts.groupByTag();
      int intVal = opts.hashCode();
      opts = opts.leasePeriod(23, TimeUnit.MILLISECONDS);
      opts = opts.tag(new byte[1]);
      opts = opts.tag("mytag");
      String strVal = opts.toString();
      return classes(Object.class);
    }
  }

  public static class LeaseOptionsBuilderApiUsage extends ExhaustiveApiUsage<LeaseOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      LeaseOptions opts = LeaseOptions.Builder.withCountLimit(23);
      opts = LeaseOptions.Builder.withDeadlineInSeconds(23d);
      opts = LeaseOptions.Builder.withLeasePeriod(23, TimeUnit.MILLISECONDS);
      opts = LeaseOptions.Builder.withTag(new byte[1]);
      opts = LeaseOptions.Builder.withTag("mytag");
      return classes(Object.class);
    }
  }

  private static class MyDeferredTask implements DeferredTask {

    @Override
    public void run() {
    }
  }

  public static class TaskOptionsApiUsage extends ExhaustiveApiUsage<TaskOptions> {

    @Override
    public Set<Class<?>> useApi() {
      TaskOptions opts = TaskOptions.Builder.withDefaults();
      opts = new TaskOptions(opts);
      opts = opts.clearParams();
      opts = opts.countdownMillis(23);
      opts = opts.etaMillis(23);
      opts = opts.header("yam", "yar");
      opts = opts.headers(ImmutableMap.of("this", "that"));
      opts = opts.method(TaskOptions.Method.GET);
      opts = opts.param("this", "that");
      opts = opts.param("this", "bytes".getBytes(UTF_8));
      opts = opts.payload("payload".getBytes(UTF_8));
      opts = opts.payload("payload");
      opts = opts.payload("payload", UTF_8.name());
      opts = opts.payload("payload".getBytes(UTF_8), "content type");
      opts = opts.payload(new MyDeferredTask());
      opts = opts.tag("a tag");
      opts = opts.tag("a tag".getBytes(UTF_8));
      opts = opts.taskName("taskname");
      opts = opts.url("url");
      opts = opts.removeHeader("header name");
      opts = opts.removeParam("param name");
      opts = opts.retryOptions(RetryOptions.Builder.withDefaults());
      opts = opts.dispatchDeadline(Duration.ofSeconds(20));
      boolean boolVal = opts.equals(opts);
      Long longVal = opts.getEtaMillis();
      longVal = opts.getCountdownMillis();
      String stringVal = opts.getUrl();
      stringVal = opts.getTaskName();
      stringVal = opts.toString();
      try {
        stringVal = opts.getTag();
      } catch (UnsupportedEncodingException e) {
        // ok
      }
      Duration dispatchDeadline = opts.getDispatchDeadline();
      byte[] bytes = opts.getTagAsBytes();
      int intVal = opts.hashCode();
      byte[] payloadBytes = opts.getPayload();
      TaskOptions.Method methodVal = opts.getMethod();
      RetryOptions retryOpts = opts.getRetryOptions();
      Map<String, List<String>> headers = opts.getHeaders();
      Map<String, List<String>> stringParams = opts.getStringParams();
      Map<String, List<byte[]>> byteParams = opts.getByteArrayParams();
      return classes(Object.class, Serializable.class);
    }
  }

  public static class TaskOptionsBuilderApiUsage extends ExhaustiveApiUsage<TaskOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      TaskOptions opts = TaskOptions.Builder.withDefaults();
      opts = TaskOptions.Builder.withCountdownMillis(23);
      opts = TaskOptions.Builder.withEtaMillis(23);
      opts = TaskOptions.Builder.withHeader("header name", "header value");
      opts = TaskOptions.Builder.withHeaders(ImmutableMap.of("this", "that"));
      opts = TaskOptions.Builder.withMethod(TaskOptions.Method.GET);
      opts = TaskOptions.Builder.withParam("param name", "value");
      opts = TaskOptions.Builder.withParam("param name", "value".getBytes(UTF_8));
      opts = TaskOptions.Builder.withPayload(new MyDeferredTask());
      opts = TaskOptions.Builder.withPayload("payload");
      opts = TaskOptions.Builder.withPayload("payload", UTF_8.name());
      opts = TaskOptions.Builder.withPayload("payload".getBytes(UTF_8), "content type");
      opts = TaskOptions.Builder.withRetryOptions(RetryOptions.Builder.withDefaults());
      opts = TaskOptions.Builder.withTaskName("taskname");
      opts = TaskOptions.Builder.withUrl("url");
      opts = TaskOptions.Builder.withTag(new byte[1]);
      opts = TaskOptions.Builder.withTag("mytag");
      return classes(Object.class);
    }
  }

  public static class TaskOptionsMethodApiUsage extends ExhaustiveApiUsage<TaskOptions.Method> {

    @Override
    public Set<Class<?>> useApi() {
      TaskOptions.Method method = TaskOptions.Method.GET;
      method = TaskOptions.Method.DELETE;
      method = TaskOptions.Method.HEAD;
      method = TaskOptions.Method.POST;
      method = TaskOptions.Method.PULL;
      method = TaskOptions.Method.PUT;
      TaskOptions.Method[] values = TaskOptions.Method.values();
      method = TaskOptions.Method.valueOf("DELETE");
      return classes(Object.class, Enum.class, Serializable.class, Comparable.class);
    }
  }

  public static class RetryOptionsApiUsage extends ExhaustiveApiUsage<RetryOptions> {

    @Override
    public Set<Class<?>> useApi() {
      RetryOptions opts = RetryOptions.Builder.withDefaults();
      opts = new RetryOptions(opts);
      opts = opts.maxBackoffSeconds(23d);
      opts = opts.maxDoublings(2);
      opts = opts.minBackoffSeconds(23d);
      opts = opts.taskAgeLimitSeconds(23);
      opts = opts.taskRetryLimit(3);
      boolean boolVal = opts.equals(opts);
      int intVal = opts.hashCode();
      String strVal = opts.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  public static class RetryOptionsBuilderApiUsage extends ExhaustiveApiUsage<RetryOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      RetryOptions opts = RetryOptions.Builder.withDefaults();
      opts = RetryOptions.Builder.withMaxBackoffSeconds(23d);
      opts = RetryOptions.Builder.withMaxDoublings(2);
      opts = RetryOptions.Builder.withMinBackoffSeconds(23d);
      opts = RetryOptions.Builder.withTaskAgeLimitSeconds(20);
      opts = RetryOptions.Builder.withTaskRetryLimit(10);
      return classes(Object.class);
    }
  }

  public static class QueueFactoryApiUsage extends ExhaustiveApiUsage<QueueFactory> {

    @Override
    public Set<Class<?>> useApi() {
      Queue queue = QueueFactory.getDefaultQueue();
      queue = QueueFactory.getQueue("yar");
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IQueueFactory}.
   */
  public static class IQueueFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IQueueFactory> {

    @Override
    public Set<Class<?>> useApi(IQueueFactory iQueueFactory) {
      iQueueFactory.getQueue("yar");

      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IQueueFactoryProvider}.
   */
  public static class IQueueFactoryProviderUsage extends
  ExhaustiveApiUsage<IQueueFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IQueueFactoryProvider p = new IQueueFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }

  public static class QueueApiUsage extends ExhaustiveApiInterfaceUsage<Queue> {

    String ___apiConstant_DEFAULT_QUEUE;
    String ___apiConstant_DEFAULT_QUEUE_PATH;

    @Override
    protected Set<Class<?>> useApi(Queue queue) {
      TaskOptions opts = null;
      TaskHandle handle = queue.add(opts);
      handle = queue.add();
      Iterable<TaskOptions> optsIter = Lists.newArrayList(opts);
      List<TaskHandle> handleList = queue.add(optsIter);
      Transaction txn = null;
      handle = queue.add(txn, opts);
      handleList = queue.add(txn, optsIter);
      Future<TaskHandle> futureHandle = queue.addAsync();
      futureHandle = queue.addAsync(opts);
      futureHandle = queue.addAsync(txn, opts);
      Future<List<TaskHandle>> futureHandleList = queue.addAsync(optsIter);
      futureHandleList = queue.addAsync(txn, optsIter);
      boolean boolVal = queue.deleteTask(handle);
      List<Boolean> boolListVal = queue.deleteTask(handleList);
      boolVal = queue.deleteTask("taskName");
      Future<Boolean> futureBool = queue.deleteTaskAsync("taskName");
      futureBool = queue.deleteTaskAsync(handle);
      Future<List<Boolean>> futureBoolList = queue.deleteTaskAsync(handleList);
      QueueStatistics stats = queue.fetchStatistics();
      Future<QueueStatistics> futureStats = queue.fetchStatisticsAsync(null);
      futureStats = queue.fetchStatisticsAsync(8.0);
      String strVal = queue.getQueueName();
      handleList = queue.leaseTasks(LeaseOptions.Builder.withTag("tag"));
      handleList = queue.leaseTasks(23, TimeUnit.DAYS, 10);
      handleList = queue.leaseTasksByTag(23, TimeUnit.DAYS, 10, "tag");
      handleList = queue.leaseTasksByTagBytes(23, TimeUnit.DAYS, 10, "tag".getBytes(UTF_8));
      futureHandleList = queue.leaseTasksAsync(
          LeaseOptions.Builder.withTag("tag"));
      futureHandleList = queue.leaseTasksAsync(23, TimeUnit.DAYS, 10);
      futureHandleList = queue.leaseTasksByTagAsync(
          23, TimeUnit.DAYS, 10, "tag");
      futureHandleList =
          queue.leaseTasksByTagBytesAsync(23, TimeUnit.DAYS, 10, "tag".getBytes(UTF_8));
      handle = queue.modifyTaskLease(handle, 23, TimeUnit.DAYS);
      queue.purge();
      ___apiConstant_DEFAULT_QUEUE = Queue.DEFAULT_QUEUE;
      ___apiConstant_DEFAULT_QUEUE_PATH = Queue.DEFAULT_QUEUE_PATH;
      return classes();
    }
  }

  @SuppressWarnings("deprecation")
  public static class TaskHandleApiUsage extends ExhaustiveApiUsage<TaskHandle> {

    @Override
    public Set<Class<?>> useApi() {
      TaskHandle handle = new TaskHandle("name", "queue", 23);
      handle = new TaskHandle(
          TaskOptions.Builder.withTaskName("yar").payload("this=that").etaMillis(23), "queue");
      boolean boolVal = handle.equals(handle);
      try {
        List<Map.Entry<String, String>> entryListVal = handle.extractParams();
      } catch (UnsupportedEncodingException e) {
        // ok
      }
      long longVal = handle.getEtaMillis();
      String strVal = handle.getName();
      byte[] byteArray = handle.getPayload();
      strVal = handle.getQueueName();
      Integer integerVal = handle.getRetryCount();
      try {
        strVal = handle.getTag();
      } catch (UnsupportedEncodingException e) {
        // ok
      }
      byteArray = handle.getTagAsBytes();
      int intVal = handle.hashCode();
      strVal = handle.toString();
      return classes(Object.class, Serializable.class);
    }
  }

  public static class QueueStatisticsApiUsage extends ExhaustiveApiUsage<QueueStatistics> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalTaskQueueTestConfig());
      helper.setUp();
      try {
        QueueStatistics stats = QueueFactory.getDefaultQueue().fetchStatistics();
        double doubleVal = stats.getEnforcedRate();
        long longVal = stats.getExecutedLastMinute();
        int intVal = stats.getNumTasks();
        Long longObjVal = stats.getOldestEtaUsec();
        String strVal = stats.getQueueName();
        intVal = stats.getRequestsInFlight();
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  @SuppressWarnings("deprecation")
  public static class QueueConstantsApiUsage extends ExhaustiveApiUsage<QueueConstants> {

    Pattern ___apiConstant_QUEUE_NAME_PATTERN;
    String ___apiConstant_QUEUE_NAME_REGEX;
    Pattern ___apiConstant_TASK_NAME_PATTERN;
    String ___apiConstant_TASK_NAME_REGEX;

    @Override
    public Set<Class<?>> useApi() {
      long longVal = QueueConstants.getMaxEtaDeltaMillis();
      longVal = QueueConstants.maxLease(TimeUnit.DAYS);
      longVal = QueueConstants.maxLeaseCount();
      int intVal = QueueConstants.maxPullTaskSizeBytes();
      intVal = QueueConstants.maxPushTaskSizeBytes();
      intVal = QueueConstants.maxQueueNameLength();
      intVal = QueueConstants.maxTaskNameLength();
      intVal = QueueConstants.maxTaskSizeBytes();
      intVal = QueueConstants.maxTasksPerAdd();
      intVal = QueueConstants.maxTaskTagLength();
      intVal = QueueConstants.maxUrlLength();
      ___apiConstant_QUEUE_NAME_PATTERN = QueueConstants.QUEUE_NAME_PATTERN;
      ___apiConstant_QUEUE_NAME_REGEX = QueueConstants.QUEUE_NAME_REGEX;
      ___apiConstant_TASK_NAME_PATTERN = QueueConstants.TASK_NAME_PATTERN;
      ___apiConstant_TASK_NAME_REGEX = QueueConstants.TASK_NAME_REGEX;
      return classes(Object.class);
    }
  }
}
