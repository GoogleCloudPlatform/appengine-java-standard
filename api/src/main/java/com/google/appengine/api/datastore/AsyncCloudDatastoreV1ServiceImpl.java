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

import static com.google.appengine.api.datastore.ReadPolicy.Consistency.EVENTUAL;

import com.google.appengine.api.datastore.Batcher.ReorderingMultiFuture;
import com.google.appengine.api.datastore.DatastoreService.KeyRangeState;
import com.google.appengine.api.datastore.FutureHelper.MultiFuture;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.datastore.v1.AllocateIdsRequest;
import com.google.datastore.v1.AllocateIdsResponse;
import com.google.datastore.v1.BeginTransactionRequest;
import com.google.datastore.v1.BeginTransactionResponse;
import com.google.datastore.v1.CommitRequest;
import com.google.datastore.v1.CommitResponse;
import com.google.datastore.v1.EntityResult;
import com.google.datastore.v1.Key.PathElement;
import com.google.datastore.v1.Key.PathElement.IdTypeCase;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.Mutation;
import com.google.datastore.v1.MutationResult;
import com.google.datastore.v1.ReadOptions;
import com.google.datastore.v1.TransactionOptions.ReadOnly;
import com.google.datastore.v1.TransactionOptions.ReadWrite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;

/** An implementation of {@link AsyncDatastoreService} using the Cloud Datastore v1 API. */
class AsyncCloudDatastoreV1ServiceImpl extends BaseAsyncDatastoreServiceImpl {

  /**
   * A base batcher for Cloud Datastore v1 operations executed in the context of an {@link
   * AsyncCloudDatastoreV1ServiceImpl}.
   *
   * @param <S> the response message type
   * @param <R> the request message builder type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  private abstract class V1Batcher<
          S extends Message, R extends Message.Builder, F, T extends Message>
      extends BaseRpcBatcher<S, R, F, T> {
    @Override
    @SuppressWarnings("unchecked")
    final R newBatch(R baseBatch) {
      return (R) baseBatch.clone();
    }
  }

  private final V1Batcher<CommitResponse, CommitRequest.Builder, Key, Mutation> deleteBatcher =
      new V1Batcher<CommitResponse, CommitRequest.Builder, Key, Mutation>() {
        @Override
        void addToBatch(Mutation mutation, CommitRequest.Builder batch) {
          batch.addMutations(mutation);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<CommitResponse> makeCall(CommitRequest.Builder batch) {
          try {
            return datastoreProxy.rawCommit(batch.build().toByteArray());
          } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Unexpected error.", e);
          }
        }

        @Override
        final Object getGroup(Key key) {
          return key.getRootKey();
        }

        @Override
        final Mutation toPb(Key value) {
          return Mutation.newBuilder().setDelete(DataTypeTranslator.toV1Key(value)).build();
        }
      };

  private final V1Batcher<LookupResponse, LookupRequest.Builder, Key, com.google.datastore.v1.Key>
      lookupByKeyBatcher =
          new V1Batcher<LookupResponse, LookupRequest.Builder, Key, com.google.datastore.v1.Key>() {
            @Override
            void addToBatch(com.google.datastore.v1.Key key, LookupRequest.Builder batch) {
              batch.addKeys(key);
            }

            @Override
            int getMaxCount() {
              return datastoreServiceConfig.maxBatchReadEntities;
            }

            @Override
            protected Future<LookupResponse> makeCall(LookupRequest.Builder batch) {
              return datastoreProxy.lookup(batch.build());
            }

            @Override
            final Object getGroup(Key key) {
              return key.getRootKey();
            }

            @Override
            final com.google.datastore.v1.Key toPb(Key value) {
              return DataTypeTranslator.toV1Key(value).build();
            }
          };

  private final V1Batcher<
          LookupResponse,
          LookupRequest.Builder,
          com.google.datastore.v1.Key,
          com.google.datastore.v1.Key>
      lookupByPbBatcher =
          new V1Batcher<
              LookupResponse,
              LookupRequest.Builder,
              com.google.datastore.v1.Key,
              com.google.datastore.v1.Key>() {
            @Override
            void addToBatch(com.google.datastore.v1.Key key, LookupRequest.Builder batch) {
              batch.addKeys(key);
            }

            @Override
            int getMaxCount() {
              return datastoreServiceConfig.maxBatchReadEntities;
            }

            @Override
            protected Future<LookupResponse> makeCall(LookupRequest.Builder batch) {
              return datastoreProxy.lookup(batch.build());
            }

            @Override
            final Object getGroup(com.google.datastore.v1.Key key) {
              return key.getPath(0);
            }

            @Override
            final com.google.datastore.v1.Key toPb(com.google.datastore.v1.Key value) {
              return value;
            }
          };

  private final V1Batcher<CommitResponse, CommitRequest.Builder, Entity, Mutation> putBatcher =
      new V1Batcher<CommitResponse, CommitRequest.Builder, Entity, Mutation>() {
        @Override
        void addToBatch(Mutation mutation, CommitRequest.Builder batch) {
          batch.addMutations(mutation);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<CommitResponse> makeCall(CommitRequest.Builder batch) {
          try {
            return datastoreProxy.rawCommit(batch.build().toByteArray());
          } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Unexpected error.", e);
          }
        }

        @Override
        final Object getGroup(Entity value) {
          return value.getKey().getRootKey();
        }

        @Override
        final Mutation toPb(Entity value) {
          return Mutation.newBuilder().setUpsert(DataTypeTranslator.toV1Entity(value)).build();
        }
      };

  private final V1Batcher<
          AllocateIdsResponse, AllocateIdsRequest.Builder, Key, com.google.datastore.v1.Key>
      allocateIdsBatcher =
          new V1Batcher<
              AllocateIdsResponse, AllocateIdsRequest.Builder, Key, com.google.datastore.v1.Key>() {
            @Override
            void addToBatch(com.google.datastore.v1.Key key, AllocateIdsRequest.Builder batch) {
              batch.addKeys(key);
            }

            @Override
            int getMaxCount() {
              return datastoreServiceConfig.maxBatchAllocateIdKeys;
            }

            @Override
            protected Future<AllocateIdsResponse> makeCall(AllocateIdsRequest.Builder batch) {
              return datastoreProxy.allocateIds(batch.build());
            }

            @Override
            // Keep this in sync with IdUtil.java
            final Object getGroup(Key key) {
              Key parent = key.getParent();
              if (parent == null) {
                // This element is used to represent the root ID class.  It has no kind
                // so cannot overlap with the return value below from the other branch.
                return PathElement.getDefaultInstance();
              } else {
                return DataTypeTranslator.toV1Key(parent).getPath(0);
              }
            }

            @Override
            final com.google.datastore.v1.Key toPb(Key value) {
              return DataTypeTranslator.toV1Key(value).build();
            }
          };

  private final CloudDatastoreV1Client datastoreProxy;

  public AsyncCloudDatastoreV1ServiceImpl(
      DatastoreServiceConfig datastoreServiceConfig,
      CloudDatastoreV1Client datastoreProxy,
      TransactionStack defaultTxnProvider) {
    super(
        datastoreServiceConfig,
        defaultTxnProvider,
        new QueryRunnerCloudDatastoreV1(datastoreServiceConfig, datastoreProxy));
    Preconditions.checkState(!DatastoreServiceGlobalConfig.getConfig().useApiProxy());
    this.datastoreProxy = datastoreProxy;
  }

  @Override
  protected TransactionImpl.InternalTransaction doBeginTransaction(TransactionOptions options) {
    BeginTransactionRequest.Builder request = BeginTransactionRequest.newBuilder();
    com.google.datastore.v1.TransactionOptions.Builder transactionOptionsBuilder =
        request.getTransactionOptionsBuilder();

    boolean isReadOnly = false;
    if (options.transactionMode() != null) {
      switch (options.transactionMode()) {
        case READ_ONLY:
          transactionOptionsBuilder.setReadOnly(ReadOnly.getDefaultInstance());
          isReadOnly = true;
          break;
        case READ_WRITE:
          transactionOptionsBuilder.setReadWrite(ReadWrite.getDefaultInstance());
          break;
        default:
          throw new AssertionError("Unrecognized transaction mode: " + options.transactionMode());
      }
    }

    if (options.previousTransaction() != null) {
      try {
        transactionOptionsBuilder
            .getReadWriteBuilder()
            .setPreviousTransaction(
                InternalTransactionCloudDatastoreV1.get(options.previousTransaction())
                    .getTransactionBytes());
      } catch (RuntimeException e) {
        logger.log(
            Level.FINE,
            "previousTransaction threw an exception, ignoring as it is likely "
                + "caused by a failed beginTransaction.",
            e);
        // Swallow, a failed commit
      }
    }

    Future<BeginTransactionResponse> future = datastoreProxy.beginTransaction(request.build());

    return InternalTransactionCloudDatastoreV1.create(datastoreProxy, future, isReadOnly);
  }

  @Override
  protected Future<Map<Key, Entity>> doBatchGet(
      @Nullable Transaction txn, final Set<Key> keysToGet, final Map<Key, Entity> resultMap) {
    // Initializing base request.
    final LookupRequest.Builder baseReq = LookupRequest.newBuilder();
    ReadOptions.Builder readOptionsBuilder = baseReq.getReadOptionsBuilder();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      readOptionsBuilder.setTransaction(
          InternalTransactionCloudDatastoreV1.get(txn).getTransactionBytes());
    } else if (datastoreServiceConfig.getReadPolicy().getConsistency() == EVENTUAL) {
      readOptionsBuilder.setReadConsistency(ReadOptions.ReadConsistency.EVENTUAL);
    } else {
      baseReq.clearReadOptions();
    }

    final boolean shouldUseMultipleBatches =
        txn == null && datastoreServiceConfig.getReadPolicy().getConsistency() != EVENTUAL;

    // Batch and issue the request(s).
    Iterator<LookupRequest.Builder> batches =
        lookupByKeyBatcher.getBatches(
            keysToGet, baseReq, baseReq.build().getSerializedSize(), shouldUseMultipleBatches);
    List<Future<LookupResponse>> futures = lookupByKeyBatcher.makeCalls(batches);

    return registerInTransaction(
        txn,
        new MultiFuture<LookupResponse, Map<Key, Entity>>(futures) {
          /**
           * A Map from a Key without an app id specified to the corresponding Key that the user
           * requested. This is a workaround for the Remote API to support matching requested Keys
           * to Entities that may be from a different app id.
           */
          private Map<com.google.datastore.v1.Key, Key> keyMapIgnoringAppId;

          @Override
          public Map<Key, Entity> get() throws InterruptedException, ExecutionException {
            try {
              aggregate(futures, null, null);
            } catch (TimeoutException e) {
              // Should never happen because we are passing null for the timeout params.
              throw new RuntimeException(e);
            }
            return resultMap;
          }

          @Override
          public Map<Key, Entity> get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            aggregate(futures, timeout, unit);
            return resultMap;
          }

          /**
           * Aggregates the results of the given Futures and issues (synchronous) followup requests
           * if any results were deferred.
           *
           * @param currentFutures the Futures corresponding to the batches of the initial
           *     LookupRequests.
           * @param timeout the timeout to use while waiting on the Future, or null for none.
           * @param timeoutUnit the unit of the timeout, or null for none.
           */
          private void aggregate(
              Iterable<Future<LookupResponse>> currentFutures,
              @Nullable Long timeout,
              @Nullable TimeUnit timeoutUnit)
              throws ExecutionException, InterruptedException, TimeoutException {
            // Use a while (true) loop so that we can issue followup requests for any deferred keys.
            while (true) {
              List<com.google.datastore.v1.Key> deferredKeys = Lists.newArrayList();

              // Aggregate the results from all of the Futures.
              // TODO: We don't actually respect the user provided timeout well.  The
              // actual max time that this can take is
              // (timeout * num batches * num deferred roundtrips)
              for (Future<LookupResponse> currentFuture : currentFutures) {
                LookupResponse resp =
                    getFutureWithOptionalTimeout(currentFuture, timeout, timeoutUnit);
                addEntitiesToResultMap(resp);
                deferredKeys.addAll(resp.getDeferredList());
              }

              if (deferredKeys.isEmpty()) {
                // Done.
                break;
              }

              // Some keys were deferred.  Issue followup requests, and loop again.
              Iterator<LookupRequest.Builder> followupBatches =
                  lookupByPbBatcher.getBatches(
                      deferredKeys,
                      baseReq,
                      baseReq.build().getSerializedSize(),
                      shouldUseMultipleBatches);
              currentFutures = lookupByPbBatcher.makeCalls(followupBatches);
            }
          }

          /**
           * Convenience method to get the result of a Future and optionally specify a timeout.
           *
           * @param future the Future to get.
           * @param timeout the timeout to use while waiting on the Future, or null for none.
           * @param timeoutUnit the unit of the timeout, or null for none.
           * @return the result of the Future.
           * @throws TimeoutException will only ever be thrown if a timeout is provided.
           */
          // TODO: Share this code with v3 impl.  Add timeout logic to multifuture?
          private LookupResponse getFutureWithOptionalTimeout(
              Future<LookupResponse> future, @Nullable Long timeout, @Nullable TimeUnit timeoutUnit)
              throws ExecutionException, InterruptedException, TimeoutException {
            if (timeout == null) {
              return future.get();
            } else {
              return future.get(timeout, timeoutUnit);
            }
          }

          /**
           * Adds the Entities from the LookupResponse to the resultMap. Will omit Keys that were
           * missing. Handles Keys with different App Ids from the Entity.Key. See {@link
           * #findKeyFromRequestIgnoringAppId}.
           */
          private void addEntitiesToResultMap(LookupResponse response) {
            for (EntityResult entityResult : response.getFoundList()) {
              Entity responseEntity = DataTypeTranslator.toEntity(entityResult.getEntity());
              Key responseKey = responseEntity.getKey();

              // Hack for Remote API which rewrites App Ids on Keys.
              if (!keysToGet.contains(responseKey)) {
                responseKey = findKeyFromRequestIgnoringAppId(entityResult.getEntity().getKey());
              }
              resultMap.put(responseKey, responseEntity);
            }
          }

          /**
           * This is a hack to support calls going through the Remote API. The problem is:
           *
           * <p>The requested Key may have a local app id. The returned Entity may have a remote app
           * id.
           *
           * <p>In this case, we want to return a Map.Entry with {LocalKey, RemoteEntity}. This way,
           * the user can always do map.get(keyFromRequest).
           *
           * <p>This method will find the corresponding requested LocalKey for a RemoteKey by
           * ignoring the AppId field.
           *
           * <p>Note that we used to be able to rely on the order of the Response Entities matching
           * the order of Request Keys. We can no longer do so with the addition of Deferred
           * results.
           *
           * @param keyFromResponse the key from the Response that did not match any of the
           *     requested Keys.
           * @return the Key from the request that corresponds to the given Key from the Response
           *     (ignoring AppId.)
           */
          private Key findKeyFromRequestIgnoringAppId(com.google.datastore.v1.Key keyFromResponse) {
            // We'll create this Map lazily the first time, then cache it for future calls.
            if (keyMapIgnoringAppId == null) {
              keyMapIgnoringAppId = Maps.newHashMap();
              for (Key requestKey : keysToGet) {
                com.google.datastore.v1.Key.Builder requestKeyAsRefWithoutApp =
                    DataTypeTranslator.toV1Key(requestKey);
                requestKeyAsRefWithoutApp.getPartitionIdBuilder().clearProjectId();
                keyMapIgnoringAppId.put(requestKeyAsRefWithoutApp.build(), requestKey);
              }
            }

            com.google.datastore.v1.Key.Builder keyBuilder = keyFromResponse.toBuilder();
            keyBuilder.getPartitionIdBuilder().clearProjectId();
            Key result = keyMapIgnoringAppId.get(keyBuilder.build());
            if (result == null) {
              // TODO: What should we do here?
              throw new DatastoreFailureException("Internal error");
            }
            return result;
          }
        });
  }

  /**
   * Returns a list of entities with duplicates (by key) removed (last entity wins). {@code
   * dedupedIndexMap} is populated with a mapping of indexes in the returned list back to the {@code
   * entities} list.
   */
  static List<Entity> dedupeByKey(
      List<Entity> entities, Multimap<Integer, Integer> dedupedIndexMap) {
    Map<Key, Integer> dedupedEntitiesIndexes = new HashMap<>();
    Map<Key, Entity> dedupedEntities = new LinkedHashMap<>();
    int entityIdx = 0;
    for (Entity entity : entities) {
      Key key = entity.getKey();
      // Add or overwrite.
      if (dedupedEntities.put(entity.getKey(), entity) == null) {
        // First time we've seen this key; record its position in the deduped list.
        dedupedEntitiesIndexes.put(key, dedupedEntities.size() - 1);
      }

      dedupedIndexMap.put(dedupedEntitiesIndexes.get(key), entityIdx);
      entityIdx++;
    }
    return new ArrayList<>(dedupedEntities.values());
  }

  @Override
  protected Future<List<Key>> doBatchPut(
      @Nullable final Transaction txn, final List<Entity> entities) {
    if (txn == null) {
      CommitRequest.Builder baseReq = CommitRequest.newBuilder();
      baseReq.setMode(CommitRequest.Mode.NON_TRANSACTIONAL);

      final Multimap<Integer, Integer> dedupedIndexMap = HashMultimap.create();
      final List<Entity> dedupedEntities = dedupeByKey(entities, dedupedIndexMap);

      final List<Integer> order = Lists.newArrayListWithCapacity(dedupedEntities.size());
      Iterator<CommitRequest.Builder> batches =
          putBatcher.getBatches(
              dedupedEntities,
              baseReq,
              baseReq.build().getSerializedSize(),
              /* group= */ true,
              order);
      List<Future<CommitResponse>> futures = putBatcher.makeCalls(batches);

      return new ReorderingMultiFuture<CommitResponse, List<Key>>(futures, order) {
        @Override
        protected List<Key> aggregate(
            CommitResponse intermediateResult, Iterator<Integer> indexItr, List<Key> result) {
          for (MutationResult mutationResult : intermediateResult.getMutationResultsList()) {
            int index = indexItr.next();
            Key key = dedupedEntities.get(index).getKey();
            if (mutationResult.hasKey()) {
              // Update the Key object from the original Entity with the allocated id.
              List<PathElement> pathElements = mutationResult.getKey().getPathList();
              key.setId(pathElements.get(pathElements.size() - 1).getId());
            }
            for (Integer dedupedIndex : dedupedIndexMap.get(index)) {
              result.set(dedupedIndex, key);
            }
          }
          return result;
        }

        @Override
        protected List<Key> initResult() {
          // Pre-deduped size.
          int size = entities.size();
          // Elements are set into this list by the reordering batcher to recreate the
          // original key order. The list size must be remain mutable for compatibility
          // with previous SDK releases.
          List<Key> keyList = Lists.newArrayListWithCapacity(size);
          keyList.addAll(Collections.<Key>nCopies(size, null));
          return keyList;
        }
      };
    }

    // Handle transactional put.
    // v1 builds transactions locally: we defer the put until transaction commit. However,
    // the Java API requires us to the return keys corresponding to the entities. Insertions
    // might have incomplete keys, so we need to allocate these IDs.

    TransactionImpl.ensureTxnActive(txn);
    final InternalTransactionCloudDatastoreV1 txnV1 = InternalTransactionCloudDatastoreV1.get(txn);

    ImmutableList.Builder<Key> keyListBuilder = ImmutableList.builder();
    final List<Key> incompleteKeys = Lists.newArrayList();
    final List<com.google.datastore.v1.Entity.Builder> incompleteEntityBldrs = Lists.newArrayList();
    for (Entity entity : entities) {
      Key key = entity.getKey();
      keyListBuilder.add(key);
      if (key.isComplete()) {
        // Mutations on complete keys and incomplete keys can't conflict with each other,
        // so it's safe to add mutations with completed keys immediately (out of order)
        // even though we need to wait for the allocations for incomplete keys.
        txnV1.deferPut(entity);
      } else {
        // Freeze entity.  We will add it to the transaction in a callback from the
        // AllocateId call below.
        com.google.datastore.v1.Entity.Builder entityV1 =
            com.google.datastore.v1.Entity.newBuilder();
        DataTypeTranslator.addPropertiesToPb(entity.getPropertyMap(), entityV1);
        incompleteEntityBldrs.add(entityV1);
        incompleteKeys.add(key);
      }
    }
    final List<Key> allKeys = keyListBuilder.build();

    if (incompleteKeys.isEmpty()) {
      return new FutureHelper.FakeFuture<List<Key>>(allKeys);
    }
    return registerInTransaction(
        txn,
        new FutureWrapper<List<com.google.datastore.v1.Key>, List<Key>>(
            allocateIds(incompleteKeys)) {
          @Override
          protected List<Key> wrap(List<com.google.datastore.v1.Key> completedKeyPbs) {
            Iterator<com.google.datastore.v1.Entity.Builder> entityPbBldrIt =
                incompleteEntityBldrs.iterator();
            Iterator<Key> incompleteKeysIt = incompleteKeys.iterator();
            for (com.google.datastore.v1.Key keyV1 : completedKeyPbs) {
              // Set the id field of the original key object.
              updateKey(keyV1, incompleteKeysIt.next());
              // Build the entity proto and add it to the transaction.
              txnV1.deferPut(entityPbBldrIt.next().setKey(keyV1));
            }
            return allKeys;
          }

          @Override
          protected Throwable convertException(Throwable cause) {
            return cause;
          }
        });
  }

  @Override
  protected Future<Void> doBatchDelete(@Nullable Transaction txn, Collection<Key> keys) {
    if (txn != null) {
      // If this is part of a transaction, then all we do is schedule the delete. No RPCs necessary.
      TransactionImpl.ensureTxnActive(txn);
      InternalTransactionCloudDatastoreV1 txnV1 = InternalTransactionCloudDatastoreV1.get(txn);
      for (Key key : keys) {
        txnV1.deferDelete(key);
      }
      return new FutureHelper.FakeFuture<Void>(null);
    }

    CommitRequest.Builder baseReq = CommitRequest.newBuilder();
    baseReq.setMode(CommitRequest.Mode.NON_TRANSACTIONAL);
    // Maintain ordering for tests.
    Set<Key> dedupedKeys = new LinkedHashSet<>(keys);
    Iterator<CommitRequest.Builder> batches =
        deleteBatcher.getBatches(
            dedupedKeys, baseReq, baseReq.build().getSerializedSize(), /* group= */ true);
    List<Future<CommitResponse>> futures = deleteBatcher.makeCalls(batches);
    return new MultiFuture<CommitResponse, Void>(futures) {
      @Override
      public Void get() throws InterruptedException, ExecutionException {
        for (Future<CommitResponse> future : futures) {
          future.get();
        }
        return null;
      }

      @Override
      public Void get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        for (Future<CommitResponse> future : futures) {
          future.get(timeout, unit);
        }
        return null;
      }
    };
  }

  /** This API is specific to sequential IDs, which Cloud Datastore v1 does not support. */
  @Override
  public Future<KeyRange> allocateIds(final Key parent, final String kind, long num) {
    // Not supported in Cloud Datastore v1. See http://b/20055469.
    throw new UnsupportedOperationException();
  }

  /** Allocates scattered IDs for a list of incomplete keys. */
  /* @VisibleForTesting */
  protected Future<List<com.google.datastore.v1.Key>> allocateIds(List<Key> keyList) {
    final List<Integer> order = Lists.newArrayListWithCapacity(keyList.size());
    Iterator<AllocateIdsRequest.Builder> batches =
        allocateIdsBatcher.getBatches(
            keyList,
            AllocateIdsRequest.newBuilder(),
            /* baseBatchSize= */ 0,
            /* group= */ true,
            order);
    List<Future<AllocateIdsResponse>> futures = allocateIdsBatcher.makeCalls(batches);

    return new ReorderingMultiFuture<AllocateIdsResponse, List<com.google.datastore.v1.Key>>(
        futures, order) {
      @Override
      protected List<com.google.datastore.v1.Key> aggregate(
          AllocateIdsResponse batch,
          Iterator<Integer> indexItr,
          List<com.google.datastore.v1.Key> result) {
        for (com.google.datastore.v1.Key key : batch.getKeysList()) {
          result.set(indexItr.next(), key);
        }
        return result;
      }

      @Override
      protected List<com.google.datastore.v1.Key> initResult() {
        return Arrays.asList(new com.google.datastore.v1.Key[order.size()]);
      }
    };
  }

  /** This API is specific to sequential IDs, which Cloud Datastore v1 does not support. */
  @Override
  public Future<KeyRangeState> allocateIdRange(final KeyRange range) {
    // Not supported in Cloud Datastore v1. See http://b/20055469.
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    // Not supported in Cloud Datastore v1. See http://b/20055663.
    throw new UnsupportedOperationException();
  }

  /** Update a {@link Key} with the id from a key proto, if it is populated. */
  private static void updateKey(com.google.datastore.v1.Key keyV1, Key key) {
    List<PathElement> pathElements = keyV1.getPathList();
    if (!pathElements.isEmpty()) {
      PathElement lastElement = pathElements.get(pathElements.size() - 1);
      if (lastElement.getIdTypeCase() == IdTypeCase.ID) {
        key.setId(lastElement.getId());
      }
    }
  }
}
