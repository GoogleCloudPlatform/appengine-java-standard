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

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.DatastoreV3Pb.CompiledCursor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
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
import com.google.datastore.v1.QueryResultBatch;
import com.google.datastore.v1.QueryResultBatch.MoreResultsType;
import com.google.datastore.v1.RollbackRequest;
import com.google.datastore.v1.RollbackResponse;
import com.google.datastore.v1.RunQueryRequest;
import com.google.datastore.v1.RunQueryResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import com.google.storage.onestore.v3.OnestoreEntity.IndexPosition;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for Cloud Datastore v1 service tests. For ease of testing, the create.* and
 * expect.*Request methods in this class take care of populating the project ID in requests.
 */
public abstract class BaseCloudDatastoreV1ServiceImplTest {

  // Incrementing transaction handle to ensure sequential transactions are unique.
  private final AtomicLong transactionId = new AtomicLong(1L);

  // Incrementing cursor position to ensure they are unique.
  private final AtomicLong cursorIndex = new AtomicLong(1L);

  private List<Object> mocks;

  static final String APP_ID = "s~project-id";

  CloudDatastoreV1Client cloudDatastoreV1Client;
  TransactionStack txnStack;
  DatastoreServiceConfig datastoreServiceConfig;

  @Before
  public final void setUpBase() throws Exception {
    DatastoreServiceGlobalConfig.setConfig(
        DatastoreServiceGlobalConfig.builder().appId(APP_ID).build());

    cloudDatastoreV1Client = EasyMock.createMock(CloudDatastoreV1Client.class);

    txnStack = new TransactionStackImpl(new InstanceMemberThreadLocalTransactionStack());
    datastoreServiceConfig =
        withImplicitTransactionManagementPolicy(ImplicitTransactionManagementPolicy.AUTO)
            .readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    resetMocks();
  }

  @After
  public void tearDown() throws Exception {
    DatastoreServiceGlobalConfig.clear();
  }

  DatastoreServiceImpl newDatastoreService() {
    return new DatastoreServiceImpl(newAsyncDatastoreService());
  }

  AsyncCloudDatastoreV1ServiceImpl newAsyncDatastoreService() {
    return newAsyncDatastoreService(null);
  }

  AsyncCloudDatastoreV1ServiceImpl newAsyncDatastoreService(final DatastoreType type) {
    return newAsyncDatastoreService(datastoreServiceConfig, txnStack, type);
  }

  AsyncCloudDatastoreV1ServiceImpl newAsyncDatastoreService(
      DatastoreServiceConfig dsConfig, TransactionStack txnStack, final DatastoreType type) {
    dsConfig = new DatastoreServiceConfig(dsConfig);
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(dsConfig.getDeadline());
    if (type == null) {
      return new AsyncCloudDatastoreV1ServiceImpl(dsConfig, cloudDatastoreV1Client, txnStack);
    } else {
      return new AsyncCloudDatastoreV1ServiceImpl(dsConfig, cloudDatastoreV1Client, txnStack) {
        @Override
        public DatastoreType getDatastoreType() {
          return type;
        }
      };
    }
  }

  Key createKey(int index) {
    return KeyFactory.createKey("testkind" + index, "testname" + index);
  }

  Key createChildKey(Key parent, int index) {
    return KeyFactory.createKey(parent, "testkind" + index, "testname" + index);
  }

  Key toKeyWithDifferentAppId(Key key, String newAppId) {
    return new Key(
        key.getKind(),
        key.getParent() == null ? null : toKeyWithDifferentAppId(key.getParent(), newAppId),
        key.getId(),
        key.getName(),
        new AppIdNamespace(newAppId, key.getAppIdNamespace().getNamespace()));
  }

  Entity createEntity(Key key, int index) {
    Entity result = new Entity(key);
    result.setProperty("testprop" + index, "testvalue" + index);
    return result;
  }

  void assertEntityEquals(Entity expected, Entity actual) {
    assertWithMessage("expected: %s; actual: %s", expected.getKey(), actual.getKey())
        .that(expected.getKey().equals(actual.getKey(), false))
        .isTrue();
    assertThat(actual.getProperty("aString")).isEqualTo(expected.getProperty("aString"));

    // Datastore may convert from Integer to Long.
    Number expectedInt = (Number) expected.getProperty("anInteger");
    Number actualInt = (Number) actual.getProperty("anInteger");
    assertThat(actualInt.longValue()).isEqualTo(expectedInt.longValue());

    // Datastore may convert from Float to Double.
    Number expectedFloat = (Number) expected.getProperty("aFloat");
    Number actualFloat = (Number) actual.getProperty("aFloat");
    assertThat(actualFloat.doubleValue()).isWithin(1e-9).of(expectedFloat.doubleValue());
  }

  /**
   * Create a LookupResponse with the given Entities. These all represent "found" entities and
   * should not be null.
   */
  LookupResponse createLookupResponse(Entity... entities) {
    return createLookupResponse(
        Arrays.asList(entities), Collections.<Key>emptyList(), Collections.<Key>emptyList());
  }

  LookupResponse createLookupResponse(
      Collection<Entity> entities, Collection<Key> missingKeys, Collection<Key> deferredKeys) {
    LookupResponse.Builder responseBuilder = LookupResponse.newBuilder();
    for (Entity entity : entities) {
      responseBuilder.addFoundBuilder().setEntity(DataTypeTranslator.toV1Entity(entity));
    }
    for (Key missingKey : missingKeys) {
      responseBuilder
          .addMissingBuilder()
          .setEntity(DataTypeTranslator.toV1Entity(new Entity(missingKey)));
    }
    for (Key deferredKey : deferredKeys) {
      responseBuilder.addDeferred(DataTypeTranslator.toV1Key(deferredKey));
    }
    return responseBuilder.build();
  }

  com.google.datastore.v1.Key completeKey(com.google.datastore.v1.Key key, long newId) {
    com.google.datastore.v1.Key.Builder keyBuilder = key.toBuilder();
    int numElements = keyBuilder.getPathCount();
    PathElement.Builder lastElementBuilder = keyBuilder.getPath(numElements - 1).toBuilder();
    keyBuilder.setPath(numElements - 1, lastElementBuilder.setId(newId));
    return keyBuilder.build();
  }

  CommitResponse createCommitResponse(CommitRequest request, Long... allocatedIds) {
    CommitResponse.Builder req = CommitResponse.newBuilder();
    Iterator<Long> idIterator = Iterators.forArray(allocatedIds);
    for (Mutation mutation : request.getMutationsList()) {
      MutationResult.Builder mutationResult = MutationResult.newBuilder();
      com.google.datastore.v1.Entity.Builder entity;
      switch (mutation.getOperationCase()) {
        case INSERT:
          entity = mutation.getInsert().toBuilder();
          break;
        case UPSERT:
          entity = mutation.getUpsert().toBuilder();
          break;
        default:
          entity = null;
      }
      if (entity != null) {
        com.google.datastore.v1.Key key = entity.getKey();
        PathElement lastPathElem = key.getPath(key.getPathCount() - 1);
        if (lastPathElem.getIdTypeCase() == IdTypeCase.IDTYPE_NOT_SET) {
          mutationResult.setKey(completeKey(key, idIterator.next()));
        }
      }
      req.addMutationResults(mutationResult);
    }
    assertThat(idIterator.hasNext()).isFalse();
    return req.build();
  }

  CommitRequest.Builder createCommitRequest(
      Mutation.@Nullable OperationCase op, ByteString remoteTxn, Entity... entities) {
    return createCommitRequest(op, remoteTxn, Arrays.asList(entities));
  }

  CommitRequest.Builder createCommitRequest(
      Mutation.@Nullable OperationCase op, ByteString remoteTxn, List<Entity> entities) {
    CommitRequest.Builder commitRequest = CommitRequest.newBuilder();

    for (Entity entity : entities) {
      Mutation.Builder mutation = commitRequest.addMutationsBuilder();
      switch (op) {
        case INSERT:
          mutation.setInsert(DataTypeTranslator.toV1Entity(entity));
          break;
        case UPDATE:
          mutation.setUpdate(DataTypeTranslator.toV1Entity(entity));
          break;
        case UPSERT:
          mutation.setUpsert(DataTypeTranslator.toV1Entity(entity));
          break;
        case DELETE:
          mutation.setDelete(DataTypeTranslator.toV1Key(entity.getKey()));
          break;
        default:
          throw new IllegalArgumentException("unknown op: " + op);
      }
    }
    if (remoteTxn != null) {
      commitRequest.setTransaction(remoteTxn);
    } else {
      commitRequest.setMode(CommitRequest.Mode.NON_TRANSACTIONAL);
    }
    return commitRequest;
  }

  CommitRequest.Builder createCommitRequest(@Nullable ByteString remoteTxn) {
    return createCommitRequest(null, remoteTxn);
  }

  CommitRequest.Builder createPutCommitRequest(@Nullable ByteString remoteTxn, Entity... entities) {
    return createCommitRequest(Mutation.OperationCase.UPSERT, remoteTxn, entities);
  }

  CommitRequest.Builder createDeleteCommitRequest(@Nullable ByteString remoteTxn, List<Key> keys) {
    List<Entity> entities = Lists.newArrayList();
    for (Key key : keys) {
      entities.add(new Entity(key));
    }
    return createCommitRequest(Mutation.OperationCase.DELETE, remoteTxn, entities);
  }

  CommitRequest.Builder createDeleteCommitRequest(
      @Nullable ByteString remoteTxn, Entity... entities) {
    return createCommitRequest(Mutation.OperationCase.DELETE, remoteTxn, entities);
  }

  LookupRequest.Builder createLookupRequest() {
    return LookupRequest.newBuilder();
  }

  LookupRequest.Builder createLookupRequest(@Nullable ByteString remoteTxn, Entity... entities) {
    Key[] keys = new Key[entities.length];
    for (int i = 0; i < entities.length; i++) {
      keys[i] = entities[i].getKey();
    }
    return createLookupRequest(remoteTxn, keys);
  }

  LookupRequest.Builder createLookupRequest(Key... keys) {
    return createLookupRequest(null, Arrays.asList(keys));
  }

  LookupRequest.Builder createLookupRequest(@Nullable ByteString remoteTxn, Key... keys) {
    return createLookupRequest(remoteTxn, Arrays.asList(keys));
  }

  LookupRequest.Builder createLookupRequest(@Nullable ByteString remoteTxn, List<Key> keys) {
    LookupRequest.Builder req = createLookupRequest();
    for (Key key : keys) {
      req.addKeys(DataTypeTranslator.toV1Key(key));
    }
    if (remoteTxn != null) {
      req.getReadOptionsBuilder().setTransaction(remoteTxn);
    }
    return req;
  }

  AllocateIdsRequest.Builder createAllocateIdsRequest() {
    return AllocateIdsRequest.newBuilder();
  }

  AllocateIdsRequest createAllocateIdsRequest(List<Key> allocKeys) {
    AllocateIdsRequest.Builder allocIdsReq = createAllocateIdsRequest();
    for (Key key : allocKeys) {
      allocIdsReq.addKeys(DataTypeTranslator.toV1Key(key));
    }
    return allocIdsReq.build();
  }

  AllocateIdsRequest createAllocateIdsRequest(Entity... entities) {
    List<Key> keys = Lists.newArrayList();
    for (Entity entity : entities) {
      keys.add(entity.getKey());
    }
    return createAllocateIdsRequest(keys);
  }

  AllocateIdsResponse createAllocateIdsResponse(AllocateIdsRequest req, Long... allocatedIds) {
    List<com.google.datastore.v1.Key> keys = req.getKeysList();
    assertThat(allocatedIds).hasLength(keys.size());
    AllocateIdsResponse.Builder resp = AllocateIdsResponse.newBuilder();
    Iterator<Long> idIterator = Iterators.forArray(allocatedIds);
    for (com.google.datastore.v1.Key key : keys) {
      resp.addKeys(completeKey(key, idIterator.next()));
    }
    return resp.build();
  }

  QueryResultBatch.Builder createQueryResultBatch(
      MoreResultsType moreResults, int skippedResults, Entity... entities) {
    return createQueryResultBatch(moreResults, skippedResults, Arrays.asList(entities));
  }

  QueryResultBatch.Builder createQueryResultBatch(
      MoreResultsType moreResults, int skippedResults, List<Entity> entities) {
    QueryResultBatch.Builder batch =
        QueryResultBatch.newBuilder()
            .setMoreResults(moreResults)
            .setEndCursor(
                new CompiledCursor()
                    .setAbsolutePosition(
                        new IndexPosition().setKey("f" + cursorIndex.incrementAndGet()))
                    .toByteString())
            .setEntityResultType(EntityResult.ResultType.FULL);

    if (skippedResults != 0) {
      batch.setSkippedResults(skippedResults);
    }
    for (Entity entity : entities) {
      batch.addEntityResultsBuilder().setEntity(DataTypeTranslator.toV1Entity(entity));
    }
    return batch;
  }

  RunQueryResponse createRunQueryResponse(
      MoreResultsType moreResults, int skippedResults, Entity... entities) {
    return createRunQueryResponse(moreResults, skippedResults, Arrays.asList(entities));
  }

  RunQueryResponse createRunQueryResponse(
      MoreResultsType moreResults, int skippedResults, List<Entity> entities) {
    return createRunQueryResponse(createQueryResultBatch(moreResults, skippedResults, entities));
  }

  RunQueryResponse createRunQueryResponse(QueryResultBatch.Builder batch) {
    RunQueryResponse.Builder runQueryResp = RunQueryResponse.newBuilder().setBatch(batch);
    return runQueryResp.build();
  }

  @Nullable
  ByteString maybeExpectBeginTransaction() {
    if (datastoreServiceConfig.getImplicitTransactionManagementPolicy()
        == ImplicitTransactionManagementPolicy.AUTO) {
      return expectBeginTransaction();
    }
    return null;
  }

  ByteString expectBeginTransaction() {
    ByteString remoteTxn = ByteString.copyFromUtf8(Long.toString(transactionId.getAndIncrement()));
    EasyMock.expect(
            cloudDatastoreV1Client.beginTransaction(
                BeginTransactionRequest.newBuilder()
                    .setTransactionOptions(
                        com.google.datastore.v1.TransactionOptions.getDefaultInstance())
                    .build()))
        .andReturn(
            Futures.immediateFuture(
                BeginTransactionResponse.newBuilder().setTransaction(remoteTxn).build()));
    return remoteTxn;
  }

  CommitResponse expectCommit(@Nullable ByteString remoteTxn) {
    return expectCommit(createCommitRequest(remoteTxn));
  }

  CommitResponse expectCommit(CommitRequest.Builder req, Long... allocatedIds) {
    return expectCommit(req.build(), allocatedIds);
  }

  CommitResponse expectCommit(CommitRequest req, Long... allocatedIds) {
    if (req.getTransaction().isEmpty()
        && req.getMutationsList().isEmpty()
        && allocatedIds.length == 0) {
      return CommitResponse.getDefaultInstance();
    }
    if (datastoreServiceConfig.getImplicitTransactionManagementPolicy()
            == ImplicitTransactionManagementPolicy.AUTO
        || !req.getTransaction().isEmpty()) {
      assertThat(req.getMode())
          .isIn(
              ImmutableSet.of(
                  CommitRequest.Mode.MODE_UNSPECIFIED, CommitRequest.Mode.TRANSACTIONAL));
    } else {
      assertThat(req.getMode()).isEqualTo(CommitRequest.Mode.NON_TRANSACTIONAL);
    }
    CommitResponse resp = createCommitResponse(req, allocatedIds);
    expectCommitRequest(req, Futures.immediateFuture(resp));
    return resp;
  }

  void expectCommit(CommitRequest req, Code code) {
    expectCommitRequest(
        req,
        Futures.immediateFailedFuture(DatastoreApiHelper.createV1Exception(code, "message", null)));
  }

  void expectCommitRequest(CommitRequest req, Future<CommitResponse> future) {
    try {
      EasyMock.expect(cloudDatastoreV1Client.rawCommit(EasyMock.aryEq(req.toByteArray())))
          .andReturn(future);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  /** Lookup entity(s) by key and expect the entity to be returned. */
  LookupResponse expectLookup(@Nullable ByteString remoteTxn, Entity... entities) {
    LookupResponse resp = createLookupResponse(entities);
    expectLookup(createLookupRequest(remoteTxn, entities).build(), resp);
    return resp;
  }

  void expectLookup(LookupRequest req, LookupResponse resp) {
    expectLookup(req, Futures.immediateFuture(resp));
  }

  void expectLookup(LookupRequest req, Future<LookupResponse> future) {
    EasyMock.expect(cloudDatastoreV1Client.lookup(req)).andReturn(future);
  }

  void expectRollback(@Nullable ByteString remoteTxn) {
    if (remoteTxn != null) {
      EasyMock.expect(
              cloudDatastoreV1Client.rollback(
                  RollbackRequest.newBuilder().setTransaction(remoteTxn).build()))
          .andReturn(Futures.immediateFuture(RollbackResponse.newBuilder().build()));
    }
  }

  AllocateIdsResponse expectAllocateIds(List<Key> keys, Long... allocatedIds) {
    AllocateIdsRequest allocIdsReq = createAllocateIdsRequest(keys);
    AllocateIdsResponse allocIdsResp = createAllocateIdsResponse(allocIdsReq, allocatedIds);
    expectAllocateIds(allocIdsReq, Futures.immediateFuture(allocIdsResp));
    return allocIdsResp;
  }

  void expectAllocateIds(AllocateIdsRequest req, Future<AllocateIdsResponse> future) {
    EasyMock.expect(cloudDatastoreV1Client.allocateIds(req)).andReturn(future);
  }

  void expectRunQuery(Query query, FetchOptions fetchOptions, RunQueryResponse resp) {
    expectRunQuery(createRunQueryRequest(query, fetchOptions), resp);
  }

  void expectRunQuery(RunQueryRequest.Builder req, RunQueryResponse resp) {
    EasyMock.expect(cloudDatastoreV1Client.runQuery(req.build()))
        .andReturn(Futures.immediateFuture(resp));
  }

  void expectRunQuery(RunQueryRequest req, Code code) {
    EasyMock.expect(cloudDatastoreV1Client.runQuery(req))
        .andReturn(
            Futures.immediateFailedFuture(
                DatastoreApiHelper.createV1Exception(code, "message", null)));
  }

  static RunQueryRequest.Builder createRunQueryRequest(Query query, FetchOptions fetchOptions) {
    return QueryRunnerCloudDatastoreV1.toV1Query(query, fetchOptions);
  }

  Entity copyWithNewId(Entity entity, long id) {
    Key key = entity.getKey();
    Entity newEntity = new Entity(KeyFactory.createKey(key.getParent(), key.getKind(), id));
    newEntity.setPropertiesFrom(entity);
    return newEntity;
  }

  void replay() {
    EasyMock.replay(mocks.toArray());
  }

  void resetMocks() {
    mocks = ImmutableList.of(cloudDatastoreV1Client);
    EasyMock.reset(mocks.toArray());
  }

  void verify() {
    EasyMock.verify(mocks.toArray());
  }
}
