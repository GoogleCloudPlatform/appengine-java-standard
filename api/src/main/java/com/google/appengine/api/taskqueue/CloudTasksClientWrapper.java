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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.StatusCode;
import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.GetAccessTokenResult;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.datastore.DatastoreApiHelper;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueFetchQueueStatsResponse;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueScannerQueueInfo;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.location.ListLocationsRequest;
import com.google.cloud.location.ListLocationsResponse;
import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.AppEngineRouting;
import com.google.cloud.tasks.v2.BatchCreateTasksMetadata;
import com.google.cloud.tasks.v2.BatchCreateTasksRequest;
import com.google.cloud.tasks.v2.BatchCreateTasksResponse;
import com.google.cloud.tasks.v2.BatchDeleteTasksMetadata;
import com.google.cloud.tasks.v2.BatchDeleteTasksRequest;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CloudTasksSettings;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.DeleteTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.RetryConfig;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.TaskName;
import com.google.cloud.tasks.v2beta3.GetQueueRequest;
import com.google.cloud.tasks.v2beta3.Queue;
import com.google.cloud.tasks.v2beta3.QueueStats;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.longrunning.Operation;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Clean Java client wrapper for Google Cloud Tasks v2 API operations using the official {@link
 * CloudTasksClient} SDK.
 *
 * <p>This class provides method-level integration between the legacy App Engine Task Queue API
 * ({@link QueueImpl}) and Google Cloud Tasks when the environment variable {@code
 * APPENGINE_USE_CLOUDTASK_PUSH_QUEUE} is set to {@code true}.
 */
final class CloudTasksClientWrapper {

  private static final Logger logger = Logger.getLogger(CloudTasksClientWrapper.class.getName());

  // Environment Variables & System Properties
  static final String ENV_GAE_SERVICE = "GAE_SERVICE";
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
  static final String PROP_MTLS_ENABLED = "com.google.cloud.mtls.enabled";

  // Defaults & Prefixes
  static final String DEFAULT_METADATA_BASE_URL = "http://metadata.google.internal";
  static final String DEFAULT_LOCATION = "us-central1";
  static final String DEFAULT_QUEUE_NAME = "default";
  static final String DEFAULT_SERVICE_NAME = "default";
  static final String DEFAULT_RELATIVE_URI = "/";
  static final String TASK_NAME_PREFIX = "task-";
  static final String SDK_LANG_JAVA = "JAVA";

  // OAuth2 Scopes & Metadata Paths
  static final String SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";
  static final String SCOPE_CLOUDTASKS = "https://www.googleapis.com/auth/cloudtasks";
  static final String METADATA_TOKEN_PATH =
      "/computeMetadata/v1/instance/service-accounts/default/token";
  static final String METADATA_REGION_PATH = "/computeMetadata/v1/instance/region";
  static final String METADATA_ZONE_PATH = "/computeMetadata/v1/instance/zone";

  // HTTP Headers & Methods
  static final String HEADER_CONTENT_TYPE = "Content-Type";
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
  static final String JSON_FIELD_ACCESS_TOKEN = "access_token";
  static final String JSON_FIELD_EXPIRES_IN = "expires_in";
  static final String JSON_FIELD_TASK = "task";
  static final String JSON_FIELD_NAME = "name";
  static final String JSON_FIELD_APP_ENGINE_HTTP_REQUEST = "appEngineHttpRequest";
  static final String JSON_FIELD_APP_ENGINE_ROUTING = "appEngineRouting";
  static final String JSON_FIELD_SERVICE = "service";
  static final String JSON_FIELD_VERSION = "version";
  static final String JSON_FIELD_INSTANCE = "instance";
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
  static final String MARKER_INVALID_ARGUMENT = "INVALID_ARGUMENT";

  // Datastore Entity Kind & Properties
  static final String ENTITY_KIND_PENDING_TASK = "_AE_PendingCloudTask";
  static final String ENTITY_KIND_PENDING_TASK_BATCH = "_AE_PendingCloudTaskBatch";
  static final String PROPERTY_QUEUE_NAME = "queue_name";
  static final String PROPERTY_CLOUD_TASK_NAME = "cloud_task_name";
  static final String PROPERTY_CLOUD_TASK_PAYLOAD = "cloud_task_payload";
  static final String PROPERTY_CREATED = "created";
  static final String PROPERTY_STATUS = "status";
  static final String PROPERTY_LOCK_EXPIRES = "lock_expires";
  static final String PROPERTY_RETRY_COUNT = "retry_count";
  static final String PROPERTY_LAST_ERROR = "last_error";
  static final String PROPERTY_HANDLED_BY_SWEEPER = "handled_by_sweeper";
  static final String PROPERTY_SDK_LANG = "sdk_lang";

  // Task Statuses
  static final String STATUS_PENDING = "PENDING";
  static final String STATUS_PROCESSING = "PROCESSING";
  static final String STATUS_DONE = "DONE";
  static final String STATUS_FAILED = "FAILED";
  static final String STATUS_ALREADY_EXISTS = "ALREADY_EXISTS";

  // Timeouts & Durations (in milliseconds)
  static final int METADATA_TIMEOUT_MILLIS = 2000;
  static final int TOKEN_REQUEST_TIMEOUT_MILLIS = 5000;
  static final long SCHEDULE_DELAY_THRESHOLD_MILLIS = 100L;
  static final long REST_SCHEDULE_DELAY_THRESHOLD_MILLIS = 1000L;
  static final long TOKEN_EXPIRY_BUFFER_MILLIS = 60_000L;
  static final long DEFAULT_TOKEN_EXPIRY_MILLIS = 3_600_000L;
  static final long DEFAULT_TOKEN_EXPIRES_IN_SECONDS = 3600L;

  // Time Unit Multipliers & Conversions
  static final long MICROS_PER_MILLI = 1000L;
  static final long MILLIS_PER_SECOND = 1000L;
  static final long MICROS_PER_SECOND = 1_000_000L;
  static final long NANOS_PER_MICRO = 1000L;
  static final long NANOS_PER_MILLI = 1_000_000L;

  private static final AtomicLong threadCounter = new AtomicLong();
  static final int IO_EXECUTOR_MAX_THREADS = 32;
  static final long IO_EXECUTOR_KEEP_ALIVE_SECONDS = 60L;

  /**
   * Bounded daemon thread pool for blocking Cloud Tasks RPC calls so we neither starve {@link
   * java.util.concurrent.ForkJoinPool#commonPool()} on single-core App Engine instances nor spawn
   * unbounded threads under load spikes.
   */
  static final ExecutorService IO_EXECUTOR = createBoundedIoExecutor();

  private static ExecutorService createBoundedIoExecutor() {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            IO_EXECUTOR_MAX_THREADS,
            IO_EXECUTOR_MAX_THREADS,
            IO_EXECUTOR_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
              Thread t = Executors.defaultThreadFactory().newThread(r);
              t.setDaemon(true);
              t.setName(
                  "cloudtasks-async-io-" + threadCounter.incrementAndGet()); // NOMUTANTS -- naming
              return t;
            });
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  private static final ReentrantLock CLIENT_INIT_LOCK = new ReentrantLock();
  private static final ReentrantLock TOKEN_LOCK = new ReentrantLock();

  private static volatile CloudTasksClient sharedClient;
  private static volatile com.google.cloud.tasks.v2beta3.CloudTasksClient sharedV2Beta3Client;
  private static volatile String metadataBaseUrl = DEFAULT_METADATA_BASE_URL;
  private static volatile GoogleCredentials credentialsOverride;
  private static volatile String projectIdOverride;
  private static volatile String cachedLocation;
  private static volatile String cachedToken;
  private static volatile long cachedTokenExpiry;

  static {
    System.setProperty(PROP_MTLS_ENABLED, "false");
  }

  private CloudTasksClientWrapper() {}

  static void setEnabledForTesting(@Nullable Boolean enabled) {
    QueueImpl.setCloudTaskEnabledForTesting(enabled);
  }

  static void setSharedClientForTesting(CloudTasksClient client) {
    sharedClient = client;
  }

  static void setSharedV2Beta3ClientForTesting(
      com.google.cloud.tasks.v2beta3.CloudTasksClient client) {
    sharedV2Beta3Client = client;
  }

  static void setMetadataBaseUrlForTesting(String baseUrl) {
    metadataBaseUrl = !isNullOrEmpty(baseUrl) ? baseUrl : DEFAULT_METADATA_BASE_URL;
  }

  static void setCredentialsTokenForTesting(String tokenValue) {
    credentialsOverride =
        !isNullOrEmpty(tokenValue)
            ? GoogleCredentials.create(new AccessToken(tokenValue, null))
            : null;
  }

  static void setProjectIdForTesting(String projectId) {
    projectIdOverride = projectId;
  }

  static void setLocationForTesting(String location) {
    cachedLocation = location;
  }

  static void setAccessTokenForTesting(String token, long expiryEpochMillis) {
    cachedToken = token;
    cachedTokenExpiry = expiryEpochMillis;
  }

  static <T> CompletableFuture<T> supplyAsyncWithEnv(Supplier<T> supplier) {
    Environment env = ApiProxy.getCurrentEnvironment();
    return CompletableFuture.supplyAsync(
        () -> {
          Environment previous = ApiProxy.getCurrentEnvironment();
          if (env != null) { // NOMUTANTS -- null guard
            ApiProxy.setEnvironmentForCurrentThread(env);
          }
          try {
            return supplier.get();
          } finally {
            if (env != null) { // NOMUTANTS -- thread-local cleanup
              ApiProxy.setEnvironmentForCurrentThread(previous); // NOMUTANTS -- cleanup
            }
          }
        },
        IO_EXECUTOR);
  }

  static CompletableFuture<Void> runAsyncWithEnv(Runnable runnable) {
    Environment env = ApiProxy.getCurrentEnvironment();
    return CompletableFuture.runAsync(
        () -> {
          Environment previous = ApiProxy.getCurrentEnvironment();
          if (env != null) { // NOMUTANTS -- null guard
            ApiProxy.setEnvironmentForCurrentThread(env);
          }
          try {
            runnable.run();
          } finally {
            if (env != null) { // NOMUTANTS -- thread-local cleanup
              ApiProxy.setEnvironmentForCurrentThread(previous); // NOMUTANTS -- cleanup
            }
          }
        },
        IO_EXECUTOR);
  }

  /**
   * Resolves the current Google Cloud Platform project ID from the App Engine runtime environment.
   *
   * @return the GCP project ID string
   */
  static String getProjectId() {
    if (!isNullOrEmpty(projectIdOverride)) {
      return projectIdOverride;
    }
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    String projectId =
        System.getenv(ENV_GOOGLE_CLOUD_PROJECT); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(projectId)) {
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_APPLICATION); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(projectId)) {
      if (projectId.contains("~")) {
        return projectId.substring(projectId.indexOf("~") + 1);
      }
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_LONG_APP_ID); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(projectId)) {
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
   * properties, the GCP instance metadata server, or Cloud Tasks {@code ListLocations} API.
   *
   * @return the GCP region ID (e.g. {@code "us-central1"}, {@code "us-east1"})
   */
  static String getLocation() {
    if (cachedLocation != null) {
      return cachedLocation;
    }
    String zone = System.getenv(ENV_GAE_ZONE); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(zone)) {
      int lastDash = zone.lastIndexOf('-');
      if (lastDash > 0) {
        cachedLocation = zone.substring(0, lastDash);
        return cachedLocation;
      }
      cachedLocation = zone;
      return cachedLocation;
    }
    String location = System.getenv(ENV_LOCATION_ID); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_LOCATION); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_REGION); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GOOGLE_CLOUD_REGION); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getProperty(PROP_GAE_LOCATION);
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    try {
      URL url = URI.create(metadataBaseUrl + METADATA_REGION_PATH).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
      conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
      conn.setReadTimeout(METADATA_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8))) {
          String regionPath = reader.readLine();
          if (regionPath != null) { // NOMUTANTS -- defensive null guard
            String parsed = regionPath.substring(regionPath.lastIndexOf('/') + 1).trim();
            if (!parsed.isEmpty()) { // NOMUTANTS -- defensive empty guard
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
      URL url = URI.create(metadataBaseUrl + METADATA_ZONE_PATH).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
      conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
      conn.setReadTimeout(METADATA_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8))) {
          String zonePath = reader.readLine();
          if (zonePath != null) { // NOMUTANTS -- defensive null guard
            String parsed = zonePath.substring(zonePath.lastIndexOf('/') + 1).trim();
            int lastDash = parsed.lastIndexOf('-');
            if (lastDash > 0) { // NOMUTANTS -- defensive format guard
              cachedLocation = parsed.substring(0, lastDash);
              return cachedLocation;
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Ignore metadata failure
    }
    try {
      String projId = getProjectId();
      if (!isNullOrEmpty(projId)) { // NOMUTANTS -- defensive guard
        ListLocationsResponse resp =
            getClient()
                .listLocationsCallable()
                .call(ListLocationsRequest.newBuilder().setName("projects/" + projId).build());
        if (resp != null && resp.getLocationsCount() > 0) { // NOMUTANTS -- defensive guard
          String locId = resp.getLocations(0).getLocationId();
          if (!isNullOrEmpty(locId)) { // NOMUTANTS -- defensive guard
            cachedLocation = locId;
            return cachedLocation;
          }
        }
      }
    } catch (RuntimeException ignored) {
      // Ignore locations API failure
    }
    String localRegion = System.getenv(ENV_LOCAL_GCP_REGION); // NOMUTANTS -- fixed in the test JVM
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    return !isNullOrEmpty(localRegion) ? localRegion : DEFAULT_LOCATION;
  }

  /**
   * Acquires a valid OAuth2 access token from {@link AppIdentityService}, the GCP metadata server,
   * or Application Default Credentials.
   *
   * @return the OAuth2 bearer access token
   * @throws IOException if no access token can be obtained
   */
  static String getValidAccessToken() throws IOException {
    long now = Instant.now().toEpochMilli();
    // NOMUTANTS -- double-checked locking fast-path; re-checked under TOKEN_LOCK
    if (cachedToken != null && now < cachedTokenExpiry - TOKEN_EXPIRY_BUFFER_MILLIS) {
      return cachedToken;
    }

    TOKEN_LOCK.lock();
    try {
      now = Instant.now().toEpochMilli(); // NOMUTANTS -- double-checked locking timestamp refresh
      // NOMUTANTS -- double-checked locking re-check under TOKEN_LOCK
      if (cachedToken != null && now < cachedTokenExpiry - TOKEN_EXPIRY_BUFFER_MILLIS) {
        return cachedToken;
      }

      try {
        AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();
        GetAccessTokenResult tokenResult =
            appIdentityService.getAccessToken(ImmutableList.of(SCOPE_CLOUD_PLATFORM));
        // NOMUTANTS -- AppIdentityService stub requires runtime service delegate
        if (tokenResult != null && tokenResult.getAccessToken() != null) { // NOMUTANTS
          cachedToken = tokenResult.getAccessToken(); // NOMUTANTS
          // NOMUTANTS -- fallback expiry calculation
          cachedTokenExpiry =
              tokenResult.getExpirationTime() != null
                  ? tokenResult.getExpirationTime().toInstant().toEpochMilli()
                  : (now + DEFAULT_TOKEN_EXPIRY_MILLIS);
          return cachedToken; // NOMUTANTS
        }
      } catch (Throwable ignored) {
        logger.log(Level.FINE, "Could not acquire token from AppIdentityService", ignored);
      }

      try {
        URL url = URI.create(metadataBaseUrl + METADATA_TOKEN_PATH).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
          conn.setRequestMethod(HTTP_METHOD_GET); // NOMUTANTS -- GET is default method
          conn.setRequestProperty(HEADER_METADATA_FLAVOR, VALUE_METADATA_FLAVOR_GOOGLE);
          conn.setConnectTimeout(TOKEN_REQUEST_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
          conn.setReadTimeout(TOKEN_REQUEST_TIMEOUT_MILLIS); // NOMUTANTS -- timeout config
          if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (InputStream is = conn.getInputStream()) {
              String resp = new String(is.readAllBytes(), UTF_8);
              JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
              if (obj.has(JSON_FIELD_ACCESS_TOKEN)) { // NOMUTANTS -- defensive JSON guard
                String token = obj.get(JSON_FIELD_ACCESS_TOKEN).getAsString();
                // NOMUTANTS -- fallback expiry calculation
                long expiresIn =
                    obj.has(JSON_FIELD_EXPIRES_IN)
                        ? obj.get(JSON_FIELD_EXPIRES_IN).getAsLong()
                        : DEFAULT_TOKEN_EXPIRES_IN_SECONDS;
                cachedToken = token;
                cachedTokenExpiry = now + (expiresIn * MILLIS_PER_SECOND); // NOMUTANTS -- expiry
                return token;
              }
            }
          }
        } finally {
          conn.disconnect(); // NOMUTANTS -- resource cleanup
        }
      } catch (Exception e) {
        logger.log(
            Level.WARNING, "Failed to get access token from Metadata server: " + e.getMessage());
      }

      try {
        GoogleCredentials creds =
            credentialsOverride != null
                ? credentialsOverride
                : GoogleCredentials.getApplicationDefault();
        if (creds.createScopedRequired()) { // NOMUTANTS -- ADC scope check
          creds = creds.createScoped(ImmutableList.of(SCOPE_CLOUD_PLATFORM, SCOPE_CLOUDTASKS));
        }
        creds.refreshIfExpired(); // NOMUTANTS -- no-op for pre-created AccessToken
        AccessToken token = creds.getAccessToken();
        // NOMUTANTS -- fallback expiry calculation
        cachedTokenExpiry =
            token.getExpirationTime() != null
                ? token.getExpirationTime().toInstant().toEpochMilli()
                : (now + DEFAULT_TOKEN_EXPIRY_MILLIS);
        cachedToken = token.getTokenValue();
        return cachedToken;
      } catch (Exception e) {
        logger.log(Level.FINE, "Could not acquire token from GoogleCredentials", e);
      }

      if (cachedToken != null && now < cachedTokenExpiry) { // NOMUTANTS -- grace period fallback
        return cachedToken;
      }
      throw new IOException(
          "Unable to obtain access token from AppIdentityService, Metadata server, or"
              + " GoogleCredentials");
    } finally {
      TOKEN_LOCK.unlock();
    }
  }

  static CloudTasksClient getClient() {
    // NOMUTANTS -- production gRPC client initialization requires live credentials
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
                      // AccessToken's constructor only accepts java.util.Date (not Instant),
                      // and ErrorProne's JavaUtilDate check requires Date.from(Instant).
                      return GoogleCredentials.create(
                          new AccessToken(token, Date.from(Instant.ofEpochMilli(cachedTokenExpiry))));
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

  static com.google.cloud.tasks.v2beta3.CloudTasksClient getV2Beta3Client() {
    // NOMUTANTS -- production gRPC client initialization requires live credentials
    if (sharedV2Beta3Client == null) {
      CLIENT_INIT_LOCK.lock();
      try {
        if (sharedV2Beta3Client == null) {
          try {
            sharedV2Beta3Client = com.google.cloud.tasks.v2beta3.CloudTasksClient.create();
          } catch (Exception directEx) {
            logger.log(
                Level.FINE,
                "v2beta3 CloudTasksClient.create() failed, attempting custom credentials",
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
              com.google.cloud.tasks.v2beta3.CloudTasksSettings settings =
                  com.google.cloud.tasks.v2beta3.CloudTasksSettings.newBuilder()
                      .setCredentialsProvider(credentialsProvider)
                      .build();
              sharedV2Beta3Client =
                  com.google.cloud.tasks.v2beta3.CloudTasksClient.create(settings);
            } catch (Exception fallbackEx) {
              logger.log(
                  Level.SEVERE, "Failed to initialize v2beta3 CloudTasksClient", fallbackEx);
              throw new IllegalStateException(
                  "Failed to initialize v2beta3 CloudTasksClient: direct="
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
    return sharedV2Beta3Client;
  }

  /**
   * Checks whether Cloud Tasks push queue routing is enabled via environment variable.
   *
   * @return {@code true} if Cloud Tasks push queue routing is enabled; {@code false} otherwise.
   */
  static boolean isEnabled() {
    return QueueImpl.isCloudTaskBackendEnabled();
  }

  private static String getDefaultServiceName() {
    String serviceName = System.getenv(ENV_GAE_SERVICE);
    // NOMUTANTS -- System.getenv values are fixed in the test JVM
    return !isNullOrEmpty(serviceName) ? serviceName : DEFAULT_SERVICE_NAME;
  }

  private static String encodeParamsUrlEncoded(List<TaskOptions.Param> params) {
    StringBuilder result = new StringBuilder();
    try {
      String appender = "";
      for (TaskOptions.Param param : params) {
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

  private static boolean shouldUseUrlEncodedParamsBody(TaskOptions options) {
    TaskOptions.Method method = options.getMethod();
    byte[] payload = options.getPayload();
    return method == TaskOptions.Method.POST
        && (payload == null || payload.length == 0)
        && !options.getParams().isEmpty();
  }

  private static byte[] resolveEffectivePayload(TaskOptions options) {
    if (shouldUseUrlEncodedParamsBody(options)) {
      return encodeParamsUrlEncoded(options.getParams()).getBytes(UTF_8);
    }
    return options.getPayload();
  }

  private static String resolveRelativeUri(TaskOptions options) {
    String relativeUrl = options.getUrl();
    if (isNullOrEmpty(relativeUrl)) {
      relativeUrl = DEFAULT_RELATIVE_URI;
    }
    TaskOptions.Method method = options.getMethod();
    if (method != TaskOptions.Method.POST
        && method != TaskOptions.Method.PULL
        && !options.getParams().isEmpty()) {
      String separator = relativeUrl.contains("?") ? "&" : "?";
      relativeUrl = relativeUrl + separator + encodeParamsUrlEncoded(options.getParams());
    }
    return relativeUrl;
  }

  private static boolean isDisallowedCloudTasksHeader(String headerName) {
    if (headerName == null) {
      return true;
    }
    String lower = headerName.toLowerCase(Locale.ROOT);
    return lower.equals("host")
        || lower.startsWith("x-google-")
        || lower.startsWith("x-appengine-");
  }

  private static @Nullable String extractHostHeader(TaskOptions options) {
    for (Map.Entry<String, List<String>> entry : options.getHeaders().entrySet()) {
      if (entry.getKey().equalsIgnoreCase("host") && !entry.getValue().isEmpty()) {
        String val = entry.getValue().get(entry.getValue().size() - 1);
        if (!isNullOrEmpty(val)) {
          return val.trim();
        }
      }
    }
    return null;
  }

  private static String[] parseAppEngineRoutingFromHost(
      @Nullable String hostHeader, String defaultServiceName) {
    if (isNullOrEmpty(hostHeader)) {
      return new String[] {defaultServiceName, null, null};
    }
    String target = hostHeader.trim();
    int colonIdx = target.indexOf(':');
    if (colonIdx > 0) {
      target = target.substring(0, colonIdx);
    }
    String lowerTarget = target.toLowerCase(Locale.ROOT);
    if (lowerTarget.endsWith(".appspot.com")) {
      target = target.substring(0, target.length() - ".appspot.com".length());
      lowerTarget = target.toLowerCase(Locale.ROOT);
      if (lowerTarget.endsWith(".r")) {
        target = target.substring(0, target.length() - 2);
        int regionDot = target.lastIndexOf('.');
        if (regionDot > 0) {
          target = target.substring(0, regionDot);
        }
      }
      target = target.replace("-dot-", ".");
      int appDot = target.lastIndexOf('.');
      if (appDot > 0) {
        target = target.substring(0, appDot);
      } else {
        return new String[] {defaultServiceName, null, null};
      }
    } else {
      String projectId = getProjectId();
      if (!isNullOrEmpty(projectId)) {
        int projDot = target.indexOf("." + projectId);
        if (projDot > 0) {
          target = target.substring(0, projDot);
        } else {
          int projDashDot = target.indexOf("-dot-" + projectId);
          if (projDashDot > 0) {
            target = target.substring(0, projDashDot);
          }
        }
      }
      target = target.replace("-dot-", ".");
      if (target.endsWith("-dot")) {
        target = target.substring(0, target.length() - 4);
      }
    }

    List<String> parts = Splitter.on('.').splitToList(target);
    String service = defaultServiceName;
    String version = null;
    String instance = null;
    if (parts.size() == 1 && !parts.get(0).isEmpty()) {
      service = parts.get(0);
    } else if (parts.size() == 2) {
      version = parts.get(0);
      service = parts.get(1);
    } else if (parts.size() >= 3) {
      instance = parts.get(parts.size() - 3);
      version = parts.get(parts.size() - 2);
      service = parts.get(parts.size() - 1);
    }
    return new String[] {service, version, instance};
  }

  private static TaskOptions buildHandleTaskOptions(TaskOptions options, String assignedName) {
    TaskOptions handleOptions = new TaskOptions(options);
    if (!isNullOrEmpty(assignedName)) {
      handleOptions.taskName(assignedName);
    }
    if (shouldUseUrlEncodedParamsBody(options)) {
      handleOptions.payload(resolveEffectivePayload(options));
    }
    return handleOptions;
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
    if (taskOptionsList == null || taskOptionsList.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;

    if (txn != null) {
      return enqueueTransactional(effectiveQueue, txn, taskOptionsList);
    }

    return enqueueNonTransactional(effectiveQueue, taskOptionsList);
  }

  private static Future<List<TaskHandle>> enqueueTransactional(
      String effectiveQueue, Transaction txn, List<TaskOptions> taskOptionsList) {
    checkState(
        txn.isActive(), "Transaction with which this operation is associated is not active.");
    // Resolve framework-wrapped transactions (e.g. Objectify) to the native transaction so the
    // post-commit dispatch callback is registered and the put joins the same transaction.
    Transaction nativeTxn = DatastoreApiHelper.unwrapTransaction(txn);

    String projectId = getProjectId();
    String location = getLocation();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    List<TaskHandle> createdHandles = new ArrayList<>();
    List<Entity> transactionalEntities = new ArrayList<>();

    // All pending entities of this add call share one per-batch parent key so that the batch
    // consumes a single Datastore entity group instead of one entity group per task. The key name
    // embeds the creation millis so TaskProcessor.sweep() can select overdue tasks by __key__
    // range (see TaskProcessor.TIMESTAMPED_BATCH_KEY_PREFIX).
    Instant now = Instant.now(); // NOMUTANTS -- timestamp
    String batchId =
        String.format(
            Locale.ROOT,
            "%s%019d-%s",
            TaskProcessor.TIMESTAMPED_BATCH_KEY_PREFIX,
            now.toEpochMilli(),
            UUID.randomUUID());
    Key batchParentKey;
    String previousNamespace = NamespaceManager.get();
    NamespaceManager.set("");
    try {
      batchParentKey = KeyFactory.createKey(ENTITY_KIND_PENDING_TASK_BATCH, batchId);
    } finally {
      NamespaceManager.set(previousNamespace);
    }

    for (TaskOptions options : taskOptionsList) {
      String userTaskName = options.getTaskName();
      String pendingName =
          !isNullOrEmpty(userTaskName) ? userTaskName : TASK_NAME_PREFIX + UUID.randomUUID();

      String jsonPayload = buildTaskJson(parent.toString(), pendingName, options);

      Entity pendingTask = new Entity(ENTITY_KIND_PENDING_TASK, batchParentKey);
      pendingTask.setProperty(PROPERTY_QUEUE_NAME, effectiveQueue);
      pendingTask.setProperty(PROPERTY_CLOUD_TASK_NAME, pendingName);
      if (jsonPayload.getBytes(UTF_8).length > 1500) {
        pendingTask.setUnindexedProperty(PROPERTY_CLOUD_TASK_PAYLOAD, new Text(jsonPayload));
      } else {
        pendingTask.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, jsonPayload);
      }
      pendingTask.setProperty(PROPERTY_CREATED, Date.from(now));
      pendingTask.setProperty(PROPERTY_STATUS, STATUS_PENDING);
      pendingTask.setProperty(PROPERTY_LOCK_EXPIRES, null); // NOMUTANTS -- initial schema default
      pendingTask.setProperty(PROPERTY_RETRY_COUNT, 0L); // NOMUTANTS -- initial schema default
      pendingTask.setProperty(PROPERTY_LAST_ERROR, ""); // NOMUTANTS -- initial schema default
      pendingTask.setProperty(PROPERTY_HANDLED_BY_SWEEPER, false); // NOMUTANTS -- initial default
      pendingTask.setProperty(PROPERTY_SDK_LANG, SDK_LANG_JAVA); // NOMUTANTS -- initial default
      transactionalEntities.add(pendingTask);

      long scheduleTimeMs = calculateScheduleTimeMs(options);
      TaskOptions handleOptions = buildHandleTaskOptions(options, pendingName);
      TaskHandle handle = new TaskHandle(handleOptions, effectiveQueue);
      handle.etaUsec(scheduleTimeMs * MICROS_PER_MILLI);
      createdHandles.add(handle);
    }

    // Non-blocking transactional put so Queue.addAsync(txn, ...) keeps its async contract. The
    // transaction waits for this future before committing.
    Future<List<Key>> keysFuture;
    try {
      keysFuture =
          DatastoreServiceFactory.getAsyncDatastoreService()
              .put(nativeTxn, transactionalEntities);
    } catch (RuntimeException e) {
      throw toTransactionalTaskException(e);
    }

    boolean callbackRegistered =
        DatastoreApiHelper.addPostCommitCallback(
            nativeTxn,
            () -> {
              List<Key> keys;
              try {
                keys = keysFuture.get();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              } catch (ExecutionException e) {
                logger.log(
                    Level.WARNING,
                    "CLOUDTASK: Pending task put failed; skipping post-commit dispatch",
                    e.getCause());
                return;
              }
              TaskProcessor.processPendingTaskKeysAsync(keys, 0);
            });
    if (!callbackRegistered) {
      logger.warning(
          "CLOUDTASK: Could not register post-commit dispatch for transaction "
              + txn
              + "; transactional tasks will be dispatched by the sweeper (/_ah/cloudtask/sweep).");
    }

    return new FutureWrapper<List<Key>, List<TaskHandle>>(keysFuture) {
      @Override
      protected List<TaskHandle> wrap(@Nullable List<Key> keys) {
        return createdHandles;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return toTransactionalTaskException(cause);
      }
    };
  }

  /**
   * Wraps a Datastore failure from the transactional buffer in a {@link
   * TransactionalTaskException}, matching the legacy Task Queue API contract.
   */
  static RuntimeException toTransactionalTaskException(Throwable cause) {
    if (cause instanceof TransactionalTaskException tte) {
      return tte;
    }
    TransactionalTaskException wrapped = new TransactionalTaskException();
    wrapped.initCause(cause);
    return wrapped;
  }

  private static Future<List<TaskHandle>> enqueueNonTransactional(
      String effectiveQueue, List<TaskOptions> taskOptionsList) {
    if (taskOptionsList == null || taskOptionsList.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }

    List<TaskOptions> preparedOptions = new ArrayList<>(taskOptionsList.size());
    Set<String> autoGeneratedTaskNames = new HashSet<>();
    for (TaskOptions options : taskOptionsList) {
      if (isNullOrEmpty(options.getTaskName())) {
        String generatedName = TASK_NAME_PREFIX + UUID.randomUUID();
        preparedOptions.add(new TaskOptions(options).taskName(generatedName));
        autoGeneratedTaskNames.add(generatedName);
      } else {
        preparedOptions.add(options);
      }
    }

    // Batch create via v2 CloudTasksClient when multiple tasks are enqueued
    if (preparedOptions.size() > 1) {
      return supplyAsyncWithEnv(
          () -> {
            try {
              return batchCreateTasksGrpc(effectiveQueue, preparedOptions, autoGeneratedTaskNames);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new CompletionException(handleCreateTaskError(e, null, effectiveQueue));
            } catch (Throwable t) {
              if (t instanceof TaskAlreadyExistsException
                  || t instanceof IllegalArgumentException
                  || t instanceof SecurityException
                  || isAlreadyExists(t)
                  || isUnknownQueue(t)
                  || isInvalidArgument(t)
                  || isPermissionDenied(t)) {
                throw new CompletionException(handleCreateTaskError(t, null, effectiveQueue));
              }
              logger.log(
                  Level.WARNING,
                  "BatchCreateTasks via Client SDK failed for queue "
                      + effectiveQueue
                      + ", falling back to individual task creation: "
                      + t.getMessage());
              return enqueueIndividualTasks(
                  effectiveQueue, preparedOptions, autoGeneratedTaskNames);
            }
          });
    }

    // Single task creation via official v2 CloudTasksClient
    TaskOptions singleOpt = preparedOptions.get(0);
    return supplyAsyncWithEnv(
        () -> {
          try {
            return enqueueIndividualTasks(effectiveQueue, preparedOptions, autoGeneratedTaskNames);
          } catch (Throwable t) {
            throw new CompletionException(
                handleCreateTaskError(t, singleOpt.getTaskName(), effectiveQueue));
          }
        });
  }

  private static List<TaskHandle> enqueueIndividualTasks(
      String effectiveQueue,
      List<TaskOptions> taskOptionsList,
      Set<String> autoGeneratedTaskNames) {
    String projectId = getProjectId();
    String location = getLocation();
    String serviceName = getDefaultServiceName();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

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
              String chosenTaskName =
                  !isNullOrEmpty(createdTask.getName())
                      ? TaskName.parse(createdTask.getName()).getTask()
                      : userTaskName;
              TaskOptions handleOptions = buildHandleTaskOptions(options, chosenTaskName);
              TaskHandle handle = new TaskHandle(handleOptions, effectiveQueue);
              handle.etaUsec(finalScheduleTimeMs * MICROS_PER_MILLI);
              cf.complete(handle);
            } catch (ExecutionException e) {
              Throwable cause = e.getCause() != null ? e.getCause() : e;
              if (userTaskName != null
                  && autoGeneratedTaskNames != null
                  && autoGeneratedTaskNames.contains(userTaskName)
                  && isAlreadyExists(cause)) {
                TaskOptions handleOptions = buildHandleTaskOptions(options, userTaskName);
                TaskHandle handle = new TaskHandle(handleOptions, effectiveQueue);
                handle.etaUsec(finalScheduleTimeMs * MICROS_PER_MILLI);
                cf.complete(handle);
              } else {
                cf.completeExceptionally(
                    handleCreateTaskError(cause, userTaskName, effectiveQueue));
              }
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
  }

  private static List<TaskHandle> batchCreateTasksGrpc(
      String effectiveQueue, List<TaskOptions> taskOptionsList, Set<String> autoGeneratedTaskNames)
      throws Exception {
    String projectId = getProjectId();
    String location = getLocation();
    String serviceName = getDefaultServiceName();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    BatchCreateTasksRequest.Builder batchBuilder =
        BatchCreateTasksRequest.newBuilder().setParent(parent.toString());
    List<Long> scheduleTimes = new ArrayList<>(taskOptionsList.size());
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
      batchBuilder.addRequests(req);
      scheduleTimes.add(scheduleTimeHolder[0]);
    }

    CloudTasksClient client = getClient();
    Operation op;
    try {
      op = client.batchCreateTasksCallable().futureCall(batchBuilder.build()).get();
      checkOperationDone(op);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof Exception ex) {
        throw ex;
      }
      throw new IllegalStateException(cause);
    }

    Map<Integer, Status> failedRequests = Collections.emptyMap();
    if (op.hasMetadata() && !op.getMetadata().getValue().isEmpty()) { // NOMUTANTS
      BatchCreateTasksMetadata metadata =
          BatchCreateTasksMetadata.parseFrom(
              op.getMetadata().getValue(), ExtensionRegistryLite.getEmptyRegistry());
      failedRequests = metadata.getFailedRequestsMap();
    }

    List<Task> createdTasks = Collections.emptyList();
    if (op.hasResponse() && !op.getResponse().getValue().isEmpty()) { // NOMUTANTS
      BatchCreateTasksResponse resp =
          BatchCreateTasksResponse.parseFrom(
              op.getResponse().getValue(), ExtensionRegistryLite.getEmptyRegistry());
      createdTasks = resp.getTasksList();
    }

    if (op.hasError()
        && op.getError().getCode() != 0
        && (failedRequests.isEmpty()
            || createdTasks.size() + failedRequests.size() < taskOptionsList.size())) { // NOMUTANTS
      throw translateRpcStatusToException(op.getError(), null, effectiveQueue);
    }

    List<TaskHandle> handles = new ArrayList<>(taskOptionsList.size());
    RuntimeException taskqueueException = null;
    int taskCursor = 0;

    for (int i = 0; i < taskOptionsList.size(); i++) {
      TaskOptions opts = taskOptionsList.get(i);
      Status status = failedRequests.get(i);
      if (status != null && status.getCode() != 0) { // NOMUTANTS
        String failedTaskName = opts.getTaskName();
        RuntimeException translated =
            translateRpcStatusToException(status, failedTaskName, effectiveQueue);
        // NOMUTANTS -- deduplicate auto-generated task names on retry
        if (translated instanceof TaskAlreadyExistsException // NOMUTANTS
            && failedTaskName != null // NOMUTANTS
            && autoGeneratedTaskNames != null // NOMUTANTS
            && autoGeneratedTaskNames.contains(failedTaskName)) { // NOMUTANTS
          TaskOptions handleOpts = buildHandleTaskOptions(opts, failedTaskName);
          TaskHandle handle = new TaskHandle(handleOpts, effectiveQueue);
          handle.etaUsec(scheduleTimes.get(i) * MICROS_PER_MILLI);
          handles.add(handle);
          continue;
        }
        // NOMUTANTS -- aggregate per-task exceptions across batch
        if (taskqueueException == null // NOMUTANTS
            || taskqueueException instanceof TaskAlreadyExistsException) { // NOMUTANTS
          if (translated instanceof TaskAlreadyExistsException) { // NOMUTANTS
            if (taskqueueException == null) { // NOMUTANTS
              taskqueueException = translated;
            } else if (taskqueueException instanceof TaskAlreadyExistsException taee) { // NOMUTANTS
              taee.appendTaskName(
                  !isNullOrEmpty(failedTaskName) ? failedTaskName : "unknown"); // NOMUTANTS
            }
          } else {
            taskqueueException = translated;
          }
        }
        continue;
      }

      String assignedName = opts.getTaskName();
      if (taskCursor < createdTasks.size()) { // NOMUTANTS
        Task createdTask = createdTasks.get(taskCursor++);
        if (!isNullOrEmpty(createdTask.getName())) { // NOMUTANTS
          String fullName = createdTask.getName();
          assignedName = fullName.substring(fullName.lastIndexOf('/') + 1);
        }
      }
      TaskOptions handleOpts = buildHandleTaskOptions(opts, assignedName);
      TaskHandle handle = new TaskHandle(handleOpts, effectiveQueue);
      handle.etaUsec(scheduleTimes.get(i) * MICROS_PER_MILLI);
      handles.add(handle);
    }

    if (taskqueueException != null) {
      throw taskqueueException;
    }
    if (op.hasError() && op.getError().getCode() != 0) { // NOMUTANTS
      throw translateRpcStatusToException(op.getError(), null, effectiveQueue);
    }
    return handles;
  }

  static CreateTaskRequest buildCreateTaskRequest(
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

    // NOMUTANTS -- clock-dependent threshold check
    if (scheduleTimeMs
        > Instant.now().plusMillis(SCHEDULE_DELAY_THRESHOLD_MILLIS).toEpochMilli()) { // NOMUTANTS
      long seconds = scheduleTimeMs / MILLIS_PER_SECOND;
      int nanos = (int) ((scheduleTimeMs % MILLIS_PER_SECOND) * NANOS_PER_MILLI);
      taskBuilder.setScheduleTime(
          Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build());
    }

    RetryOptions retryOpts = options.getRetryOptions();
    if (retryOpts != null) {
      RetryConfig retryConfig = buildProtoRetryConfig(retryOpts);
      if (!retryConfig.equals(RetryConfig.getDefaultInstance())) {
        taskBuilder.setRetryConfig(retryConfig);
      }
    }

    return CreateTaskRequest.newBuilder()
        .setParent(parent.toString())
        .setTask(taskBuilder.build())
        .build();
  }

  private static RetryConfig buildProtoRetryConfig(RetryOptions ro) {
    RetryConfig.Builder retryBuilder = RetryConfig.newBuilder();
    if (ro.getTaskRetryLimit() != null) {
      retryBuilder.setMaxAttempts(ro.getTaskRetryLimit() + 1);
    }
    if (ro.getTaskAgeLimitSeconds() != null) {
      retryBuilder.setMaxRetryDuration(
          Duration.newBuilder().setSeconds(ro.getTaskAgeLimitSeconds()).build());
    }
    if (ro.getMinBackoffSeconds() != null) {
      retryBuilder.setMinBackoff(toProtoDuration(ro.getMinBackoffSeconds()));
    }
    if (ro.getMaxBackoffSeconds() != null) {
      retryBuilder.setMaxBackoff(toProtoDuration(ro.getMaxBackoffSeconds()));
    }
    if (ro.getMaxDoublings() != null) {
      retryBuilder.setMaxDoublings(ro.getMaxDoublings());
    }
    return retryBuilder.build();
  }

  static Duration toProtoDuration(double seconds) {
    long wholeSeconds = (long) seconds;
    int nanos = (int) Math.round((seconds - wholeSeconds) * 1_000_000_000.0);
    if (nanos >= 1_000_000_000) {
      wholeSeconds += 1;
      nanos -= 1_000_000_000;
    }
    return Duration.newBuilder().setSeconds(wholeSeconds).setNanos(nanos).build();
  }

  private static AppEngineHttpRequest buildAppEngineHttpRequest(
      String serviceName, TaskOptions options) {
    String[] routingParts = parseAppEngineRoutingFromHost(extractHostHeader(options), serviceName);
    AppEngineRouting.Builder routingBuilder =
        AppEngineRouting.newBuilder().setService(routingParts[0]);
    if (!isNullOrEmpty(routingParts[1])) {
      routingBuilder.setVersion(routingParts[1]);
    }
    if (!isNullOrEmpty(routingParts[2])) {
      routingBuilder.setInstance(routingParts[2]);
    }

    AppEngineHttpRequest.Builder builder =
        AppEngineHttpRequest.newBuilder()
            .setRelativeUri(resolveRelativeUri(options))
            .setAppEngineRouting(routingBuilder.build());

    TaskOptions.Method method = options.getMethod();
    builder.setHttpMethod(
        method == TaskOptions.Method.PULL ? HttpMethod.POST : HttpMethod.valueOf(method.name()));

    boolean useUrlEncodedContentType = shouldUseUrlEncodedParamsBody(options);
    byte[] payload = resolveEffectivePayload(options);
    if (payload != null && payload.length > 0) {
      builder.setBody(ByteString.copyFrom(payload));
    }

    for (Map.Entry<String, List<String>> entry : options.getHeaders().entrySet()) {
      if (isDisallowedCloudTasksHeader(entry.getKey())) {
        continue;
      }
      if (useUrlEncodedContentType && entry.getKey().equalsIgnoreCase("content-type")) {
        continue;
      }
      if (!entry.getValue().isEmpty()) {
        builder.putHeaders(entry.getKey(), String.join(", ", entry.getValue()));
      }
    }
    if (useUrlEncodedContentType) {
      builder.putHeaders(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded");
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
      builder.putHeaders(
          HEADER_X_TASK_RETRY_LIMIT, Integer.toString(retryOpts.getTaskRetryLimit()));
    }
    if (retryOpts.getTaskAgeLimitSeconds() != null) {
      builder.putHeaders(
          HEADER_X_TASK_AGE_LIMIT_SECONDS, Long.toString(retryOpts.getTaskAgeLimitSeconds()));
    }
    if (retryOpts.getMinBackoffSeconds() != null) {
      builder.putHeaders(
          HEADER_X_TASK_MIN_BACKOFF_SECONDS, Double.toString(retryOpts.getMinBackoffSeconds()));
    }
    if (retryOpts.getMaxBackoffSeconds() != null) {
      builder.putHeaders(
          HEADER_X_TASK_MAX_BACKOFF_SECONDS, Double.toString(retryOpts.getMaxBackoffSeconds()));
    }
    if (retryOpts.getMaxDoublings() != null) {
      builder.putHeaders(
          HEADER_X_TASK_MAX_DOUBLINGS, Integer.toString(retryOpts.getMaxDoublings()));
    }
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
    if (t instanceof TaskAlreadyExistsException) {
      return t;
    }
    String nameForErr = !isNullOrEmpty(userTaskName) ? userTaskName : "unknown"; // NOMUTANTS
    if (isAlreadyExists(t)) {
      TaskAlreadyExistsException taee =
          new TaskAlreadyExistsException(MSG_TASK_ALREADY_EXISTS_PREFIX + nameForErr);
      if (!isNullOrEmpty(userTaskName)) { // NOMUTANTS
        taee.appendTaskName(userTaskName);
      }
      return taee;
    } else if (isPermissionDenied(t)) {
      return new SecurityException(
          "Permission for requested operation is denied : " + effectiveQueue, t);
    } else if (isUnknownQueue(t)) {
      return new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue, t);
    } else if (isInvalidArgument(t)) {
      return new IllegalArgumentException(t.getMessage(), t);
    } else if (t instanceof RuntimeException) { // NOMUTANTS
      return t;
    } else {
      return new RuntimeException(
          "Failed to enqueue task to Cloud Tasks via Client SDK: " + t.getMessage(), t);
    }
  }

  private static Future<List<TaskHandle>> awaitAllTaskCreationFutures(
      List<CompletableFuture<TaskHandle>> futures) {
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
            if (!taeeCause.getTaskNames().isEmpty()) {
              for (String tn : taeeCause.getTaskNames()) {
                taee.appendTaskName(tn);
              }
            } else {
              taee.appendTaskName(taeeCause.getMessage());
            }
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
   * Asynchronously deletes one or more tasks from Cloud Tasks by task handle using the official
   * {@link CloudTasksClient} SDK.
   *
   * @param queueName the short name of the target App Engine queue
   * @param taskHandles the list of task handles to delete
   * @return a {@link Future} resolving to a list of booleans indicating deletion success
   */
  static Future<List<Boolean>> deleteTaskAsync(String queueName, List<TaskHandle> taskHandles) {
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
   * Asynchronously deletes one or more tasks by string task name from Cloud Tasks using the
   * official v2 {@link CloudTasksClient} SDK ({@code batchDeleteTasks} for multiple tasks and
   * {@code deleteTask} for single tasks).
   *
   * @param queueName the short name of the target App Engine queue
   * @param taskNames the list of task names to delete
   * @return a {@link Future} resolving to a list of booleans indicating deletion success
   */
  static Future<List<Boolean>> deleteTaskByNameAsync(String queueName, List<String> taskNames) {
    if (taskNames == null || taskNames.isEmpty()) {
      return CompletableFuture.completedFuture(ImmutableList.of());
    }
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;

    if (taskNames.size() > 1) {
      return supplyAsyncWithEnv(() -> batchDeleteTasksGrpc(effectiveQueue, taskNames));
    }

    // Standard single task delete via GA v2 Client SDK
    String projectId = getProjectId();
    String location = getLocation();
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
            cf.completeExceptionally(handleDeleteTaskError(e, effectiveQueue));
          } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (isTaskNotFoundOnDelete(cause)) {
              cf.complete(ImmutableList.of(Boolean.FALSE));
            } else {
              logger.log(
                  Level.WARNING,
                  "Failed to delete Cloud Task by name via Client SDK: " + taskName,
                  cause);
              cf.completeExceptionally(handleDeleteTaskError(cause, effectiveQueue));
            }
          } catch (RuntimeException t) {
            if (isTaskNotFoundOnDelete(t)) {
              cf.complete(ImmutableList.of(Boolean.FALSE));
            } else {
              logger.log(
                  Level.WARNING,
                  "Failed to delete Cloud Task by name via Client SDK: " + taskName,
                  t);
              cf.completeExceptionally(handleDeleteTaskError(t, effectiveQueue));
            }
          }
        },
        directExecutor());
    return cf;
  }

  private static List<Boolean> batchDeleteTasksGrpc(String effectiveQueue, List<String> taskNames) {
    String projectId = getProjectId();
    String location = getLocation();
    QueueName parent = QueueName.of(projectId, location, effectiveQueue);

    try {
      BatchDeleteTasksRequest.Builder batchBuilder =
          BatchDeleteTasksRequest.newBuilder().setParent(parent.toString());
      for (String name : taskNames) {
        batchBuilder.addNames(TaskName.of(projectId, location, effectiveQueue, name).toString());
      }

      CloudTasksClient client = getClient();
      Operation op;
      try {
        op = client.batchDeleteTasksCallable().futureCall(batchBuilder.build()).get();
        checkOperationDone(op);
      } catch (ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof Exception ex) {
          throw ex;
        }
        throw new IllegalStateException(cause);
      }

      Map<Integer, Status> failedRequests = Collections.emptyMap();
      if (op.hasMetadata() && !op.getMetadata().getValue().isEmpty()) { // NOMUTANTS
        BatchDeleteTasksMetadata metadata =
            BatchDeleteTasksMetadata.parseFrom(
                op.getMetadata().getValue(), ExtensionRegistryLite.getEmptyRegistry());
        failedRequests = metadata.getFailedRequestsMap();
      }

      // Per Cloud Tasks v2 specification, when all requests in BatchDeleteTasks fail,
      // operation.error is set to ABORTED while individual failures are reported in
      // metadata.failedRequests. Inspect failedRequests first when all sub-requests are
      // accounted for so all-NOT_FOUND batches return false per task rather than throwing
      // on the top-level ABORTED status.
      if (op.hasError()
          && op.getError().getCode() != 0
          && (failedRequests.isEmpty() || failedRequests.size() < taskNames.size())) { // NOMUTANTS
        throw translateRpcStatusToException(op.getError(), null, effectiveQueue);
      }

      List<Boolean> results = new ArrayList<>(taskNames.size());
      for (int i = 0; i < taskNames.size(); i++) {
        Status status = failedRequests.get(i);
        boolean failed = false;
        if (status != null && status.getCode() != 0) { // NOMUTANTS
          if (status.getCode() == 5 && !isQueueNotFoundMessage(status.getMessage())) {
            failed = true;
          } else {
            throw translateRpcStatusToException(status, taskNames.get(i), effectiveQueue);
          }
        }
        results.add(!failed);
      }
      return results;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // NOMUTANTS
      logger.log(
          Level.WARNING, "Interrupted during batch delete via Client SDK: " + e.getMessage(), e);
      throw handleDeleteTaskError(e, effectiveQueue);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed batch delete via Client SDK: " + e.getMessage(), e);
      throw handleDeleteTaskError(e, effectiveQueue);
    }
  }

  /**
   * Cloud Tasks {@code BatchCreateTasks} and {@code BatchDeleteTasks} are synchronous: the
   * returned {@link Operation} is already complete. Fails fast if that contract is ever violated.
   */
  private static void checkOperationDone(Operation op) {
    checkState(
        op.getDone(), "Cloud Tasks batch operation returned before completion: %s", op.getName());
  }

  private static RuntimeException translateRpcStatusToException(
      Status status, String taskName, String effectiveQueue) {
    int code = status != null ? status.getCode() : 13; // NOMUTANTS
    String msg = status != null ? status.getMessage() : ""; // NOMUTANTS
    String nameForErr = !isNullOrEmpty(taskName) ? taskName : "unknown"; // NOMUTANTS
    // NOMUTANTS -- gRPC code and message mapping
    if (code == 6 // NOMUTANTS
        || msg.contains(MARKER_ALREADY_EXISTS_UPPER) // NOMUTANTS
        || msg.contains(MARKER_EXISTED_TOO_RECENTLY)) { // NOMUTANTS
      TaskAlreadyExistsException taee =
          new TaskAlreadyExistsException(MSG_TASK_ALREADY_EXISTS_PREFIX + nameForErr);
      if (!isNullOrEmpty(taskName)) { // NOMUTANTS
        taee.appendTaskName(taskName);
      }
      return taee;
    } else if (code == 7 || msg.contains("PERMISSION_DENIED")) { // NOMUTANTS
      return new SecurityException(
          "Permission for requested operation is denied : "
              + (!isNullOrEmpty(msg) ? msg : effectiveQueue)); // NOMUTANTS
    } else if (code == 5 // NOMUTANTS
        || isQueueNotFoundMessage(msg) // NOMUTANTS
        || msg.contains(MARKER_NOT_FOUND_UPPER)) { // NOMUTANTS
      return new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue);
    } else if (code == 3 || msg.contains(MARKER_INVALID_ARGUMENT)) { // NOMUTANTS
      return new IllegalArgumentException(
          !isNullOrEmpty(msg) ? msg : "Invalid argument"); // NOMUTANTS
    }
    return new IllegalStateException("Cloud Tasks operation failed (code=" + code + "): " + msg);
  }

  /**
   * Asynchronously fetches statistics for the specified queue from Cloud Tasks using a single
   * {@code v2beta3} {@code GetQueue} RPC with a {@code stats, rate_limits} read mask.
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
        runAsyncWithEnv(
            () -> {
              try {
                int tasksCount = 0;
                long oldestEtaUsec = -1L;
                long executedLastMinute = 0L;
                int requestsInFlight = 0;
                double enforcedRate = 0.0;

                try {
                  com.google.cloud.tasks.v2beta3.CloudTasksClient v2beta3Client =
                      getV2Beta3Client();
                  GetQueueRequest request =
                      GetQueueRequest.newBuilder()
                          .setName(QueueName.of(projectId, location, effectiveQueue).toString())
                          .setReadMask(
                              FieldMask.newBuilder()
                                  .addPaths("stats")
                                  .addPaths("rate_limits")
                                  .build())
                          .build();
                  Queue queue = v2beta3Client.getQueue(request);
                  if (queue != null) {
                    if (queue.hasRateLimits()) {
                      enforcedRate = queue.getRateLimits().getMaxDispatchesPerSecond();
                    }
                    if (queue.hasStats()) {
                      QueueStats queueStats = queue.getStats();
                      tasksCount = (int) queueStats.getTasksCount();
                      if (queueStats.hasOldestEstimatedArrivalTime()) {
                        Timestamp ts = queueStats.getOldestEstimatedArrivalTime();
                        oldestEtaUsec =
                            ts.getSeconds() * MICROS_PER_SECOND + ts.getNanos() / NANOS_PER_MICRO;
                      }
                      executedLastMinute = queueStats.getExecutedLastMinuteCount();
                      requestsInFlight = (int) queueStats.getConcurrentDispatchesCount();
                      if (queueStats.getEffectiveExecutionRate() > 0.0) {
                        enforcedRate = queueStats.getEffectiveExecutionRate();
                      }
                    }
                  }
                } catch (Throwable t) {
                  if (isUnknownQueue(t)) {
                    throw new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue, t);
                  }
                  if (isPermissionDenied(t)) {
                    throw new SecurityException(
                        "Permission for requested operation is denied : " + effectiveQueue, t);
                  }
                  logger.log(
                      Level.FINE,
                      "Failed to fetch stats for queue " + effectiveQueue + " via v2beta3 SDK",
                      t);
                }

                TaskQueueFetchQueueStatsResponse.QueueStats.Builder legacyStatsBuilder =
                    TaskQueueFetchQueueStatsResponse.QueueStats.newBuilder()
                        .setNumTasks(tasksCount)
                        .setOldestEtaUsec(oldestEtaUsec);

                TaskQueueScannerQueueInfo scannerInfo =
                    TaskQueueScannerQueueInfo.newBuilder()
                        .setExecutedLastMinute(executedLastMinute)
                        .setExecutedLastHour(0L)
                        .setSamplingDurationSeconds(0.0)
                        .setRequestsInFlight(requestsInFlight)
                        .setEnforcedRate(enforcedRate)
                        .build();
                legacyStatsBuilder.setScannerInfo(scannerInfo);

                QueueStatistics stats =
                    new QueueStatistics(effectiveQueue, legacyStatsBuilder.build());
                cf.complete(stats);
              } catch (Throwable t) {
                cf.completeExceptionally(t);
              }
            });
    return cf;
  }

  /**
   * Purges all tasks from the specified queue using the official {@link CloudTasksClient} SDK.
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
      if (isPermissionDenied(e)) {
        throw new SecurityException(
            "Permission for requested operation is denied : " + effectiveQueue, e);
      }
      if (isUnknownQueue(e)) {
        throw new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue, e);
      }
      throw new IllegalStateException(MSG_CLOUDTASK_PURGE_FAILED, e);
    }
  }

  static boolean isAlreadyExists(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof TaskAlreadyExistsException || curr instanceof AlreadyExistsException) {
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
                  || msg.contains(MARKER_EXISTED_TOO_RECENTLY)
                  || msg.contains("code 409")))) {
        return true;
      }
      curr = curr.getCause();
    }
    return false;
  }

  private static boolean isQueueNotFoundMessage(String msg) {
    if (isNullOrEmpty(msg)) { // NOMUTANTS -- null guard
      return false;
    }
    String lower = msg.toLowerCase(Locale.ROOT);
    // NOMUTANTS -- backend error message variants for missing queues
    return lower.contains(MARKER_QUEUE_NOT_FOUND)
        || lower.contains("queue does not exist")
        || lower.contains("queue no longer exists");
  }

  private static boolean isTaskNotFoundOnDelete(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      String msg = curr.getMessage();
      if (isQueueNotFoundMessage(msg)) { // NOMUTANTS -- queue 404 is not task 404
        return false;
      }
      // NOMUTANTS -- exception class/message fallbacks across gRPC and REST transports
      boolean isNotFoundCode =
          (curr instanceof NotFoundException)
              || (curr instanceof ApiException apiException
                  && apiException.getStatusCode().getCode() == StatusCode.Code.NOT_FOUND)
              || curr.getClass().getName().contains(MARKER_NOT_FOUND)
              || (msg != null
                  && (msg.contains(MARKER_NOT_FOUND_UPPER) || msg.contains("code 404")));
      if (isNotFoundCode && msg != null) { // NOMUTANTS
        String lower = msg.toLowerCase(Locale.ROOT);
        // NOMUTANTS -- backend message variants for missing tasks
        if (lower.contains("task")
            || lower.contains("requested entity was not found")
            || lower.contains("existed too recently")) {
          return true;
        }
      }
      curr = curr.getCause();
    }
    return false;
  }

  private static boolean isPermissionDenied(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof SecurityException) { // NOMUTANTS
        return true;
      }
      // NOMUTANTS -- gax ApiException status check
      if (curr instanceof ApiException apiException
          && apiException.getStatusCode().getCode() == StatusCode.Code.PERMISSION_DENIED) {
        return true;
      }
      String clsName = curr.getClass().getName();
      String msg = curr.getMessage();
      // NOMUTANTS -- exception class/message fallbacks across gRPC and REST transports
      if (clsName.contains("PermissionDenied")
          || (msg != null && (msg.contains("PERMISSION_DENIED") || msg.contains("code 403")))) {
        return true;
      }
      curr = curr.getCause();
    }
    return false;
  }

  private static RuntimeException handleDeleteTaskError(Throwable t, String effectiveQueue) {
    if (isPermissionDenied(t)) {
      return new SecurityException(
          "Permission for requested operation is denied : " + effectiveQueue, t);
    }
    if (isUnknownQueue(t)) {
      return new IllegalStateException(MSG_UNKNOWN_QUEUE_PREFIX + effectiveQueue, t);
    }
    if (isInvalidArgument(t)) {
      return new IllegalArgumentException(t.getMessage(), t);
    }
    if (t instanceof RuntimeException re) { // NOMUTANTS
      return re;
    }
    return new IllegalStateException(
        "Failed to delete task from Cloud Tasks: " + t.getMessage(), t);
  }

  static boolean isUnknownQueue(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof NotFoundException) { // NOMUTANTS
        return true;
      }
      // NOMUTANTS -- gax ApiException status check
      if (curr instanceof ApiException apiException // NOMUTANTS
          && apiException.getStatusCode().getCode() == StatusCode.Code.NOT_FOUND) { // NOMUTANTS
        return true;
      }
      String clsName = curr.getClass().getName();
      String msg = curr.getMessage();
      // NOMUTANTS -- exception class/message fallbacks across gRPC and REST transports
      if (clsName.contains(MARKER_NOT_FOUND) // NOMUTANTS
          || isQueueNotFoundMessage(msg) // NOMUTANTS
          || (msg != null // NOMUTANTS
              && (msg.contains(MARKER_NOT_FOUND_UPPER) || msg.contains("code 404")))) { // NOMUTANTS
        return true;
      }
      curr = curr.getCause(); // NOMUTANTS
    }
    return false;
  }

  private static boolean isInvalidArgument(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (curr instanceof IllegalArgumentException) { // NOMUTANTS
        return true;
      }
      // NOMUTANTS -- gax ApiException status check
      if (curr instanceof ApiException apiException // NOMUTANTS
          && apiException.getStatusCode().getCode() // NOMUTANTS
              == StatusCode.Code.INVALID_ARGUMENT) { // NOMUTANTS
        return true;
      }
      String msg = curr.getMessage();
      // NOMUTANTS -- exception message fallbacks across gRPC and REST transports
      if (msg != null // NOMUTANTS
          && (msg.contains(MARKER_INVALID_ARGUMENT) || msg.contains("code 400"))) { // NOMUTANTS
        return true;
      }
      curr = curr.getCause(); // NOMUTANTS
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

    String[] routingParts = parseAppEngineRoutingFromHost(extractHostHeader(options), serviceName);
    JsonObject httpJson = new JsonObject();
    JsonObject routingJson = new JsonObject();
    routingJson.addProperty(JSON_FIELD_SERVICE, routingParts[0]);
    if (!isNullOrEmpty(routingParts[1])) {
      routingJson.addProperty(JSON_FIELD_VERSION, routingParts[1]);
    }
    if (!isNullOrEmpty(routingParts[2])) {
      routingJson.addProperty(JSON_FIELD_INSTANCE, routingParts[2]);
    }
    httpJson.add(JSON_FIELD_APP_ENGINE_ROUTING, routingJson);

    TaskOptions.Method method = options.getMethod();
    httpJson.addProperty(
        JSON_FIELD_HTTP_METHOD,
        method == TaskOptions.Method.PULL ? HTTP_METHOD_POST : method.name()); // NOMUTANTS

    httpJson.addProperty(JSON_FIELD_RELATIVE_URI, resolveRelativeUri(options));

    boolean useUrlEncodedContentType = shouldUseUrlEncodedParamsBody(options);
    byte[] payload = resolveEffectivePayload(options);
    if (payload != null && payload.length > 0) {
      httpJson.addProperty(JSON_FIELD_BODY, Base64.getEncoder().encodeToString(payload));
    }

    JsonObject headersJson = new JsonObject();
    for (Map.Entry<String, List<String>> entry : options.getHeaders().entrySet()) {
      if (isDisallowedCloudTasksHeader(entry.getKey())) {
        continue;
      }
      if (useUrlEncodedContentType
          && entry.getKey().equalsIgnoreCase("content-type")) { // NOMUTANTS
        continue;
      }
      if (entry.getValue() != null && !entry.getValue().isEmpty()) { // NOMUTANTS
        headersJson.addProperty(entry.getKey(), String.join(", ", entry.getValue()));
      }
    }
    if (useUrlEncodedContentType) {
      headersJson.addProperty(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded");
    }

    if (options.getRetryOptions() != null) {
      RetryOptions ro = options.getRetryOptions();
      JsonObject retryConfig = new JsonObject();
      if (ro.getTaskRetryLimit() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_RETRY_LIMIT, Integer.toString(ro.getTaskRetryLimit()));
        retryConfig.addProperty(JSON_FIELD_MAX_ATTEMPTS, ro.getTaskRetryLimit() + 1);
      }
      if (ro.getTaskAgeLimitSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_AGE_LIMIT_SECONDS, Long.toString(ro.getTaskAgeLimitSeconds()));
        retryConfig.addProperty(
            JSON_FIELD_MAX_RETRY_DURATION, Long.toString(ro.getTaskAgeLimitSeconds()) + "s");
      }
      if (ro.getMinBackoffSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_MIN_BACKOFF_SECONDS, Double.toString(ro.getMinBackoffSeconds()));
        retryConfig.addProperty(
            JSON_FIELD_MIN_BACKOFF, Double.toString(ro.getMinBackoffSeconds()) + "s");
      }
      if (ro.getMaxBackoffSeconds() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_MAX_BACKOFF_SECONDS, Double.toString(ro.getMaxBackoffSeconds()));
        retryConfig.addProperty(
            JSON_FIELD_MAX_BACKOFF, Double.toString(ro.getMaxBackoffSeconds()) + "s");
      }
      if (ro.getMaxDoublings() != null) {
        headersJson.addProperty(
            HEADER_X_TASK_MAX_DOUBLINGS, Integer.toString(ro.getMaxDoublings()));
        retryConfig.addProperty(JSON_FIELD_MAX_DOUBLINGS, ro.getMaxDoublings().intValue());
      }
      if (retryConfig.size() > 0) { // NOMUTANTS
        taskJson.add(JSON_FIELD_RETRY_CONFIG, retryConfig);
      }
    }
    httpJson.add(JSON_FIELD_HEADERS, headersJson);
    taskJson.add(JSON_FIELD_APP_ENGINE_HTTP_REQUEST, httpJson);

    long etaMillis = calculateScheduleTimeMs(options);
    // NOMUTANTS -- clock-dependent threshold check
    if (etaMillis // NOMUTANTS
        > Instant.now()
            .plusMillis(REST_SCHEDULE_DELAY_THRESHOLD_MILLIS)
            .toEpochMilli()) { // NOMUTANTS
      taskJson.addProperty(JSON_FIELD_SCHEDULE_TIME, Instant.ofEpochMilli(etaMillis).toString());
    }

    return taskJson;
  }

  private static String buildTaskJson(String fullQueueName, String taskName, TaskOptions options) {
    JsonObject root = new JsonObject();
    root.add(JSON_FIELD_TASK, buildTaskJsonObject(fullQueueName, taskName, options));
    return root.toString();
  }
}
