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

package com.google.appengine.tools.remoteapi.testing;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Carries out a series of tests to exercise the Remote API.  This can be invoked from within an App
 * Engine environment or outside of one.  It can target a Java or Python app.
 *
 */
public class RemoteApiSharedTests {

  private static final Logger logger = Logger.getLogger(RemoteApiSharedTests.class.getName());

  private final String localAppId;
  private final String remoteAppId;
  private final String username;
  private final String password;
  private final String server;
  private final String remoteApiPath;
  private final int port;
  private final boolean testKeysCreatedBeforeRemoteApiInstall;
  private final boolean expectRemoteAppIdsOnKeysAfterInstallingRemoteApi;

  /**
   * Builder for a {@link RemoteApiSharedTests} with some sensible defaults.
   */
  public static class Builder {
    private String localAppId;
    private String remoteAppId;
    private String username;
    private String password;
    private String server;

    /** This is the default for Java.  Needs to be /_ah/remote_api for Python. */
    private String remoteApiPath = "/remote_api";

    /** Default for apps running in the App Engine environment. */
    private int port = 443;

    /**
     * This should be set to false for Non-Hosted clients.  They won't have an Environment available
     * to generate Keys until the Remote API is installed.
     */
    private boolean testKeysCreatedBeforeRemoteApiInstall = true;

    /**
     * Allow these tests to be turned off because they rely on recent changes that haven't been
     * rolled out everywhere yet.  Targeting 1.8.8.
     * See http://b/11254141 and http://b/10788115
     * TODO: Remove this.
     */
    private boolean expectRemoteAppIdsOnKeysAfterInstallingRemoteApi = true;

    @CanIgnoreReturnValue
    public Builder setLocalAppId(String localAppId) {
      this.localAppId = localAppId;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setRemoteAppId(String remoteAppId) {
      this.remoteAppId = remoteAppId;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setUsername(String username) {
      this.username = username;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPassword(String password) {
      this.password = password;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setServer(String server) {
      this.server = server;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    public RemoteApiSharedTests build() {
      return new RemoteApiSharedTests(
          localAppId,
          remoteAppId,
          username,
          password,
          server,
          remoteApiPath,
          port,
          testKeysCreatedBeforeRemoteApiInstall,
          expectRemoteAppIdsOnKeysAfterInstallingRemoteApi);
    }
  }

  private RemoteApiSharedTests(
      String localAppId,
      String remoteAppId,
      String username,
      String password,
      String server,
      String remoteApiPath,
      int port,
      boolean testKeysCreatedBeforeRemoteApiInstall,
      boolean expectRemoteAppIdsOnKeysAfterInstallingRemoteApi) {
    this.localAppId = localAppId;
    this.remoteAppId = remoteAppId;
    this.username = username;
    this.password = password;
    this.server = server;
    this.remoteApiPath = remoteApiPath;
    this.port = port;
    this.testKeysCreatedBeforeRemoteApiInstall = testKeysCreatedBeforeRemoteApiInstall;
    this.expectRemoteAppIdsOnKeysAfterInstallingRemoteApi =
        expectRemoteAppIdsOnKeysAfterInstallingRemoteApi;
  }

  /**
   * Throws an exception if any errors are encountered.  If it completes successfully, then all test
   * cases passed.
   */
  public void runTests() throws IOException {
    RemoteApiOptions options = new RemoteApiOptions()
        .server(server, port)
        .credentials(username, password)
        .remoteApiPath(remoteApiPath);

    // Once we install the RemoteApi, all keys will start using the remote app id.  We'll store some
    // keys with the local app id first.
    LocalKeysHolder localKeysHolder = null;
    LocalEntitiesHolder localEntitiesHolder = null;
    if (testKeysCreatedBeforeRemoteApiInstall) {
      localKeysHolder = new LocalKeysHolder();
      localEntitiesHolder = new LocalEntitiesHolder();
    }

    RemoteApiInstaller installer = new RemoteApiInstaller();
    installer.install(options);

    // Update the options with reusable credentials.
    options.reuseCredentials(username, installer.serializeCredentials());
    // Execute our tests using the initial installation.
    try {
      doTest(localKeysHolder, localEntitiesHolder);
    } finally {
      installer.uninstall();
    }

    if (testKeysCreatedBeforeRemoteApiInstall) {
      // Make sure uninstalling brings the keys back to the local app id.
      assertNewKeysUseLocalAppId();
    }

    installer.install(options);
    // Execute our tests using the second installation.
    try {
      doTest(localKeysHolder, localEntitiesHolder);
    } finally {
      installer.uninstall();
    }
  }

  /**
   * Runs a series of tests using keys with both local app ids and remote app ids.
   */
  private void doTest(LocalKeysHolder localKeysHolder, LocalEntitiesHolder localEntitiesHolder) {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

    List<RemoteApiUnitTest> tests = ImmutableList.of(
        new PutAndGetTester(),
        new PutAndGetInTransactionTester(),
        new QueryTester(),
        new DeleteTester(),
        new XgTransactionTester());

    // Run each test once with local keys and once with remote keys.
    for (RemoteApiUnitTest test : tests) {
      if (localKeysHolder != null) {
        test.run(
            ds,
            localKeysHolder.createSupplierForFreshKind(),
            localEntitiesHolder.createSupplierForFreshKind());
        logger.info("Test passed with local keys: " + test.getClass().getName());
      }

      test.run(ds, new RemoteKeySupplier(), new RemoteEntitySupplier());
      logger.info("Test passed with remote keys: " + test.getClass().getName());
    }
  }

  private class PutAndGetTester implements RemoteApiUnitTest {

    @Override
    public void run(
        DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier) {
      Entity entity1 = new Entity(keySupplier.get());
      entity1.setProperty("prop1", 75L);

      // Verify results of Put
      Key keyReturnedFromPut = ds.put(entity1);
      assertEquals(entity1.getKey(), keyReturnedFromPut);

      // Make sure we can retrieve it again.
      assertGetEquals(ds, keyReturnedFromPut, entity1);

      // Test EntityNotFoundException
      Key unsavedKey = keySupplier.get();
      assertEntityNotFoundException(ds, unsavedKey);

      // Test batch get
      Entity entity2 = new Entity(keySupplier.get());
      entity2.setProperty("prop1", 88L);
      ds.put(entity2);

      Map<Key, Entity> batchGetResult =
          ds.get(Arrays.asList(entity1.getKey(), unsavedKey, entity2.getKey()));

      // Omits the unsaved key from results.
      assertEquals(2, batchGetResult.size());
      assertEquals(entity1, batchGetResult.get(entity1.getKey()));
      assertEquals(entity2, batchGetResult.get(entity2.getKey()));

      // Test Put and Get with id generated by Datastore backend.
      Entity entity3 = entitySupplier.get();
      entity3.setProperty("prop1", 35L);
      assertNoIdOrName(entity3.getKey());

      Key assignedKey = ds.put(entity3);

      assertTrue(assignedKey.getId() > 0);
      assertEquals(assignedKey, entity3.getKey());
      assertGetEquals(ds, assignedKey, entity3);
    }
  }

  private class PutAndGetInTransactionTester implements RemoteApiUnitTest {

    @Override
    public void run(
        DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier) {
      // Put a fresh entity.
      Entity originalEntity = new Entity(getFreshKindName());
      originalEntity.setProperty("prop1", 75L);
      ds.put(originalEntity);
      Key key = originalEntity.getKey();

      // Prepare a new version of it with a different property value.
      Entity mutatedEntity = new Entity(key);
      mutatedEntity.setProperty("prop1", 76L);

      // Test Get/Put within a transaction.
      Transaction txn = ds.beginTransaction();
      assertGetEquals(ds, key, originalEntity);
      ds.put(mutatedEntity); // Write the mutated Entity.
      assertGetEquals(ds, key, originalEntity); // Within a txn, the put is not yet reflected.
      txn.commit();

      // Now that the txn is committed, the mutated entity will show up in Get.
      assertGetEquals(ds, key, mutatedEntity);
    }
  }

  private class QueryTester implements RemoteApiUnitTest {

    @Override
    public void run(
        DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier) {
      // Note that we can't use local keys here.  Query will fail if you set an ancestor whose app
      // id does not match the "global" AppIdNamespace.
      // TODO: Consider making it more lenient, but it's not a big deal.  Users can
      // just use a Key that was created after installing the Remote API.
      Entity entity = new Entity(getFreshKindName());
      entity.setProperty("prop1", 99L);
      ds.put(entity);

      // Make sure we can retrieve it via a query.
      Query query = new Query(entity.getKind());
      query.setAncestor(entity.getKey());
      query.setFilter(
          new Query.FilterPredicate(
              Entity.KEY_RESERVED_PROPERTY,
              Query.FilterOperator.GREATER_THAN_OR_EQUAL,
              entity.getKey()));

      Entity queryResult = ds.prepare(query).asSingleEntity();

      // Queries return the Entities with the remote app id.
      assertRemoteAppId(queryResult.getKey());
      assertEquals(99L, queryResult.getProperty("prop1"));
    }
  }

  private class DeleteTester implements RemoteApiUnitTest {

    @Override
    public void run(
        DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier) {
      Key key = keySupplier.get();
      Entity entity = new Entity(key);
      entity.setProperty("prop1", 75L);
      ds.put(entity);

      assertGetEquals(ds, key, entity);
      ds.delete(key);
      assertEntityNotFoundException(ds, key);
    }
  }

  private static class XgTransactionTester implements RemoteApiUnitTest {

    @Override
    public void run(
        DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier) {
      Transaction txn = ds.beginTransaction(TransactionOptions.Builder.withXG(true));
      if (ds.put(new Entity("xgfoo")).getId() == 0) {
        throw new RuntimeException("first entity should have received an id");
      }
      if (ds.put(new Entity("xgfoo")).getId() == 0) {
        throw new RuntimeException("second entity should have received an id");
      }
      txn.commit();
    }
  }

  /**
   * Simple interface for test cases.
   */
  private interface RemoteApiUnitTest {

    /**
     * Runs the test case using the given DatastoreService and Keys from the given KeySupplier.
     *
     * @throws RuntimeException if the test fails.
     */
    void run(DatastoreService ds, Supplier<Key> keySupplier, Supplier<Entity> entitySupplier);
  }

  /**
   * A {@link Supplier} that creates Keys with the Remote app id.  Assumes the Remote API is already
   * installed.
   */
  private class RemoteKeySupplier implements Supplier<Key> {

    private final String kind;
    private int nameCounter = 0;

    private RemoteKeySupplier() {
      this.kind = getFreshKindName();
    }

    @Override
    public Key get() {
      // This assumes that the remote api has already been installed.
      Key key = KeyFactory.createKey(kind, "somename" + nameCounter);
      if (expectRemoteAppIdsOnKeysAfterInstallingRemoteApi) {
        assertRemoteAppId(key);
      }
      nameCounter++;

      return key;
    }
  }

  /**
   * A {@link Supplier} that creates Entities with a Key with the Remote app id (and no id or name
   * set.)  Assumes the Remote API is already installed.
   */
  private class RemoteEntitySupplier implements Supplier<Entity> {

    private final String kind;

    private RemoteEntitySupplier() {
      this.kind = getFreshKindName();
    }

    @Override
    public Entity get() {
      // This assumes that the remote api has already been installed.
      Entity entity = new Entity(kind);
      if (expectRemoteAppIdsOnKeysAfterInstallingRemoteApi) {
        assertRemoteAppId(entity.getKey());
      }

      return entity;
    }
  }

  /**
   * Creates and caches Keys with the local app id.  Assumes that the Remote API has not yet been
   * installed when a new instance is created.
   */
  private class LocalKeysHolder {
    private static final int NUM_KINDS = 50;
    private static final int NUM_KEYS_PER_KIND = 20;

    private Multimap<String, Key> keysByKind;

    public LocalKeysHolder() {
      keysByKind = LinkedListMultimap.create();

      for (int kindCounter = 0; kindCounter < NUM_KINDS; ++kindCounter) {
        String kind = getFreshKindName();
        for (int keyNameCounter = 0; keyNameCounter < NUM_KEYS_PER_KIND; ++keyNameCounter) {
          String name = "somename" + keyNameCounter;

          Key key = KeyFactory.createKey(kind, name);
          assertLocalAppId(key);
          keysByKind.put(kind, key);
        }
      }
    }

    private Supplier<Key> createSupplierForFreshKind() {
      String kind = keysByKind.keySet().iterator().next();
      final Iterator<Key> keysIterator = keysByKind.get(kind).iterator();
      keysByKind.removeAll(kind);

      return new Supplier<Key>() {
        @Override
        public Key get() {
          return keysIterator.next();
        }
      };
    }
  }

  /**
   * Creates and caches Entities with Keys containing the local app id (but no id or name set.)
   * Assumes that the Remote API has not yet been installed when a new instance is created.
   */
  private class LocalEntitiesHolder {
    private static final int NUM_KINDS = 50;
    private static final int NUM_ENTITIES_PER_KIND = 20;

    private Multimap<String, Entity> entitiesByKind;

    public LocalEntitiesHolder() {
      entitiesByKind = LinkedListMultimap.create();

      for (int kindCounter = 0; kindCounter < NUM_KINDS; ++kindCounter) {
        String kind = getFreshKindName();
        for (int i = 0; i < NUM_ENTITIES_PER_KIND; ++i) {
          // Will get a default Key with the local app id and no id or name.
          Entity entity = new Entity(kind);

          assertLocalAppId(entity.getKey());
          entitiesByKind.put(kind, entity);
        }
      }
    }

    private Supplier<Entity> createSupplierForFreshKind() {
      String kind = entitiesByKind.keySet().iterator().next();
      final Iterator<Entity> entitiesIterator = entitiesByKind.get(kind).iterator();
      entitiesByKind.removeAll(kind);

      return new Supplier<Entity>() {
        @Override
        public Entity get() {
          return entitiesIterator.next();
        }
      };
    }
  }

  private void assertTrue(boolean condition, String message) {
    if (!condition) {
      throw new RuntimeException(message);
    }
  }

  private void assertTrue(boolean condition) {
    assertTrue(condition, "");
  }

  private void assertEquals(Object o1, Object o2) {
    assertEquals(o1, o2, "Expected " + o1 + " to equal " + o2);
  }

  private void assertEquals(Object o1, Object o2, String message) {
    if (o1 == null) {
      assertTrue(o2 == null, message);
      return;
    }
    assertTrue(o1.equals(o2), message);
  }

  /** Special version of assertEquals for Entities that will ignore app ids on Keys. */
  private void assertEquals(Entity e1, Entity e2) {
    if (e1 == null) {
      assertTrue(e2 == null);
      return;
    }

    assertEquals(e1.getProperties(), e2.getProperties());
    assertEquals(e1.getKey(), e2.getKey());
  }

  /** Special version of assertEquals for Keys that will ignore app ids. */
  private void assertEquals(Key k1, Key k2) {
    if (k1 == null) {
      assertTrue(k2 == null);
      return;
    }

    assertEquals(k1.getKind(), k2.getKind());
    assertEquals(k1.getId(), k2.getId());
    assertEquals(k1.getName(), k2.getName());

    assertEquals(k1.getParent(), k2.getParent());
  }

  private void assertLocalAppId(Key key) {
    assertAppIdsMatchIgnoringPartition(localAppId, key.getAppId());
  }

  private void assertRemoteAppId(Key key) {
    assertAppIdsMatchIgnoringPartition(remoteAppId, key.getAppId());
  }

  /**
   * The e2e testing framework is not very strict about requiring fully specified app ids.
   * Therefore, we might get "display" app ids given to us and we need to consider "s~foo" and "foo"
   * to be equal.
   */
  private void assertAppIdsMatchIgnoringPartition(String appId1, String appId2) {
    if (appId1.equals(appId2)) {
      // Exact match.
      return;
    }

    // Consider s~foo == foo.
    assertEquals(
        stripPartitionFromAppId(appId1),
        stripPartitionFromAppId(appId2),
        "Expected app id to be: " + appId1 + ", but was: " + appId2);
  }

  /**
   * Example conversions:
   *
   * foo => foo
   * s~foo => foo
   * e~foo => foo
   * hrd~foo => foo (Doesn't exist in App Engine today, but this code will support partitions
   * greater than 1 char.)
   */
  private String stripPartitionFromAppId(String appId) {
    int partitionIndex = appId.indexOf('~');
    if (partitionIndex != -1 && appId.length() > partitionIndex + 1) {
      return appId.substring(partitionIndex + 1);
    }
    return appId;
  }

  private void assertNewKeysUseLocalAppId() {
    assertLocalAppId(KeyFactory.createKey(getFreshKindName(), "somename"));
  }

  private void assertNoIdOrName(Key key) {
    assertEquals(0L, key.getId());
    assertEquals(null, key.getName());
  }

  private void assertGetEquals(DatastoreService ds, Key keyToGet, Entity expectedEntity) {
    // Test the single key api.
    Entity entityFromGet = quietGet(ds, keyToGet);
    assertEquals(expectedEntity, entityFromGet);
    assertRemoteAppId(entityFromGet.getKey());

    // Test the multi-get api.
    Map<Key, Entity> getResults = ds.get(Collections.singletonList(keyToGet));
    assertEquals(1, getResults.size());
    Entity entityFromBatchGet = getResults.get(keyToGet);
    assertEquals(expectedEntity, entityFromBatchGet);
    assertRemoteAppId(entityFromBatchGet.getKey());
  }

  private void assertEntityNotFoundException(DatastoreService ds, Key missingKey) {
    try {
      ds.get(missingKey);
      throw new RuntimeException("Did not receive expected exception");
    } catch (EntityNotFoundException e) {
      // expected
    }
  }

  /**
   * Propagates {@link EntityNotFoundException} as {@link RuntimeException}
   */
  private Entity quietGet(DatastoreService ds, Key key) {
    try {
      return ds.get(key);
    } catch (EntityNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Generates a Kind name that has not yet been used.
   */
  private String getFreshKindName() {
    return "testkind" + UUID.randomUUID();
  }
}
