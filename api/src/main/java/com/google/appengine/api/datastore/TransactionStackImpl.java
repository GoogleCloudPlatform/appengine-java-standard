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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Future;

/**
 * For now each thread is going to have its own stack. This prevents users from sharing a
 * transaction across threads and also prevents users from reliably sharing a transaction across
 * requests that happen to be serviced by the same thread. When we start allowing users to create
 * threads we could change this implementation to allow transactions to be shared across threads,
 * but there's little point in supporting this now.
 *
 */
class TransactionStackImpl implements TransactionStack {

  private final ThreadLocalTransactionStack stack;

  public TransactionStackImpl() {
    // The thread local is static because the services we expose
    // in the datastore api and the services they depend on may have very
    // short lifespans (creating 1 or more per request), and we need to make
    // sure our transaction state persists independent of the lifecycle of
    // the services that need access to the transaction state.  This makes
    // testing a little bit tricky though, so make sure that tests invoke
    // clearStack() in their tearDown() methods.
    this(new ThreadLocalTransactionStack.StaticMember());
  }

  /**
   * Just for testing. Gives tests the opportunity to use some other implementation of {@link
   * ThreadLocalTransactionStack} that doesn't maintain state across instances like {@link
   * ThreadLocalTransactionStack.StaticMember} does.
   */
  TransactionStackImpl(ThreadLocalTransactionStack stack) {
    this.stack = stack;
  }

  @Override
  public void push(Transaction txn) {
    if (txn == null) {
      throw new NullPointerException("txn cannot be null");
    }
    getStack().txns.addFirst(txn);
  }

  // Only used by tests.  Could probably go away.
  Transaction pop() {
    try {
      Transaction txn = getStack().txns.removeFirst();
      // txn is guaranteed not to be null because we don't allow null when we
      // push and we would get a NoSuchElementException if the stack was empty.
      getStack().txnIdToTransactionData.remove(txn.getId());
      return txn;
    } catch (NoSuchElementException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void remove(Transaction txn) {
    // Performance is linear, but for this to have any real negative
    // impact users would have to have a large number (like hundreds) of nested
    // txns.  Doesn't seem worth trying to optimize for this case.
    if (!getStack().txns.remove(txn)) {
      throw new IllegalStateException(
          "Attempted to deregister a transaction that is not currently registered.");
    }
    getStack().txnIdToTransactionData.remove(txn.getId());
  }

  @Override
  public Transaction peek() {
    try {
      return getStack().txns.getFirst();
    } catch (NoSuchElementException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Transaction peek(Transaction returnedIfNoTxn) {
    LinkedList<Transaction> txns = getStack().txns;
    Transaction txn = txns.isEmpty() ? null : txns.peek();
    return txn == null ? returnedIfNoTxn : txn;
  }

  @Override
  public Collection<Transaction> getAll() {
    // defensive copy
    return new ArrayList<Transaction>(getStack().txns);
  }

  TransactionDataMap getStack() {
    return stack.get();
  }

  @Override
  public void clearAll() {
    getStack().clear();
  }

  @Override
  public void addFuture(Transaction txn, Future<?> future) {
    getFutures(txn).add(future);
  }

  private TransactionData getTransactionData(Transaction txn) {
    TransactionDataMap txnDataMap = getStack();
    TransactionData data = txnDataMap.txnIdToTransactionData.get(txn.getId());
    if (data == null) {
      // No data yet so create one and associate it.
      data = new TransactionData();
      // The call to getId() will block until the BeginTxn rpc finishes.
      txnDataMap.txnIdToTransactionData.put(txn.getId(), data);
    }
    return data;
  }

  @Override
  public LinkedHashSet<Future<?>> getFutures(Transaction txn) {
    return getTransactionData(txn).futures;
  }

  @Override
  public void addPutEntities(Transaction txn, List<Entity> putEntities) {
    getTransactionData(txn).puts.addAll(putEntities);
  }

  @Override
  public void addDeletedKeys(Transaction txn, List<Key> deletedKeys) {
    getTransactionData(txn).deletes.addAll(deletedKeys);
  }

  @Override
  public List<Entity> getPutEntities(Transaction txn) {
    return getTransactionData(txn).puts;
  }

  @Override
  public List<Key> getDeletedKeys(Transaction txn) {
    return getTransactionData(txn).deletes;
  }

  /**
   * A wrapper for a ThreadLocal<TransactionDataMap> that gives us flexibility in terms of the
   * lifecycle of the ThreadLocal values. This just exists so that our production code can use a
   * static member and our test code can use an instance member (it's easy to end up with flaky
   * tests when your tests rely on static members because it's too easy to forget to clear them
   * out).
   */
  interface ThreadLocalTransactionStack {

    TransactionDataMap get();

    class StaticMember implements ThreadLocalTransactionStack {
      private static final ThreadLocal<TransactionDataMap> STACK =
          new ThreadLocal<TransactionDataMap>() {
            @Override
            protected TransactionDataMap initialValue() {
              return new TransactionDataMap();
            }
          };

      @Override
      public TransactionDataMap get() {
        return STACK.get();
      }
    }
  }

  /**
   * Associates a list of {@link Transaction Transactions} (the stack) with a map that ties
   * transaction ids to its associated {@link TransactionData}. Given a given Transaction in the
   * list we can find: The Futures whose completion must be blocked on when committing or rolling
   * back the Transaction can be found. The entities that have been put and deleted as part of the
   * Transaction (used for post op callbacks).
   */
  static final class TransactionDataMap {
    final LinkedList<Transaction> txns = new LinkedList<Transaction>();
    final Map<String, TransactionData> txnIdToTransactionData =
        new HashMap<String, TransactionData>();

    void clear() {
      txns.clear();
      txnIdToTransactionData.clear();
    }
  }

  /**
   * Data associated with a Transaction. We can't store this data inside {@link TransactionImpl}
   * because of an API design mistake we made very early on - making Transaction an interface and
   * allowing users to pass arbitrary implementations to us without checking the runtime type.
   */
  static final class TransactionData {
    final LinkedHashSet<Future<?>> futures = new LinkedHashSet<Future<?>>();
    final List<Key> deletes = Lists.newArrayList();
    final List<Entity> puts = Lists.newArrayList();
  }
}
