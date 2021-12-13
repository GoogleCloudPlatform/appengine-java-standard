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

import static com.google.appengine.api.datastore.FutureHelper.quietGet;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType;
import com.google.appengine.api.datastore.TransactionOptions.Mode;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * State and behavior that is common to all asynchronous Datastore API implementations.
 *
 */
abstract class BaseAsyncDatastoreServiceImpl
    implements AsyncDatastoreServiceInternal, CurrentTransactionProvider {
  /**
   * It doesn't actually matter what this value is, the back end will set its own deadline. All that
   * matters is that we set a value.
   */
  static final long ARBITRARY_FAILOVER_READ_MS = -1;

  /** User-provided config options. */
  final DatastoreServiceConfig datastoreServiceConfig;

  /** Knows which transaction to use when the user does not explicitly provide one. */
  final TransactionStack defaultTxnProvider;

  final Logger logger = Logger.getLogger(getClass().getName());

  // write-once cached field.
  private DatastoreType datastoreType;

  private final QueryRunner queryRunner;

  /**
   * A base batcher for operations executed in the context of a {@link DatastoreService}.
   *
   * @param <S> the response message type
   * @param <R> the request message type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  abstract class BaseRpcBatcher<
          S extends Message, R extends MessageLiteOrBuilder, F, T extends MessageLite>
      extends Batcher<R, F, T> {
    abstract Future<S> makeCall(R batch);

    @Override
    final int getMaxSize() {
      return datastoreServiceConfig.maxRpcSizeBytes;
    }

    @Override
    final int getMaxGroups() {
      return datastoreServiceConfig.maxEntityGroupsPerRpc;
    }

    final List<Future<S>> makeCalls(Iterator<R> batches) {
      List<Future<S>> futures = new ArrayList<Future<S>>();
      while (batches.hasNext()) {
        futures.add(makeCall(batches.next()));
      }
      return futures;
    }
  }

  BaseAsyncDatastoreServiceImpl(
      DatastoreServiceConfig datastoreServiceConfig,
      TransactionStack defaultTxnProvider,
      QueryRunner queryRunner) {
    // TODO: Seems like this should be doing a defensive copy (especially since
    // AsyncDatastoreServiceImpl validated the current value which can technically change).
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.defaultTxnProvider = defaultTxnProvider;
    this.queryRunner = queryRunner;
  }

  protected abstract TransactionImpl.InternalTransaction doBeginTransaction(
      TransactionOptions options);

  protected abstract Future<Map<Key, Entity>> doBatchGet(
      @Nullable Transaction txn, final Set<Key> keysToGet, final Map<Key, Entity> resultMap);

  protected abstract Future<List<Key>> doBatchPut(
      @Nullable Transaction txn, final List<Entity> entities);

  protected abstract Future<Void> doBatchDelete(@Nullable Transaction txn, Collection<Key> keys);

  @SuppressWarnings("deprecation")
  static void validateQuery(Query query) {
    checkArgument(
        query.getFilterPredicates().isEmpty() || query.getFilter() == null,
        "A query cannot have both a filter and filter predicates set.");
    checkArgument(
        query.getProjections().isEmpty() || !query.isKeysOnly(),
        "A query cannot have both projections and keys-only set.");
  }

  /**
   * Return the current transaction if one already exists, otherwise create a new transaction or
   * throw an exception according to the {@link ImplicitTransactionManagementPolicy}.
   */
  GetOrCreateTransactionResult getOrCreateTransaction() {
    Transaction currentTxn = getCurrentTransaction(null); // return null if no current txn
    // if we've already got a transaction, use it
    if (currentTxn != null) {
      return new GetOrCreateTransactionResult(false, currentTxn);
    }

    // Establish a transaction according to the policy specified by the user.
    switch (datastoreServiceConfig.getImplicitTransactionManagementPolicy()) {
      case NONE:
        // no current txn so just execute without one
        return new GetOrCreateTransactionResult(false, null);
      case AUTO:
        return new GetOrCreateTransactionResult(
            true, createTransaction(TransactionOptions.Builder.withDefaults(), false));
      default:
        final String msg =
            "Unexpected Transaction Creation Policy: "
                + datastoreServiceConfig.getImplicitTransactionManagementPolicy();
        logger.severe(msg);
        throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public Transaction getCurrentTransaction() {
    return defaultTxnProvider.peek();
  }

  @Override
  public Transaction getCurrentTransaction(Transaction returnedIfNoTxn) {
    return defaultTxnProvider.peek(returnedIfNoTxn);
  }

  DatastoreServiceConfig getDatastoreServiceConfig() {
    return datastoreServiceConfig;
  }

  @Override
  public Future<Entity> get(Key key) {
    if (key == null) {
      throw new NullPointerException("key cannot be null");
    }
    return wrapSingleGet(key, get(Arrays.asList(key)));
  }

  @Override
  public Future<Entity> get(@Nullable Transaction txn, final Key key) {
    if (key == null) {
      throw new NullPointerException("key cannot be null");
    }
    return wrapSingleGet(key, get(txn, Arrays.asList(key)));
  }

  @Override
  public Future<Map<Key, Entity>> get(final Iterable<Key> keys) {
    return new TransactionRunner<Map<Key, Entity>>(getOrCreateTransaction()) {
      @Override
      protected Future<Map<Key, Entity>> runInternal(Transaction txn) {
        return get(txn, keys);
      }
    }.runReadInTransaction();
  }

  @Override
  public Future<Map<Key, Entity>> get(@Nullable Transaction txn, Iterable<Key> keys) {
    if (keys == null) {
      throw new NullPointerException("keys cannot be null");
    }

    // TODO: The handling of the Keys is pretty ugly.  We get an Iterable from the
    // user.  We need to copy this to a random access list (that we can mutate) for the PreGet
    // stuff. We will also convert it to a HashSet to support contains checks (for the RemoteApi
    // workaround).  There are also some O(N) calls to remove Keys from a List.
    List<Key> keyList = Lists.newArrayList(keys);

    // Allocate the Map that will receive the result of the RPC here so that PreGet callbacks can
    // add results.
    Map<Key, Entity> resultMap = new HashMap<Key, Entity>();
    PreGetContext preGetContext = new PreGetContext(this, keyList, resultMap);
    datastoreServiceConfig.getDatastoreCallbacks().executePreGetCallbacks(preGetContext);

    // Don't fetch anything from datastore that was provided by the preGet hooks.
    keyList.removeAll(resultMap.keySet());

    // Send the RPC(s).
    Future<Map<Key, Entity>> result = doBatchGet(txn, Sets.newLinkedHashSet(keyList), resultMap);

    // Invoke the user post-get callbacks.
    return new PostLoadFuture(result, datastoreServiceConfig.getDatastoreCallbacks(), this);
  }

  private Future<Entity> wrapSingleGet(final Key key, Future<Map<Key, Entity>> futureEntities) {
    return new FutureWrapper<Map<Key, Entity>, Entity>(futureEntities) {
      @Override
      protected Entity wrap(Map<Key, Entity> entities) throws Exception {
        Entity entity = entities.get(key);
        if (entity == null) {
          throw new EntityNotFoundException(key);
        }
        return entity;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Key> put(Entity entity) {
    return wrapSinglePut(put(Arrays.asList(entity)));
  }

  @Override
  public Future<Key> put(@Nullable Transaction txn, Entity entity) {
    return wrapSinglePut(put(txn, Arrays.asList(entity)));
  }

  @Override
  public Future<List<Key>> put(final Iterable<Entity> entities) {
    return new TransactionRunner<List<Key>>(getOrCreateTransaction()) {
      @Override
      protected Future<List<Key>> runInternal(Transaction txn) {
        return put(txn, entities);
      }
    }.runWriteInTransaction();
  }

  @Override
  public Future<List<Key>> put(@Nullable Transaction txn, Iterable<Entity> entities) {
    // Invoke the pre-put callbacks.
    List<Entity> entityList =
        entities instanceof List ? (List<Entity>) entities : Lists.newArrayList(entities);
    PutContext prePutContext = new PutContext(this, entityList);
    datastoreServiceConfig.getDatastoreCallbacks().executePrePutCallbacks(prePutContext);

    // Do the datastore put RPC on the remaining entities.
    Future<List<Key>> result = doBatchPut(txn, ImmutableList.copyOf(entities));

    if (txn == null) {
      // We're not in a txn so make sure we execute post-put callbacks when
      // the user asks for the result of the future.
      PutContext postPutContext = new PutContext(this, entityList);
      result =
          new PostPutFuture(result, datastoreServiceConfig.getDatastoreCallbacks(), postPutContext);
    } else {
      // We are in a txn so register the entities that have been written with
      // the txn so that we execute the appropriate post put callbacks when
      // (if) the txn commits.
      defaultTxnProvider.addPutEntities(txn, entityList);
    }
    return result;
  }

  private Future<Key> wrapSinglePut(Future<List<Key>> futureKeys) {
    return new FutureWrapper<List<Key>, Key>(futureKeys) {
      @Override
      protected Key wrap(List<Key> keys) throws Exception {
        return keys.get(0);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Void> delete(Key... keys) {
    return delete(Arrays.asList(keys));
  }

  @Override
  public Future<Void> delete(@Nullable Transaction txn, Key... keys) {
    return delete(txn, Arrays.asList(keys));
  }

  @Override
  public Future<Void> delete(final Iterable<Key> keys) {
    return new TransactionRunner<Void>(getOrCreateTransaction()) {
      @Override
      protected Future<Void> runInternal(Transaction txn) {
        return delete(txn, keys);
      }
    }.runWriteInTransaction();
  }

  @Override
  public Future<Void> delete(@Nullable Transaction txn, Iterable<Key> keys) {
    List<Key> allKeys = keys instanceof List ? (List<Key>) keys : ImmutableList.copyOf(keys);
    DeleteContext preDeleteContext = new DeleteContext(this, allKeys);
    datastoreServiceConfig.getDatastoreCallbacks().executePreDeleteCallbacks(preDeleteContext);
    // NOTE: We are reusing the user's list here, we can do this because
    // we do not hold on to this list after this function returns.
    Future<Void> result = doBatchDelete(txn, allKeys);

    if (txn == null) {
      // We're not in a txn so make sure we execute post delete callbacks when
      // the user asks for the result of the future.
      result =
          new PostDeleteFuture(
              result,
              datastoreServiceConfig.getDatastoreCallbacks(),
              new DeleteContext(this, allKeys));
    } else {
      // We are in a txn so register the entities that have been deleted with
      // the txn so that we execute the appropriate post delete callbacks when
      // (if) the txn commits.
      defaultTxnProvider.addDeletedKeys(txn, allKeys);
    }
    return result;
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return defaultTxnProvider.getAll();
  }

  /**
   * Register the provided future with the provided txn so that we know to perform a {@link
   * java.util.concurrent.Future#get()} before the txn is committed.
   *
   * @param txn The txn with which the future must be associated.
   * @param future The future to associate with the txn.
   * @param <T> The type of the Future
   * @return The same future that was passed in, for caller convenience.
   */
  protected final <T> Future<T> registerInTransaction(@Nullable Transaction txn, Future<T> future) {
    if (txn != null) {
      defaultTxnProvider.addFuture(txn, future);
      return new FutureHelper.TxnAwareFuture<T>(future, txn, defaultTxnProvider);
    }
    return future;
  }

  @Override
  public Future<Transaction> beginTransaction() {
    return beginTransaction(TransactionOptions.Builder.withDefaults());
  }

  @Override
  public Future<Transaction> beginTransaction(TransactionOptions options) {
    if (options.transactionMode() == Mode.READ_ONLY && options.previousTransaction() != null) {
      throw new IllegalArgumentException(
          "Cannot specify previous transaction for a read only transaction");
    }

    Transaction txn = createTransaction(options, true);

    // This transaction was created explicitly, so register the newly created transaction so that
    // it is available via getCurrentTransaction()
    defaultTxnProvider.push(txn);

    // Our transaction object is implicitly async so wrap it in a FakeFuture.
    return new FutureHelper.FakeFuture<Transaction>(txn);
  }

  private Transaction createTransaction(TransactionOptions options, boolean isExplicit) {
    return new TransactionImpl(
        datastoreServiceConfig.getAppIdNamespace().getAppId(),
        defaultTxnProvider,
        datastoreServiceConfig.getDatastoreCallbacks(),
        isExplicit,
        doBeginTransaction(options));
  }

  @Override
  public PreparedQuery prepare(Query query) {
    return prepare(null, query);
    // TODO The code that is commented out auto-enlists ancestor queries
    // in the current transaction if a current transaction exists.  We can't enable
    // this code right now because existing apps that execute ancestor queries
    // with a current transaction available risk getting exceptions if the entity
    // group with which the current transaction is associated does not match
    // the entity group of the ancestor.  We need to wait for a major version
    // upgrade to enable this.
    //    if (query.getAncestor() != null) {
    //      // Only ancestor queries can run inside transactions. If this is an
    //      // ancestor query, use the current txn.  Note that we do not consult
    //      // the ImplicitTransactionManagementPolicy to see if we should create
    //      // a txn if there is no current txn.  The reason is that if we were to
    //      // automatically start the txn, in order to be consistent we would also
    //      // need to automatically commit the txn, and since we need the txn
    //      // to stay open as long as the user is still consuming results, and
    //      // since we don't require that users 'close' the result set, we don't
    //      // get a callback that would allow us to perform the automatic commit.
    //      // This prevents us from supporting implicit transaction management
    //      // for queries.
    //      Transaction currentTxn = getCurrentTransaction(null); // return null if no current txn
    //      return prepare(currentTxn, query);
    //    } else {
    //      return prepare(null, query);
    //    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public PreparedQuery prepare(Transaction txn, Query query) {
    PreQueryContext context = new PreQueryContext(this, query);
    datastoreServiceConfig.getDatastoreCallbacks().executePreQueryCallbacks(context);
    // Make sure we get the query off the context in case the interceptor has
    // modified it.

    // Don't call getCurrentElement() - the callbacks have already run so this
    // points past the end of the list.
    query = context.getElements().get(0);
    validateQuery(query);
    if (isGeoQuery(query)) {
      // Geo-spatial queries don't need to be split; which is just as
      // well, because they actually can't even be represented in the
      // form produced by split.
      return new PreparedQueryImpl(query, txn, queryRunner);
    }
    List<MultiQueryBuilder> queriesToRun = QuerySplitHelper.splitQuery(query);
    // All Filters should be in queriesToRun
    query.setFilter(null);
    query.getFilterPredicates().clear();
    if (queriesToRun.size() == 1 && queriesToRun.get(0).isSingleton()) {
      query.getFilterPredicates().addAll(queriesToRun.get(0).getBaseFilters());
      return new PreparedQueryImpl(query, txn, queryRunner);
    }
    return new PreparedMultiQuery(query, queriesToRun, txn, queryRunner);
  }

  /** Determines whether the query has a geo-spatial filter. */
  private boolean isGeoQuery(Query query) {
    Query.Filter filter = query.getFilter();
    if (filter == null) {
      // Either the (deprecated) list-of-predicates form of filter
      // (which is incapable of representing a geo-query), or no
      // filter at all
      return false;
    }
    return isGeoFilter(filter);
  }

  /**
   * Walks the filter tree searching for a geo-spatial component.
   *
   * @return true, if we find an StContainsFilter, without thoroughly validating that the rest of
   *     the tree is compatible with the geo-filter.
   */
  private boolean isGeoFilter(Query.Filter filter) {
    if (filter instanceof Query.StContainsFilter) {
      return true;
    }
    if (filter instanceof Query.CompositeFilter) {
      for (Query.Filter f : ((Query.CompositeFilter) filter).getSubFilters()) {
        if (isGeoFilter(f)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Future<KeyRange> allocateIds(String kind, long num) {
    return allocateIds(null, kind, num);
  }

  protected DatastoreType getDatastoreType() {
    if (datastoreType == null) {
      // datastoreType is derived from appId and we do not expect that to
      // change for the same instance of DatastoreService.
      datastoreType = quietGet(getDatastoreAttributes()).getDatastoreType();
    }
    return datastoreType;
  }

  @Override
  public Future<DatastoreAttributes> getDatastoreAttributes() {
    DatastoreAttributes attributes = new DatastoreAttributes();
    return new FutureHelper.FakeFuture<DatastoreAttributes>(attributes);
  }
}
