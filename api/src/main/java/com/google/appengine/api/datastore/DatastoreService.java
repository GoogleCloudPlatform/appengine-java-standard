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

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

// Note: Do not confuse this class with the class of the same name in
// com.google.apphosting.datastore, which is the *internal* Stubby
// *implementation* of the data service that is used by the appserver.
// This class is the *external* API that we present to user code,
// which simply forwards requests on to the appserver via ApiProxy.

// TODO that does not take a transaction should
// enlist the operation in the current transaction if the query is an ancestor.
// We have to wait for a major version upgrade to make this change otherwise
// we'll break existing apps that are running ancestor queries inside
// transactions that are associated with an entity group not equal to the
// entity group of the ancestor.

/**
 * The {@code DatastoreService} provides synchronous access to a schema-less data storage system.
 * The fundamental unit of data in this system is the {@code Entity}, which has an immutable
 * identity (represented by a {@code Key}) and zero or more mutable properties. {@code Entity}
 * objects can be created, updated, deleted, retrieved by identifier, and queried via a combination
 * of properties.
 *
 * <p>The {@code DatastoreService} can be used transactionally and supports the notion of a
 * "current" transaction. A current transaction is established by calling {@link
 * #beginTransaction()}. The transaction returned by this method ceases to be current when an
 * attempt is made to commit or rollback or when another call is made to {@link
 * #beginTransaction()}. A transaction can only be current within the Thread that created it.
 *
 * <p>The various overloads of put, get, and delete all support transactions. Users of this class
 * have the choice of explicitly passing a (potentially {@code null}) {@link Transaction} to these
 * methods or relying on the behavior governed by the {@link ImplicitTransactionManagementPolicy}.
 * If a user explicitly provides a {@link Transaction} it is up to the user to call {@link
 * Transaction#commit()} or {@link Transaction#rollback()} at the proper time. If a user relies on
 * implicit transaction management and the installed policy creates a transaction, that transaction
 * will be committed (in the case of a success) or rolled back (in the case of a failure) before the
 * operation returns to the user. The methods that manage transactions according to {@link
 * ImplicitTransactionManagementPolicy} are: {@link #delete(Key...)}, {@link #delete(Iterable)},
 * {@link #get(Key)}, {@link #get(Iterable)}, {@link #put(Entity)}, and {@link #put(Iterable)}.
 *
 * <p>The overload of prepare that takes a {@link Transaction} parameter behaves the same as the
 * overloads of put, get, and delete that take a {@link Transaction} parameter. However, the
 * overload of prepare that does not take a {@link Transaction} parameter, unlike put, get, and
 * delete, does not use an existing {@link Transaction} if one is already running and does not
 * consult the {@link ImplicitTransactionManagementPolicy} if one is not already running.
 */
public interface DatastoreService extends BaseDatastoreService {
  /**
   * Retrieves the {@code Entity} with the specified {@code Key}.
   *
   * <p>If there is a current transaction, this operation will execute within that transaction. In
   * this case it is up to the caller to commit or rollback. If there is no current transaction, the
   * behavior of this method with respect to transactions will be determined by the {@link
   * ImplicitTransactionManagementPolicy} available on the {@link DatastoreServiceConfig}.
   *
   * @throws EntityNotFoundException If the specified entity could not be found.
   * @throws IllegalArgumentException If the specified key is invalid.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  Entity get(Key key) throws EntityNotFoundException;

  /**
   * Exhibits the same behavior as {@link #get(Key)}, but executes within the provided transaction.
   * It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @throws IllegalStateException If {@code txn} is not null and not active.
   */
  Entity get(Transaction txn, Key key) throws EntityNotFoundException;

  /**
   * Retrieves the set of {@link Entity Entities} matching {@code keys}. The result {@code Map} will
   * only contain {@code Keys} for which {@code Entities} could be found.
   *
   * <p>If there is a current transaction, this operation will execute within that transaction. In
   * this case it is up to the caller to commit or rollback. If there is no current transaction, the
   * behavior of this method with respect to transactions will be determined by the {@link
   * ImplicitTransactionManagementPolicy} available on the {@link DatastoreServiceConfig}.
   *
   * @throws IllegalArgumentException If any {@code Key} in keys is invalid.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  Map<Key, Entity> get(Iterable<Key> keys);

  /**
   * Exhibits the same behavior as {@link #get(Iterable)}, but executes within the provided
   * transaction. It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @throws IllegalStateException If {@code txn} is not null and not active.
   */
  Map<Key, Entity> get(Transaction txn, Iterable<Key> keys);

  /**
   * If the specified {@code Entity} does not yet exist in the data store, create it and assign its
   * {@code Key}. If the specified {@code Entity} already exists in the data store, save the new
   * version.
   *
   * <p>The {@code Key} is returned, and is also returned by future calls to {@code
   * entity.getKey()}.
   *
   * <p>If there is a current transaction, this operation will execute within that transaction. In
   * this case it is up to the caller to commit or rollback. If there is no current transaction, the
   * behavior of this method with respect to transactions will be determined by the {@link
   * ImplicitTransactionManagementPolicy} available on the {@link DatastoreServiceConfig}.
   *
   * @throws IllegalArgumentException If the specified entity was incomplete.
   * @throws ConcurrentModificationException If the entity group to which the entity belongs was
   *     modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  Key put(Entity entity);

  /**
   * Exhibits the same behavior as {@link #put(Entity)}, but executes within the provided
   * transaction. It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @throws IllegalStateException If {@code txn} is not null and not active.
   * @throws ConcurrentModificationException If the entity group to which the entity belongs was
   *     modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  Key put(Transaction txn, Entity entity);

  /**
   * Performs a batch {@link #put(Entity) put} of all {@code entities}.
   *
   * <p>If there is a current transaction, this operation will execute within that transaction. In
   * this case it is up to the caller to commit or rollback. If there is no current transaction, the
   * behavior of this method with respect to transactions will be determined by the {@link
   * ImplicitTransactionManagementPolicy} available on the {@link DatastoreServiceConfig}.
   *
   * @return The {@code Key}s that were assigned to the entities that were put. If the {@code
   *     Iterable} that was provided as an argument has a stable iteration order the {@code Key}s in
   *     the {@code List} we return are in that same order. If the {@code Iterable} that was
   *     provided as an argument does not have a stable iteration order the order of the {@code
   *     Key}s in the {@code List} we return is undefined.
   * @throws IllegalArgumentException If any entity is incomplete.
   * @throws ConcurrentModificationException If an entity group to which any provided entity belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  List<Key> put(Iterable<Entity> entities);

  /**
   * Exhibits the same behavior as {@link #put(Iterable)}, but executes within the provided
   * transaction. It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @return The {@code Key}s that were assigned to the entities that were put. If the {@code
   *     Iterable} that was provided as an argument has a stable iteration order the {@code Key}s in
   *     the {@code List} we return are in that same order. If the {@code Iterable} that was
   *     provided as an argument does not have a stable iteration order the order of the {@code
   *     Key}s in the {@code List} we return is undefined.
   * @throws IllegalStateException If {@code txn} is not null and not active.
   * @throws ConcurrentModificationException If an entity group to which any provided entity belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  List<Key> put(Transaction txn, Iterable<Entity> entities);

  /**
   * Deletes the {@code Entity entities} specified by {@code keys}.
   *
   * <p>If there is a current transaction, this operation will execute within that transaction. In
   * this case it is up to the caller to commit or rollback. If there is no current transaction, the
   * behavior of this method with respect to transactions will be determined by the {@link
   * ImplicitTransactionManagementPolicy} available on the {@link DatastoreServiceConfig}.
   *
   * @throws IllegalArgumentException If the specified key was invalid.
   * @throws ConcurrentModificationException If an entity group to which any provided key belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  void delete(Key... keys);

  /**
   * Exhibits the same behavior as {@link #delete(Key...)}, but executes within the provided
   * transaction. It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @throws IllegalStateException If {@code txn} is not null and not active.
   * @throws ConcurrentModificationException If an entity group to which any provided key belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  void delete(Transaction txn, Key... keys);

  /**
   * Equivalent to {@link #delete(Key...)}.
   *
   * @throws ConcurrentModificationException If an entity group to which any provided key belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  void delete(Iterable<Key> keys);

  /**
   * Exhibits the same behavior as {@link #delete(Iterable)}, but executes within the provided
   * transaction. It is up to the caller to commit or rollback. Transaction can be null.
   *
   * @throws IllegalStateException If {@code txn} is not null and not active.
   * @throws ConcurrentModificationException If an entity group to which any provided key belongs
   *     was modified concurrently.
   * @throws DatastoreFailureException If any other datastore error occurs.
   */
  void delete(Transaction txn, Iterable<Key> keys);

  /**
   * Equivalent to {@code beginTransaction(TransactionOptions.Builder.withDefaults())}.
   *
   * @return the {@code Transaction} that was started.
   * @throws DatastoreFailureException If a datastore error occurs.
   * @see #beginTransaction(TransactionOptions)
   */
  Transaction beginTransaction();

  /**
   * Begins a transaction against the datastore. Callers are responsible for explicitly calling
   * {@link Transaction#commit()} or {@link Transaction#rollback()} when they no longer need the
   * {@code Transaction}.
   *
   * <p>The {@code Transaction} returned by this call will be considered the current transaction and
   * will be returned by subsequent, same-thread calls to {@link #getCurrentTransaction()} and
   * {@link #getCurrentTransaction(Transaction)} until one of the following happens: 1) {@link
   * #beginTransaction()} is invoked from the same thread. In this case {@link
   * #getCurrentTransaction()} and {@link #getCurrentTransaction(Transaction)} will return the
   * result of the more recent call to {@link #beginTransaction()}. 2) {@link Transaction#commit()}
   * is invoked on the {@link Transaction} returned by this method. Whether or not the commit
   * returns successfully, the {@code Transaction} will no longer be the current transaction. 3)
   * {@link Transaction#rollback()} ()} is invoked on the {@link Transaction} returned by this
   * method. Whether or not the rollback returns successfully, the {@code Transaction} will no
   * longer be the current transaction.
   *
   * @param options The options for the new transaction.
   * @return the {@code Transaction} that was started.
   * @throws DatastoreFailureException If a datastore error occurs.
   * @see #getCurrentTransaction()
   * @see TransactionOptions
   */
  Transaction beginTransaction(TransactionOptions options);

  /**
   * IDs are allocated within a namespace defined by a parent key and a kind. This method allocates
   * a contiguous range of unique IDs of size {@code num} within the namespace defined by a null
   * parent key (root entities) and the given kind.
   *
   * <p>IDs allocated in this manner may be provided manually to newly created entities. They will
   * not be used by the datastore's automatic ID allocator for root entities of the same kind.
   *
   * @param kind The kind for which the root entity IDs should be allocated.
   * @param num The number of IDs to allocate.
   * @return A {@code KeyRange} of size {@code num}.
   * @throws IllegalArgumentException If {@code num} is less than 1 or if {@code num} is greater
   *     than 1 billion.
   * @throws DatastoreFailureException If a datastore error occurs.
   */
  KeyRange allocateIds(String kind, long num);

  /**
   * IDs are allocated within a namespace defined by a parent key and a kind. This method allocates
   * a contiguous range of unique IDs of size {@code num} within the namespace defined by the given
   * parent key and the given kind.
   *
   * <p>IDs allocated in this manner may be provided manually to newly created entities. They will
   * not be used by the datastore's automatic ID allocator for entities with the same kind and
   * parent.
   *
   * @param parent The key for which the child entity IDs should be allocated. Can be null.
   * @param kind The kind for which the child entity IDs should be allocated.
   * @param num The number of IDs to allocate.
   * @return A range of IDs of size {@code num} that are guaranteed to be unique.
   * @throws IllegalArgumentException If {@code parent} is not a complete key, if {@code num} is
   *     less than 1, or if {@code num} is greater than 1 billion.
   * @throws DatastoreFailureException If a datastore error occurs.
   */
  KeyRange allocateIds(Key parent, String kind, long num);

  /**
   * Indicates the state of a {@link KeyRange}.
   *
   * @see DatastoreService#allocateIdRange(KeyRange)
   */
  enum KeyRangeState {
    /**
     * Indicates the given {@link KeyRange} is empty and the datastore's automatic ID allocator will
     * not assign keys in this range to new entities.
     */
    EMPTY,
    /**
     * Indicates the given {@link KeyRange} is empty but the datastore's automatic ID allocator may
     * assign new entities keys in this range. However it is safe to manually assign {@link Key
     * Keys} in this range if either of the following is true:
     *
     * <ul>
     *   <li>No other request will insert entities with the same kind and parent as the given {@link
     *       KeyRange} until all entities with manually assigned keys from this range have been
     *       written.
     *   <li>Overwriting entities written by other requests with the same kind and parent as the
     *       given {@link KeyRange} is acceptable.
     * </ul>
     *
     * <p>The datastore's automatic ID allocator will not assign a key to a new entity that will
     * overwrite an existing entity, so once the range is populated there will no longer be any
     * contention.
     */
    CONTENTION,
    /**
     * Indicates that entities with keys inside the given {@link KeyRange} already exist and writing
     * to this range will overwrite those entities. Additionally the implications of {@link
     * #CONTENTION} apply. If overwriting entities that exist in this range is acceptable it is safe
     * to use the given range.
     *
     * <p>The datastore's automatic ID allocator will never assign a key to a new entity that will
     * overwrite an existing entity so entities written by the user to this range will never be
     * overwritten by an entity with an automatically assigned key.
     */
    COLLISION,
  }

  /**
   * This method allocates a user-specified contiguous range of unique IDs.
   *
   * <p>Once these IDs have been allocated they may be provided manually to newly created entities.
   *
   * <p>Since the datastore's automatic ID allocator will never assign a key to a new entity that
   * will cause an existing entity to be overwritten, entities written to the given {@link KeyRange}
   * will never be overwritten. However, writing entities with manually assigned keys in this range
   * may overwrite existing entities (or new entities written by a separate request) depending on
   * the {@link KeyRangeState} returned.
   *
   * <p>This method should only be used if you have an existing numeric id range that you want to
   * reserve, e.g. bulk loading entities that already have IDs. If you don't care about which IDs
   * you receive, use {@link #allocateIds} instead.
   *
   * @param range The key range to allocate.
   * @return The state of the id range allocated.
   * @throws DatastoreFailureException If a datastore error occurs.
   */
  KeyRangeState allocateIdRange(KeyRange range);

  /**
   * Retrieves the current datastore's attributes.
   *
   * @return The attributes of the datastore used to fulfill requests.
   * @throws DatastoreFailureException If a datastore error occurs.
   */
  DatastoreAttributes getDatastoreAttributes();

  /** Returns the application indexes and their states. */
  Map<Index, Index.IndexState> getIndexes();
}
