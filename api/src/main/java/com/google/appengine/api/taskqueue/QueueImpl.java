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

import static com.google.appengine.api.taskqueue.QueueApiHelper.getInternal;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionHelper;
import com.google.appengine.api.taskqueue.TaskOptions.Param;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueModifyTaskLeaseRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueModifyTaskLeaseResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements the {@link Queue} interface. {@link QueueImpl} is thread safe.
 *
 */
class QueueImpl implements Queue {
  private final String queueName;
  // access this member via the getter so that we can override it in tests
  private final DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
  private final QueueApiHelper apiHelper;

  // Keep this in sync with X_APPENGINE_DEFAULT_NAMESPACE in
  // google3/apphosting/base/http_proto.cc and
  // com.google.appengine.tools.development.LocalHttpRequestEnvironment.DEFAULT_NAMESPACE_HEADER
  // com.google.appengine.api.NamespaceManager.DEFAULT_API_NAMESPACE_KEY
  /** The name of the HTTP header specifying the default namespace for API calls. */
  // (Not private so that tests can use it.)
  static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";

  static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";

  static final double DEFAULT_LEASE_TASKS_DEADLINE_SECONDS = 10.0;
  static final double DEFAULT_FETCH_STATISTICS_DEADLINE_SECONDS = 10.0;

  QueueImpl(String queueName, QueueApiHelper apiHelper) {
    QueueApiHelper.validateQueueName(queueName);

    this.apiHelper = apiHelper;
    this.queueName = queueName;
  }

  /**
   * Transform a future returning a single-entry list into a future returning that entry.
   *
   * @param future A future whose result is a singleton list.
   * @return A future whose result is the only element of the list.
   */
  private <T> Future<T> extractSingleEntry(Future<List<T>> future) {
    return new FutureAdapter<List<T>, T>(future) {
      @Override
      protected T wrap(List<T> key) throws Exception {
        if (key.size() != 1) {
          throw new InternalFailureException(
              "An internal error occurred while accessing queue '" + queueName + "'");
        }
        return key.get(0);
      }
    };
  }

  /** See {@link Queue#add()} */
  @Override
  public TaskHandle add() {
    return getInternal(addAsync());
  }

  /** See {@link Queue#addAsync()} */
  @Override
  public Future<TaskHandle> addAsync() {
    return addAsync(
        getDatastoreService().getCurrentTransaction(null), TaskOptions.Builder.withDefaults());
  }

  /**
   * Returns a {@link URI} validated to only contain legal components.
   *
   * <p>The "scheme", "authority" and "fragment" components of a URI must not be specified. The path
   * component must be absolute (i.e. start with "/").
   *
   * @param urlString The "url" specified by the client.
   * @throws IllegalArgumentException The provided urlString is null, too long or does not have
   *     correct syntax.
   */
  private URI parsePartialUrl(String urlString) {
    if (urlString == null) {
      throw new IllegalArgumentException("url must not be null");
    }

    // TODO This assumes that the unicode characters are all ASCII.
    // This really needs to be converted to bytes first but we need the
    // URI mapping for unicode.  An alternative is to enforce that all the
    // characters are 1 byte (encoded already).
    if (urlString.length() > QueueConstants.maxUrlLength()) {
      throw new IllegalArgumentException(
          "url is longer than " + QueueConstants.maxUrlLength() + ".");
    }

    URI uri;
    try {
      uri = new URI(urlString);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("URL syntax error", exception);
    }

    // Perform checks.
    uriCheckNull(uri.getScheme(), "scheme");
    uriCheckNull(uri.getRawAuthority(), "authority");
    uriCheckNull(uri.getRawFragment(), "fragment");
    String path = uri.getPath();

    if (path == null || path.length() == 0 || path.charAt(0) != '/') {
      if (path == null) {
        path = "(null)";
      } else if (path.length() == 0) {
        path = "<empty string>";
      }
      throw new IllegalArgumentException(
          "url must contain a path starting with '/' part - contains :" + path);
    }

    return uri;
  }

  private void uriCheckNull(String value, String valueName) {
    if (value != null) {
      throw new IllegalArgumentException(
          "url must not contain a '" + valueName + "' part - contains :" + value);
    }
  }

  private void checkPullTask(
      String url,
      LinkedHashMap<String, List<String>> headers,
      byte[] payload,
      RetryOptions retryOptions) {
    // PULL method, verify that it can't have url, headers, or parameters, and must have payload.
    if (url != null && !url.isEmpty()) {
      throw new IllegalArgumentException("May not specify url in tasks that have method PULL");
    }
    if (!headers.isEmpty()) {
      throw new IllegalArgumentException(
          "May not specify any header in tasks that have method PULL");
    }
    if (retryOptions != null) {
      throw new IllegalArgumentException(
          "May not specify retry options in tasks that have method PULL");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload must be specified for tasks with method PULL");
    }
  }

  private void checkPostTask(String query) {
    if (query != null && query.length() != 0) {
      throw new IllegalArgumentException(
          "POST method may not have a query string; use param() instead");
    }
  }

  /**
   * Construct a byte array data from params if payload is not specified. If it sees payload is
   * specified, return null.
   *
   * @throws IllegalArgumentException if params and payload both exist
   */
  private byte[] constructPayloadFromParams(List<Param> params, byte[] payload) {
    // params and payload must not be both specified when we do construction.
    if (!params.isEmpty() && payload != null) {
      throw new IllegalArgumentException(
          "Message body and parameters may not both be present; "
              + "only one of these may be supplied");
    }
    // If the payload is specified, we don't need to construct from params, return null
    return payload != null ? null : encodeParamsPost(params);
  }

  // Validate options and populate a TaskQueueAddRequest object.
  // txn can be null, it indicates that a task is not transactional.
  private void validateAndFillAddRequest(
      Transaction txn, TaskOptions taskOptions, TaskQueueAddRequest.Builder addRequest) {

    boolean useUrlEncodedContentType = false;

    LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>(taskOptions.getHeaders());
    String url = taskOptions.getUrl();
    byte[] payload = taskOptions.getPayload();
    List<Param> params = taskOptions.getParams();
    RetryOptions retryOptions = taskOptions.getRetryOptions();
    TaskOptions.Method method = taskOptions.getMethod();

    // Determine the URL.
    URI parsedUrl;
    if (url == null) {
      parsedUrl = parsePartialUrl(defaultUrl());
    } else {
      parsedUrl = parsePartialUrl(url);
    }
    String query = parsedUrl.getQuery();
    StringBuilder relativeUrl = new StringBuilder(parsedUrl.getRawPath());
    if (query != null && query.length() != 0 && !params.isEmpty()) {
      throw new IllegalArgumentException(
          "Query string and parameters both present; only one of these may be supplied");
    }

    // Validate task and construct payload or query string.
    byte[] constructedPayload;
    if (method == TaskOptions.Method.PULL) {
      constructedPayload = constructPayloadFromParams(params, payload);
      if (constructedPayload != null) {
        payload = constructedPayload;
      }
      checkPullTask(url, headers, payload, retryOptions);
    } else if (method == TaskOptions.Method.POST) {
      constructedPayload = constructPayloadFromParams(params, payload);
      if (constructedPayload != null) {
        payload = constructedPayload;
        useUrlEncodedContentType = true;
      }
      checkPostTask(query);
    } else {
      // A non-post method.
      if (!params.isEmpty()) {
        query = encodeParamsUrlEncoded(params);
      }
      if (query != null && query.length() != 0) {
        relativeUrl.append("?").append(query);
      }
    }
    if (payload != null && payload.length != 0 && !taskOptions.getMethod().supportsBody()) {
      throw new IllegalArgumentException(
          taskOptions.getMethod() + " method may not specify a payload.");
    }
    fillAddRequest(
        txn,
        queueName,
        taskOptions.getTaskName(),
        determineEta(taskOptions),
        method,
        relativeUrl.toString(),
        payload,
        headers,
        retryOptions,
        useUrlEncodedContentType,
        taskOptions.getTagAsBytes(),
        taskOptions.getDispatchDeadline(),
        addRequest);
  }

  // Populate a TaskQueueAddRequest object with all related data.
  private static void fillAddRequest(
      com.google.appengine.api.datastore.Transaction txn,
      String queueName,
      String taskName,
      long etaMillis,
      TaskOptions.Method method,
      String relativeUrl,
      byte[] payload,
      LinkedHashMap<String, List<String>> headers,
      RetryOptions retryOptions,
      boolean useUrlEncodedContentType,
      byte[] tag,
      Duration dispatchDeadline,
      TaskQueueAddRequest.Builder addRequest) {
    // Fills queue name and task name.
    addRequest
        .setQueueName(ByteString.copyFromUtf8(queueName))
        .setTaskName(ByteString.copyFromUtf8(Strings.nullToEmpty(taskName)));

    // Fills mode, method and url
    if (method == TaskOptions.Method.PULL) {
      addRequest.setMode(TaskQueueMode.Mode.PULL);
    } else {
      addRequest
          .setUrl(ByteString.copyFromUtf8(relativeUrl))
          .setMode(TaskQueueMode.Mode.PUSH)
          .setMethod(method.getPbMethod());
    }

    // Fills payload
    if (payload != null) {
      addRequest.setBody(ByteString.copyFrom(payload));
    }

    // Fills task ETA.
    addRequest.setEtaUsec(etaMillis * 1000);

    // Fills transactional data. Transactional tasks cannot be named.
    if (taskName != null && !taskName.isEmpty() && txn != null) {
      throw new IllegalArgumentException("transactional tasks cannot be named: " + taskName);
    }
    if (txn != null) {
      TransactionHelper.setTransaction(txn, addRequest);
    }

    // Fills retry options.
    if (retryOptions != null) {
      fillRetryParameters(retryOptions, addRequest.getRetryParametersBuilder());
    }

    // Adds special headers and copy to addRequest.
    if (NamespaceManager.getGoogleAppsNamespace().length() != 0) {
      if (!headers.containsKey(DEFAULT_NAMESPACE_HEADER)) {
        headers.put(
            DEFAULT_NAMESPACE_HEADER, Arrays.asList(NamespaceManager.getGoogleAppsNamespace()));
      }
    }
    if (!headers.containsKey(CURRENT_NAMESPACE_HEADER)) {
      String namespace = NamespaceManager.get();
      headers.put(CURRENT_NAMESPACE_HEADER, Arrays.asList(nullToEmpty(namespace)));
    }
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      // If the POST method is being used with parameters then ignore the content-type provided
      // by the user and use application/x-www-form-urlencoded.
      if (useUrlEncodedContentType && entry.getKey().toLowerCase().equals("content-type")) {
        continue;
      }

      for (String value : entry.getValue()) {
        addRequest
            .addHeaderBuilder()
            .setKey(ByteString.copyFromUtf8(entry.getKey()))
            .setValue(ByteString.copyFromUtf8(value));
      }
    }
    if (useUrlEncodedContentType) {
      addRequest
          .addHeaderBuilder()
          .setKey(ByteString.copyFromUtf8("content-type"))
          .setValue(ByteString.copyFromUtf8("application/x-www-form-urlencoded"));
    }

    if (tag != null) {
      if (method != TaskOptions.Method.PULL) {
        throw new IllegalArgumentException("Only PULL tasks can have a tag.");
      }
      if (tag.length > QueueConstants.maxTaskTagLength()) {
        throw new IllegalArgumentException(
            "Task tag must be no more than " + QueueConstants.maxTaskTagLength() + " bytes.");
      }
      addRequest.setTag(ByteString.copyFrom(tag));
    }

    if (dispatchDeadline != null) {
      addRequest.setDispatchDeadlineUsec(dispatchDeadline.toNanos() / 1000);
    }

    if (method == TaskOptions.Method.PULL) {
      if (addRequest.build().getSerializedSize() > QueueConstants.maxPullTaskSizeBytes()) {
        throw new IllegalArgumentException("Task size too large");
      }
    } else {
      if (addRequest.build().getSerializedSize() > QueueConstants.maxPushTaskSizeBytes()) {
        throw new IllegalArgumentException("Task size too large");
      }
    }
  }

  /**
   * Translates from RetryOptions to TaskQueueRetryParameters. Also checks ensures minBackoffSeconds
   * and maxBackoffSeconds are ordered correctly.
   */
  private static void fillRetryParameters(
      RetryOptions retryOptions, TaskQueueRetryParameters.Builder retryParameters) {
    if (retryOptions.getTaskRetryLimit() != null) {
      retryParameters.setRetryLimit(retryOptions.getTaskRetryLimit());
    }
    if (retryOptions.getTaskAgeLimitSeconds() != null) {
      retryParameters.setAgeLimitSec(retryOptions.getTaskAgeLimitSeconds());
    }
    if (retryOptions.getMinBackoffSeconds() != null) {
      retryParameters.setMinBackoffSec(retryOptions.getMinBackoffSeconds());
    }
    if (retryOptions.getMaxBackoffSeconds() != null) {
      retryParameters.setMaxBackoffSec(retryOptions.getMaxBackoffSeconds());
    }
    if (retryOptions.getMaxDoublings() != null) {
      retryParameters.setMaxDoublings(retryOptions.getMaxDoublings());
    }

    if (retryParameters.hasMinBackoffSec() && retryParameters.hasMaxBackoffSec()) {
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        throw new IllegalArgumentException(
            "minBackoffSeconds must not be greater than maxBackoffSeconds.");
      }
    } else if (retryParameters.hasMinBackoffSec()) {
      // TaskQueueRetryParameters has default values for min and max backoff.
      // We can play nice if someone sets one but not the other and
      // unintentionally causes minBackoffSec > maxBackoffSec.
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        retryParameters.setMaxBackoffSec(retryParameters.getMinBackoffSec());
      }
    } else if (retryParameters.hasMaxBackoffSec()) {
      // Play nice if someone unintentionally causes
      // minBackoffSec > maxBackoffSec.
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        retryParameters.setMinBackoffSec(retryParameters.getMaxBackoffSec());
      }
    }
  }

  /** See {@link Queue#add(TaskOptions)}. */
  @Override
  public TaskHandle add(TaskOptions taskOptions) {
    return getInternal(addAsync(taskOptions));
  }

  /** See {@link Queue#addAsync(TaskOptions)}. */
  @Override
  public Future<TaskHandle> addAsync(TaskOptions taskOptions) {
    return addAsync(getDatastoreService().getCurrentTransaction(null), taskOptions);
  }

  /** See {@link Queue#add(Iterable)}. */
  @Override
  public List<TaskHandle> add(Iterable<TaskOptions> taskOptions) {
    return getInternal(addAsync(taskOptions));
  }

  /** See {@link Queue#addAsync(Iterable)}. */
  @Override
  public Future<List<TaskHandle>> addAsync(Iterable<TaskOptions> taskOptions) {
    return addAsync(getDatastoreService().getCurrentTransaction(null), taskOptions);
  }

  /** See {@link Queue#add(com.google.appengine.api.datastore.Transaction, TaskOptions)}. */
  @Override
  public TaskHandle add(Transaction txn, TaskOptions taskOptions) {
    return getInternal(addAsync(txn, taskOptions));
  }

  /** See {@link Queue#addAsync(com.google.appengine.api.datastore.Transaction, TaskOptions)}. */
  @Override
  public Future<TaskHandle> addAsync(Transaction txn, TaskOptions taskOptions) {
    return extractSingleEntry(addAsync(txn, Collections.singletonList(taskOptions)));
  }

  /** See {@link Queue#add(com.google.appengine.api.datastore.Transaction, Iterable)}. */
  @Override
  public List<TaskHandle> add(Transaction txn, Iterable<TaskOptions> taskOptions) {
    return getInternal(addAsync(txn, taskOptions));
  }

  /** See {@link Queue#addAsync(com.google.appengine.api.datastore.Transaction, Iterable)}. */
  @Override
  public Future<List<TaskHandle>> addAsync(Transaction txn, Iterable<TaskOptions> taskOptions) {
    final List<TaskOptions> taskOptionsList = new ArrayList<>();
    Set<String> taskNames = new HashSet<>();

    final TaskQueueBulkAddRequest.Builder bulkAddRequest = TaskQueueBulkAddRequest.newBuilder();

    boolean hasPushTask = false;
    boolean hasPullTask = false;
    for (TaskOptions option : taskOptions) {
      TaskQueueAddRequest.Builder addRequest = bulkAddRequest.addAddRequestBuilder();
      validateAndFillAddRequest(txn, option, addRequest);
      if (addRequest.getMode() == TaskQueueMode.Mode.PULL) {
        hasPullTask = true;
      } else {
        hasPushTask = true;
      }

      taskOptionsList.add(option);
      if (option.getTaskName() != null && !option.getTaskName().isEmpty()) {
        if (!taskNames.add(option.getTaskName())) {
          throw new IllegalArgumentException(
              String.format(
                  "Identical task names in request : \"%s\" duplicated", option.getTaskName()));
        }
      }
    }
    if (bulkAddRequest.getAddRequestCount() > QueueConstants.maxTasksPerAdd()) {
      throw new IllegalArgumentException(
          String.format(
              "No more than %d tasks can be added in a single add call",
              QueueConstants.maxTasksPerAdd()));
    }

    if (hasPullTask && hasPushTask) {
      throw new IllegalArgumentException(
          "May not add both push tasks and pull tasks in the same call.");
    }
    TaskQueueBulkAddRequest builtRequest = bulkAddRequest.build();
    if (txn != null
        && builtRequest.getSerializedSize() > QueueConstants.maxTransactionalRequestSizeBytes()) {
      throw new IllegalArgumentException(
          String.format(
              "Transactional add may not be larger than %d bytes: %d bytes requested.",
              QueueConstants.maxTransactionalRequestSizeBytes(), builtRequest.getSerializedSize()));
    }

    Future<TaskQueueBulkAddResponse> responseFuture =
        makeAsyncCall("BulkAdd", builtRequest, TaskQueueBulkAddResponse.getDefaultInstance());
    return new FutureAdapter<TaskQueueBulkAddResponse, List<TaskHandle>>(responseFuture) {
      @Override
      protected List<TaskHandle> wrap(TaskQueueBulkAddResponse bulkAddResponse) {
        if (bulkAddResponse.getTaskResultCount() != bulkAddRequest.getAddRequestCount()) {
          throw new InternalFailureException(
              String.format(
                  "expected %d results from BulkAdd(), got %d",
                  bulkAddRequest.getAddRequestCount(), bulkAddResponse.getTaskResultCount()));
        }

        List<TaskHandle> tasks = new ArrayList<>();
        RuntimeException taskqueueException = null;
        for (int i = 0; i < bulkAddResponse.getTaskResultCount(); ++i) {
          TaskQueueBulkAddResponse.TaskResult.Builder taskResult =
              bulkAddResponse.toBuilder().getTaskResultBuilder(i);
          TaskQueueAddRequest addRequest = bulkAddRequest.getAddRequest(i);
          TaskOptions options = taskOptionsList.get(i);

          if (taskResult.getResult() == TaskQueueServiceError.ErrorCode.OK) {
            String taskName = options.getTaskName();
            if (taskResult.hasChosenTaskName()) {
              taskName = taskResult.getChosenTaskName().toStringUtf8();
            }
            TaskOptions taskResultOptions = new TaskOptions(options);
            taskResultOptions.taskName(taskName).payload(addRequest.getBody().toByteArray());
            TaskHandle handle = new TaskHandle(taskResultOptions, queueName);
            tasks.add(handle.etaUsec(addRequest.getEtaUsec()));
          } else if (taskResult.getResult() != TaskQueueServiceError.ErrorCode.SKIPPED) {
            // Since we are possibly adding multiple tasks, there may be a different error code
            // returned for each task. We will throw an exception corresponding to the first
            // error code we see, except that we treat TaskAlreadyExistsException specially.
            // We will only throw this Exception if TASK_ALREADY_EXISTS is the only error code
            // received. This is because a TaskAlreadyExistsException means that some of the
            // adds failed because the specified name exists already, and *all of the other tasks
            // were successfully added.* Also, if we throw a TaskAlreadyExistsException then
            // we will throw one that contains a list of the names of all tasks that were
            // not successfully added.
            if (taskqueueException == null
                || taskqueueException instanceof TaskAlreadyExistsException) {
              int result = taskResult.getResult().getNumber();
              String detail =
                  (result == TaskQueueServiceError.ErrorCode.UNKNOWN_QUEUE_VALUE)
                      ? queueName
                      : options.getTaskName();
              RuntimeException e = QueueApiHelper.translateError(result, detail);
              if (e instanceof TaskAlreadyExistsException) {
                if (taskqueueException == null) {
                  taskqueueException = e;
                }
                TaskAlreadyExistsException taee = (TaskAlreadyExistsException) taskqueueException;
                taee.appendTaskName(options.getTaskName());
              } else {
                taskqueueException = e;
              }
            }
          }
        }

        if (taskqueueException != null) {
          throw taskqueueException;
        }

        return tasks;
      }
    };
  }

  // Returns the System.currentTimeMillis().
  // May be overridden by a mock for testing.
  long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  // Computes the absolute time to execute task given taskOptions.
  // ETA may be specified as a absolute time (etaMillis) or a
  // delay (countdownMillis) but not both.
  private long determineEta(TaskOptions taskOptions) {
    Long etaMillis = taskOptions.getEtaMillis();
    Long countdownMillis = taskOptions.getCountdownMillis();
    if (etaMillis == null) {
      if (countdownMillis == null) {
        // Unspecified means now.
        return currentTimeMillis();
      } else {
        if (countdownMillis > QueueConstants.getMaxEtaDeltaMillis()) {
          throw new IllegalArgumentException("ETA too far into the future");
        }
        if (countdownMillis < 0) {
          throw new IllegalArgumentException("Negative countdown is not allowed");
        }
        return currentTimeMillis() + countdownMillis;
      }
    } else {
      if (countdownMillis == null) {
        if (etaMillis - currentTimeMillis() > QueueConstants.getMaxEtaDeltaMillis()) {
          throw new IllegalArgumentException("ETA too far into the future");
        }
        if (etaMillis < 0) {
          throw new IllegalArgumentException("Negative ETA is invalid");
        }
        return etaMillis;
      } else {
        // Error if countdownMillis and etaMillis are both specified.
        throw new IllegalArgumentException(
            "Only one or neither of EtaMillis and CountdownMillis may be specified");
      }
    }
  }

  // Encode params for a Post.
  // Note that this method may provide a different encoding
  // (e.g. multipart/form-data) that can support more space efficient
  // binary formatted payloads.
  byte[] encodeParamsPost(List<Param> params) {
    byte[] payload;
    try {
      payload = encodeParamsUrlEncoded(params).getBytes("UTF-8");
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(exception);
    }

    return payload;
  }

  // Returns content in the x-www-form-urlencoded format for parameters.
  String encodeParamsUrlEncoded(List<Param> params) {
    StringBuilder result = new StringBuilder();
    try {
      String appender = "";
      for (Param param : params) {
        result.append(appender);
        appender = "&";
        result.append(param.getURLEncodedName());
        result.append("=");
        result.append(param.getURLEncodedValue());
      }
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(exception);
    }
    return result.toString();
  }

  private String defaultUrl() {
    return DEFAULT_QUEUE_PATH + "/" + queueName;
  }

  /** See {@link Queue#getQueueName()}. */
  @Override
  public String getQueueName() {
    return queueName;
  }

  // for testing
  DatastoreService getDatastoreService() {
    return datastoreService;
  }

  /** See {@link Queue#purge()}. */
  @Override
  public void purge() {
    TaskQueuePurgeQueueRequest purgeRequest =
        TaskQueuePurgeQueueRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(queueName))
            .build();
    TaskQueuePurgeQueueResponse.Builder purgeResponse = TaskQueuePurgeQueueResponse.newBuilder();

    apiHelper.makeSyncCall("PurgeQueue", purgeRequest, purgeResponse);
  }

  /** See {@link Queue#deleteTask(String)}. */
  @Override
  public boolean deleteTask(String taskName) {
    return getInternal(deleteTaskAsync(taskName));
  }

  /** See {@link Queue#deleteTaskAsync(String)}. */
  @Override
  public Future<Boolean> deleteTaskAsync(String taskName) {
    TaskHandle.validateTaskName(taskName);
    return deleteTaskAsync(new TaskHandle(TaskOptions.Builder.withTaskName(taskName), queueName));
  }

  /** See {@link Queue#deleteTask(TaskHandle)}. */
  @Override
  public boolean deleteTask(TaskHandle taskHandle) {
    return getInternal(deleteTaskAsync(taskHandle));
  }

  /** See {@link Queue#deleteTaskAsync(TaskHandle)}. */
  @Override
  public Future<Boolean> deleteTaskAsync(TaskHandle taskHandle) {
    return extractSingleEntry(deleteTaskAsync(Collections.singletonList(taskHandle)));
  }

  /** See {@link Queue#deleteTask(List<TaskHandle>)}. */
  @Override
  public List<Boolean> deleteTask(List<TaskHandle> taskHandles) {
    return getInternal(deleteTaskAsync(taskHandles));
  }

  /** See {@link Queue#deleteTaskAsync(List<TaskHandle>)}. */
  @Override
  public Future<List<Boolean>> deleteTaskAsync(List<TaskHandle> taskHandles) {

    final TaskQueueDeleteRequest.Builder deleteRequest =
        TaskQueueDeleteRequest.newBuilder().setQueueName(ByteString.copyFromUtf8(queueName));

    for (TaskHandle taskHandle : taskHandles) {
      if (taskHandle.getQueueName().equals(this.queueName)) {
        deleteRequest.addTaskName(ByteString.copyFromUtf8(taskHandle.getName()));
      } else {
        throw new QueueNameMismatchException(
            String.format(
                "The task %s is associated with the queue named %s "
                    + "and cannot be deleted from the queue named %s.",
                taskHandle.getName(), taskHandle.getQueueName(), this.queueName));
      }
    }

    Future<TaskQueueDeleteResponse> responseFuture =
        makeAsyncCall(
            "Delete", deleteRequest.build(), TaskQueueDeleteResponse.getDefaultInstance());
    return new FutureAdapter<TaskQueueDeleteResponse, List<Boolean>>(responseFuture) {
      @Override
      protected List<Boolean> wrap(TaskQueueDeleteResponse deleteResponse) {
        List<Boolean> result = new ArrayList<>(deleteResponse.getResultCount());

        for (int i = 0; i < deleteResponse.getResultCount(); ++i) {
          TaskQueueServiceError.ErrorCode errorCode = deleteResponse.getResult(i);
          if (errorCode != TaskQueueServiceError.ErrorCode.OK
              && errorCode != TaskQueueServiceError.ErrorCode.TOMBSTONED_TASK
              && errorCode != TaskQueueServiceError.ErrorCode.UNKNOWN_TASK) {
            throw QueueApiHelper.translateError(
                errorCode.getNumber(), deleteRequest.getTaskName(i).toString());
          }
          result.add(errorCode == TaskQueueServiceError.ErrorCode.OK);
        }

        return result;
      }
    };
  }

  private Future<List<TaskHandle>> leaseTasksInternal(LeaseOptions options) {
    long leaseMillis = options.getUnit().toMillis(options.getLease());
    if (leaseMillis > QueueConstants.maxLease(MILLISECONDS)) {
      throw new IllegalArgumentException(
          String.format(
              "A lease period can be no longer than %d seconds", QueueConstants.maxLease(SECONDS)));
    }

    if (options.getCountLimit() > QueueConstants.maxLeaseCount()) {
      throw new IllegalArgumentException(
          String.format(
              "No more than %d tasks can be leased in one call", QueueConstants.maxLeaseCount()));
    }

    TaskQueueQueryAndOwnTasksRequest.Builder leaseRequest =
        TaskQueueQueryAndOwnTasksRequest.newBuilder()
            .setQueueName(ByteString.copyFromUtf8(queueName))
            .setLeaseSeconds(leaseMillis / 1000.0)
            .setMaxTasks(options.getCountLimit());
    if (options.getGroupByTag()) {
      // You can groupByTag with a null tag. This means "return tasks grouped by the same
      // tag as the task of minimum eta".
      leaseRequest.setGroupByTag(true);
      if (options.getTag() != null) {
        leaseRequest.setTag(ByteString.copyFrom(options.getTag()));
      }
    }

    ApiConfig apiConfig = new ApiConfig();
    if (options.getDeadlineInSeconds() == null) {
      apiConfig.setDeadlineInSeconds(DEFAULT_LEASE_TASKS_DEADLINE_SECONDS);
    } else {
      apiConfig.setDeadlineInSeconds(options.getDeadlineInSeconds());
    }

    Future<TaskQueueQueryAndOwnTasksResponse> responseFuture =
        apiHelper.makeAsyncCall(
            "QueryAndOwnTasks",
            leaseRequest.build(),
            TaskQueueQueryAndOwnTasksResponse.getDefaultInstance(),
            apiConfig);
    return new FutureAdapter<TaskQueueQueryAndOwnTasksResponse, List<TaskHandle>>(responseFuture) {
      @Override
      protected List<TaskHandle> wrap(TaskQueueQueryAndOwnTasksResponse leaseResponse) {
        List<TaskHandle> result = new ArrayList<>();
        for (TaskQueueQueryAndOwnTasksResponse.Task response : leaseResponse.getTaskList()) {
          TaskOptions taskOptions =
              TaskOptions.Builder.withTaskName(response.getTaskName().toStringUtf8())
                  .payload(response.getBody().toByteArray())
                  .method(TaskOptions.Method.PULL);
          if (response.hasTag()) {
            taskOptions.tag(response.getTag().toByteArray());
          }
          TaskHandle handle = new TaskHandle(taskOptions, queueName, response.getRetryCount());
          result.add(handle.etaUsec(response.getEtaUsec()));
        }

        return result;
      }
    };
  }

  /** See {@link Queue#leaseTasks(long, TimeUnit, long)}. */
  @Override
  public List<TaskHandle> leaseTasks(long lease, TimeUnit unit, long countLimit) {
    return getInternal(leaseTasksAsync(lease, unit, countLimit));
  }

  /** See {@link Queue#leaseTasksAsync(long, TimeUnit, long)}. */
  @Override
  public Future<List<TaskHandle>> leaseTasksAsync(long lease, TimeUnit unit, long countLimit) {
    return leaseTasksInternal(
        LeaseOptions.Builder.withLeasePeriod(lease, unit).countLimit(countLimit));
  }

  /** See {@link Queue#leaseTasksAsync(LeaseOptions)}. */
  @Override
  public Future<List<TaskHandle>> leaseTasksAsync(LeaseOptions options) {
    if (options.getLease() == null) {
      throw new IllegalArgumentException("The lease period must be specified");
    }
    if (options.getCountLimit() == null) {
      throw new IllegalArgumentException("The count limit must be specified");
    }
    return leaseTasksInternal(options);
  }

  /** See {@link Queue#leaseTasksByTagBytes(long, TimeUnit, long, byte[])}. */
  @Override
  public List<TaskHandle> leaseTasksByTagBytes(
      long lease, TimeUnit unit, long countLimit, byte[] tag) {
    return getInternal(leaseTasksByTagBytesAsync(lease, unit, countLimit, tag));
  }

  /** See {@link Queue#leaseTasksByTagBytesAsync(long, TimeUnit, long, byte[])}. */
  @Override
  public Future<List<TaskHandle>> leaseTasksByTagBytesAsync(
      long lease, TimeUnit unit, long countLimit, byte[] tag) {
    LeaseOptions options = LeaseOptions.Builder.withLeasePeriod(lease, unit).countLimit(countLimit);
    if (tag != null) {
      options.tag(tag);
    } else {
      options.groupByTag();
    }
    return leaseTasksInternal(options);
  }

  /** See {@link Queue#leaseTasksByTag(long, TimeUnit, long, String)}. */
  @Override
  public List<TaskHandle> leaseTasksByTag(long lease, TimeUnit unit, long countLimit, String tag) {
    return getInternal(leaseTasksByTagAsync(lease, unit, countLimit, tag));
  }

  /** See {@link Queue#leaseTasksByTagAsync(long, TimeUnit, long, String)}. */
  @Override
  public Future<List<TaskHandle>> leaseTasksByTagAsync(
      long lease, TimeUnit unit, long countLimit, String tag) {
    LeaseOptions options = LeaseOptions.Builder.withLeasePeriod(lease, unit).countLimit(countLimit);
    if (tag != null) {
      options.tag(tag);
    } else {
      options.groupByTag();
    }
    return leaseTasksInternal(options);
  }

  /** See {@link Queue#leaseTasks(LeaseOptions)}. */
  @Override
  public List<TaskHandle> leaseTasks(LeaseOptions options) {
    return getInternal(leaseTasksAsync(options));
  }

  /** See {@link Queue#modifyTaskLease(TaskHandle, long, TimeUnit)}. */
  @Override
  public TaskHandle modifyTaskLease(TaskHandle taskHandle, long lease, TimeUnit unit) {
    long leaseMillis = unit.toMillis(lease);
    if (leaseMillis > QueueConstants.maxLease(MILLISECONDS)) {
      throw new IllegalArgumentException(
          String.format(
              "The lease time specified (%s seconds) is too large. "
                  + "Lease period can be no longer than %d seconds.",
              formatLeaseTimeInSeconds(leaseMillis), QueueConstants.maxLease(SECONDS)));
    }
    if (leaseMillis < 0) {
      throw new IllegalArgumentException(
          String.format(
              "The lease time must not be negative. Specified lease time was %s seconds.",
              formatLeaseTimeInSeconds(leaseMillis)));
    }

    TaskQueueModifyTaskLeaseRequest.Builder request = TaskQueueModifyTaskLeaseRequest.newBuilder();
    TaskQueueModifyTaskLeaseResponse.Builder response =
        TaskQueueModifyTaskLeaseResponse.newBuilder();

    request
        .setQueueName(ByteString.copyFromUtf8(this.queueName))
        .setTaskName(ByteString.copyFromUtf8(taskHandle.getName()))
        .setLeaseSeconds(leaseMillis / 1000.0)
        .setEtaUsec(taskHandle.getEtaUsec());

    apiHelper.makeSyncCall("ModifyTaskLease", request.build(), response);
    taskHandle.etaUsec(response.getUpdatedEtaUsec());
    return taskHandle;
  }

  private String formatLeaseTimeInSeconds(long milliSeconds) {
    long seconds = TimeUnit.SECONDS.convert(milliSeconds, TimeUnit.MILLISECONDS);
    long remainder = milliSeconds - TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);
    String formatString = milliSeconds < 0 ? "-%01d.%03d" : "%01d.%03d";
    return String.format(formatString, Math.abs(seconds), Math.abs(remainder));
  }

  /** See {@link Queue#fetchStatistics()}. */
  @Override
  public QueueStatistics fetchStatistics() {
    return getInternal(fetchStatisticsAsync(null));
  }

  /** See {@link Queue#fetchStatisticsAsync(Double)}. */
  @Override
  public Future<QueueStatistics> fetchStatisticsAsync(@Nullable Double deadlineInSeconds) {
    if (deadlineInSeconds == null) {
      deadlineInSeconds = DEFAULT_FETCH_STATISTICS_DEADLINE_SECONDS;
    }

    if (deadlineInSeconds <= 0.0) {
      throw new IllegalArgumentException("Deadline must be > 0, got " + deadlineInSeconds);
    }

    List<Queue> queues = Collections.<Queue>singletonList(this);
    Future<List<QueueStatistics>> future =
        QueueStatistics.fetchForQueuesAsync(queues, apiHelper, deadlineInSeconds);
    return extractSingleEntry(future);
  }

  <T extends MessageLite> Future<T> makeAsyncCall(
      String methodName, MessageLite request, T responseTemplate) {
    return apiHelper.makeAsyncCall(methodName, request, responseTemplate, new ApiConfig());
  }
}
