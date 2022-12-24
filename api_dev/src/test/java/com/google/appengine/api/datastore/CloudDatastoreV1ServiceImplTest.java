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

import static com.google.appengine.api.datastore.DatastoreServiceConfig.Builder.withReadPolicy;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withOffset;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withStartCursor;
import static com.google.common.truth.Truth.assertThat;
import static com.google.datastore.v1.QueryResultBatch.MoreResultsType.NOT_FINISHED;
import static com.google.datastore.v1.QueryResultBatch.MoreResultsType.NO_MORE_RESULTS;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.TransactionImpl.InternalTransaction;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.datastore.v1.BeginTransactionResponse;
import com.google.datastore.v1.CommitRequest;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.QueryResultBatch;
import com.google.datastore.v1.QueryResultBatch.MoreResultsType;
import com.google.datastore.v1.ReadOptions;
import com.google.datastore.v1.ReadOptions.ReadConsistency;
import com.google.datastore.v1.RunQueryRequest;
import com.google.protobuf.ByteString;
import com.google.rpc.Code;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("deprecation") // Many methods on Query.
public class CloudDatastoreV1ServiceImplTest extends BaseCloudDatastoreV1ServiceImplTest {

  // Cloud Datastore v1 does not expose a query continuation RPC.
  private Cursor expectRunQueryReq(
      Query query,
      FetchOptions fetchOptions,
      MoreResultsType moreResults,
      int skippedResults,
      Entity... entities) {
    QueryResultBatch.Builder batch = createQueryResultBatch(moreResults, skippedResults, entities);
    expectRunQuery(query, fetchOptions, createRunQueryResponse(batch));
    return new Cursor(batch.getEndCursor());
  }

  @Test
  public void testCheckQuery() {
    Query query = new Query("Foo");
    query.setKeysOnly();
    query.addProjection(new PropertyProjection("bar", null));

    assertThrows(IllegalArgumentException.class, () -> newDatastoreService().prepare(query));
  }

  @Test
  public void testPrefetchedQueryResults() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test3");
    golden3.setProperty("anInteger", 44);
    golden3.setProperty("aFloat", 62.5);

    expectRunQuery(
        query, withLimit(3), createRunQueryResponse(NO_MORE_RESULTS, 0, golden1, golden2, golden3));
    List<Entity> entities = newDatastoreService().prepare(query).asList(withLimit(3));
    int unused = entities.size(); // force all results to be pulled back

    assertThat(entities).hasSize(3);
    assertEntityEquals(golden1, entities.get(0));
    assertEntityEquals(golden2, entities.get(1));
    assertEntityEquals(golden3, entities.get(2));
  }

  @Test
  public void testSuccessfulGet() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);

    Key key = golden.getKey();
    key.simulatePutForTesting(12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest = createLookupRequest(remoteTxn, key).build();

    expectLookup(lookupRequest, createLookupResponse(golden));
    expectCommit(remoteTxn);

    Entity entity = newDatastoreService().get(key);

    assertEntityEquals(golden, entity);
  }

  @Test
  public void testSuccessfulBatchGet() throws Exception {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Bar");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest = createLookupRequest(remoteTxn, golden1, golden2).build();

    expectLookup(lookupRequest, createLookupResponse(golden1, golden2));
    expectCommit(remoteTxn);

    Map<Key, Entity> entities =
        newDatastoreService().get(Arrays.asList(golden1.getKey(), golden2.getKey()));

    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
  }

  @Test
  public void testGetThrowsEntityNotFound() throws Exception {
    Key key = KeyFactory.createKey("Foo", 12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest = createLookupRequest(remoteTxn, key).build();

    expectCommit(remoteTxn);
    expectLookup(
        lookupRequest,
        createLookupResponse(
            Collections.<Entity>emptyList(), /* foundEntities */
            Collections.singletonList(key), /* missingKeys */
            Collections.<Key>emptyList())); /* deferredKeys */


    EntityNotFoundException ex =
        assertThrows(EntityNotFoundException.class, () -> newDatastoreService().get(key));
    assertThat(ex.getKey()).isEqualTo(key);
  }

  @Test
  public void testGetWithUserTxnRollsBackOnException() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);

    Key key = golden.getKey();
    key.simulatePutForTesting(12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest = createLookupRequest(remoteTxn, key).build();

    expectLookup(
        lookupRequest,
        createLookupResponse(
            Collections.<Entity>emptyList(), /* foundEntities */
            Collections.singletonList(key), /* missingKeys */
            Collections.<Key>emptyList())); /* deferredKeys */
    expectCommit(remoteTxn);


    EntityNotFoundException ex =
        assertThrows(EntityNotFoundException.class, () -> newDatastoreService().get(key));
    assertThat(ex.getKey()).isEqualTo(key);
  }

  @Test
  public void testGetWithInactiveTxn() throws Exception {
    Entity golden = new Entity("Foo");
    Key key = golden.getKey();
    key.simulatePutForTesting(12345L);

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    DatastoreService ds = newDatastoreService();
    Transaction txn = ds.beginTransaction();
    txn.commit();
    assertThrows(IllegalStateException.class, () -> ds.get(txn, key));
  }

  @Test
  public void testPutWithInactiveTxn() throws Exception {
    Entity golden = new Entity("Foo");

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    DatastoreService ds = newDatastoreService();
    Transaction txn = ds.beginTransaction();
    txn.commit();
    assertThrows(IllegalStateException.class, () -> ds.put(txn, golden));
  }

  @Test
  public void testDeleteWithInactiveTxn() throws Exception {
    Entity golden = new Entity("Foo");
    Key key = golden.getKey();
    key.simulatePutForTesting(12345L);

    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    DatastoreService ds = newDatastoreService();
    Transaction txn = ds.beginTransaction();
    txn.commit();
    assertThrows(IllegalStateException.class, () -> ds.delete(txn, key));
  }

  @Test
  public void testCommitTxnWhileIterating() throws Exception {
    Entity parent = new Entity("Parent");
    parent.getKey().simulatePutForTesting(12345L);
    parent.setProperty("aString", "parent");

    Query query = new Query("Foo");
    query.setAncestor(parent.getKey());

    final int chunkSize = 17;

    Entity golden1 = new Entity("Foo", parent.getKey());
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo", parent.getKey());
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    ByteString txn = expectBeginTransaction();

    RunQueryRequest.Builder req =
        createRunQueryRequest(query, withChunkSize(chunkSize))
            .setReadOptions(ReadOptions.newBuilder().setTransaction(txn));
    QueryResultBatch.Builder batch1 = createQueryResultBatch(NOT_FINISHED, 0);
    expectRunQuery(req, createRunQueryResponse(batch1));
    req.getQueryBuilder().setStartCursor(batch1.getEndCursor());
    QueryResultBatch.Builder batch2 = createQueryResultBatch(NOT_FINISHED, 0, golden1, golden2);
    expectRunQuery(req, createRunQueryResponse(batch2));
    req.getQueryBuilder().setStartCursor(batch2.getEndCursor());
    expectRunQuery(req, createRunQueryResponse(NO_MORE_RESULTS, 0));
    expectCommit(txn);


    DatastoreService ds = newDatastoreService();
    Transaction started = ds.beginTransaction();
    Iterator<Entity> iterator = ds.prepare(started, query).asIterator(withChunkSize(chunkSize));

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);
    started.commit();
    assertThrows(IllegalStateException.class, iterator::next);
  }

  @Test
  public void testSuccessfulPutNewEntity() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);
    golden.setProperty("aNull", null);
    Key key = golden.getKey();

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden), 12345L);
    } else {
      expectAllocateIds(ImmutableList.of(key), 12345L);
      key.simulatePutForTesting(12345L);
      expectCommit(createPutCommitRequest(remoteTxn, golden));
      key.simulatePutForTesting(0L);
    }
    assertThat(golden.getKey().isComplete()).isFalse();

    Key newKey = newDatastoreService().put(golden);

    assertThat(newKey.isComplete()).isTrue();

    // Also make sure that it updated the existing Key in place.
    assertThat(golden.getKey()).isSameInstanceAs(newKey);
    assertThat(golden.getProperty("aNull")).isEqualTo(null);
  }

  @Test
  public void testSuccessfulPutExistingEntity() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);

    golden.getKey().simulatePutForTesting(12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    expectCommit(createPutCommitRequest(remoteTxn, golden));

    assertThat(golden.getKey().isComplete()).isTrue();

    String keyToString = golden.getKey().toString();
    Key newKey = newDatastoreService().put(golden);

    assertThat(golden.getKey().isComplete()).isTrue();
    assertThat(golden.getKey()).isSameInstanceAs(newKey);
    // Easy way to check that golden.getKey() didn't change.
    assertThat(golden.getKey().toString()).isEqualTo(keyToString);
  }

  @Test
  public void testAsyncBatchingGroupLimit_Put() throws Exception {
    datastoreServiceConfig.maxEntityGroupsPerRpc(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);
    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    Entity golden4 = new Entity("Foo", golden1.getKey());
    golden4.setProperty("aString", "test");
    golden4.setProperty("anInteger", 44);
    golden4.setProperty("aFloat", 64.3);

    List<Entity> golden = Arrays.asList(golden1, golden2, golden3, golden4);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1, golden4, golden2), 43L, 6789L);
      expectCommit(createPutCommitRequest(null, golden3), 42L);
    } else {
      // Limits ignored on commit.
      expectAllocateIds(
          Arrays.asList(golden2.getKey(), golden3.getKey(), golden4.getKey()), 6789L, 42L, 43L);
      expectCommit(
          createPutCommitRequest(
              remoteTxn,
              golden1,
              copyWithNewId(golden2, 6789L),
              copyWithNewId(golden3, 42L),
              copyWithNewId(golden4, 43L)));
    }

    assertThat(golden1.getKey().isComplete()).isTrue();
    assertThat(golden2.getKey().isComplete()).isFalse();
    assertThat(golden3.getKey().isComplete()).isFalse();
    assertThat(golden4.getKey().isComplete()).isFalse();

    List<Key> keys = datastore.put(golden);

    assertThat(golden1.getKey().isComplete()).isTrue();
    assertThat(golden2.getKey().isComplete()).isTrue();
    assertThat(golden3.getKey().isComplete()).isTrue();
    assertThat(golden4.getKey().isComplete()).isTrue();
    assertThat(keys).hasSize(4);
    assertThat(golden1.getKey()).isEqualTo(keys.get(0));
    assertThat(golden2.getKey()).isEqualTo(keys.get(1));
    assertThat(golden3.getKey()).isEqualTo(keys.get(2));
    assertThat(golden4.getKey()).isEqualTo(keys.get(3));
    assertThat(golden1.getKey().getId()).isEqualTo(12345L);
    assertThat(golden2.getKey().getId()).isEqualTo(6789L);
    assertThat(golden3.getKey().getId()).isEqualTo(42L);
    assertThat(golden4.getKey().getId()).isEqualTo(43L);
  }

  @Test
  public void testAsyncBatchingGroupLimit_Delete() throws Exception {
    datastoreServiceConfig.maxEntityGroupsPerRpc(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.getKey().simulatePutForTesting(42L);

    Entity golden4 = new Entity("Foo", golden1.getKey());
    golden4.getKey().simulatePutForTesting(43);

    List<Key> golden =
        Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey(), golden4.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createDeleteCommitRequest(null, golden1, golden4, golden2));
      expectCommit(createDeleteCommitRequest(null, golden3));
    } else {
      expectCommit(createDeleteCommitRequest(remoteTxn, golden1, golden2, golden3, golden4));
    }

    datastore.delete(golden);
  }

  @Test
  public void testAsyncBatchingGroupLimit_Get() throws Exception {
    datastoreServiceConfig.maxEntityGroupsPerRpc(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    golden3.getKey().simulatePutForTesting(42L);

    Entity golden4 = new Entity("Foo", golden1.getKey());
    golden4.setProperty("aString", "test");
    golden4.setProperty("anInteger", 44);
    golden4.setProperty("aFloat", 64.3);
    golden4.getKey().simulatePutForTesting(43L);

    // Same root path as golden2, but different entity group.
    Entity golden5;
    String old = NamespaceManager.get();
    try {
      NamespaceManager.set("some-ns");
      golden5 = new Entity("Foo");
      golden5.setProperty("aString", "test");
      golden5.setProperty("anInteger", 45);
      golden5.setProperty("aFloat", 65.3);
      golden5.getKey().simulatePutForTesting(6789L);
    } finally {
      NamespaceManager.set(old);
    }

    List<Key> golden =
        Arrays.asList(
            golden1.getKey(),
            golden2.getKey(),
            golden3.getKey(),
            golden4.getKey(),
            golden5.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectLookup(
          createLookupRequest(null, golden1, golden4, golden2).build(),
          createLookupResponse(golden1, golden4, golden2));
      expectLookup(
          createLookupRequest(null, golden3, golden5).build(),
          createLookupResponse(golden3, golden5));
    } else {
      LookupRequest lookupRequest =
          createLookupRequest(remoteTxn, golden1, golden2, golden3, golden4, golden5).build();
      expectLookup(
          lookupRequest, createLookupResponse(golden1, golden2, golden3, golden4, golden5));
    }
    expectCommit(remoteTxn);

    Map<Key, Entity> entities = datastore.get(golden);

    assertThat(entities).hasSize(5);
    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
    assertEntityEquals(golden3, entities.get(golden3.getKey()));
    assertEntityEquals(golden4, entities.get(golden4.getKey()));
    assertEntityEquals(golden5, entities.get(golden5.getKey()));
  }

  @Test
  public void testAsyncBatchingCountLimit_Put() throws Exception {
    datastoreServiceConfig.maxBatchWriteEntities(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);

    List<Entity> golden = Arrays.asList(golden1, golden2, golden3);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1, golden2), 12345L, 6789L);
      expectCommit(createPutCommitRequest(null, golden3), 42L);
    } else {
      // Batch limits ignored for a commit
      expectAllocateIds(
          Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey()), 12345L, 6789L, 42L);
      expectCommit(
          createPutCommitRequest(
              remoteTxn,
              copyWithNewId(golden1, 12345L),
              copyWithNewId(golden2, 6789L),
              copyWithNewId(golden3, 42L)));
    }

    assertThat(golden1.getKey().isComplete()).isFalse();
    assertThat(golden2.getKey().isComplete()).isFalse();
    assertThat(golden3.getKey().isComplete()).isFalse();

    List<Key> keys = datastore.put(golden);

    assertThat(golden1.getKey().isComplete()).isTrue();
    assertThat(golden2.getKey().isComplete()).isTrue();
    assertThat(golden3.getKey().isComplete()).isTrue();
    assertThat(keys).hasSize(3);
    assertThat(golden1.getKey()).isEqualTo(keys.get(0));
    assertThat(golden2.getKey()).isEqualTo(keys.get(1));
    assertThat(golden3.getKey()).isEqualTo(keys.get(2));
  }

  @Test
  public void testAsyncBatchingCountLimit_Delete() throws Exception {
    datastoreServiceConfig.maxBatchWriteEntities(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    golden3.getKey().simulatePutForTesting(42L);

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createDeleteCommitRequest(null, golden1, golden2));
      expectCommit(createDeleteCommitRequest(null, golden3));
    } else {
      expectCommit(createDeleteCommitRequest(remoteTxn, golden));
    }

    datastore.delete(golden);
  }

  @Test
  public void testAsyncBatchingCountLimit_Get() throws Exception {
    datastoreServiceConfig.maxBatchReadEntities(2);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    golden3.getKey().simulatePutForTesting(42L);

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    expectCommit(remoteTxn);

    expectLookup(remoteTxn, golden1, golden2);
    expectLookup(remoteTxn, golden3);

    Map<Key, Entity> entities = datastore.get(golden);

    assertThat(entities).hasSize(3);
    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
    assertEntityEquals(golden3, entities.get(golden3.getKey()));
  }

  @Test
  public void testAsyncBatchingSizeLimit_Put() throws Exception {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "tes"); // 1 byte smaller
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);

    List<Entity> golden = Arrays.asList(golden1, golden2, golden3);

    ByteString remoteTxn = maybeExpectBeginTransaction();

    // Batching calculations happen before project id has been populated.
    int maxSizeBytes =
        createPutCommitRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // 1 byte over
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes - 1);
    DatastoreService datastore = newDatastoreService();

    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1), 12345L);
      expectCommit(createPutCommitRequest(null, golden2, golden3), 6789L, 42L);
    } else {
      expectAllocateIds(
          Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey()), 12345L, 6789L, 42L);
      expectCommit(
          createPutCommitRequest(
              remoteTxn,
              copyWithNewId(golden1, 12345L),
              copyWithNewId(golden2, 6789L),
              copyWithNewId(golden3, 42L)));
    }

    for (Entity entity : golden) {
      assertThat(entity.getKey().isComplete()).isFalse();
    }

    List<Key> keys = datastore.put(golden);

    for (Entity entity : golden) {
      assertThat(entity.getKey().isComplete()).isTrue();
    }
    assertThat(keys).hasSize(3);
    Iterator<Key> itr = keys.iterator();
    for (Entity entity : golden) {
      assertThat(itr.next()).isEqualTo(entity.getKey());
    }

    resetMocks();

    remoteTxn = maybeExpectBeginTransaction();

    // Batching calculations happen before project id has been populated.
    maxSizeBytes =
        createPutCommitRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // exactly at max
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes);
    datastore = newDatastoreService();

    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1, golden2));
      expectCommit(createPutCommitRequest(null, golden3));
    } else {
      expectCommit(createPutCommitRequest(remoteTxn, golden1, golden2, golden3));
    }

    datastore.put(golden);
  }

  @Test
  public void testAsyncBatchingSizeLimit_Delete() throws Exception {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    golden3.getKey().simulatePutForTesting(42L); // At least 1 byte smaller

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();

    // Batching calculations happen before project id has been populated.
    int maxSizeBytes =
        createDeleteCommitRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // 1 byte over
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes - 1);
    DatastoreService datastore = newDatastoreService();

    if (remoteTxn == null) {
      expectCommit(createDeleteCommitRequest(null, golden1));
      expectCommit(createDeleteCommitRequest(null, golden2, golden3));
    } else {
      expectCommit(createDeleteCommitRequest(remoteTxn, golden1, golden2, golden3));
    }
    datastore.delete(golden);

    resetMocks();

    remoteTxn = maybeExpectBeginTransaction();

    // Batching calculations happen before project id has been populated.
    maxSizeBytes =
        createDeleteCommitRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // exactly the max
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes);
    datastore = newDatastoreService();
    if (remoteTxn == null) {
      expectCommit(createDeleteCommitRequest(null, golden1, golden2));
      expectCommit(createDeleteCommitRequest(null, golden3));
    } else {
      expectCommit(createDeleteCommitRequest(remoteTxn, golden1, golden2, golden3));
    }

    datastore.delete(golden);
  }

  @Test
  public void testAsyncBatchingEmptyRequest() throws Exception {
    DatastoreService datastore = newDatastoreService();

    expectCommit(maybeExpectBeginTransaction());
    expectCommit(createPutCommitRequest(maybeExpectBeginTransaction()));
    expectCommit(createDeleteCommitRequest(maybeExpectBeginTransaction()));

    // No calls besides transactions are actually made
    datastore.get(Arrays.<Key>asList());
    datastore.put(Arrays.<Entity>asList());
    datastore.delete(Arrays.<Key>asList());
  }

  @Test
  public void testAsyncBatchingSizeLimit_Get() throws Exception {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test");
    golden3.setProperty("anInteger", 43);
    golden3.setProperty("aFloat", 63.3);
    golden3.getKey().simulatePutForTesting(42L); // At least 1 byte smaller

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey(), golden3.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest1 = createLookupRequest(remoteTxn, golden1).build();
    LookupRequest lookupRequest2 = createLookupRequest(remoteTxn, golden2, golden3).build();
    expectCommit(remoteTxn);

    // Batching calculations happen before project id has been populated.
    int maxSizeBytes =
        createLookupRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // 1 byte over
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes - 1); // 1 byte over.
    DatastoreService datastore = newDatastoreService();

    expectLookup(lookupRequest1, createLookupResponse(golden1));
    expectLookup(lookupRequest2, createLookupResponse(golden2, golden3));

    Map<Key, Entity> entities = datastore.get(golden);

    assertThat(entities).hasSize(3);
    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
    assertEntityEquals(golden3, entities.get(golden3.getKey()));

    resetMocks();

    remoteTxn = maybeExpectBeginTransaction();
    lookupRequest1 = createLookupRequest(remoteTxn, golden1, golden2).build();
    lookupRequest2 = createLookupRequest(remoteTxn, golden3).build();
    expectCommit(remoteTxn);

    // Batching calculations happen before project id has been populated.
    maxSizeBytes =
        createLookupRequest(remoteTxn, golden1, golden2)
            .clearProjectId()
            .build()
            .getSerializedSize();

    // exactly the max
    datastoreServiceConfig.maxRpcSizeBytes(maxSizeBytes);
    datastore = newDatastoreService();

    expectLookup(lookupRequest1, createLookupResponse(golden1, golden2));
    expectLookup(lookupRequest2, createLookupResponse(golden3));

    entities = datastore.get(golden);

    assertThat(entities).hasSize(3);
    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
    assertEntityEquals(golden3, entities.get(golden3.getKey()));
  }

  @Test
  public void testAsyncBatchingOverSizeLimit_Put() throws Exception {
    datastoreServiceConfig.maxRpcSizeBytes(0);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo1");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);

    Entity golden2 = new Entity("Foo2");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);

    List<Entity> golden = Arrays.asList(golden1, golden2);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1), 12345L);
      expectCommit(createPutCommitRequest(null, golden2), 6789L);
    } else {
      expectAllocateIds(Arrays.asList(golden1.getKey()), 12345L);
      expectAllocateIds(Arrays.asList(golden2.getKey()), 6789L);
      expectCommit(
          createPutCommitRequest(
              remoteTxn, copyWithNewId(golden1, 12345L), copyWithNewId(golden2, 6789L)));
    }

    assertThat(golden1.getKey().isComplete()).isFalse();
    assertThat(golden2.getKey().isComplete()).isFalse();

    List<Key> keys = datastore.put(golden);

    assertThat(golden1.getKey().isComplete()).isTrue();
    assertThat(golden2.getKey().isComplete()).isTrue();
    assertThat(keys).hasSize(2);
    assertThat(golden1.getKey()).isEqualTo(keys.get(0));
    assertThat(golden2.getKey()).isEqualTo(keys.get(1));
  }

  @Test
  public void testAsyncBatchingOverSizeLimit_Delete() throws Exception {
    datastoreServiceConfig.maxRpcSizeBytes(0);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createDeleteCommitRequest(null, golden1));
      expectCommit(createDeleteCommitRequest(null, golden2));
    } else {
      expectCommit(createDeleteCommitRequest(remoteTxn, golden));
    }

    datastore.delete(golden);
  }

  @Test
  public void testAsyncBatchingOverSizeLimit_Get() throws Exception {
    datastoreServiceConfig.maxRpcSizeBytes(0);
    DatastoreService datastore = newDatastoreService();

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 41);
    golden1.setProperty("aFloat", 61.3);
    golden1.getKey().simulatePutForTesting(12345L);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);
    golden2.getKey().simulatePutForTesting(6789L);

    List<Key> golden = Arrays.asList(golden1.getKey(), golden2.getKey());

    ByteString remoteTxn = maybeExpectBeginTransaction();
    LookupRequest lookupRequest1 = createLookupRequest(remoteTxn, golden1).build();
    LookupRequest lookupRequest2 = createLookupRequest(remoteTxn, golden2).build();
    expectCommit(remoteTxn);

    expectLookup(lookupRequest1, createLookupResponse(golden1));
    expectLookup(lookupRequest2, createLookupResponse(golden2));

    Map<Key, Entity> entities = datastore.get(golden);

    assertThat(entities).hasSize(2);
    assertEntityEquals(golden1, entities.get(golden1.getKey()));
    assertEntityEquals(golden2, entities.get(golden2.getKey()));
  }

  @Test
  public void testSuccessfulBatchPut() throws Exception {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    if (remoteTxn == null) {
      expectCommit(createPutCommitRequest(null, golden1, golden2), 12345L, 6789L);
    } else {
      expectAllocateIds(Arrays.asList(golden1.getKey(), golden2.getKey()), 12345L, 6789L);
      expectCommit(
          createPutCommitRequest(
              remoteTxn, copyWithNewId(golden1, 12345L), copyWithNewId(golden2, 6789L)));
    }

    assertThat(golden1.getKey().isComplete()).isFalse();
    assertThat(golden2.getKey().isComplete()).isFalse();

    List<Key> keys = newDatastoreService().put(Arrays.asList(golden1, golden2));

    assertThat(golden1.getKey().isComplete()).isTrue();
    assertThat(golden2.getKey().isComplete()).isTrue();
    assertThat(keys).hasSize(2);
    assertThat(golden1.getKey()).isEqualTo(keys.get(0));
    assertThat(golden2.getKey()).isEqualTo(keys.get(1));
  }

  @Test
  public void testPutThrowsBadRequestException() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> putAndReturnError(Code.INVALID_ARGUMENT));
  }

  @Test
  public void testPutThrowsGeneralError() throws Exception {
    assertThrows(DatastoreFailureException.class, () -> putAndReturnError(Code.INTERNAL));
  }

  @Test
  public void testPutThrowsTimeoutException() throws Exception {
    assertThrows(DatastoreTimeoutException.class, () -> putAndReturnError(Code.DEADLINE_EXCEEDED));
  }

  private void putAndReturnError(Code code) throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);

    golden.getKey().simulatePutForTesting(12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();
    CommitRequest putRequest = createPutCommitRequest(remoteTxn, golden).build();
    expectCommit(putRequest, code);
    newDatastoreService().put(golden);
    throw new AssertionError("should have thrown an exception");
  }

  @Test
  public void testSuccessfulDelete() throws Exception {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);

    Key key = golden.getKey();
    key.simulatePutForTesting(12345L);

    ByteString remoteTxn = maybeExpectBeginTransaction();

    expectCommit(createDeleteCommitRequest(remoteTxn, ImmutableList.of(key)));

    newDatastoreService().delete(key);
  }

  @Test
  public void testSuccessfulDeleteMultipleKeys() {
    Entity golden = new Entity("Foo");
    golden.setProperty("aString", "test");
    golden.setProperty("anInteger", 42);
    golden.setProperty("aFloat", 62.3);
    golden.getKey().simulatePutForTesting(12345L);

    Entity silver = new Entity("Yam");
    silver.setProperty("aString", "test2");
    silver.setProperty("anInteger", 43);
    silver.setProperty("aFloat", 62.4);
    silver.getKey().simulatePutForTesting(12346L);

    expectCommit(createDeleteCommitRequest(maybeExpectBeginTransaction(), golden, silver));

    newDatastoreService().delete(golden.getKey(), silver.getKey());
  }

  @Test
  public void testDeleteNonexistentKeyIsNoop() throws Exception {
    ByteString remoteTxn = maybeExpectBeginTransaction();
    Key key = new Key("Foo", "name");
    expectCommit(createDeleteCommitRequest(remoteTxn, ImmutableList.of(key)));

    newDatastoreService().delete(key);
  }

  @Test
  public void testQueryAllOfKind() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Entity golden3 = new Entity("Foo");
    golden3.setProperty("aString", "test3");
    golden3.setProperty("anInteger", 44);
    golden3.setProperty("aFloat", 62.5);

    expectRunQuery(
        query, withLimit(3), createRunQueryResponse(NO_MORE_RESULTS, 0, golden1, golden2, golden3));

    List<Entity> entities = newDatastoreService().prepare(query).asList(withLimit(3));
    int unused = entities.size(); // force all results to be pulled back

    assertThat(entities).hasSize(3);
    assertEntityEquals(golden1, entities.get(0));
    assertEntityEquals(golden2, entities.get(1));
    assertEntityEquals(golden3, entities.get(2));
  }

  @Test
  public void testUnboundedQueryIterator() throws Exception {
    Query query = new Query("Foo");

    Entity[][] entities = new Entity[][] {new Entity[20], new Entity[20], new Entity[20]};
    for (int i = 0; i < 60; i++) {
      Entity golden = new Entity("Foo");
      golden.setProperty("aString", "test" + i);
      golden.setProperty("anInteger", i);
      golden.setProperty("aFloat", i / 1.0);
      entities[i / 20][i % 20] = golden;
    }

    Cursor cursor = expectRunQueryReq(query, withChunkSize(20), NOT_FINISHED, 0);
    for (int i = 0; i < 3; i++) {
      cursor =
          expectRunQueryReq(
              query, withChunkSize(20).startCursor(cursor), NOT_FINISHED, 0, entities[i]);
    }
    expectRunQueryReq(query, withChunkSize(20).startCursor(cursor), NO_MORE_RESULTS, 0);

    Iterator<Entity> iterator = newDatastoreService().prepare(query).asIterator(withChunkSize(20));

    for (Entity[] entitiesArr : entities) {
      for (Entity golden : entitiesArr) {
        assertThat(iterator.hasNext()).isTrue();
        assertEntityEquals(iterator.next(), golden);
      }
    }

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testBoundedQueryIterator() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Cursor cursor = expectRunQueryReq(query, withLimit(2), NOT_FINISHED, 0, golden1);
    expectRunQueryReq(query, withLimit(1).startCursor(cursor), NO_MORE_RESULTS, 0, golden2);

    Iterator<Entity> iterator = newDatastoreService().prepare(query).asIterator(withLimit(2));

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden2);

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testBoundedQueryIteratorWithCustomChunkSize() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Cursor cursor1 = expectRunQueryReq(query, withLimit(2).chunkSize(1), NOT_FINISHED, 0);
    // Called three times: twice to retrieve 1 item each, and once more to get
    // NO_MORE_RESULTS.
    Cursor cursor2 =
        expectRunQueryReq(
            query, withLimit(2).startCursor(cursor1).chunkSize(1), NOT_FINISHED, 0, golden1);
    Cursor cursor3 =
        expectRunQueryReq(
            query, withLimit(1).startCursor(cursor2).chunkSize(1), NOT_FINISHED, 0, golden2);
    // We do another query even though we have already returned the requested results. Huh.
    expectRunQueryReq(query, withLimit(0).startCursor(cursor3).chunkSize(1), NO_MORE_RESULTS, 0);


    FetchOptions fs = withLimit(2).chunkSize(1);
    Iterator<Entity> iterator = newDatastoreService().prepare(query).asIterator(fs);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden2);

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testQueryIteratorWithOffset() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Cursor cursor1 = expectRunQueryReq(query, withOffset(1), NOT_FINISHED, 1);
    expectRunQueryReq(query, withStartCursor(cursor1), NO_MORE_RESULTS, 0, golden1, golden2);


    Iterator<Entity> iterator = newDatastoreService().prepare(query).asIterator(withOffset(1));

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden2);

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testQueryIteratorWithOffsetAndLimit() throws Exception {
    Query query = new Query("Foo");

    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo");
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    Cursor cursor1 = expectRunQueryReq(query, withOffset(1).limit(3), NOT_FINISHED, 0);
    // Testing less than asked for returned although more exist case
    Cursor cursor2 =
        expectRunQueryReq(
            query, withOffset(1).limit(3).startCursor(cursor1), NOT_FINISHED, 1, golden1);
    expectRunQueryReq(query, withLimit(2).startCursor(cursor2), NO_MORE_RESULTS, 1, golden2);

    Iterator<Entity> iterator =
        newDatastoreService().prepare(query).asIterator(withOffset(1).limit(3));

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden2);

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void testQueryChaining() {
    Query q = new Query("Foo");
    assertThat(q.addFilter("a", Query.FilterOperator.EQUAL, "b")).isSameInstanceAs(q);
    assertThat(q.addSort("a")).isSameInstanceAs(q);
    assertThat(q.addSort("a", Query.SortDirection.ASCENDING)).isSameInstanceAs(q);
    assertThat(q.setAncestor(KeyFactory.createKey("Foo", "d"))).isSameInstanceAs(q);
  }

  @Test
  public void testRunQuery_AsyncException() {
    Query query = new Query("blarg");
    query.addFilter("p1", Query.FilterOperator.EQUAL, 22);
    query.addFilter("p2", Query.FilterOperator.GREATER_THAN, 33);
    DatastoreService ds = newDatastoreService();

    Cursor cursor = expectRunQueryReq(query, withDefaults(), NOT_FINISHED, 0, new Entity("Foo"));

    expectRunQuery(
        createRunQueryRequest(query, withStartCursor(cursor)).build(), Code.DEADLINE_EXCEEDED);

    Iterator<Entity> itr = ds.prepare(query).asIterator();
    itr.next();
    assertThrows(DatastoreTimeoutException.class, itr::next);
  }

  @Test
  public void testQueryOffset() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    Cursor cursor1 = expectRunQueryReq(query, withOffset(6).compile(true), NOT_FINISHED, 3);
    Cursor cursor2 =
        expectRunQueryReq(query, withOffset(3).compile(true).startCursor(cursor1), NOT_FINISHED, 3);
    expectRunQueryReq(
        query,
        withStartCursor(cursor2).compile(true),
        NO_MORE_RESULTS,
        0,
        new Entity("Foo"),
        new Entity("Foo"),
        new Entity("Foo"));

    QueryResultIteratorImpl itr =
        (QueryResultIteratorImpl)
            newDatastoreService().prepare(query).asQueryResultIterator(withOffset(6));
    assertThat(itr.getNumSkipped()).isEqualTo(6);
    assertThat(Lists.newArrayList(itr)).hasSize(3);
  }

  @Test
  public void testQueryOffsetLimit() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    Cursor cursor1 = expectRunQueryReq(query, withOffset(6).limit(5), NOT_FINISHED, 0);
    Cursor cursor2 =
        expectRunQueryReq(query, withOffset(6).limit(5).startCursor(cursor1), NOT_FINISHED, 3);
    Cursor cursor3 =
        expectRunQueryReq(
            query,
            withOffset(3).limit(5).startCursor(cursor2),
            NOT_FINISHED,
            3,
            new Entity("Foo"),
            new Entity("Foo"),
            new Entity("Foo"));
    expectRunQueryReq(
        query, withLimit(2).startCursor(cursor3), NO_MORE_RESULTS, 3, new Entity("Foo"));

    QueryResultList<Entity> list =
        newDatastoreService().prepare(query).asQueryResultList(withOffset(6).limit(5));
    assertThat(list).hasSize(4);
  }

  @Test
  public void testQueryOffsetNoProgressDatastore() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    Cursor cursor1 = expectRunQueryReq(query, withOffset(6), NOT_FINISHED, 0);
    Cursor cursor2 = expectRunQueryReq(query, withOffset(6).startCursor(cursor1), NOT_FINISHED, 3);
    Cursor cursor3 = expectRunQueryReq(query, withOffset(3).startCursor(cursor2), NOT_FINISHED, 0);
    expectRunQueryReq(query, withOffset(3).startCursor(cursor3), NO_MORE_RESULTS, 0);

    QueryResultIteratorImpl itr =
        (QueryResultIteratorImpl)
            newDatastoreService().prepare(query).asQueryResultIterator(withOffset(6));
    assertThat(itr.getNumSkipped()).isEqualTo(3);
    assertThat(Lists.newArrayList(itr)).isEmpty();
  }

  @Test
  public void testQueryOffsetInterweaved() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    Cursor cursor1 = expectRunQueryReq(query, withOffset(6).compile(true), NOT_FINISHED, 0);
    Cursor cursor2 =
        expectRunQueryReq(query, withOffset(6).startCursor(cursor1).compile(true), NOT_FINISHED, 3);
    Cursor cursor3 =
        expectRunQueryReq(
            query,
            withOffset(3).startCursor(cursor2).compile(true),
            NOT_FINISHED,
            3,
            new Entity("Foo"),
            new Entity("Foo"),
            new Entity("Foo"));
    expectRunQueryReq(query, withStartCursor(cursor3).compile(true), NO_MORE_RESULTS, 3);

    QueryResultIteratorImpl itr =
        (QueryResultIteratorImpl)
            newDatastoreService().prepare(query).asQueryResultIterator(withOffset(6));
    assertThat(itr.getNumSkipped()).isEqualTo(6);
    assertThat(Lists.newArrayList(itr)).hasSize(3);
    assertThat(itr.getNumSkipped()).isEqualTo(9);
  }

  @Test
  public void testCountQuery() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(query, withOffset(1000).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    assertThat(newDatastoreService().prepare(query).countEntities()).isEqualTo(3);
  }

  @Test
  public void testCountQueryWithDefaults() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(
        query, withOffset(Integer.MAX_VALUE).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    assertThat(newDatastoreService().prepare(query).countEntities(withDefaults())).isEqualTo(3);
  }

  @Test
  public void testCountQueryWithOffset() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(
        query, withOffset(Integer.MAX_VALUE).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 8));
    assertThat(newDatastoreService().prepare(query).countEntities(withOffset(5))).isEqualTo(3);
  }

  @Test
  public void testCountQueryWithLmit() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(query, withOffset(5).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    assertThat(newDatastoreService().prepare(query).countEntities(withLimit(5))).isEqualTo(3);
  }

  @Test
  public void testCountQueryWithOffsetAndLimit() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(query, withOffset(9).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    expectRunQuery(query, withOffset(9).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    expectRunQuery(query, withOffset(9).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    assertThat(newDatastoreService().prepare(query).countEntities(withLimit(5).offset(4)))
        .isEqualTo(0);
    assertThat(newDatastoreService().prepare(query).countEntities(withLimit(6).offset(3)))
        .isEqualTo(0);
    assertThat(newDatastoreService().prepare(query).countEntities(withLimit(7).offset(2)))
        .isEqualTo(1);
  }

  @Test
  public void testCountQueryWithOffsetAndLimit_Overflow() throws Exception {
    Query query = new Query("Foo");
    query.addFilter("prop1", Query.FilterOperator.EQUAL, "value1");
    query.addFilter("prop2", Query.FilterOperator.EQUAL, "value2");

    expectRunQuery(
        query,
        withOffset(Integer.MAX_VALUE).limit(0),
        // causes (skipped - offset) to overflow
        createRunQueryResponse(NO_MORE_RESULTS, -10));
    assertThat(
            newDatastoreService()
                .prepare(query)
                .countEntities(
                    // causes (offset + limit) to overflow
                    withLimit(Integer.MAX_VALUE - 2).offset(Integer.MAX_VALUE - 2)))
        .isEqualTo(0);
  }

  @Test
  public void testAncestorQuery() throws Exception {
    Entity parent = new Entity("Parent");
    parent.getKey().simulatePutForTesting(12345L);
    parent.setProperty("aString", "parent");

    Query query = new Query("Foo");
    query.setAncestor(parent.getKey());

    final int chunkSize = 17;

    Entity golden1 = new Entity("Foo", parent.getKey());
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Foo", parent.getKey());
    golden2.setProperty("aString", "test2");
    golden2.setProperty("anInteger", 43);
    golden2.setProperty("aFloat", 62.4);

    ByteString txn = expectBeginTransaction();
    if (txn != null) {
      // TODO This test will start to fail when we address http://b/1943558
      // Uncomment to fix.
      // queryProto.setTransaction(txn);
    }
    expectCommit(txn);

    Cursor cursor1 = expectRunQueryReq(query, withChunkSize(chunkSize), NOT_FINISHED, 0);
    // Called twice: once to retrieve 2 items, and once more to get
    // NO_MORE_RESULTS.
    Cursor cursor2 =
        expectRunQueryReq(
            query,
            withStartCursor(cursor1).chunkSize(chunkSize),
            NOT_FINISHED,
            0,
            golden1,
            golden2);
    expectRunQueryReq(query, withStartCursor(cursor2).chunkSize(chunkSize), NO_MORE_RESULTS, 0);

    DatastoreService ds = newDatastoreService();
    Transaction started = ds.beginTransaction();
    Iterator<Entity> iterator = ds.prepare(query).asIterator(withChunkSize(chunkSize));

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden1);

    assertThat(iterator.hasNext()).isTrue();
    assertEntityEquals(iterator.next(), golden2);

    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
    started.commit();
  }

  @Test
  public void testCountAncestorQuery_NoCurrentTxn() throws Exception {
    Query query = new Query("Foo");
    query.setAncestor(KeyFactory.createKey("Foo", "name"));

    expectRunQuery(query, withOffset(100).limit(0), createRunQueryResponse(NO_MORE_RESULTS, 3));
    DatastoreService ds = newDatastoreService();
    assertThat(ds.prepare(query).countEntities(withLimit(100))).isEqualTo(3);
  }

  @Test
  public void testAncestorQueryWithCommitedTxn() throws Exception {
    Query query = new Query("Foo");
    query.setAncestor(KeyFactory.createKey("Foo", "name"));

    ByteString txn = expectBeginTransaction();
    expectCommit(txn);
    DatastoreService ds = newDatastoreService();
    Transaction started = ds.beginTransaction();
    started.commit();
    assertThrows(IllegalStateException.class, () -> ds.prepare(started, query));
  }

  @Test
  public void testAncestorQueryWithRolledBackTxn() throws Exception {
    Query query = new Query("Foo");
    query.setAncestor(KeyFactory.createKey("Foo", "name"));

    ByteString txn = expectBeginTransaction();
    expectRollback(txn);
    DatastoreService ds = newDatastoreService();
    Transaction started = ds.beginTransaction();
    started.rollback();
    assertThrows(IllegalStateException.class, () -> ds.prepare(started, query));
  }

  @Test
  public void testAsSingleEntity_NoResults() {
    Query query = new Query("Foo");

    expectRunQuery(query, withLimit(2), createRunQueryResponse(NO_MORE_RESULTS, 0));

    assertThat(newDatastoreService().prepare(query).asSingleEntity()).isNull();
  }

  @Test
  public void testQueryIteratorUseOfHasMoreEntities() {
    QueryResultIteratorImpl itr =
        new QueryResultIteratorImpl(
            null,
            new QueryResultsSource() {
              @Override
              public Cursor loadMoreEntities(List<Entity> buffer, List<Cursor> cursorBuffer) {
                throw new AssertionError("should never be called");
              }

              @Override
              public Cursor loadMoreEntities(
                  int numberToLoad, List<Entity> buffer, List<Cursor> cursorBuffer) {
                throw new AssertionError("should never be called");
              }

              @Override
              public boolean hasMoreEntities() {
                return false;
              }

              @Override
              public int getNumSkipped() {
                throw new AssertionError();
              }

              @Override
              public List<Index> getIndexList() {
                throw new AssertionError("should never be called");
              }
            },
            withDefaults(),
            null);
    assertThat(itr.hasNext()).isFalse();
  }

  @Test
  public void testIteratorBehaviorOnNoProgress_CursorChanged() {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Query query = new Query("Foo");

    Cursor cursor1 = expectRunQueryReq(query, withDefaults(), NOT_FINISHED, 0);
    Cursor cursor2 = expectRunQueryReq(query, withStartCursor(cursor1), NOT_FINISHED, 0, golden1);
    Cursor cursor3 = expectRunQueryReq(query, withStartCursor(cursor2), NOT_FINISHED, 0, golden1);
    // Returned no results, so we keep asking until we are told there are no more results.
    Cursor cursor4 = expectRunQueryReq(query, withStartCursor(cursor3), NOT_FINISHED, 0);
    expectRunQueryReq(query, withStartCursor(cursor4), NO_MORE_RESULTS, 0);

    List<Entity> entities = newDatastoreService().prepare(query).asList(withDefaults());
    int unused = entities.size(); // force all results to get pulled back

    assertThat(entities).hasSize(2);
    assertEntityEquals(golden1, entities.get(0));
    assertEntityEquals(golden1, entities.get(1));
  }

  @Test
  public void testIteratorBehaviorOnNoProgress_CursorUnchanged() {
    Query query = new Query("Foo");

    // First runQuery() returns no results and NOT_FINISHED.
    Cursor cursor1 = expectRunQueryReq(query, withDefaults(), NOT_FINISHED, 0);

    // Second runQuery() returns no results, NOT_FINISHED, and the same end
    // cursor as the first runQuery().
    QueryResultBatch.Builder batch2 = createQueryResultBatch(NOT_FINISHED, 0);
    batch2.setEndCursor(cursor1.toByteString());
    expectRunQuery(query, withStartCursor(cursor1), createRunQueryResponse(batch2));

    List<Entity> entities = newDatastoreService().prepare(query).asList(withDefaults());
    // force all results to get pulled back
    assertThrows(DatastoreTimeoutException.class, entities::size);
  }

  @Test
  public void testIteratorBehaviorOnNoProgress_EmptyCursorInFirstResponse() {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Query query = new Query("Foo");

    // First runQuery() returns 1 result, NOT_FINISHED, and no end cursor
    // (there are a handful of queries that don't return cursors).
    QueryResultBatch.Builder batch1 = createQueryResultBatch(NOT_FINISHED, 0, golden1);
    batch1.clearEndCursor();
    expectRunQuery(query, withDefaults(), createRunQueryResponse(batch1));

    List<Entity> entities = newDatastoreService().prepare(query).asList(withDefaults());
    // force all results to get pulled back
    assertThrows(IllegalStateException.class, entities::size);
  }

  @Test
  public void testIteratorBehaviorOnNoProgress_EmptyCursorInSecondResponse() {
    Query query = new Query("Foo");

    // First runQuery() returns no results and NOT_FINISHED.
    Cursor cursor1 = expectRunQueryReq(query, withDefaults(), NOT_FINISHED, 0);

    // Second runQuery() returns no results, NOT_FINISHED, and no end cursor.
    QueryResultBatch.Builder batch2 = createQueryResultBatch(NOT_FINISHED, 0);
    batch2.clearEndCursor();
    expectRunQuery(query, withStartCursor(cursor1), createRunQueryResponse(batch2));

    List<Entity> entities = newDatastoreService().prepare(query).asList(withDefaults());
    // force all results to get pulled back
    assertThrows(IllegalStateException.class, entities::size);
  }

  @Test
  public void testAsSingleEntity_OneResult() {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Query query = new Query("Foo");

    expectRunQuery(query, withLimit(2), createRunQueryResponse(NO_MORE_RESULTS, 0, golden1));

    assertEntityEquals(golden1, newDatastoreService().prepare(query).asSingleEntity());
  }

  @Test
  public void testAsSingleEntity_TwoResults() {
    Entity golden1 = new Entity("Foo");
    golden1.setProperty("aString", "test");
    golden1.setProperty("anInteger", 42);
    golden1.setProperty("aFloat", 62.3);

    Entity golden2 = new Entity("Bar");
    golden2.setProperty("aString", "test");
    golden2.setProperty("anInteger", 42);
    golden2.setProperty("aFloat", 62.3);

    Query query = new Query("Foo");

    expectRunQuery(
        query, withLimit(2), createRunQueryResponse(NO_MORE_RESULTS, 0, golden1, golden2));

    assertThrows(
        PreparedQuery.TooManyResultsException.class,
        () -> newDatastoreService().prepare(query).asSingleEntity());
  }

  Future<BeginTransactionResponse> newTxnFuture(ByteString txHandle) {
    return new FutureHelper.FakeFuture<BeginTransactionResponse>(
        BeginTransactionResponse.newBuilder().setTransaction(txHandle).build());
  }

  @Test
  public void testAncestorQuery_ExplicitTransaction() {
    Entity entity = new Entity("Foo", "name");
    Query query = new Query(entity.getKey());
    ByteString txHandle = ByteString.copyFromUtf8("123");

    RunQueryRequest.Builder runQueryReq = createRunQueryRequest(query, withLimit(2));
    runQueryReq.getReadOptionsBuilder().setTransaction(txHandle);
    expectRunQuery(runQueryReq, createRunQueryResponse(NO_MORE_RESULTS, 0, entity));

    DatastoreService ds = newDatastoreService();

    InternalTransaction internalTransaction =
        InternalTransactionCloudDatastoreV1.create(
            cloudDatastoreV1Client, newTxnFuture(txHandle), false /* isReadOnly */);

    Transaction txn = new TransactionImpl(APP_ID.toString(), null, null, true, internalTransaction);
    assertThat(ds.prepare(txn, query).asSingleEntity().getKey()).isEqualTo(entity.getKey());
  }

  @Test
  public void testCurrentTransaction_Simple() {
    ByteString remoteTxn = expectBeginTransaction();
    expectCommit(remoteTxn);

    DatastoreService ds = newDatastoreService();
    Transaction txn = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn);
    txn.commit();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testCurrentTransaction_Nested_CommitCommit() {
    ImmutableList<ByteString> transactions = expectBeginTransaction(2);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);

    expectCommit(remoteTxn1);
    expectCommit(remoteTxn2);

    DatastoreService ds = newDatastoreService();
    Transaction txn1 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    Transaction txn2 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn2.commit();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    txn1.commit();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testCurrentTransaction_Nested_CommitRollback() {
    ImmutableList<ByteString> transactions = expectBeginTransaction(2);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);

    expectRollback(remoteTxn1);
    expectCommit(remoteTxn2);

    DatastoreService ds = newDatastoreService();
    Transaction txn1 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    Transaction txn2 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn2.commit();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    txn1.rollback();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testCurrentTransaction_Nested_RollbackCommit() {
    ImmutableList<ByteString> transactions = expectBeginTransaction(2);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);

    expectCommit(remoteTxn1);
    expectRollback(remoteTxn2);

    DatastoreService ds = newDatastoreService();
    Transaction txn1 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    Transaction txn2 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn2.rollback();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    txn1.commit();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testCurrentTransaction_Nested_RollbackRollback() {
    ImmutableList<ByteString> transactions = expectBeginTransaction(2);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);

    expectRollback(remoteTxn1);
    expectRollback(remoteTxn2);

    DatastoreService ds = newDatastoreService();
    Transaction txn1 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    Transaction txn2 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn2.rollback();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    txn1.rollback();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testCurrentTransaction_Nested_CommitOutOfOrder() {
    ImmutableList<ByteString> transactions = expectBeginTransaction(2);
    ByteString remoteTxn1 = transactions.get(0);
    ByteString remoteTxn2 = transactions.get(1);

    expectCommit(remoteTxn1);
    expectCommit(remoteTxn2);

    DatastoreService ds = newDatastoreService();
    Transaction txn1 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn1);
    Transaction txn2 = ds.beginTransaction();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn1.commit();
    assertThat(ds.getCurrentTransaction()).isEqualTo(txn2);
    txn2.commit();
    assertThat(ds.getCurrentTransaction(null)).isNull();
  }

  @Test
  public void testRunQuery_NeedsIndex() {
    Query query = new Query("blarg");
    query.addFilter("p1", Query.FilterOperator.EQUAL, 22);
    query.addFilter("p2", Query.FilterOperator.GREATER_THAN, 33);

    expectRunQuery(createRunQueryRequest(query, withLimit(2)).build(), Code.FAILED_PRECONDITION);

    DatastoreService ds = newDatastoreService();
    DatastoreNeedIndexException e =
        assertThrows(DatastoreNeedIndexException.class, () -> ds.prepare(query).asSingleEntity());
    // Cloud Datastore v1 does not currently add missing index info.
    assertThat(e.getMissingIndexDefinitionXml()).isEqualTo(null);
  }

  @Test
  public void testRunQuery_NeedsIndex_ProdLocalMismatch() {
    // tests the scenario where prod says we need an index but the composite
    // inde manager says we don't need one
    Query query = new Query("blarg");

    expectRunQuery(createRunQueryRequest(query, withLimit(2)).build(), Code.FAILED_PRECONDITION);


    DatastoreService ds = newDatastoreService();
    DatastoreNeedIndexException e =
        assertThrows(DatastoreNeedIndexException.class, () -> ds.prepare(query).asSingleEntity());
    assertThat(e.getMissingIndexDefinitionXml()).isNull();
  }

  @Test
  public void testEventualConsistencyPropagates_Get() {
    datastoreServiceConfig = withReadPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    LookupRequest.Builder lookupBldr = createLookupRequest();
    lookupBldr.getReadOptionsBuilder().setReadConsistency(ReadOptions.ReadConsistency.EVENTUAL);
    Key key = KeyFactory.createKey("foo", 33);
    lookupBldr.addKeys(DataTypeTranslator.toV1Key(key));
    LookupResponse.Builder lookupRespBldr = LookupResponse.newBuilder();
    lookupRespBldr.addMissingBuilder().setEntity(DataTypeTranslator.toV1Entity(new Entity(key)));
    expectLookup(lookupBldr.build(), lookupRespBldr.build());

    assertThrows(EntityNotFoundException.class, () -> newDatastoreService().get(key));
  }

  @Test
  public void testEventualConsistencyPropagates_Query() {
    datastoreServiceConfig = withReadPolicy(new ReadPolicy(ReadPolicy.Consistency.EVENTUAL));
    Query query = new Query("blarg").setAncestor(KeyFactory.createKey("foo", 123));
    RunQueryRequest.Builder v1Query = createRunQueryRequest(query, withLimit(2));
    v1Query.getReadOptionsBuilder().setReadConsistency(ReadConsistency.EVENTUAL);
    expectRunQuery(v1Query, createRunQueryResponse(NO_MORE_RESULTS, 0));

    DatastoreService ds = newDatastoreService();
    assertThat(ds.prepare(query).asSingleEntity()).isNull();
  }

  @Test
  public void testDeadlinePropagates_Query() {
    datastoreServiceConfig = DatastoreServiceConfig.Builder.withDeadline(5.0);
    Query query = new Query("blarg");
    expectRunQuery(
        createRunQueryRequest(query, withDefaults()), createRunQueryResponse(NO_MORE_RESULTS, 0));
    assertThat(newDatastoreService().prepare(query).asIterator().hasNext()).isFalse();
  }

  @Test
  public void testDeadlinePropagates_Get() {
    datastoreServiceConfig = DatastoreServiceConfig.Builder.withDeadline(5.0);
    Key key = KeyFactory.createKey("foo", 33);
    LookupResponse.Builder lookupRespBldr = LookupResponse.newBuilder();
    lookupRespBldr.addMissingBuilder().setEntity(DataTypeTranslator.toV1Entity(new Entity(key)));
    expectLookup(createLookupRequest(key).build(), lookupRespBldr.build());

    DatastoreService ds = newDatastoreService();
    assertThrows(EntityNotFoundException.class, () -> ds.get(key));
  }

  @Test
  public void testDeadlinePropagates_Put() {
    datastoreServiceConfig = DatastoreServiceConfig.Builder.withDeadline(5.0);

    Entity golden = new Entity("Foo");

    ByteString remoteTxn = maybeExpectBeginTransaction();
    expectCommit(createPutCommitRequest(remoteTxn, golden), 12345L);

    newDatastoreService().put(golden);
  }

  @Test
  public void testMultipleEgTransaction() {
    expectBeginTransaction();
    newDatastoreService().beginTransaction(TransactionOptions.Builder.withXG(true));
  }

  @Test
  public void testDatastoreAttributes() {
    ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
    try {
      ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment("app", "1"));
      assertThat(newDatastoreService().getDatastoreAttributes().getDatastoreType())
          .isEqualTo(DatastoreAttributes.DatastoreType.HIGH_REPLICATION);

      ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment("s~app", "1"));
      assertThat(newDatastoreService().getDatastoreAttributes().getDatastoreType())
          .isEqualTo(DatastoreAttributes.DatastoreType.HIGH_REPLICATION);

      ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment("e~app", "1"));
      assertThat(newDatastoreService().getDatastoreAttributes().getDatastoreType())
          .isEqualTo(DatastoreAttributes.DatastoreType.HIGH_REPLICATION);
    } finally {
      ApiProxy.setEnvironmentForCurrentThread(env);
    }
  }
}
