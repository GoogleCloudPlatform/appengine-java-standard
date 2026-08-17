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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.GetAccessTokenResult;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.apphosting.api.ApiProxy;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor utility responsible for executing and dispatching pending Cloud Tasks stored in
 * Datastore ({@code _AE_PendingCloudTask}) to Google Cloud Tasks via REST API.
 */
final class TaskProcessor {
  private static final Logger logger = Logger.getLogger(TaskProcessor.class.getName());

  private TaskProcessor() {}

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
  static final String HEADER_METADATA_FLAVOR = "Metadata-Flavor";
  static final String VALUE_METADATA_FLAVOR_GOOGLE = "Google";
  static final String HEADER_AUTHORIZATION = "Authorization";
  static final String VALUE_BEARER_PREFIX = "Bearer ";
  static final String HTTP_METHOD_GET = "GET";
  static final String HTTP_METHOD_POST = "POST";
  static final String HEADER_CONTENT_TYPE = "Content-Type";
  static final String VALUE_APPLICATION_JSON = "application/json";
  static final String CLOUD_TASKS_LOCATIONS_URL_FORMAT =
      "https://cloudtasks.googleapis.com/v2beta3/projects/%s/locations";
  static final String JSON_FIELD_LOCATIONS = "locations";
  static final String JSON_FIELD_LOCATION_ID = "locationId";
  static final String DEFAULT_LOCATION = "us-central1";

  // OAuth Scopes & Metadata Server Constants
  private static final String SCOPE_CLOUD_PLATFORM =
      "https://www.googleapis.com/auth/cloud-platform";
  private static final String METADATA_TOKEN_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
  private static final String DEFAULT_QUEUE_NAME = "default";
  private static final String JSON_FIELD_TASK = "task";
  private static final String JSON_FIELD_ACCESS_TOKEN = "access_token";
  private static final String JSON_FIELD_EXPIRES_IN = "expires_in";

  // Sweeper & Processing Configuration
  static final long REALTIME_DISPATCH_GRACE_PERIOD_MILLIS = 60_000L;
  static final long MAX_RETRY_COUNT = 5L;
  static final Duration LOCK_DURATION = Duration.ofMinutes(1);

  // Timeouts & Durations (in milliseconds)
  static final int METADATA_TIMEOUT_MILLIS = 2000;
  static final int REST_REQUEST_TIMEOUT_MILLIS = 5000;
  static final long TOKEN_EXPIRY_BUFFER_MILLIS = 60_000L;
  static final long DEFAULT_TOKEN_EXPIRY_MILLIS = 3_600_000L;
  static final long DEFAULT_TOKEN_EXPIRES_IN_SECONDS = 3600L;

  // Time Unit Multipliers & Conversions
  static final long MILLIS_PER_SECOND = 1000L;

  /**
   * Asynchronously processes a list of pending task entity IDs after an optional delay.
   *
   * @param ids the list of Datastore entity IDs for {@code _AE_PendingCloudTask} entities
   * @param delayMillis milliseconds to wait before executing
   */
  static void processPendingTasksAsync(List<Long> ids, long delayMillis) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
    CompletableFuture<?> unused =
        CompletableFuture.runAsync(
            () -> {
              if (env != null) {
                ApiProxy.setEnvironmentForCurrentThread(env);
              }
              try {
                if (delayMillis > 0) {
                  try {
                    Thread.sleep(delayMillis);
                  } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                  }
                }
                processPendingTasks(ids, false);
              } catch (Throwable t) {
                logger.log(Level.SEVERE, "Error in processPendingTasksAsync: " + t.getMessage(), t);
              } finally {
                if (env != null) {
                  ApiProxy.clearEnvironmentForCurrentThread();
                }
              }
            });
  }

  /**
   * Processes a list of pending task entity IDs stored in Datastore.
   *
   * @param ids the list of Datastore entity IDs for {@code _AE_PendingCloudTask} entities to
   *     process
   */
  static void processPendingTasks(List<Long> ids) {
    processPendingTasks(ids, false);
  }

  /**
   * Processes a list of pending task entity IDs stored in Datastore, indicating whether invocation
   * originated from the background sweeper cron job.
   *
   * @param ids the list of Datastore entity IDs for {@code _AE_PendingCloudTask} entities to
   *     process
   * @param handledBySweeper {@code true} if triggered by the cron sweeper; {@code false} if
   *     triggered by fast-path
   */
  static void processPendingTasks(List<Long> ids, boolean handledBySweeper) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    for (Long id : ids) {
      if (id == null || id <= 0) {
        continue;
      }
      Key key = KeyFactory.createKey(ENTITY_KIND_PENDING_TASK, id);
      try {
        processSingleTask(ds, key, handledBySweeper);
      } catch (RuntimeException e) {
        logger.log(Level.SEVERE, "Failed to process pending task " + id + ": " + e.getMessage(), e);
      }
    }
  }

  /**
   * Sweeps Datastore for orphaned or un-dispatched {@code _AE_PendingCloudTask} entities and
   * processes them.
   *
   * @return the number of swept tasks processed
   */
  static int sweep() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query q = new Query(ENTITY_KIND_PENDING_TASK);
    PreparedQuery pq = ds.prepare(q);

    long now = Instant.now().toEpochMilli();
    List<Long> idsToProcess = new ArrayList<>();
    for (Entity entity : pq.asIterable()) {
      String status = (String) entity.getProperty(PROPERTY_STATUS);
      if (status == null || Objects.equals(status, STATUS_PENDING)) {
        Object createdObj = entity.getProperty(PROPERTY_CREATED);
        Instant created = (createdObj instanceof Date d) ? d.toInstant() : null;
        if (created != null && (now - created.toEpochMilli()) < REALTIME_DISPATCH_GRACE_PERIOD_MILLIS) {
          continue; // Give real-time post-commit 60s to dispatch
        }
      } else if (Objects.equals(status, STATUS_PROCESSING)) {
        Object lockObj = entity.getProperty(PROPERTY_LOCK_EXPIRES);
        Instant lockExpires = (lockObj instanceof Date d) ? d.toInstant() : null;
        if (lockExpires != null && now < lockExpires.toEpochMilli()) {
          continue; // Still actively processing and lock valid
        } else if (lockExpires == null) {
          continue; // Assume lock valid if just started without timestamp
        }
      } else if (Objects.equals(status, STATUS_FAILED)) {
        Object retryObj = entity.getProperty(PROPERTY_RETRY_COUNT);
        long retryCount = (retryObj instanceof Number num) ? num.longValue() : 0L;
        if (retryCount >= MAX_RETRY_COUNT) {
          continue; // Exceeded max sweeper retries
        }
      } else if (Objects.equals(status, STATUS_DONE)
          || Objects.equals(status, STATUS_ALREADY_EXISTS)) {
        continue;
      }
      idsToProcess.add(entity.getKey().getId());
    }

    if (!idsToProcess.isEmpty()) {
      logger.info("CLOUDTASK: Sweeper found " + idsToProcess.size() + " tasks to process.");
      processPendingTasks(idsToProcess, true);
    }
    return idsToProcess.size();
  }

  private static void processSingleTask(DatastoreService ds, Key key, boolean handledBySweeper) {
    Entity entity = null;
    try {
      entity = ds.get(key);
    } catch (EntityNotFoundException enfe) {
      // Entity was deleted or rolled back
      return;
    }

    String status = (String) entity.getProperty(PROPERTY_STATUS);
    if (Objects.equals(status, STATUS_DONE) || Objects.equals(status, STATUS_ALREADY_EXISTS)) {
      return;
    }

    Object lockObj = entity.getProperty(PROPERTY_LOCK_EXPIRES);
    Instant lockExpires = (lockObj instanceof Date d) ? d.toInstant() : null;
    if (Objects.equals(status, STATUS_PROCESSING) && lockExpires != null) {
      if (Instant.now().isBefore(lockExpires)) {
        // Still actively processing
        return;
      }
    }

    entity.setProperty(PROPERTY_STATUS, STATUS_PROCESSING);
    entity.setProperty(PROPERTY_LOCK_EXPIRES, Date.from(Instant.now().plus(LOCK_DURATION)));
    entity.setProperty(PROPERTY_HANDLED_BY_SWEEPER, handledBySweeper);
    try {
      ds.put(entity);
    } catch (RuntimeException e) {
      logger.log(
          Level.WARNING, "Failed to acquire lock for task " + key.getId() + ": " + e.getMessage());
      return;
    }

    String queueName = (String) entity.getProperty(PROPERTY_QUEUE_NAME);
    String payload = (String) entity.getProperty(PROPERTY_CLOUD_TASK_PAYLOAD);
    long entityId = key.getId();

    boolean success = false;
    ErrorCode resCode = ErrorCode.INTERNAL_ERROR;
    try {
      resCode =
          callCloudTasksViaSdk(
              queueName, payload, entityId, (String) entity.getProperty(PROPERTY_CLOUD_TASK_NAME));
      success = (resCode == ErrorCode.OK || resCode == ErrorCode.TASK_ALREADY_EXISTS);
    } catch (RuntimeException ex) {
      logger.log(
          Level.SEVERE,
          "CLOUDTASK: Exception during Client SDK dispatch for task "
              + entityId
              + ": "
              + ex.getMessage(),
          ex);
      success = false;
    }

    if (success) {
      try {
        ds.delete(key);
        logger.info("CLOUDTASK: Successfully processed and cleaned up task " + entityId);
      } catch (RuntimeException e) {
        logger.warning("Failed to clean up task entity " + entityId + ": " + e.getMessage());
      }
    } else {
      try {
        Object retryObj = entity.getProperty(PROPERTY_RETRY_COUNT);
        long retryCount = (retryObj instanceof Number num) ? num.longValue() : 0L;
        retryCount++;
        entity.setProperty(PROPERTY_RETRY_COUNT, retryCount);
        entity.setProperty(
            PROPERTY_LAST_ERROR, "Cloud Tasks Client SDK call failed with " + resCode);
        if (retryCount >= MAX_RETRY_COUNT) {
          entity.setProperty(PROPERTY_STATUS, STATUS_FAILED);
        } else {
          entity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
        }
        entity.setProperty(PROPERTY_LOCK_EXPIRES, null);
        ds.put(entity);
        logger.warning(
            "CLOUDTASK: Failed to process task " + entityId + ", retry count: " + retryCount);
      } catch (RuntimeException putErr) {
        logger.severe(
            "Failed to record error state for task " + entityId + ": " + putErr.getMessage());
      }
    }
  }

  private static volatile String cachedLocation = null;

  /**
   * Resolves the current Google Cloud Platform project ID from the App Engine runtime environment.
   *
   * @return the GCP project ID string
   */
  static String getProjectId() {
    String projectId = System.getenv(ENV_GOOGLE_CLOUD_PROJECT);
    if (!isNullOrEmpty(projectId)) {
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_APPLICATION);
    if (!isNullOrEmpty(projectId)) {
      if (projectId.contains("~")) {
        return projectId.substring(projectId.indexOf("~") + 1);
      }
      return projectId;
    }
    projectId = System.getenv(ENV_GAE_LONG_APP_ID);
    if (!isNullOrEmpty(projectId)) {
      return projectId;
    }
    ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
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
    if (!isNullOrEmpty(zone)) {
      int lastDash = zone.lastIndexOf('-');
      if (lastDash > 0) {
        cachedLocation = zone.substring(0, lastDash);
        return cachedLocation;
      }
      cachedLocation = zone;
      return cachedLocation;
    }
    String location = System.getenv(ENV_LOCATION_ID);
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_LOCATION);
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GAE_REGION);
    if (!isNullOrEmpty(location)) {
      cachedLocation = location;
      return cachedLocation;
    }
    location = System.getenv(ENV_GOOGLE_CLOUD_REGION);
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
      if (!isNullOrEmpty(projId) && token != null) {
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
      conn.setConnectTimeout(METADATA_TIMEOUT_MILLIS);
      conn.setReadTimeout(METADATA_TIMEOUT_MILLIS);
      if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        try (InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            sb.append(line);
          }
          JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
          if (json.has(JSON_FIELD_ACCESS_TOKEN)) {
            cachedToken = json.get(JSON_FIELD_ACCESS_TOKEN).getAsString();
            long expiresIn =
                json.has(JSON_FIELD_EXPIRES_IN)
                    ? json.get(JSON_FIELD_EXPIRES_IN).getAsLong()
                    : DEFAULT_TOKEN_EXPIRES_IN_SECONDS;
            cachedTokenExpiry = now + (expiresIn * MILLIS_PER_SECOND);
            return cachedToken;
          }
        }
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to get access token from Metadata server: " + e.getMessage());
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
        "Unable to obtain access token from AppIdentityService, Metadata server, or GoogleCredentials");
  }

  /**
   * Dispatches a single pending push task stored in Datastore to Google Cloud Tasks via REST API.
   *
   * @param queueName the target task queue name
   * @param payload the JSON task payload stored in Datastore
   * @param entityId the Datastore entity ID for fallback task naming
   * @param taskName the chosen task name or {@code null}
   * @return a {@link ErrorCode} indicating success or failure code
   */
  static ErrorCode dispatchPendingTask(
      String queueName, String payload, long entityId, String taskName) {
    String projectId = getProjectId();
    String location = getLocation();
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;
    String parentQueue =
        "projects/" + projectId + "/locations/" + location + "/queues/" + effectiveQueue;
    String urlStr = "https://cloudtasks.googleapis.com/v2beta3/" + parentQueue + "/tasks";

    try {
      String token = getValidAccessToken();
      URL url = URI.create(urlStr).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod(HTTP_METHOD_POST);
      conn.setRequestProperty(HEADER_AUTHORIZATION, VALUE_BEARER_PREFIX + token);
      conn.setRequestProperty(HEADER_CONTENT_TYPE, VALUE_APPLICATION_JSON);
      conn.setDoOutput(true);
      conn.setConnectTimeout(REST_REQUEST_TIMEOUT_MILLIS);
      conn.setReadTimeout(REST_REQUEST_TIMEOUT_MILLIS);

      String jsonBody = payload;
      if (jsonBody == null || jsonBody.trim().isEmpty()) {
        jsonBody = "{}";
      } else if (!jsonBody.trim().startsWith("{\"task\":")) {
        JsonObject root = new JsonObject();
        root.add(JSON_FIELD_TASK, JsonParser.parseString(jsonBody));
        jsonBody = root.toString();
      }

      try (OutputStream os = conn.getOutputStream()) {
        os.write(jsonBody.getBytes(UTF_8));
      }

      int respCode = conn.getResponseCode();
      if (respCode >= HttpURLConnection.HTTP_OK && respCode < HttpURLConnection.HTTP_MULT_CHOICE) {
        logger.info(
            "CLOUDTASK: Successfully dispatched pending task via REST: "
                + taskName
                + " to queue "
                + effectiveQueue);
        return ErrorCode.OK;
      } else if (respCode == HttpURLConnection.HTTP_CONFLICT) {
        logger.info("CLOUDTASK: Pending task already exists (idempotency): " + taskName);
        return ErrorCode.TASK_ALREADY_EXISTS;
      } else if (respCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.warning("CLOUDTASK: Queue not found: " + queueName);
        return ErrorCode.UNKNOWN_QUEUE;
      } else {
        String err;
        try (InputStream es = conn.getErrorStream()) {
          err = es != null ? new String(es.readAllBytes(), UTF_8) : "HTTP " + respCode;
        }
        logger.severe("CLOUDTASK: REST dispatch failed with code " + respCode + ": " + err);
        return ErrorCode.INTERNAL_ERROR;
      }
    } catch (Exception e) {
      logger.log(
          Level.SEVERE,
          "CLOUDTASK: Exception dispatching pending task " + taskName + ": " + e.getMessage(),
          e);
      return ErrorCode.INTERNAL_ERROR;
    }
  }

  /**
   * Dispatches a single push task using {@link #dispatchPendingTask}.
   *
   * @param queueName the target task queue name
   * @param payload the JSON task payload stored in Datastore
   * @param entityId the Datastore entity ID for fallback task naming
   * @param taskName the chosen task name or {@code null}
   * @return a {@link ErrorCode} indicating success or failure code
   */
  static ErrorCode callCloudTasksViaSdk(
      String queueName, String payload, long entityId, String taskName) {
    return dispatchPendingTask(queueName, payload, entityId, taskName);
  }
}
