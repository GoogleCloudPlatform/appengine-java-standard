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

import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * User-configurable properties of the datastore.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@code DatastoreServiceConfig} object is to statically
 * import {@link Builder}.* and invoke a static creation method followed by an instance mutator (if
 * needed):
 *
 * <pre>{@code
 * import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.*;
 * import com.google.appengine.api.datastore.ReadPolicy.Consistency;
 *
 * ...
 *
 * // eventually consistent reads
 * DatastoreServiceConfig config = withReadPolicy(new ReadPolicy(Consistency.EVENTUAL));
 *
 * // eventually consistent reads with a 5 second deadline
 * DatastoreServiceConfig config =
 *   withReadPolicy(new ReadPolicy(Consistency.EVENTUAL)).deadline(5.0);
 * }</pre>
 *
 */
public final class DatastoreServiceConfig {

  /**
   * This is the name of a system property that determines how the Java SDK writes/reads empty lists
   * to/from Datastore.
   *
   * <p>Historically Datastore has not had the ability to represent an empty list in its persistent
   * store. Different SDKs have made different decisions on what to write to Datastore when a client
   * attempts to insert an empty list to the persistent store. The Java SDK has historically written
   * both empty lists and null values as null values to the persistent store.
   *
   * <p>With the release of this flag, Datastore can now represent empty lists within its persistent
   * store. This means that Datastore SDKs can distinguish between empty lists and null values. This
   * property controls whether this SDK writes an empty list as empty list or null to the persistent
   * store.
   *
   * <p>A note on queries: Null values can be indexed by Datastore which means they can interact
   * with queries. For example queries that find all entities with null values or order by a
   * property will include null values. Empty lists are not indexable by Datastore and so cannot
   * appear in similar queries.
   *
   * <p>Thus, if this flag was unset (the old behavior) and an empty list was stored into the
   * persistent store, it could appear in query results because it was really stored as a null
   * value.
   *
   * <p>If this flag is set (the new behavior) and an empty list is stored into the persistent
   * store, it will not appear it query results because it is stored as an empty list.
   *
   * <p>When this variable is set to anything other than "true" the system provides legacy behavior.
   *
   * <ul>
   *   <li>Null properties are written as null to Datastore
   *   <li>Empty collections are written as null to Datastore
   *   <li>A null is read as null from Datastore
   *   <li>An empty collection is read as null. <b>Note that a read modify write of an entity with
   *       an empty list will cause that list to be turned into a null value.</b>
   * </ul>
   *
   * <p>When this variable is set to "true":
   *
   * <ul>
   *   <li>Null properties are written as null to Datastore
   *   <li>Empty collections (#{@link Collection#isEmpty()}) are written as empty list to Datastore
   *   <li>A null is read as null from Datastore
   *   <li>An empty collection is read as null
   *   <li>When reading from Datastore an empty list is returned as an empty {@link Collection}.
   * </ul>
   *
   * <p>It is strongly recommended that this property be set to true in order to provide
   * compatibility with other Datastore SDK's, as well as future versions of Datastore.
   *
   * <p>To set the flag: <code>
   * System.setProperty(DatastoreServiceConfig.DATASTORE_EMPTY_LIST_SUPPORT,
   *                    Boolean.TRUE.toString());
   * </code>
   */
  public static final String DATASTORE_EMPTY_LIST_SUPPORT = "DATASTORE_EMPTY_LIST_SUPPORT";

  /**
   * Returns whether or not empty list support is enabled; see {@link
   * #DATASTORE_EMPTY_LIST_SUPPORT}.
   */
  public static boolean getEmptyListSupport() {
    String value = System.getProperty(DATASTORE_EMPTY_LIST_SUPPORT);
    return value != null && value.equals(Boolean.TRUE.toString());
  }

  /** The default maximum size a request RPC can be. */
  // NOTE: keep in sync with
  // com.google.appengine.tools.development.ApiProxyLocalImpl.MAX_API_REQUEST_SIZE
  static final int DEFAULT_RPC_SIZE_LIMIT_BYTES = 1024 * 1024; // 1MB

  /** The default maximum number of keys allowed in a Get request. */
  // NOTE: keep in sync with
  // com.google.apphosting.datastore.MegastoreDatastore.DEFAULT_MAX_BATCH_GET_KEYS
  static final int DEFAULT_MAX_BATCH_GET_KEYS = 1000;

  /** The default maximum number of entities allowed in a Put or Delete request. */
  // NOTE: keep in sync with
  // com.google.apphosting.datastore.MegastoreDatastore.DEFAULT_MAX_BATCH_WRITE_ENTITIES
  static final int DEFAULT_MAX_BATCH_WRITE_ENTITIES = 500;

  /**
   * Name of a system property that users can specify to control the default max entity groups per
   * rpc.
   */
  static final String DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP =
      "appengine.datastore.defaultMaxEntityGroupsPerRpc";

  /** The default number of max entity groups per rpc. */
  static final int DEFAULT_MAX_ENTITY_GROUPS_PER_RPC = getDefaultMaxEntityGroupsPerRpc();

  static final String CALLBACKS_CONFIG_SYS_PROP = "appengine.datastore.callbacksConfig";

  // Not final to make it easy for tests to reset it.
  static volatile @Nullable DatastoreCallbacks CALLBACKS = null;

  /**
   * Keep in sync with
   * com.google.appengine.tools.compilation.DatastoreCallbacksProcessor.CALLBACKS_CONFIG_FILE
   */
  private static final String CALLBACKS_CONFIG_FILE = "/META-INF/datastorecallbacks.xml";

  /* @VisibleForTesting */
  static int getDefaultMaxEntityGroupsPerRpc() {
    // If there is no sysprop set, client-side batching kicks in for all ops
    // with more than 10 entity groups.
    return getDefaultMaxEntityGroupsPerRpc(DEFAULT_MAX_ENTITY_GROUPS_PER_RPC_SYS_PROP, 10);
  }

  /**
   * Returns the value of the given system property as an int, or return the default value if there
   * is no property with that name set.
   */
  static int getDefaultMaxEntityGroupsPerRpc(String sysPropName, int defaultVal) {
    String sysPropVal = System.getProperty(sysPropName);
    return sysPropVal == null ? defaultVal : Integer.parseInt(sysPropVal);
  }

  private static InputStream getCallbacksConfigInputStream() {
    InputStream is;
    String callbacksConfig = System.getProperty(CALLBACKS_CONFIG_SYS_PROP);
    if (callbacksConfig != null) {
      // Read the config from the system property
      is = new ByteArrayInputStream(callbacksConfig.getBytes());
    } else {
      // Read the config from a file in a well-known location.
      is = DatastoreServiceConfig.class.getResourceAsStream(CALLBACKS_CONFIG_FILE);
    }
    return is;
  }

  private final @Nullable DatastoreCallbacks instanceDatastoreCallbacks;

  private ImplicitTransactionManagementPolicy implicitTransactionManagementPolicy =
      ImplicitTransactionManagementPolicy.NONE;

  private ReadPolicy readPolicy = new ReadPolicy(Consistency.STRONG);

  private Double deadline;

  private AppIdNamespace appIdNamespace;

  int maxRpcSizeBytes = DEFAULT_RPC_SIZE_LIMIT_BYTES;
  int maxBatchWriteEntities = DEFAULT_MAX_BATCH_WRITE_ENTITIES;
  int maxBatchReadEntities = DEFAULT_MAX_BATCH_GET_KEYS;
  int maxBatchAllocateIdKeys = DEFAULT_MAX_BATCH_WRITE_ENTITIES;
  int maxEntityGroupsPerRpc = DEFAULT_MAX_ENTITY_GROUPS_PER_RPC;

  /**
   * Cannot be directly instantiated, use {@link Builder} instead.
   *
   * @param datastoreCallbacks the callback methods to invoke on specific datastore operations. If
   *     null the datastore callbacks are derived from {@link #getCallbacksConfigInputStream}.
   */
  private DatastoreServiceConfig(@Nullable DatastoreCallbacks datastoreCallbacks) {
    instanceDatastoreCallbacks = datastoreCallbacks;
  }

  /** Copy constructor */
  DatastoreServiceConfig(DatastoreServiceConfig config) {
    implicitTransactionManagementPolicy = config.implicitTransactionManagementPolicy;
    // readPolicy is immutable so shared ref is ok
    readPolicy = config.readPolicy;
    deadline = config.deadline;
    maxRpcSizeBytes = config.maxRpcSizeBytes;
    maxBatchWriteEntities = config.maxBatchWriteEntities;
    maxBatchReadEntities = config.maxBatchReadEntities;
    maxEntityGroupsPerRpc = config.maxEntityGroupsPerRpc;
    instanceDatastoreCallbacks = config.instanceDatastoreCallbacks;
    appIdNamespace = config.appIdNamespace;
  }

  /**
   * Sets the implicit transaction management policy.
   *
   * @param p the implicit transaction management policy to set.
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig implicitTransactionManagementPolicy(
      ImplicitTransactionManagementPolicy p) {
    if (p == null) {
      throw new NullPointerException("implicit transaction management policy must not be null");
    }
    implicitTransactionManagementPolicy = p;
    return this;
  }

  /**
   * Sets the read policy.
   *
   * @param readPolicy the read policy to set.
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig readPolicy(ReadPolicy readPolicy) {
    if (readPolicy == null) {
      throw new NullPointerException("read policy must not be null");
    }
    this.readPolicy = readPolicy;
    return this;
  }

  /**
   * Sets the deadline, in seconds, for all rpcs initiated by the {@link DatastoreService} with
   * which this config is associated.
   *
   * @param deadline the deadline to set.
   * @throws IllegalArgumentException if deadline is not positive
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig deadline(double deadline) {
    if (deadline <= 0.0) {
      throw new IllegalArgumentException("deadline must be > 0, got " + deadline);
    }
    this.deadline = deadline;
    return this;
  }

  DatastoreServiceConfig appIdNamespace(AppIdNamespace appIdNamespace) {
    this.appIdNamespace = appIdNamespace;
    return this;
  }

  /**
   * Sets the maximum number of entities that can be modified in a single RPC.
   *
   * @param maxBatchWriteEntities the limit to set
   * @throws IllegalArgumentException if maxBatchWriteEntities is not greater than zero
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxBatchWriteEntities(int maxBatchWriteEntities) {
    if (maxBatchWriteEntities <= 0) {
      throw new IllegalArgumentException(
          "maxBatchWriteEntities must be > 0, got " + maxBatchWriteEntities);
    }
    this.maxBatchWriteEntities = maxBatchWriteEntities;
    return this;
  }

  /**
   * Sets the maximum number of entities that can be read in a single RPC.
   *
   * @param maxBatchReadEntities the limit to set
   * @throws IllegalArgumentException if maxBatchReadEntities is not greater than zero
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxBatchReadEntities(int maxBatchReadEntities) {
    if (maxBatchReadEntities <= 0) {
      throw new IllegalArgumentException(
          "maxBatchReadEntities must be > 0, got " + maxBatchReadEntities);
    }
    this.maxBatchReadEntities = maxBatchReadEntities;
    return this;
  }

  /**
   * Sets the maximum size in bytes an RPC can be.
   *
   * <p>The size of the request can be exceeded if the RPC cannot be split enough to respect this
   * limit. However there may be a hard limit on the RPC which, if exceeded, will cause an exception
   * to be thrown.
   *
   * @param maxRpcSizeBytes the limit to set
   * @throws IllegalArgumentException if maxRpcSizeBytes is not positive
   * @return {@code this} (for chaining)
   */
  DatastoreServiceConfig maxRpcSizeBytes(int maxRpcSizeBytes) {
    if (maxRpcSizeBytes < 0) {
      throw new IllegalArgumentException("maxRpcSizeBytes must be >= 0, got " + maxRpcSizeBytes);
    }
    this.maxRpcSizeBytes = maxRpcSizeBytes;
    return this;
  }

  /**
   * Sets the maximum number of entity groups that can be represented in a single rpc.
   *
   * <p>For a non-transactional operation that involves more entity groups than the maximum, the
   * operation will be performed by executing multiple, asynchronous rpcs to the datastore, each of
   * which has no more entity groups represented than the maximum. So, if a put() operation has 8
   * entity groups and the maximum is 3, we will send 3 rpcs, 2 with 3 entity groups and 1 with 2
   * entity groups. This is a performance optimization - in many cases multiple, small, asynchronous
   * rpcs will finish faster than a single large asynchronous rpc. The optimal value for this
   * property will be application-specific, so experimentation is encouraged.
   *
   * @param maxEntityGroupsPerRpc the maximum number of entity groups per rpc
   * @throws IllegalArgumentException if maxEntityGroupsPerRpc is not greater than zero
   * @return {@code this} (for chaining)
   */
  public DatastoreServiceConfig maxEntityGroupsPerRpc(int maxEntityGroupsPerRpc) {
    if (maxEntityGroupsPerRpc <= 0) {
      throw new IllegalArgumentException(
          "maxEntityGroupsPerRpc must be > 0, got " + maxEntityGroupsPerRpc);
    }
    this.maxEntityGroupsPerRpc = maxEntityGroupsPerRpc;
    // Respect the user's wishes for both reads and writes
    return this;
  }

  /** Returns the {@code ImplicitTransactionManagementPolicy} to use. */
  public ImplicitTransactionManagementPolicy getImplicitTransactionManagementPolicy() {
    return implicitTransactionManagementPolicy;
  }

  /** Returns the {@code ReadPolicy} to use. */
  public ReadPolicy getReadPolicy() {
    return readPolicy;
  }

  /** Returns the maximum number of entity groups per rpc. */
  // Leaving the return type as Integer for backwards compatibility
  // (may not be an issue due to auto-boxing but doesn't seem worth
  // the risk to change it).
  public Integer getMaxEntityGroupsPerRpc() {
    return getMaxEntityGroupsPerRpcInternal();
  }

  // Allows internal callers to access this value without the appearance of an
  // NPE.
  int getMaxEntityGroupsPerRpcInternal() {
    return maxEntityGroupsPerRpc;
  }

  /** Returns the deadline, in seconds, to use. Can be {@code null}. */
  public Double getDeadline() {
    return deadline;
  }

  AppIdNamespace getAppIdNamespace() {
    return appIdNamespace == null ? DatastoreApiHelper.getCurrentAppIdNamespace() : appIdNamespace;
  }

  DatastoreCallbacks getDatastoreCallbacks() {
    if (instanceDatastoreCallbacks != null) {
      return instanceDatastoreCallbacks;
    }

    // no need to synchronize, the member is volatile and if this gets called
    // concurrently at startup the worst that will happen is we'll load the
    // config more than once
    if (CALLBACKS == null) {
      InputStream is = getCallbacksConfigInputStream();
      if (is == null) {
        // If the file doesn't exist, just return a no-op callbacks implementation.
        CALLBACKS = DatastoreCallbacks.NoOpDatastoreCallbacks.INSTANCE;
      } else {
        CALLBACKS = new DatastoreCallbacksImpl(is, false);
      }
    }
    return CALLBACKS;
  }

  ApiProxy.ApiConfig constructApiConfig() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(deadline);
    return apiConfig;
  }

  /** Contains static creation methods for {@link DatastoreServiceConfig}. */
  public static final class Builder {

    /**
     * Create a {@link DatastoreServiceConfig} with the given implicit transaction management
     * policy.
     *
     * @param p the implicit transaction management policy to set.
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withImplicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy p) {
      return withDefaults().implicitTransactionManagementPolicy(p);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given read policy.
     *
     * @param readPolicy the read policy to set.
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withReadPolicy(ReadPolicy readPolicy) {
      return withDefaults().readPolicy(readPolicy);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given deadline, in seconds.
     *
     * @param deadline the deadline to set.
     * @throws IllegalArgumentException if deadline is not positive
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withDeadline(double deadline) {
      return withDefaults().deadline(deadline);
    }

    /**
     * Create a {@link DatastoreServiceConfig} with the given maximum entity groups per rpc.
     *
     * @param maxEntityGroupsPerRpc the maximum entity groups per rpc to set.
     * @return The newly created DatastoreServiceConfig instance.
     * @see DatastoreServiceConfig#maxEntityGroupsPerRpc(int)
     */
    public static DatastoreServiceConfig withMaxEntityGroupsPerRpc(int maxEntityGroupsPerRpc) {
      return withDefaults().maxEntityGroupsPerRpc(maxEntityGroupsPerRpc);
    }

    /**
     * Helper method for creating a {@link DatastoreServiceConfig} instance with the specified
     * {@code datastoreCallbacks}. The callbacks defined for the application are bypassed and the
     * specified callbacks are used instead.
     *
     * @return The newly created DatastoreServiceConfig instance.
     */
    static DatastoreServiceConfig withDatastoreCallbacks(DatastoreCallbacks datastoreCallbacks) {
      Preconditions.checkNotNull(datastoreCallbacks);
      return new DatastoreServiceConfig(datastoreCallbacks);
    }

    /**
     * Helper method for creating a {@link DatastoreServiceConfig} instance with default values:
     * Implicit transactions are disabled, reads execute with {@link Consistency#STRONG}, and no
     * deadline is provided. When no deadline is provided, datastore rpcs execute with the
     * system-defined deadline.
     *
     * @return The newly created DatastoreServiceConfig instance.
     */
    public static DatastoreServiceConfig withDefaults() {
      return new DatastoreServiceConfig((DatastoreCallbacks) null);
    }

    // Only utility methods, no need to instantiate.
    private Builder() {}
  }
}
