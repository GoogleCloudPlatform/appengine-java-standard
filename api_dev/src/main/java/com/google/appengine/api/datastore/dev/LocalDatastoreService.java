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

import static com.google.appengine.api.datastore.dev.Utils.checkRequest;
import static com.google.appengine.api.datastore.dev.Utils.getKind;
import static com.google.appengine.api.datastore.dev.Utils.newError;
import static com.google.cloud.datastore.core.proto2.EntityStorageConversions.postprocessIndexes;
import static com.google.cloud.datastore.core.proto2.EntityStorageConversions.preprocessIndexesWithoutEmptyListSupport;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;

import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.dev.ReservedKinds;
import com.google.appengine.api.datastore.DataTypeUtils;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityProtoComparators.EntityProtoComparator;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.LocalCompositeIndexManager.IndexConfigurationFormat;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.LiveTxn.ConcurrencyMode;
import com.google.appengine.api.images.dev.ImagesReservedKinds;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalRpcService.Status;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy.ApplicationException;
import com.google.apphosting.api.proto2api.ApiBasePb.Integer64Proto;
import com.google.apphosting.api.proto2api.ApiBasePb.StringProto;
import com.google.apphosting.api.proto2api.ApiBasePb.VoidProto;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.AllocateIdsRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.AllocateIdsResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.BeginTransactionRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.BeginTransactionRequest.TransactionMode;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CommitResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompiledQuery;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompiledQuery.PrimaryScan;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Cost;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Cursor;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.DeleteResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Error.ErrorCode;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.PutResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Order;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.QueryResult;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Transaction;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.auto.value.AutoValue;
import com.google.cloud.datastore.core.exception.InvalidConversionException;
import com.google.cloud.datastore.core.proto2.CursorModernizer;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
// <internal22>
// <internal24>
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.CompositeIndex;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProtoOrBuilder;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.IndexPostfix;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Property;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Property.Meaning;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.PropertyOrBuilder;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A local implementation of the Datastore.
 *
 * <p>This is a memory-based implementation which can persist itself to disk through a batch
 * operation.
 *
 * <p>This class is no longer a {@link com.google.appengine.tools.development.LocalRpcService},
 * however it is still called a service for backwards compatibility.
 */
public abstract class LocalDatastoreService {
  //  TODO Improve this implementation such that it's backed by
  //  something like Derby, which is bundled with the JDK, perhaps in
  //  "iteration 2".

  private static final Logger logger = Logger.getLogger(LocalDatastoreService.class.getName());

  static final double DEFAULT_DEADLINE_SECONDS = 30.0;
  static final double MAX_DEADLINE_SECONDS = DEFAULT_DEADLINE_SECONDS;

  /**
   * Default number of {@link Entity} objects to retrieve at a time. This is an optimization that
   * avoids making an RPC call for each {@link Entity}.
   */
  // NOTE: Keep synchronized with `megastore_batch_size` default value at
  // <internal11>
  static final int DEFAULT_BATCH_SIZE = 20;

  // This should be synchronized with the production datastore's --max_query_results flag value.
  public static final int MAX_QUERY_RESULTS = 300;

  /** The package name for this service. */
  public static final String PACKAGE = "datastore_v3";

  /** How long a query can stay "live" before we expire it. */
  public static final String MAX_QUERY_LIFETIME_PROPERTY = "datastore.max_query_lifetime";

  private static final int DEFAULT_MAX_QUERY_LIFETIME = 30000;

  /** How long a transaction can stay "live" before we expire it. */
  public static final String MAX_TRANSACTION_LIFETIME_PROPERTY = "datastore.max_txn_lifetime";

  private static final int DEFAULT_MAX_TRANSACTION_LIFETIME = 300000;

  /** How long to wait before updating the persistent store in milliseconds. */
  public static final String STORE_DELAY_PROPERTY = "datastore.store_delay";

  static final int DEFAULT_STORE_DELAY_MS = 30000;

  static final int MAX_STRING_LENGTH = DataTypeUtils.MAX_STRING_PROPERTY_LENGTH;

  static final int MAX_LINK_LENGTH = DataTypeUtils.MAX_LINK_PROPERTY_LENGTH;

  static final int MAX_BLOB_LENGTH = 1048487;

  /** The maximum number of entity groups in a transaction. */
  public static final int MAX_EG_PER_TXN = 25;

  /** Where to read/store the datastore from/to. */
  public static final String BACKING_STORE_PROPERTY = "datastore.backing_store";

  /** The backing store file format version number. */
  private static final long CURRENT_STORAGE_VERSION = 2;

  /**
   * True to emulate Datastore vnext features. These features mandate strong consistency everywhere,
   * so custom {@link HighRepJobPolicy} policies are disallowed while this flag is true.
   */
  // Emulates Datastore-on-spanner features. These features include strong consistency
  // everywhere and increased latency on writes due to high replication.
  public static final String EMULATE_VNEXT_FEATURES = "datastore.use_vnext";

  /**
   * True to prevent the datastore from writing {@link
   * com.google.apphosting.utils.config.IndexesXmlReader#GENERATED_INDEX_FILENAME}.
   */
  public static final String NO_INDEX_AUTO_GEN_PROP = "datastore.no_index_auto_gen";

  /** True to put the datastore into "memory-only" mode. */
  public static final String NO_STORAGE_PROPERTY = "datastore.no_storage";

  /**
   * The fully-qualifed name of a class that implements {@link HighRepJobPolicy} and has a no-arg
   * constructor. If not provided we use a {@link DefaultHighRepJobPolicy}. See the javadoc for this
   * class for information on its configurable properties.
   */
  public static final String HIGH_REP_JOB_POLICY_CLASS_PROPERTY =
      "datastore.high_replication_job_policy_class";

  /**
   * If this property exists we consider the datastore to be high replication independent of the
   * high rep job policy.
   */
  public static final String FORCE_IS_HIGH_REP_PROPERTY = "datastore.force_is_high_replication";

  public static final String INDEX_CONFIGURATION_FORMAT_PROPERTY =
      "datastore.index_configuration_format";

  private static final Pattern RESERVED_NAME = Pattern.compile("^__.*__$");

  private static final String SUBSCRIPTION_MAP_KIND = "__ProspectiveSearchSubscriptions__";

  /**
   * Reserved kinds that we allow writes to. This lets us throw an exception when users try to write
   * reserved kinds but lets the kinds used by the local blobstore get through. This means usercode
   * can write to these reserved blobstore kinds in the dev appserver, which can't happen in prod.
   * This is an acceptable discrepancy.
   */
  static final ImmutableSet<String> RESERVED_KIND_ALLOWLIST =
      ImmutableSet.of(
          ReservedKinds.BLOB_UPLOAD_SESSION_KIND,
          ReservedKinds.GOOGLE_STORAGE_FILE_KIND,
          BlobInfoFactory.KIND,
          SUBSCRIPTION_MAP_KIND,
          ImagesReservedKinds.BLOB_SERVING_URL_KIND);

  static final String ENTITY_GROUP_MESSAGE =
      "cross-group transaction need to be explicitly specified, "
          + "see TransactionOptions.Builder.withXG";

  static final String TOO_MANY_ENTITY_GROUP_MESSAGE =
      "operating on too many entity groups in a single transaction.";

  static final String MULTI_EG_TXN_NOT_ALLOWED =
      "transactions on multiple entity groups only allowed in High Replication applications";

  static final String CONTENTION_MESSAGE =
      "too much contention on these datastore entities. please try again.";

  static final String TRANSACTION_CLOSED = "transaction closed";

  static final String TRANSACTION_NOT_FOUND = "transaction has expired or is invalid";

  static final String TRANSACTION_RETRY_ON_READ_ONLY =
      "specifying a previous transaction handle on a read-only transaction is not allowed";

  static final String TRANSACTION_RETRY_ON_PREVIOUSLY_READ_ONLY =
      "Cannot retry a read-only transaction.";

  static final String TRANSACTION_OPTIONS_CHANGED_ON_RESET =
      "Transaction options should be the same as specified previous transaction.";

  static final String NAME_TOO_LONG =
      "The key path element name is longer than " + MAX_STRING_LENGTH + " bytes.";

  static final String QUERY_NOT_FOUND =
      "query has expired or is invalid. Please "
          + "restart it with the last cursor to read more results.";

  static final String VNEXT_POLICY_DISALLOWED =
      "custom job policies are disallowed "
          + "when using vnext features as vnext must be strongly consistent.";

  /**
   * Index of the bit in a 64-bit integer representation of the non-inclusive upper bound of the
   * sequential id space. Integer ids must be representable as IEEE 64-bit floats to support JSON
   * numeric encoding. Ids in any id space can be no greater than 1 << _MAX_SEQUENTIAL_BIT + 1.
   */
  private static final long MAX_SEQUENTIAL_BIT = 52;

  /**
   * Maximum permitted value for the counter which generates sequential ids. The sequential id space
   * is (0, 2^52) non-inclusive.
   */
  private static final long MAX_SEQUENTIAL_COUNTER = (1L << MAX_SEQUENTIAL_BIT) - 1;

  /** Maximum valid sequential id. */
  private static final long MAX_SEQUENTIAL_ID = MAX_SEQUENTIAL_COUNTER;

  /**
   * Maximum permitted value for the counter which generates scattered ids. The scattered id space
   * is [2^52, 2^52 + 2^51).
   */
  private static final long MAX_SCATTERED_COUNTER = (1L << (MAX_SEQUENTIAL_BIT - 1)) - 1;

  /** Number of empty high-order bits above the scattered id space. */
  private static final long SCATTER_SHIFT = 64 - MAX_SEQUENTIAL_BIT + 1;

  public static final String AUTO_ID_ALLOCATION_POLICY_PROPERTY =
      "datastore.auto_id_allocation_policy";

  /**
   * The set of supported values for autoIdAllocationPolicy, which controls how auto IDs are
   * assigned by put().
   */
  public static enum AutoIdAllocationPolicy {
    SEQUENTIAL,
    SCATTERED
  }

  // Important that these begin with 1, because 0 stands for "not assigned".
  private final AtomicLong entityIdSequential = new AtomicLong(1);
  private final AtomicLong entityIdScattered = new AtomicLong(1);
  private AutoIdAllocationPolicy autoIdAllocationPolicy = AutoIdAllocationPolicy.SEQUENTIAL;

  private final AtomicLong queryId = new AtomicLong(0);

  /** The location this database is persisted to and loaded from. */
  private String backingStore;

  /** The set of Profiles for this datastore, categorized by name. */
  private final Map<String, Profile> profiles =
      Collections.synchronizedMap(new HashMap<String, Profile>());

  private final Map<String, SpecialProperty> specialPropertyMap = Maps.newHashMap();

  // TODO: Replace the use of the Clock with Stopwatch.
  private Clock clock;

  private static final long MAX_BATCH_GET_KEYS = 1000000000;

  /**
   * Mimic production behavior by limiting number of datastore actions this should be synchronized
   * with MegastoreDatastore._DEFAULT_MAX_ACTIONS_PER_TXN
   */
  private static final long MAX_ACTIONS_PER_TXN = 5;

  /**
   * Calls the add method on the taskqueue service.
   *
   * <p>Subclasses should override this to use the appropriate method of calling other services.
   */
  protected abstract void addActionImpl(TaskQueueAddRequest action);

  /**
   * Clear out the in-memory datastore. Note that this does not clear out any data that has been
   * persisted on disk.
   */
  public void clearProfiles() {
    profiles.clear();
  }

  /** Clear out the query history that we use for generating indexes. */
  public void clearQueryHistory() {
    LocalCompositeIndexManager.getInstance().clearQueryHistory();
  }

  /**
   * The amount of time a query is protected from being "GC'd". Must be no shorter than the max
   * request deadline set by the dev appserver.
   */
  private int maxQueryLifetimeMs;

  /**
   * The amount of time a txn is protected from being "GC'd". Must be no shorter than the max
   * request deadline set by the dev appserver.
   */
  private int maxTransactionLifetimeMs;

  /**
   * The number of core threads to use for background tasks for all LocalDatastoreService instances.
   */
  private static final int BACKGROUND_THREAD_COUNT = 10;

  private static final ScheduledThreadPoolExecutor scheduler = createScheduler();

  /** Creates a new scheduler. */
  private static ScheduledThreadPoolExecutor createScheduler() {
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            BACKGROUND_THREAD_COUNT,
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("LocalDatastoreService-%d")
                .build());
    scheduler.setRemoveOnCancelPolicy(true);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                cleanupActiveServices();
              }
            });
    return scheduler;
  }

  /** A set of all active LocalDatastoreService objects. */
  private static final Set<LocalDatastoreService> activeServices = Sets.newConcurrentHashSet();

  /** Hands out transaction handles */
  private final AtomicInteger transactionHandleProvider = new AtomicInteger(0);

  /** How often we attempt to persist the database. */
  private int storeDelayMs;

  /** Is the datastore dirty, requiring a write? */
  private volatile boolean dirty;

  /**
   * A lock around the database that is used to prevent background persisting from interfering with
   * real time updates.
   */
  private final ReadWriteLock globalLock = new ReentrantReadWriteLock();

  private boolean noStorage;

  /** The pseudoKinds known to this local datastore. */
  private PseudoKinds pseudoKinds;

  private HighRepJobPolicy highRepJobPolicy;
  private boolean spannerBacked;
  private LocalDatastoreCostAnalysis costAnalysis;

  /** The list of scheduled tasks for this instance of the service. */
  private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

  public LocalDatastoreService() {
    setMaxQueryLifetime(DEFAULT_MAX_QUERY_LIFETIME);
    setMaxTransactionLifetime(DEFAULT_MAX_TRANSACTION_LIFETIME);
    setStoreDelay(DEFAULT_STORE_DELAY_MS);
    enableScatterProperty(true);
  }

  public void init(LocalServiceContext context, Map<String, String> properties) {
    init(context.getLocalServerEnvironment().getAppDir(), context.getClock(), properties);
  }

  public void init(File appDirectory, Clock clock, Map<String, String> properties) {
    this.clock = clock;
    String storeFile = properties.get(BACKING_STORE_PROPERTY);

    String noStorageProp = properties.get(NO_STORAGE_PROPERTY);
    if (noStorageProp != null) {
      noStorage = Boolean.parseBoolean(noStorageProp);
    }

    if (storeFile == null && !noStorage) {
      File dir = GenerationDirectory.getGenerationDirectory(appDirectory);
      dir.mkdirs();
      storeFile = dir.getAbsolutePath() + File.separator + "local_db.bin";
    }
    setBackingStore(storeFile);

    String storeDelayTime = properties.get(STORE_DELAY_PROPERTY);
    storeDelayMs = parseInt(storeDelayTime, storeDelayMs, STORE_DELAY_PROPERTY);

    String maxQueryLifetime = properties.get(MAX_QUERY_LIFETIME_PROPERTY);
    maxQueryLifetimeMs =
        parseInt(maxQueryLifetime, maxQueryLifetimeMs, MAX_QUERY_LIFETIME_PROPERTY);

    String maxTxnLifetime = properties.get(MAX_TRANSACTION_LIFETIME_PROPERTY);
    maxTransactionLifetimeMs =
        parseInt(maxTxnLifetime, maxTransactionLifetimeMs, MAX_TRANSACTION_LIFETIME_PROPERTY);

    autoIdAllocationPolicy =
        getEnumProperty(
            properties,
            AutoIdAllocationPolicy.class,
            AUTO_ID_ALLOCATION_POLICY_PROPERTY,
            autoIdAllocationPolicy);

    IndexConfigurationFormat indexConfigurationFormat =
        getEnumProperty(
            properties,
            IndexConfigurationFormat.class,
            INDEX_CONFIGURATION_FORMAT_PROPERTY,
            IndexConfigurationFormat.DEFAULT);

    LocalCompositeIndexManager.init(indexConfigurationFormat);
    LocalCompositeIndexManager.getInstance().setAppDir(appDirectory);
    LocalCompositeIndexManager.getInstance().setClock(clock);

    String noIndexAutoGenProp = properties.get(NO_INDEX_AUTO_GEN_PROP);
    if (noIndexAutoGenProp != null) {
      LocalCompositeIndexManager.getInstance()
          .setStoreIndexConfiguration(!Boolean.parseBoolean(noIndexAutoGenProp));
    }

    String vnextPropStr = properties.get(EMULATE_VNEXT_FEATURES);
    if (vnextPropStr != null) {
      spannerBacked = Boolean.parseBoolean(vnextPropStr);
    } else {
      spannerBacked = false;
    }

    initHighRepJobPolicy(properties);

    /* Create and register all pseudo-kinds */
    pseudoKinds = new PseudoKinds();
    pseudoKinds.register(new KindPseudoKind(this));
    pseudoKinds.register(new PropertyPseudoKind(this));
    pseudoKinds.register(new NamespacePseudoKind(this));
    if (!spannerBacked()) {
      pseudoKinds.register(new EntityGroupPseudoKind());
    }

    costAnalysis = new LocalDatastoreCostAnalysis(LocalCompositeIndexManager.getInstance());

    logger.info(
        String.format(
            "Local Datastore initialized: " + "Type: %s " + "Storage: %s",
            spannerBacked() ? "VNext" : "High Replication",
            noStorage ? "In-memory" : backingStore));
  }

  private static <T extends Enum<T>> T getEnumProperty(
      Map<String, String> properties, Class<T> enumType, String propertyName, T defaultIfNotSet) {
    String propertyValue = properties.get(propertyName);
    if (propertyValue == null) {
      return defaultIfNotSet;
    }
    try {
      return Enum.valueOf(enumType, propertyValue.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Invalid value \"%s\" for property \"%s\"", propertyValue, propertyName),
          e);
    }
  }

  boolean spannerBacked() {
    return spannerBacked;
  }

  private void initHighRepJobPolicy(Map<String, String> properties) {
    String highRepJobPolicyStr = properties.get(HIGH_REP_JOB_POLICY_CLASS_PROPERTY);

    if (highRepJobPolicyStr == null) {
      if (spannerBacked()) {
        highRepJobPolicy = new SpannerJobPolicy();
      } else {
        DefaultHighRepJobPolicy defaultPolicy = new DefaultHighRepJobPolicy(properties);
        spannerBacked = false;
        highRepJobPolicy = defaultPolicy;
      }

    } else {
      if (spannerBacked()) {
        // Spanner is always strongly consistent - cannot change consistency model
        throw newError(ErrorCode.BAD_REQUEST, VNEXT_POLICY_DISALLOWED);
      }

      try {
        Class<?> highRepJobPolicyCls = Class.forName(highRepJobPolicyStr);
        Constructor<?> ctor = highRepJobPolicyCls.getDeclaredConstructor();
        // In case the constructor isn't accessible to us.
        ctor.setAccessible(true);
        // We'll get a ClassCastException if the class doesn't implement the
        this.highRepJobPolicy = (HighRepJobPolicy) ctor.newInstance();
      } catch (ClassNotFoundException
          | InvocationTargetException
          | NoSuchMethodException
          | InstantiationException
          | IllegalAccessException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  // Spanner Job Policy
  // Applies all jobs immediately to ensure strong consistency.
  static class SpannerJobPolicy implements HighRepJobPolicy {
    @Override
    public boolean shouldApplyNewJob(Key entityGroup) {
      return true;
    }

    @Override
    public boolean shouldRollForwardExistingJob(Key entityGroup) {
      return true;
    }
  }

  private static int parseInt(String valStr, int defaultVal, String propName) {
    if (valStr != null) {
      try {
        return Integer.parseInt(valStr);
      } catch (NumberFormatException e) {
        logger.log(
            Level.WARNING,
            "Expected a numeric value for property "
                + propName
                + "but received, "
                + valStr
                + ". Resetting property to the default.");
      }
    }
    return defaultVal;
  }

  public void start() {
    startInternal();
  }

  private synchronized void startInternal() {
    if (activeServices.contains(this)) {
      return;
    }
    load();
    activeServices.add(this);
    scheduledTasks.add(
        scheduler.scheduleWithFixedDelay(
            new Runnable() {
              @Override
              public void run() {
                removeStaleQueries(clock.getCurrentTime());
              }
            },
            maxQueryLifetimeMs * 5L,
            maxQueryLifetimeMs * 5L,
            TimeUnit.MILLISECONDS));

    scheduledTasks.add(
        scheduler.scheduleWithFixedDelay(
            new Runnable() {
              @Override
              public void run() {
                removeStaleTransactions(clock.getCurrentTime());
              }
            },
            maxTransactionLifetimeMs * 5L,
            maxTransactionLifetimeMs * 5L,
            TimeUnit.MILLISECONDS));

    if (!noStorage) {
      scheduledTasks.add(
          scheduler.scheduleWithFixedDelay(
              new Runnable() {
                @Override
                public void run() {
                  persist();
                }
              },
              storeDelayMs,
              storeDelayMs,
              TimeUnit.MILLISECONDS));
    }
  }

  public synchronized void stop() {
    if (!activeServices.contains(this)) {
      return;
    }
    activeServices.remove(this);

    if (!noStorage) {
      // All information about unapplied jobs is transient, so roll everything
      // forward before we shut down.
      rollForwardAllUnappliedJobs();
      persist();
    }

    clearProfiles();
    clearQueryHistory();

    for (ScheduledFuture<?> scheduledTask : scheduledTasks) {
      scheduledTask.cancel(false);
    }
    scheduledTasks.clear();
  }

  private void rollForwardAllUnappliedJobs() {
    // Since we're not persisting unapplied jobs, force all unapplied jobs to
    // roll forward before we shut down.  We could actually persist this
    // information if we wanted, but it doesn't seem worth the effort (we'd
    // need to make all the Runnables implement Serializable).
    for (Profile profile : profiles.values()) {
      if (profile.getGroups() != null) {
        for (Profile.EntityGroup eg : profile.getGroups().values()) {
          eg.rollForwardUnappliedJobs();
        }
      }
    }
  }

  public void setMaxQueryLifetime(int milliseconds) {
    this.maxQueryLifetimeMs = milliseconds;
  }

  public void setMaxTransactionLifetime(int milliseconds) {
    this.maxTransactionLifetimeMs = milliseconds;
  }

  public void setBackingStore(String backingStore) {
    this.backingStore = backingStore;
  }

  public void setStoreDelay(int delayMs) {
    this.storeDelayMs = delayMs;
  }

  public void setNoStorage(boolean noStorage) {
    this.noStorage = noStorage;
  }

  // TODO: Add a unit test for this.
  public void enableScatterProperty(boolean enable) {
    if (enable) {
      specialPropertyMap.put(Entity.SCATTER_RESERVED_PROPERTY, SpecialProperty.SCATTER);
    } else {
      specialPropertyMap.remove(Entity.SCATTER_RESERVED_PROPERTY);
    }
  }

  public String getPackage() {
    return PACKAGE;
  }

  public GetResponse get(@SuppressWarnings("unused") Status status, GetRequest request) {
    GetResponse.Builder response = GetResponse.newBuilder();
    LiveTxn liveTxn = null;
    for (Reference key : request.getKeyList()) {
      validatePathComplete(key);
      String app = key.getApp();
      Path groupPath = getGroup(key);
      GetResponse.Entity.Builder responseEntity = response.addEntityBuilder();
      Profile profile = getOrCreateProfile(app);
      synchronized (profile) {
        Profile.EntityGroup eg = profile.getGroup(groupPath);
        if (request.hasTransaction()) {
          if (liveTxn == null) {
            liveTxn = profile.getTxn(request.getTransaction().getHandle());
          }
          // this will throw an exception if we attempt to read from
          // the wrong entity group
          eg.addTransaction(liveTxn);
        }
        boolean eventualConsistency = request.hasFailoverMs() && liveTxn == null;
        EntityProto entity = pseudoKinds.get(liveTxn, eg, key, eventualConsistency);
        if (entity == PseudoKinds.NOT_A_PSEUDO_KIND) {
          VersionedEntity versionedEntity = eg.get(liveTxn, key, eventualConsistency);
          if (versionedEntity == null) {
            entity = null;
            if (!eventualConsistency) {
              responseEntity.setVersion(profile.getReadTimestamp());
            }
          } else {
            entity = versionedEntity.entityProto();
            responseEntity.setVersion(versionedEntity.version());
          }
        }
        if (entity != null) {
          responseEntity.setEntity(entity);
          postprocessEntity(responseEntity.getEntityBuilder());
        } else {
          responseEntity.setKey(key);
        }
        // Give all entity groups with unapplied jobs the opportunity to catch
        // up.  Note that this will not impact the result we're about to return.
        profile.groom();
      }
    }

    return response.build();
  }

  public PutResponse put(Status status, PutRequest request) {
    globalLock.readLock().lock();
    try {
      return putImpl(status, request);
    } finally {
      globalLock.readLock().unlock();
    }
  }

  /** Prepares an entity provided by a client for storage. */
  private void preprocessEntity(EntityProto.Builder entity) {
    preprocessIndexesWithoutEmptyListSupport(entity);
    processEntityForSpecialProperties(entity, true);
  }

  /** Prepares a stored entity to be returned to a client. */
  private void postprocessEntity(EntityProto.Builder entity) {
    processEntityForSpecialProperties(entity, false);
    postprocessIndexes(entity);
  }

  // Equivalent to processSpecialProperties in prod.
  /**
   * Adds the appropriate special properties to the given entity and removes all others.
   *
   * @param entity the entity to modify.
   * @param store true if we're storing the entity, false if we're loading it.
   */
  private void processEntityForSpecialProperties(EntityProto.Builder entity, boolean store) {
    List<Property> properties = new ArrayList<>();
    for (Property property : entity.getPropertyList()) {
      if (!specialPropertyMap.containsKey(property.getName())) {
        properties.add(property);
      }
    }
    entity.clearProperty().addAllProperty(properties);

    for (SpecialProperty specialProp : specialPropertyMap.values()) {
      if (store ? specialProp.isStored() : specialProp.isVisible()) {
        PropertyValue value = specialProp.getValue(entity);
        if (value != null) {
          entity.addProperty(specialProp.getProperty(value));
        }
      }
    }
  }

  @SuppressWarnings("unused") // status
  public PutResponse putImpl(Status status, PutRequest request) {
    PutResponse.Builder response = PutResponse.newBuilder();
    if (request.getEntityCount() == 0) {
      return response.build();
    }
    Cost.Builder totalCost = response.getCostBuilder();
    String app = request.getEntity(0).getKey().getApp();
    List<EntityProto> clones = new ArrayList<>();
    for (EntityProto entity : request.getEntityList()) {
      EntityProto.Builder clone = entity.toBuilder();
      validateAndProcessEntityProto(clone);

      checkArgument(clone.hasKey());
      Reference.Builder key = clone.getKeyBuilder();
      checkArgument(key.getPath().getElementCount() > 0);

      key.setApp(app);

      Path.Builder path = key.getPathBuilder();
      int lastIndex = path.getElementCount() - 1;
      Element.Builder lastPath = path.getElementBuilder(lastIndex);

      if (lastPath.getId() == 0 && !lastPath.hasName()) {
        if (autoIdAllocationPolicy == AutoIdAllocationPolicy.SEQUENTIAL) {
          lastPath.setId(entityIdSequential.getAndIncrement());
        } else {
          lastPath.setId(toScatteredId(entityIdScattered.getAndIncrement()));
        }
      }

      preprocessEntity(clone);

      if (clone.getEntityGroup().getElementCount() == 0) {
        // The entity needs its entity group set.
        Path.Builder group = clone.getEntityGroupBuilder();
        Element root = key.getPath().getElement(0);
        Element.Builder pathElement = group.addElementBuilder();
        pathElement.setType(root.getType());
        if (root.hasName()) {
          pathElement.setName(root.getName());
        } else {
          pathElement.setId(root.getId());
        }
      } else {
        // update an existing entity
        checkState(clone.hasEntityGroup() && clone.getEntityGroup().getElementCount() > 0);
      }
      clones.add(clone.build());
    }

    Map<Path, List<EntityProto>> entitiesByEntityGroup = new LinkedHashMap<>();
    Map<Reference, Long> writtenVersions = new HashMap<>();
    final Profile profile = getOrCreateProfile(app);
    synchronized (profile) {
      LiveTxn liveTxn = null;
      for (EntityProto clone : clones) {
        Profile.EntityGroup eg = profile.getGroup(clone.getEntityGroup());
        if (request.hasTransaction()) {
          // If there's a transaction we delay the put until
          // the transaction is committed.
          if (liveTxn == null) {
            liveTxn = profile.getTxn(request.getTransaction().getHandle());
          }
          checkRequest(!liveTxn.isReadOnly(), "Cannot modify entities in a read-only transaction.");
          // this will throw an exception if we attempt to
          // modify the wrong entity group
          eg.addTransaction(liveTxn).addWrittenEntity(clone);
        } else {
          List<EntityProto> entities = entitiesByEntityGroup.get(clone.getEntityGroup());
          if (entities == null) {
            entities = new ArrayList<>();
            entitiesByEntityGroup.put(clone.getEntityGroup(), entities);
          }
          entities.add(clone);
        }
        response.addKey(clone.getKey());
      }
      for (final Map.Entry<Path, List<EntityProto>> entry : entitiesByEntityGroup.entrySet()) {
        Profile.EntityGroup eg = profile.getGroup(entry.getKey());
        eg.incrementVersion();
        LocalDatastoreJob job =
            new WriteJob(
                highRepJobPolicy,
                eg,
                profile,
                entry.getValue(),
                Collections.<Reference>emptyList());
        addTo(totalCost, job.calculateJobCost());
        eg.addJob(job);

        for (EntityProto entity : entry.getValue()) {
          writtenVersions.put(entity.getKey(), job.getMutationTimestamp(entity.getKey()));
        }
      }
    }

    if (!request.hasTransaction()) {
      logger.fine("put: " + request.getEntityCount() + " entities");
      // Fill the version numbers, in the same order
      for (Reference key : response.getKeyList()) {
        response.addVersion(writtenVersions.get(key));
      }
    }
    response.setCost(totalCost);
    return response.build();
  }

  private void validateAndProcessEntityProto(EntityProto.Builder entity) {
    validatePathForPut(entity.getKey());
    for (Property.Builder prop : entity.getPropertyBuilderList()) {
      validateAndProcessProperty(prop);
      validateLengthLimit(prop);
    }
    for (Property.Builder prop : entity.getRawPropertyBuilderList()) {
      validateAndProcessProperty(prop);
      validateRawPropLengthLimit(prop);
    }
  }

  private void validatePathComplete(Reference key) {
    Path path = key.getPath();
    for (Element ele : path.getElementList()) {
      if (ele.getName().isEmpty() && ele.getId() == 0) {
        throw newError(
            ErrorCode.BAD_REQUEST, String.format("Incomplete key.path.element: %s", ele));
      }
    }
  }

  private void validatePathForPut(Reference key) {
    Path path = key.getPath();
    for (Element ele : path.getElementList()) {
      String type = ele.getType();
      if (RESERVED_NAME.matcher(type).matches() && !RESERVED_KIND_ALLOWLIST.contains(type)) {
        throw newError(
            ErrorCode.BAD_REQUEST,
            String.format("The key path element kind \"%s\" is reserved.", ele.getType()));
      }
      if (ele.hasName() && ele.getNameBytes().size() > MAX_STRING_LENGTH) {
        throw newError(ErrorCode.BAD_REQUEST, NAME_TOO_LONG);
      }
    }
  }

  private void validateAndProcessProperty(Property.Builder prop) {
    if (RESERVED_NAME.matcher(prop.getName()).matches()) {
      throw newError(
          ErrorCode.BAD_REQUEST, String.format("illegal property.name: %s", prop.getName()));
    }

    PropertyValue.Builder val = prop.getValueBuilder();
    if (val.hasUserValue() && !val.getUserValue().hasObfuscatedGaiaid()) {
      // If not already set, populate obfuscated gaia id with hash of email address.
      PropertyValue.UserValue.Builder userVal = val.getUserValueBuilder();
      userVal.setObfuscatedGaiaid(Integer.toString(userVal.getEmail().hashCode()));
    }
  }

  private void validateLengthLimit(PropertyOrBuilder property) {
    String name = property.getName();
    PropertyValue value = property.getValue();

    if (value.hasStringValue()) {
      if (property.hasMeaning() && property.getMeaning() == Property.Meaning.ATOM_LINK) {
        if (value.getStringValue().size() > MAX_LINK_LENGTH) {
          throw newError(
              ErrorCode.BAD_REQUEST,
              "Link property "
                  + name
                  + " is too long. Use TEXT for links over "
                  + MAX_LINK_LENGTH
                  + " bytes.");
        }
      } else if (property.hasMeaning() && property.getMeaning() == Property.Meaning.ENTITY_PROTO) {
        if (value.getStringValue().size() > MAX_BLOB_LENGTH) {
          throw newError(
              ErrorCode.BAD_REQUEST,
              "embedded entity property "
                  + name
                  + " is too big.  It cannot exceed "
                  + MAX_BLOB_LENGTH
                  + " bytes.");
        }
      } else {
        if (value.getStringValue().size() > MAX_STRING_LENGTH) {
          throw newError(
              ErrorCode.BAD_REQUEST,
              "string property "
                  + name
                  + " is too long.  It cannot exceed "
                  + MAX_STRING_LENGTH
                  + " bytes.");
        }
      }
    }
  }

  private void validateRawPropLengthLimit(PropertyOrBuilder property) {
    String name = property.getName();
    PropertyValue value = property.getValue();
    if (!value.hasStringValue() || !property.hasMeaning()) {
      return;
    }
    if (property.getMeaning() == Property.Meaning.BLOB
        || property.getMeaning() == Property.Meaning.ENTITY_PROTO
        || property.getMeaning() == Property.Meaning.TEXT) {
      if (value.getStringValue().size() > MAX_BLOB_LENGTH) {
        throw newError(
            ErrorCode.BAD_REQUEST,
            "Property " + name + " is too long. It cannot exceed " + MAX_BLOB_LENGTH + " bytes.");
      }
    }
  }

  public DeleteResponse delete(Status status, DeleteRequest request) {
    globalLock.readLock().lock();
    try {
      return deleteImpl(status, request);
    } finally {
      globalLock.readLock().unlock();
    }
  }

  public VoidProto addActions(Status status, TaskQueueBulkAddRequest request) {
    globalLock.readLock().lock();
    try {
      addActionsImpl(status, request);
    } finally {
      globalLock.readLock().unlock();
    }
    return VoidProto.getDefaultInstance();
  }

  /**
   * Returns the entity group for the specified key. This is simply a new {@code Path} instance
   * containing the first element in the specified key.
   */
  private Path getGroup(Reference key) {
    Path path = key.getPath();
    Path.Builder group = Path.newBuilder();
    group.addElement(path.getElement(0));
    return group.build();
  }

  @SuppressWarnings("unused") // status
  public DeleteResponse deleteImpl(Status status, DeleteRequest request) {
    DeleteResponse.Builder response = DeleteResponse.newBuilder();
    if (request.getKeyCount() == 0) {
      return response.build();
    }
    Cost.Builder totalCost = response.getCostBuilder();
    // We don't support requests that span apps, so the app for the first key
    // is the app for all keys.
    String app = request.getKey(0).getApp();
    final Profile profile = getOrCreateProfile(app);
    LiveTxn liveTxn = null;
    // Maintain a mapping of keys by entity group so that we can apply one job
    // per entity group.
    Map<Path, List<Reference>> keysByEntityGroup = new LinkedHashMap<>();
    Map<Reference, Long> writtenVersions = new HashMap<>();
    synchronized (profile) {
      for (final Reference key : request.getKeyList()) {
        validatePathComplete(key);
        Path group = getGroup(key);
        if (request.hasTransaction()) {
          if (liveTxn == null) {
            liveTxn = profile.getTxn(request.getTransaction().getHandle());
          }
          checkRequest(!liveTxn.isReadOnly(), "Cannot modify entities in a read-only transaction.");
          Profile.EntityGroup eg = profile.getGroup(group);
          // this will throw an exception if we attempt to modify
          // the wrong entity group
          eg.addTransaction(liveTxn).addDeletedEntity(key);
        } else {
          List<Reference> keysToDelete = keysByEntityGroup.get(group);
          if (keysToDelete == null) {
            keysToDelete = new ArrayList<>();
            keysByEntityGroup.put(group, keysToDelete);
          }
          keysToDelete.add(key);
        }
      }
      // Now loop over the entity groups.  We will attempt to apply one job that
      // does all the work for each entity group.
      for (final Map.Entry<Path, List<Reference>> entry : keysByEntityGroup.entrySet()) {
        Profile.EntityGroup eg = profile.getGroup(entry.getKey());
        eg.incrementVersion();
        LocalDatastoreJob job =
            new WriteJob(
                highRepJobPolicy,
                eg,
                profile,
                Collections.<EntityProto>emptyList(),
                entry.getValue());
        addTo(totalCost, job.calculateJobCost());
        eg.addJob(job);

        for (Reference deletedKey : entry.getValue()) {
          writtenVersions.put(deletedKey, job.getMutationTimestamp(deletedKey));
        }
      }
    }

    if (!request.hasTransaction()) {
      for (Reference key : request.getKeyList()) {
        response.addVersion(writtenVersions.get(key));
      }
    }
    return response.build();
  }

  @SuppressWarnings("unused") // status
  private void addActionsImpl(Status status, TaskQueueBulkAddRequest request) {
    // Does not verify that every TaskQueueAddRequest is part of the same transaction because this
    // checking is done at the API level.

    if (request.getAddRequestCount() == 0) {
      return;
    }

    // The transactional tasks need to be associated with the txn.
    // When the txn is committed the tasks will be sent back over to
    // the taskqueue stub. We need to wipe out their transactions before sending
    // so that the tasks actually get added and we don't continue spinning
    // around in and infinite loop.
    List<TaskQueueAddRequest> addRequests = new ArrayList<>(request.getAddRequestCount());
    for (TaskQueueAddRequest addRequest : request.getAddRequestList()) {
      addRequests.add(
          addRequest.toBuilder().clearTransaction().clearDatastoreTransaction().build());
    }

    Transaction transaction;
    if (request.getAddRequestList().get(0).hasDatastoreTransaction()) {
      ByteString datastoreTransaction =
          request.getAddRequestList().get(0).getDatastoreTransaction();
      try {
        transaction = Transaction.parser().parseFrom(datastoreTransaction);
      } catch (InvalidProtocolBufferException e) {
        throw newError(ErrorCode.BAD_REQUEST, "Invalid transaction");
      }
    } else {
      transaction = toProto1(request.getAddRequest(0).getTransaction());
    }

    Profile profile = profiles.get(transaction.getApp());
    LiveTxn liveTxn = profile.getTxn(transaction.getHandle());
    liveTxn.addActions(addRequests);
  }

  static Transaction toProto1(Transaction txn) {
    Transaction.Builder txnProto = Transaction.newBuilder();
    try {
      txnProto.mergeFrom(txn.toByteArray(), ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw newError(ErrorCode.BAD_REQUEST, "Invalid transaction");
    }
    return txnProto.build();
  }

  @SuppressWarnings("unused") // status
  public QueryResult runQuery(Status status, Query.Builder query) {
    // Construct a validated query right away so we can fail fast
    // if something is wrong.
    final LocalCompositeIndexManager.ValidatedQuery validatedQuery =
        new LocalCompositeIndexManager.ValidatedQuery(query);
    query = validatedQuery.getV3Query();

    // Modernize the query's cursors.
    // NOTE: Modernization must follow (not precede) construction of
    // LocalCompositeIndexManager.ValidatedQuery.  I don't know why.
    try {
      CursorModernizer.modernizeQueryCursors(query);
    } catch (InvalidConversionException e) {
      throw newError(ErrorCode.BAD_REQUEST, "Invalid cursor");
    }

    String app = query.getApp();
    Profile profile = getOrCreateProfile(app);

    synchronized (profile) {
      if (query.hasTransaction()) {
        if (!app.equals(query.getTransaction().getApp())) {
          throw newError(
              ErrorCode.INTERNAL_ERROR,
              "Can't query app "
                  + app
                  + "in a transaction on app "
                  + query.getTransaction().getApp());
        }
      }

      if (query.hasAncestor()) {
        Path groupPath = getGroup(query.getAncestor());
        Profile.EntityGroup eg = profile.getGroup(groupPath);
        if (query.hasTransaction()) {
          LiveTxn liveTxn = profile.getTxn(query.getTransaction().getHandle());
          // this will throw an exception if we attempt to read from
          // the wrong entity group
          eg.addTransaction(liveTxn);
          // Use snapshot profile.
          profile = eg.getSnapshot(liveTxn);
        }

        if (query.hasTransaction() || !query.hasFailoverMs()) {
          // Either we have a transaction or the user has requested strongly
          // consistent results.  Either way, we need to apply jobs.
          eg.rollForwardUnappliedJobs();
        }
      }

      if (query.hasSearchQuery()) {
        throw newError(ErrorCode.BAD_REQUEST, "full-text search unsupported");
      }

      // Run as a PseudoKind query if necessary, otherwise check the actual local datastore
      List<EntityProto> queryEntities = pseudoKinds.runQuery(query.build());
      Map<Reference, Long> versions = null;

      if (queryEntities == null) {
        Collection<VersionedEntity> versionedEntities = null;
        Map<String, Extent> extents = profile.getExtents();
        Extent extent = extents.get(query.getKind());

        if (extent != null) {
          // Make a copy of the list of all the entities in the extent
          versionedEntities = extent.getAllEntities();
        } else if (!query.hasKind()) {
          // Kind-less query, so we need a list containing all entities of
          // all kinds.
          versionedEntities = profile.getAllEntities();
          if (query.getOrderCount() == 0) {
            // add a sort by key asc to match the behavior of prod
            query.addOrder(
                Order.newBuilder()
                    .setDirection(Query.Order.Direction.ASCENDING)
                    .setProperty(Entity.KEY_RESERVED_PROPERTY));
          }
        } else {
          // no extent - we're querying for a kind without any entities
        }

        if (versionedEntities != null) {
          queryEntities = new ArrayList<>();
          versions = new HashMap<>();
          for (VersionedEntity entity : versionedEntities) {
            queryEntities.add(entity.entityProto());
            versions.put(entity.entityProto().getKey(), entity.version());
          }
        }
      }
      // Give all entity groups with unapplied jobs the opportunity to catch
      // up.  Note that this will not impact the result of the query we're
      // currently fulfilling since we already have the (unfiltered) result
      // set.
      profile.groom();

      if (queryEntities == null) {
        // so we don't need to check for null anywhere else down below
        queryEntities = Collections.emptyList();
      }

      // Building filter predicate
      List<Predicate<EntityProto>> predicates = new ArrayList<>();
      // apply ancestor restriction
      if (query.hasAncestor()) {
        final List<Element> ancestorPath = query.getAncestor().getPath().getElementList();
        predicates.add(
            new Predicate<EntityProto>() {
              @Override
              public boolean apply(EntityProto entity) {
                List<Element> path = entity.getKey().getPath().getElementList();
                return path.size() >= ancestorPath.size()
                    && path.subList(0, ancestorPath.size()).equals(ancestorPath);
              }
            });
      }

      if (query.getShallow()) {
        final long keyPathLength =
            query.hasAncestor() ? query.getAncestor().getPath().getElementCount() + 1 : 1;
        predicates.add(
            new Predicate<EntityProto>() {
              @Override
              public boolean apply(EntityProto entity) {
                return entity.getKey().getPath().getElementCount() == keyPathLength;
              }
            });
      }

      // apply namespace restriction
      final boolean hasNamespace = query.hasNameSpace();
      final String namespace = query.getNameSpace();
      predicates.add(
          new Predicate<EntityProto>() {
            @Override
            public boolean apply(EntityProto entity) {
              Reference ref = entity.getKey();
              // Filter all elements not in the query's namespace.
              if (hasNamespace) {
                if (!ref.hasNameSpace() || !namespace.equals(ref.getNameSpace())) {
                  return false;
                }
              } else {
                if (ref.hasNameSpace()) {
                  return false;
                }
              }
              return true;
            }
          });

      // Get entityComparator with filter matching capability
      final EntityProtoComparator entityComparator =
          new EntityProtoComparator(
              validatedQuery.getQuery().getOrderList(), validatedQuery.getQuery().getFilterList());

      // applying filter restrictions
      predicates.add(
          new Predicate<EntityProto>() {
            @Override
            public boolean apply(EntityProto entity) {
              return entityComparator.matches(entity);
            }
          });

      Predicate<EntityProto> queryPredicate =
          Predicates.<EntityProto>not(Predicates.<EntityProto>and(predicates));

      // The ordering of the following operations is important to maintain correct
      // query functionality.

      // Filtering entities
      Iterables.removeIf(queryEntities, queryPredicate);

      // Expanding projections
      if (query.getPropertyNameCount() > 0) {
        queryEntities = createIndexOnlyQueryResults(queryEntities, entityComparator);
      }
      // Sorting entities
      Collections.sort(queryEntities, entityComparator);

      // Apply group by. This must happen after sorting to select the correct first entity.
      queryEntities = applyGroupByProperties(queryEntities, query);

      // store the query and return the results
      LiveQuery liveQuery = new LiveQuery(queryEntities, versions, query, entityComparator, clock);

      // CompositeIndexManager does some filesystem reads/writes
      LocalCompositeIndexManager.getInstance().processQuery(validatedQuery.getV3Query());

      // Using next function to prefetch results and return them from runQuery
      QueryResult.Builder result =
          liveQuery.nextResult(
              query.hasOffset() ? query.getOffset() : null,
              query.hasCount() ? query.getCount() : null,
              query.getCompile());
      if (query.getCompile()) {
        result.setCompiledQuery(liveQuery.compileQuery());
      }
      if (result.getMoreResults()) {
        long cursor = queryId.getAndIncrement();
        profile.addQuery(cursor, liveQuery);
        result.getCursorBuilder().setApp(query.getApp()).setCursor(cursor);
      }
      // Copy the index list for the query into the result.
      for (Index index : LocalCompositeIndexManager.getInstance().queryIndexList(query)) {
        result.addIndex(wrapIndexInCompositeIndex(app, index));
      } // for
      if (!result
          .hasMoreResults()) { // more_results is a required field so we need to set it to false}
        result.setMoreResults(false);
      }
      return result.build();
    }
  }

  @AutoValue
  abstract static class NameValue {
    public abstract String name();

    public abstract PropertyValue value();

    public static NameValue of(String name, PropertyValue value) {
      return new AutoValue_LocalDatastoreService_NameValue(name, value);
    }
  }

  /**
   * Creates a new List of entities after applying group by properties.
   *
   * @param queryEntities a sorted list of entities.
   * @param query the current query.
   * @return a new list of entities with unique properties.
   */
  private List<EntityProto> applyGroupByProperties(
      List<EntityProto> queryEntities, Query.Builder query) {
    Set<String> groupByProperties = Sets.newHashSet(query.getGroupByPropertyNameList());

    // Nothing to do if there are no group by properties.
    if (groupByProperties.isEmpty()) {
      return queryEntities;
    }

    Set<NameValue> lastEntity = Sets.newHashSet();
    List<EntityProto> results = Lists.newArrayList();
    for (EntityProto entity : queryEntities) {
      boolean isFirst = false;
      for (Property prop : entity.getPropertyList()) {
        if (groupByProperties.contains(prop.getName())
            && !lastEntity.contains(NameValue.of(prop.getName(), prop.getValue()))) {
          isFirst = true;
          break;
        }
      }
      if (isFirst) {
        results.add(entity);
        // Set lastEntity to be the new set of properties.
        lastEntity.clear();
        for (Property prop : entity.getPropertyList()) {
          if (groupByProperties.contains(prop.getName())) {
            lastEntity.add(NameValue.of(prop.getName(), prop.getValue()));
          }
        }
      }
    }
    return results;
  }

  /**
   * Converts a normal result set into the results seen in an index-only query (a projection).
   *
   * @param queryEntities the results to convert
   * @param entityComparator the comparator derived from the query
   * @return the converted results
   */
  private List<EntityProto> createIndexOnlyQueryResults(
      List<EntityProto> queryEntities, EntityProtoComparator entityComparator) {
    Set<String> postfixProps =
        Sets.newHashSetWithExpectedSize(entityComparator.getAdjustedOrders().size());
    for (Query.Order order : entityComparator.getAdjustedOrders()) {
      postfixProps.add(order.getProperty());
    }

    List<EntityProto> results = Lists.newArrayListWithExpectedSize(queryEntities.size());
    for (EntityProto entity : queryEntities) {
      List<EntityProto> indexEntities = createIndexEntities(entity, postfixProps, entityComparator);
      results.addAll(indexEntities);
    }

    return results;
  }

  /**
   * Splits a full entity into all index entities seen in a projection.
   *
   * @param entity the entity to split
   * @param postfixProps the properties included in the postfix
   * @return A list of the index entities.
   */
  private ImmutableList<EntityProto> createIndexEntities(
      EntityProto entity, Set<String> postfixProps, EntityProtoComparator entityComparator) {
    SetMultimap<String, PropertyValue> toSplit =
        MultimapBuilder.hashKeys(postfixProps.size()).hashSetValues(1).build();
    Set<String> seen = Sets.newHashSet();
    boolean splitRequired = false;
    for (Property prop : entity.getPropertyList()) {
      if (postfixProps.contains(prop.getName())) {
        // If we have multiple values for any postfix property, we need to split.
        splitRequired |= !seen.add(prop.getName());
        // Only add the value if it matches the query filters
        if (entityComparator.matches(prop)) {
          toSplit.put(prop.getName(), prop.getValue());
        }
      }
    }

    if (!splitRequired) {
      // No need for splitting!
      return ImmutableList.of(entity);
    }

    EntityProto.Builder clone = EntityProto.newBuilder();
    clone.getKeyBuilder().mergeFrom(entity.getKey());
    clone.setEntityGroup(Path.getDefaultInstance());
    List<EntityProto.Builder> results = Lists.newArrayList(clone);

    for (Map.Entry<String, Collection<PropertyValue>> entry : toSplit.asMap().entrySet()) {
      if (entry.getValue().size() == 1) {
        // No need for cloning!
        for (EntityProto.Builder result : results) {
          result
              .addPropertyBuilder()
              .setName(entry.getKey())
              .setMeaning(Property.Meaning.INDEX_VALUE)
              .setMultiple(false)
              .getValueBuilder()
              .mergeFrom(Iterables.getOnlyElement(entry.getValue()));
        }
        continue;
      }
      List<EntityProto.Builder> splitResults =
          Lists.newArrayListWithCapacity(results.size() * entry.getValue().size());
      for (PropertyValue value : entry.getValue()) {
        for (EntityProto.Builder result : results) {
          EntityProto.Builder split = result.clone();
          split
              .addPropertyBuilder()
              .setName(entry.getKey())
              .setMeaning(Property.Meaning.INDEX_VALUE)
              .setMultiple(false)
              .getValueBuilder()
              .mergeFrom(value);
          splitResults.add(split);
        }
      }
      results = splitResults;
    }
    ImmutableList.Builder<EntityProto> builder = ImmutableList.builder();
    for (EntityProto.Builder result : results) {
      builder.add(result.build());
    }
    return builder.build();
  }

  /**
   * Retrieves the value in the given map keyed by the provided key, throwing an appropriate {@link
   * ApplicationException} if there is no value associated with the key.
   */
  private static <T> T safeGetFromExpiringMap(Map<Long, T> map, long key, String errorMsg) {
    T value = map.get(key);
    if (value == null) {
      throw newError(ErrorCode.BAD_REQUEST, errorMsg);
    }
    return value;
  }

  @SuppressWarnings("unused") // status
  public QueryResult next(Status status, NextRequest request) {
    Profile profile = profiles.get(request.getCursor().getApp());
    LiveQuery liveQuery = profile.getQuery(request.getCursor().getCursor());

    QueryResult.Builder result =
        liveQuery.nextResult(
            request.hasOffset() ? request.getOffset() : null,
            request.hasCount() ? request.getCount() : null,
            request.getCompile());

    if (result.getMoreResults()) {
      result.setCursor(request.getCursor());
    } else {
      profile.removeQuery(request.getCursor().getCursor());
    }

    return result.build();
  }

  @SuppressWarnings("unused") // status
  public VoidProto deleteCursor(Status status, Cursor request) {
    Profile profile = profiles.get(request.getApp());
    profile.removeQuery(request.getCursor());
    return VoidProto.getDefaultInstance();
  }

  @SuppressWarnings("unused") // status
  public Transaction beginTransaction(Status status, BeginTransactionRequest req) {
    Profile profile = getOrCreateProfile(req.getApp());

    if (req.hasPreviousTransaction()) {
      if (req.getMode() == TransactionMode.READ_ONLY) {
        throw newError(ErrorCode.BAD_REQUEST, TRANSACTION_RETRY_ON_READ_ONLY);
      }

      // synchronize to prevent check-remove race on previous transaction
      synchronized (profile) {
        LiveTxn previousTransaction =
            profile.getTxnQuietly(req.getPreviousTransaction().getHandle());

        if (previousTransaction != null) {
          if (previousTransaction.concurrencyMode == ConcurrencyMode.READ_ONLY) {
            throw newError(ErrorCode.BAD_REQUEST, TRANSACTION_RETRY_ON_PREVIOUSLY_READ_ONLY);
          }

          if (previousTransaction.allowMultipleEg != req.getAllowMultipleEg()) {
            throw newError(ErrorCode.BAD_REQUEST, TRANSACTION_OPTIONS_CHANGED_ON_RESET);
          }

          profile.removeTxn(req.getPreviousTransaction().getHandle());
        }
      }
    }

    Transaction.Builder txn =
        Transaction.newBuilder()
            .setApp(req.getApp())
            .setHandle(transactionHandleProvider.getAndIncrement());
    ConcurrencyMode mode = toConcurrencyMode(req.getMode());
    profile.addTxn(txn.getHandle(), new LiveTxn(clock, req.getAllowMultipleEg(), req.getMode()));
    return txn.build();
  }

  @SuppressWarnings("unused") // status
  public CommitResponse commit(Status status, final Transaction req) {
    Profile profile = profiles.get(req.getApp());
    checkNotNull(profile);
    CommitResponse.Builder response = CommitResponse.newBuilder();

    globalLock.readLock().lock();

    // Synchronized so we can't commit and rollback at the same time.
    synchronized (profile) {
      LiveTxn liveTxn;

      try {
        liveTxn = profile.removeTxn(req.getHandle());

        try {
          if (liveTxn.isDirty()) {
            response = commitImpl(liveTxn, profile);
          } else {
            // cost of a read-only txn is 0
            response.setCost(Cost.newBuilder().setEntityWrites(0).setIndexWrites(0).build());
          }
        } catch (ApplicationException e) {
          // commit failed, re-add transaction so that it can be rolled back or reset.
          profile.addTxn(
              req.getHandle(),
              new LiveTxn(clock, liveTxn.allowMultipleEg, liveTxn.originalTransactionMode, true));
          throw e;
        }
      } finally {
        globalLock.readLock().unlock();
      }

      // Sends all pending actions.
      // Note: this is an approximation of the true Datastore behavior.
      // Currently, dev_server holds taskqueue tasks in memory, so they are lost
      // on a dev_server restart.
      // TODO: persist actions as a part of the transactions when
      // taskqueue tasks become durable.
      for (TaskQueueAddRequest action : liveTxn.getActions()) {
        try {
          addActionImpl(action);
        } catch (ApplicationException e) {
          logger.log(Level.WARNING, "Transactional task: " + action + " has been dropped.", e);
        }
      }
    }
    return response.build();
  }

  /** Requires a lock on the provided profile. */
  private CommitResponse.Builder commitImpl(LiveTxn liveTxn, final Profile profile) {
    // assumes we already have a lock on the profile
    CommitResponse.Builder response = CommitResponse.newBuilder();

    for (EntityGroupTracker tracker : liveTxn.getAllTrackers()) {
      // This will throw an exception if the entity group
      // has been modified since this transaction started.
      tracker.checkEntityGroupVersion();
    }

    int deleted = 0;
    int written = 0;
    Cost.Builder totalCost = Cost.newBuilder();
    long commitTimestamp = profile.incrementAndGetCommitTimestamp();
    for (EntityGroupTracker tracker : liveTxn.getAllTrackers()) {
      Profile.EntityGroup eg = tracker.getEntityGroup();
      eg.incrementVersion();

      final Collection<EntityProto> writtenEntities = tracker.getWrittenEntities();
      final Collection<Reference> deletedKeys = tracker.getDeletedKeys();

      LocalDatastoreJob job =
          new WriteJob(
              highRepJobPolicy, eg, profile, commitTimestamp, writtenEntities, deletedKeys);
      addTo(totalCost, job.calculateJobCost());
      eg.addJob(job);

      deleted += deletedKeys.size();
      written += writtenEntities.size();

      for (EntityProto writtenEntity : writtenEntities) {
        response
            .addVersionBuilder()
            .setRootEntityKey(writtenEntity.getKey())
            .setVersion(job.getMutationTimestamp(writtenEntity.getKey()));
      }
      for (Reference deletedKey : deletedKeys) {
        response
            .addVersionBuilder()
            .setRootEntityKey(deletedKey)
            .setVersion(job.getMutationTimestamp(deletedKey));
      }
    }
    logger.fine(
        "committed: "
            + written
            + " puts, "
            + deleted
            + " deletes in "
            + liveTxn.getAllTrackers().size()
            + " entity groups");
    response.setCost(totalCost);

    return response;
  }

  @SuppressWarnings("unused") // status
  public VoidProto rollback(Status status, Transaction req) {
    profiles.get(req.getApp()).removeTxn(req.getHandle());
    return VoidProto.getDefaultInstance();
  }

  // index operations

  @SuppressWarnings("unused") // status
  public Integer64Proto createIndex(Status status, CompositeIndex req) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @SuppressWarnings("unused") // status
  public VoidProto updateIndex(Status status, CompositeIndex req) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  private CompositeIndex wrapIndexInCompositeIndex(String app, @Nullable Index index) {
    CompositeIndex.Builder ci =
        CompositeIndex.newBuilder()
            .setAppId(app)
            .setId(0)
            .setState(CompositeIndex.State.READ_WRITE);
    if (index != null) {
      ci.setDefinition(index);
    }
    return ci.build();
  }

  @SuppressWarnings("unused") // status
  public CompositeIndices getIndices(Status status, StringProto req) {
    Set<Index> indexSet = LocalCompositeIndexManager.getInstance().getIndexes();
    CompositeIndices.Builder answer = CompositeIndices.newBuilder();
    for (Index index : indexSet) {
      CompositeIndex ci = wrapIndexInCompositeIndex(req.getValue(), index);
      answer.addIndex(ci);
    }
    return answer.build();
  }

  @SuppressWarnings("unused") // status
  public VoidProto deleteIndex(Status status, CompositeIndex req) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @SuppressWarnings("unused") // status
  public AllocateIdsResponse allocateIds(Status status, AllocateIdsRequest req) {
    globalLock.readLock().lock();
    try {
      return allocateIdsImpl(req);
    } finally {
      globalLock.readLock().unlock();
    }
  }

  private AllocateIdsResponse allocateIdsImpl(AllocateIdsRequest req) {
    if (req.hasSize()) {
      if (req.getSize() > MAX_BATCH_GET_KEYS) { // 1 billion
        throw newError(
            ErrorCode.BAD_REQUEST,
            "cannot get more than " + MAX_BATCH_GET_KEYS + " keys in a single call");
      }
      // The local implementation just ignores the path in the request
      // because we have a single, global counter.

      // Suppose currentId is 100 and the request is for 5.
      // We'll return a range of 100 - 104, leaving entityIdSequential with a value of 105.
      // Now the next request asks for 10.
      // We'll return a range of 105 - 114, leaving entityIdSequential with a value of 115.
      long start = entityIdSequential.getAndAdd(req.getSize());
      return AllocateIdsResponse.newBuilder()
          .setStart(start)
          .setEnd(start + req.getSize() - 1)
          .build();
    } else {
      long current = entityIdSequential.get();
      while (current <= req.getMax()) {
        if (entityIdSequential.compareAndSet(current, req.getMax() + 1)) {
          break;
        }
        current = entityIdSequential.get();
      }
      return AllocateIdsResponse.newBuilder()
          .setStart(current)
          .setEnd(Math.max(req.getMax(), current - 1))
          .build();
    }
  }

  static long toScatteredId(long counter) {
    if (counter >= MAX_SCATTERED_COUNTER) {
      throw newError(ErrorCode.INTERNAL_ERROR, "Maximum scattered ID counter value exceeded");
    }
    return MAX_SEQUENTIAL_ID + 1 + Long.reverse(counter << SCATTER_SHIFT);
  }

  Profile getOrCreateProfile(String app) {
    synchronized (profiles) {
      checkArgument(app != null && app.length() > 0, "appId not set");
      Profile profile = profiles.get(app);
      if (profile == null) {
        profile = new Profile();
        profile.setAppId(app);
        profiles.put(app, profile);
      }
      return profile;
    }
  }

  Extent getOrCreateExtent(Profile profile, String kind) {
    Map<String, Extent> extents = profile.getExtents();
    synchronized (extents) {
      Extent e = extents.get(kind);
      if (e == null) {
        e = new Extent();
        extents.put(kind, e);
      }
      return e;
    }
  }

  // <internal23>
  private void load() {
    if (noStorage) {
      return;
    }
    File backingStoreFile = new File(backingStore);
    String path = backingStoreFile.getAbsolutePath();
    if (!backingStoreFile.exists()) {
      logger.log(
          Level.INFO, "The backing store, " + path + ", does not exist. " + "It will be created.");
      backingStoreFile.getParentFile().mkdirs();
      return;
    }

    long start = clock.getCurrentTime();
    try (ObjectInputStream objectIn =
        new ObjectInputStream(new BufferedInputStream(new FileInputStream(backingStore)))) {

      long version = -objectIn.readLong();
      if (version < 0) {
        // It's not the version code after all, it's the sequential ID counter of a persisted
        // local datastore from a time before version codes and scattered IDs.
        entityIdSequential.set(-version);
      } else {
        entityIdSequential.set(objectIn.readLong());
        entityIdScattered.set(objectIn.readLong());
      }
      @SuppressWarnings("unchecked")
      Map<String, Profile> profilesOnDisk = (Map<String, Profile>) objectIn.readObject();
      for (Map.Entry<String, Profile> entry : profilesOnDisk.entrySet()) {
        entry.getValue().setAppId(entry.getKey());
      }
      synchronized (profiles) {
        profiles.clear();
        profiles.putAll(profilesOnDisk);
      }
      long end = clock.getCurrentTime();

      logger.log(Level.INFO, "Time to load datastore: " + (end - start) + " ms");
    } catch (FileNotFoundException e) {
      // Should never happen, because we just checked for it
      logger.log(Level.SEVERE, "Failed to find the backing store, " + path);
    } catch (IOException | ClassNotFoundException e) {
      logger.log(Level.INFO, "Failed to load from the backing store, " + path, e);
    }
  }

  /** A profile for an application. Contains all the Extents owned by the application. */
  static class Profile implements Serializable {

    private static final long MINIMUM_VERSION = 1;

    /* Default serial version from 195 SDK. */
    private static final long serialVersionUID = -4667954926644227154L;

    private transient String appId;

    String getAppId() {
      return appId;
    }

    void setAppId(String appId) {
      this.appId = appId;
    }

    /**
     * An EntityGroup maintains a consistent view of a profile during a transaction. All access to
     * an entity group should be synchronized on the enclosing profile.
     */
    class EntityGroup {
      private final Path path;
      private final AtomicLong version = new AtomicLong();
      private final WeakHashMap<LiveTxn, Profile> snapshots = new WeakHashMap<LiveTxn, Profile>();
      // Using a LinkedList because we insert at the end and remove from the front.
      private final LinkedList<LocalDatastoreJob> unappliedJobs =
          new LinkedList<LocalDatastoreJob>();

      private EntityGroup(Path path) {
        this.path = path;
      }

      public long getVersion() {
        return version.get();
      }

      /**
       * Mark an entity group as modified. If there are open transactions for the current version of
       * the entity group, this will take a snapshot of the profile which the transactions will
       * continue to read from. You must call this before actually modifying the entities, or the
       * snapshot will be incorrect.
       */
      public void incrementVersion() {
        long oldVersion = version.getAndIncrement();
        Profile snapshot = null;
        for (Map.Entry<LiveTxn, Profile> entry : snapshots.entrySet()) {
          LiveTxn txn = entry.getKey();
          if (txn.trackEntityGroup(this).getEntityGroupVersion() == oldVersion) {
            if (snapshot == null) {
              snapshot = takeSnapshot();
            }
            entry.setValue(snapshot);
          }
        }
      }

      /**
       * Get an entity from the profile by key.
       *
       * <p>If there's a transaction, it reads from the transaction's snapshot instead of the
       * profile.
       */
      public VersionedEntity get(
          @Nullable LiveTxn liveTxn, Reference key, boolean eventualConsistency) {
        if (!eventualConsistency) {
          // User wants strongly consistent results so we must roll forward.
          rollForwardUnappliedJobs();
        }
        Profile profile = getSnapshot(liveTxn);
        Map<String, Extent> extents = profile.getExtents();
        Extent extent = extents.get(getKind(key));
        if (extent != null) {
          return extent.getEntityByKey(key);
        }
        return null;
      }

      public EntityGroupTracker addTransaction(LiveTxn txn) {
        EntityGroupTracker tracker = txn.trackEntityGroup(this);
        if (!snapshots.containsKey(txn)) {
          snapshots.put(txn, null);
        }
        return tracker;
      }

      public void removeTransaction(LiveTxn txn) {
        snapshots.remove(txn);
      }

      private Profile getSnapshot(@Nullable LiveTxn txn) {
        if (txn == null) {
          return Profile.this;
        }
        Profile snapshot = snapshots.get(txn);
        if (snapshot == null) {
          return Profile.this;
        } else {
          return snapshot;
        }
      }

      // <internal23>
      private Profile takeSnapshot() {
        try {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(bos);
          oos.writeObject(Profile.this);
          oos.close();
          ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
          ObjectInputStream ois = new ObjectInputStream(bis);
          Profile profile = (Profile) ois.readObject();
          profile.setAppId(Profile.this.getAppId());
          return profile;
        } catch (IOException | ClassNotFoundException ex) {
          throw new RuntimeException("Unable to take transaction snapshot.", ex);
        }
      }

      @Override
      public String toString() {
        return path.toString();
      }

      /**
       * Returns the latest unapplied {@link LocalDatastoreJob} for this entity group. If there are
       * no unapplied job, returns {@code null}.
       */
      public @Nullable LocalDatastoreJob getLastJob() {
        return unappliedJobs.isEmpty() ? null : unappliedJobs.getLast();
      }

      public void addJob(LocalDatastoreJob job) {
        // need to apply existing unapplied jobs before we can try to apply
        // this one
        unappliedJobs.addLast(job);
        getGroupsWithUnappliedJobs().add(path);
        maybeRollForwardUnappliedJobs();
      }

      /** Rolls forward all unapplied jobs for the entity group without consulting the policy. */
      public void rollForwardUnappliedJobs() {
        if (!unappliedJobs.isEmpty()) {
          for (LocalDatastoreJob applyJob : unappliedJobs) {
            applyJob.apply();
          }
          unappliedJobs.clear();
          getGroupsWithUnappliedJobs().remove(path);
          logger.fine("Rolled forward unapplied jobs for " + path);
        }
      }

      /**
       * Attempts to roll forward all unapplied jobs for the entity group, consulting the policy for
       * each job to see if it should proceed. Since jobs must be applied in order, we stop applying
       * as soon as we are unable to apply a single job.
       */
      public void maybeRollForwardUnappliedJobs() {
        int jobsAtStart = unappliedJobs.size();
        logger.fine(
            String.format("Maybe rolling forward %d unapplied jobs for %s.", jobsAtStart, path));
        int applied = 0;
        for (Iterator<LocalDatastoreJob> iter = unappliedJobs.iterator(); iter.hasNext(); ) {
          if (iter.next().tryApply()) {
            iter.remove();
            applied++;
          } else {
            // Jobs must apply in order, so if we get one failure we have to
            // stop.
            break;
          }
        }
        if (unappliedJobs.isEmpty()) {
          getGroupsWithUnappliedJobs().remove(path);
        }
        logger.fine(
            String.format("Rolled forward %d of %d jobs for %s", applied, jobsAtStart, path));
      }

      public Key pathAsKey() {
        Reference.Builder entityGroupRef = Reference.newBuilder();
        entityGroupRef.setPath(path);
        entityGroupRef.setApp(getAppId());
        return LocalCompositeIndexManager.KeyTranslator.createFromPb(entityGroupRef.build());
      }
    }

    // synchronized to ensure a consistent view of all the entities
    public synchronized List<VersionedEntity> getAllEntities() {
      List<VersionedEntity> entities = new ArrayList<>();
      for (Extent extent : extents.values()) {
        entities.addAll(extent.getAllEntities());
      }
      return entities;
    }

    private long lastCommitTimestamp = MINIMUM_VERSION;

    private final Map<String, Extent> extents =
        Collections.synchronizedMap(new HashMap<String, Extent>());

    // These four maps are transient to preserve the Serialized format.
    // Since uncommitted transactions are not persisted it is
    // fine to reset the entity group version numbers when
    // loading the Datastore from disk.
    // All access to this map must be synchronized.  We initialize it lazily
    // because initializers for transient fields don't run when an object is
    // deserialized.
    private transient Map<Path, EntityGroup> groups;

    // All access to this set must be synchronized.  We initialize it lazily
    // because initializers for transient fields don't run when an object is
    // deserialized.
    private transient Set<Path> groupsWithUnappliedJobs;

    /** The set of outstanding query results, keyed by query id (also referred to as "cursor"). */
    private transient Map<Long, LiveQuery> queries;

    /** The set of active transactions, keyed by transaction id (also referred to as "handle"). */
    private transient Map<Long, LiveTxn> txns;

    /**
     * Returns the current timestamp of the profile. This is equal to the commit timestamp of the
     * last job added to the profile.
     */
    public long getReadTimestamp() {
      return lastCommitTimestamp;
    }

    /**
     * Returns a commit timestamp for a newly created Job. This increments the read timestamp of the
     * profile.
     */
    private long incrementAndGetCommitTimestamp() {
      return ++lastCommitTimestamp;
    }

    /**
     * Returns the set of all {@code Extents} for this {@code Profile}, organized by kind. The
     * returned {@code Map} is synchronized.
     *
     * @return map of {@code Extents} organized by kind
     */
    public Map<String, Extent> getExtents() {
      return extents;
    }

    public synchronized EntityGroup getGroup(Path path) {
      Map<Path, EntityGroup> map = getGroups();
      EntityGroup group = map.get(path);
      if (group == null) {
        group = new EntityGroup(path);
        map.put(path, group);
      }
      return group;
    }

    /**
     * The "groomer" for the local datastore. Rather than relying on a background thread, we instead
     * implement a method that iterates over all entity groups with unapplied jobs and gives each
     * entity group the opportunity to apply these jobs. This makes grooming behavior independent of
     * time and instead ties it to operations that users control, which makes tests much easier to
     * write.
     */
    private synchronized void groom() {
      // Need to iterate over a copy because grooming manipulates the list
      // we're iterating over. Note that a consistent order is necessary to
      // get consistent grooming.
      for (Path path : new LinkedHashSet<Path>(getGroupsWithUnappliedJobs())) {
        EntityGroup eg = getGroup(path);
        eg.maybeRollForwardUnappliedJobs();
      }
    }

    public synchronized LiveQuery getQuery(long cursor) {
      return safeGetFromExpiringMap(getQueries(), cursor, QUERY_NOT_FOUND);
    }

    public synchronized void addQuery(long cursor, LiveQuery query) {
      getQueries().put(cursor, query);
    }

    private synchronized LiveQuery removeQuery(long cursor) {
      LiveQuery query = getQuery(cursor);
      queries.remove(cursor);
      return query;
    }

    private synchronized Map<Long, LiveQuery> getQueries() {
      if (queries == null) {
        queries = new HashMap<>();
      }
      return queries;
    }

    public synchronized LiveTxn getTxn(long handle) {
      return safeGetFromExpiringMap(getTxns(), handle, TRANSACTION_NOT_FOUND);
    }

    @Nullable
    public synchronized LiveTxn getTxnQuietly(long handle) {
      return getTxns().get(handle);
    }

    public synchronized void addTxn(long handle, LiveTxn txn) {
      getTxns().put(handle, txn);
    }

    private synchronized LiveTxn removeTxn(long handle) {
      LiveTxn txn = getTxn(handle);
      txn.close();
      txns.remove(handle);
      return txn;
    }

    private synchronized Map<Long, LiveTxn> getTxns() {
      if (txns == null) {
        txns = new HashMap<>();
      }
      return txns;
    }

    private synchronized Map<Path, EntityGroup> getGroups() {
      if (groups == null) {
        groups = new LinkedHashMap<Path, EntityGroup>();
      }
      return groups;
    }

    private synchronized Set<Path> getGroupsWithUnappliedJobs() {
      if (groupsWithUnappliedJobs == null) {
        groupsWithUnappliedJobs = new LinkedHashSet<Path>();
      }
      return groupsWithUnappliedJobs;
    }
  }

  /**
   * An EntityProto with its associated version number. Version numbers are monotically increasing
   * with every modification to the entity.
   */
  @AutoValue
  abstract static class VersionedEntity {

    public abstract EntityProto entityProto();

    public abstract long version();

    public static VersionedEntity create(EntityProto entityProto, long version) {
      return new AutoValue_LocalDatastoreService_VersionedEntity(entityProto, version);
    }
  }

  /** The set of all {@link EntityProto EntityProtos} of a single kind, organized by id. */
  static class Extent implements Serializable {

    /**
     * Uses a LinkedHashMap to facilitate testing. Yes, there's a cost to this but this is the
     * datastore stub so it should be tolerable.
     *
     * <p>Entities are identified by the key of the proto, which is guaranteed to be unique.
     *
     * <p>Non-final to permit manual initialization during deserialization. Serialization uses proto
     * format for EntityProtos, instead of default Java format, to keep SDK local db backwards
     * compatible as proto internals change.
     */
    private Map<Reference, EntityProto> entities = new LinkedHashMap<>();

    /**
     * A mapping from entity keys to last updated versions. If an entity exists in the extent, then
     * it is guaranteed to have a version in this map. We use MINIMUM_VERSION to represent entities
     * for which the version number is unknown, for example because they were restored from a legacy
     * Profile serialization format.
     */
    private Map<Reference, Long> versions = new HashMap<>();

    /* Default serial version from 195 SDK. */
    private static final long serialVersionUID = 1199103439874512494L;

    /**
     * The property name used to store the version of an entity when persisting the datastore to
     * disk.
     */
    private static final String ENTITY_VERSION_RESERVED_PROPERTY = "__entity_version__";

    public Collection<VersionedEntity> getAllEntities() {
      ImmutableList.Builder<VersionedEntity> builder = ImmutableList.builder();
      for (Reference key : entities.keySet()) {
        builder.add(getEntityByKey(key));
      }
      return builder.build();
    }

    public Collection<EntityProto> getAllEntityProtos() {
      return entities.values();
    }

    public VersionedEntity getEntityByKey(Reference key) {
      EntityProto entity = entities.get(key);
      Long version = versions.get(key);

      return (entity == null) ? null : VersionedEntity.create(entity, version);
    }

    public EntityProto getEntityProtoByKey(Reference key) {
      return entities.get(key);
    }

    public void removeEntity(Reference key) {
      versions.remove(key);
      entities.remove(key);
    }

    public void putEntity(VersionedEntity entity) {
      Reference key = entity.entityProto().getKey();
      entities.put(key, entity.entityProto());
      versions.put(key, entity.version());
    }

    /**
     * Serializes a given {@link VersionedEntity} to a byte array, used by the {@link Serializable}
     * implementation of {@link Extent}.
     */
    private byte[] serializeEntity(VersionedEntity entity) {
      EntityProto.Builder stored = EntityProto.newBuilder();
      stored.mergeFrom(entity.entityProto());

      Property.Builder version = stored.addPropertyBuilder();
      version.setName(ENTITY_VERSION_RESERVED_PROPERTY);
      version.setMultiple(false);
      version.setValue(PropertyValue.newBuilder().setInt64Value(entity.version()));

      return stored.build().toByteArray();
    }

    /**
     * Deserializes a {@link VersionedEntity} from a byte array, as returned by {@link
     * #serializeEntity(VersionedEntity)}.
     */
    private VersionedEntity deserializeEntity(byte[] serialized) throws IOException {
      EntityProto.Builder entityProto = EntityProto.newBuilder();
      try {
        entityProto.mergeFrom(serialized, ExtensionRegistry.getEmptyRegistry());
      } catch (InvalidProtocolBufferException e) {
        throw new IOException("Corrupt or incomplete EntityProto");
      }

      long version = Profile.MINIMUM_VERSION;
      List<Property> properties = new ArrayList<>(entityProto.getPropertyList());
      for (Iterator<Property> iter = properties.iterator(); iter.hasNext(); ) {
        Property property = iter.next();
        if (property.getName().equals(ENTITY_VERSION_RESERVED_PROPERTY)) {
          version = property.getValue().getInt64Value();
          iter.remove();
          break;
        }
      }
      entityProto.clearProperty();
      entityProto.addAllProperty(properties);

      return VersionedEntity.create(entityProto.build(), version);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      // We must call putFields() and writeFields() to write the Extent Object header.
      // This permits us to later call readFields() to try reading the legacy format.
      out.putFields();
      out.writeFields();
      out.writeLong(CURRENT_STORAGE_VERSION);
      out.writeInt(entities.size());
      for (VersionedEntity entity : getAllEntities()) {
        out.writeObject(serializeEntity(entity));
      }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      ObjectInputStream.GetField fields = in.readFields();
      if (fields.get("entities", null) != null) {
        // Legacy storage format.
        @SuppressWarnings("unchecked")
        LinkedHashMap<Reference, EntityProto> legacy =
            (LinkedHashMap<Reference, EntityProto>) fields.get("entities", null);
        entities = legacy;
        versions = new HashMap<>();
        for (Reference key : entities.keySet()) {
          versions.put(key, Profile.MINIMUM_VERSION);
        }
      } else {
        entities = new LinkedHashMap<>();
        versions = new HashMap<>();
        long version = in.readLong();
        if (version == CURRENT_STORAGE_VERSION) {
          int entityCount = in.readInt();
          for (int i = 0; i < entityCount; ++i) {
            VersionedEntity entity = deserializeEntity((byte[]) in.readObject());
            Reference key = entity.entityProto().getKey();
            entities.put(key, entity.entityProto());
            versions.put(key, entity.version());
          }
        } else {
          throw new IOException(String.format("Unsupported storage format [%d]", version));
        }
      }
    }
  }

  /** A {@link LocalDatastoreJob} that puts and deletes a set of entities. */
  class WriteJob extends LocalDatastoreJob {
    private final Profile profile;
    // TODO: remove this field and rely instead on the list of unappliedJobs in EntityGroup.
    @Nullable private LocalDatastoreJob previousJob;
    private final Map<Reference, EntityProto> puts;
    private final Set<Reference> deletes;

    WriteJob(
        HighRepJobPolicy jobPolicy,
        Profile.EntityGroup entityGroup,
        Profile profile,
        Iterable<EntityProto> puts,
        Iterable<Reference> deletes) {
      this(
          jobPolicy,
          entityGroup,
          profile,
          checkNotNull(profile).incrementAndGetCommitTimestamp(),
          puts,
          deletes);
    }

    WriteJob(
        HighRepJobPolicy jobPolicy,
        Profile.EntityGroup entityGroup,
        Profile profile,
        long commitTimestamp,
        Iterable<EntityProto> puts,
        Iterable<Reference> deletes) {
      super(jobPolicy, entityGroup.pathAsKey(), commitTimestamp);
      this.profile = checkNotNull(profile);
      this.previousJob = entityGroup.getLastJob();
      this.deletes = ImmutableSet.copyOf(deletes);
      // The list of EntityProto to write might contain duplicate. In that case, the last one
      // written wins.
      Map<Reference, EntityProto> dedupePuts = new HashMap<>();
      for (EntityProto put : puts) {
        dedupePuts.put(put.getKey(), put);
      }
      this.puts = ImmutableMap.copyOf(dedupePuts);
    }

    @Override
    EntityProto getEntity(Reference key) {
      if (deletes.contains(key)) {
        return null;
      } else if (puts.containsKey(key)) {
        return puts.get(key);
      } else {
        return getSnapshotEntity(key);
      }
    }

    @Override
    EntityProto getSnapshotEntity(Reference key) {
      if (previousJob != null) {
        return previousJob.getEntity(key);
      } else {
        Extent extent = profile.getExtents().get(getKind(key));
        return extent == null ? null : extent.getEntityProtoByKey(key);
      }
    }

    @Override
    Cost calculateJobCost() {
      Cost.Builder totalCost = Cost.newBuilder();
      // Deletes
      for (Reference key : deletes) {
        EntityProto oldEntity = getSnapshotEntity(key);
        if (oldEntity != null) {
          addTo(totalCost, costAnalysis.getWriteCost(oldEntity));
        }
      }
      // Puts
      for (EntityProto entity : puts.values()) {
        EntityProto oldEntity = getSnapshotEntity(entity.getKey());
        addTo(totalCost, costAnalysis.getWriteOps(oldEntity, entity));
      }
      return totalCost.build();
    }

    @Override
    void applyInternal() {
      // Just before applying this job, the profile is all caught up so we can remove the back link
      // to the previous job. Keeping the link would lead to OOM in case we have a policy that
      // always leaves a job unapplied per entity group.
      previousJob = null;
      for (Reference key : deletes) {
        Extent extent = profile.getExtents().get(getKind(key));
        if (extent != null) {
          extent.removeEntity(key);
        }
      }
      for (Map.Entry<Reference, EntityProto> entry : puts.entrySet()) {
        if (!isNoOpWrite(entry.getKey())) {
          Extent extent = getOrCreateExtent(profile, getKind(entry.getKey()));
          extent.putEntity(VersionedEntity.create(entry.getValue(), timestamp));
        }
      }
      dirty = true;
    }

    @Override
    public long getMutationTimestamp(Reference key) {
      if (isNoOpWrite(key)) {
        if (previousJob != null) {
          return previousJob.getMutationTimestamp(key);
        } else {
          Extent extent = profile.getExtents().get(getKind(key));
          if (extent != null) {
            VersionedEntity entity = extent.getEntityByKey(key);
            if (entity != null) {
              return entity.version();
            }
          }
          return profile.getReadTimestamp();
        }
      } else {
        return timestamp;
      }
    }

    /**
     * Returns true if this job does not modify the state of the entity with the given {@link
     * Reference key}.
     */
    public boolean isNoOpWrite(Reference key) {
      if (deletes.contains(key)) {
        return getSnapshotEntity(key) == null;
      } else if (puts.containsKey(key)) {
        return equalProperties(getSnapshotEntity(key), puts.get(key));
      } else {
        return true;
      }
    }
  }

  static class HasCreationTime {
    private final long creationTime;

    HasCreationTime(long creationTime) {
      this.creationTime = creationTime;
    }

    long getCreationTime() {
      return creationTime;
    }
  }

  /** An outstanding query. */
  class LiveQuery extends HasCreationTime {
    class DecompiledCursor {
      final EntityProto cursorEntity;
      final boolean inclusive;
      final boolean isStart;

      public DecompiledCursor(CompiledCursor compiledCursor, boolean isStart) {
        // The cursor is unset.
        if (compiledCursor == null) {
          cursorEntity = null;
          inclusive = false;
          this.isStart = isStart;
          return;
        }

        IndexPostfix position = compiledCursor.getPostfixPosition();
        // The cursor has been set but without any position data. Treating as the default start
        // cursor places it before all entities.
        if (!(position.hasKey() || position.getIndexValueCount() > 0)) {
          cursorEntity = null;
          inclusive = false;
          this.isStart = true;
          return;
        }
        cursorEntity = decompilePosition(position);
        inclusive = position.getBefore();
        this.isStart = isStart;
      }

      public int getPosition(EntityProtoComparator entityComparator) {
        if (cursorEntity == null) {
          return isStart ? 0 : Integer.MAX_VALUE;
        }

        int loc = Collections.binarySearch(entities, cursorEntity, entityComparator);
        if (loc < 0) { // savedEntity doesn't exist
          return -(loc + 1); // insertion_point
        } else { // savedEntity exists
          return inclusive ? loc : loc + 1;
        }
      }

      public EntityProto getCursorEntity() {
        return cursorEntity;
      }
    }

    private final Set<String> orderProperties;
    private final Set<String> projectedProperties;
    private final Set<String> groupByProperties;
    private final Query.Builder query;

    private final List<EntityProto> entities;
    private final Map<Reference, Long> versions;
    private EntityProto lastResult = null;
    private int remainingOffset = 0;

    public LiveQuery(
        List<EntityProto> entities,
        @Nullable Map<Reference, Long> versions,
        Query.Builder query,
        EntityProtoComparator entityComparator,
        Clock clock) {
      super(clock.getCurrentTime());
      if (entities == null) {
        throw new NullPointerException("entities cannot be null");
      }

      this.query = query;
      this.remainingOffset = query.getOffset();

      orderProperties = new HashSet<>();
      for (Query.Order order : entityComparator.getAdjustedOrders()) {
        if (!Entity.KEY_RESERVED_PROPERTY.equals(order.getProperty())) {
          orderProperties.add(order.getProperty());
        }
      }
      groupByProperties = Sets.newHashSet(query.getGroupByPropertyNameList());
      projectedProperties = Sets.newHashSet(query.getPropertyNameList());

      this.entities = Lists.newArrayList(entities);

      ImmutableMap.Builder<Reference, Long> versionsBuilder = ImmutableMap.builder();
      if (this.projectedProperties.isEmpty() && !this.query.getKeysOnly() && versions != null) {
        for (EntityProto entity : this.entities) {
          Reference key = entity.getKey();
          checkArgument(versions.containsKey(key));
          versionsBuilder.put(key, versions.get(key));
        }
      }
      this.versions = versionsBuilder.buildOrThrow();

      // Apply cursors
      DecompiledCursor startCursor =
          new DecompiledCursor(query.hasCompiledCursor() ? query.getCompiledCursor() : null, true);
      DecompiledCursor endCursor =
          new DecompiledCursor(
              query.hasEndCompiledCursor() ? query.getEndCompiledCursor() : null, false);

      lastResult = startCursor.getCursorEntity();

      int endCursorPos = Math.min(endCursor.getPosition(entityComparator), this.entities.size());
      int startCursorPos = Math.min(endCursorPos, startCursor.getPosition(entityComparator));

      if (endCursorPos < this.entities.size()) {
        this.entities.subList(endCursorPos, this.entities.size()).clear();
      }
      this.entities.subList(0, startCursorPos).clear();

      // Apply limit.
      if (query.hasLimit()) {
        int toIndex =
            (int) Math.min((long) query.getLimit() + query.getOffset(), Integer.MAX_VALUE);
        if (toIndex < this.entities.size()) {
          this.entities.subList(toIndex, this.entities.size()).clear();
        }
      }
    }

    private int offsetResults(int offset) {
      int realOffset = Math.min(Math.min(offset, entities.size()), MAX_QUERY_RESULTS);
      if (realOffset > 0) {
        lastResult = entities.get(realOffset - 1);
        entities.subList(0, realOffset).clear();
        remainingOffset -= realOffset;
      }
      return realOffset;
    }

    public QueryResult.Builder nextResult(Integer offset, Integer count, boolean compile) {
      QueryResult.Builder result = QueryResult.newBuilder().setMoreResults(false);

      if (count == null) {
        if (query.hasCount()) {
          count = query.getCount();
        } else {
          count = DEFAULT_BATCH_SIZE;
        }
      }

      if (offset != null && offset != remainingOffset) {
        throw newError(ErrorCode.BAD_REQUEST, "offset mismatch");
      }
      offset = remainingOffset;

      if (offset > 0) {
        result.setSkippedResults(offsetResults(offset));
        if (compile) {
          result
              .getSkippedResultsCompiledCursorBuilder()
              .setPostfixPosition(compilePosition(lastResult));
        }
      }

      if (offset == result.getSkippedResults()) {
        // Offset has been satisfied so return real results
        List<EntityProto> entities = removeEntities(Math.min(MAX_QUERY_RESULTS, count));
        for (EntityProto entity : entities) {
          result.addResult(postProcessEntityForQuery(entity).buildPartial());
          if (!versions.isEmpty()) {
            result.addVersion(versions.get(entity.getKey()));
          }
          if (compile) {
            result.addResultCompiledCursorBuilder().setPostfixPosition(compilePosition(entity));
          }
        }
      }
      result.setMoreResults(!entities.isEmpty());
      result.setKeysOnly(query.getKeysOnly());
      result.setIndexOnly(query.getPropertyNameCount() > 0);
      if (compile) {
        result.getCompiledCursorBuilder().setPostfixPosition(compilePosition(lastResult));
      }
      return result;
    }

    /** Removes and returns the given number of entities from the result set. */
    private List<EntityProto> removeEntities(int count) {
      List<EntityProto> subList = entities.subList(0, Math.min(count, entities.size()));

      if (!subList.isEmpty()) {
        lastResult = subList.get(subList.size() - 1);
      }

      List<EntityProto> results = new ArrayList<>(subList);
      subList.clear();

      return results;
    }

    /** Converts an entity to the format requested by the user. */
    private EntityProto.Builder postProcessEntityForQuery(EntityProto entity) {
      EntityProto.Builder result;
      if (!projectedProperties.isEmpty()) {
        result = EntityProto.newBuilder();
        result.getKeyBuilder().mergeFrom(entity.getKey());
        result.setEntityGroup(Path.getDefaultInstance());
        Set<String> seenProps = Sets.newHashSetWithExpectedSize(query.getPropertyNameCount());
        for (Property prop : entity.getPropertyList()) {
          if (projectedProperties.contains(prop.getName())) {
            // Dev stubs should have already removed multi-valued properties.
            if (!seenProps.add(prop.getName())) {
              throw newError(
                  ErrorCode.INTERNAL_ERROR, "LocalDatastoreServer produced invalid results.");
            }
            result
                .addPropertyBuilder()
                .setName(prop.getName())
                .setMeaning(Property.Meaning.INDEX_VALUE)
                .setMultiple(false)
                .getValueBuilder()
                .mergeFrom(prop.getValue());
          }
        }
      } else if (query.getKeysOnly()) {
        result = EntityProto.newBuilder();
        result.getKeyBuilder().mergeFrom(entity.getKey());
        result.setEntityGroup(Path.getDefaultInstance());
      } else {
        result = entity.toBuilder().clone();
      }
      postprocessEntity(result);
      return result;
    }

    EntityProto decompilePosition(IndexPostfix position) {
      EntityProto.Builder result = EntityProto.newBuilder();
      if (position.hasKey()) {
        if (query.hasKind()) {
          String queryKind = query.getKind();
          String cursorKind = getLast(position.getKey().getPath().getElementList()).getType();
          if (!queryKind.equals(cursorKind)) {
            // This is not technically a problem, but we try to throw exceptions in as many
            // 'unsupported' use cases as possible
            throw newError(
                ErrorCode.BAD_REQUEST,
                String.format(
                    "The query kind is %s but cursor.postfix_position.key kind is %s.",
                    queryKind, cursorKind));
          }
        }
        result.setKey(position.getKey());
      }

      Set<String> cursorProperties =
          groupByProperties.isEmpty() ? orderProperties : groupByProperties;
      Set<String> remainingProperties = new HashSet<>(cursorProperties);
      for (IndexPostfix.IndexValue prop : position.getIndexValueList()) {
        if (!cursorProperties.contains(prop.getPropertyName())) {
          // This is not technically a problem, but the datastore will likely
          // an throw exception in this case.
          throw newError(ErrorCode.BAD_REQUEST, "cursor does not match query");
        }
        remainingProperties.remove(prop.getPropertyName());
        Property.Builder propBuilder =
            result
                .addPropertyBuilder()
                .setName(prop.getPropertyName())
                .setValue(prop.getValue())
                .setMultiple(false);
        if (query.getPropertyNameCount() > 0 || query.getGroupByPropertyNameCount() > 0) {
          propBuilder.setMeaning(Property.Meaning.INDEX_VALUE);
        }
      }

      if (!remainingProperties.isEmpty()) {
        throw newError(ErrorCode.BAD_REQUEST, "cursor does not match query");
      }
      return result.buildPartial(); // No key.
    }

    private IndexPostfix compilePosition(EntityProto entity) {
      /* TODO: This is not actually how compiled cursors behave in
       * the real datastore. We are storing all values of relevant properties while
       * the datastore would normally only store the index key (which contains
       * the exact property values that caused in entity to appear in the result
       * set). We can do this because result set does not contain duplicates.
       * However if Query.distinct=false was supported this would not work.
       */
      IndexPostfix.Builder position = IndexPostfix.newBuilder();

      if (entity != null) {
        // The cursor properties will be the group by properties, or the order properties if no
        // group by properties exist.
        Set<String> cursorProperties;
        if (groupByProperties.isEmpty()) {
          cursorProperties = Sets.newHashSet(orderProperties);
          // We always want to add the key when we are not doing group by queries.
          cursorProperties.add(Entity.KEY_RESERVED_PROPERTY);
          position.setKey(entity.getKey());
        } else {
          cursorProperties = groupByProperties;
        }

        for (Property prop : entity.getPropertyList()) {
          if (cursorProperties.contains(prop.getName())) {
            position
                .addIndexValueBuilder()
                .setPropertyName(prop.getName())
                .setValue(prop.getValue());
          }
        }
        // This entity has already been returned so exclude it.
        position.setBefore(false);
        CursorModernizer.setBeforeAscending(position, CursorModernizer.firstSortDirection(query));
      }
      return position.build();
    }

    public CompiledQuery compileQuery() {
      CompiledQuery.Builder result = CompiledQuery.newBuilder();
      result.setKeysOnly(query.getKeysOnly());
      PrimaryScan.Builder scan = result.getPrimaryScanBuilder();

      // saving the entire original query as the index
      scan.setIndexNameBytes(query.build().toByteString());

      return result.build();
    }
  }

  /** An outstanding txn. All methods that operate on mutable members must be synchronized. */
  static class LiveTxn extends HasCreationTime {

    /** Defines the concurrency mechanis, used by this transaction. */
    enum ConcurrencyMode {
      /**
       * The transaction obtains exclusive locks only at commit time, but still guarantees the
       * validity of the snapshot at every read.
       */
      OPTIMISTIC,
      /** The transaction obtains exclusive locks on the first read or write to an entity. */
      PESSIMISTIC,
      /**
       * The transaction obtains shared locks on reads and upgrade them to exclusive locks at commit
       * time.
       */
      SHARED_READ,
      /**
       * The transaction can only do reads and do not cause contention with any other transaction.
       */
      READ_ONLY;
    }

    private final Map<Profile.EntityGroup, EntityGroupTracker> entityGroups = new HashMap<>();
    private final List<TaskQueueAddRequest> actions = new ArrayList<>();
    private final boolean allowMultipleEg;
    private boolean failed = false;
    private final ConcurrencyMode concurrencyMode;
    // The original transaction mode set by the request.
    private final TransactionMode originalTransactionMode;

    LiveTxn(Clock clock, boolean allowMultipleEg, TransactionMode transactionMode) {
      this(clock, allowMultipleEg, transactionMode, false);
    }

    LiveTxn(Clock clock, boolean allowMultipleEg, TransactionMode transactionMode, boolean failed) {
      super(clock.getCurrentTime());
      this.originalTransactionMode = transactionMode;
      ConcurrencyMode concurrencyMode = toConcurrencyMode(transactionMode);
      // TODO: maybe support those extra modes.
      checkArgument(
          concurrencyMode != ConcurrencyMode.PESSIMISTIC
              && concurrencyMode != ConcurrencyMode.SHARED_READ);
      this.allowMultipleEg = allowMultipleEg;
      this.concurrencyMode = concurrencyMode;
      this.failed = failed;
    }

    /** Sets the entity group in a threadsafe way. */
    synchronized EntityGroupTracker trackEntityGroup(Profile.EntityGroup newEntityGroup) {
      if (newEntityGroup == null) {
        throw new NullPointerException("entityGroup cannot be null");
      }
      checkFailed();
      EntityGroupTracker tracker = entityGroups.get(newEntityGroup);
      if (tracker == null) {
        if (allowMultipleEg) {
          if (entityGroups.size() >= MAX_EG_PER_TXN) {
            throw newError(ErrorCode.BAD_REQUEST, TOO_MANY_ENTITY_GROUP_MESSAGE);
          }
        } else {
          if (entityGroups.size() >= 1) {
            Profile.EntityGroup entityGroup = entityGroups.keySet().iterator().next();
            throw newError(
                ErrorCode.BAD_REQUEST,
                ENTITY_GROUP_MESSAGE + "found both " + entityGroup + " and " + newEntityGroup);
          }
        }

        /* NOTE: if we start delaying snapshotting until the first read as
         * in the real datastore, this check must move to the snapshotting
         * logic. */
        /* Check if the other entity groups are still unchanged, i.e. that we
         * have a consistent snapshot (safe to do before creating the new
         * tracker as we have a lock on the profile). */
        for (EntityGroupTracker other : getAllTrackers()) {
          try {
            other.checkEntityGroupVersion();
          } catch (ApplicationException e) {
            /* Fail all future requests except rollback */
            failed = true;
            throw e;
          }
        }

        tracker = new EntityGroupTracker(newEntityGroup, isReadOnly());
        entityGroups.put(newEntityGroup, tracker);
      }
      return tracker;
    }

    synchronized Collection<EntityGroupTracker> getAllTrackers() {
      return entityGroups.values();
    }

    synchronized void addActions(Collection<TaskQueueAddRequest> newActions) {
      checkFailed();
      if (actions.size() + newActions.size() > MAX_ACTIONS_PER_TXN) {
        throw newError(
            ErrorCode.BAD_REQUEST, "Too many messages, maximum allowed: " + MAX_ACTIONS_PER_TXN);
      }
      actions.addAll(newActions);
    }

    synchronized Collection<TaskQueueAddRequest> getActions() {
      return new ArrayList<>(actions);
    }

    synchronized boolean isDirty() {
      checkFailed();
      for (EntityGroupTracker tracker : getAllTrackers()) {
        if (tracker.isDirty()) {
          return true;
        }
      }
      return false;
    }

    synchronized void close() {
      // Calling close is optional. Eventually the transaction will
      // timeout and get GC'd since EntityGroup uses a WeakHashMap.
      // Closing the transaction does prevent us from making an extra,
      // useless snapshot. In particular, the transaction should
      // be closed during commit before modifying any entities,
      // to prevent an extra snapshot during each commit.
      for (EntityGroupTracker tracker : getAllTrackers()) {
        tracker.getEntityGroup().removeTransaction(this);
      }
    }

    synchronized boolean isReadOnly() {
      return concurrencyMode == ConcurrencyMode.READ_ONLY;
    }

    private void checkFailed() {
      if (failed) {
        throw newError(ErrorCode.BAD_REQUEST, TRANSACTION_CLOSED);
      }
    }
  }

  static class EntityGroupTracker {
    private final Profile.EntityGroup entityGroup;
    private final Long entityGroupVersion;
    private final boolean readOnly;

    // Keys of entities that we've written or deleted during the txn to this entity group.
    // We use these to delay mutations until this txn is applied on this entity group.
    private final Map<Reference, EntityProto> written = new HashMap<>();
    private final Set<Reference> deleted = new HashSet<>();

    EntityGroupTracker(Profile.EntityGroup entityGroup, boolean readOnly) {
      this.entityGroup = entityGroup;
      this.entityGroupVersion = entityGroup.getVersion();
      this.readOnly = readOnly;
    }

    synchronized Profile.EntityGroup getEntityGroup() {
      return entityGroup;
    }

    synchronized void checkEntityGroupVersion() {
      if (!entityGroupVersion.equals(entityGroup.getVersion())) {
        throw newError(ErrorCode.CONCURRENT_TRANSACTION, CONTENTION_MESSAGE);
      }
    }

    synchronized Long getEntityGroupVersion() {
      return entityGroupVersion;
    }

    /** Records that this entity was written in this transaction. */
    synchronized void addWrittenEntity(EntityProto entity) {
      checkState(!readOnly);
      Reference key = entity.getKey();
      written.put(key, entity);

      // If the entity was deleted earlier in the transaction, this overrides
      // that delete.
      deleted.remove(key);
    }

    /** Records that this entity was deleted in this transaction. */
    synchronized void addDeletedEntity(Reference key) {
      checkState(!readOnly);
      deleted.add(key);

      // If the entity was written earlier in the transaction, this overrides
      // that delete.
      written.remove(key);
    }

    synchronized Collection<EntityProto> getWrittenEntities() {
      return new ArrayList<>(written.values());
    }

    synchronized Collection<Reference> getDeletedKeys() {
      return new ArrayList<>(deleted);
    }

    synchronized boolean isDirty() {
      return written.size() + deleted.size() > 0;
    }
  }

  /**
   * Broken out to support testing.
   *
   * @return Number of pruned objects.
   */
  static int pruneHasCreationTimeMap(
      long now, int maxLifetimeMs, Map<Long, ? extends HasCreationTime> hasCreationTimeMap) {
    // Entries with values that were created before the deadline are removed.
    long deadline = now - maxLifetimeMs;
    int numPrunedObjects = 0;
    for (Iterator<? extends Map.Entry<Long, ? extends HasCreationTime>> queryIt =
            hasCreationTimeMap.entrySet().iterator();
        queryIt.hasNext(); ) {
      Map.Entry<Long, ? extends HasCreationTime> entry = queryIt.next();
      HasCreationTime query = entry.getValue();
      if (query.getCreationTime() < deadline) {
        queryIt.remove();
        numPrunedObjects++;
      }
    }
    return numPrunedObjects;
  }

  // NB: This is mostly copied from //j/c/g/ah/datastore/SpecialProperty.
  // However, that has Megastore dependencies, and thanks to the wonder of enums in Java,
  // if there's a way to separate it out nicely, it's beyond me.
  /**
   * SpecialProperty encodes the information needed to know when and how to generate special
   * properties. Special properties are entity properties that are created dynamically at store or
   * load time).
   */
  static enum SpecialProperty {
    SCATTER(false, true, Meaning.BYTESTRING) {
      /** Number of bytes of the hash to save */
      private static final int SMALL_LENGTH = 2;

      // <internal25>
      @SuppressWarnings("UnsafeFinalization")
      @Override
      PropertyValue getValue(EntityProtoOrBuilder entity) {
        int hashCode = 0;
        for (Element elem : entity.getKey().getPath().getElementList()) {
          if (elem.hasId()) {
            // Convert to string and take the hash of that in order to get a
            // nice distribution in the upper bits.
            hashCode = (int) (hashCode ^ elem.getId());
          } else if (elem.hasName()) {
            hashCode ^= elem.getName().hashCode();
          } else {
            throw new IllegalStateException(
                "Couldn't find name or id for entity " + entity.getKey());
          }
        }
        // We're just using MD5 to get a good distribution of bits.
        try {
          byte[] digest =
              MessageDigest.getInstance("MD5")
                  .digest(("" + hashCode).getBytes(StandardCharsets.UTF_8));
          // The Charset doesn't much matter here since decimal digits and minus are
          // the same in all common ones.
          byte[] miniDigest = new byte[SMALL_LENGTH];
          System.arraycopy(digest, 0, miniDigest, 0, SMALL_LENGTH);

          if ((miniDigest[0] & 0x01) != 0) {
            return PropertyValue.newBuilder()
                .setStringValue(ByteString.copyFrom(miniDigest))
                .build();
          }
        } catch (NoSuchAlgorithmException ex) {
          Logger logger = Logger.getLogger(SpecialProperty.class.getName());
          logger.log(
              Level.WARNING,
              "Your JDK doesn't have an MD5 implementation, which is required for scatter "
                  + " property support.");
        }

        return null;
      }
    };

    /** The reserved name of the special property. */
    private final String name;

    /** Whether or not the property is populated in the EntityProto that is sent to the user. */
    private final boolean isVisible;

    /**
     * Whether or not the special property's value is persisted in the datastore and indexed in
     * native indices like normal values.
     */
    private final boolean isStored;

    private final Meaning meaning;

    /**
     * These two properties apply to the transition of the entity between the storage layer and the
     * user layer. We always overwrite the property if we should add and it already exists.
     * Stripping a property silently succeeds if the property doesn't already exist.
     *
     * <p>If isVisible == true, then we add the property when it transitions from storage to user.
     * If isVisible == false, then we strip the property when it transitions from storage to user.
     * If isStored == true, then we add the property when it transitions from user to storage. If
     * isStored == false, then we strip the property when it transitions from user to storage.
     */
    private SpecialProperty(boolean isVisible, boolean isStored, Meaning meaning) {
      this.name = "__" + name().toLowerCase(Locale.ROOT) + "__";
      this.isVisible = isVisible;
      this.isStored = isStored;
      this.meaning = meaning;
    }

    /** Returns the reserved name of the special property. */
    public final String getName() {
      return name;
    }

    /** Returns true iff this property is populated in the EntityProto that is sent to the user. */
    public final boolean isVisible() {
      return isVisible;
    }

    /**
     * Returns true iff this property is populated in the entity that is persisted to the datastore.
     * This means that there is a built-in index for the property.
     */
    final boolean isStored() {
      return isStored;
    }

    /**
     * Returns this property's value. Must be overridden for any property whose {@link #isStored}
     * method returns true.
     *
     * @param entity the entity for which the value is being obtained
     */
    PropertyValue getValue(EntityProtoOrBuilder entity) {
      throw new UnsupportedOperationException();
    }

    /** Returns a property with the given value for this SpecialProperty. */
    Property getProperty(PropertyValue value) {
      return Property.newBuilder()
          .setName(getName())
          .setValue(value)
          .setMultiple(false)
          .setMeaning(meaning)
          .build();
    }
  }

  Map<String, SpecialProperty> getSpecialPropertyMap() {
    return Collections.unmodifiableMap(specialPropertyMap);
  }

  private void persist() {
    globalLock.writeLock().lock();
    try {
      if (noStorage || !dirty) {
        return;
      }

      long start = clock.getCurrentTime();
      try (ObjectOutputStream objectOut =
          new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(backingStore)))) {
        objectOut.writeLong(-CURRENT_STORAGE_VERSION);
        objectOut.writeLong(entityIdSequential.get());
        objectOut.writeLong(entityIdScattered.get());
        objectOut.writeObject(profiles);
      }

      dirty = false;
      long end = clock.getCurrentTime();

      logger.log(Level.INFO, "Time to persist datastore: " + (end - start) + " ms");

    } catch (Exception e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        logger.log(Level.SEVERE, "Unable to save the datastore", e);
      } else {
        throw new RuntimeException(e);
      }
      throw new RuntimeException(t);
    } finally {
      globalLock.writeLock().unlock();
    }
  }

  /**
   * Triggers the stale query sweeper with a simulated delay sufficient to expire all active
   * queries.
   *
   * @return The number of queries removed.
   */
  int expireOutstandingQueries() {
    return removeStaleQueries(maxQueryLifetimeMs * 2L + clock.getCurrentTime());
  }

  /**
   * Removes any stale queries at the given time.
   *
   * @param currentTime the current time to use when calculating if a query is stale
   * @return the number of queries removed
   */
  private int removeStaleQueries(long currentTime) {
    int totalPruned = 0;
    synchronized (profiles) {
      for (Profile profile : profiles.values()) {
        synchronized (profile.getQueries()) {
          totalPruned +=
              pruneHasCreationTimeMap(currentTime, maxQueryLifetimeMs, profile.getQueries());
        }
      }
    }
    return totalPruned;
  }

  /**
   * Triggers the stale transaction sweeper with a simulated delay sufficient to expire all active
   * transactions.
   *
   * @return The number of transactions removed.
   */
  int expireOutstandingTransactions() {
    return removeStaleTransactions(maxTransactionLifetimeMs * 2L + clock.getCurrentTime());
  }

  /**
   * Removes any stale transactions at the given time.
   *
   * @param currentTime the current time to use when calculating if a transaction is stale
   * @return the number of transactions removed
   */
  private int removeStaleTransactions(long currentTime) {
    int totalPruned = 0;
    for (Profile profile : profiles.values()) {
      synchronized (profile.getTxns()) {
        totalPruned +=
            pruneHasCreationTimeMap(currentTime, maxTransactionLifetimeMs, profile.getTxns());
      }
    }
    return totalPruned;
  }

  /**
   * Cleans up any actively running services.
   *
   * <p>This should only be called when the JVM is exiting.
   */
  // @VisibleForTesting
  static int cleanupActiveServices() {
    int cleanedUpServices = 0;
    logger.info("scheduler shutting down.");
    for (LocalDatastoreService service : activeServices) {
      cleanedUpServices++;
      service.stop();
    }
    scheduler.shutdownNow();
    logger.info("scheduler finished shutting down.");
    return cleanedUpServices;
  }

  /** Returns the count of all actively running {@link LocalDatastoreService} instances. */
  static int getActiveServiceCount() {
    return activeServices.size();
  }

  public Double getDefaultDeadline(boolean isOfflineRequest) {
    return DEFAULT_DEADLINE_SECONDS;
  }

  public Double getMaximumDeadline(boolean isOfflineRequest) {
    return MAX_DEADLINE_SECONDS;
  }

  /** Returns true if the two given {@link EntityProto entities} have the same property values. */
  static boolean equalProperties(@Nullable EntityProto entity1, EntityProto entity2) {
    return entity1 != null
        && entity1.getPropertyList().equals(entity2.getPropertyList())
        && entity1.getRawPropertyList().equals(entity2.getRawPropertyList());
  }

  /** Adds {@code addMe} to {@code target}. */
  private static void addTo(Cost.Builder target, Cost addMe) {
    target.setEntityWrites(target.getEntityWrites() + addMe.getEntityWrites());
    target.setIndexWrites(target.getIndexWrites() + addMe.getIndexWrites());
  }

  /** Returns the transaction {@link ConcurrencyMode} for the given V3 transaction mode. */
  private static ConcurrencyMode toConcurrencyMode(TransactionMode transactionMode) {
    switch (transactionMode) {
      case UNKNOWN:
      // TODO: map to SHARED_READ in spanner mode.
      case READ_WRITE:
        // TODO: map to SHARED_READ in spanner mode.
        // TODO: map to PESSIMISTIC in megastore mode.
        return ConcurrencyMode.OPTIMISTIC;
      case READ_ONLY:
        return ConcurrencyMode.READ_ONLY;
      default:
        throw new IllegalArgumentException("Unknown transaction mode: " + transactionMode);
    }
  }
}
