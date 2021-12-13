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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.DefaultHighRepJobPolicy;
import com.google.appengine.api.datastore.dev.HighRepJobPolicy;
import com.google.appengine.api.datastore.dev.LocalDatastoreService;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.AutoIdAllocationPolicy;
import com.google.appengine.api.datastore.dev.LocalDatastoreV3Service;
import com.google.appengine.tools.development.ApiProxyLocal;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Config for accessing the local datastore service in tests.
 * Default behavior is to configure the local datastore to only store data
 * in-memory, to not write anything to disk, and for all jobs to apply on the
 * first attempt.  {@link #tearDown()} wipes
 * out all in-memory state so that the datastore is empty at the end of every
 * test.
 *
 */
public final class LocalDatastoreServiceTestConfig implements LocalServiceTestConfig {

  private boolean noStorage = true;
  private AutoIdAllocationPolicy autoIdAllocationPolicy = AutoIdAllocationPolicy.SEQUENTIAL;
  @Nullable private Integer maxQueryLifetimeMs;
  @Nullable private Integer maxTxnLifetimeMs;
  @Nullable private Integer storeDelayMs;
  @Nullable private String backingStoreLocation;
  private boolean noIndexAutoGen = true;
  @Nullable private Long defaultHighRepJobPolicyRandomSeed;
  @Nullable private Float defaultHighRepJobPolicyUnappliedJobPercentage;
  @Nullable private Class<? extends HighRepJobPolicy> alternateHighRepJobPolicyClass;

  public boolean isNoStorage() {
    return noStorage;
  }

  /**
   * True to put the datastore into "memory-only" mode.
   * @param noStorage
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setNoStorage(boolean noStorage) {
    this.noStorage = noStorage;
    return this;
  }

  public AutoIdAllocationPolicy getAutoIdAllocationPolicy() {
    return autoIdAllocationPolicy;
  }

  /**
   * Dictate how Put() assigns auto IDs.
   * @param autoIdAllocationPolicy
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setAutoIdAllocationPolicy(
      AutoIdAllocationPolicy autoIdAllocationPolicy) {
    this.autoIdAllocationPolicy = autoIdAllocationPolicy;
    return this;
  }

  public Integer getMaxQueryLifetimeMs() {
    return maxQueryLifetimeMs;
  }

  /**
   * Sets how long a query can stay "live" before we expire it.
   * @param maxQueryLifetimeMs
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setMaxQueryLifetimeMs(int maxQueryLifetimeMs) {
    this.maxQueryLifetimeMs = maxQueryLifetimeMs;
    return this;
  }

  public Integer getMaxTxnLifetimeMs() {
    return maxTxnLifetimeMs;
  }

  /**
   * Sets how long a txn can stay "live" before we expire it.
   * @param maxTxnLifetimeMs
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setMaxTxnLifetimeMs(int maxTxnLifetimeMs) {
    this.maxTxnLifetimeMs = maxTxnLifetimeMs;
    return this;
  }

  public Integer getStoreDelayMs() {
    return storeDelayMs;
  }

  /**
   * Sets how long to wait before updating the persistent store.  Only useful
   * if {@link #isNoStorage()} returns {@code false}.
   * @param storeDelayMs
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setStoreDelayMs(int storeDelayMs) {
    this.storeDelayMs = storeDelayMs;
    return this;
  }

  public String getBackingStoreLocation() {
    return backingStoreLocation;
  }


  /**
   * Where to read/store the datastore from/to.
   * @param backingStoreLocation
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setBackingStoreLocation(String backingStoreLocation) {
    this.backingStoreLocation = backingStoreLocation;
    return this;
  }

  public boolean isNoIndexAutoGen() {
    return noIndexAutoGen;
  }

  /**
   * True to prevent the datastore from writing the auto-generated index file.
   * @param noIndexAutoGen
   * @return {@code this} (for chaining)
   */
  public LocalDatastoreServiceTestConfig setNoIndexAutoGen(boolean noIndexAutoGen) {
    this.noIndexAutoGen = noIndexAutoGen;
    return this;
  }

  public Long getDefaultHighRepJobPolicyRandomSeed() {
    return defaultHighRepJobPolicyRandomSeed;
  }

  /**
   * A seed for a {@link java.util.Random} used by
   * {@link DefaultHighRepJobPolicy} to determine whether or not a job
   * application attempt fails.  This method cannot be used in combination with
   * {@link #setAlternateHighRepJobPolicyClass(Class)}.
   *
   * @param defaultHighRepJobPolicyRandomSeed The random seed.
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException If
   * {@link #setAlternateHighRepJobPolicyClass(Class)} has been called.
   */
  public LocalDatastoreServiceTestConfig setDefaultHighRepJobPolicyRandomSeed(
      long defaultHighRepJobPolicyRandomSeed) {
    if (alternateHighRepJobPolicyClass != null) {
      throw new IllegalArgumentException(
          "setAlternateHighRepJobPolicyClass() has already been called.");
    }
    this.defaultHighRepJobPolicyRandomSeed = defaultHighRepJobPolicyRandomSeed;
    return this;
  }

  public Float getDefaultHighRepJobPolicyUnappliedJobPercentage() {
    return defaultHighRepJobPolicyUnappliedJobPercentage;
  }

  /**
   * The percentage of job application attempts that will fail.  Must be
   * >= 0 and <= 100.  This validation is performed during the initialization
   * of {@link LocalDatastoreService}, not in the setter, so it will not fail
   * fast.  In addition, any portion of the value beyond two decimal places
   * will be truncated.  This method cannot be used in combination with
   * {@link #setAlternateHighRepJobPolicyClass(Class)}.
   *
   * @param defaultHighRepJobPolicyUnappliedJobPercentage The percentage of job
   * application attempts that should fail.
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException If
   * {@link #setAlternateHighRepJobPolicyClass(Class)} has been called.
   */
  public LocalDatastoreServiceTestConfig setDefaultHighRepJobPolicyUnappliedJobPercentage(
      float defaultHighRepJobPolicyUnappliedJobPercentage) {
    if (alternateHighRepJobPolicyClass != null) {
      throw new IllegalArgumentException(
          "setAlternateHighRepJobPolicyClass() has already been called.");
    }
    this.defaultHighRepJobPolicyUnappliedJobPercentage =
        defaultHighRepJobPolicyUnappliedJobPercentage;
    return this;
  }

  public Class<? extends HighRepJobPolicy> getAlternateHighRepJobPolicyClass() {
    return alternateHighRepJobPolicyClass;
  }

  /**
   * An alternate {@link HighRepJobPolicy} implementation.  The class must have
   * a no-arg constructor, but this validation is performed during the
   * iniitialization of {@link LocalDatastoreService}, not in the setter, so it
   * will not fail fast.  This method cannot be used in combination with
   * {@link #setDefaultHighRepJobPolicyRandomSeed(long)} or
   * {@link #setDefaultHighRepJobPolicyUnappliedJobPercentage(float)}.
   *
   * @param alternateHighRepJobPolicyClass The {@link HighRepJobPolicy}
   * implementation.
   * @return {@code this} (for chaining)
   * @throws IllegalArgumentException If
   * {@link #setDefaultHighRepJobPolicyRandomSeed(long)} or
   * {@link #setDefaultHighRepJobPolicyUnappliedJobPercentage(float)} has been
   * called.
   */
  public LocalDatastoreServiceTestConfig setAlternateHighRepJobPolicyClass(
      Class<? extends HighRepJobPolicy> alternateHighRepJobPolicyClass) {
    if (defaultHighRepJobPolicyRandomSeed != null) {
      throw new IllegalArgumentException(
          "setDefaultHighRepJobPolicyRandomSeed() has already been called.");
    }
    if (defaultHighRepJobPolicyUnappliedJobPercentage != null) {
      throw new IllegalArgumentException(
          "defaultHighRepJobPolicyUnappliedJobPercentage() has already been called.");
    }
    this.alternateHighRepJobPolicyClass = alternateHighRepJobPolicyClass;
    return this;
  }

  /**
   * Make the datastore a high-replication datastore, but with all jobs applying
   * immediately (simplifies tests that use eventually-consistent queries).
   */
  public LocalDatastoreServiceTestConfig setApplyAllHighRepJobPolicy() {
    return setAlternateHighRepJobPolicyClass(ApplyAll.class);
  }

  static class ApplyAll implements HighRepJobPolicy {
    @Override
    public boolean shouldApplyNewJob(Key entityGroup) {
      return true;
    }

    @Override
    public boolean shouldRollForwardExistingJob(Key entityGroup) {
      return true;
    }
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(LocalDatastoreService.NO_STORAGE_PROPERTY, Boolean.toString(noStorage));
    proxy.setProperty(LocalDatastoreService.AUTO_ID_ALLOCATION_POLICY_PROPERTY,
        autoIdAllocationPolicy.toString());

    if (maxQueryLifetimeMs != null) {
      proxy.setProperty(LocalDatastoreService.MAX_QUERY_LIFETIME_PROPERTY,
          Integer.toString(maxQueryLifetimeMs));
    }

    if (maxTxnLifetimeMs != null) {
      proxy.setProperty(LocalDatastoreService.MAX_TRANSACTION_LIFETIME_PROPERTY,
          Integer.toString(maxTxnLifetimeMs));
    }

    if (storeDelayMs != null) {
      proxy.setProperty(LocalDatastoreService.STORE_DELAY_PROPERTY,
          Integer.toString(storeDelayMs));
    }

    if (backingStoreLocation != null) {
      proxy.setProperty(LocalDatastoreService.BACKING_STORE_PROPERTY, backingStoreLocation);
    }
    proxy.setProperty(LocalDatastoreService.NO_INDEX_AUTO_GEN_PROP,
        Boolean.toString(noIndexAutoGen));
    if (defaultHighRepJobPolicyRandomSeed != null) {
      proxy.setProperty(DefaultHighRepJobPolicy.RANDOM_SEED_PROPERTY,
          Long.toString(defaultHighRepJobPolicyRandomSeed));
    }

    if (defaultHighRepJobPolicyUnappliedJobPercentage != null) {
      proxy.setProperty(DefaultHighRepJobPolicy.UNAPPLIED_JOB_PERCENTAGE_PROPERTY,
          Float.toString(defaultHighRepJobPolicyUnappliedJobPercentage));
    }

    if (alternateHighRepJobPolicyClass != null) {
      proxy.setProperty(LocalDatastoreService.HIGH_REP_JOB_POLICY_CLASS_PROPERTY,
          alternateHighRepJobPolicyClass.getName());
    }
    // grab the Datastore service to force it to startup.
    getLocalDatastoreService();
  }

  @Override
  public void tearDown() {
    getLocalDatastoreService().stop();
  }

  public static LocalDatastoreV3Service getLocalDatastoreService() {
    return (LocalDatastoreV3Service) LocalServiceTestHelper.getLocalService(
        LocalDatastoreService.PACKAGE);
  }
}
