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

import static com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType.HIGH_REPLICATION;
import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withDefaults;
import static com.google.appengine.api.datastore.ImplicitTransactionManagementPolicy.AUTO;
import static com.google.appengine.api.datastore.ImplicitTransactionManagementPolicy.NONE;
import static com.google.appengine.api.datastore.ReadPolicy.Consistency.EVENTUAL;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import com.google.datastore.v1.AllocateIdsRequest;
import com.google.datastore.v1.AllocateIdsResponse;
import com.google.datastore.v1.CommitRequest;
import com.google.datastore.v1.CommitResponse;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.MutationResult;
import com.google.datastore.v1.ReadOptions;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AsyncCloudDatastoreV1ServiceImplTest extends BaseCloudDatastoreV1ServiceImplTest {

  @Before
  public void setUp() throws Exception {
    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.NONE);
  }

  @Test
  public void testImplicitTxnCreation() {
    expectBeginTransaction();

    AsyncCloudDatastoreV1ServiceImpl asyncDatastoreService =
        newAsyncDatastoreService(
            DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy(NONE),
            txnStack,
            null);
    GetOrCreateTransactionResult result = asyncDatastoreService.getOrCreateTransaction();
    assertThat(result.isNew()).isFalse();
    assertThat(result.getTransaction()).isNull();

    asyncDatastoreService =
        newAsyncDatastoreService(
            DatastoreServiceConfig.Builder.withImplicitTransactionManagementPolicy(AUTO),
            txnStack,
            null);
    result = asyncDatastoreService.getOrCreateTransaction();
    assertThat(result.isNew()).isTrue();
    assertThat(result.getTransaction()).isNotNull();
    asyncDatastoreService.defaultTxnProvider.clearAll();
  }

  @Test
  public void testGet_ImplicitTxn() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    Key key = golden.getKey();
    key.simulatePutForTesting(123L);

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(createCommitRequest(remoteTxn));
    expectLookup(remoteTxn, golden);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = ads.get(key);

    txn.get().commit();
  }

  @Test
  public void testIndependentImplicitTxns() throws Exception {
    // The goal of this test is to ensure that implicit transactions don't overlap incorrectly.
    // Try writing a new value and reading it. We expect to find the new value, which implies that
    // we are not in the same implicit transaction as the read.
    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.AUTO);

    // First, we're going to inform the mock about the behavior that we expect.

    // [A] This is the one entity that we'll be creating and modifying throughout the test.
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    // [B] We're going to store the new entity. A transaction should be implicitly started and
    // committed. The entity is assigned an ID.
    ImmutableList<ByteString> transactions = expectBeginTransaction(5);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);
    ByteString remoteTxn3 = transactions.get(2);
    ByteString remoteTxn4 = transactions.get(3);
    ByteString remoteTxn5 = transactions.get(4);
    expectAllocateIds(Arrays.asList(golden.getKey()), 123L);
    golden.getKey().simulatePutForTesting(123L);
    expectCommit(createPutCommitRequest(remoteTxn1, golden));

    // [C] Next, we're going to fetch the entity asynchronously, without blocking. We expect that a
    // transaction will be started, but not completed.
    expectLookup(remoteTxn2, golden);

    // [D] Next, we're going to fetch the entity and block on the result, so that we can get a copy
    // of the entity that has the right ID. This time, we expect that a transaction will be both
    // started and committed.
    expectLookup(remoteTxn3, golden);
    expectCommit(createCommitRequest(remoteTxn3));

    // [E] Next, we're going to modify the entity.
    golden.setProperty("aString", "bar");

    // [F] Next, we're going to update the stored version of the entity. Again, we expect that a
    // tranasction will be both started and committed.
    expectCommit(createPutCommitRequest(remoteTxn4, golden));

    // [G] Next, we're going to fetch the entity again. We expect to see the new version of the
    // entity, because the transaction that updated the entity has been committed.
    expectLookup(remoteTxn5, golden);
    expectCommit(remoteTxn5);

    // [H] Finally, we're going to resolve the future from [D] above. This should produce the *old*
    // value of the entity, since the transaction was started before any of the modifications
    // happened.
    expectCommit(remoteTxn2);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    // [A] Create the entity.
    Entity e = new Entity("Foo");
    e.setProperty("aString", "test");
    // [B] Store the entity.
    Key k = ads.put(e).get();
    // [C] Asynchronously fetch the entity, but don't block.
    Future<Entity> fe = ads.get(k);
    // [D] Synchronously fetch the entity.
    Entity e2 = ads.get(k).get();
    // [E] Modify the entity.
    e2.setProperty("aString", "bar");
    // [F] Update the stored version of the entity.
    Key k2 = ads.put(e2).get();
    // [G] Fetch the entity -- we should see the new version.
    Entity e3 = ads.get(k2).get();
    assertThat(e3).isEqualTo(e2);
    // [H] Block on the result of [C] -- we should see the old version.
    Entity e4 = fe.get();
    assertThat(e4.getProperty("aString")).isEqualTo("test");
  }

  @Test
  public void testTwoDifferentTransactionPolicies() throws Exception {
    // The goal of this test is to ensure that a Datastore with implicit transactions can coexist
    // with one without them.

    // First, we're going to inform the mock about the behavior that we expect.

    // [A] This is the one entity that we'll be creating and modifying throughout the test.
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    // [B] We're going to store the new entity using the Datastore without implicit
    // tranasctions.
    expectCommit(createPutCommitRequest(null, golden), 123L);

    // [C] The entity has now been assigned an ID, so we're going to simulate that.
    golden.getKey().simulatePutForTesting(123L);

    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.AUTO);

    // [D] Next, we're going to fetch the entity asynchronously, without blocking, using the
    // Datastore with implicit tranasctions. We expect that a transaction will be started,
    // but not completed.
    ByteString remoteTxn2 = expectBeginTransaction();
    expectLookup(remoteTxn2, golden);

    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.NONE);

    // [E] Next, using the Datastore without implicit tranasctions, we're going to fetch the
    // entity and block on the result, so that we can get a copy of the entity that has the right
    // ID.
    expectLookup(null, golden);

    // [F] Next, we're going to modify the entity.
    golden.setProperty("aString", "bar");

    // [G] Next, using the Datastore without implicit transactions, we're going to update the
    // stored version of the entity.
    expectCommit(createPutCommitRequest(null, golden));

    // [H] Next, using the Datastore without implicit transactions, we're going to fetch the entity
    // again. We expect to see the new version of the entity, because the update has already been
    // applied (it wasn't in a transaction).
    expectLookup(null, golden);

    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.AUTO);

    // [I] Finally, we're going to resolve the future from [D] above. This should produce the *old*
    // value of the entity, since the transaction was started before any of the modifications
    // happened.
    expectCommit(remoteTxn2);

    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.NONE);
    AsyncCloudDatastoreV1ServiceImpl adsNone = newAsyncDatastoreService();
    datastoreServiceConfig.implicitTransactionManagementPolicy(
        ImplicitTransactionManagementPolicy.AUTO);
    AsyncCloudDatastoreV1ServiceImpl adsAuto = newAsyncDatastoreService();
    // [A] Create the entity.
    Entity e = new Entity("Foo");
    e.setProperty("aString", "test");
    // [B] Store the entity.
    Key k = adsNone.put(e).get();
    // [D] Asynchronously fetch the entity, but don't block.
    Future<Entity> fe = adsAuto.get(k);
    // [E] Synchronously fetch the entity.
    Entity e2 = adsNone.get(k).get();
    // [F] Modify the entity.
    e2.setProperty("aString", "bar");
    // [G] Update the stored version of the entity.
    Key k2 = adsNone.put(e2).get();
    // [H] Fetch the entity -- we should see the new version.
    Entity e3 = adsNone.get(k2).get();
    assertThat(e3).isEqualTo(e2);
    // [I] Block on the result of [D] -- we should see the old version.
    Entity e4 = fe.get();
    assertThat(e4.getProperty("aString")).isEqualTo("test");
  }

  @Test
  public void testCommitTriggersGetOnAllFutures() throws Exception {
    // This test will have 2 Put calls and 1 Get call within a transaction.
    Entity putEntity1 = new Entity("Foo1");
    putEntity1.setProperty("aString", "test1");

    Entity putEntity2 = new Entity("Foo2");
    putEntity1.setProperty("aString", "test2");

    Key getKey1 = KeyFactory.createKey("Foo3", "name1");
    Entity expectedGetEntity = new Entity(getKey1);
    expectedGetEntity.setProperty("aString", "test3");

    ByteString remoteTxn = expectBeginTransaction();

    AllocateIdsRequest expectedAllocIdsReq1 = createAllocateIdsRequest(putEntity1);
    AllocateIdsRequest expectedAllocIdsReq2 = createAllocateIdsRequest(putEntity2);
    LookupRequest expectedLookupRequest = createLookupRequest(remoteTxn, getKey1).build();

    // Use TrackingFutures to allow us to assert exactly when Future.get is called.
    TrackingFuture<AllocateIdsResponse> trackingAllocIdRespFuture1 =
        new TrackingFuture<>(createAllocateIdsResponse(expectedAllocIdsReq1, 11L));
    TrackingFuture<AllocateIdsResponse> trackingAllocIdRespFuture2 =
        new TrackingFuture<>(createAllocateIdsResponse(expectedAllocIdsReq2, 22L));

    CommitRequest expectedPutRequest =
        createPutCommitRequest(
                remoteTxn, copyWithNewId(putEntity2, 22L), copyWithNewId(putEntity1, 11L))
            .build();

    CommitResponse expectedPutResponse =
        CommitResponse.newBuilder()
            .addMutationResults(MutationResult.getDefaultInstance())
            .addMutationResults(MutationResult.getDefaultInstance())
            .build();

    TrackingFuture<CommitResponse> trackingPutResponseFuture =
        new TrackingFuture<>(expectedPutResponse);
    TrackingFuture<LookupResponse> trackingLookupResponseFuture =
        new TrackingFuture<>(createLookupResponse(expectedGetEntity));

    expectAllocateIds(expectedAllocIdsReq1, trackingAllocIdRespFuture1);
    expectAllocateIds(expectedAllocIdsReq2, trackingAllocIdRespFuture2);
    expectLookup(expectedLookupRequest, trackingLookupResponseFuture);
    expectCommitRequest(expectedPutRequest, trackingPutResponseFuture);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();

    // Begin a transaction.
    Transaction txn = ads.beginTransaction().get();

    // Issue the async calls.
    Future<Key> putFuture1 = ads.put(putEntity1);
    Future<Key> putFuture2 = ads.put(putEntity2);
    Future<Entity> getFuture = ads.get(getKey1);

    // Nothing should have triggered the Future.get calls yet.
    assertThat(trackingAllocIdRespFuture1.getNumCallsToGet()).isEqualTo(0);
    assertThat(trackingAllocIdRespFuture2.getNumCallsToGet()).isEqualTo(0);
    assertThat(trackingPutResponseFuture.getNumCallsToGet()).isEqualTo(0);
    assertThat(trackingLookupResponseFuture.getNumCallsToGet()).isEqualTo(0);

    // Simulate user code getting one of the Futures.
    Key putResult2 = putFuture2.get();
    assertThat(putResult2.getId()).isEqualTo(22L);

    // That should be the only one of the four underlying Futures that had get() called.
    assertThat(trackingAllocIdRespFuture1.getNumCallsToGet()).isEqualTo(0);
    assertThat(trackingAllocIdRespFuture2.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingPutResponseFuture.getNumCallsToGet()).isEqualTo(0);
    assertThat(trackingLookupResponseFuture.getNumCallsToGet()).isEqualTo(0);

    // Commit the transaction.  This should trigger the other underlying Futures.
    txn.commit();
    assertThat(trackingAllocIdRespFuture1.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingAllocIdRespFuture2.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingPutResponseFuture.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingLookupResponseFuture.getNumCallsToGet()).isEqualTo(1);

    // User code gets the remaining results after the commit.
    Key putResult1 = putFuture1.get();
    assertThat(putResult1.getId()).isEqualTo(11L);

    Entity getResult = getFuture.get();
    assertThat(getResult).isEqualTo(expectedGetEntity);

    // This triggers an additional call on the getFuture, but no additional calls to the
    // CloudDatastoreV1Proxy.
    assertThat(trackingAllocIdRespFuture1.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingAllocIdRespFuture2.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingPutResponseFuture.getNumCallsToGet()).isEqualTo(1);
    assertThat(trackingLookupResponseFuture.getNumCallsToGet()).isEqualTo(2);
  }

  @Test
  public void testDeferredGetAndFuzzyKeyMatchingForRemoteApi() throws Exception {
    datastoreServiceConfig.maxEntityGroupsPerRpc(1); // Set this to 1 to get predictable batching.
    AsyncCloudDatastoreV1ServiceImpl asyncDatastoreService = newAsyncDatastoreService();

    Key parentKey1 = createKey(1);
    Key parentKey2 = createKey(2);

    Key childKey11 = createChildKey(parentKey1, 11);
    Key childKey12 = createChildKey(parentKey1, 12);
    Key childKey13 = createChildKey(parentKey1, 13);

    Key childKey21 = createChildKey(parentKey2, 21);
    Key childKey22 = createChildKey(parentKey2, 22);

    Entity parentEntity1 = createEntity(parentKey1, 1);
    Entity parentEntity2 = createEntity(parentKey2, 2);

    Entity childEntity11 = createEntity(childKey11, 11);
    Entity childEntity12 = createEntity(childKey12, 12);
    Entity childEntity13 = createEntity(childKey13, 13);

    Entity childEntity22 = createEntity(childKey22, 22);

    // Simulate a Remote API use case where the requested key does not match that of the returned
    // Entity.
    Key keyWithOneAppId = createKey(99);
    Key keyWithOtherAppId = toKeyWithDifferentAppId(keyWithOneAppId, "some-remote-api-app-id");
    Entity entityWithAppIdConflict = createEntity(keyWithOneAppId, 99);

    List<Key> allKeysToGet =
        ImmutableList.of(
            parentKey1,
            parentKey2,
            childKey11,
            childKey11, // Duplicate key will be omitted from requests to backend.
            childKey12,
            childKey13,
            childKey21,
            childKey22,
            keyWithOtherAppId);

    // The Keys will be grouped into 3 requests based on entity group.
    List<Key> requestKeys11 = ImmutableList.of(parentKey1, childKey11, childKey12, childKey13);
    List<Key> requestKeys21 = ImmutableList.of(parentKey2, childKey21, childKey22);
    List<Key> requestKeys31 = ImmutableList.of(keyWithOtherAppId);

    LookupResponse response11 =
        createLookupResponse(
            /* entities= */ ImmutableList.of(childEntity11, childEntity13),
            /* missingKeys= */ Collections.<Key>emptyList(),
            /* deferredKeys= */ ImmutableList.of(parentKey1, childKey12));
    LookupResponse response21 =
        createLookupResponse(
            /* entities= */ ImmutableList.of(parentEntity2),
            /* missingKeys= */ ImmutableList.of(childKey21),
            /* deferredKeys= */ ImmutableList.of(childKey22));
    LookupResponse response31 =
        createLookupResponse(
            /* entities= */ ImmutableList.of(entityWithAppIdConflict),
            /* missingKeys= */ Collections.<Key>emptyList(),
            /* deferredKeys= */ Collections.<Key>emptyList());

    // Followup requests for deferred keys.
    List<Key> requestKeys12 = ImmutableList.of(parentKey1, childKey12);
    List<Key> requestKeys22 = ImmutableList.of(childKey22);

    LookupResponse response12 =
        createLookupResponse(
            /*entities=*/ ImmutableList.of(childEntity12),
            /* missingKeys= */ Collections.<Key>emptyList(),
            /* deferredKeys= */ ImmutableList.of(parentKey1));
    LookupResponse response22 =
        createLookupResponse(
            /*entities=*/ ImmutableList.of(childEntity22),
            /* missingKeys= */ Collections.<Key>emptyList(),
            /* deferredKeys= */ Collections.<Key>emptyList());

    // One more followup request for the last deferred key.
    List<Key> requestKeys13 = ImmutableList.of(parentKey1);

    LookupResponse response13 =
        createLookupResponse(
            /* entities= */ ImmutableList.of(parentEntity1),
            /* missingKeys= */ Collections.<Key>emptyList(),
            /* deferredKeys= */ Collections.<Key>emptyList());

    expectLookup(createLookupRequest(null, requestKeys11).build(), response11);
    expectLookup(createLookupRequest(null, requestKeys21).build(), response21);
    expectLookup(createLookupRequest(null, requestKeys31).build(), response31);
    expectLookup(createLookupRequest(null, requestKeys12).build(), response12);
    expectLookup(createLookupRequest(null, requestKeys22).build(), response22);
    expectLookup(createLookupRequest(null, requestKeys13).build(), response13);

    // childKey21 was missing, so it does not appear in the result map.
    Map<Key, Entity> expectedResults =
        ImmutableMap.<Key, Entity>builder()
            .put(parentKey1, parentEntity1)
            .put(childKey11, childEntity11)
            .put(childKey12, childEntity12)
            .put(childKey13, childEntity13)
            .put(parentKey2, parentEntity2)
            .put(childKey22, childEntity22)
            .put(keyWithOtherAppId, entityWithAppIdConflict)
            .buildOrThrow();

    Map<Key, Entity> actualResults = asyncDatastoreService.get(allKeysToGet).get();
    assertThat(actualResults).isEqualTo(expectedResults);

    // Sanity check that we successfully returned a Map.Key that does not equal the Value/Entity.Key
    Entity returnedEntityWithDifferentAppId = actualResults.get(keyWithOtherAppId);
    assertThat(returnedEntityWithDifferentAppId.getKey()).isEqualTo(keyWithOneAppId);
    assertThat(keyWithOneAppId.equals(keyWithOtherAppId)).isFalse();
  }

  @Test
  public void testMultiGet_ImplicitTxn() throws Exception {
    ByteString remoteTxn = expectBeginTransaction();

    Entity golden1 = new Entity("Foo");
    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    expectLookup(remoteTxn, golden1);
    expectLookup(remoteTxn, golden2);
    expectCommit(remoteTxn);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    Future<Entity> future1 = ads.get(key1);
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = newAsyncDatastoreService().get(key2);
    // There are now 2 async ops associated with the txn
    // We'll resolve one of them explicitly with the expectation that the
    // commit will resolve the other.
    future1.get();
    txn.get().commit();
  }

  @Test
  public void testMultiGet_ImplicitTxn_MaxEntityGroupsPerRpc() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);
    testMultiGet_ImplicitTxn();
  }

  @Test
  public void testMultiGet() throws Exception {
    Entity golden1 = new Entity("Foo");
    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    expectLookup(null, golden1, golden2);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    List<Key> keys = Lists.newArrayList(key1, key2);
    Future<Map<Key, Entity>> future = ads.get(keys);
    keys.clear(); // Should not affect results;
    Map<Key, Entity> result = future.get();
    assertThat(result).containsExactly(key1, golden1, key2, golden2);
    // Test that the future result is cached and mutable (*sad face*).
    assertThat(future.get()).isSameInstanceAs(result);
    result.clear();
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void testMultiGet_MaxEntityGroupsPerRpc_HighRep_EventualConsistency() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(EVENTUAL)).maxEntityGroupsPerRpc(1);

    Entity golden1 = new Entity("Foo");
    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    LookupRequest.Builder lookupReq =
        createLookupRequest()
            .addKeys(DataTypeTranslator.toV1Key(key1))
            .addKeys(DataTypeTranslator.toV1Key(key2));
    lookupReq.getReadOptionsBuilder().setReadConsistency(ReadOptions.ReadConsistency.EVENTUAL);
    expectLookup(lookupReq.build(), createLookupResponse(golden1, golden2));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService(HIGH_REPLICATION);
    Future<Map<Key, Entity>> future1 = ads.get(Arrays.asList(key1, key2));
    future1.get();
  }

  private void doMultiGet_MaxEntityGroupsPerRpcDefault_HighRep_StrongConsistency()
      throws ExecutionException, InterruptedException {
    Entity golden1 = new Entity("Foo");
    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    // Test that more than one entity group per get is used.
    expectLookup(null, golden1, golden2);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService(HIGH_REPLICATION);
    Future<Map<Key, Entity>> future1 = ads.get(Arrays.asList(key1, key2));
    future1.get();
  }

  @Test
  public void testMultiGet_MaxEntityGroupsPerRpcDefault_HighRep_StrongConsistency()
      throws Exception {
    // default value for max_entity_groups_per_rpc
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    doMultiGet_MaxEntityGroupsPerRpcDefault_HighRep_StrongConsistency();
  }

  @Test
  public void testMultiGet_MaxEntityGroupsPerRpc_HighRep_StrongConsistency() throws Exception {
    // explicitly set max_entity_groups_per_rpc
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(10);
    doMultiGet_MaxEntityGroupsPerRpcDefault_HighRep_StrongConsistency();
  }

  @Test
  public void testDelete_ImplicitTxn() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    Key key = golden.getKey();
    key.simulatePutForTesting(123L);

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(createDeleteCommitRequest(remoteTxn, golden));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = ads.delete(key);

    txn.get().commit();
  }

  @Test
  public void testMultiDelete_ImplicitTxn() throws Exception {
    ByteString remoteTxn = expectBeginTransaction();

    Entity golden1 = new Entity("Foo");
    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    expectCommit(createDeleteCommitRequest(remoteTxn, golden1, golden2));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();

    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = ads.delete(key1);
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError1 = ads.delete(key2);

    txn.get().commit();
  }

  @Test
  public void testMultiDelete_ImplicitTxn_MaxEntityGroupsPerRpc() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);
    testMultiDelete_ImplicitTxn();
  }

  @Test
  public void testMultiDelete() throws Exception {
    Entity golden1 = new Entity("Foo");

    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    expectCommit(createDeleteCommitRequest(null, golden1, golden2));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();

    List<Key> keys = Lists.newArrayList(key1, key2);
    Future<Void> future = ads.delete(keys);
    keys.clear(); // Should not affect results;
    future.get();
  }

  @Test
  public void testMultiDelete_MaxEntityGroupsPerRpc() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);

    Entity golden1 = new Entity("Foo");

    Key key1 = golden1.getKey();
    key1.simulatePutForTesting(123L);

    Entity golden2 = new Entity("Foo");
    Key key2 = golden2.getKey();
    key2.simulatePutForTesting(456L);

    expectCommit(createDeleteCommitRequest(null, golden1));
    expectCommit(createDeleteCommitRequest(null, golden2));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Void> future1 = ads.delete(key1, key2);
    future1.get();
  }

  @Test
  public void testPut_ImplicitTxn() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    ByteString remoteTxn = expectBeginTransaction();
    expectAllocateIds(Arrays.asList(golden.getKey()), 123L);
    expectCommit(createPutCommitRequest(remoteTxn, copyWithNewId(golden, 123L)));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = newAsyncDatastoreService().put(golden);

    txn.get().commit();
  }

  @Test
  public void testMultiPut_ImplicitTxn() throws Exception {
    Entity golden1 = new Entity("Foo1");
    Entity golden2 = new Entity("Foo2");

    ByteString remoteTxn = expectBeginTransaction();
    expectAllocateIds(Arrays.asList(golden1.getKey()), 123L);
    expectAllocateIds(Arrays.asList(golden2.getKey()), 456L);
    expectCommit(
        createPutCommitRequest(
            remoteTxn, copyWithNewId(golden1, 123L), copyWithNewId(golden2, 456L)));

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();

    Future<Key> future1 = ads.put(golden1);
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = ads.put(golden2);
    // There are now 2 async ops associated with the txn
    // We'll resolve one of them explicitly with the expectation that the
    // commit will resolve the other.
    future1.get();
    txn.get().commit();
  }

  @Test
  public void testMultiPut_ImplicitTxn_MaxEntityGroupsPerRpc() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);
    testMultiPut_ImplicitTxn();
  }

  // This test is designed to catch regressions from our current behavior when multiple Entities
  // are created with the same key instance. See b/15838550.
  @Test
  public void testMultiPut_SameKey() throws Exception {
    Key baseKey = new Key("Foo");
    Entity[] entities = new Entity[3];
    for (int i = 0; i < 3; i++) {
      entities[i] = new Entity(baseKey); // Each entity has same key instance.
      entities[i].setProperty("entName", "e" + i);
    }

    AllocateIdsRequest allocIdsReq = createAllocateIdsRequest(ImmutableList.of(baseKey));
    AllocateIdsResponse allocIdsResp1 = createAllocateIdsResponse(allocIdsReq, 12L);
    AllocateIdsResponse allocIdsResp2 = createAllocateIdsResponse(allocIdsReq, 34L);
    when(cloudDatastoreV1Client.allocateIds(allocIdsReq))
        .thenReturn(Futures.immediateFuture(allocIdsResp1))
        .thenReturn(Futures.immediateFuture(allocIdsResp2));

    Entity putEnt0 = copyWithNewId(entities[0], 12L);
    Entity putEnt2 = copyWithNewId(entities[2], 34L);

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(createPutCommitRequest(remoteTxn, putEnt2, putEnt0));
    expectLookup(null, putEnt0);
    expectLookup(null, putEnt2);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();

    Future<Transaction> txn = ads.beginTransaction();
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        ads.put(entities[0]); // put an entity but don't resolve the returned future.
    Future<Key> putFut = ads.put(entities[1]); // baseKey incomplete, an id will be allocated.
    putFut.get(); // Resolve the future, this will update baseKey with entities[1]'s key.
    assertThat(baseKey.getId()).isEqualTo(34L); // Now baseKey has a complete key.
    @SuppressWarnings({"unused", "nullness"}) // go/futurereturn-lsc
    Future<?> possiblyIgnoredError1 =
        ads.put(entities[2]); // This overwrites the pending entities[1]!
    txn.get().commit();

    // This is different from V3 because keys are updated from server response
    // even if they are already complete.
    assertThat(baseKey.getId()).isEqualTo(12L); // Implicit allocation for entity 0.

    assertThat(baseKey).isEqualTo(putEnt0.getKey());
    assertThat(ads.get(putEnt0.getKey()).get().getProperty("entName")).isEqualTo("e0");
    assertThat(ads.get(putEnt2.getKey()).get().getProperty("entName")).isEqualTo("e2");
  }

  @Test
  public void testMultiPut() throws Exception {
    Entity golden1 = new Entity("Foo");
    Entity golden2 = new Entity("Foo");

    expectCommit(createPutCommitRequest(null, golden1, golden2), 123L, 456L);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    List<Entity> mutableList = Lists.newArrayList(golden1, golden2);
    Future<List<Key>> future = ads.put(mutableList);
    mutableList.clear(); // Should not affect future.
    List<Key> result = future.get();
    assertThat(result).containsExactly(golden1.getKey(), golden2.getKey()).inOrder();
    // Test that the future result is cached and mutable (*sad face*).
    assertThat(future.get()).isSameInstanceAs(result);
    result.clear();
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void testMultiPut_MaxEntityGroupsPerRpc() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);
    Entity golden1 = new Entity(KeyFactory.createKey("Foo", 1L));
    Entity golden2 = new Entity(KeyFactory.createKey(golden1.getKey(), "Foo", 2L));
    Entity golden3 = new Entity("Foo");
    Entity golden4 = new Entity("Foo", golden2.getKey());
    Entity golden5 = new Entity("Foo", golden1.getKey());
    Entity golden6 = new Entity("Foo");

    expectCommit(createPutCommitRequest(null, golden1, golden2, golden4, golden5), 4L, 5L);
    expectCommit(createPutCommitRequest(null, golden3), 3L);
    expectCommit(createPutCommitRequest(null, golden6), 6L);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();

    Future<List<Key>> future1 =
        ads.put(Arrays.asList(golden1, golden2, golden3, golden4, golden5, golden6));
    assertThat(future1.get())
        .containsExactly(
            golden1.getKey(),
            golden2.getKey(),
            golden3.getKey(),
            golden4.getKey(),
            golden5.getKey(),
            golden6.getKey())
        .inOrder();
  }

  @Test
  public void testMultiPutSplitDueToBatchWriteMax() throws Exception {
    Entity golden1 = new Entity("Foo");
    Entity golden2 = new Entity("Foo");
    Entity golden3 = new Entity("Foo");

    expectCommit(createPutCommitRequest(null, golden1), 123L);
    expectCommit(createPutCommitRequest(null, golden2), 456L);
    expectCommit(createPutCommitRequest(null, golden3), 789L);

    AsyncCloudDatastoreV1ServiceImpl ads =
        newAsyncDatastoreService(
            DatastoreServiceConfig.Builder.withDefaults().maxBatchWriteEntities(1), txnStack, null);
    Future<List<Key>> future1 = ads.put(Arrays.asList(golden1, golden2, golden3));
    assertThat(future1.get())
        .containsExactly(golden1.getKey(), golden2.getKey(), golden3.getKey())
        .inOrder();
  }

  @Test
  public void testMultiPut_MaxEntityGroupsPerRpc_SplitDueToBatchSize() throws Exception {
    datastoreServiceConfig.readPolicy(new ReadPolicy(ReadPolicy.Consistency.STRONG));
    datastoreServiceConfig.maxEntityGroupsPerRpc(1);
    Entity golden1 = new Entity("Foo1");
    Entity golden2 = new Entity("Foo2");
    Entity golden3 = new Entity("Foo3");
    Entity golden4 = new Entity("Foo4", golden1.getKey());
    Entity golden5 = new Entity("Foo5", golden2.getKey());

    // 1, 2, 4, and 5 will get batched but then split into 1, 4 and 2, 5
    // because we'll set maxBatchWriteEntities to 2.
    expectCommit(createPutCommitRequest(null, golden1, golden4), 1L, 4L);
    expectCommit(createPutCommitRequest(null, golden2, golden5), 2L, 5L);
    expectCommit(createPutCommitRequest(null, golden3), 3L);

    AsyncCloudDatastoreV1ServiceImpl ads =
        newAsyncDatastoreService(
            withDefaults().maxBatchWriteEntities(2).maxEntityGroupsPerRpc(1), txnStack, null);
    Future<List<Key>> future1 = ads.put(Arrays.asList(golden1, golden2, golden3, golden4, golden5));
    assertThat(future1.get())
        .containsExactly(
            golden1.getKey(),
            golden2.getKey(),
            golden3.getKey(),
            golden4.getKey(),
            golden5.getKey())
        .inOrder();
  }

  @Test
  public void testEmptyTxn() throws ExecutionException, InterruptedException {
    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    txn.get().commit();
  }

  @Test
  public void testMultipleEgTxn() throws ExecutionException, InterruptedException {
    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    Future<Transaction> txn = ads.beginTransaction(TransactionOptions.Builder.withXG(true));
    txn.get().commit();
  }

  @Test
  public void testSyncAndAsync()
      throws EntityNotFoundException, ExecutionException, InterruptedException {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");

    Key key = golden.getKey();
    key.simulatePutForTesting(123L);

    ByteString remoteTxn = expectBeginTransaction();
    expectLookup(remoteTxn, golden);
    expectCommit(remoteTxn);

    // Start and commit the txn with the async service but perform the get with
    // the synchronous service.
    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    DatastoreServiceImpl ds = newDatastoreService();
    Future<Transaction> txn = ads.beginTransaction();
    ds.get(key);
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn.get());
    assertThat(ads.getCurrentTransaction()).isEqualTo(txn.get());
    txn.get().commitAsync().get();
  }

  @Test
  public void testAllocateIds() throws Exception {
    Key rootKey = new Key("root1");
    Key childKey = new Key("child", new Key("parent", "foo"));
    ImmutableList<Key> incompleteKeys = ImmutableList.of(rootKey, childKey);

    expectAllocateIds(incompleteKeys, 123L, 456L);

    AsyncCloudDatastoreV1ServiceImpl ads = newAsyncDatastoreService();
    List<com.google.datastore.v1.Key> allocatedKeys = ads.allocateIds(incompleteKeys).get();
    com.google.datastore.v1.Key rootKeyV1 = allocatedKeys.get(0);
    com.google.datastore.v1.Key childKeyV1 = allocatedKeys.get(1);

    assertThat(rootKeyV1.getPathCount()).isEqualTo(1);
    assertThat(rootKeyV1.getPath(0).getId()).isEqualTo(123L);
    assertThat(childKeyV1.getPathCount()).isEqualTo(2);
    assertThat(childKeyV1.getPath(1).getId()).isEqualTo(456L);
  }

  private static void assertDedupedByKey(
      List<Entity> entities,
      List<Entity> expectedDedupedEntities,
      Multimap<Integer, Integer> expectedDedupedIndexMap) {
    Multimap<Integer, Integer> dedupedIndexMap = HashMultimap.create();
    List<Entity> dedupedEntities =
        AsyncCloudDatastoreV1ServiceImpl.dedupeByKey(entities, dedupedIndexMap);
    assertThat(dedupedEntities).containsExactlyElementsIn(expectedDedupedEntities).inOrder();
    assertThat(dedupedIndexMap).containsExactlyEntriesIn(expectedDedupedIndexMap);
  }

  @Test
  public void testDedupeByKey() throws Exception {
    Key key1 = new Key("Kind", "one");
    Key key2 = new Key("Kind", "two");

    Entity entity1a = new Entity(key1);
    entity1a.setProperty("foo", "a");
    Entity entity1b = new Entity(key1);
    entity1b.setProperty("foo", "b");
    Entity entity2a = new Entity(key2);
    entity2a.setProperty("foo", "a");
    Entity entity2b = new Entity(key2);
    entity2b.setProperty("foo", "b");

    assertDedupedByKey(
        ImmutableList.of(entity1a, entity1b),
        ImmutableList.of(entity1b),
        ImmutableMultimap.of(0, 0, 0, 1));

    assertDedupedByKey(
        ImmutableList.of(entity1b, entity1a),
        ImmutableList.of(entity1a),
        ImmutableMultimap.of(0, 0, 0, 1));

    assertDedupedByKey(
        ImmutableList.of(entity1a, entity2a, entity1a, entity2b, entity1b),
        ImmutableList.of(entity1b, entity2b),
        ImmutableMultimap.of(0, 0, 0, 2, 0, 4, 1, 1, 1, 3));
  }
}
