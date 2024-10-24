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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.AdminDatastoreService.AsyncDatastoreServiceFactory;
import com.google.appengine.api.datastore.AdminDatastoreService.EntityBuilder;
import com.google.appengine.api.datastore.AdminDatastoreService.KeyBuilder;
import com.google.appengine.api.datastore.DatastoreAttributes.DatastoreType;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.primitives.Primitives;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for {@link AdminDatastoreService}.
 *
 */
@SuppressWarnings("javadoc")
@RunWith(JUnit4.class)
public class AdminDatastoreServiceTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final AppIdNamespace APP_ID_NS = new AppIdNamespace("koko", "ns1");

  @Mock AsyncDatastoreServiceImpl delegate;
  @Mock Transaction transaction;
  @Mock CompositeIndexManager indexManager;
  private final LocalServiceTestHelper testHelper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
  private AdminDatastoreService adminDsWithMockDelegate;
  private AdminDatastoreService adminDsWithLocalDsDelegate;
  private AsyncDatastoreServiceFactory oldFactory;

  @Before
  public void setUp() throws Exception {
    adminDsWithLocalDsDelegate =
        AdminDatastoreService.getInstance(APP_ID_NS.getAppId(), APP_ID_NS.getNamespace());
    oldFactory = AdminDatastoreService.factory;
    testHelper.setUp();
    AdminDatastoreService.factory =
        new AsyncDatastoreServiceFactory() {
          @Override
          public AsyncDatastoreService getInstance(DatastoreServiceConfig config) {
            when(delegate.getDatastoreServiceConfig()).thenReturn(config);
            return delegate;
          }

          @Override
          public CompositeIndexManager getCompositeIndexManager() {
            return indexManager;
          }
        };
    adminDsWithMockDelegate =
        AdminDatastoreService.getInstance(APP_ID_NS.getAppId(), APP_ID_NS.getNamespace());
  }

  @After
  public void tearDown() {
    AdminDatastoreService.factory = oldFactory;
    testHelper.tearDown();
  }

  @Test
  public void testGetInstanceWithAppId() {
    adminDsWithMockDelegate = AdminDatastoreService.getInstance("koko");
    assertThat(adminDsWithMockDelegate.getAppId()).isEqualTo("koko");
    assertThat(adminDsWithMockDelegate.getNamespace()).isEmpty();
  }

  @Test
  public void testGetInstanceWithAppIdAndNamespace() {
    assertThat(adminDsWithMockDelegate.getAppId()).isEqualTo("koko");
    assertThat(adminDsWithMockDelegate.getNamespace()).isEqualTo("ns1");
  }

  @Test
  public void testGetInstanceWithConfigAndAppId() {
    DatastoreServiceConfig dsConf = DatastoreServiceConfig.Builder.withDeadline(20);
    adminDsWithMockDelegate = AdminDatastoreService.getInstance(dsConf, "koko");
    assertThat(adminDsWithMockDelegate.getAppId()).isEqualTo("koko");
    assertThat(adminDsWithMockDelegate.getNamespace()).isEmpty();
    assertThat(
            ((BaseAsyncDatastoreServiceImpl) adminDsWithMockDelegate.getDelegate())
                .getDatastoreServiceConfig()
                .getDeadline())
        .isEqualTo(20.0);
  }

  @Test
  public void testGetInstanceWithConfigAppIdAndNamespace() {
    DatastoreServiceConfig dsConf = DatastoreServiceConfig.Builder.withDeadline(20);
    adminDsWithMockDelegate = AdminDatastoreService.getInstance(dsConf, "koko", "ns1");
    assertThat(adminDsWithMockDelegate.getAppId()).isEqualTo("koko");
    assertThat(adminDsWithMockDelegate.getNamespace()).isEqualTo("ns1");
    assertThat(
            ((BaseAsyncDatastoreServiceImpl) adminDsWithMockDelegate.getDelegate())
                .getDatastoreServiceConfig()
                .getDeadline())
        .isEqualTo(20.0);
  }

  @Test
  public void testNewQueryBuilder() {
    adminDsWithMockDelegate = AdminDatastoreService.getInstance("koko");
    Query kindlessQuery = adminDsWithMockDelegate.newQueryBuilder().build();
    assertThat(kindlessQuery.getKind()).isNull();
    assertThat(kindlessQuery.getAppIdNamespace()).isEqualTo(new AppIdNamespace("koko", ""));
    Query kindQuery = adminDsWithMockDelegate.newQueryBuilder("kind1").build();
    assertThat(kindQuery.getKind()).isEqualTo("kind1");
    assertThat(kindQuery.getAppIdNamespace()).isEqualTo(new AppIdNamespace("koko", ""));
    assertThat(adminDsWithMockDelegate.newQueryBuilder().setKind("kind1").build())
        .isEqualTo(kindQuery);
  }

  @Test
  public void testNewKeyBuilder() {
    KeyBuilder keyBuilder = adminDsWithMockDelegate.newKeyBuilder("kind0");
    assertThat(new Key("kind0", null, 0, null, APP_ID_NS).equals(keyBuilder.build(), false))
        .isTrue();
    keyBuilder.setName("name1");
    assertThat(keyBuilder.build()).isEqualTo(new Key("kind0", null, 0, "name1", APP_ID_NS));
    keyBuilder.setKind("kind1").setName(null).setId(10L);
    assertThat(keyBuilder.build()).isEqualTo(new Key("kind1", null, 10L, null, APP_ID_NS));
    Key parent = KeyFactory.createKey("kind2", "name2");
    keyBuilder.setParent(parent);
    validateIllegalArgumentException(keyBuilder, "build");
    parent = new Key("kind2", null, 0, "name2", APP_ID_NS);
    keyBuilder.setParent(parent);
    assertThat(keyBuilder.build()).isEqualTo(new Key("kind1", parent, 10L, null, APP_ID_NS));
  }

  @Test
  public void testNewEntityBuilder() {
    EntityBuilder entityBuilder = adminDsWithMockDelegate.newEntityBuilder("kind1");
    entityBuilder.setName("name1");
    Entity entity = entityBuilder.build();
    assertThat(entity.getKey()).isEqualTo(new Key("kind1", null, 0, "name1", APP_ID_NS));
    assertThat(entity.getAppId()).isEqualTo("koko");
    assertThat(entity.getKind()).isEqualTo("kind1");
    assertThat(entity.getProperties()).isEmpty();
    entityBuilder.setKind("kind2").setName(null).setId(10L);
    entity = entityBuilder.build();
    assertThat(entity.getKey()).isEqualTo(new Key("kind2", null, 10L, null, APP_ID_NS));
    Key parent = KeyFactory.createKey("kind3", "name2");
    entityBuilder.setParent(parent);
    validateIllegalArgumentException(entityBuilder, "build");
    parent = new Key("kind3", null, 0, "name2", APP_ID_NS);
    entityBuilder.setParent(parent);
    entity = entityBuilder.build();
    assertThat(entity.getKey()).isEqualTo(new Key("kind2", parent, 10L, null, APP_ID_NS));
  }

  @Test
  public void testCompositeIndexForQuery() {
    Query query = new Query("kind1");
    FetchOptions fo = FetchOptions.Builder.withDefaults();
    DatastoreV3Pb.Query queryPb = QueryTranslator.convertToPb(query, fo);
    CompositeIndexManager.IndexComponentsOnlyQuery ic =
        new CompositeIndexManager.IndexComponentsOnlyQuery(queryPb);
    Index index =
        new Index(
            0,
            "kind1",
            true,
            ImmutableList.of(new Index.Property("p1", Query.SortDirection.DESCENDING)));
    OnestoreEntity.Index indexPb = IndexTranslator.convertToPb(index);
    when(indexManager.compositeIndexForQuery(ic)).thenReturn(indexPb.toBuilder());
    assertThat(adminDsWithMockDelegate.compositeIndexForQuery(query)).isEqualTo(index);
  }

  @Test
  public void testMinimumCompositeIndexForQuery() {
    Query query = new Query("kind1");
    FetchOptions fo = FetchOptions.Builder.withDefaults();
    DatastoreV3Pb.Query queryPb = QueryTranslator.convertToPb(query, fo);
    CompositeIndexManager.IndexComponentsOnlyQuery ic =
        new CompositeIndexManager.IndexComponentsOnlyQuery(queryPb);
    Index index =
        new Index(
            0,
            "kind1",
            true,
            ImmutableList.of(new Index.Property("p1", Query.SortDirection.DESCENDING)));
    OnestoreEntity.Index indexPb = IndexTranslator.convertToPb(index);
    Collection<OnestoreEntity.Index> indexPbList = ImmutableList.of(indexPb);
    when(indexManager.minimumCompositeIndexForQuery(ic, indexPbList)).thenReturn(indexPb.toBuilder());
    Index minimumIndex =
        adminDsWithMockDelegate.minimumCompositeIndexForQuery(query, ImmutableList.of(index));
    assertThat(minimumIndex).isEqualTo(index);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testMinimumCompositeIndexForInequalityQuery() {
    Query query = new Query("kind1");
    query.setFilter(
        CompositeFilterOperator.and(
            new FilterPredicate("a", FilterOperator.EQUAL, 1),
            new FilterPredicate("b", FilterOperator.LESS_THAN, 2)));

    Query expectedQuery = new Query("kind1");
    expectedQuery.addFilter("a", FilterOperator.EQUAL, 1);
    expectedQuery.addFilter("b", FilterOperator.LESS_THAN, 2);

    Collection<Index> indexList = ImmutableList.of();
    Collection<OnestoreEntity.Index> indexPbList = ImmutableList.of();

    Index expectedIndex =
        new Index(
            0,
            "kind1",
            true,
            Lists.newArrayList(
                new Index.Property("a", Query.SortDirection.DESCENDING),
                new Index.Property("b", Query.SortDirection.DESCENDING)));
    OnestoreEntity.Index expectedIndexPb = IndexTranslator.convertToPb(expectedIndex);
    // We cannot predict the order of predicates in the IndexComponentsOnlyQuery.
    when(indexManager.minimumCompositeIndexForQuery(notNull(), eq(indexPbList)))
        .thenReturn(expectedIndexPb.toBuilder());
    Index minimumIndex = adminDsWithMockDelegate.minimumCompositeIndexForQuery(query, indexList);
    assertThat(minimumIndex).isEqualTo(expectedIndex);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testMinimumCompositeIndexWithOr() {
    Query query = new Query("kind1");
    query.setFilter(
        CompositeFilterOperator.and(
            CompositeFilterOperator.or(
                new FilterPredicate("a", FilterOperator.EQUAL, 1),
                new FilterPredicate("b", FilterOperator.EQUAL, 1)),
            new FilterPredicate("c", FilterOperator.LESS_THAN, 2)));

    Query firstQuery = new Query("kind1");
    firstQuery.addFilter("a", FilterOperator.EQUAL, 1);
    firstQuery.addFilter("c", FilterOperator.LESS_THAN, 2);

    Query secondQuery = new Query("kind1");
    secondQuery.addFilter("b", FilterOperator.EQUAL, 1);
    secondQuery.addFilter("c", FilterOperator.LESS_THAN, 2);

    Collection<Index> indexList = ImmutableList.of();
    Collection<OnestoreEntity.Index> indexPbList = ImmutableList.of();

    Index expectedFirstIndex =
        new Index(
            0,
            "kind1",
            true,
            Lists.newArrayList(
                new Index.Property("a", Query.SortDirection.DESCENDING),
                new Index.Property("c", Query.SortDirection.DESCENDING)));
    OnestoreEntity.Index expectedFirstIndexPb = IndexTranslator.convertToPb(expectedFirstIndex);

    Index expectedSecondIndex =
        new Index(
            0,
            "kind1",
            true,
            Lists.newArrayList(
                new Index.Property("b", Query.SortDirection.DESCENDING),
                new Index.Property("c", Query.SortDirection.DESCENDING)));
    OnestoreEntity.Index expectedSecondIndexPb = IndexTranslator.convertToPb(expectedSecondIndex);

    // We cannot predict the order of predicates in the IndexComponentsOnlyQuery.
    when(indexManager.minimumCompositeIndexForQuery(notNull(), eq(indexPbList)))
        .thenReturn(expectedFirstIndexPb.toBuilder())
        .thenReturn(expectedSecondIndexPb.toBuilder());

    Set<Index> minimumIndexes =
        adminDsWithMockDelegate.minimumCompositeIndexesForQuery(query, indexList);

    assertThat(minimumIndexes).containsExactly(expectedFirstIndex, expectedSecondIndex);
  }

  @Test
  public void testPrepareQuery() {
    PreparedQuery pQuery = mock(PreparedQuery.class);
    Query goodQuery = adminDsWithMockDelegate.newQueryBuilder("kind1").build();
    Query badQuery = new Query("kind1");
    when(delegate.prepare(goodQuery)).thenReturn(pQuery);
    when(delegate.prepare(transaction, goodQuery)).thenReturn(pQuery);
    validateIllegalArgumentException(adminDsWithMockDelegate, "prepare", badQuery);
    validateIllegalArgumentException(adminDsWithMockDelegate, "prepare", transaction, badQuery);
    assertThat(adminDsWithMockDelegate.prepare(goodQuery)).isEqualTo(pQuery);
    assertThat(adminDsWithMockDelegate.prepare(transaction, goodQuery)).isEqualTo(pQuery);
  }

  @Test
  public void testGetCurrentTransaction() {
    when(delegate.getCurrentTransaction()).thenReturn(transaction);
    when(delegate.getCurrentTransaction(transaction)).thenReturn(transaction);
    assertThat(adminDsWithMockDelegate.getCurrentTransaction()).isEqualTo(transaction);
    assertThat(adminDsWithMockDelegate.getCurrentTransaction(transaction)).isEqualTo(transaction);
  }

  @Test
  public void testGetActiveTransactions() {
    Collection<Transaction> noTrans = ImmutableList.of();
    when(delegate.getActiveTransactions()).thenReturn(noTrans);
    assertThat(adminDsWithMockDelegate.getActiveTransactions()).containsExactlyElementsIn(noTrans);
  }

  @Test
  public void testBeginTransaction() throws InterruptedException, ExecutionException {
    when(delegate.beginTransaction()).thenReturn(immediateFuture(transaction));
    TransactionOptions ops = TransactionOptions.Builder.withDefaults();
    when(delegate.beginTransaction(ops)).thenReturn(immediateFuture(transaction));
    assertThat(adminDsWithMockDelegate.beginTransaction().get()).isEqualTo(transaction);
    assertThat(adminDsWithMockDelegate.beginTransaction(ops).get()).isEqualTo(transaction);
    Transaction tx = adminDsWithLocalDsDelegate.beginTransaction().get();
    assertThat(tx.getApp()).isEqualTo(APP_ID_NS.getAppId());
    tx.rollback();
  }

  @Test
  public void testGet() throws InterruptedException, ExecutionException {
    Key badKey = KeyFactory.createKey("kind", "name");
    Key goodKey = adminDsWithMockDelegate.newKeyBuilder("kind").setName("name").build();
    Iterable<Key> badKeys = Lists.newArrayList(goodKey, badKey);
    Iterable<Key> goodKeys = Lists.newArrayList(goodKey, goodKey);
    Entity entity = new Entity(goodKey);
    Map<Key, Entity> entities = ImmutableMap.of(goodKey, entity);
    when(delegate.get(goodKey)).thenReturn(immediateFuture(entity));
    when(delegate.get(transaction, goodKey)).thenReturn(immediateFuture(entity));
    when(delegate.get(goodKeys)).thenReturn(immediateFuture(entities));
    when(delegate.get(transaction, goodKeys)).thenReturn(immediateFuture(entities));
    validateIllegalArgumentException(adminDsWithMockDelegate, "get", badKey);
    validateIllegalArgumentException(adminDsWithMockDelegate, "get", transaction, badKey);
    validateIllegalArgumentException(adminDsWithMockDelegate, "get", badKeys);
    validateIllegalArgumentException(adminDsWithMockDelegate, "get", transaction, badKeys);
    assertThat(adminDsWithMockDelegate.get(goodKey).get()).isEqualTo(entity);
    assertThat(adminDsWithMockDelegate.get(transaction, goodKey).get()).isEqualTo(entity);
    assertThat(adminDsWithMockDelegate.get(goodKeys).get()).isEqualTo(entities);
    assertThat(adminDsWithMockDelegate.get(transaction, goodKeys).get()).isEqualTo(entities);
  }

  @Test
  public void testPut() throws InterruptedException, ExecutionException {
    Key key = adminDsWithMockDelegate.newKeyBuilder("kind").setName("name").build();
    List<Key> keys = Lists.newArrayList(key);
    Entity goodEntity = new Entity(key);
    Entity badEntity = new Entity(KeyFactory.createKey("kind", "name"));
    List<Entity> goodEntities = Lists.newArrayList(goodEntity);
    List<Entity> badEntities = Lists.newArrayList(goodEntity, badEntity);
    when(delegate.put(goodEntity)).thenReturn(immediateFuture(key));
    when(delegate.put(transaction, goodEntity)).thenReturn(immediateFuture(key));
    when(delegate.put(goodEntities)).thenReturn(immediateFuture(keys));
    when(delegate.put(transaction, goodEntities)).thenReturn(immediateFuture(keys));
    validateIllegalArgumentException(adminDsWithMockDelegate, "put", badEntity);
    validateIllegalArgumentException(adminDsWithMockDelegate, "put", transaction, badEntity);
    validateIllegalArgumentException(adminDsWithMockDelegate, "put", badEntities);
    validateIllegalArgumentException(adminDsWithMockDelegate, "put", transaction, badEntities);
    assertThat(adminDsWithMockDelegate.put(goodEntity).get()).isEqualTo(key);
    assertThat(adminDsWithMockDelegate.put(transaction, goodEntity).get()).isEqualTo(key);
    assertThat(adminDsWithMockDelegate.put(goodEntities).get()).isEqualTo(keys);
    assertThat(adminDsWithMockDelegate.put(transaction, goodEntities).get()).isEqualTo(keys);
  }

  @Test
  public void testDeleteKeyArray() {
    Key goodKey = adminDsWithMockDelegate.newKeyBuilder("kind").setName("name").build();
    Iterable<Key> goodKeys = Lists.newArrayList(goodKey, goodKey);
    Key badKey = KeyFactory.createKey("kind", "name");
    Iterable<Key> badKeys = Lists.newArrayList(badKey, goodKey);
    when(delegate.delete(goodKey)).thenReturn(null);
    when(delegate.delete(transaction, goodKey)).thenReturn(null);
    when(delegate.delete(goodKeys)).thenReturn(null);
    when(delegate.delete(transaction, goodKeys)).thenReturn(null);
    validateIllegalArgumentException(
        adminDsWithMockDelegate, "delete", new Object[] {new Key[] {badKey}});
    validateIllegalArgumentException(
        adminDsWithMockDelegate, "delete", transaction, new Key[] {badKey});
    validateIllegalArgumentException(adminDsWithMockDelegate, "delete", badKeys);
    validateIllegalArgumentException(adminDsWithMockDelegate, "delete", transaction, badKeys);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = adminDsWithMockDelegate.delete(goodKey);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError1 = adminDsWithMockDelegate.delete(transaction, goodKey);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError2 = adminDsWithMockDelegate.delete(goodKeys);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError3 = adminDsWithMockDelegate.delete(transaction, goodKeys);
  }

  @Test
  public void testAllocateIds() throws InterruptedException, ExecutionException {
    KeyRange keyRange = adminDsWithLocalDsDelegate.allocateIds("kind1", 10).get();
    validateKeyRange(keyRange, "kind1", null, 10, APP_ID_NS);
    Key parent = adminDsWithLocalDsDelegate.newKeyBuilder("pkind").setId(1L).build();
    keyRange = adminDsWithLocalDsDelegate.allocateIds(parent, "kind2", 8).get();
    validateKeyRange(keyRange, "kind2", parent, 8, APP_ID_NS);
    Key badParent = KeyFactory.createKey("bpkind", 10);
    validateIllegalArgumentException(
        adminDsWithLocalDsDelegate, "allocateIds", badParent, "kind3", 5L);
  }

  private static void validateKeyRange(
      KeyRange keyRange, String kind, Key parent, int size, AppIdNamespace appIdNamespace) {
    assertThat(keyRange.getKind()).isEqualTo(kind);
    assertThat(keyRange.getSize()).isEqualTo(size);
    assertThat(keyRange.getParent()).isEqualTo(parent);
    assertThat(keyRange.getAppIdNamespace()).isEqualTo(appIdNamespace);
    long startId = 0;
    int count = 0;
    for (Key key : keyRange) {
      assertThat(key.getAppIdNamespace()).isEqualTo(APP_ID_NS);
      assertThat(key.getKind()).isEqualTo(kind);
      assertThat(key.getParent()).isEqualTo(parent);
      assertThat(key.getId()).isGreaterThan(startId);
      startId = key.getId();
      count++;
    }
    assertThat(count).isEqualTo(size);
  }

  @Test
  public void testGetDatastoreAttributes() throws InterruptedException, ExecutionException {
    DatastoreAttributes attributes = new DatastoreAttributes();
    when(delegate.getDatastoreAttributes()).thenReturn(immediateFuture(attributes));
    assertThat(adminDsWithMockDelegate.getDatastoreAttributes().get()).isEqualTo(attributes);
    assertThat(adminDsWithLocalDsDelegate.getDatastoreAttributes().get().getDatastoreType())
        .isEqualTo(DatastoreType.HIGH_REPLICATION);
  }

  @Test
  public void testGetIndexes() throws InterruptedException, ExecutionException {
    Map<Index, IndexState> indexes = ImmutableMap.of();
    when(delegate.getIndexes()).thenReturn(immediateFuture(indexes));
    assertThat(adminDsWithMockDelegate.getIndexes().get()).isEqualTo(indexes);
  }

  private static void validateIllegalArgumentException(
      Object target, String methodName, Object... arguments) {
    for (Method m : target.getClass().getMethods()) {
      if (m.getName().equals(methodName) && checkTypeMatching(m.getParameterTypes(), arguments)) {
        InvocationTargetException ite =
            assertThrows(InvocationTargetException.class, () -> m.invoke(target, arguments));
        assertThat(ite).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        return;
      }
    }
    assertWithMessage("Method %s was not found", methodName).fail();
  }

  private static boolean checkTypeMatching(Class<?>[] types, Object[] values) {
    if (types.length != values.length) {
      return false;
    }
    for (int i = 0; i < types.length; i++) {
      Class<?> paramType = types[i].isPrimitive() ? Primitives.wrap(types[i]) : types[i];
      Class<?> valueType = values[i].getClass();
      if (!paramType.isAssignableFrom(valueType)) {
        return false;
      }
    }
    return true;
  }
}
