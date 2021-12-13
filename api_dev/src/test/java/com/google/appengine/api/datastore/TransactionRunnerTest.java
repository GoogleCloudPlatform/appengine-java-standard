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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransactionRunnerTest {

  @Test
  public void testSuccess_FinishTxn_Read() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    txn.commit();
    quietGet(
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(true, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            return new FutureHelper.FakeFuture<>(null);
          }
        }.runReadInTransaction());
  }

  @Test
  public void testSuccess_FinishTxn_Write() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    when(txn.commitAsync()).thenReturn(new FutureHelper.FakeFuture<Void>(null));
    quietGet(
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(true, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            return new FutureHelper.FakeFuture<>(null);
          }
        }.runWriteInTransaction());
  }

  @Test
  public void testSuccess_DoNotFinishTxn_Read() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    quietGet(
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(false, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            return new FutureHelper.FakeFuture<>(null);
          }
        }.runReadInTransaction());
  }

  @Test
  public void testSuccess_DoNotFinishTxn_Write() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    quietGet(
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(false, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            return new FutureHelper.FakeFuture<>(null);
          }
        }.runWriteInTransaction());
  }

  @Test
  public void testFailure_FinishTxn_Read() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    TransactionRunner<Void> runner =
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(true, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            throw new RuntimeException();
          }
        };
    assertThrows(RuntimeException.class, () -> quietGet(runner.runReadInTransaction()));
  }

  @Test
  public void testFailure_FinishTxn_Write() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    when(txn.rollbackAsync()).thenReturn(new FutureHelper.FakeFuture<Void>(null));
    TransactionRunner<Void> runner =
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(true, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            throw new RuntimeException();
          }
        };
    assertThrows(RuntimeException.class, () -> quietGet(runner.runWriteInTransaction()));
    verify(txn).rollbackAsync();
  }

  @Test
  public void testFailure_DoNotFinishTxn_Read() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    TransactionRunner<Void> runner =
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(false, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            throw new RuntimeException();
          }
        };
    assertThrows(RuntimeException.class, () -> quietGet(runner.runReadInTransaction()));
  }

  @Test
  public void testFailure_DoNotFinishTxn_Write() {
    Transaction txn = mock(Transaction.class);
    when(txn.isActive()).thenReturn(true);
    TransactionRunner<Void> runner =
        new TransactionRunner<Void>(new GetOrCreateTransactionResult(false, txn)) {
          @Override
          protected Future<Void> runInternal(Transaction innerTxn) {
            throw new RuntimeException();
          }
        };
    assertThrows(RuntimeException.class, () -> quietGet(runner.runWriteInTransaction()));
  }

  @Test
  public void testIllegalCtorArg() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TransactionRunner<Void>(new GetOrCreateTransactionResult(true, null)) {
              @Override
              protected Future<Void> runInternal(Transaction innerTxn) {
                throw new UnsupportedOperationException();
              }
            });
  }
}
