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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.StatusCode;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.GetAccessTokenResult;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.datastore.DatastoreApiHelper;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.AppEngineRouting;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CloudTasksSettings;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.DeleteTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.Queue;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.RunTaskRequest;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.TaskName;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clean Java client wrapper for Google Cloud Tasks API operations using official CloudTasksClient
 * SDK.
 *
 * <p>This class provides method-level integration between the legacy App Engine Task Queue API
 * ({@link QueueImpl}) and Google Cloud Tasks when the environment variable {@code
 * APPENGINE_USE_CLOUDTASK_PUSH_QUEUE} is set to {@code true}.
 */
final class CloudTasksClientWrapper {

  private static final Logger logger = Logger.getLogger(CloudTasksClientWrapper.class.getName());

  // Environment Variables & System Properties
  static final String ENV_APPENGINE_USE_CLOUDTASK_PUSH_QUEUE = "APPENGINE_USE_CLOUDTASK_PUSH_QUEUE";
  static final String ENV_GAE_SERVICE = "GAE_SERVICE";
  static final String PROP_MTLS_ENABLED = "com.google.cloud.mtls.enabled";

  // Defaults & Prefixes
  static final String DEFAULT_QUEUE_NAME = "default";
  static final String DEFAULT_SERVICE_NAME = "default";
  static final String DEFAULT_RELATIVE_URI = "/";
  static final String TASK_NAME_PREFIX = "task-";
  static final String SDK_LANG_JAVA = "JAVA";

  // OAuth Scopes & Metadata Server Constants
  static final String SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";
  static final String SCOPE_CLOUDTASKS = "https://www.googleapis.com/auth/cloudtasks";
  static final String METADATA_TOKEN_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
  // HTTP Headers & Methods
  static final String HEADER_AUTHORIZATION = "Authorization";
  static final String VALUE_BEARER_PREFIX = "Bearer ";
  static final String HEADER_CONTENT_TYPE = "Content-Type";
  static final String VALUE_APPLICATION_JSON = "application/json";
  static final String HEADER_METADATA_FLAVOR = "Metadata-Flavor";
  static final String VALUE_METADATA_FLAVOR_GOOGLE = "Google";
  static final String HTTP_METHOD_GET = "GET";
  static final String HTTP_METHOD_POST = "POST";

  // Custom HTTP Headers
  static final String HEADER_X_TASK_RETRY_LIMIT = "X-Task-Retry-Limit";
  static final String HEADER_X_TASK_AGE_LIMIT_SECONDS = "X-Task-Age-Limit-Seconds";
  static final String HEADER_X_TASK_MIN_BACKOFF_SECONDS = "X-Task-Min-Backoff-Seconds";
  static final String HEADER_X_TASK_MAX_BACKOFF_SECONDS = "X-Task-Max-Backoff-Seconds";
  static final String HEADER_X_TASK_MAX_DOUBLINGS = "X-Task-Max-Doublings";

  // JSON Payload Field Names
  static final String JSON_FIELD_TASK = "task";
  static final String JSON_FIELD_NAME = "name";
  static final String JSON_FIELD_APP_ENGINE_HTTP_REQUEST = "appEngineHttpRequest";
  static final String JSON_FIELD_APP_ENGINE_ROUTING = "appEngineRouting";
  static final String JSON_FIELD_SERVICE = "service";
  static final String JSON_FIELD_HTTP_METHOD = "httpMethod";
  static final String JSON_FIELD_RELATIVE_URI = "relativeUri";
  static final String JSON_FIELD_BODY = "body";
  static final String JSON_FIELD_HEADERS = "headers";
  static final String JSON_FIELD_SCHEDULE_TIME = "scheduleTime";
  static final String JSON_FIELD_RETRY_CONFIG = "retryConfig";
  static final String JSON_FIELD_MAX_ATTEMPTS = "maxAttempts";
  static final String JSON_FIELD_MAX_RETRY_DURATION = "maxRetryDuration";
  static final String JSON_FIELD_MIN_BACKOFF = "minBackoff";
  static final String JSON_FIELD_MAX_BACKOFF = "maxBackoff";
  static final String JSON_FIELD_MAX_DOUBLINGS = "maxDoublings";
  static final String JSON_FIELD_ACCESS_TOKEN = "access_token";
  static final String JSON_FIELD_EXPIRES_IN = "expires_in";
  static final String JSON_FIELD_STATS = "stats";
  static final String JSON_FIELD_TASKS_COUNT = "tasksCount";
  static final String JSON_FIELD_OLDEST_ESTIMATED_ARRIVAL_TIME = "oldestEstimatedArrivalTime";
  static final String JSON_FIELD_EXECUTED_LAST_MINUTE_COUNT = "executedLastMinuteCount";
  static final String JSON_FIELD_CONCURRENT_DISPATCHES_COUNT = "concurrentDispatchesCount";
  static final String JSON_FIELD_EFFECTIVE_EXECUTION_RATE = "effectiveExecutionRate";
  static final String JSON_FIELD_REQUESTS = "requests";
  static final String JSON_FIELD_NAMES = "names";
  static final String JSON_FIELD_RESPONSE = "response";
  static final String JSON_FIELD_TASKS = "tasks";

  // Cloud Tasks Field Mask Paths
  static final String READ_MASK_STATS = "stats";

  // Error Messages & Markers
  static final String MSG_UNKNOWN_QUEUE_PREFIX = "The specified queue is unknown : ";
  static final String MSG_TASK_ALREADY_EXISTS_PREFIX = "Task already exists: ";
  static final String MSG_CLOUDTASK_PURGE_FAILED = "CLOUDTASK_PURGE_FAILED";
  static final String MARKER_ALREADY_EXISTS = "AlreadyExists";
  static final String MARKER_ALREADY_EXISTS_UPPER = "ALREADY_EXISTS";
  static final String MARKER_EXISTED_TOO_RECENTLY = "existed too recently";
  static final String MARKER_NOT_FOUND = "NotFound";
  static final String MARKER_NOT_FOUND_UPPER = "NOT_FOUND";
  static final String MARKER_QUEUE_NOT_FOUND = "queue not found";
  static final String MARKER_QUEUE_DOES_NOT_EXIST = "Queue does not exist";

  // Datastore Entity Kind & Properties
  static final String ENTITY_KIND_PENDING_TASK = "_AE_PendingCloudTask";
  static final String PROPERTY_QUEUE_NAME = "queue_name";
  static final String PROPERTY_CLOUD_TASK_NAME = "cloud_task_name";
  static final String PROPERTY_CLOUD_TASK_PAYLOAD = "cloud_task_payload";
  static final String PROPERTY_CREATED = "created";
  static final String PROPERTY_STATUS = "status";
  static final String PROPERTY_LOCK_EXPIRES = "lock_expires";
  static final String PROPERTY_RETRY_COUNT = "retry_count";
  static final String PROPERTY_LAST_ERROR = "last_error";
  static final String PROPERTY_LAST_RES_CODE = "last_res_code";
  static final String PROPERTY_NEXT_RETRY_AT = "next_retry_at";
  static final String PROPERTY_HANDLED_BY_SWEEPER = "handled_by_sweeper";
  static final String PROPERTY_SDK_LANG = "sdk_lang";

  // Task Statuses
  static final String STATUS_PENDING = "PENDING";
  static final String STATUS_PROCESSING = "PROCESSING";
  static final String STATUS_DONE = "DONE";
  static final String STATUS_FAILED = "FAILED";
  static final String STATUS_ALREADY_EXISTS = "ALREADY_EXISTS";

  // Sweeper & Cron Endpoints / Headers
  static final String SWEEP_ENDPOINT = "/_ah/cloudtask/sweep";
  static final String HEADER_CRON = "X-AppEngine-Cron";
  static final String HEADER_HTTP_CRON = "HTTP_X_APPENGINE_CRON";

  // Environment Variables & System Properties
  static final String ENV_GOOGLE_CLOUD_PROJECT = "GOOGLE_CLOUD_PROJECT";
  static final String ENV_GAE_APPLICATION = "GAE_APPLICATION";
  static final String ENV_GAE_LONG_APP_ID = "GAE_LONG_APP_ID";
  static final String ENV_GOOGLE_CLOUD_REGION = "GOOGLE_CLOUD_REGION";
  static final String ENV_LOCAL_GCP_REGION = "LOCAL_GCP_REGION";
  static final String ENV_GAE_ZONE = "GAE_ZONE";
  static final String ENV_LOCATION_ID = "LOCATION_ID";
  static final String ENV_GAE_LOCATION = "GAE_LOCATION";
  static final String ENV_GAE_REGION = "GAE_REGION";
  static final String PROP_GAE_LOCATION = "gae.location";

  // Metadata Server & Discovery URLs
  static final String METADATA_REGION_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/region";
  static final String METADATA_ZONE_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/zone";
  static final String CLOUD_TASKS_LOCATIONS_URL_FORMAT =
      "https://cloudtasks.googleapis.com/v2beta3/projects/%s/locations";
  static final String JSON_FIELD_LOCATIONS = "locations";
  static final String JSON_FIELD_LOCATION_ID = "locationId";
  static final String DEFAULT_LOCATION = "us-central1";

  // Timeouts & Durations (in milliseconds)
  static final int METADATA_TIMEOUT_MILLIS = 2000;
  static final int TOKEN_REQUEST_TIMEOUT_MILLIS = 5000;
  static final int STATS_REQUEST_TIMEOUT_MILLIS = 3000;
  static final int REST_REQUEST_TIMEOUT_MILLIS = 5000;
  static final int BATCH_REQUEST_TIMEOUT_MILLIS = 10000;
  static final long TOKEN_EXPIRY_BUFFER_MILLIS = 60_000L;
  static final long DEFAULT_TOKEN_EXPIRY_MILLIS = 3_600_000L;
  static final long DEFAULT_TOKEN_EXPIRES_IN_SECONDS = 3600L;
  static final long SCHEDULE_DELAY_THRESHOLD_MILLIS = 100L;
  static final long REST_SCHEDULE_DELAY_THRESHOLD_MILLIS = 1000L;

  // Time Unit Multipliers & Conversions
  static final long MICROS_PER_MILLI = 1000L;
  static final long MILLIS_PER_SECOND = 1000L;
  static final long MICROS_PER_SECOND = 1_000_000L;
  static final long NANOS_PER_MICRO = 1000L;
  static final long NANOS_PER_MILLI = 1_000_000L;

  private static volatile String cachedLocation = null;

  /**
   * Resolves the current Google Cloud Platform project ID from the App Engine runtime environment.
   *
   * @return the GCP project ID string
   */
  static String getProjectId() {
    String projectId = System.getenv(ENV_GOOGLE_CLOUD_PROJECT);
    if (projectId != null && !projectId.isEmpty()) {
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_APPLICATION);
    if (projectId != null && !projectId.isEmpty()) {
      if (projectId.contains("~")) {
        return projectId.substring(projectId.indexOf("~") + 1);
      }
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_LONG_APP_ID);
    if (projectId != null && !projectId.isEmpty()) {
      return projectId;
    }
    Environment env = ApiProxy.getCurrentEnvironment();
    if (env != null) {
      String appId = env.getAppId();
      if (appId != null && appId.contains("~")) {
        return appId.substring(appId.indexOf("~") + 1);
      }
      return (appId != null) ? appId : "";
    }
    return "";
  }

  /**
   * Resolves the current App Engine deployment location/region from environment variables, system
   * properties, the GCP instance metadata server, or Cloud Tasks locations API.
   *
   * @return the GCP region ID (e.g. {@code "us-central1"}, {@code "us-east1"})
   */
  static String getLocation() {
    if (cachedLocation != null) {
      return cachedLocation;
    }
    String zone = System.getenv(ENV_GAE_ZONE);
    if (zone != null && !zone.isEmpty()) {
      int lastDash = zone.lastIndexOf('-');
      if (lastDash > 0) {
        cachedLocation = zone.substring(0, lastDash);
        return cachedLocation;
      }
      cachedLocation = zone;
      return cachedLocation;
    }
    String location = System.getenv(ENV_LOCATION_ID);
    if (location != null && !location.isEmpty()) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_LOCATION);
    if (location != null && !location.isEmpty()) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_REGION);
    if (location != null && !location.isEmpty()) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GOOGLE_CLOUD_REGION);
    if (location != null && !location.isEmpty()) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getProperty(PROP_GAE_LOCATION);
    if (location != null && !location.isEmpty()) {
      cachedLocation = location;
      return cachedLocation;
    }
    try {
      URL url = URI.create(METADATA_REGION_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
      conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS);
      conn.setReadTimeout(METADATA_TIMEOUT_MILLIS);
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8))) {
          String regionPath = reader.readLine();
          if (regionPath != null) {
            String parsed =
                regionPath.contains("/")
                    ? regionPath.substring(regionPath.lastIndexOf('/') + 1).trim()
                    : regionPath.trim();
            if (!parsed.isEmpty()) {
              cachedLocation = parsed;
              return cachedLocation;
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Ignore metadata failure
    }
    try {
      URL url = URI.create(METADATA_ZONE_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
      conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS);
      conn.setReadTimeout(METADATA_TIMEOUT_MILLIS);
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8))) {
          String zonePath = reader.readLine();
          if (zonePath != null) {
            String parsed =
                zonePath.contains("/")
                    ? zonePath.substring(zonePath.lastIndexOf('/') + 1).trim()
                    : zonePath.trim();
            int lastDash = parsed.lastIndexOf('-');
            if (lastDash > 0) {
              cachedLocation = parsed.substring(0, lastDash);
              return cachedLocation;
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Ignore
    }
    try {
      String projId = getProjectId();
      String token = getValidAccessToken();
      if (projId != null && !projId.isEmpty() && token != null) {
        URL url = URI.create(String.format(CLOUD_TASKS_LOCATIONS_URL_FORMAT, projId)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(HTTP_METHOD_GET);
        conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
        conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS);
        conn.setReadTimeout(METADATA_TIMEOUT_MILLIS);
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
          try (BufferedReader reader =
              new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
              sb.append(line);
            }
            JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
            if (json.has(JSON_FIELD_LOCATIONS)) {
              JsonArray locs = json.getAsJsonArray(JSON_FIELD_LOCATIONS);
              if (locs.size() > 0) {
                JsonObject firstLoc = locs.get(0).getAsJsonObject();
                if (firstLoc.has(JSON_FIELD_LOCATION_ID)) {
                  cachedLocation = firstLoc.get(JSON_FIELD_LOCATION_ID).getAsString();
                  return cachedLocation;
                }
              }
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Ignore
    }
    String localRegion = System.getenv(ENV_LOCAL_GCP_REGION);
    return (localRegion != null && !localRegion.isEmpty()) ? localRegion : DEFAULT_LOCATION;
  }

  static {
    System.setProperty(PROP_MTLS_ENABLED, "false");
  }

  private static volatile CloudTasksClient sharedClient;

  private CloudTasksClientWrapper() {}

  private static volatile String cachedToken = null;
  private static volatile long cachedTokenExpiry = 0;

  static String getValidAccessToken() throws Exception {
    long now = Instant.now().toEpochMilli();
    if (cachedToken != null && now < cachedTokenExpiry - TOKEN_EXPIRY_BUFFER_MILLIS) {
      return cachedToken;
    }

    try {
      AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
      GetAccessTokenResult tokenResult =
          appIdentityService.getAccessToken(ImmutableList.of(SCOPE_CLOUD_PLATFORM));
      if (tokenResult != null && tokenResult.getAccessToken() != null) {
        cachedToken = tokenResult.getAccessToken();
        cachedTokenExpiry =
            tokenResult.getExpirationTime() != null
                ? tokenResult.getExpirationTime().toInstant().toEpochMilli()
                : (now + DEFAULT_TOKEN_EXPIRY_MILLIS);
        return cachedToken;
      }
    } catch (Throwable ignored) {
      logger.log(Level.FINE, "Could not acquire token from AppIdentityService", ignored);
    }

    try {
      URL url = URI.create(METADATA_TOKEN_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod(HTTP_METHOD_GET);
      conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
      conn.setConnectTimeout(TOKEN_REQUEST_TIMEOUT_MILLIS);
      conn.setReadTimeout(TOKEN_REQUEST_TIMEOUT_MILLIS);
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (InputStream is = conn.getInputStream()) {
          String resp = new String(is.readAllBytes(), UTF_8);
          JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
          String token = obj.get(JSON_FIELD_ACCESS_TOKEN).getAsString();
          long expiresIn =
              obj.has(JSON_FIELD_EXPIRES_IN)
                  ? obj.get(JSON_FIELD_EXPIRES_IN).getAsLong()
                  : DEFAULT_TOKEN_EXPIRES_IN_SECONDS;
          cachedToken = token;
          cachedTokenExpiry = now + (expiresIn * MILLIS_PER_SECOND);
          return token;
        }
      }
    } catch (Exception e) {
      logger.log(
          Level.WARNING, "Failed to get access token from Metadata server: " + e.getMessage());
    }

    try {
      GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
      if (creds != null) {
        creds.refreshIfExpired();
        AccessToken token = creds.getAccessToken();
        if (token != null && token.getTokenValue() != null) {
          cachedToken = token.getTokenValue();
          cachedTokenExpiry =
              token.getExpirationTime() != null
                  ? token.getExpirationTime().toInstant().toEpochMilli()
                  : (now + DEFAULT_TOKEN_EXPIRY_MILLIS);
          return cachedToken;
        }
      }
    } catch (Exception e) {
      logger.log(Level.FINE, "Could not acquire token from GoogleCredentials", e);
    }

    if (cachedToken != null) {
      return cachedToken;
    }
    throw new IOException(
        "Unable to obtain access token from AppIdentityService, Metadata server, or"
            + " GoogleCredentials");
  }

  private static final ReentrantLock CLIENT_INIT_LOCK = new ReentrantLock();

  private static CloudTasksClient getClient() {
    if (sharedClient == null) {
      CLIENT_INIT_LOCK.lock();
      try {
        if (sharedClient == null) {
          try {
            sharedClient = CloudTasksClient.create();
          } catch (Exception directEx) {
            logger.log(
                Level.FINE,
                "CloudTasksClient.create() failed, attempting custom credentials",
                directEx);
            try {
              CredentialsProvider credentialsProvider =
                  () -> {
                    try {
                      String token = getValidAccessToken();
                      return GoogleCredentials.create(
                          new AccessToken(
                              token, Date.from(Instant.ofEpochMilli(cachedTokenExpiry))));
                    } catch (Exception e) {
                      throw new IOException("Failed to refresh access token", e);
                    }
                  };
              CloudTasksSettings settings =
                  CloudTasksSettings.newBuilder()
                      .setCredentialsProvider(credentialsProvider)
                      .build();
              sharedClient = CloudTasksClient.create(settings);
            } catch (Exception fallbackEx) {
              logger.log(Level.SEVERE, "Failed to initialize CloudTasksClient", fallbackEx);
              throw new IllegalStateException(
                  "Failed to initialize CloudTasksClient: direct="
                      + directEx.getMessage()
                      + ", fallback="
                      + fallbackEx.getMessage(),
                  fallbackEx);
            }
          }
        }
      } finally {
        CLIENT_INIT_LOCK.unlock();
      }
    }
    return sharedClient;
  }

  /**
   * Checks whether Cloud Tasks push queue routing is enabled via environment variable.
   *
   * @return {@code true} if Cloud Tasks push queue routing is enabled; {@code false} otherwise.
   */
  static boolean isEnabled() {
    return Boolean.parseBoolean(System.getenv(ENV_APPENGINE_USE_CLOUDTASK_PUSH_QUEUE));
  }

  private static String getDefaultServiceName() {
    String serviceName = System.getenv(ENV_GAE_SERVICE);
    return !isNullOrEmpty(serviceName) ? serviceName : DEFAULT_SERVICE_NAME;
  }

  /**
   * Asynchronously enqueues one or more push tasks to Cloud Tasks or records them in Datastore if
   * transactional.
   *
   * @param queueName the short name of the target App Engine queue
   * @param txn the active Datastore transaction, or {@code null} for non-transactional enqueue
   * @param taskOptionsList the list of task options to enqueue
   * @return a {@link Future} resolving to the list of created {@link TaskHandle}s
   */
  static Future<List<TaskHandle>> addAsync(
      String queueName, Transaction txn, List<TaskOptions> taskOptionsList) {
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;

    if (txn != null) {
      return enqueueTransactional(effectiveQueue, txn, taskOptionsList);
    }

    return enqueueNonTransactional(effectiveQueue, taskOptionsList);
  }

  private static Future<List<TaskHandle>> enqueueTransactional(
      String effectiveQueue, Transaction txn, List<TaskOptions> taskOptionsList) {
    String projectId = getProjectId();
    String location = getLocation();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    List<TaskHandle> createdHandles = new ArrayList<>();
    List<Entity> transactionalEntities = new ArrayList<>();

    for (TaskOptions options : taskOptionsList) {
      String userTaskName = options.getTaskName();
      String pendingName =
          !isNullOrEmpty(userTaskName) ? userTaskName : TASK_NAME_PREFIX + UUID.randomUUID();

      String jsonPayload = buildTaskJson(parent.toString(), pendingName, options);

      Entity pendingTask = new Entity(ENTITY_KIND_PENDING_TASK);
      pendingTask.setProperty(PROPERTY_QUEUE_NAME, effectiveQueue);
      pendingTask.setProperty(PROPERTY_CLOUD_TASK_NAME, pendingName);
      pendingTask.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, jsonPayload);
      pendingTask.setProperty(PROPERTY_CREATED, Date.from(Instant.now()));
      pendingTask.setProperty(PROPERTY_STATUS, STATUS_PENDING);
      pendingTask.setProperty(PROPERTY_LOCK_EXPIRES, null);
      pendingTask.setProperty(PROPERTY_RETRY_COUNT, 0L);
      pendingTask.setProperty(PROPERTY_LAST_ERROR, "");
      pendingTask.setProperty(PROPERTY_HANDLED_BY_SWEEPER, false);
      pendingTask.setProperty(PROPERTY_SDK_LANG, SDK_LANG_JAVA);
      transactionalEntities.add(pendingTask);

      long scheduleTimeMs = calculateScheduleTimeMs(options);
      TaskOptions handleOptions = new TaskOptions(options);
      handleOptions.taskName(pendingName);
      TaskHandle handle = new TaskHandle(handleOptions, effectiveQueue);
      handle.etaUsec(scheduleTimeMs * MICROS_PER_MILLI);
      createdHandles.add(handle);
    }

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    List<Key> keys = ds.put(txn, transactionalEntities);
    List<Long> taskIds = new ArrayList<>();
    for (Key k : keys) {
      taskIds.add(k.getId());
    }

    DatastoreApiHelper.addPostCommitCallback(
        txn,
        () -> {
          TaskProcessor.processPendingTasks(taskIds, false);
        });

    return CompletableFuture.completedFuture(createdHandles);
  }

  private static Future<List<TaskHandle>> enqueueNonTransactional(
      String effectiveQueue, List<TaskOptions> taskOptionsList) {
    if (taskOptionsList == null || taskOptionsList.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }

    // Batch create via v2beta3 REST endpoint when multiple tasks are enqueued
    if (taskOptionsList.size() > 1) {
      return CompletableFuture.supplyAsync(
          () -> {
            try {
              return batchCreateTasksRest(effectiveQueue, taskOptionsList);
            } catch (Throwable t) {
              logger.log(
                  Level.WARNING,
                  "BatchCreateTasks via REST failed for queue "
                      + effectiveQueue
                      + ", falling back to individual task creation: "
                      + t.getMessage());
              return enqueueIndividualTasks(effectiveQueue, taskOptionsList);
            }
          });
    }

    // Single task creation via v2beta3 REST endpoint
    TaskOptions singleOpt = taskOptionsList.get(0);
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            TaskHandle handle = createTaskRest(effectiveQueue, singleOpt);
            return ImmutableList.of(handle);
          } catch (Throwable t) {
            throw new CompletionException(
                handleCreateTaskError(t, singleOpt.getTaskName(), effectiveQueue));
          }
        });
  }

  private static List<TaskHandle> enqueueIndividualTasks(
      String effectiveQueue, List<TaskOptions> taskOptionsList) {
    String projectId = getProjectId();
    String location = getLocation();
    String serviceName = getDefaultServiceName();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    try {
      CloudTasksClient client = getClient();
      List<CompletableFuture<TaskHandle>> taskFutures = new ArrayList<>();

      for (TaskOptions options : taskOptionsList) {
        long[] scheduleTimeHolder = new long[1];
        CreateTaskRequest req =
            buildCreateTaskRequest(
                parent,
                projectId,
                location,
                effectiveQueue,
                serviceName,
                options,
                scheduleTimeHolder);

        final long finalScheduleTimeMs = scheduleTimeHolder[0];
        final String userTaskName = options.getTaskName();

        CompletableFuture<TaskHandle> cf = new CompletableFuture<>();
        ApiFuture<Task> apiFuture = client.createTaskCallable().futureCall(req);
        apiFuture.addListener(
            () -> {
              try {
                Task createdTask = apiFuture.get();
                String chosenTaskName = TaskName.parse(createdTask.getName()).getTask();
                TaskOptions handleOptions = new TaskOptions(options);
                handleOptions.taskName(chosenTaskName);
                TaskHandle handle = new TaskHandle(handleOptions, effectiveQueue);
                handle.etaUsec(finalScheduleTimeMs * MICROS_PER_MILLI);
                cf.complete(handle);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                cf.completeExceptionally(
                    handleCreateTaskError(cause, userTaskName, effectiveQueue));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cf.completeExceptionally(handleCreateTaskError(e, userTaskName, effectiveQueue));
              } catch (RuntimeException e) {
                cf.completeExceptionally(handleCreateTaskError(e, userTaskName, effectiveQueue));
              }
            },
            directExecutor());

        taskFutures.add(cf);
      }

      Future<List<TaskHandle>> allFutures = awaitAllTaskCreationFutures(taskFutures);
      try {
        return allFutures.get();
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        if (e.getCause() instanceof RuntimeException re) {
          throw re;
        }
        throw new IllegalStateException("Failed to wait for task creation", e);
      }
    } catch (Throwable e) {
      logger.log(
          Level.WARNING,
          "CLOUDTASK: Falling back to REST for enqueueIndividualTasks: " + e.getMessage(),
          e);
      try {
        List<TaskHandle> handles = new ArrayList<>();
        for (TaskOptions opt : taskOptionsList) {
          handles.add(createTaskRest(effectiveQueue, opt));
        }
        return handles;
      } catch (Throwable restErr) {
        logger.log(
            Level.SEVERE, "CLOUDTASK: REST fallback also failed: " + restErr.getMessage(), restErr);
        if (e instanceof RuntimeException re) {
          throw re;
        }
        throw new IllegalStateException(
            "Failed to enqueue tasks in Cloud Tasks: " + e.getMessage(), e);
      }
    }
  }

  private static CreateTaskRequest buildCreateTaskRequest(
      QueueName parent,
      String projectId,
      String location,
      String effectiveQueue,
      String serviceName,
      TaskOptions options,
      long[] scheduleTimeHolder) {

    AppEngineHttpRequest httpRequest = buildAppEngineHttpRequest(serviceName, options);

    Task.Builder taskBuilder = Task.newBuilder().setAppEngineHttpRequest(httpRequest);

    String userTaskName = options.getTaskName();
    if (!isNullOrEmpty(userTaskName)) {
      taskBuilder.setName(
          TaskName.of(projectId, location, effectiveQueue, userTaskName).toString());
    }

    long scheduleTimeMs = calculateScheduleTimeMs(options);
    scheduleTimeHolder[0] = scheduleTimeMs;

    if (isDelayed(options)
        && scheduleTimeMs
            > Instant.now().plusMillis(SCHEDULE_DELAY_THRESHOLD_MILLIS).toEpochMilli()) {
      long seconds = scheduleTimeMs / MILLIS_PER_SECOND;
      int nanos = (int) ((scheduleTimeMs % MILLIS_PER_SECOND) * NANOS_PER_MILLI);
      taskBuilder.setScheduleTime(
          Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build());
    }

    return CreateTaskRequest.newBuilder()
        .setParent(parent.toString())
        .setTask(taskBuilder.build())
        .build();
  }

  private static AppEngineHttpRequest buildAppEngineHttpRequest(
      String serviceName, TaskOptions options) {
    AppEngineHttpRequest.Builder builder =
        AppEngineHttpRequest.newBuilder()
            .setRelativeUri(
                !isNullOrEmpty(options.getUrl()) ? options.getUrl() : DEFAULT_RELATIVE_URI)
            .setAppEngineRouting(AppEngineRouting.newBuilder().setService(serviceName).build());

    if (options.getMethod() != null) {
      switch (options.getMethod()) {
        case POST -> builder.setHttpMethod(HttpMethod.POST);
        case GET -> builder.setHttpMethod(HttpMethod.GET);
        case HEAD -> builder.setHttpMethod(HttpMethod.HEAD);
        case PUT -> builder.setHttpMethod(HttpMethod.PUT);
        case DELETE -> builder.setHttpMethod(HttpMethod.DELETE);
        default -> builder.setHttpMethod(HttpMethod.POST);
      }
    }

    byte[] payload = options.getPayload();
    if (payload != null && payload.length > 0) {
      builder.setBody(ByteString.copyFrom(payload));
    }

    for (Map.Entry<String, List<String>> entry : options.getHeaders().entrySet()) {
      for (String val : entry.getValue()) {
        builder.putHeaders(entry.getKey(), val);
      }
    }

    applyRetryOptions(builder, options.getRetryOptions());

    return builder.build();
  }

  private static void applyRetryOptions(
      AppEngineHttpRequest.Builder builder, RetryOptions retryOpts) {
    if (retryOpts == null) {
      return;
    }

    if (retryOpts.getTaskRetryLimit() != null) {
      builder.putHeaders(HEADER_X_TASK_RETRY_LIMIT, String.valueOf(retryOpts.getTaskRetryLimit()));
    }
    if (retryOpts.getTaskAgeLimitSeconds() != null) {
      builder.putHeaders(
          HEADER_X_TASK_AGE_LIMIT_SECONDS, String.valueOf(retryOpts.getTaskAgeLimitSeconds()));
    }
  }

  private static boolean isDelayed(TaskOptions options) {
    return options.getEtaMillis() != null || options.getCountdownMillis() != null;
  }

  private static long calculateScheduleTimeMs(TaskOptions options) {
    long scheduleTimeMs = Instant.now().toEpochMilli();
    if (options.getEtaMillis() != null) {
      scheduleTimeMs = options.getEtaMillis();
    } else if (options.getCountdownMillis() != null) {
      scheduleTimeMs += options.getCountdownMillis();
    }
    return scheduleTimeMs;
  }

  private static Throwable handleCreateTaskError(
      Throwable t, String userTaskName, String effectiveQueue) {
    String nameForErr = !isNullOrEmpty(userTaskName) ? userTaskName : "unknown";
    if (isAlreadyExists(t)) {
      return new TaskAlreadyExistsException(MSG_TASK_ALREADY_EXISTS_PREFIX + nameForErr);
    } else if (isUnknownQueue(t)) {
      return new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue, t);
    } else {
      return new RuntimeException(
          "Failed to enqueue task to Cloud Tasks via Client SDK: " + t.getMessage(), t);
    }
  }

  private static Future<List<TaskHandle>> awaitAllTaskCreationFutures(
      List<CompletableFuture<TaskHandle>> futures) {
    CompletableFuture<Void> allDone =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    try {
      allDone.join();
    } catch (CompletionException ce) {
      // Handled during individual iteration below
    }

    List<TaskHandle> createdHandles = new ArrayList<>();
    TaskAlreadyExistsException taee = null;

    for (CompletableFuture<TaskHandle> f : futures) {
      try {
        createdHandles.add(f.join());
      } catch (CompletionException ce) {
        Throwable cause = ce.getCause();
        if (cause instanceof TaskAlreadyExistsException taeeCause) {
          if (taee == null) {
            taee = taeeCause;
          } else {
            taee.appendTaskName(taeeCause.getMessage());
          }
        } else if (cause instanceof RuntimeException re) {
          throw re;
        } else {
          throw new IllegalStateException(cause);
        }
      }
    }
    if (taee != null) {
      throw taee;
    }
    return CompletableFuture.completedFuture(createdHandles);
  }

  /**
   * Asynchronously deletes one or more tasks from Cloud Tasks by task handle concurrently in
   * parallel using official Client SDK.
   *
   * @param queueName the short name of the target App Engine queue
   * @param taskHandles the list of task handles to delete
   * @return a {@link Future} resolving to a list of booleans indicating deletion success
   */
  static Future<List<Boolean>> deleteTaskAsync(
      String queueName, List<TaskHandle> taskHandles) {
    if (taskHandles == null || taskHandles.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }
    List<String> names = new ArrayList<>();
    for (TaskHandle handle : taskHandles) {
      names.add(handle.getName());
    }
    return deleteTaskByNameAsync(queueName, names);
  }

  /**
   * Asynchronously deletes one or more tasks by string task name from Cloud Tasks. Uses v2beta3
   * BatchDeleteTasks REST for multiple tasks and official Client SDK for single tasks.
   *
   * @param queueName the short name of the target App Engine queue
   * @param taskNames the list of task names to delete
   * @return a {@link Future} resolving to a list of booleans indicating deletion success
   */
  static Future<List<Boolean>> deleteTaskByNameAsync(
      String queueName, List<String> taskNames) {
    if (taskNames == null || taskNames.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;

    // Batch delete via v2beta3 REST endpoint when multiple tasks are deleted
    if (taskNames.size() > 1) {
      return CompletableFuture.supplyAsync(() -> batchDeleteTasksRest(effectiveQueue, taskNames));
    }

    // Standard single task delete via GA v2 Client SDK
    String projectId = getProjectId();
    String location = getLocation();
    try {
      CloudTasksClient client = getClient();
      String name = taskNames.get(0);
      TaskName taskName = TaskName.of(projectId, location, effectiveQueue, name);
      DeleteTaskRequest req = DeleteTaskRequest.newBuilder().setName(taskName.toString()).build();

      CompletableFuture<List<Boolean>> cf = new CompletableFuture<>();
      ApiFuture<?> apiFuture = client.deleteTaskCallable().futureCall(req);
      apiFuture.addListener(
          () -> {
            try {
              apiFuture.get();
              cf.complete(ImmutableList.of(Boolean.TRUE));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              logger.log(
                  Level.WARNING,
                  "Interrupted while deleting Cloud Task by name via Client SDK: " + taskName,
                  e);
              cf.complete(ImmutableList.of(Boolean.FALSE));
            } catch (Exception t) {
              logger.log(
                  Level.WARNING,
                  "Failed to delete Cloud Task by name via Client SDK: " + taskName,
                  t);
              cf.complete(ImmutableList.of(Boolean.FALSE));
            }
          },
          directExecutor());
      return cf;
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Falling back to REST for deleteSingleTask", e);
      return CompletableFuture.supplyAsync(
          () -> {
            try {
              return batchDeleteTasksRest(
                  effectiveQueue, ImmutableList.of(taskNames.get(0)));
            } catch (Throwable t) {
              return ImmutableList.of(Boolean.FALSE);
            }
          });
    }
  }

  /**
   * Asynchronously fetches statistics for the specified queue from Cloud Tasks using official
   * Client SDK.
   *
   * @param queueName the short name of the queue to fetch statistics for
   * @return a {@link Future} resolving to the {@link QueueStatistics}
   */
  static Future<QueueStatistics> fetchStatisticsAsync(String queueName) {
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;
    String projectId = getProjectId();
    String location = getLocation();

    CompletableFuture<QueueStatistics> cf = new CompletableFuture<>();
    CompletableFuture<?> unusedStats =
        CompletableFuture.runAsync(
            () -> {
              int tasksCount = 0;
              long oldestEtaUsec = 0;
              int executedLastMinute = 0;
              int requestsInFlight = 0;
              double enforcedRate = 0.0;

              try {
                CloudTasksClient client = getClient();
                Queue queue =
                    client.getQueue(QueueName.of(projectId, location, effectiveQueue).toString());
                if (queue != null && queue.hasRateLimits()) {
                  enforcedRate = queue.getRateLimits().getMaxDispatchesPerSecond();
                }
              } catch (Throwable t) {
                logger.log(
                    Level.FINE,
                    "Failed to get queue rate limits via Client SDK for " + effectiveQueue,
                    t);
              }

              try {
                String token = getValidAccessToken();
                String restUrl =
                    "https://cloudtasks.googleapis.com/v2beta3/projects/"
                        + projectId
                        + "/locations/"
                        + location
                        + "/queues/"
                        + effectiveQueue
                        + "?readMask=stats";
                URL url = URI.create(restUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(HTTP_METHOD_GET);
                conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
                conn.setConnectTimeout(STATS_REQUEST_TIMEOUT_MILLIS);
                conn.setReadTimeout(STATS_REQUEST_TIMEOUT_MILLIS);
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                  try (InputStream is = conn.getInputStream()) {
                    String resp = new String(is.readAllBytes(), UTF_8);
                    JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
                    if (obj.has(JSON_FIELD_STATS)) {
                      JsonObject statsJson = obj.getAsJsonObject(JSON_FIELD_STATS);
                      if (statsJson.has(JSON_FIELD_TASKS_COUNT)) {
                        tasksCount = statsJson.get(JSON_FIELD_TASKS_COUNT).getAsInt();
                      }
                      if (statsJson.has(JSON_FIELD_OLDEST_ESTIMATED_ARRIVAL_TIME)) {
                        String isoTime =
                            statsJson.get(JSON_FIELD_OLDEST_ESTIMATED_ARRIVAL_TIME).getAsString();
                        Instant instant = Instant.parse(isoTime);
                        oldestEtaUsec =
                            instant.getEpochSecond() * MICROS_PER_SECOND
                                + instant.getNano() / NANOS_PER_MICRO;
                      }
                      if (statsJson.has(JSON_FIELD_EXECUTED_LAST_MINUTE_COUNT)) {
                        executedLastMinute =
                            statsJson.get(JSON_FIELD_EXECUTED_LAST_MINUTE_COUNT).getAsInt();
                      }
                      if (statsJson.has(JSON_FIELD_CONCURRENT_DISPATCHES_COUNT)) {
                        requestsInFlight =
                            statsJson.get(JSON_FIELD_CONCURRENT_DISPATCHES_COUNT).getAsInt();
                      }
                      if (statsJson.has(JSON_FIELD_EFFECTIVE_EXECUTION_RATE)) {
                        enforcedRate =
                            statsJson.get(JSON_FIELD_EFFECTIVE_EXECUTION_RATE).getAsDouble();
                      }
                    }
                  }
                }
              } catch (Throwable t) {
                logger.log(
                    Level.FINE,
                    "Failed to fetch stats for queue " + effectiveQueue + " via REST endpoint",
                    t);
              }

              TaskQueueFetchQueueStatsResponse.QueueStats.Builder legacyStatsBuilder =
                  TaskQueueFetchQueueStatsResponse.QueueStats.newBuilder()
                      .setNumTasks(tasksCount)
                      .setOldestEtaUsec(oldestEtaUsec);

              TaskQueueScannerQueueInfo scannerInfo =
                  TaskQueueScannerQueueInfo.newBuilder()
                      .setExecutedLastMinute(executedLastMinute)
                      .setRequestsInFlight(requestsInFlight)
                      .setEnforcedRate(enforcedRate)
                      .build();
              legacyStatsBuilder.setScannerInfo(scannerInfo);

              QueueStatistics stats =
                  new QueueStatistics(effectiveQueue, legacyStatsBuilder.build());
              cf.complete(stats);
            });
    return cf;
  }

  /**
   * Purges all tasks from the specified queue using the official CloudTasksClient SDK.
   *
   * @param queueName the short name of the queue to purge
   */
  static void purge(String queueName) {
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;
    String projectId = getProjectId();
    String location = getLocation();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    try {
      CloudTasksClient client = getClient();
      var unused = client.purgeQueue(parent);
    } catch (RuntimeException e) {
      logger.log(
          Level.SEVERE,
          "CLOUDTASK: Failed to purge queue "
              + effectiveQueue
              + " via Client SDK: "
              + e.getMessage(),
          e);
      throw new IllegalStateException(MSG_CLOUDTASK_PURGE_FAILED, e);
    }
  }

  private static boolean isAlreadyExists(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof AlreadyExistsException) {
        return true;
      }
      if (curr instanceof ApiException apiException
          && apiException.getStatusCode().getCode() == StatusCode.Code.ALREADY_EXISTS) {
        return true;
      }
      String clsName = curr.getClass().getName();
      String msg = curr.getMessage();
      if (clsName.contains(MARKER_ALREADY_EXISTS)
          || (msg != null
              && (msg.contains(MARKER_ALREADY_EXISTS_UPPER)
                  || msg.contains(MARKER_EXISTED_TOO_RECENTLY)))) {
        return true;
      }
      curr = curr.getCause();
    }
    return false;
  }

  private static boolean isUnknownQueue(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof NotFoundException) {
        return true;
      }
      if (curr instanceof ApiException apiException
          && apiException.getStatusCode().getCode() == StatusCode.Code.NOT_FOUND) {
        return true;
      }
      String clsName = curr.getClass().getName();
      String msg = curr.getMessage();
      if (clsName.contains(MARKER_NOT_FOUND)
          || (msg != null
              && (msg.contains(MARKER_QUEUE_DOES_NOT_EXIST)
                  || msg.contains(MARKER_QUEUE_NOT_FOUND)
                  || msg.contains(MARKER_NOT_FOUND_UPPER)))) {
        return true;
      }
      curr = curr.getCause();
    }
    return false;
  }

  private static JsonObject buildTaskJsonObject(
      String fullQueueName, String taskName, TaskOptions options) {
    String serviceName = getDefaultServiceName();
    JsonObject taskJson = new JsonObject();
    if (!isNullOrEmpty(taskName)) {
      taskJson.addProperty(JSON_FIELD_NAME, fullQueueName + "/tasks/" + taskName);
    }

    JsonObject httpJson = new JsonObject();
    JsonObject routingJson = new JsonObject();
    routingJson.addProperty(JSON_FIELD_SERVICE, serviceName);
    httpJson.add(JSON_FIELD_APP_ENGINE_ROUTING, routingJson);

    if (options.getMethod() != null) {
      httpJson.addProperty(JSON_FIELD_HTTP_METHOD, options.getMethod().name());
    }

    String relativeUrl = options.getUrl();
    if (isNullOrEmpty(relativeUrl)) {
      relativeUrl = DEFAULT_RELATIVE_URI;
    }
    httpJson.addProperty(JSON_FIELD_RELATIVE_URI, relativeUrl);

    byte[] payload = options.getPayload();
    if (payload != null && payload.length > 0) {
      httpJson.addProperty(JSON_FIELD_BODY, Base64.getEncoder().encodeToString(payload));
    }

    JsonObject headersJson = new JsonObject();
    for (Map.Entry<String, List<String>> entry : options.getHeaders().entrySet()) {
      for (String val : entry.getValue()) {
        headersJson.addProperty(entry.getKey(), val);
      }
    }

    if (options.getRetryOptions() != null) {
      RetryOptions ro = options.getRetryOptions();
      if (ro.getTaskRetryLimit() != null) {
        headersJson.addProperty(HEADER_X_TASK_RETRY_LIMIT, String.valueOf(ro.getTaskRetryLimit()));
      }
      if (ro.getTaskAgeLimitSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_AGE_LIMIT_SECONDS, String.valueOf(ro.getTaskAgeLimitSeconds()));
      }
      if (ro.getMinBackoffSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_MIN_BACKOFF_SECONDS, String.valueOf(ro.getMinBackoffSeconds()));
      }
      if (ro.getMaxBackoffSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_MAX_BACKOFF_SECONDS, String.valueOf(ro.getMaxBackoffSeconds()));
      }
      if (ro.getMaxDoublings() != null) {
        headersJson.addProperty(HEADER_X_TASK_MAX_DOUBLINGS, String.valueOf(ro.getMaxDoublings()));
      }
    }
    httpJson.add(JSON_FIELD_HEADERS, headersJson);
    taskJson.add(JSON_FIELD_APP_ENGINE_HTTP_REQUEST, httpJson);

    long etaMillis = calculateScheduleTimeMs(options);
    if (etaMillis
        > Instant.now().plusMillis(REST_SCHEDULE_DELAY_THRESHOLD_MILLIS).toEpochMilli()) {
      taskJson.addProperty(JSON_FIELD_SCHEDULE_TIME, Instant.ofEpochMilli(etaMillis).toString());
    }

    if (options.getRetryOptions() != null) {
      RetryOptions ro = options.getRetryOptions();
      JsonObject retryConfig = new JsonObject();
      if (ro.getTaskRetryLimit() != null) {
        retryConfig.addProperty(JSON_FIELD_MAX_ATTEMPTS, ro.getTaskRetryLimit() + 1);
      }
      if (ro.getTaskAgeLimitSeconds() != null) {
        retryConfig.addProperty(JSON_FIELD_MAX_RETRY_DURATION, ro.getTaskAgeLimitSeconds() + "s");
      }
      if (ro.getMinBackoffSeconds() != null) {
        retryConfig.addProperty(JSON_FIELD_MIN_BACKOFF, ro.getMinBackoffSeconds() + "s");
      }
      if (ro.getMaxBackoffSeconds() != null) {
        retryConfig.addProperty(JSON_FIELD_MAX_BACKOFF, ro.getMaxBackoffSeconds() + "s");
      }
      if (ro.getMaxDoublings() != null) {
        retryConfig.addProperty(JSON_FIELD_MAX_DOUBLINGS, ro.getMaxDoublings());
      }
      if (retryConfig.size() > 0) {
        taskJson.add(JSON_FIELD_RETRY_CONFIG, retryConfig);
      }
    }

    return taskJson;
  }

  private static String buildTaskJson(String fullQueueName, String taskName, TaskOptions options) {
    JsonObject root = new JsonObject();
    root.add(JSON_FIELD_TASK, buildTaskJsonObject(fullQueueName, taskName, options));
    return root.toString();
  }

  private static TaskHandle createTaskRest(String effectiveQueue, TaskOptions options)
      throws Exception {
    String projectId = getProjectId();
    String location = getLocation();
    String parentQueue =
        "projects/" + projectId + "/locations/" + location + "/queues/" + effectiveQueue;
    String urlStr = "https://cloudtasks.googleapis.com/v2beta3/" + parentQueue + "/tasks";

    JsonObject rootJson = new JsonObject();
    JsonObject taskObj = buildTaskJsonObject(parentQueue, options.getTaskName(), options);
    rootJson.add(JSON_FIELD_TASK, taskObj);

    String token = getValidAccessToken();
    URL url = URI.create(urlStr).toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(HTTP_METHOD_POST);
    conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
    conn.setRequestProperty(HEADER_CONTENT_TYPE, VALUE_APPLICATION_JSON);
    conn.setDoOutput(true);
    conn.setConnectTimeout(REST_REQUEST_TIMEOUT_MILLIS);
    conn.setReadTimeout(REST_REQUEST_TIMEOUT_MILLIS);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(rootJson.toString().getBytes(UTF_8));
    }

    int respCode = conn.getResponseCode();
    if (respCode >= HttpURLConnection.HTTP_OK && respCode < HttpURLConnection.HTTP_MULT_CHOICE) {
      try (InputStream is = conn.getInputStream()) {
        String resp = new String(is.readAllBytes(), UTF_8);
        JsonObject respJson = JsonParser.parseString(resp).getAsJsonObject();
        String assignedName = options.getTaskName();
        if (respJson.has(JSON_FIELD_NAME)) {
          String fullName = respJson.get(JSON_FIELD_NAME).getAsString();
          assignedName = fullName.substring(fullName.lastIndexOf('/') + 1);
        }
        TaskOptions handleOpts = new TaskOptions(options);
        if (!isNullOrEmpty(assignedName)) {
          handleOpts.taskName(assignedName);
        }
        TaskHandle handle = new TaskHandle(handleOpts, effectiveQueue);
        handle.etaUsec(calculateScheduleTimeMs(options) * MICROS_PER_MILLI);
        return handle;
      }
    } else {
      String err;
      try (InputStream es = conn.getErrorStream()) {
        err = es != null ? new String(es.readAllBytes(), UTF_8) : "HTTP " + respCode;
      }
      throw new IllegalStateException(
          "CreateTask via REST failed with code " + respCode + ": " + err);
    }
  }

  private static List<TaskHandle> batchCreateTasksRest(
      String effectiveQueue, List<TaskOptions> taskOptionsList) throws Exception {
    String projectId = getProjectId();
    String location = getLocation();
    String parentQueue =
        "projects/" + projectId + "/locations/" + location + "/queues/" + effectiveQueue;
    String urlStr =
        "https://cloudtasks.googleapis.com/v2beta3/" + parentQueue + "/tasks:batchCreate";

    JsonObject rootJson = new JsonObject();
    JsonArray requestsArray = new JsonArray();

    List<Long> scheduleTimes = new ArrayList<>();
    for (TaskOptions options : taskOptionsList) {
      String userTaskName = options.getTaskName();
      JsonObject taskObj = buildTaskJsonObject(parentQueue, userTaskName, options);
      JsonObject reqObj = new JsonObject();
      reqObj.add(JSON_FIELD_TASK, taskObj);
      requestsArray.add(reqObj);
      scheduleTimes.add(calculateScheduleTimeMs(options));
    }
    rootJson.add(JSON_FIELD_REQUESTS, requestsArray);

    String token = getValidAccessToken();
    URL url = URI.create(urlStr).toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(HTTP_METHOD_POST);
    conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
    conn.setRequestProperty(HEADER_CONTENT_TYPE, VALUE_APPLICATION_JSON);
    conn.setDoOutput(true);
    conn.setConnectTimeout(BATCH_REQUEST_TIMEOUT_MILLIS);
    conn.setReadTimeout(BATCH_REQUEST_TIMEOUT_MILLIS);

    try (OutputStream os = conn.getOutputStream()) {
      os.write(rootJson.toString().getBytes(UTF_8));
    }

    int respCode = conn.getResponseCode();
    if (respCode >= HttpURLConnection.HTTP_OK && respCode < HttpURLConnection.HTTP_MULT_CHOICE) {
      try (InputStream is = conn.getInputStream()) {
        String resp = new String(is.readAllBytes(), UTF_8);
        JsonObject respJson = JsonParser.parseString(resp).getAsJsonObject();
        JsonArray tasksArray = null;
        if (respJson.has(JSON_FIELD_RESPONSE)) {
          JsonObject responseObj = respJson.getAsJsonObject(JSON_FIELD_RESPONSE);
          if (responseObj.has(JSON_FIELD_TASKS)) {
            tasksArray = responseObj.getAsJsonArray(JSON_FIELD_TASKS);
          }
        } else if (respJson.has(JSON_FIELD_TASKS)) {
          tasksArray = respJson.getAsJsonArray(JSON_FIELD_TASKS);
        }

        List<TaskHandle> handles = new ArrayList<>();
        for (int i = 0; i < taskOptionsList.size(); i++) {
          TaskOptions opts = taskOptionsList.get(i);
          String assignedName = opts.getTaskName();
          if (tasksArray != null && i < tasksArray.size()) {
            JsonObject tObj = tasksArray.get(i).getAsJsonObject();
            if (tObj.has(JSON_FIELD_NAME)) {
              String fullName = tObj.get(JSON_FIELD_NAME).getAsString();
              assignedName = fullName.substring(fullName.lastIndexOf('/') + 1);
            }
          }
          TaskOptions handleOpts = new TaskOptions(opts);
          if (!isNullOrEmpty(assignedName)) {
            handleOpts.taskName(assignedName);
          }
          TaskHandle handle = new TaskHandle(handleOpts, effectiveQueue);
          handle.etaUsec(scheduleTimes.get(i) * MICROS_PER_MILLI);
          handles.add(handle);
        }
        return handles;
      }
    } else {
      String err;
      try (InputStream es = conn.getErrorStream()) {
        err = es != null ? new String(es.readAllBytes(), UTF_8) : "HTTP " + respCode;
      }
      throw new IllegalStateException(
          "BatchCreateTasks via REST failed with code " + respCode + ": " + err);
    }
  }

  private static List<Boolean> batchDeleteTasksRest(String effectiveQueue, List<String> taskNames) {
    String projectId = getProjectId();
    String location = getLocation();
    String parentQueue =
        "projects/" + projectId + "/locations/" + location + "/queues/" + effectiveQueue;
    String urlStr =
        "https://cloudtasks.googleapis.com/v2beta3/" + parentQueue + "/tasks:batchDelete";

    try {
      JsonObject rootJson = new JsonObject();
      JsonArray namesArray = new JsonArray();
      for (String name : taskNames) {
        namesArray.add(parentQueue + "/tasks/" + name);
      }
      rootJson.add(JSON_FIELD_NAMES, namesArray);

      String token = getValidAccessToken();
      URL url = URI.create(urlStr).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod(HTTP_METHOD_POST);
      conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
      conn.setRequestProperty(HEADER_CONTENT_TYPE, VALUE_APPLICATION_JSON);
      conn.setDoOutput(true);
      conn.setConnectTimeout(BATCH_REQUEST_TIMEOUT_MILLIS);
      conn.setReadTimeout(BATCH_REQUEST_TIMEOUT_MILLIS);

      try (OutputStream os = conn.getOutputStream()) {
        os.write(rootJson.toString().getBytes(UTF_8));
      }

      int respCode = conn.getResponseCode();
      List<Boolean> results = new ArrayList<>();
      boolean success =
          (respCode >= HttpURLConnection.HTTP_OK && respCode < HttpURLConnection.HTTP_MULT_CHOICE);
      results.addAll(Collections.nCopies(taskNames.size(), success));
      return results;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed batch delete via REST: " + e.getMessage(), e);
      List<Boolean> results = new ArrayList<>();
      results.addAll(Collections.nCopies(taskNames.size(), Boolean.FALSE));
      return results;
    }
  }

  /**
   * Executes a task force-run / retry via official Client SDK.
   *
   * @param queueName the queue name
   * @param taskName the task name
   * @return a {@link Future} resolving to true if successful
   */
  static Future<Boolean> runTaskAsync(String queueName, String taskName) {
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;
    String projectId = getProjectId();
    String location = getLocation();

    try {
      CloudTasksClient client = getClient();
      TaskName fullName = TaskName.of(projectId, location, effectiveQueue, taskName);
      RunTaskRequest req = RunTaskRequest.newBuilder().setName(fullName.toString()).build();

      CompletableFuture<Boolean> cf = new CompletableFuture<>();
      ApiFuture<Task> apiFuture = client.runTaskCallable().futureCall(req);
      apiFuture.addListener(
          () -> {
            try {
              apiFuture.get();
              cf.complete(Boolean.TRUE);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              logger.log(
                  Level.WARNING,
                  "Interrupted while running/retrying task via Client SDK: " + taskName,
                  e);
              cf.complete(Boolean.FALSE);
            } catch (Exception t) {
              logger.log(Level.WARNING, "Failed to run/retry task via Client SDK: " + taskName, t);
              cf.complete(Boolean.FALSE);
            }
          },
          directExecutor());
      return cf;
    } catch (RuntimeException e) {
      logger.log(
          Level.WARNING, "Failed to initialize CloudTasksClient for runTask: " + taskName, e);
      return CompletableFuture.completedFuture(Boolean.FALSE);
    }
  }
}
