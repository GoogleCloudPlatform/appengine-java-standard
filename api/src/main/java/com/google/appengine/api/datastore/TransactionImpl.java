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

import com.google.appengine.api.utils.FutureWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * State and behavior that is common to all {@link Transaction} implementations.
 *
 * <p>Our implementation is implicitly async. BeginTransaction RPCs always return instantly, and
 * this class maintains a reference to the {@link Future} associated with the RPC. We service as
 * much of the {@link Transaction} interface as we can without retrieving the result of the future.
 *
 * <p>There is no synchronization in this code because transactions are associated with a single
 * thread and are documented as such.
 */
class TransactionImpl implements Transaction, CurrentTransactionProvider {

  private static final Logger logger = Logger.getLogger(TransactionImpl.class.getName());

  /**
   * Interface to a coupled object which handles the actual transaction RPCs and other service
   * protocol dependent details.
   */
  interface InternalTransaction {
    /** Issues an asynchronous RPC to commit this transaction. */
    Future<Void> doCommitAsync();

    /** Issues an asynchronous RPC to rollback this transaction. */
    Future<Void> doRollbackAsync();

    String getId();

    @Override
    boolean equals(@Nullable Object o);

    @Override
    int hashCode();
  }

  // The states in which a Transaction can exist.
  enum TransactionState {
    BEGUN,
    // Used to detect calls to commit, commitAsync, rollback, and rollbackAsync
    // while a commitAsync or a rollbackAsync is in progress.
    COMPLETION_IN_PROGRESS,
    COMMITTED,
    ROLLED_BACK,
    ERROR
  }

  private final String app;

  private final TransactionStack txnStack;

  private final DatastoreCallbacks callbacks;

  private final boolean isExplicit;

  private final InternalTransaction internalTransaction;

  TransactionState state = TransactionState.BEGUN;

  /** A {@link PostOpFuture} implementation that runs both post put and post delete callbacks. */
  private class PostCommitFuture extends PostOpFuture<Void> {
    private final List<Entity> putEntities;
    private final List<Key> deletedKeys;

    private PostCommitFuture(
        List<Entity> putEntities, List<Key> deletedKeys, Future<Void> delegate) {
      super(delegate, callbacks);
      this.putEntities = putEntities;
      this.deletedKeys = deletedKeys;
    }

    @Override
    void executeCallbacks(Void ignoreMe) {
      PutContext putContext = new PutContext(TransactionImpl.this, putEntities);
      callbacks.executePostPutCallbacks(putContext);
      DeleteContext deleteContext = new DeleteContext(TransactionImpl.this, deletedKeys);
      callbacks.executePostDeleteCallbacks(deleteContext);
    }
  }

  TransactionImpl(
      String app,
      TransactionStack txnStack,
      DatastoreCallbacks callbacks,
      boolean isExplicit,
      InternalTransaction txnProvider) {
    this.app = app;
    this.txnStack = txnStack;
    this.callbacks = callbacks;
    this.isExplicit = isExplicit;
    this.internalTransaction = txnProvider;
  }

  @Override
  public String getId() {
    return internalTransaction.getId();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o instanceof TransactionImpl) {
      return internalTransaction.equals(((TransactionImpl) o).internalTransaction);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return internalTransaction.hashCode();
  }

  @Override
  public void commit() {
    FutureHelper.quietGet(commitAsync());
  }

  @Override
  public Future<Void> commitAsync() {
    ensureTxnActive(this);
    try {
      // Make sure we wait for all dependent futures to finish first.
      List<RuntimeException> exceptions = new ArrayList<>();
      for (Future<?> f : txnStack.getFutures(this)) {
        try {
          FutureHelper.quietGet(f);
        } catch (RuntimeException e) {
          exceptions.add(e);
        }
      }
      if (!exceptions.isEmpty()) {
        // We'll throw the first exception and log any others.
        for (int i = 1; i < exceptions.size(); i++) {
          RuntimeException e = exceptions.get(i);
          logger.log(Level.WARNING, "Failure while waiting to commit", e);
        }
        throw exceptions.get(0);
      }
      // Issue the commit RPC.
      Future<Void> commitResponse = internalTransaction.doCommitAsync();
      // Don't transition into the next state until we've successfully fired off
      // the RPC. See http://b/5403846
      state = TransactionState.COMPLETION_IN_PROGRESS;
      // Translate the commit response.
      Future<Void> result =
          new FutureWrapper<Void, Void>(commitResponse) {
            @Override
            protected Void wrap(Void ignore) throws Exception {
              state = TransactionState.COMMITTED;
              return null;
            }

            @Override
            protected Throwable convertException(Throwable cause) {
              // All exceptions force a transition to the error state.
              state = TransactionState.ERROR;
              return cause;
            }
          };
      // Make sure we capture the entities and keys associated with this txn now
      // because we're about to pop the stack and lose track of them.
      return new PostCommitFuture(
          txnStack.getPutEntities(this), txnStack.getDeletedKeys(this), result);
    } finally {
      // We might not be the current transaction so we have to request explicit
      // removal.  Note that we're doing this synchronously, so when you commit
      // a txn, even async, the txn immediately ceases to be the current txn.
      if (isExplicit) {
        txnStack.remove(this);
      }
    }
  }

  @Override
  public void rollback() {
    FutureHelper.quietGet(rollbackAsync());
  }

  @Override
  public Future<Void> rollbackAsync() {
    ensureTxnActive(this);
    try {
      // Make sure we wait for all dependent futures to finish first.  This
      // is necessary to prevent server-side txns from being rolled back in the
      // middle of transactional operations, a scenario that is almost certain
      // to result in unexpected behavior.
      for (Future<?> f : txnStack.getFutures(this)) {
        try {
          FutureHelper.quietGet(f);
        } catch (RuntimeException e) {
          // Any failure of these futures is irrelevant since we are rolling back.
          // However, they may still be of interest for debugging.
          logger.log(Level.INFO, "Failure while waiting to rollback", e);
        }
      }
      Future<Void> future = internalTransaction.doRollbackAsync();
      // Don't transition into the next state until we've successfully fired off
      // the RPC. See http://b/5403846
      state = TransactionState.COMPLETION_IN_PROGRESS;
      return new FutureWrapper<Void, Void>(future) {
        @Override
        protected Void wrap(Void ignore) throws Exception {
          state = TransactionState.ROLLED_BACK;
          return null;
        }

        @Override
        protected Void absorbParentException(Throwable cause) throws Throwable {
          // This will be executed if the rollback fails. We suppress the
          // exception in that case so that callers don't have to worry about
          // wrapping calls to rollback() in a try block.
          logger.log(Level.INFO, "Rollback of transaction failed", cause);
          state = TransactionState.ERROR;
          return null;
        }

        @Override
        protected Throwable convertException(Throwable cause) {
          // It looks like this will never be executed, except possibly for
          // something like an OutOfMemoryError. All expected exceptions
          // are handled by absorbParentException.
          state = TransactionState.ERROR;
          // This throwable will be propagated.
          return cause;
        }
      };
    } finally {
      // We might not be the current transaction so we have to request explicit
      // removal.  Note that we're doing this synchronously, so when you
      // rollback a txn, even async, the txn immediately ceases to be the
      // current txn.
      if (isExplicit) {
        txnStack.remove(this);
      }
    }
  }

  @Override
  public String getApp() {
    return app;
  }

  @Override
  public boolean isActive() {
    return state == TransactionState.BEGUN;
  }

  @Override
  public Transaction getCurrentTransaction(Transaction defaultValue) {
    return this;
  }

  /** If {@code txn} is not null and not active, throw {@link IllegalStateException}. */
  static void ensureTxnActive(Transaction txn) {
    if (txn != null && !txn.isActive()) {
      throw new IllegalStateException(
          "Transaction with which this operation is " + "associated is not active.");
    }
  }

  @Override
  public String toString() {
    return "Txn [" + app + "." + getId() + ", " + state + "]";
  }
}
