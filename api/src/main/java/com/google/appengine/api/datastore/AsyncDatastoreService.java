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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An asynchronous version of {@link DatastoreService}. All methods return immediately and provide
 * {@link Future Futures} as their return values.
 *
 * <p>The key difference between implementations of {@link AsyncDatastoreService} and
 * implementations of {@link DatastoreService} is that async implementations do not perform implicit
 * transaction management. The reason is that implicit transaction management requires automatic
 * commits of some transactions, and without some sort of callback mechanism there is no way to
 * determine that a put/get/delete that has been implicitly enrolled in a transaction is complete
 * and therefore ready to be committed. See {@link ImplicitTransactionManagementPolicy} for more
 * information.
 *
 */
public interface AsyncDatastoreService extends BaseDatastoreService {
  /** See {@link DatastoreService#get(Key)}. */
  Future<Entity> get(Key key);

  /** See {@link DatastoreService#get(Transaction, Key)}. */
  Future<Entity> get(@Nullable Transaction txn, Key key);

  /** See {@link DatastoreService#get(Iterable)}. */
  Future<Map<Key, Entity>> get(Iterable<Key> keys);

  /** See {@link DatastoreService#get(Transaction, Iterable)}. */
  Future<Map<Key, Entity>> get(@Nullable Transaction txn, Iterable<Key> keys);

  /** See {@link DatastoreService#put(Entity)}. */
  Future<Key> put(Entity entity);

  /** See {@link DatastoreService#put(Transaction, Entity)}. */
  Future<Key> put(@Nullable Transaction txn, Entity entity);

  /** See {@link DatastoreService#put(Iterable)}. */
  Future<List<Key>> put(Iterable<Entity> entities);

  /** See {@link DatastoreService#put(Transaction, Iterable)}. */
  Future<List<Key>> put(@Nullable Transaction txn, Iterable<Entity> entities);

  /** See {@link DatastoreService#delete(Key...)}. */
  Future<Void> delete(Key... keys);

  /** See {@link DatastoreService#delete(Transaction, Iterable)}. */
  Future<Void> delete(@Nullable Transaction txn, Key... keys);

  /** See {@link DatastoreService#delete(Iterable)}. */
  Future<Void> delete(Iterable<Key> keys);

  /** See {@link DatastoreService#delete(Transaction, Iterable)}. */
  Future<Void> delete(@Nullable Transaction txn, Iterable<Key> keys);

  /** See {@link DatastoreService#beginTransaction()}. */
  Future<Transaction> beginTransaction();

  /** See {@link DatastoreService#beginTransaction(TransactionOptions)}. */
  Future<Transaction> beginTransaction(TransactionOptions options);

  /** See {@link DatastoreService#allocateIds(String, long)}. */
  Future<KeyRange> allocateIds(String kind, long num);

  /** See {@link DatastoreService#allocateIds(Key, String, long)}. */
  Future<KeyRange> allocateIds(@Nullable Key parent, String kind, long num);

  /** See {@link DatastoreService#getDatastoreAttributes()}. */
  Future<DatastoreAttributes> getDatastoreAttributes();

  /** See {@link DatastoreService#getIndexes()}. */
  Future<Map<Index, Index.IndexState>> getIndexes();
}
