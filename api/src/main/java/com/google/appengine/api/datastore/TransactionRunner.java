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

import com.google.appengine.api.utils.FutureWrapper;
import java.util.concurrent.Future;

/**
 * A class that knows how to run things inside transactions.
 *
 * @param <T> The type of result of the operation that we run in a transaction.
 */
abstract class TransactionRunner<T> {

  // In an ideal world, we would use something like ListenableFuture, so that when the
  // operation finishes, we would commit the transaction. However, we are running in a context
  // where we aren't able to perform multiple asynchronous activities, even if one of them is just
  // awaiting the completion of another one. We also have another problem, which is that we can't
  // commit a transaction until all associated operations have completed.
  //
  // Therefore, we do the following:
  //
  // If we're *not* completing an implicit transaction (i.e. finishTxn is false), then we just
  // return the result of running the actual operation, which will be a future. This is the "good"
  // case -- we're starting the work that the user has requested, but not finishing it.
  //
  // If we *are* completing an implicit transaction (i.e. finishTxn is true), and we're performing
  // a write operation, then we synchronously resolve the actual operation. Then, based on the
  // result of that operation, we either commit or rollback the transaction. We return the future
  // corresponding to the transactional operation. This is the "bad" case, since it's somewhat
  // contrary to user expectations; they called put/delete asynchronously, but we're completing it
  // synchronously. However, given that the put/delete operation doesn't actually have any effect
  // until it's committed, this isn't completely terrible. Also, the most common use for this
  // pattern is to support implicit transactions coming from the *synchronous* DatastoreService, in
  // which case all this asynchrony is just a parlor game anyway.
  //
  // If we're completing an implicit transaction, and we're performing a read operation, then we
  // issue the read asynchronously. When the read completes, we synchronously commit or rollback the
  // transaction, depending on whether the read succeeded or failed.

  private final Transaction txn;
  private final boolean finishTxn;

  protected TransactionRunner(GetOrCreateTransactionResult result) {
    txn = result.getTransaction();
    finishTxn = result.isNew();
    if (txn == null && finishTxn) {
      throw new IllegalArgumentException(
          "Cannot have a null txn when finishTxn is true.  This "
              + "almost certainly represents a programming error on the part of the App Engine "
              + "team.  Please report this via standard support channels and accept our humblest "
              + "apologies.");
    }
    TransactionImpl.ensureTxnActive(txn);
  }

  // This method is optimized for read operations, where the original operation is relatively slow,
  // but committing the transaction is relatively fast.
  public Future<T> runReadInTransaction() {
    if (!finishTxn) {
      return runInternal(txn);
    }

    return new FutureWrapper<T, T>(runInternal(txn)) {
      @Override
      protected T wrap(T result) throws Exception {
        // if commit() throws an exception we don't attempt a rollback because
        // the transaction is already in the error state
        txn.commit();
        return result;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        txn.rollback();
        return cause;
      }
    };
  }

  // This method is optimized for write operations, where the original operation is relatively
  // fast, but committing the transaction is relatively slow.
  public Future<T> runWriteInTransaction() {
    if (!finishTxn) {
      return runInternal(txn);
    }

    Future<Void> txnFuture;
    T result = null;
    Exception capturedException = null;
    try {
      result = quietGet(runInternal(txn));
    } catch (Exception e) {
      capturedException = e;
    } finally {
      if (capturedException == null) {
        txnFuture = txn.commitAsync();
        // if commit() throws an exception we don't attempt a rollback because
        // the transaction is already in the error state
      } else {
        txnFuture = txn.rollbackAsync();
      }
    }

    final T finalResult = result;
    final Exception finalCapturedException = capturedException;
    return new FutureWrapper<Void, T>(txnFuture) {
      @Override
      protected T wrap(Void v) throws Exception {
        if (finalCapturedException != null) {
          throw finalCapturedException;
        } else {
          return finalResult;
        }
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  protected abstract Future<T> runInternal(Transaction txn);
}
