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

package com.google.appengine.api.datastore.dev;

import com.google.appengine.api.datastore.Key;
import com.google.apphosting.datastore.DatastoreV3Pb.Cost;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a job in the local datastore, which is a unit of transactional work to be performed
 * against a single entity group.
 *
 * <p>This class is not thread-safe. We rely on our callers to guarantee that there is no concurrent
 * access to a LocalDatastoreJob.
 *
 */
abstract class LocalDatastoreJob {
  final HighRepJobPolicy jobPolicy;
  final Key entityGroup;
  final long timestamp;
  boolean newJob = true;
  boolean applied = false;

  LocalDatastoreJob(HighRepJobPolicy jobPolicy, Key entityGroup, long timestamp) {
    if (jobPolicy == null) {
      throw new NullPointerException("jobPolicy cannot be null");
    }
    this.jobPolicy = jobPolicy;

    if (entityGroup == null) {
      throw new NullPointerException("entityGroup cannot be null");
    }
    this.entityGroup = entityGroup;

    this.timestamp = timestamp;
  }

  /**
   * Applies the job if the {@link #jobPolicy} says it should apply.
   *
   * @return whether or not the job was applied.
   */
  final boolean tryApply() {
    try {
      if (newJob) {
        // This is the first attempt to apply, so ask the policy about applying
        // a new job.
        if (jobPolicy.shouldApplyNewJob(entityGroup)) {
          apply();
          return true;
        }
      } else if (jobPolicy.shouldRollForwardExistingJob(entityGroup)) {
        // This is not the first attempt to apply, so we asked the policy about
        // rolling forward an existing job.
        apply();
        return true;
      }
      return false;
    } finally {
      newJob = false;
    }
  }

  /** Applies the job. The job policy is not consulted. */
  final void apply() {
    if (applied) {
      throw new IllegalStateException(
          String.format("Job on entity group %s was already applied.", entityGroup));
    }
    applied = true;
    applyInternal();
  }

  /**
   * Subclass specific job application logic. Do not call directly, use {@link #apply()} instead.
   */
  abstract void applyInternal();

  /**
   * Subclass specific job cost calculation logic. Call this instead of {@link #apply()} if you want
   * to know how much the job costs without having to apply it.
   *
   * @return The cost of the job.
   */
  abstract Cost calculateJobCost();

  /**
   * Returns the {@link EntityProto} with a given key, in the state it is after applying this job.
   */
  abstract @Nullable EntityProto getEntity(Reference key);

  /**
   * Returns the {@link EntityProto} with a given key, in the state it is just before applying this
   * job.
   */
  abstract @Nullable EntityProto getSnapshotEntity(Reference key);

  /**
   * Returns the timestamp at which a given {@link Reference key} was last mutated. If it was
   * modified by the current job, returns the job's timestamp.
   */
  abstract long getMutationTimestamp(Reference key);
}
