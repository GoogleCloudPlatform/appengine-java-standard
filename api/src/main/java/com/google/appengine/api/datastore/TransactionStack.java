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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Describes an object that helps us decide which transaction to use when a user does not explicitly
 * tell us which transaction to use. This is an internal interface - there should be no need to
 * expose any of this to users.
 *
 * <p>Typical usage will be to push a transaction, fetch it at some later point via {@link #peek()},
 * and then later on pop it. Transactions that are pushed are required to be popped before the end
 * of the request. We make no guarantees about the availability of transactions that are pushed in
 * one request and accessed in another request, and if a transaction that is pushed in one request
 * does happen to be accessible in another request, we make no guarantees about the validity of that
 * transaction against the datastore. In otherwords, if you forget or fail to pop a transaction
 * before the request returns, all bets are off.
 *
 * <p>For users who want to think of their open transactions as a list rather than a stack, we do
 * support the ability to pop a specific {@link Transaction}. This allows users to do things like
 * push one transaction, push a second transaction, and then remove the first transaction even
 * though it isn't at the top of the stack.
 *
 * <p>The management of the transaction objects is intentionally kept separate from the management
 * of transaction state to allow for modifications to the above functionality without impact to the
 * apis we expose to users. In other words, how we manage transaction state should have nothing to
 * do with how we keep track of which transaction to use when one is not explicitly provided.
 *
 * <p>Implementations of this interface must be thread-safe.
 *
 */
interface TransactionStack {

  /**
   * Pushes the provided transaction.
   *
   * @param txn The transaction to push.
   */
  void push(Transaction txn);

  /**
   * Removes the provided transaction. The transaction can be at any location on the stack.
   *
   * @param txn The transaction to remove.
   * @throws IllegalStateException If the provided transaction was not on the stack at any location.
   */
  void remove(Transaction txn);

  /**
   * Returns the current transaction, or throws an exception if there is no current transaction.
   *
   * @return The current transaction.
   * @throws IllegalStateException If there is no current transaction.
   */
  Transaction peek();

  /**
   * Returns the current transaction, or returns the parameter if there is no current transaction.
   *
   * @param returnedIfNoTxn The return value of this method if there is no current transaction.
   * @return The current transaction, or the parameter that was passed in if there is no current
   *     transaction.
   */
  Transaction peek(Transaction returnedIfNoTxn);

  /**
   * @return All {@link Transaction}s started by this thread upon which no attempt to commit or
   *     rollback has been made.
   */
  Collection<Transaction> getAll();

  /** Clear the stack */
  void clearAll();

  /**
   * Associate the provided {@link Future} with the provided {@link Transaction} so that we can
   * block on the Future's completion when the txn is committed or rolled back.
   */
  void addFuture(Transaction txn, Future<?> future);

  /** Returns the {@link Future Futures} associated with the provided {@link Transaction}. */
  LinkedHashSet<Future<?>> getFutures(Transaction txn);

  /** Register that the provided entities were put as part of the given txn. */
  void addPutEntities(Transaction txn, List<Entity> putEntities);

  /** Returns the entities that were put as part of the given txn. */
  List<Entity> getPutEntities(Transaction txn);

  /**
   * Register that the entities uniquely identified by the given keys were deleted as part of the
   * given txn.
   */
  void addDeletedKeys(Transaction txn, List<Key> deletedKeys);

  /**
   * Returns the keys uniquely identifying the entities that were deleted as part of the given txn.
   */
  List<Key> getDeletedKeys(Transaction txn);
}
