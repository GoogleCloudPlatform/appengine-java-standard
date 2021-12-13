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

import static com.google.appengine.api.datastore.FutureHelper.quietGet;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link DatastoreService} that farms out all calls to a provided {@link
 * AsyncDatastoreService}.
 *
 */
final class DatastoreServiceImpl implements DatastoreService {

  private final AsyncDatastoreServiceInternal async;

  public DatastoreServiceImpl(AsyncDatastoreServiceInternal async) {
    this.async = async;
  }

  @Override
  public Transaction getCurrentTransaction() {
    return async.getCurrentTransaction();
  }

  @Override
  public Transaction getCurrentTransaction(Transaction returnedIfNoTxn) {
    return async.getCurrentTransaction(returnedIfNoTxn);
  }

  @Override
  public Entity get(Transaction txn, Key key) throws EntityNotFoundException {
    return quietGet(async.get(txn, key), EntityNotFoundException.class);
  }

  @Override
  public Entity get(Key key) throws EntityNotFoundException {
    return quietGet(async.get(key), EntityNotFoundException.class);
  }

  @Override
  public Map<Key, Entity> get(Iterable<Key> keys) {
    return quietGet(async.get(keys));
  }

  @Override
  public Map<Key, Entity> get(Transaction txn, Iterable<Key> keys) {
    return quietGet(async.get(txn, keys));
  }

  @Override
  public Key put(Entity entity) {
    return quietGet(async.put(entity));
  }

  @Override
  public Key put(Transaction txn, Entity entity) {
    return quietGet(async.put(txn, entity));
  }

  @Override
  public List<Key> put(Iterable<Entity> entities) {
    return quietGet(async.put(entities));
  }

  @Override
  public List<Key> put(Transaction txn, Iterable<Entity> entities) {
    return quietGet(async.put(txn, entities));
  }

  @Override
  public void delete(Key... keys) {
    quietGet(async.delete(keys));
  }

  @Override
  public void delete(Transaction txn, Key... keys) {
    quietGet(async.delete(txn, keys));
  }

  @Override
  public void delete(Iterable<Key> keys) {
    quietGet(async.delete(keys));
  }

  @Override
  public void delete(Transaction txn, Iterable<Key> keys) {
    quietGet(async.delete(txn, keys));
  }

  @Override
  public PreparedQuery prepare(Query query) {
    return async.prepare(query);
  }

  @Override
  public PreparedQuery prepare(Transaction txn, Query query) {
    return async.prepare(txn, query);
  }

  @Override
  public Collection<Transaction> getActiveTransactions() {
    return async.getActiveTransactions();
  }

  @Override
  public KeyRange allocateIds(String kind, long num) {
    return quietGet(async.allocateIds(kind, num));
  }

  @Override
  public KeyRange allocateIds(Key parent, String kind, long num) {
    return quietGet(async.allocateIds(parent, kind, num));
  }

  @Override
  public KeyRangeState allocateIdRange(KeyRange range) {
    return quietGet(async.allocateIdRange(range));
  }

  @Override
  public Transaction beginTransaction() {
    return quietGet(async.beginTransaction());
  }

  @Override
  public Transaction beginTransaction(TransactionOptions options) {
    return quietGet(async.beginTransaction(options));
  }

  @Override
  public DatastoreAttributes getDatastoreAttributes() {
    return quietGet(async.getDatastoreAttributes());
  }

  @Override
  public Map<Index, Index.IndexState> getIndexes() {
    return quietGet(async.getIndexes());
  }
}
