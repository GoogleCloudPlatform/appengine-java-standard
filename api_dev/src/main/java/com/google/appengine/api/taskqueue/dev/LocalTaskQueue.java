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

import com.google.appengine.api.taskqueue.InternalFailureException;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueConstants;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueModifyTaskLeaseResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.api.urlfetch.dev.LocalURLFetchService;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.ApiUtils;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LatencyPercentiles;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.ConfigurationException;
import com.google.apphosting.utils.config.QueueXml;
import com.google.apphosting.utils.config.QueueXmlReader;
import com.google.apphosting.utils.config.QueueYamlReader;
import com.google.auto.service.AutoService;
import com.google.protobuf.ByteString;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

/**
 * A local implementation of the Task Queue service interface backed by Quartz
 * (http://www.opensymphony.com/quartz). This class is responsible for managing the lifecycle of the
 * Quartz {@link Scheduler} but otherwise delegates to {@link DevQueue} for all the scheduling
 * intelligence.
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalTaskQueue extends AbstractLocalRpcService {
  private static final Logger logger = Logger.getLogger(LocalTaskQueue.class.getName());
  private static final String DOC_LINK =
      "https://cloud.google.com/appengine/docs/standard/java/config/queueref-yaml";

  /** The package name for this service. */
  public static final String PACKAGE = "taskqueue";

  /**
   * The name of a property that disables automatic task execution. If this property exists and is
   * set to true in the {@code properties} argument to {@link #init} then the schedule will not
   * automatically run any tasks. Manual task execution will still work as normal.
   */
  public static final String DISABLE_AUTO_TASK_EXEC_PROP = "task_queue.disable_auto_task_execution";

  /**
   * Overrides the path of queue.xml. Must be a full path, e.g. /usr/local/dev/myapp/tests/queue.xml
   */
  public static final String QUEUE_XML_PATH_PROP = "task_queue.queue_xml_path";

  /**
   * Overrides the path of queue.yaml. Must be a full path, e.g.
   * /usr/local/dev/myapp/tests/queue.yaml
   */
  public static final String QUEUE_YAML_PATH_PROP = "task_queue.queue_yaml_path";

  /**
   * Overrides the {@link LocalTaskQueueCallback} class that is used to service async task
   * execution. The value of this property must be the fully-qualified name of a class with a
   * public, no-arg constructor that implements the {@link LocalTaskQueueCallback} interface.
   */
  public static final String CALLBACK_CLASS_PROP = "task_queue.callback_class";

  /** Collection of queues mapped by queue name, sorted by queue name. */
  private final Map<String, DevQueue> queues =
      // Using a TreeMap to get deterministic ordering.
      Collections.synchronizedMap(new TreeMap<String, DevQueue>());

  private QueueXml queueConfig;
  private Scheduler scheduler;
  private boolean disableAutoTaskExecution = false;
  private LocalServerEnvironment localServerEnvironment;
  private Clock clock;
  private LocalURLFetchService fetchService;
  private LocalTaskQueueCallback callback;
  /** Shutdown hook to stop this task queue when the VM exits. */
  private Thread shutdownHook;

  private Random rng;

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    localServerEnvironment = context.getLocalServerEnvironment();
    clock = context.getClock();
    queueConfig =
        parseQueueConfiguration(
            localServerEnvironment.getAppDir().getPath(),
            properties.get(QUEUE_XML_PATH_PROP),
            properties.get(QUEUE_YAML_PATH_PROP));

    logger.log(Level.INFO, "LocalTaskQueue is initialized");
    if (Boolean.parseBoolean(properties.get(DISABLE_AUTO_TASK_EXEC_PROP))) {
      disableAutoTaskExecution = true;
      logger.log(Level.INFO, "Automatic task execution is disabled.");
    }

    fetchService = new LocalURLFetchService();
    fetchService.init(null, new HashMap<String, String>());
    // We're only hitting urls of our own app.  The app gets 10 minutes
    // to process the request so set the url fetch deadline to 10 minutes.
    fetchService.setTimeoutInMs(10 * 60 * 1000);

    rng = new Random();

    initializeCallback(properties);
  }

  /**
   * Parse the queue configuration from application directory, either from xml or yaml configuration
   *
   * @param appDir the application war directory.
   * @param queueXmlPath a user provided path that overrides the default path of queue.xml. This is
   *     used by local unit tests.
   * @param queueYamlPath a user provided path that overrides the default path of queue.yaml. This
   *     is used by local unit tests.
   * @return the parsed queue configuration.
   * @throws ConfigurationException if both yaml and xml path are not null.
   */
  QueueXml parseQueueConfiguration(
      String appDir, @Nullable final String queueXmlPath, @Nullable final String queueYamlPath) {
    if (queueXmlPath != null && queueYamlPath != null) {
      throw new ConfigurationException(
          "Found both queue.xml and queue.yaml. Please use queue.yaml and remove queue.xml. "
              + "For more information: "
              + DOC_LINK);
    }
    QueueXml resultFromXml = null;
    QueueXml resultFromYaml = null;
    if (queueXmlPath != null) {
      // user wants a custom queue.xml loaded
      QueueXmlReader xmlReader =
          new QueueXmlReader(appDir) {
            @Override
            public String getFilename() {
              return queueXmlPath;
            }
          };
      resultFromXml = xmlReader.readQueueXml();
    } else if (queueYamlPath != null) {
      // user wants a custom queue.yaml loaded
      QueueYamlReader yamlReader =
          new QueueYamlReader(appDir) {
            @Override
            public String getFilename() {
              return queueYamlPath;
            }
          };
      resultFromYaml = yamlReader.parse();
    } else { // Tries default path for xml or yaml configuration.
      QueueXmlReader xmlReader = new QueueXmlReader(appDir);
      // TODO Consider reloading queue.xml if changed.
      // resultFromXml would be null iff the xml file does not exist.
      resultFromXml = xmlReader.readQueueXml();
      QueueYamlReader yamlReader = new QueueYamlReader(Paths.get(appDir, "WEB-INF").toString());
      resultFromYaml = yamlReader.parse();
    }

    if (!ApiUtils.isPromotingYaml()) {
      return (resultFromXml != null) ? resultFromXml : resultFromYaml;
    }

    // When promoting yaml, given proper messages if queue.xml is present.
    if (resultFromXml != null && resultFromYaml == null) {
      logger.warning(
          "Using queue.xml. Please migrate to queue.yaml. For more information: " + DOC_LINK);
    }
    return (resultFromYaml != null) ? resultFromYaml : resultFromXml;
  }

  private void initializeCallback(Map<String, String> properties) {
    String callbackOverrideClass = properties.get(CALLBACK_CLASS_PROP);
    if (callbackOverrideClass != null) {
      // user provided an override for the callback class
      try {
        callback = (LocalTaskQueueCallback) newInstance(Class.forName(callbackOverrideClass));
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    } else {
      // no override so use the default implementation
      callback = new UrlFetchServiceLocalTaskQueueCallback(fetchService);
    }
    callback.initialize(properties);
  }

  /**
   * Returns a new instance of the provided class, if the provided class has a zero-argument
   * constructor. Works even if the class or its constructor is private.
   */
  private static <E> E newInstance(Class<E> clazz)
      throws InstantiationException, IllegalAccessException {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      Constructor<E> defaultConstructor;
      try {
        defaultConstructor = clazz.getDeclaredConstructor();
      } catch (NoSuchMethodException f) {
        throw new InstantiationException("No zero-arg constructor.");
      }
      defaultConstructor.setAccessible(true);
      try {
        return defaultConstructor.newInstance();
      } catch (InvocationTargetException g) {
        throw new RuntimeException(g);
      }
    }
  }

  // For testing!
  void setQueueXml(QueueXml queueXml) {
    this.queueConfig = queueXml;
  }

  @Override
  public void start() {
    AccessController.doPrivileged(
        new PrivilegedAction<Object>() {
          @Override
          public Object run() {
            start_();
            return null;
          }
        });
  }

  private void start_() {
    // TODO Have DevAppServer install a shutdown hook and guarantee
    // the call of stop. (This to do was pulled from LocalDatastoreService, it applies here too).
    shutdownHook =
        new Thread() {
          @Override
          public void run() {
            LocalTaskQueue.this.stop_();
          }
        };
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    // Start the local fetch service.
    // Needs to happen before we start the scheduler to ensure that no
    // jobs get executed before the job has access to a fetch service.
    // Note: We considered other ways of having the local task queue
    // fetch urls, including a direct dependency on Apache HttpClient,
    // the api-level UrlFetchService, and ApiProxy.  If we had used HttpClient
    // we would have effectively been reimplementing LocalURLFetchService.
    // If we had used UrlFetchService we would have had to set up a dummy
    // Environment for each call.  If we had used ApiProxy we would have had
    // the same Environment issue, plus we would have given up type safety.
    // LocalURLFetchService maximizes type safety, reuse, and ease of use.
    fetchService.start();

    // Also needs to happen before we start the scheduler
    UrlFetchJob.initialize(localServerEnvironment, clock);

    scheduler = startScheduler(disableAutoTaskExecution);
    String baseUrl = getBaseUrl(localServerEnvironment);
    // TODO need to special case the default queue and limit
    // number of queues.
    if (queueConfig != null) {
      for (QueueXml.Entry entry : queueConfig.getEntries()) {
        if ("pull".equals(entry.getMode())) {
          queues.put(entry.getName(), new DevPullQueue(entry, clock));
        } else {
          queues.put(entry.getName(), new DevPushQueue(entry, scheduler, baseUrl, clock, callback));
        }
      }
    }

    // TODO Need to check consistency with production, may
    //  need to not create if the quota of queues is reached.
    if (queues.get(Queue.DEFAULT_QUEUE) == null) {
      QueueXml.Entry entry = QueueXml.defaultEntry();
      queues.put(entry.getName(), new DevPushQueue(entry, scheduler, baseUrl, clock, callback));
    }
    logger.info("Local task queue initialized with base url " + baseUrl);
  }

  static String getBaseUrl(LocalServerEnvironment localServerEnvironment) {
    String destAddress = localServerEnvironment.getAddress();
    if ("0.0.0.0".equals(destAddress)) {
      // TODO: Consider whether we could do something more general here.
      boolean ipv6 = InetAddress.getLoopbackAddress() instanceof Inet6Address;
      destAddress = ipv6 ? "[::1]" : "127.0.0.1";
    }
    return String.format("http://%s:%d", destAddress, localServerEnvironment.getPort());
  }

  @Override
  public void stop() {
    // Avoid removing the shutdownHook while a JVM shutdown is in progress.
    if (shutdownHook != null) {
      AccessController.doPrivileged(
          new PrivilegedAction<Void>() {
            @Override
            public Void run() {
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
              return null;
            }
          });
      shutdownHook = null;
    }
    stop_();
  }

  private void stop_() {
    queues.clear();
    stopScheduler(scheduler);
    fetchService.stop();
  }

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  private long currentTimeMillis() {
    return clock.getCurrentTime();
  }

  private long currentTimeUsec() {
    return currentTimeMillis() * 1000;
  }

  ErrorCode validateAddRequest(TaskQueueAddRequest.Builder addRequest) {
    ByteString taskName = addRequest.getTaskName();
    if (!taskName.isEmpty()
        && !QueueConstants.TASK_NAME_PATTERN.matcher(taskName.toStringUtf8()).matches()) {
      return ErrorCode.INVALID_TASK_NAME;
    }

    ByteString queueName = addRequest.getQueueName();
    if (queueName.isEmpty()
        || !QueueConstants.QUEUE_NAME_PATTERN.matcher(queueName.toStringUtf8()).matches()) {
      return ErrorCode.INVALID_QUEUE_NAME;
    }

    if (addRequest.getEtaUsec() < 0) {
      return ErrorCode.INVALID_ETA;
    }

    if (addRequest.getEtaUsec() - currentTimeUsec() > getMaxEtaDeltaUsec()) {
      return ErrorCode.INVALID_ETA;
    }

    if (addRequest.getMode() == Mode.PULL) {
      return validateAddPullRequest(addRequest);
    } else {
      return validateAddPushRequest(addRequest);
    }
  }

  ErrorCode validateAddPullRequest(TaskQueueAddRequest.Builder addRequest) {
    if (!addRequest.hasBody()) {
      return ErrorCode.INVALID_REQUEST;
    }
    return ErrorCode.OK;
  }

  ErrorCode validateAddPushRequest(TaskQueueAddRequest.Builder addRequest) {
    ByteString url = addRequest.getUrl();
    if (!addRequest.hasUrl() || url.isEmpty()) {
      return ErrorCode.INVALID_URL;
    }
    String urlString = url.toStringUtf8();
    if (urlString.charAt(0) != '/' || urlString.length() > QueueConstants.maxUrlLength()) {
      return ErrorCode.INVALID_URL;
    }
    return ErrorCode.OK;
  }

  // broken out for testing
  static long getMaxEtaDeltaUsec() {
    return QueueConstants.getMaxEtaDeltaMillis() * 1000;
  }

  @LatencyPercentiles(latency50th = 4)
  public TaskQueueAddResponse add(Status status, TaskQueueAddRequest addRequest) {
    TaskQueueBulkAddRequest.Builder bulkRequest = TaskQueueBulkAddRequest.newBuilder();
    bulkRequest.addAddRequestBuilder().mergeFrom(addRequest);

    TaskQueueAddResponse.Builder addResponse = TaskQueueAddResponse.newBuilder();

    TaskQueueBulkAddResponse bulkResponse = bulkAdd(status, bulkRequest.build());

    if (bulkResponse.getTaskResultCount() != 1) {
      throw new InternalFailureException(
          String.format(
              "expected 1 result from BulkAdd(), got %d", bulkResponse.getTaskResultCount()));
    }

    int result = bulkResponse.getTaskResult(0).getResult().getNumber();

    if (result != ErrorCode.OK_VALUE) {
      throw new ApiProxy.ApplicationException(result);
    } else if (bulkResponse.getTaskResult(0).hasChosenTaskName()) {
      addResponse.setChosenTaskName(bulkResponse.getTaskResult(0).getChosenTaskName());
    }

    return addResponse.build();
  }

  /** FetchQueueStats RPC implementation. */
  @LatencyPercentiles(latency50th = 3)
  public TaskQueueFetchQueueStatsResponse fetchQueueStats(
      Status status, TaskQueueFetchQueueStatsRequest fetchQueueStatsRequest) {
    TaskQueueFetchQueueStatsResponse.Builder fetchQueueStatsResponse =
        TaskQueueFetchQueueStatsResponse.newBuilder();

    for (ByteString unused : fetchQueueStatsRequest.getQueueNameList()) {
      TaskQueueFetchQueueStatsResponse.QueueStats.Builder stats =
          TaskQueueFetchQueueStatsResponse.QueueStats.newBuilder();
      TaskQueueScannerQueueInfo.Builder scannerInfo = TaskQueueScannerQueueInfo.newBuilder();

      // Random statistics.
      scannerInfo.setEnforcedRate(rng.nextInt(500) + 1);
      scannerInfo.setExecutedLastMinute(rng.nextInt(3000));
      scannerInfo.setRequestsInFlight(rng.nextInt(5));
      scannerInfo.setExecutedLastHour(0);
      scannerInfo.setSamplingDurationSeconds(0.0);
      if (rng.nextBoolean()) {
        stats.setNumTasks(0);
        stats.setOldestEtaUsec(-1);
      } else {
        stats.setNumTasks(rng.nextInt(2000) + 1);
        stats.setOldestEtaUsec(currentTimeMillis() * 1000);
      }
      stats.setScannerInfo(scannerInfo);

      fetchQueueStatsResponse.addQueueStats(stats);
    }
    return fetchQueueStatsResponse.build();
  }

  /** PurgeQueue RPC implementation. */
  @LatencyPercentiles(latency50th = 3)
  public TaskQueuePurgeQueueResponse purgeQueue(
      Status status, TaskQueuePurgeQueueRequest purgeQueueRequest) {
    TaskQueuePurgeQueueResponse purgeQueueResponse =
        TaskQueuePurgeQueueResponse.getDefaultInstance();
    flushQueue(purgeQueueRequest.getQueueName().toStringUtf8());
    return purgeQueueResponse;
  }

  /** BulkAdd RPC implementation. */
  @LatencyPercentiles(latency50th = 4)
  public TaskQueueBulkAddResponse bulkAdd(Status status, TaskQueueBulkAddRequest bulkAddRequest) {
    TaskQueueBulkAddResponse.Builder bulkAddResponse = TaskQueueBulkAddResponse.newBuilder();

    if (bulkAddRequest.getAddRequestCount() == 0) {
      return bulkAddResponse.build();
    }
    TaskQueueBulkAddRequest.Builder bulkAddRequestBuilder = bulkAddRequest.toBuilder();
    DevQueue queue =
        getQueueByName(bulkAddRequestBuilder.getAddRequest(0).getQueueName().toStringUtf8());

    Map<TaskQueueBulkAddResponse.TaskResult.Builder, String> chosenNames = new IdentityHashMap<>();
    boolean errorFound = false;

    for (TaskQueueAddRequest.Builder addRequest :
        bulkAddRequestBuilder.getAddRequestBuilderList()) {
      TaskQueueBulkAddResponse.TaskResult.Builder taskResult =
          bulkAddResponse.addTaskResultBuilder();
      ErrorCode error = validateAddRequest(addRequest);
      taskResult.setResult(error);
      if (error == ErrorCode.OK) {

        if (!addRequest.hasTaskName() || addRequest.getTaskName().isEmpty()) {
          addRequest.setTaskName(ByteString.copyFromUtf8(DevQueue.genTaskName()));
          chosenNames.put(taskResult, addRequest.getTaskName().toStringUtf8());
        }
        // Initialize the result as SKIPPED - this will be set to the actual result value if the
        // request does not contain any errors and proceeds to the AddActions/BulkAdd stage.
        taskResult.setResult(ErrorCode.SKIPPED);
      } else {
        taskResult.setResult(error);
        errorFound = true;
      }
    }

    if (errorFound) {
      return bulkAddResponse.build();
    }

    if (bulkAddRequestBuilder.getAddRequest(0).hasTransaction()
        || bulkAddRequestBuilder.getAddRequest(0).hasDatastoreTransaction()) {
      // This is a transactional request. The tasks will be handed to the datastore,
      // which will associate them with the txn.
      // When the txn is committed the tasks will be sent back over to
      // the taskqueue stub with the tranaction wiped out so that
      // they actually get added and we don't continue spinning around in
      // an infinite loop.
      //
      // Note that locally, datastore_v3.addActions expects an apphosting.TaskQueueBulkAddRequest
      // rather than an apphosting_datastore_v3.AddActionsRequest.
      try {
        ApiProxy.makeSyncCall(
            "datastore_v3", "addActions", bulkAddRequestBuilder.build().toByteArray());
      } catch (ApiProxy.ApplicationException exception) {
        throw new ApiProxy.ApplicationException(
            exception.getApplicationError() + ErrorCode.DATASTORE_ERROR_VALUE,
            exception.getErrorDetail());
      }
    } else {
      for (int i = 0; i < bulkAddRequestBuilder.getAddRequestCount(); ++i) {
        TaskQueueAddRequest.Builder addRequest = bulkAddRequestBuilder.getAddRequestBuilder(i);
        TaskQueueBulkAddResponse.TaskResult.Builder taskResult =
            bulkAddResponse.getTaskResultBuilder(i);

        try {
          // Validation of task mode will be performed in DevQueue object.
          queue.add(addRequest);
        } catch (ApiProxy.ApplicationException exception) {
          taskResult.setResult(ErrorCode.forNumber(exception.getApplicationError()));
        }
      }
    }

    for (TaskQueueBulkAddResponse.TaskResult.Builder taskResult :
        bulkAddResponse.getTaskResultBuilderList()) {
      if (taskResult.getResult() == ErrorCode.SKIPPED) {
        taskResult.setResult(ErrorCode.OK);
        if (chosenNames.containsKey(taskResult)) {
          taskResult.setChosenTaskName(ByteString.copyFromUtf8(chosenNames.get(taskResult)));
        }
      }
    }

    return bulkAddResponse.build();
  }

  /** Delete RPC implementation. */
  public TaskQueueDeleteResponse delete(Status status, TaskQueueDeleteRequest request) {
    String queueName = request.getQueueName().toStringUtf8();
    // throws if queue does not exist so no need to check for null
    DevQueue queue = getQueueByName(queueName);
    TaskQueueDeleteResponse.Builder response = TaskQueueDeleteResponse.newBuilder();
    for (ByteString taskName : request.getTaskNameList()) {
      try {
        if (!queue.deleteTask(taskName.toStringUtf8())) {
          response.addResult(ErrorCode.UNKNOWN_TASK);
        } else {
          response.addResult(ErrorCode.OK);
        }
      } catch (ApiProxy.ApplicationException e) {
        response.addResult(ErrorCode.forNumber(e.getApplicationError()));
      }
    }
    return response.build();
  }

  @LatencyPercentiles(latency50th = 8)
  public TaskQueueQueryAndOwnTasksResponse queryAndOwnTasks(
      Status status, TaskQueueQueryAndOwnTasksRequest request) {
    String queueName = request.getQueueName().toStringUtf8();
    validateQueueName(queueName);

    // getQueueByName will throw UNKNOWN_QUEUE if the queue does not exist.
    DevQueue queue = getQueueByName(queueName);

    if (queue.getMode() != Mode.PULL) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    }

    DevPullQueue pullQueue = (DevPullQueue) queue;
    List<TaskQueueAddRequest.Builder> results =
        pullQueue.queryAndOwnTasks(
            request.getLeaseSeconds(), request.getMaxTasks(),
            request.hasGroupByTag(), request.getTag().toByteArray());

    TaskQueueQueryAndOwnTasksResponse.Builder response =
        TaskQueueQueryAndOwnTasksResponse.newBuilder();
    for (TaskQueueAddRequest.Builder task : results) {
      TaskQueueQueryAndOwnTasksResponse.Task.Builder responseTask =
          response
              .addTaskBuilder()
              .setTaskName(task.getTaskName())
              .setBody(task.getBody())
              .setEtaUsec(task.getEtaUsec());
      if (task.hasTag()) {
        responseTask.setTag(task.getTag());
      }
      // TODO: To keep track of retry count, we can replace TaskQueueAddRequest with
      // TaskQueueQueryTasksResponse to represent a task.
    }
    return response.build();
  }

  public TaskQueueModifyTaskLeaseResponse modifyTaskLease(
      Status status, TaskQueueModifyTaskLeaseRequest request) {
    String queueName = request.getQueueName().toStringUtf8();
    validateQueueName(queueName);

    String taskName = request.getTaskName().toStringUtf8();
    validateTaskName(taskName);

    DevQueue queue = getQueueByName(queueName);

    if (queue.getMode() != Mode.PULL) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_QUEUE_MODE_VALUE);
    }

    DevPullQueue pullQueue = (DevPullQueue) queue;

    return pullQueue.modifyTaskLease(request);
  }

  /**
   * Returns a map of QueueStateInfo objects keyed by queue name.
   *
   * <p>This is not part of the public interface. It is used by the dev server admin console Task
   * Queue Viewer function.
   */
  public Map<String, QueueStateInfo> getQueueStateInfo() {
    return getQueueStateInfoInternal();
  }

  private Map<String, QueueStateInfo> getQueueStateInfoInternal() {
    TreeMap<String, QueueStateInfo> queueStateInfo = new TreeMap<String, QueueStateInfo>();

    for (Entry<String, DevQueue> entry : queues.entrySet()) {
      String queueName = entry.getKey();
      queueStateInfo.put(queueName, entry.getValue().getStateInfo());
    }

    return queueStateInfo;
  }

  private DevQueue getQueueByName(String queueName) {
    DevQueue queue = queues.get(queueName);
    if (queue == null) {
      throw new ApiProxy.ApplicationException(ErrorCode.UNKNOWN_QUEUE_VALUE, queueName);
    }
    return queue;
  }

  /** Remove all entries from a queue. */
  @LatencyPercentiles(latency50th = 4)
  public void flushQueue(String queueName) {
    // throws if queue does not exist so no need to check for null
    DevQueue queue = getQueueByName(queueName);
    queue.flush();
  }

  /**
   * Delete a task by name.
   *
   * @return False if the task name was not found.
   */
  public boolean deleteTask(String queueName, String taskName) {
    DevQueue queue = getQueueByName(queueName);
    return queue.deleteTask(taskName);
  }

  static Scheduler startScheduler(boolean disableAutoTaskExecution) {
    // TODO: Investigate config options for the scheduler like
    // threadpool size.
    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      // When a scheduler is first created it is in standby mode, which means
      // it will accept and schedule tasks but won't ever run them.
      if (!disableAutoTaskExecution) {
        // Move the scheduler into a state where it will run tasks at the
        // appropriate time.
        scheduler.start();
      }
      return scheduler;
    } catch (SchedulerException e) {
      throw new RuntimeException(e);
    }
  }

  static void stopScheduler(Scheduler scheduler) {
    if (scheduler == null) {
      return;
    }
    try {
      scheduler.shutdown(true);
    } catch (SchedulerException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Run a task by name.
   *
   * @return False if the task name was not found.
   */
  public boolean runTask(String queueName, String taskName) {
    DevQueue queue = getQueueByName(queueName);
    return queue.runTask(taskName);
  }

  @Override
  public Double getMaximumDeadline(boolean isOfflineRequest) {
    return 30.0;
  }

  static final void validateQueueName(String queueName) throws ApiProxy.ApplicationException {
    if (queueName == null
        || queueName.length() == 0
        || !QueueConstants.QUEUE_NAME_PATTERN.matcher(queueName).matches()) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_QUEUE_NAME_VALUE);
    }
  }

  static final void validateTaskName(String taskName) throws ApiProxy.ApplicationException {
    if (taskName == null
        || taskName.length() == 0
        || !QueueConstants.TASK_NAME_PATTERN.matcher(taskName).matches()) {
      throw new ApiProxy.ApplicationException(ErrorCode.INVALID_TASK_NAME_VALUE);
    }
  }
  /**
   * {@link LocalTaskQueueCallback} implementation that executes a url fetch using the {@link
   * LocalURLFetchService}. This implementation is used by the local task queue unless the user
   * provides their own implementation via the {@link #CALLBACK_CLASS_PROP} property.
   */
  static final class UrlFetchServiceLocalTaskQueueCallback implements LocalTaskQueueCallback {

    private final LocalURLFetchService fetchService;

    UrlFetchServiceLocalTaskQueueCallback(LocalURLFetchService fetchService) {
      this.fetchService = fetchService;
    }

    @Override
    public int execute(URLFetchServicePb.URLFetchRequest fetchReq) {
      LocalRpcService.Status status = new LocalRpcService.Status();
      return fetchService.fetch(status, fetchReq).getStatusCode();
    }

    @Override
    public void initialize(Map<String, String> properties) {
      // no initialization necessary
    }
  }
}
