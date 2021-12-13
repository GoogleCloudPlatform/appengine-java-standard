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
import java.util.Map;
import java.util.Random;

/**
 * An implementation of {@link HighRepJobPolicy} in which a user-defined percentage of jobs do not
 * apply. The percentage applies to both new (initial write) and existing (roll forward) jobs. The
 * user can also provide a random seed, which can be used to make the behavior of this class
 * deterministic in tests.
 *
 */
public class DefaultHighRepJobPolicy implements HighRepJobPolicy {

  /**
   * A long that will be used as the seed for the {@link Random} that determines whether or not a
   * job application attempt fails. Must be parsable via {@link Long#parseLong(String)}. If not
   * provided we use the current time (in millis) as the seed.
   */
  public static final String RANDOM_SEED_PROPERTY =
      "datastore.default_high_rep_job_policy_random_seed";

  /**
   * A float >= 0 and <= 100 representing the percentage of job application attempts that will fail.
   * Must be parsable via {@link Float#parseFloat(String)}. Any portion of the value beyond two
   * decimal places will be truncated. If not provided we set the percentage to 0.
   */
  public static final String UNAPPLIED_JOB_PERCENTAGE_PROPERTY =
      "datastore.default_high_rep_job_policy_unapplied_job_pct";

  /**
   * The default unapplied job percentage. Used if no value is provided for
   * UNAPPLIED_JOB_PERCENTAGE_PROPERTY.
   *
   * <p>Note that the dev appserver and dev appserver integration tests set this property explicitly
   * in order to provide HRD-like consistency by default.
   *
   * <p>Therefore this value is the default value only for unit tests.
   */
  private static final float DEFAULT_UNAPPLIED_JOB_PCT = 0;

  /**
   * The ceiling for all random values we generate. We have a maximum precision of 2 decimal places.
   */
  /* @VisibleForTesting */
  static final int RANDOM_CEILING = 100 * 100;

  /**
   * Multiply the unapplied job percentage by this number to map it into the range [0,
   * RANDOM_CEILING].
   */
  private static final int UNAPPLIED_JOB_PCT_MULTIPLIER = RANDOM_CEILING / 100;

  /* @VisibleForTesting */
  final Random random;

  /**
   * When we generate a random number that is greater than or equal to this cutoff, the job applies.
   * When the number is less than this cutoff, the job does not apply.
   */
  /* @VisibleForTesting */
  final int unappliedJobCutoff;

  /**
   * Constructs a {@code DefaultHighRepJobPolicy}.
   *
   * @param unappliedJobSeed The seed to use for random number generation.
   * @param unappliedJobPercentage The percentage of jobs that should fail to apply. This percentage
   *     applies to both new jobs and attempts to roll forward existing unapplied jobs.
   */
  public DefaultHighRepJobPolicy(long unappliedJobSeed, float unappliedJobPercentage) {
    random = new Random(unappliedJobSeed);
    if (unappliedJobPercentage < 0) {
      throw new IllegalArgumentException(
          String.format(
              "Unapplied job percentage must be >= 0 (received %f)", unappliedJobPercentage));
    }
    if (unappliedJobPercentage > 100.00d) {
      throw new IllegalArgumentException(
          String.format(
              "Unapplied job percentage must be <= 100.00 (received %f)", unappliedJobPercentage));
    }
    unappliedJobCutoff = (int) (unappliedJobPercentage * UNAPPLIED_JOB_PCT_MULTIPLIER);
  }

  /**
   * Constructs a {@code DefaultHighRepJobPolicy} from a property map.
   *
   * @param localDatastoreServiceProperties The property map.
   */
  DefaultHighRepJobPolicy(Map<String, String> localDatastoreServiceProperties) {
    this(
        getUnappliedJobSeedPropValue(localDatastoreServiceProperties),
        getUnappliedJobPctPropValue(localDatastoreServiceProperties));
  }

  private static float getUnappliedJobPctPropValue(
      Map<String, String> localDatastoreServiceProperties) {
    String unappliedJobPctProp =
        localDatastoreServiceProperties.get(UNAPPLIED_JOB_PERCENTAGE_PROPERTY);
    if (unappliedJobPctProp != null) {
      return Float.parseFloat(unappliedJobPctProp);
    }
    return DEFAULT_UNAPPLIED_JOB_PCT;
  }

  static long getUnappliedJobSeedPropValue(Map<String, String> localDatastoreServiceProperties) {
    String unappliedJobSeedProp = localDatastoreServiceProperties.get(RANDOM_SEED_PROPERTY);
    if (unappliedJobSeedProp != null) {
      return Long.parseLong(unappliedJobSeedProp);
    }
    // User didn't provide a seed so use the current time
    return System.currentTimeMillis();
  }

  private boolean shouldApply() {
    // Gives us a value x in between 0 (incl) and RANDOM_CEILING (excl).
    return nextRandomInt() >= unappliedJobCutoff;
  }

  @Override
  public boolean shouldApplyNewJob(Key entityGroup) {
    // We don't need the entityGroup param but other implementations of the
    // HighRepJobPolicy interface might.
    return shouldApply();
  }

  @Override
  public boolean shouldRollForwardExistingJob(Key entityGroup) {
    // We don't need the entityGroup param but other implementations of the
    // HighRepJobPolicy interface might.
    return shouldApply();
  }

  /* @VisibleForTesting */
  int nextRandomInt() {
    return random.nextInt(RANDOM_CEILING);
  }
}
