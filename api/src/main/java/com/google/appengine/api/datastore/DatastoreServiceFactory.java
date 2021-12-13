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

package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withDefaults;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy;

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * Creates DatastoreService implementations.
 *
 */
public final class DatastoreServiceFactory {

  /** Creates a {@code DatastoreService} using the provided config. */
  public static DatastoreService getDatastoreService(DatastoreServiceConfig config) {
    return getFactory().getDatastoreService(config);
  }

  /**
   * Creates a {@code DatastoreService} using the default config ( {@link
   * DatastoreServiceConfig.Builder#withDefaults()}).
   */
  public static DatastoreService getDatastoreService() {
    return getDatastoreService(withDefaults());
  }

  /**
   * Creates a {@code DatastoreService} using the provided config.
   *
   * @deprecated Use {@link #getDatastoreService(DatastoreServiceConfig)} instead.
   */
  @Deprecated
  public static DatastoreService getDatastoreService(DatastoreConfig oldConfig) {
    ImplicitTransactionManagementPolicy policy = oldConfig.getImplicitTransactionManagementPolicy();
    DatastoreServiceConfig newConfig = withImplicitTransactionManagementPolicy(policy);
    return getDatastoreService(newConfig);
  }

  /**
   * Creates an {@code AsyncDatastoreService} using the provided config. The async datastore service
   * does not support implicit transaction management policy {@link
   * ImplicitTransactionManagementPolicy#AUTO}.
   *
   * @throws IllegalArgumentException If the provided {@link DatastoreServiceConfig} has an implicit
   *     transaction management policy of {@link ImplicitTransactionManagementPolicy#AUTO}.
   */
  public static AsyncDatastoreService getAsyncDatastoreService(DatastoreServiceConfig config) {
    return getFactory().getAsyncDatastoreService(config);
  }

  /**
   * Creates an {@code AsyncDatastoreService} using the default config ( {@link
   * DatastoreServiceConfig.Builder#withDefaults()}).
   */
  public static AsyncDatastoreService getAsyncDatastoreService() {
    return getAsyncDatastoreService(withDefaults());
  }

  /** @deprecated Use {@link DatastoreServiceConfig.Builder#withDefaults()} instead. */
  @Deprecated
  public static DatastoreConfig getDefaultDatastoreConfig() {
    return DatastoreConfig.DEFAULT;
  }

  /** @deprecated Exposed by accident, do not instantiate. */
  @Deprecated
  public DatastoreServiceFactory() {}

  private static IDatastoreServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(IDatastoreServiceFactory.class);
  }
}
