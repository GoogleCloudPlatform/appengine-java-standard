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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.CompositeIndexManager.IndexComponentsOnlyQuery;
import com.google.appengine.api.datastore.Index.IndexState;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.apphosting.api.AppEngineInternal;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An AsyncDatastoreService implementation that is pinned to a specific appId and namesapce. This
 * implementation ignores the "current" appId provided by {@code
 * ApiProxy.getCurrentEnvironment().getAppId()} and the "current" namespace provided by {@code
 * NamespaceManager.get()}. Note, this is particularly important in the following methods:
 *
 * <ul>
 *   <li>{@link AsyncDatastoreService#getIndexes()}
 *   <li>{@link AsyncDatastoreService#getDatastoreAttributes()}
 *   <li>{@link AsyncDatastoreService#allocateIds(String, long)}
 * </ul>
 *
 * In addition this class provides ways to create {@link Query}, {@link Entity} and {@link Key} that
 * are pinned to the same appId/namespace.
 *
 * <p>Note: users should not access this class directly.
 *
 */
@AppEngineInternal
public final class AdminDatastoreService implements AsyncDatastoreService {

  /** A {@link Query} builder that pins it to the AdminUtils {@code appId} and {@code namespace}. */
  public static final class QueryBuilder {

    private final AppIdNamespace appIdNamespace;
    private String kind;

    private QueryBuilder(AppIdNamespace appIdNamespace) {
      this.appIdNamespace = appIdNamespace;
    }

    public QueryBuilder setKind(String kind) {
      this.kind = kind;
      return this;
    }

    public String getKind() {
      return kind;
    }

    public Query build() {
      return new Query(kind, null, null, false, appIdNamespace, false);
    }
  }

  private abstract static class AbstractKeyBuilder<T extends AbstractKeyBuilder<T>> {
    protected final AppIdNamespace appIdNamespace;
    protected String kind;
    protected String name;
    protected Long id;
    protected Key parent;

    protected AbstractKeyBuilder(AppIdNamespace appIdNamespace, String kind) {
      this.appIdNamespace = appIdNamespace;
      setKind(kind);
    }

    public String getKind() {
      return kind;
    }

    @SuppressWarnings("unchecked")
    public T setKind(String kind) {
      checkNotNull(kind);
      this.kind = kind;
      return (T) this;
    }

    public String getName() {
      return name;
    }

    @SuppressWarnings("unchecked")
    public T setName(String name) {
      this.name = name;
      return (T) this;
    }

    public Long getId() {
      return id;
    }

    protected long getIdAsPrimitiveLong() {
      return id == null ? Key.NOT_ASSIGNED : id;
    }

    @SuppressWarnings("unchecked")
    public T setId(Long id) {
      this.id = id;
      return (T) this;
    }

    public Key getParent() {
      return parent;
    }

    /**
     * When {@code parent} is not {@code null} its application and namespace will be used instead of
     * the ones associated with AdminUtils.
     */
    @SuppressWarnings("unchecked")
    public T setParent(Key parent) {
      this.parent = parent;
      return (T) this;
    }

    protected Key createKey() {
      return new Key(kind, parent, getIdAsPrimitiveLong(), name, appIdNamespace);
    }
  }

  /** A {@link Key} builder that pins it to the AdminUtils {@code appId} and {@code namespace}. */
  public static final class KeyBuilder extends AbstractKeyBuilder<KeyBuilder> {

    private KeyBuilder(AppIdNamespace appIdNamespace, String kind) {
      super(appIdNamespace, kind);
    }

    public Key build() {
      return createKey();
    }
  }

  /**
   * An {@link Entity} builder that pins it to the AdminUtils {@code appId} and {@code namespace}.
   */
  public static final class EntityBuilder extends AbstractKeyBuilder<EntityBuilder> {

    private EntityBuilder(AppIdNamespace appIdNamespace, String kind) {
      super(appIdNamespace, kind);
    }

    public Entity build() {
      return new Entity(createKey());
    }
  }

  private final AsyncDatastoreService delegate;
  private final AppIdNamespace appIdNamespace;

  // @VisibleForTesting
  interface AsyncDatastoreServiceFactory {
    AsyncDatastoreService getInstance(DatastoreServiceConfig config);

    CompositeIndexManager getCompositeIndexManager();
  }

  // @VisibleForTesting
  static AsyncDatastoreServiceFactory factory =
      new AsyncDatastoreServiceFactory() {
        @Override
        public AsyncDatastoreService getInstance(DatastoreServiceConfig config) {
          return DatastoreServiceFactory.getAsyncDatastoreService(config);
        }

        @Override
        public CompositeIndexManager getCompositeIndexManager() {
          return new CompositeIndexManager();
        }
      };

  private AdminDatastoreService(DatastoreServiceConfig config, String appId, String namespace) {
    appIdNamespace = new AppIdNamespace(appId, namespace);
    config = new DatastoreServiceConfig(config).appIdNamespace(appIdNamespace);
    delegate = factory.getInstance(config);
  }

  /** Returns an AdminUtils instance for the given {@code appId} and the "" (empty) namespace. */
  public static AdminDatastoreService getInstance(String appId) {
    return getInstance(DatastoreServiceConfig.Builder.withDefaults(), appId, "");
  }

  /** Returns an AdminUtils instance for the given {@code appId} and {@code namespace}. */
  public static AdminDatastoreService getInstance(String appId, String namespace) {
    return getInstance(DatastoreServiceConfig.Builder.withDefaults(), appId, namespace);
  }

  /** Returns an AdminUtils instance for the given {@code appId} and the "" (empty) namespace. */
  public static AdminDatastoreService getInstance(DatastoreServiceConfig config, String appId) {
    return getInstance(config, appId, "");
  }

  /** Returns an AdminUtils instance for the given {@code appId} and {@code namespace}. */
  public static AdminDatastoreService getInstance(
      DatastoreServiceConfig config, String appId, String namespace) {
    return new AdminDatastoreService(config, appId, namespace);
  }

  public String getAppId() {
    return appIdNamespace.getAppId();
  }

  public String getNamespace() {
    return appIdNamespace.getNamespace();
  }

  // @VisibleForTesting
  AsyncDatastoreService getDelegate() {
    return delegate;
  }

  public QueryBuilder newQueryBuilder() {
    return new QueryBuilder(appIdNamespace);
  }

  public QueryBuilder newQueryBuilder(String kind) {
    return new QueryBuilder(appIdNamespace).setKind(kind);
  }

  public KeyBuilder newKeyBuilder(String kind) {
    return new KeyBuilder(appIdNamespace, kind);
  }

  public EntityBuilder newEntityBuilder(String kind) {
    return new EntityBuilder(appIdNamespace, kind);
  }

  public Index compositeIndexForQuery(Query query) {
    Set<Index> resultIndexes = compositeIndexesForQuery(query);
    return Iterables.getFirst(resultIndexes, null);
  }

  public Set<Index> compositeIndexesForQuery(Query query) {
    List<DatastoreV3Pb.Query> pbQueries =
        convertQueryToPbs(query, FetchOptions.Builder.withDefaults());
    Set<Index> resultSet = new HashSet<Index>();
    for (DatastoreV3Pb.Query queryProto : pbQueries) {
      IndexComponentsOnlyQuery indexQuery = new IndexComponentsOnlyQuery(queryProto);

      OnestoreEntity.Index index =
          factory.getCompositeIndexManager().compositeIndexForQuery(indexQuery);
      if (index != null) {
        resultSet.add(IndexTranslator.convertFromPb(index));
      }
    }
    return resultSet;
  }

  public Index minimumCompositeIndexForQuery(Query query, Collection<Index> indexes) {
    Set<Index> resultIndexes = minimumCompositeIndexesForQuery(query, indexes);
    return Iterables.getFirst(resultIndexes, null);
  }

  public Set<Index> minimumCompositeIndexesForQuery(Query query, Collection<Index> indexes) {
    List<DatastoreV3Pb.Query> pbQueries =
        convertQueryToPbs(query, FetchOptions.Builder.withDefaults());

    List<OnestoreEntity.Index> indexPbs = Lists.newArrayListWithCapacity(indexes.size());
    for (Index index : indexes) {
      indexPbs.add(IndexTranslator.convertToPb(index));
    }

    Set<Index> resultSet = new HashSet<Index>();
    for (DatastoreV3Pb.Query queryProto : pbQueries) {
      IndexComponentsOnlyQuery indexQuery = new IndexComponentsOnlyQuery(queryProto);

      OnestoreEntity.Index index =
          factory.getCompositeIndexManager().minimumCompositeIndexForQuery(indexQuery, indexPbs);
      if (index != null) {
        resultSet.add(IndexTranslator.convertFromPb(index));
      }
    }
    return resultSet;
  }

  /** Convert a query to a list of ProtocolBuffer Queries. */
  @SuppressWarnings("deprecation")
  private static List<DatastoreV3Pb.Query> convertQueryToPbs(
      Query query, FetchOptions fetchOptions) {
    List<MultiQueryBuilder> queriesToRun = QuerySplitHelper.splitQuery(query);
    // All Filters should be in queriesToRun
    query.setFilter(null);
    query.getFilterPredicates().clear();
    List<DatastoreV3Pb.Query> resultQueries = new ArrayList<DatastoreV3Pb.Query>();
    for (MultiQueryBuilder multiQuery : queriesToRun) {
      for (List<List<FilterPredicate>> parallelQueries : multiQuery) {
        for (List<FilterPredicate> singleQuery : parallelQueries) {
          Query newQuery = new Query(query);
          newQuery.getFilterPredicates().addAll(singleQuery);
          DatastoreV3Pb.Query queryProto = QueryTranslator.convertToPb(newQuery, fetchOptions);
          resultQueries.add(queryProto);
        }
      }
    }
    return resultQueries;
  }

  // TODO: instead of implementing all of AsyncDatastoreService methods consider using
  // AbstractWrapper when it becomes publicly available.

  @Override
  public PreparedQuery prepare(Query query) {
    validateQuery(query);
    return delegate.prepare(query);
  }

  @Override
  public PreparedQuery prepare(Transaction txn, Query query) {
    validateQuery(query);
    return delegate.prepare(txn, query);
  }

  @Override
  public Transaction getCurrentTransaction() {
    return delegate.getCurrentTransaction();
  }

  @Override
  public Transaction getCurrentTransaction(Transaction returnedIfNoTxn) {
    return delegate.getCurrentTransaction(returnedIfNoTxn);
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return delegate.getActiveTransactions();
  }

  @Override
  public Future<Transaction> beginTransaction() {
    return delegate.beginTransaction();
  }

  @Override
  public Future<Transaction> beginTransaction(TransactionOptions options) {
    return delegate.beginTransaction(options);
  }

  @Override
  public Future<Entity> get(Key key) {
    validateKey(key);
    return delegate.get(key);
  }

  @Override
  public Future<Entity> get(@Nullable Transaction txn, Key key) {
    validateKey(key);
    return delegate.get(txn, key);
  }

  @Override
  public Future<Map<Key, Entity>> get(Iterable<Key> keys) {
    validateKeys(keys);
    return delegate.get(keys);
  }

  @Override
  public Future<Map<Key, Entity>> get(@Nullable Transaction txn, Iterable<Key> keys) {
    validateKeys(keys);
    return delegate.get(txn, keys);
  }

  @Override
  public Future<Key> put(Entity entity) {
    validateEntity(entity);
    return delegate.put(entity);
  }

  @Override
  public Future<Key> put(@Nullable Transaction txn, Entity entity) {
    validateEntity(entity);
    return delegate.put(txn, entity);
  }

  @Override
  public Future<List<Key>> put(Iterable<Entity> entities) {
    validateEntities(entities);
    return delegate.put(entities);
  }

  @Override
  public Future<List<Key>> put(@Nullable Transaction txn, Iterable<Entity> entities) {
    validateEntities(entities);
    return delegate.put(txn, entities);
  }

  @Override
  public Future<Void> delete(Key... keys) {
    validateKeys(Arrays.asList(keys));
    return delegate.delete(keys);
  }

  @Override
  public Future<Void> delete(@Nullable Transaction txn, Key... keys) {
    validateKeys(Arrays.asList(keys));
    return delegate.delete(txn, keys);
  }

  @Override
  public Future<Void> delete(Iterable<Key> keys) {
    validateKeys(keys);
    return delegate.delete(keys);
  }

  @Override
  public Future<Void> delete(@Nullable Transaction txn, Iterable<Key> keys) {
    validateKeys(keys);
    return delegate.delete(txn, keys);
  }

  @Override
  public Future<KeyRange> allocateIds(String kind, long num) {
    return delegate.allocateIds(kind, num);
  }

  @Override
  public Future<KeyRange> allocateIds(@Nullable Key parent, String kind, long num) {
    // AsyncDatastoreServiceImpl already validates that when parent is not
    // null it must matches the datastore appid/namespace
    return delegate.allocateIds(parent, kind, num);
  }

  @Override
  public Future<DatastoreAttributes> getDatastoreAttributes() {
    return delegate.getDatastoreAttributes();
  }

  @Override
  public Future<Map<Index, IndexState>> getIndexes() {
    return delegate.getIndexes();
  }

  private void validateQuery(Query query) {
    checkArgument(
        query.getAppIdNamespace().equals(appIdNamespace),
        "Query is associated with a different appId or namespace");
  }

  private void validateKey(Key key) {
    checkArgument(
        key.getAppIdNamespace().equals(appIdNamespace),
        "key %s is associated with a different appId or namespace",
        key);
  }

  private void validateKeys(Iterable<Key> keys) {
    for (Key key : keys) {
      validateKey(key);
    }
  }

  private void validateEntity(Entity entity) {
    checkArgument(
        entity.getAppIdNamespace().equals(appIdNamespace),
        "Entity %s is associated with a different appId or namespace",
        entity.getKey());
  }

  private void validateEntities(Iterable<Entity> entities) {
    for (Entity entity : entities) {
      validateEntity(entity);
    }
  }
}
