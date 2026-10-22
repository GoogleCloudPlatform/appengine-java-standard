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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceConfig;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.cloud.tasks.v2.AppEngineHttpRequest;
import com.google.cloud.tasks.v2.AppEngineRouting;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.RetryConfig;
import com.google.cloud.tasks.v2.Task;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor utility responsible for executing and dispatching pending Cloud Tasks stored in
 * Datastore ({@code _AE_PendingCloudTask}) to Google Cloud Tasks via the official v2 {@link
 * com.google.cloud.tasks.v2.CloudTasksClient} SDK.
 */
final class TaskProcessor {
  private static final Logger logger = Logger.getLogger(TaskProcessor.class.getName());

  private TaskProcessor() {}

  // Datastore Entity Kind & Properties
  static final String ENTITY_KIND_PENDING_TASK = "_AE_PendingCloudTask";
  /**
   * Prefix of {@code _AE_PendingCloudTaskBatch} key names: sorts after every hexadecimal character
   * (so bare-UUID keys from the previous version sort first) and is followed by the creation epoch
   * millis zero-padded to 19 digits, which keeps lexicographic order equal to numeric order for
   * any positive long.
   */
  static final String TIMESTAMPED_BATCH_KEY_PREFIX = "z";
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

  private static final String DEFAULT_QUEUE_NAME = "default";
  private static final String JSON_FIELD_TASK = "task";

  // Sweeper & Processing Configuration
  static final long REALTIME_DISPATCH_GRACE_PERIOD_MILLIS = 60_000L;
  static final long MAX_RETRY_COUNT = 5L;
  static final int SWEEP_BATCH_LIMIT = 500;
  /**
   * Upper bound on {@code PENDING} entities with a pre-timestamp batch key (bare UUID, or none)
   * read per sweep. Such keys are not creation-ordered, so grace-period eligibility must be
   * checked in memory; this bound keeps one sweep's Datastore work finite while that transitional
   * backlog drains.
   */
  static final int SWEEP_SCAN_LIMIT = 10 * SWEEP_BATCH_LIMIT;
  static final Duration LOCK_DURATION = Duration.ofMinutes(1);
  static final double DATASTORE_DEADLINE_SECONDS = 10.0;

  /**
   * Asynchronously processes a list of pending task entity keys after an optional delay.
   *
   * @param keys the list of Datastore keys for {@code _AE_PendingCloudTask} entities
   * @param delayMillis milliseconds to wait before executing
   */
  static void processPendingTaskKeysAsync(List<Key> keys, long delayMillis) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (Key key : keys) {
      if (key == null) {
        continue;
      }
      CompletableFuture<?> unused =
          CloudTasksClientWrapper.runAsyncWithEnv(
              () -> {
                try {
                  if (delayMillis > 0) {
                    try {
                      Thread.sleep(delayMillis);
                    } catch (InterruptedException ignored) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                  }
                  processSingleTaskWithNamespace(key, false);
                } catch (Throwable t) {
                  logger.log(
                      Level.SEVERE, "Error in processPendingTaskKeysAsync: " + t.getMessage(), t);
                }
              });
    }
  }

  /**
   * Processes a list of pending task entity keys stored in Datastore concurrently, indicating
   * whether invocation originated from the background sweeper cron job.
   *
   * @param keys the list of Datastore keys for {@code _AE_PendingCloudTask} entities to process
   * @param handledBySweeper {@code true} if triggered by the cron sweeper; {@code false} if
   *     triggered by fast-path
   */
  static void processPendingTaskKeys(List<Key> keys, boolean handledBySweeper) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    List<Key> validKeys = new ArrayList<>(keys.size());
    for (Key key : keys) {
      if (key != null) {
        validKeys.add(key);
      }
    }
    if (validKeys.isEmpty()) {
      return;
    }
    if (validKeys.size() == 1) {
      processSingleTaskWithNamespace(validKeys.get(0), handledBySweeper);
      return;
    }
    List<CompletableFuture<Void>> futures = new ArrayList<>(validKeys.size());
    for (Key key : validKeys) {
      futures.add(
          CloudTasksClientWrapper.runAsyncWithEnv(
              () -> processSingleTaskWithNamespace(key, handledBySweeper)));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
  }

  private static void processSingleTaskWithNamespace(Key key, boolean handledBySweeper) {
    String previousNamespace = NamespaceManager.get();
    NamespaceManager.set("");
    try {
      DatastoreService ds =
          DatastoreServiceFactory.getDatastoreService(
              DatastoreServiceConfig.Builder.withDeadline(DATASTORE_DEADLINE_SECONDS));
      processSingleTask(ds, key, handledBySweeper);
    } catch (RuntimeException e) {
      logger.log(
          Level.SEVERE, "Failed to process pending task " + key + ": " + e.getMessage(), e);
    } finally {
      NamespaceManager.set(previousNamespace);
    }
  }

  /**
   * Sweeps Datastore for orphaned or un-dispatched {@code _AE_PendingCloudTask} entities and
   * processes them. See {@link #findSweepCandidates} for the selection rules; only built-in
   * single-property indexes are used, so no user-defined composite index is required.
   *
   * @return the number of swept tasks processed
   */
  static int sweep() {
    String previousNamespace = NamespaceManager.get();
    NamespaceManager.set("");
    try {
      DatastoreService ds =
          DatastoreServiceFactory.getDatastoreService(
              DatastoreServiceConfig.Builder.withDeadline(DATASTORE_DEADLINE_SECONDS));
      List<Key> keysToProcess = findSweepCandidates(ds, Instant.now());
      if (!keysToProcess.isEmpty()) {
        logger.info("CLOUDTASK: Sweeper found " + keysToProcess.size() + " tasks to process.");
        processPendingTaskKeys(keysToProcess, true);
      }
      return keysToProcess.size();
    } finally {
      NamespaceManager.set(previousNamespace);
    }
  }

  /**
   * Returns up to {@link #SWEEP_BATCH_LIMIT} keys of overdue tasks: {@code PROCESSING} tasks whose
   * lock has expired (oldest lock first), then {@code PENDING} tasks past the grace period (oldest
   * first). Every query is served by a built-in single-property index, so no composite index is
   * required:
   *
   * <ul>
   *   <li>{@code 0 < lock_expires <= now}: {@code lock_expires} is non-null only while a task is
   *       {@code PROCESSING}, so this is exactly the set of crashed dispatches.
   *   <li>{@code status == PROCESSING AND lock_expires == null}: equality-only merge join; keeps
   *       the pre-existing fallback for entities locked without an expiry (treated as expired once
   *       older than {@link #LOCK_DURATION}).
   *   <li>{@code status == PENDING AND z0 <= __key__ < z(cutoff + 1)}: {@code __key__} filters are
   *       planned natively alongside one equality filter. Because timestamped batch keys embed the
   *       creation millis, this range is exactly the tasks created on or before the grace cutoff.
   *   <li>{@code status == PENDING AND __key__ < z0}: tasks keyed by an older SDK (bare UUID, or no
   *       batch key). Those keys are not creation-ordered, so up to {@link #SWEEP_SCAN_LIMIT} are
   *       read and merged with the timestamped candidates by {@code created}; ordering is exact
   *       within that window.
   * </ul>
   */
  static List<Key> findSweepCandidates(DatastoreService ds, Instant now) {
    List<Key> keysToProcess = new ArrayList<>();
    Date nowDate = Date.from(now);

    Query expiredLocks =
        new Query(ENTITY_KIND_PENDING_TASK)
            .setFilter(
                CompositeFilterOperator.and(
                    // > EPOCH excludes lock_expires == null (null sorts below every date), i.e.
                    // every PENDING/FAILED task; otherwise they would fill the batch first.
                    new FilterPredicate(
                        PROPERTY_LOCK_EXPIRES,
                        FilterOperator.GREATER_THAN,
                        Date.from(Instant.EPOCH)),
                    new FilterPredicate(
                        PROPERTY_LOCK_EXPIRES, FilterOperator.LESS_THAN_OR_EQUAL, nowDate)))
            .addSort(PROPERTY_LOCK_EXPIRES, Query.SortDirection.ASCENDING);
    for (Entity entity : ds.prepare(expiredLocks).asIterable(fetchUpTo(SWEEP_BATCH_LIMIT))) {
      if (isActionableForSweep(entity, now)) {
        keysToProcess.add(entity.getKey());
      } else {
        clearStaleLock(ds, entity.getKey());
      }
    }
    int remaining = SWEEP_BATCH_LIMIT - keysToProcess.size();
    if (remaining <= 0) {
      return keysToProcess;
    }

    Query unlockedProcessing =
        new Query(ENTITY_KIND_PENDING_TASK)
            .setFilter(
                CompositeFilterOperator.and(
                    new FilterPredicate(PROPERTY_STATUS, FilterOperator.EQUAL, STATUS_PROCESSING),
                    new FilterPredicate(PROPERTY_LOCK_EXPIRES, FilterOperator.EQUAL, null)));
    for (Entity entity : ds.prepare(unlockedProcessing).asIterable(fetchUpTo(remaining))) {
      if (isActionableForSweep(entity, now)) {
        keysToProcess.add(entity.getKey());
      }
    }
    remaining = SWEEP_BATCH_LIMIT - keysToProcess.size();
    if (remaining <= 0) {
      return keysToProcess;
    }

    // Keep only key and creation time per candidate; entities carry the task payload (up to 100KB)
    // and must not be retained across the scan.
    List<SweepCandidate> pendingCandidates = new ArrayList<>();
    Key minTimestampedKey = timestampedBoundaryKey(Instant.EPOCH);
    Key cutoffTimestampedKey =
        timestampedBoundaryKey(now.minusMillis(REALTIME_DISPATCH_GRACE_PERIOD_MILLIS).plusMillis(1));
    Query timestampedPending =
        new Query(ENTITY_KIND_PENDING_TASK)
            .setFilter(
                CompositeFilterOperator.and(
                    new FilterPredicate(PROPERTY_STATUS, FilterOperator.EQUAL, STATUS_PENDING),
                    new FilterPredicate(
                        Entity.KEY_RESERVED_PROPERTY,
                        FilterOperator.GREATER_THAN_OR_EQUAL,
                        minTimestampedKey),
                    new FilterPredicate(
                        Entity.KEY_RESERVED_PROPERTY,
                        FilterOperator.LESS_THAN,
                        cutoffTimestampedKey)));
    for (Entity entity : ds.prepare(timestampedPending).asIterable(fetchUpTo(remaining))) {
      pendingCandidates.add(SweepCandidate.of(entity)); // key range implies created <= cutoff
    }

    Query legacyPending =
        new Query(ENTITY_KIND_PENDING_TASK)
            .setFilter(
                CompositeFilterOperator.and(
                    new FilterPredicate(PROPERTY_STATUS, FilterOperator.EQUAL, STATUS_PENDING),
                    new FilterPredicate(
                        Entity.KEY_RESERVED_PROPERTY, FilterOperator.LESS_THAN, minTimestampedKey)));
    for (Entity entity : ds.prepare(legacyPending).asIterable(fetchUpTo(SWEEP_SCAN_LIMIT))) {
      if (isActionableForSweep(entity, now)) {
        pendingCandidates.add(SweepCandidate.of(entity));
      }
    }

    pendingCandidates.sort(
        Comparator.comparing(SweepCandidate::created).thenComparing(SweepCandidate::key));
    for (int i = 0; i < Math.min(remaining, pendingCandidates.size()); i++) {
      keysToProcess.add(pendingCandidates.get(i).key());
    }
    return keysToProcess;
  }

  /** Key and creation time of a {@code PENDING} task, without the entity's payload. */
  private record SweepCandidate(Key key, Instant created) {
    static SweepCandidate of(Entity entity) {
      Object createdObj = entity.getProperty(PROPERTY_CREATED);
      Instant created = (createdObj instanceof Date d) ? d.toInstant() : Instant.MIN;
      return new SweepCandidate(entity.getKey(), created);
    }
  }

  private static FetchOptions fetchUpTo(int limit) {
    return FetchOptions.Builder.withLimit(limit).chunkSize(Math.min(limit, SWEEP_BATCH_LIMIT));
  }

  /**
   * Clears {@code lock_expires} on an entity that is not {@code PROCESSING} yet carries an expired
   * lock. No code path writes such an entity, but if one existed it would be returned by every
   * expired-lock scan ahead of real crashed dispatches and, with enough of them, crowd those out
   * permanently. The entity is re-read in a transaction so a lock acquired concurrently by a
   * dispatcher is never overwritten.
   */
  private static void clearStaleLock(DatastoreService ds, Key key) {
    Transaction txn = ds.beginTransaction();
    try {
      Entity current = ds.get(txn, key);
      if (Objects.equals(current.getProperty(PROPERTY_STATUS), STATUS_PROCESSING)
          || current.getProperty(PROPERTY_LOCK_EXPIRES) == null) {
        txn.rollback();
        return;
      }
      current.setProperty(PROPERTY_LOCK_EXPIRES, null);
      ds.put(txn, current);
      txn.commit();
      logger.warning("CLOUDTASK: Cleared stale lock_expires on non-PROCESSING task " + key);
    } catch (EntityNotFoundException enfe) {
      // Deleted concurrently; nothing to heal.
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Failed to clear stale lock on " + key + ": " + e.getMessage());
    } finally {
      if (txn.isActive()) {
        try {
          txn.rollback();
        } catch (RuntimeException ignored) {
          // Ignore rollback failure
        }
      }
    }
  }

  /**
   * Returns a key that sorts at the start of all {@code _AE_PendingCloudTask} keys whose batch was
   * created at {@code at}: every real batch key has a {@code -<UUID>} suffix after the timestamp
   * and therefore compares greater.
   */
  private static Key timestampedBoundaryKey(Instant at) {
    Key batchKey =
        KeyFactory.createKey(
            CloudTasksClientWrapper.ENTITY_KIND_PENDING_TASK_BATCH,
            String.format(
                Locale.ROOT, "%s%019d", TIMESTAMPED_BATCH_KEY_PREFIX, at.toEpochMilli()));
    return KeyFactory.createKey(batchKey, ENTITY_KIND_PENDING_TASK, 1L);
  }

  /**
   * Returns whether a {@code PENDING} or {@code PROCESSING} entity is overdue and should be
   * dispatched by the sweeper.
   */
  private static boolean isActionableForSweep(Entity entity, Instant now) {
    String status = (String) entity.getProperty(PROPERTY_STATUS);
    Object createdObj = entity.getProperty(PROPERTY_CREATED);
    Instant created = (createdObj instanceof Date d) ? d.toInstant() : null;
    if (Objects.equals(status, STATUS_PENDING)) {
      return created == null
          || !now.isBefore(created.plusMillis(REALTIME_DISPATCH_GRACE_PERIOD_MILLIS));
    }
    if (Objects.equals(status, STATUS_PROCESSING)) {
      Object lockObj = entity.getProperty(PROPERTY_LOCK_EXPIRES);
      Instant lockExpires = (lockObj instanceof Date d) ? d.toInstant() : null;
      if (lockExpires != null) {
        return !now.isBefore(lockExpires);
      }
      return created == null || !now.isBefore(created.plus(LOCK_DURATION));
    }
    return false;
  }

  private static void processSingleTask(DatastoreService ds, Key key, boolean handledBySweeper) {
    Entity entity = null;
    Transaction lockTxn = null;
    Date acquiredLockExpires = null;
    try {
      lockTxn = ds.beginTransaction();
      entity = ds.get(lockTxn, key);

      String status = (String) entity.getProperty(PROPERTY_STATUS);
      if (Objects.equals(status, STATUS_DONE) || Objects.equals(status, STATUS_ALREADY_EXISTS)) {
        lockTxn.rollback();
        return;
      }

      Object lockObj = entity.getProperty(PROPERTY_LOCK_EXPIRES);
      Instant lockExpires = (lockObj instanceof Date d) ? d.toInstant() : null;
      if (Objects.equals(status, STATUS_PROCESSING) && lockExpires != null) {
        if (Instant.now().isBefore(lockExpires)) {
          // Still actively processing
          lockTxn.rollback();
          return;
        }
      }

      acquiredLockExpires = Date.from(Instant.now().plus(LOCK_DURATION));
      entity.setProperty(PROPERTY_STATUS, STATUS_PROCESSING);
      entity.setProperty(PROPERTY_LOCK_EXPIRES, acquiredLockExpires);
      entity.setProperty(PROPERTY_HANDLED_BY_SWEEPER, handledBySweeper);
      ds.put(lockTxn, entity);
      lockTxn.commit();
    } catch (EntityNotFoundException enfe) {
      if (lockTxn != null && lockTxn.isActive()) {
        try {
          lockTxn.rollback();
        } catch (RuntimeException ignored) {
          // Ignore rollback failure
        }
      }
      // Entity was deleted or rolled back
      return;
    } catch (RuntimeException e) {
      if (lockTxn != null && lockTxn.isActive()) {
        try {
          lockTxn.rollback();
        } catch (RuntimeException ignored) {
          // Ignore rollback failure
        }
      }
      logger.log(
          Level.WARNING, "Failed to acquire lock for task " + key.getId() + ": " + e.getMessage());
      return;
    }

    String queueName = (String) entity.getProperty(PROPERTY_QUEUE_NAME);
    Object payloadObj = entity.getProperty(PROPERTY_CLOUD_TASK_PAYLOAD);
    String payload =
        (payloadObj instanceof Text text)
            ? text.getValue()
            : (payloadObj != null ? payloadObj.toString() : null);
    long entityId = key.getId();

    boolean success;
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
      Transaction updateTxn = null;
      try {
        updateTxn = ds.beginTransaction();
        Entity currentEntity = ds.get(updateTxn, key);
        String currentStatus = (String) currentEntity.getProperty(PROPERTY_STATUS);
        Object currentLockObj = currentEntity.getProperty(PROPERTY_LOCK_EXPIRES);
        if (!Objects.equals(currentStatus, STATUS_PROCESSING)
            || !(currentLockObj instanceof Date currentLock)
            || acquiredLockExpires == null
            || !currentLock.toInstant().equals(acquiredLockExpires.toInstant())) {
          updateTxn.rollback();
          return;
        }
        Object retryObj = currentEntity.getProperty(PROPERTY_RETRY_COUNT);
        long retryCount = (retryObj instanceof Number num) ? num.longValue() : 0L;
        retryCount++;
        currentEntity.setProperty(PROPERTY_RETRY_COUNT, retryCount);
        currentEntity.setProperty(
            PROPERTY_LAST_ERROR, "Cloud Tasks Client SDK call failed with " + resCode);
        if (retryCount >= MAX_RETRY_COUNT) {
          currentEntity.setProperty(PROPERTY_STATUS, STATUS_FAILED);
        } else {
          currentEntity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
        }
        currentEntity.setProperty(PROPERTY_LOCK_EXPIRES, null);
        ds.put(updateTxn, currentEntity);
        updateTxn.commit();
        if (retryCount >= MAX_RETRY_COUNT) {
          logger.severe(
              "CLOUDTASK: Task "
                  + key
                  + " permanently failed after "
                  + retryCount
                  + " attempts and will not be retried: "
                  + resCode);
        } else {
          logger.warning(
              "CLOUDTASK: Failed to process task " + entityId + ", retry count: " + retryCount);
        }
      } catch (EntityNotFoundException enfe) {
        if (updateTxn != null && updateTxn.isActive()) {
          try {
            updateTxn.rollback();
          } catch (RuntimeException ignored) {
            // Ignore rollback failure
          }
        }
      } catch (RuntimeException putErr) {
        if (updateTxn != null && updateTxn.isActive()) {
          try {
            updateTxn.rollback();
          } catch (RuntimeException ignored) {
            // Ignore rollback failure
          }
        }
        logger.severe(
            "Failed to record error state for task " + entityId + ": " + putErr.getMessage());
      }
    }
  }

  /**
   * Dispatches a single pending push task stored in Datastore to Google Cloud Tasks via the
   * official v2 {@link com.google.cloud.tasks.v2.CloudTasksClient} SDK.
   *
   * @param queueName the target task queue name
   * @param payload the JSON task payload stored in Datastore
   * @param entityId the Datastore entity ID for fallback task naming
   * @param taskName the chosen task name or {@code null}
   * @return an {@link ErrorCode} indicating success or failure code
   */
  static ErrorCode dispatchPendingTask(
      String queueName, String payload, long entityId, String taskName) {
    String projectId = CloudTasksClientWrapper.getProjectId();
    String location = CloudTasksClientWrapper.getLocation();
    String effectiveQueue = isNullOrEmpty(queueName) ? DEFAULT_QUEUE_NAME : queueName;
    String parentQueue =
        "projects/" + projectId + "/locations/" + location + "/queues/" + effectiveQueue;

    try {
      Task task = parseTaskFromJson(payload, parentQueue, taskName);
      CreateTaskRequest req =
          CreateTaskRequest.newBuilder().setParent(parentQueue).setTask(task).build();
      Task unused = CloudTasksClientWrapper.getClient().createTask(req);
      logger.info(
          "CLOUDTASK: Successfully dispatched pending task via Client SDK: "
              + taskName
              + " to queue "
              + effectiveQueue);
      return ErrorCode.OK;
    } catch (RuntimeException e) {
      if (CloudTasksClientWrapper.isAlreadyExists(e)) {
        logger.info("CLOUDTASK: Pending task already exists (idempotency): " + taskName);
        return ErrorCode.TASK_ALREADY_EXISTS;
      }
      if (CloudTasksClientWrapper.isUnknownQueue(e)) {
        logger.warning("CLOUDTASK: Queue not found: " + queueName);
        return ErrorCode.UNKNOWN_QUEUE;
      }
      logger.log(
          Level.SEVERE,
          "CLOUDTASK: Exception dispatching pending task " + taskName + ": " + e.getMessage(),
          e);
      return ErrorCode.INTERNAL_ERROR;
    }
  }

  private static Task parseTaskFromJson(
      String payload, String parentQueue, String fallbackTaskName) {
    Task.Builder taskBuilder = Task.newBuilder();
    if (!isNullOrEmpty(payload) && !payload.trim().isEmpty()) {
      JsonObject parsed = JsonParser.parseString(payload).getAsJsonObject();
      JsonObject taskJson =
          parsed.has(JSON_FIELD_TASK) && parsed.get(JSON_FIELD_TASK).isJsonObject()
              ? parsed.getAsJsonObject(JSON_FIELD_TASK)
              : parsed;

      if (taskJson.has(CloudTasksClientWrapper.JSON_FIELD_NAME)) {
        taskBuilder.setName(taskJson.get(CloudTasksClientWrapper.JSON_FIELD_NAME).getAsString());
      }
      if (taskJson.has(CloudTasksClientWrapper.JSON_FIELD_SCHEDULE_TIME)) {
        Instant inst =
            Instant.parse(
                taskJson.get(CloudTasksClientWrapper.JSON_FIELD_SCHEDULE_TIME).getAsString());
        taskBuilder.setScheduleTime(
            Timestamp.newBuilder()
                .setSeconds(inst.getEpochSecond())
                .setNanos(inst.getNano())
                .build());
      }
      if (taskJson.has(CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_HTTP_REQUEST)
          && taskJson
              .get(CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_HTTP_REQUEST)
              .isJsonObject()) {
        JsonObject httpJson =
            taskJson.getAsJsonObject(
                CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_HTTP_REQUEST);
        AppEngineHttpRequest.Builder httpBuilder = AppEngineHttpRequest.newBuilder();
        if (httpJson.has(CloudTasksClientWrapper.JSON_FIELD_HTTP_METHOD)) {
          httpBuilder.setHttpMethod(
              HttpMethod.valueOf(
                  httpJson.get(CloudTasksClientWrapper.JSON_FIELD_HTTP_METHOD).getAsString()));
        }
        if (httpJson.has(CloudTasksClientWrapper.JSON_FIELD_RELATIVE_URI)) {
          httpBuilder.setRelativeUri(
              httpJson.get(CloudTasksClientWrapper.JSON_FIELD_RELATIVE_URI).getAsString());
        }
        if (httpJson.has(CloudTasksClientWrapper.JSON_FIELD_BODY)) {
          byte[] decoded =
              Base64.getDecoder()
                  .decode(httpJson.get(CloudTasksClientWrapper.JSON_FIELD_BODY).getAsString());
          httpBuilder.setBody(ByteString.copyFrom(decoded));
        }
        if (httpJson.has(CloudTasksClientWrapper.JSON_FIELD_HEADERS)
            && httpJson.get(CloudTasksClientWrapper.JSON_FIELD_HEADERS).isJsonObject()) {
          JsonObject headersJson =
              httpJson.getAsJsonObject(CloudTasksClientWrapper.JSON_FIELD_HEADERS);
          for (Map.Entry<String, JsonElement> entry : headersJson.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
              httpBuilder.putHeaders(entry.getKey(), entry.getValue().getAsString());
            }
          }
        }
        if (httpJson.has(CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_ROUTING)
            && httpJson
                .get(CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_ROUTING)
                .isJsonObject()) {
          JsonObject routingJson =
              httpJson.getAsJsonObject(CloudTasksClientWrapper.JSON_FIELD_APP_ENGINE_ROUTING);
          AppEngineRouting.Builder routingBuilder = AppEngineRouting.newBuilder();
          if (routingJson.has(CloudTasksClientWrapper.JSON_FIELD_SERVICE)) {
            routingBuilder.setService(
                routingJson.get(CloudTasksClientWrapper.JSON_FIELD_SERVICE).getAsString());
          }
          if (routingJson.has(CloudTasksClientWrapper.JSON_FIELD_VERSION)) {
            routingBuilder.setVersion(
                routingJson.get(CloudTasksClientWrapper.JSON_FIELD_VERSION).getAsString());
          }
          if (routingJson.has(CloudTasksClientWrapper.JSON_FIELD_INSTANCE)) {
            routingBuilder.setInstance(
                routingJson.get(CloudTasksClientWrapper.JSON_FIELD_INSTANCE).getAsString());
          }
          httpBuilder.setAppEngineRouting(routingBuilder.build());
        }
        taskBuilder.setAppEngineHttpRequest(httpBuilder.build());
      }
      if (taskJson.has(CloudTasksClientWrapper.JSON_FIELD_RETRY_CONFIG)
          && taskJson.get(CloudTasksClientWrapper.JSON_FIELD_RETRY_CONFIG).isJsonObject()) {
        JsonObject retryJson =
            taskJson.getAsJsonObject(CloudTasksClientWrapper.JSON_FIELD_RETRY_CONFIG);
        RetryConfig.Builder retryBuilder = RetryConfig.newBuilder();
        if (retryJson.has(CloudTasksClientWrapper.JSON_FIELD_MAX_ATTEMPTS)) {
          retryBuilder.setMaxAttempts(
              retryJson.get(CloudTasksClientWrapper.JSON_FIELD_MAX_ATTEMPTS).getAsInt());
        }
        if (retryJson.has(CloudTasksClientWrapper.JSON_FIELD_MAX_RETRY_DURATION)) {
          retryBuilder.setMaxRetryDuration(
              parseProtoDurationSeconds(
                  retryJson
                      .get(CloudTasksClientWrapper.JSON_FIELD_MAX_RETRY_DURATION)
                      .getAsString()));
        }
        if (retryJson.has(CloudTasksClientWrapper.JSON_FIELD_MIN_BACKOFF)) {
          retryBuilder.setMinBackoff(
              parseProtoDurationSeconds(
                  retryJson.get(CloudTasksClientWrapper.JSON_FIELD_MIN_BACKOFF).getAsString()));
        }
        if (retryJson.has(CloudTasksClientWrapper.JSON_FIELD_MAX_BACKOFF)) {
          retryBuilder.setMaxBackoff(
              parseProtoDurationSeconds(
                  retryJson.get(CloudTasksClientWrapper.JSON_FIELD_MAX_BACKOFF).getAsString()));
        }
        if (retryJson.has(CloudTasksClientWrapper.JSON_FIELD_MAX_DOUBLINGS)) {
          retryBuilder.setMaxDoublings(
              retryJson.get(CloudTasksClientWrapper.JSON_FIELD_MAX_DOUBLINGS).getAsInt());
        }
        taskBuilder.setRetryConfig(retryBuilder.build());
      }
    }
    if (taskBuilder.getName().isEmpty() && !isNullOrEmpty(fallbackTaskName)) {
      taskBuilder.setName(parentQueue + "/tasks/" + fallbackTaskName);
    }
    return taskBuilder.build();
  }

  private static com.google.protobuf.Duration parseProtoDurationSeconds(String durationStr) {
    String trimmed = durationStr.trim();
    if (trimmed.endsWith("s")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return CloudTasksClientWrapper.toProtoDuration(Double.parseDouble(trimmed));
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
