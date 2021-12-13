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

/**
 * A policy for high replication job application. Implementations can decide whether or not a new
 * job should apply and whether or not a job that initially failed to apply should roll forward on
 * subsequent attempts.
 *
 * <p>Some implementation details that are pertinent to implementors of this interface:
 *
 * <p>When a user performs a non-transactional Put(), a non-transactional Delete(), or a Commit() of
 * a transaction to which at least one transactional Put() or Delete() was added, the {@link
 * LocalDatastoreService} attempts to apply that mutation using a job associated with the entity
 * group of the mutation. The decision of whether or not the job should apply is delegated to {@link
 * HighRepJobPolicy#shouldApplyNewJob(Key)}.
 *
 * <p>Unapplied jobs may be rolled forward in two ways: when the consistency model dictates it, and
 * when an entity group is groomed. We'll discuss these in turn.
 *
 * <p>When the high replication consistency model guarantees that users will see the most up-to-date
 * values for an entity group, we roll forward unapplied jobs before returning any data from the
 * entity group. Specifically, a transactional Get() will roll forward any unapplied jobs in the
 * entity group, as will a non-transactional Get() with the read policy set to STRONG (the default).
 * A transactional Query (which is by definition an ancestor Query and therefore a scan over the
 * entities in a single entity group) will also roll forward any unapplied jobs associated with the
 * entity group of the query before the query executes.
 *
 * <p>Unapplied jobs can also be rolled forward when the entity group with these jobs is groomed. In
 * production, the groomer is a background process that is continuously scanning and rolling forward
 * unapplied jobs. We considered implementing something similar, but it's nearly impossible to write
 * tests in an environment where you have a background process randomly adjusting your persistent
 * state, so we opted for a different approach: the local datastore groomer looks at all unapplied
 * jobs on every Get() and every Query(), and for each unapplied job, consults {@link
 * HighRepJobPolicy#shouldRollForwardExistingJob(Key)} to see if that job should be rolled forward.
 * This simulates grooming, but in a deterministic manner that makes testing much more
 * straightforward.
 *
 * <p>Note, however, that when the groomer rolls these jobs forward, it does so in such a way that
 * the result of the operation being performed is not affected. This is important, because it
 * guarantees that when a job fails to apply, a user who reads the entity group without any strong
 * consistency guarantees will always see the "old" version of the data in the entity group at least
 * once. Without this guarantee we would have jobs that failed to apply but whose failure was
 * invisible, which defeats the purpose of what we're trying to simulate.
 *
 */
// It's a little wonky to be using a low-level API object in the local
// datastore, but it's not unheard of.  Here's the reason we do it: This
// interface is exposed via the testing apis so that developers can provide
// their own implementations, and I'd much rather have people coding against
// Key, which is part of the public api, than Onestore.Path, which is not.
public interface HighRepJobPolicy {

  /**
   * Returns {@code true} if the new job should apply according to the policy, {@code false}
   * otherwise.
   *
   * @param entityGroup A unique identifier for the entity group.
   */
  boolean shouldApplyNewJob(Key entityGroup);

  /**
   * Returns {@code true} if the existing job should roll forward according to the policy, {@code
   * false} otherwise.
   *
   * @param entityGroup A unique identifier for the entity group.
   */
  boolean shouldRollForwardExistingJob(Key entityGroup);
}
