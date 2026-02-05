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

import static com.google.appengine.api.datastore.DatastoreApiHelper.makeAsyncCall;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.ReadPolicy.Consistency.EVENTUAL;

import com.google.appengine.api.datastore.Batcher.ReorderingMultiFuture;
import com.google.appengine.api.datastore.DatastoreService.KeyRangeState;
import com.google.appengine.api.datastore.FutureHelper.MultiFuture;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.api.proto2api.ApiBasePb.StringProto;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.AllocateIdsRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.AllocateIdsResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.BeginTransactionRequest.TransactionMode;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.DatastoreService_3;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.DeleteResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.PutResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.CompositeIndex;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * An implementation of AsyncDatastoreService using the DatastoreV3 API.
 *
 */
class AsyncDatastoreServiceImpl extends BaseAsyncDatastoreServiceImpl {

  /**
   * A base batcher for DatastoreV3 operations executed in the context of an {@link
   * AsyncDatastoreServiceImpl}.
   *
   * @param <S> the response message type
   * @param <R> the request message type
   * @param <F> the Java specific representation of a value
   * @param <T> the proto representation of value
   */
  private abstract class V3Batcher<
          S extends Message, R extends MessageLite.Builder, F, T extends Message>
      extends BaseRpcBatcher<S, R, F, T> {
    @Override
    @SuppressWarnings("unchecked")
    final R newBatch(R baseBatch) {
      return (R) baseBatch.clone();
    }
  }

  /**
   * A base batcher for operations that operate on {@link Key}s.
   *
   * @param <S> the response message type
   * @param <R> the request message type
   */
  private abstract class V3KeyBatcher<S extends Message, R extends Message.Builder>
      extends V3Batcher<S, R, Key, Reference> {
    @Override
    final Object getGroup(Key value) {
      return value.getRootKey();
    }

    @Override
    final Reference toPb(Key value) {
      return KeyTranslator.convertToPb(value);
    }
  }

  private final V3KeyBatcher<DeleteResponse, DeleteRequest.Builder> deleteBatcher =
      new V3KeyBatcher<DeleteResponse, DeleteRequest.Builder>() {
        @Override
        void addToBatch(Reference value, DeleteRequest.Builder batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<DeleteResponse> makeCall(DeleteRequest.Builder batch) {
          return makeAsyncCall(
              apiConfig, DatastoreService_3.Method.Delete, batch, DeleteResponse.newBuilder());
        }

        @Override
        protected int getEmbeddedSize(Reference value) {
          return CodedOutputStream.computeMessageSize(DeleteRequest.KEY_FIELD_NUMBER, value);
        }
      };

  private final V3KeyBatcher<GetResponse, GetRequest.Builder> getByKeyBatcher =
      new V3KeyBatcher<GetResponse, GetRequest.Builder>() {
        @Override
        void addToBatch(Reference value, GetRequest.Builder batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<GetResponse> makeCall(GetRequest.Builder batch) {
          return makeAsyncCall(apiConfig, DatastoreService_3.Method.Get, batch, GetResponse.newBuilder());
        }

        @Override
        protected int getEmbeddedSize(Reference value) {
          return CodedOutputStream.computeMessageSize(GetRequest.KEY_FIELD_NUMBER, value);
        }
      };

  private final V3Batcher<GetResponse, GetRequest.Builder, Reference, Reference> getByReferenceBatcher =
      new V3Batcher<GetResponse, GetRequest.Builder, Reference, Reference>() {
        @Override
        final Object getGroup(Reference value) {
          return value.getPath().getElement(0);
        }

        @Override
        final Reference toPb(Reference value) {
          return value;
        }

        @Override
        void addToBatch(Reference value, GetRequest.Builder batch) {
          batch.addKey(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchReadEntities;
        }

        @Override
        protected Future<GetResponse> makeCall(GetRequest.Builder batch) {
          return makeAsyncCall(apiConfig, DatastoreService_3.Method.Get, batch, GetResponse.newBuilder());
        }

        @Override
        protected int getEmbeddedSize(Reference value) {
          return CodedOutputStream.computeMessageSize(GetRequest.KEY_FIELD_NUMBER, value);
        }
      };

  private final V3Batcher<PutResponse, PutRequest.Builder, Entity, EntityProto> putBatcher =
      new V3Batcher<PutResponse, PutRequest.Builder, Entity, EntityProto>() {
        @Override
        Object getGroup(Entity value) {
          return value.getKey().getRootKey();
        }

        @Override
        void addToBatch(EntityProto value, PutRequest.Builder batch) {
          batch.addEntity(value);
        }

        @Override
        int getMaxCount() {
          return datastoreServiceConfig.maxBatchWriteEntities;
        }

        @Override
        protected Future<PutResponse> makeCall(PutRequest.Builder batch) {
          return makeAsyncCall(apiConfig, DatastoreService_3.Method.Put, batch, PutResponse.newBuilder());
        }

        @Override
        EntityProto toPb(Entity value) {
          return EntityTranslator.convertToPb(value);
        }

        @Override
        protected int getEmbeddedSize(EntityProto value) {
          return CodedOutputStream.computeMessageSize(PutRequest.ENTITY_FIELD_NUMBER, value);
        }
      };

  private final ApiConfig apiConfig;

  public AsyncDatastoreServiceImpl(
      DatastoreServiceConfig datastoreServiceConfig,
      ApiConfig apiConfig,
      TransactionStack defaultTxnProvider) {
    super(
        datastoreServiceConfig,
        defaultTxnProvider,
        new QueryRunnerV3(datastoreServiceConfig, apiConfig));
    this.apiConfig = apiConfig;
  }

  @Override
  protected TransactionImpl.InternalTransaction doBeginTransaction(TransactionOptions options) {
    DatastoreV3Pb.Transaction.Builder remoteTxn = DatastoreV3Pb.Transaction.newBuilder();
    DatastoreV3Pb.BeginTransactionRequest.Builder request =
        DatastoreV3Pb.BeginTransactionRequest.newBuilder()
            .setApp(datastoreServiceConfig.getAppIdNamespace().getAppId())
            .setAllowMultipleEg(options.isXG());
    if (options.previousTransaction() != null) {
      try {
        request.setPreviousTransaction(
            InternalTransactionV3.toProto(options.previousTransaction()));
      } catch (RuntimeException e) {
        logger.log(
            Level.FINE,
            "previousTransaction threw an exception, ignoring as it is likely "
                + "caused by a failed beginTransaction.",
            e);
      }
    }
    if (options.transactionMode() != null) {
      request.setMode(switch (options.transactionMode()) {
        case READ_ONLY -> TransactionMode.READ_ONLY;
        case READ_WRITE -> TransactionMode.READ_WRITE;
        default -> throw new AssertionError("Unrecognized transaction mode: " + options.transactionMode());
      });
    }

    Future<DatastoreV3Pb.Transaction> future =
        DatastoreApiHelper.makeAsyncCall(
            apiConfig, DatastoreService_3.Method.BeginTransaction, request, remoteTxn);

    return new InternalTransactionV3(apiConfig, request.getApp(), future);
  }

  @Override
  protected final Future<Map<Key, Entity>> doBatchGet(
      @Nullable Transaction txn, final Set<Key> keysToGet, final Map<Key, Entity> resultMap) {
    // Initializing base request.
    final GetRequest.Builder baseReq = GetRequest.newBuilder().setAllowDeferred(true);
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.toProto(txn));
    }
    if (datastoreServiceConfig.getReadPolicy().getConsistency() == EVENTUAL) {
      baseReq.setFailoverMs(ARBITRARY_FAILOVER_READ_MS);
      baseReq.setStrong(false); // Allows the datastore to always use READ_CONSISTENT.
    }

    final boolean shouldUseMultipleBatches =
        txn == null && datastoreServiceConfig.getReadPolicy().getConsistency() != EVENTUAL;

    // Batch and issue the request(s).
    Iterator<GetRequest.Builder> batches =
        getByKeyBatcher.getBatches(
            keysToGet, baseReq, baseReq.build().getSerializedSize(), shouldUseMultipleBatches);
    List<Future<GetResponse>> futures = getByKeyBatcher.makeCalls(batches);

    return registerInTransaction(
        txn,
        new MultiFuture<GetResponse, Map<Key, Entity>>(futures) {
          /**
           * A Map from a Reference without an App Id specified to the corresponding Key that the
           * user requested. This is a workaround for the Remote API to support matching requested
           * Keys to Entities that may be from a different App Id .
           */
          private Map<Reference, Key> keyMapIgnoringAppId;

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
           *     GetRequests.
           * @param timeout the timeout to use while waiting on the Future, or null for none.
           * @param timeoutUnit the unit of the timeout, or null for none.
           */
          private void aggregate(
              Iterable<Future<GetResponse>> currentFutures,
              @Nullable Long timeout,
              @Nullable TimeUnit timeoutUnit)
              throws ExecutionException, InterruptedException, TimeoutException {
            // Use a while (true) loop so that we can issue followup requests for any deferred keys.
            while (true) {
              List<Reference> deferredRefs = Lists.newLinkedList();

              // Aggregate the results from all of the Futures.
              // TODO: We don't actually respect the user provided timeout well.  The
              // actual max time that this can take is (timeout * num batches * num deferred
              // roundtrips)
              for (Future<GetResponse> currentFuture : currentFutures) {
                GetResponse resp =
                    getFutureWithOptionalTimeout(currentFuture, timeout, timeoutUnit);
                addEntitiesToResultMap(resp);
                deferredRefs.addAll(resp.getDeferredList());
              }

              if (deferredRefs.isEmpty()) {
                // Done.
                break;
              }

              // Some keys were deferred.  Issue followup requests, and loop again.
              Iterator<GetRequest.Builder> followupBatches =
                  getByReferenceBatcher.getBatches(
                      deferredRefs, baseReq, baseReq.build().getSerializedSize(), shouldUseMultipleBatches);
              currentFutures = getByReferenceBatcher.makeCalls(followupBatches);
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
          private GetResponse getFutureWithOptionalTimeout(
              Future<GetResponse> future, @Nullable Long timeout, @Nullable TimeUnit timeoutUnit)
              throws ExecutionException, InterruptedException, TimeoutException {
            if (timeout == null) {
              return future.get();
            } else {
              return future.get(timeout, timeoutUnit);
            }
          }

          /**
           * Adds the Entities from the GetResponse to the resultMap. Will omit Keys that were
           * missing. Handles Keys with different App Ids from the Entity.Key. See {@link
           * #findKeyFromRequestIgnoringAppId(Reference)}
           */
          private void addEntitiesToResultMap(GetResponse response) {
            for (GetResponse.Entity entityResult : response.getEntityList()) {
              if (entityResult.hasEntity()) {
                Entity responseEntity = EntityTranslator.createFromPb(entityResult.getEntity());
                Key responseKey = responseEntity.getKey();

                // Hack for Remote API which rewrites App Ids on Keys.
                if (!keysToGet.contains(responseKey)) {
                  responseKey = findKeyFromRequestIgnoringAppId(entityResult.getEntity().getKey().toBuilder());
                }
                resultMap.put(responseKey, responseEntity);
              }
              // Else, the Key was missing.
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
           * @param referenceFromResponse the reference from the Response that did not match any of
           *     the requested Keys. (May be mutated.)
           * @return the Key from the request that corresponds to the given Reference from the
           *     Response (ignoring AppId.)
           */
          private Key findKeyFromRequestIgnoringAppId(Reference.Builder referenceFromResponse) {
            // We'll create this Map lazily the first time, then cache it for future calls.
            if (keyMapIgnoringAppId == null) {
              keyMapIgnoringAppId = Maps.newHashMap();
              for (Key requestKey : keysToGet) {
                Reference.Builder requestKeyAsRefWithoutApp =
                    KeyTranslator.convertToPb(requestKey).toBuilder().setApp("");
                keyMapIgnoringAppId.put(requestKeyAsRefWithoutApp.build(), requestKey);
              }
            }
            // Note: mutating the input ref, but that's ok.
            Key result = keyMapIgnoringAppId.get(referenceFromResponse.setApp("").build());
            if (result == null) {
              // TODO: What should we do here?
              throw new DatastoreFailureException("Internal error");
            }
            return result;
          }
        });
  }

  @Override
  protected Future<List<Key>> doBatchPut(@Nullable Transaction txn, final List<Entity> entities) {
    PutRequest.Builder baseReq = PutRequest.newBuilder();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.toProto(txn));
    }
    boolean group = !baseReq.hasTransaction(); // Do not group when inside a transaction.
    final List<Integer> order = Lists.newArrayListWithCapacity(entities.size());
    Iterator<PutRequest.Builder> batches =
        putBatcher.getBatches(entities, baseReq, baseReq.build().getSerializedSize(), group, order);
    List<Future<PutResponse>> futures = putBatcher.makeCalls(batches);

    return registerInTransaction(
        txn,
        new ReorderingMultiFuture<PutResponse, List<Key>>(futures, order) {
          @Override
          protected List<Key> aggregate(
              PutResponse intermediateResult, Iterator<Integer> indexItr, List<Key> result) {
            for (Reference reference : intermediateResult.getKeyList()) {
              int index = indexItr.next();
              Key key = entities.get(index).getKey();
              KeyTranslator.updateKey(reference, key);
              result.set(index, key);
            }
            return result;
          }

          @Override
          protected List<Key> initResult() {
            // Create an array pre-populated with null values (twice :-))
            List<Key> result = new ArrayList<Key>(Collections.<Key>nCopies(order.size(), null));
            return result;
          }
        });
  }

  @Override
  protected Future<Void> doBatchDelete(@Nullable Transaction txn, Collection<Key> keys) {
    DeleteRequest.Builder baseReq = DeleteRequest.newBuilder();
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      baseReq.setTransaction(InternalTransactionV3.toProto(txn));
    }
    boolean group = !baseReq.hasTransaction(); // Do not group inside a transaction.
    Iterator<DeleteRequest.Builder> batches =
        deleteBatcher.getBatches(keys, baseReq, baseReq.build().getSerializedSize(), group);
    List<Future<DeleteResponse>> futures = deleteBatcher.makeCalls(batches);
    return registerInTransaction(
        txn,
        new MultiFuture<DeleteResponse, Void>(futures) {
          @Override
          public Void get() throws InterruptedException, ExecutionException {
            for (Future<DeleteResponse> future : futures) {
              future.get();
            }
            return null;
          }

          @Override
          public Void get(long timeout, TimeUnit unit)
              throws InterruptedException, ExecutionException, TimeoutException {
            for (Future<DeleteResponse> future : futures) {
              future.get(timeout, unit);
            }
            return null;
          }
        });
  }

  // exposed for testing
  static Reference buildAllocateIdsRef(Key parent, String kind, AppIdNamespace appIdNamespace) {
    if (parent != null && !parent.isComplete()) {
      throw new IllegalArgumentException("parent key must be complete");
    }
    // the datastore just ignores the name component
    Key key = new Key(kind, parent, Key.NOT_ASSIGNED, "ignored", appIdNamespace);
    return KeyTranslator.convertToPb(key);
  }

  @Override
  public Future<KeyRange> allocateIds(final Key parent, final String kind, long num) {
    if (num <= 0) {
      throw new IllegalArgumentException("num must be > 0");
    }

    if (num > 1000000000) {
      throw new IllegalArgumentException("num must be < 1 billion");
    }

    // kind validation taken care of by the next call
    final AppIdNamespace appIdNamespace = datastoreServiceConfig.getAppIdNamespace();
    Reference allocateIdsRef = buildAllocateIdsRef(parent, kind, appIdNamespace);
    AllocateIdsRequest.Builder req =
        AllocateIdsRequest.newBuilder().setSize(num).setModelKey(allocateIdsRef);
    AllocateIdsResponse.Builder resp = AllocateIdsResponse.newBuilder();
    Future<AllocateIdsResponse> future =
        makeAsyncCall(apiConfig, DatastoreService_3.Method.AllocateIds, req, resp);
    return new FutureWrapper<AllocateIdsResponse, KeyRange>(future) {
      @Override
      protected KeyRange wrap(AllocateIdsResponse resp) throws Exception {
        return new KeyRange(parent, kind, resp.getStart(), resp.getEnd(), appIdNamespace);
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<KeyRangeState> allocateIdRange(final KeyRange range) {
    Key parent = range.getParent();
    final String kind = range.getKind();
    final long start = range.getStart().getId();
    long end = range.getEnd().getId();

    AllocateIdsRequest.Builder req =
        AllocateIdsRequest.newBuilder()
            .setModelKey(AsyncDatastoreServiceImpl.buildAllocateIdsRef(parent, kind, null))
            .setMax(end);
    AllocateIdsResponse.Builder resp = AllocateIdsResponse.newBuilder();
    Future<AllocateIdsResponse> future =
        makeAsyncCall(apiConfig, DatastoreService_3.Method.AllocateIds, req, resp);
    return new FutureWrapper<AllocateIdsResponse, KeyRangeState>(future) {
      @SuppressWarnings("deprecation")
      @Override
      protected KeyRangeState wrap(AllocateIdsResponse resp) throws Exception {
        // Check for collisions, i.e. existing entities with ids in this range.
        //
        // We could do this before the allocation, but we'd still have to do it
        // afterward as well to catch the race condition where an entity is inserted
        // after that initial check but before the allocation. So, skip the up front
        // check and just do it once, here.
        Query query = new Query(kind).setKeysOnly();
        query.addFilter(
            Entity.KEY_RESERVED_PROPERTY, FilterOperator.GREATER_THAN_OR_EQUAL, range.getStart());
        query.addFilter(
            Entity.KEY_RESERVED_PROPERTY, FilterOperator.LESS_THAN_OR_EQUAL, range.getEnd());
        List<Entity> collision = prepare(query).asList(withLimit(1));

        if (!collision.isEmpty()) {
          return KeyRangeState.COLLISION;
        }

        // Check for a race condition, i.e. cases where the datastore may have
        // cached id batches that contain ids in this range.
        boolean raceCondition = start < resp.getStart();
        return raceCondition ? KeyRangeState.CONTENTION : KeyRangeState.EMPTY;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    StringProto.Builder req =
        StringProto.newBuilder()
            .setValue(datastoreServiceConfig.getAppIdNamespace().getAppId());
    return new FutureWrapper<CompositeIndices, Map<Index, IndexState>>(
        makeAsyncCall(
            apiConfig, DatastoreService_3.Method.GetIndices, req, CompositeIndices.newBuilder())) {
      @Override
      protected Map<Index, IndexState> wrap(CompositeIndices indices) throws Exception {
        Map<Index, IndexState> answer = new LinkedHashMap<Index, IndexState>();
        for (CompositeIndex ci : indices.getIndexList()) {
          Index index = IndexTranslator.convertFromPb(ci);
          switch (ci.getState()) {
            case DELETED -> answer.put(index, IndexState.DELETING);
            case ERROR -> answer.put(index, IndexState.ERROR);
            case READ_WRITE -> answer.put(index, IndexState.SERVING);
            case WRITE_ONLY -> answer.put(index, IndexState.BUILDING);
            default -> logger.log(Level.WARNING, "Unrecognized index state for {0}", index);
          }
        }
        return answer;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }
}
