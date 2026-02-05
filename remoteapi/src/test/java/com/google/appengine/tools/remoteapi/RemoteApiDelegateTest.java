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

package com.google.appengine.tools.remoteapi;

import static com.google.common.io.BaseEncoding.base64Url;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.memcache.MemcacheServicePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Verifies that the remote API makes the right RPC calls.
 *
 */
@RunWith(JUnit4.class)
public class RemoteApiDelegateTest {
  private MockRpc rpc;
  private DatastoreService datastore;
  private RemoteApiInstaller installer;

  @Before
  @SuppressWarnings("deprecation") // RemoteApiOptions.credentials is deprecated.
  public void setUp() throws Exception {
    installer =
        new RemoteApiInstaller() {
          @Override
          AppEngineClient login(RemoteApiOptions options) {
            return new AppEngineClient(options, ImmutableList.of(), getAppId()) {
              @Override
              public Response get(String path) throws IOException {
                return null;
              }

              @Override
              public Response post(String path, String mimeType, byte[] body) throws IOException {
                return null;
              }

              @Override
              public LegacyResponse legacyGet(String path) throws IOException {
                return null;
              }

              @Override
              public LegacyResponse legacyPost(String path, String mimeType, byte[] body)
                  throws IOException {
                return null;
              }
            };
          }

          @Override
          RemoteApiDelegate createDelegate(
              RemoteApiOptions options,
              RemoteApiClient client,
              ApiProxy.Delegate<ApiProxy.Environment> originalDelegate) {
            rpc = new MockRpc(client);
            return RemoteApiDelegate.newInstance(rpc, options, originalDelegate);
          }

          @Override
          ApiProxy.Environment createEnv(RemoteApiOptions options, RemoteApiClient client) {
            return new ToolEnvironment(getAppId(), "someone@google.com");
          }
        };

    RemoteApiOptions ignoredOptions =
        new RemoteApiOptions()
            .server("ignored.example.com", 1234)
            .credentials("someone@google.com", "ignored");
    installer.install(ignoredOptions);
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  @After
  public void tearDown() throws Exception {
    installer.uninstall();
  }

  protected String getAppId() {
    return "test~appId";
  }

  @Test
  public void testFlushMemcache() throws Exception {
    rpc.addResponse(MemcacheServicePb.MemcacheFlushResponse.getDefaultInstance());

    MemcacheServiceFactory.getMemcacheService().clearAll();

    rpc.verifyNextRpc("memcache", "FlushAll");
    rpc.verifyNoMoreRpc();
  }

  @Test
  public void testJavaException() throws Exception {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteStream);
    out.writeObject(new RuntimeException("an exception"));
    out.close();
    byte[] serializedException = byteStream.toByteArray();

    RemoteApiPb.Response response =
        RemoteApiPb.Response.newBuilder()
            .setJavaException(ByteString.copyFrom(serializedException))
            .build();
    rpc.addResponse(response);

    RuntimeException e = assertThrows(RuntimeException.class, () -> datastore.get(KeyFactory.createKey("Something", 123)));
    assertThat(e).hasMessageThat().isEqualTo("an exception");
  }

  @Test
  public void testPythonException() throws Exception {
    RemoteApiPb.Response response =
        RemoteApiPb.Response.newBuilder()
            .setException(ByteString.copyFromUtf8("pickle goes here"))
            .build();
    rpc.addResponse(response);

    ApiProxy.ApiProxyException e = assertThrows(ApiProxy.ApiProxyException.class, () -> datastore.get(KeyFactory.createKey("Something", 123)));
    assertThat(e).hasMessageThat().contains("response was a python exception:\n");
    assertThat(e).hasMessageThat().contains("pickle goes here");
  }

  @Test
  public void testQueryReturningAllResultsInOneRpcCall() throws Exception {
    replyToQuery(new Entity("Foo"), new Entity("Foo"));

    Query query = new Query("Foo");
    List<Entity> result = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10));
    assertEquals(2, result.size());

    verifyCalledRunQuery("Foo", null);
    rpc.verifyNoMoreRpc();
  }

  /**
   * Verifies that we can run a query that requires 3 RPC calls. Note that because queries run
   * async, the RPC calls overlap with verification!
   */
  @Test
  public void testQueryReturningResultInThreeRpcCalls() throws Exception {
    DatastoreV3Pb.CompiledCursor cursor1 = makeFakeCursor("cursor1");
    DatastoreV3Pb.CompiledCursor cursor2 = makeFakeCursor("cursor2");

    Query query = new Query("Foo");

    // Start RPC #1, which runs async.

    replyToQuery(cursor1, new Entity("Foo")); // needed by RPC #1
    Iterator<Entity> result = datastore.prepare(query).asIterator();

    // Wait for RPC #1. When it finishes, RPC #2 may start.

    replyToQuery(cursor2, new Entity("Foo"), new Entity("Foo")); // needed by RPC #2
    assertTrue(result.hasNext());
    verifyCalledRunQuery("Foo", null); // check RPC #1

    // Skip one result from RPC #1

    result.next();

    // Wait for RPC #2, and RPC #3 might start.

    replyToQuery(new Entity("Foo")); // needed by RPC #3
    assertTrue(result.hasNext());
    verifyCalledRunQuery("Foo", cursor1); // check RPC #2 (Uses RunQuery, not Next.)

    // Skip two results from RPC #2

    result.next();
    assertTrue(result.hasNext());
    result.next();

    // Wait for RPC #3. No more RPC's should start.

    assertTrue(result.hasNext());
    verifyCalledRunQuery("Foo", cursor2); // check RPC #3
    rpc.verifyNoMoreRpc();

    // Reading the rest of the query results shouldn't require any more RPCs.

    result.next();
    assertFalse(result.hasNext());

    rpc.verifyNoMoreRpc();
  }

  @Test
  public void testTransactionThatInsertsOneEntityWithNewId() throws Exception {
    Entity entityToInsert = new Entity("Foo");

    // Starting a transaction using the remote API doesn't require an RPC.

    Transaction tx = datastore.beginTransaction();
    rpc.verifyNoMoreRpc();

    // In a transaction, put() calls don't happen immediately, but we need
    // an RPC to allocate an id for the new Entity (if it doesn't have one already).

    Key newKey = KeyFactory.createKey("Foo", 123);
    replyToGetIds(newKey);
    datastore.put(entityToInsert);
    rpc.verifyNextRpc("remote_datastore", "GetIDs");

    assertEquals(newKey, entityToInsert.getKey());

    // Committing the transaction performs the RPC.

    rpc.verifyNoMoreRpc();
    replyToRemoteTransaction();

    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(); // none
    checker.checkPuts(entityToInsert);
    checker.checkDeletes(); // none
  }

  @Test
  public void testTransactionThatUpdatesOneProperty() throws Exception {

    // The beginning of the transaction is handled locally, without doing any RPC call.

    Transaction tx = datastore.beginTransaction();
    rpc.verifyNoMoreRpc();

    // When we get() the entity, the RPC is passed through.

    Entity entityReturnedByRpc = new Entity("Foo", "hello");
    replyToGetWithEntity(entityReturnedByRpc);

    Entity entity = datastore.get(KeyFactory.createKey("Foo", "hello"));

    rpc.verifyNextRpc("datastore_v3", "Get");
    assertEquals(entityReturnedByRpc, entity);

    entity.setProperty("bar", 123);

    // The put() call is remembered locally (the datastore won't be updated until commit).

    datastore.put(entity);
    rpc.verifyNoMoreRpc();

    // commit() uses a special RPC call to do the transaction all at once.

    replyToRemoteTransaction();

    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(entityReturnedByRpc);
    checker.checkPuts(entity);
    checker.checkDeletes(); // none

    rpc.verifyNoMoreRpc();
  }

  @Test
  public void testTransactionThatDeletesAnEntity() throws Exception {

    Key keyToDelete = KeyFactory.createKey("Foo", "hello");

    Transaction tx = datastore.beginTransaction();

    // Do the delete. No RPC now, because deletes should be saved until the transaction commits.

    datastore.delete(keyToDelete);
    rpc.verifyNoMoreRpc();

    // Do the commit.

    replyToRemoteTransaction();

    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(); // none
    checker.checkPuts(); // none
    checker.checkDeletes(keyToDelete);
  }

  @Test
  public void testGetsAreCachedInTransaction() throws Exception {

    Key keyToGet = KeyFactory.createKey("Foo", "hello");
    Entity entityReturnedByRpc = new Entity("Foo", "hello");

    Transaction tx = datastore.beginTransaction();

    // First get does an rpc

    replyToGetWithEntity(entityReturnedByRpc);

    Entity firstEntity = datastore.get(keyToGet);

    rpc.verifyNextRpc("datastore_v3", "Get");
    assertEquals(entityReturnedByRpc, firstEntity);

    // Second get skips the rpc and returns the same entity

    Entity secondEntity = datastore.get(keyToGet);
    rpc.verifyNoMoreRpc();

    assertNotSame("entities shouldn't be the same", firstEntity, secondEntity);
    assertEquals("entities should be equal", firstEntity, secondEntity);

    // The commit should contain one precondition

    replyToRemoteTransaction();

    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(entityReturnedByRpc);
    checker.checkPuts(); // none
    checker.checkDeletes(); // none
  }

  @Test
  public void testTransactionThatGetsNonexistentEntity() throws Exception {

    Key keyToGet = KeyFactory.createKey("Foo", "hello");

    Transaction tx = datastore.beginTransaction();

    // The first Get does an rpc and finds out there's no entity in the datastore.

    replyToGetWithMissing(keyToGet);

    EntityNotFoundException e1 = assertThrows(EntityNotFoundException.class, () -> datastore.get(keyToGet));
    assertThat(e1.getKey()).isEqualTo(keyToGet);

    rpc.verifyNextRpc("datastore_v3", "Get");

    // The second Get skips the rpc and throws the same exception.

    EntityNotFoundException e2 = assertThrows(EntityNotFoundException.class, () -> datastore.get(keyToGet));
    assertThat(e2.getKey()).isEqualTo(keyToGet);

    rpc.verifyNoMoreRpc();

    // The commit contains a precondition that the entity doesn't exist

    replyToRemoteTransaction();

    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkNonexistentEntityPreconditions(keyToGet);
    checker.checkPuts(); // none
    checker.checkDeletes(); // none
  }

  @Test
  public void testAncestorQueryInTransaction() throws Exception {
    Key parentKey = KeyFactory.createKey("Parent", 123);
    Key childKey = KeyFactory.createKey(parentKey, "Child", 456);
    Entity childEntity = new Entity(childKey);

    Query query = new Query("Child");
    query.setAncestor(parentKey);

    Transaction tx = datastore.beginTransaction();
    PreparedQuery preparedQuery = datastore.prepare(tx, query);

    rpc.verifyNoMoreRpc();

    Entity witness = replyToTxQuery(null, childEntity);
    List<Entity> result = preparedQuery.asList(FetchOptions.Builder.withLimit(10));

    assertEquals(1, result.size());
    // The list pulls results lazily, so don't verify the rpc until after we've
    // called size().
    verifyCalledTxQuery("Child", null);
    assertEquals(childKey, result.get(0).getKey());

    replyToRemoteTransaction();
    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(witness);
    checker.checkPuts(); // none
    checker.checkDeletes(); // none

    rpc.verifyNoMoreRpc();
  }

  /**
   * Verifies that we can run a query in a transaction that requires 2 RPC calls. Note that because
   * queries run async, the RPC calls overlap with verification!
   */
  @Test
  public void testTwoPartAncestorQueryInTransaction() throws Exception {
    DatastoreV3Pb.CompiledCursor cursor1 = makeFakeCursor("cursor1");

    Key parentKey = KeyFactory.createKey("Parent", 123);
    Key child1Key = KeyFactory.createKey(parentKey, "Child", 456);
    Entity child1Entity = new Entity(child1Key);
    Key child2Key = KeyFactory.createKey(parentKey, "Child", 457);
    Entity child2Entity = new Entity(child2Key);

    Query query = new Query("Child");
    query.setAncestor(parentKey);

    Transaction tx = datastore.beginTransaction();
    PreparedQuery preparedQuery = datastore.prepare(tx, query);

    rpc.verifyNoMoreRpc();

    // start RPC #1 (async)

    Entity witness1 = replyToTxQuery(cursor1, child1Entity); // needed by RPC #1
    Iterator<Entity> it = preparedQuery.asIterator();

    // wait for RPC #1 and start RPC #2

    Entity witness2 = replyToTxQuery(null, child2Entity); // needed by RPC #2
    assertTrue(it.hasNext());
    verifyCalledTxQuery("Child", null); // check RPC #1
    assertEquals(child1Key, it.next().getKey());

    // wait for RPC #2

    boolean unused = it.hasNext();
    verifyCalledTxQuery("Child", cursor1); // check RPC #2
    rpc.verifyNoMoreRpc();

    // check second result

    assertTrue(it.hasNext());
    assertEquals(child2Key, it.next().getKey());
    assertFalse(it.hasNext());

    rpc.verifyNoMoreRpc();

    // commit

    replyToRemoteTransaction();
    tx.commit();

    CommitChecker checker = verifyCommitRequest();
    checker.checkPreconditions(witness1, witness2);
    checker.checkPuts(); // none
    checker.checkDeletes(); // none
  }

  @Test
  public void testRollbackTransaction() throws Exception {
    Transaction tx = datastore.beginTransaction();
    rpc.verifyNoMoreRpc();

    tx.rollback();
    rpc.verifyNoMoreRpc();
  }

  @Test
  public void testNoRemoteDatastore() throws Exception {
    String property = "com.google.appengine.devappserver2";
    String oldProperty = System.getProperty(property);
    try {
      System.setProperty(property, "true");
      doTestNoRemoteDatastore();
    } finally {
      if (oldProperty == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, oldProperty);
      }
    }
  }

  @SuppressWarnings(
      "deprecation") // RemoteApiOptions.credentials is deprecated, OK to use in tests.
  private void doTestNoRemoteDatastore() throws Exception {
    RemoteApiOptions remoteApiOptions =
        new RemoteApiOptions().credentials("someone@google.com", "password123");
    RemoteApiDelegate delegate = RemoteApiDelegate.newInstance(rpc, remoteApiOptions, null);
    ApiProxy.Environment fakeEnvironment = new ToolEnvironment(getAppId(), "someone@google.com");
    byte[] fakeRequest = {1, 2, 3, 4};
    byte[] fakeResponse = {5, 6, 7, 8};
    rpc.addResponse(fakeResponse);
    byte[] unused =
        delegate.makeSyncCall(
            fakeEnvironment, RemoteDatastore.DATASTORE_SERVICE, "Commit", fakeRequest);
    rpc.verifyNextRpc(RemoteDatastore.DATASTORE_SERVICE, "Commit");
    rpc.verifyNoMoreRpc();
  }

  // === end of tests ===

  @SuppressWarnings("deprecation") // CompiledCursor.Position.start_key is deprecated but we need to
  // set it here for testing.
  private static DatastoreV3Pb.CompiledCursor makeFakeCursor(String name) {
    DatastoreV3Pb.CompiledCursor.Builder result = DatastoreV3Pb.CompiledCursor.newBuilder();
    // DatastoreV3Pb (proto2api) uses String for string fields.
    result.getPositionBuilder().setStartKey(ByteString.copyFromUtf8(name));
    return result.build();
  }

  private void replyToQuery(Entity... entities) {
    replyToQuery(null, entities);
  }

  private void replyToQuery(DatastoreV3Pb.CompiledCursor cursor, Entity... entities) {
    DatastoreV3Pb.QueryResult.Builder resultPb = DatastoreV3Pb.QueryResult.newBuilder();
    for (Entity entity : entities) {
      resultPb.addResult(entityToProto2(entity));
    }
    if (cursor != null) {
      resultPb.setMoreResults(true);
      resultPb.setCompiledCursor(cursor);
    } else {
      resultPb.setMoreResults(false);
    }
    rpc.addResponse(resultPb.build());
  }

  private Entity replyToTxQuery(DatastoreV3Pb.CompiledCursor cursor, Entity... entities) {
    RemoteApiPb.TransactionQueryResult.Builder resultPb =
        RemoteApiPb.TransactionQueryResult.newBuilder();
    DatastoreV3Pb.QueryResult.Builder queryResultPb = resultPb.getResultBuilder();
    for (Entity entity : entities) {
      queryResultPb.addResult(entityToProto2(entity));
    }
    if (cursor != null) {
      queryResultPb.setMoreResults(true);
      queryResultPb.setCompiledCursor(cursor);
    } else {
      queryResultPb.setMoreResults(false);
    }
    // Make a somewhat arbitrary witness.
    Entity witness = new Entity("__entity_group__", "foo", entities[0].getKey());
    resultPb
        .setEntityGroupKey(entityToProto2(witness).getKey())
        .setEntityGroup(entityToProto2(witness));
    rpc.addResponse(resultPb.build());
    return witness;
  }

  private void replyToGetWithMissing(Key missingKey) {
    DatastoreV3Pb.GetResponse.Builder response = DatastoreV3Pb.GetResponse.newBuilder();
    response.addEntityBuilder().setKey(keyToRefProto2(missingKey));
    rpc.addResponse(response.build());
  }

  private void replyToGetWithEntity(Entity entity) {
    DatastoreV3Pb.GetResponse.Builder response = DatastoreV3Pb.GetResponse.newBuilder();
    response.addEntityBuilder().setEntity(entityToProto2(entity));
    rpc.addResponse(response.build());
  }

  private void replyToGetIds(Key... updatedKeys) {
    DatastoreV3Pb.PutResponse.Builder response = DatastoreV3Pb.PutResponse.newBuilder();
    for (Key key : updatedKeys) {
      response.addKey(keyToRefProto2(key));
    }
    rpc.addResponse(response.build());
  }

  private void replyToRemoteTransaction() {
    DatastoreV3Pb.PutResponse response = DatastoreV3Pb.PutResponse.getDefaultInstance();
    rpc.addResponse(response.toByteArray());
  }

  @CanIgnoreReturnValue
  private DatastoreV3Pb.Query verifyCalledRunQuery(
      String expectedKind, DatastoreV3Pb.CompiledCursor expectedCursor) {
    RemoteApiPb.Request wrappedRequest = rpc.verifyNextRpc("datastore_v3", "RunQuery");
    DatastoreV3Pb.Query query = verifyQuery(wrappedRequest, expectedKind, expectedCursor);
    // not in a transaction
    assertFalse("query shouldn't be in a transaction", query.hasTransaction());

    return query;
  }

  @CanIgnoreReturnValue
  private DatastoreV3Pb.Query verifyCalledTxQuery(
      String expectedKind, DatastoreV3Pb.CompiledCursor expectedCursor) {
    RemoteApiPb.Request wrappedRequest = rpc.verifyNextRpc("remote_datastore", "TransactionQuery");
    DatastoreV3Pb.Query query = verifyQuery(wrappedRequest, expectedKind, expectedCursor);

    return query;
  }

  @SuppressWarnings("deprecation") // Testing deprecated Query.compile field.
  private static DatastoreV3Pb.Query verifyQuery(
      RemoteApiPb.Request wrappedRequest,
      String expectedKind,
      DatastoreV3Pb.CompiledCursor expectedCursor) {
    DatastoreV3Pb.Query query;
    try {
      query =
          DatastoreV3Pb.Query.parseFrom(
              wrappedRequest.getRequest(), ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }

    assertEquals("query on unexpected kind", expectedKind, query.getKind());
    assertWithMessage("Compile flag not set for query").that(query.getCompile()).isTrue();
    assertEquals("query with unexpected offset", 0, query.getOffset());
    if (expectedCursor != null) {
      assertEquals("cursor doesn't match", expectedCursor, query.getCompiledCursor());
    } else {
      assertFalse(query.hasCompiledCursor());
    }

    return query;
  }

  private CommitChecker verifyCommitRequest() {
    RemoteApiPb.Request wrappedRequest = rpc.verifyNextRpc("remote_datastore", "Transaction");
    return new CommitChecker(wrappedRequest);
  }

  private static OnestoreEntity.EntityProto entityToProto2(Entity entity) {
    return EntityTranslator.convertToPb(entity);
  }

  private static OnestoreEntity.Reference keyToRefProto2(Key key) {
    try {
      String encoded = KeyFactory.keyToString(key);
      byte[] bytes = base64Url().decode(encoded);
      return OnestoreEntity.Reference.parseFrom(bytes, ExtensionRegistry.getEmptyRegistry());
    } catch (Exception e) {
      throw new RuntimeException("can't convert key to reference", e);
    }
  }

  /**
   * Accepts RPC calls and returns the appropriate fake value. Handles async calls by waiting before
   * verifying.
   */
  static class MockRpc extends RemoteRpc {

    Queue<RemoteApiPb.Request> receivedRequests;
    Queue<RemoteApiPb.Response> responsesToSend;

    MockRpc(RemoteApiClient client) {
      super(client);
      receivedRequests = new LinkedBlockingQueue<RemoteApiPb.Request>();
      responsesToSend = new LinkedBlockingQueue<RemoteApiPb.Response>();
    }

    void addResponse(RemoteApiPb.Response response) {
      responsesToSend.add(response);
    }

    void addResponse(byte[] bytes) {
      RemoteApiPb.Response response =
          RemoteApiPb.Response.newBuilder().setResponse(ByteString.copyFrom(bytes)).build();

      addResponse(response);
    }

    void addResponse(Message result) {
      addResponse(result.toByteArray());
    }

    @Override
    RemoteApiPb.Response callImpl(RemoteApiPb.Request requestProto) {
      receivedRequests.add(requestProto);
      return responsesToSend.remove();
    }

    /**
     * Checks that at least one RPC happened and returns its value. In the case of async RPC, you
     * must arrange to wait until the async call finishes before calling this method (typically by
     * getting the value from the Future).
     */
    RemoteApiPb.Request verifyNextRpc() {
      return receivedRequests.remove();
    }

    @CanIgnoreReturnValue
    RemoteApiPb.Request verifyNextRpc(String expectedService, String expectedMethod) {
      RemoteApiPb.Request request = verifyNextRpc();
      assertWithMessage("unexpected method call")
          .that(request.getServiceName() + "." + request.getMethod())
          .isEqualTo(expectedService + "." + expectedMethod);
      return request;
    }

    /** Verify that no RPC happened. (Waits a bit to make sure an async RPC isn't in progress.) */
    void verifyNoMoreRpc() throws InterruptedException {
      // need to wait in the case of async calls
      Thread.sleep(1000);
      assertThat(receivedRequests).isEmpty();
      assertThat(responsesToSend).isEmpty();
    }
  }

  /** Verifies the fields in a request to commit a transaction using the remote API. */
  static class CommitChecker {
    private final RemoteApiPb.TransactionRequest actualRequest;

    CommitChecker(RemoteApiPb.Request wrappedRequest) {
      try {
        actualRequest =
            RemoteApiPb.TransactionRequest.parseFrom(
                wrappedRequest.getRequest(), ExtensionRegistry.getEmptyRegistry());
      } catch (InvalidProtocolBufferException e) {
        throw new RuntimeException(e);
      }
    }

    void checkPreconditions(Entity... expectedEntities) {

      List<SimpleImmutableEntry<ByteString, ByteString>> expected = Lists.newArrayList();
      for (Entity entity : expectedEntities) {
        ByteString key = entityToProto2(entity).getKey().toByteString();
        ByteString hash = getSha1Hash(entity);
        expected.add(new SimpleImmutableEntry<>(key, hash));
      }

      checkPreconditions(expected);
    }

    private void checkPreconditions(List<SimpleImmutableEntry<ByteString, ByteString>> expected) {
      List<SimpleImmutableEntry<ByteString, ByteString>> actual = Lists.newArrayList();
      for (RemoteApiPb.TransactionRequest.Precondition precondition :
          actualRequest.getPreconditionList()) {
        ByteString key = precondition.getKey().toByteString();
        ByteString hash = precondition.hasHash() ? precondition.getHash() : null;
        actual.add(new SimpleImmutableEntry<>(key, hash));
      }

      assertWithMessage("commit call doesn't have the right preconditions")
          .that(actual)
          .containsExactlyElementsIn(expected);
    }

    void checkNonexistentEntityPreconditions(Key... expectedKeys) {
      List<SimpleImmutableEntry<ByteString, ByteString>> expected = Lists.newArrayList();
      for (Key key : expectedKeys) {
        ByteString keyBytes = keyToRefProto2(key).toByteString();
        expected.add(new SimpleImmutableEntry<>(keyBytes, (ByteString) null));
      }
      checkPreconditions(expected);
    }

    void checkPuts(Entity... expectedPuts) {
      assertWithMessage("commit call doesn't put() the right entities")
          .that(createEntities(actualRequest.getPuts().getEntityList()))
          .containsExactlyElementsIn(expectedPuts);
    }

    void checkDeletes(Key... expected) {
      assertWithMessage("commit call doesn't delete the right entities")
          .that(createKeys(actualRequest.getDeletes().getKeyList()))
          .containsExactlyElementsIn(expected);
    }

    private static ByteString getSha1Hash(Entity entity) {
      byte[] bytes = entityToProto2(entity).toByteArray();
      return ByteString.copyFrom(Hashing.sha1().hashBytes(bytes, 0, bytes.length).asBytes());
    }

    private static List<Entity> createEntities(Iterable<OnestoreEntity.EntityProto> entities) {
      List<Entity> result = Lists.newArrayList();
      for (OnestoreEntity.EntityProto entityPb : entities) {
        result.add(EntityTranslator.createFromPb(entityPb));
      }
      return result;
    }

    private static List<Key> createKeys(Iterable<OnestoreEntity.Reference> keys) {
      List<Key> result = Lists.newArrayList();
      for (OnestoreEntity.Reference keyProto : keys) {
        result.add(createKey(keyProto));
      }
      return result;
    }

    private static Key createKey(OnestoreEntity.Reference keyPb) {
      // KeyTranslator isn't public but we can call it indirectly through KeyFactory
      return KeyFactory.stringToKey(base64Url().omitPadding().encode(keyPb.toByteArray()));
    }
  }
}
