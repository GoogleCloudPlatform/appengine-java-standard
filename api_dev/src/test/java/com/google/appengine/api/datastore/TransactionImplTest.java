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
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.FutureHelper.FakeFuture;
import com.google.appengine.api.datastore.TransactionImpl.TransactionState;
import com.google.appengine.api.datastore.TransactionStackImpl.TransactionDataMap;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link TransactionImpl} and {@link InternalTransactionV3}.
 *
 */
@RunWith(JUnit4.class)
public class TransactionImplTest {

  protected static final String APP = "app";
  protected Transaction txn;
  protected TransactionStack txnStack;
  protected final DatastoreCallbacks callbacks = mock(DatastoreCallbacks.class);
  protected final LocalServiceTestHelper testHelper = new LocalServiceTestHelper();

  @Before
  public void before() {
    testHelper.setUp();
    txnStack = new TransactionStackImpl();
    // Make sure thread local state doesn't leak across tests.
    txnStack.clearAll();
    txn = newStubTxn();
  }

  @After
  public void after() {
    testHelper.tearDown();
  }

  /** A {@link Transaction} for which all transactional API calls return null. */
  protected Transaction newStubTxn() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        new InternalTransactionV3(apiConfig, APP, newBeginTransactionFuture()) {
          @Override
          <T extends Message> Future<Void> makeAsyncCall(
              DatastoreV3Pb.DatastoreService_3.Method method, MessageLite request, T response) {
            // no-op
            return new FutureHelper.FakeFuture<Void>(null);
          }
        });
  }

  /** A {@link Transaction} for which all transactional API calls throw exceptions. */
  protected Transaction newThrowingTxn() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        new InternalTransactionV3(apiConfig, APP, newBeginTransactionFuture()) {
          @Override
          <T extends Message> Future<Void> makeAsyncCall(
              DatastoreV3Pb.DatastoreService_3.Method method, MessageLite request, T response) {
            return newImmediateFailedFuture();
          }
        });
  }

  /**
   * A {@link Transaction} for which the begin transaction call and all transactional API calls
   * throw exceptions.
   */
  protected Transaction newBeginTransactionThrowsTxn() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        new InternalTransactionV3(apiConfig, APP, newFailedBeginTransactionFuture()) {
          @Override
          <T extends Message> Future<Void> makeAsyncCall(
              DatastoreV3Pb.DatastoreService_3.Method method, MessageLite request, T response) {
            return newImmediateFailedFuture();
          }
        });
  }

  private static Future<DatastoreV3Pb.Transaction> newBeginTransactionFuture() {
    DatastoreV3Pb.Transaction.Builder txn = DatastoreV3Pb.Transaction.newBuilder();
    txn.setHandle(123);
    return new FutureHelper.FakeFuture<DatastoreV3Pb.Transaction>(txn.build());
  }

  private static Future<DatastoreV3Pb.Transaction> newFailedBeginTransactionFuture() {
    return newImmediateFailedFuture();
  }

  protected static <T> Future<T> newImmediateFailedFuture() {
    return Futures.immediateFailedFuture(new DatastoreFailureException("yam"));
  }

  @Test
  public void testCommitStateTransitions() {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    txn.commit();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
    try {
      txn.commit();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the COMMITTED state
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
    try {
      txn.rollback();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the COMMITTED state
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
  }

  @Test
  public void testCommitAsyncStateTransitions() throws ExecutionException, InterruptedException {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    Future<Void> future = txn.commitAsync();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
    future.get();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
    try {
      Future<?> unused = txn.commitAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the COMMITTED state
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
    try {
      Future<?> unused = txn.rollbackAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the COMMITTED state
    assertThat(getState(txn)).isEqualTo(TransactionState.COMMITTED);
  }

  @Test
  public void testCommitAsyncTwice() {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = txn.commitAsync();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
    try {
      Future<?> unused = txn.commitAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
  }

  @Test
  public void testRollbackStateTransitions() {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    txn.rollback();
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
    try {
      txn.rollback();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ROLLED_BACK state
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
    try {
      txn.commit();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ROLLED_BACK state
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
  }

  @Test
  public void testRollbackAsyncStateTransitions() throws ExecutionException, InterruptedException {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    Future<Void> future = txn.rollbackAsync();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
    future.get();
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
    try {
      Future<?> unused = txn.rollbackAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ROLLED_BACK state
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
    try {
      Future<?> unused = txn.commitAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ROLLED_BACK state
    assertThat(getState(txn)).isEqualTo(TransactionState.ROLLED_BACK);
  }

  @Test
  public void testRollbackAsyncTwice() {
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = txn.rollbackAsync();
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
    try {
      Future<?> unused = txn.rollbackAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    assertThat(getState(txn)).isEqualTo(TransactionState.COMPLETION_IN_PROGRESS);
  }

  /**
   * Verifies that if an error occurs during rollback, the transaction is transitioned to the error
   * state, but no exception is thrown.
   */
  @Test
  public void testRollbackError() {
    txn = newThrowingTxn();
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    txn.rollback();
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      txn.rollback();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      txn.commit();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
  }

  /**
   * Verifies that if an error occurs during an asynchronous rollback, the transaction is
   * transitioned to the error state, but no exception is thrown.
   */
  @Test
  public void testAsyncRollbackError() throws Exception {
    txn = newThrowingTxn();
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    Future<Void> future = txn.rollbackAsync();
    future.get();
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      Future<?> unused = txn.rollbackAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      Future<?> unused = txn.commitAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
  }

  @Test
  public void testExceptionRaisingCommitStateTransitions() {
    txn = newThrowingTxn();
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    try {
      txn.commit();
      assertWithMessage("expected dfe").fail();
    } catch (DatastoreFailureException dfe) {
      // good
    }
    // in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      txn.commit();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      txn.rollback();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
  }

  @Test
  public void testExceptionRaisingCommitAsyncStateTransitions() throws InterruptedException {
    txn = newThrowingTxn();
    txnStack.push(txn);
    assertThat(getState(txn)).isEqualTo(TransactionState.BEGUN);
    Future<Void> future = txn.commitAsync();
    try {
      future.get();
      assertWithMessage("expected dfe").fail();
    } catch (ExecutionException e) {
      // good
      assertThat(e).hasCauseThat().isInstanceOf(DatastoreFailureException.class);
    }
    // in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      Future<?> unused = txn.commitAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
    try {
      Future<?> unused = txn.rollbackAsync();
      assertWithMessage("expected ise").fail();
    } catch (IllegalStateException ise) {
      // good
    }
    // still in the ERROR state
    assertThat(getState(txn)).isEqualTo(TransactionState.ERROR);
  }

  @Test
  public void testIsActive_Commit() {
    txnStack.push(txn);
    assertThat(txn.isActive()).isTrue();
    txn.commit();
    assertThat(txn.isActive()).isFalse();
  }

  @Test
  public void testIsActive_CommitAsync() throws ExecutionException, InterruptedException {
    txnStack.push(txn);
    assertThat(txn.isActive()).isTrue();
    Future<Void> future = txn.commitAsync();
    assertThat(txn.isActive()).isFalse();
    future.get();
    assertThat(txn.isActive()).isFalse();
  }

  @Test
  public void testIsActive_Rollback() {
    txnStack.push(txn);
    assertThat(txn.isActive()).isTrue();
    txn.rollback();
    assertThat(txn.isActive()).isFalse();
  }

  @Test
  public void testIsActive_RollbackAsync() throws ExecutionException, InterruptedException {
    txnStack.push(txn);
    assertThat(txn.isActive()).isTrue();
    Future<Void> future = txn.rollbackAsync();
    assertThat(txn.isActive()).isFalse();
    future.get();
    assertThat(txn.isActive()).isFalse();
  }

  @Test
  public void testEnsureTxnActive_Commit() {
    TransactionImpl.ensureTxnActive(null);
    TransactionImpl.ensureTxnActive(txn);
    txnStack.push(txn);
    txn.commit();
    try {
      TransactionImpl.ensureTxnActive(txn);
      assertWithMessage("expected exception").fail();
    } catch (IllegalStateException ise) {
      // good
    }
  }

  @Test
  public void testEnsureTxnActive_CommitAsync() {
    TransactionImpl.ensureTxnActive(null);
    TransactionImpl.ensureTxnActive(txn);
    txnStack.push(txn);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = txn.commitAsync();
    try {
      TransactionImpl.ensureTxnActive(txn);
      assertWithMessage("expected exception").fail();
    } catch (IllegalStateException ise) {
      // good
    }
  }

  @Test
  public void testEnsureTxnActive_Rollback() {
    TransactionImpl.ensureTxnActive(null);
    TransactionImpl.ensureTxnActive(txn);
    txnStack.push(txn);
    txn.rollback();
    try {
      TransactionImpl.ensureTxnActive(txn);
      assertWithMessage("expected exception").fail();
    } catch (IllegalStateException ise) {
      // good
    }
  }

  @Test
  public void testEnsureTxnActive_RollbackAsync() {
    TransactionImpl.ensureTxnActive(null);
    TransactionImpl.ensureTxnActive(txn);
    txnStack.push(txn);
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError = txn.rollbackAsync();
    try {
      TransactionImpl.ensureTxnActive(txn);
      assertWithMessage("expected exception").fail();
    } catch (IllegalStateException ise) {
      // good
    }
  }

  private ImmutableList<Future<?>> setupBlockingFutures(boolean wrappedTransaction)
      throws ExecutionException, InterruptedException {
    @SuppressWarnings("DoNotMock") // we want to check that get() has been called
    Future<?> f1 = mock(Future.class);
    when(f1.get()).thenReturn(null);
    @SuppressWarnings("DoNotMock")
    Future<?> f2 = mock(Future.class);
    when(f2.get()).thenReturn(null);
    txn = newStubTxn();
    txnStack.push(txn);
    Transaction addFutureTxn = wrappedTransaction ? new TransactionWrapper(txn) : txn;
    txnStack.addFuture(addFutureTxn, f1);
    txnStack.addFuture(addFutureTxn, f2);
    return ImmutableList.of(f1, f2);
  }

  private void testCommitBlocksOnFutures(boolean async, boolean wrappedtransaction)
      throws ExecutionException, InterruptedException {
    ImmutableList<Future<?>> futures = setupBlockingFutures(wrappedtransaction);
    if (async) {
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError = txn.commitAsync();
    } else {
      txn.commit();
    }
    verify(futures.get(0)).get();
    verify(futures.get(1)).get();
  }

  @Test
  public void testCommitBlocksOnFutures_Sync() throws ExecutionException, InterruptedException {
    testCommitBlocksOnFutures(false, false);
  }

  @Test
  public void testCommitBlocksOnFutures_Sync_WrappedTransaction()
      throws ExecutionException, InterruptedException {
    testCommitBlocksOnFutures(false, true);
  }

  @Test
  public void testCommitBlocksOnFutures_Async() throws ExecutionException, InterruptedException {
    testCommitBlocksOnFutures(true, false);
  }

  @Test
  public void testCommitBlocksOnFutures_Async_WrappedTransaction()
      throws ExecutionException, InterruptedException {
    testCommitBlocksOnFutures(true, true);
  }

  private void testCommitBlocksOnExplosiveFuture(boolean async, int numFutures)
      throws ExecutionException, InterruptedException {
    txnStack = mock(TransactionStack.class);
    ImmutableSet.Builder<Future<?>> futuresBuilder = ImmutableSet.builder();
    for (int i = 0; i < numFutures; i++) {
      @SuppressWarnings("DoNotMock") // we want to check that get() has been called
      Future<?> f = mock(Future.class);
      when(f.get()).thenThrow(new ExecutionException(new DatastoreFailureException("yam" + i)));
      futuresBuilder.add(f);
    }
    LinkedHashSet<Future<?>> futures = new LinkedHashSet<>(futuresBuilder.build());
    when(txnStack.getFutures(isA(TransactionImpl.class)))
        .thenReturn(futures)
        .thenReturn(Sets.<Future<?>>newLinkedHashSet());
    when(txnStack.getPutEntities(isA(TransactionImpl.class)))
        .thenReturn(Lists.<Entity>newArrayList());
    when(txnStack.getDeletedKeys(isA(TransactionImpl.class))).thenReturn(Lists.<Key>newArrayList());
    when(txnStack.getPutEntities(isA(TransactionImpl.class)))
        .thenReturn(Lists.<Entity>newArrayList());
    when(txnStack.getDeletedKeys(isA(TransactionImpl.class))).thenReturn(Lists.<Key>newArrayList());
    txn = newStubTxn();
    try {
      if (async) {
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError = txn.commitAsync();
      } else {
        txn.commit();
      }
      assertWithMessage("expected exception").fail();
    } catch (DatastoreFailureException dfe) {
      // good
      assertThat(dfe).hasMessageThat().isEqualTo("yam0");
    }
    assertThat(txn.isActive()).isTrue();
    txn.rollback();
    assertThat(txn.isActive()).isFalse();
    verify(txnStack, times(2)).remove(isA(TransactionImpl.class));
    for (Future<?> f : futures) {
      verify(f).get();
    }
  }

  @Test
  public void testCommitBlocksOnExplosiveFuture_Sync()
      throws ExecutionException, InterruptedException {
    testCommitBlocksOnExplosiveFuture(false, 1);
  }

  @Test
  public void testCommitBlocksOnExplosiveFuture_Async()
      throws ExecutionException, InterruptedException {
    testCommitBlocksOnExplosiveFuture(true, 1);
  }

  @Test
  public void testCommitBlocksOnExplosiveFuture_Multiple()
      throws ExecutionException, InterruptedException {
    testCommitBlocksOnExplosiveFuture(false, 2);
  }

  private void testRollbackBlocksOnFutures(boolean async, boolean wrappedtransaction)
      throws ExecutionException, InterruptedException {
    ImmutableList<Future<?>> futures = setupBlockingFutures(wrappedtransaction);
    if (async) {
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError = txn.rollbackAsync();
    } else {
      txn.rollback();
    }
    verify(futures.get(0)).get();
    verify(futures.get(1)).get();
  }

  @Test
  public void testRollbackBlocksOnFutures_Sync() throws ExecutionException, InterruptedException {
    testRollbackBlocksOnFutures(false, false);
  }

  @Test
  public void testRollbackBlocksOnFutures_Sync_WrappedTransaction()
      throws ExecutionException, InterruptedException {
    testRollbackBlocksOnFutures(false, true);
  }

  @Test
  public void testRollbackBlocksOnFutures_Async() throws ExecutionException, InterruptedException {
    testRollbackBlocksOnFutures(true, false);
  }

  @Test
  public void testRollbackBlocksOnFutures_Async_WrappedTransaction()
      throws ExecutionException, InterruptedException {
    testRollbackBlocksOnFutures(true, true);
  }

  private void testRollbackBlocksOnExplosiveFuture(boolean async)
      throws ExecutionException, InterruptedException {
    txnStack = mock(TransactionStack.class);
    @SuppressWarnings("DoNotMock") // we want to check that get() has been called
    Future<?> f1 = mock(Future.class);
    when(f1.get()).thenThrow(new ExecutionException(new DatastoreFailureException("yam")));
    LinkedHashSet<Future<?>> futures = new LinkedHashSet<>(ImmutableSet.<Future<?>>of(f1));
    when(txnStack.getFutures(isA(TransactionImpl.class))).thenReturn(futures);
    txn = newStubTxn();
    if (async) {
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError = txn.rollbackAsync();
    } else {
      txn.rollback();
    }
    assertThat(txn.isActive()).isFalse();
    verify(txnStack).remove(isA(TransactionImpl.class));
    verify(f1).get();
  }

  @Test
  public void testRollbackBlocksOnExplosiveFuture_Sync()
      throws ExecutionException, InterruptedException {
    testRollbackBlocksOnExplosiveFuture(false);
  }

  @Test
  public void testRollbackBlocksOnExplosiveFuture_Async()
      throws ExecutionException, InterruptedException {
    testRollbackBlocksOnExplosiveFuture(true);
  }

  /** Operations that can affect a {@link TransactionDataMap}. */
  protected static enum TransactionDataOp {
    ADD_FUTURE,
    ADD_PUT,
    ADD_DELETE;
  }

  /** Strategies for closing a transaction. */
  protected static enum TransactionCloseStrategy {
    COMMIT,
    ROLLBACK;
  }

  protected void doTestBeginTransactionThrows(
      TransactionDataOp dataOp, TransactionCloseStrategy closeStrategy) throws Exception {
    TransactionDataMap data = ((TransactionStackImpl) txnStack).getStack();
    txn = newBeginTransactionThrowsTxn();

    txnStack.push(txn);
    assertThat(data.txns).hasSize(1);
    // Transaction data is created lazily.
    assertThat(data.txnIdToTransactionData).isEmpty();

    try {
      switch (dataOp) {
        case ADD_FUTURE:
          txnStack.addFuture(txn, new FakeFuture<String>("never called"));
          break;
        case ADD_PUT:
          txnStack.addPutEntities(txn, ImmutableList.of(new Entity("kind")));
          break;
        case ADD_DELETE:
          txnStack.addDeletedKeys(txn, ImmutableList.of(new Key("kind")));
          break;
      }
      assertWithMessage("expected exception").fail();
    } catch (DatastoreFailureException expected) {
      // The expected result of beginTransaction() failing.
    }
    assertThat(data.txns).hasSize(1);
    // The exception prevents transaction data from ever being stored.
    assertThat(data.txnIdToTransactionData).isEmpty();

    try {
      switch (closeStrategy) {
        case COMMIT:
          txn.commit();
          break;
        case ROLLBACK:
          txn.rollback();
          break;
      }
      assertWithMessage("expected exception").fail();
    } catch (DatastoreFailureException expected) {
      // The expected result of beginTransaction() failing.
    }
    // TransactionStack is cleaned up anyway. No leak.
    assertThat(data.txns).isEmpty();
    assertThat(data.txnIdToTransactionData).isEmpty();
  }

  @Test
  public void testBeginTransactionThrows_WithFuture_Commit() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_FUTURE, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testBeginTransactionThrows_WithPut_Commit() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_PUT, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testBeginTransactionThrows_WithDelete_Commit() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_DELETE, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testBeginTransactionThrows_WithFuture_Rollback() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_FUTURE, TransactionCloseStrategy.ROLLBACK);
  }

  @Test
  public void testBeginTransactionThrows_WithPut_Rollback() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_PUT, TransactionCloseStrategy.ROLLBACK);
  }

  @Test
  public void testBeginTransactionThrows_WithDelete_Rollback() throws Exception {
    doTestBeginTransactionThrows(TransactionDataOp.ADD_DELETE, TransactionCloseStrategy.ROLLBACK);
  }

  private void doTestWrappedTransaction(
      TransactionDataOp dataOp, TransactionCloseStrategy closeStrategy) throws Exception {
    TransactionDataMap data = ((TransactionStackImpl) txnStack).getStack();
    txn = newStubTxn();

    txnStack.push(txn);
    assertThat(data.txns).hasSize(1);
    // Transaction data is created lazily.
    assertThat(data.txnIdToTransactionData).isEmpty();

    TransactionWrapper wrappedTxn = new TransactionWrapper(txn);
    switch (dataOp) {
      case ADD_FUTURE:
        txnStack.addFuture(wrappedTxn, new FakeFuture<String>("never called"));
        break;
      case ADD_PUT:
        txnStack.addPutEntities(wrappedTxn, ImmutableList.of(new Entity("kind")));
        break;
      case ADD_DELETE:
        txnStack.addDeletedKeys(wrappedTxn, ImmutableList.of(new Key("kind")));
        break;
    }
    assertThat(data.txns).hasSize(1);
    assertThat(data.txnIdToTransactionData).hasSize(1);

    switch (closeStrategy) {
      case COMMIT:
        wrappedTxn.commit();
        break;
      case ROLLBACK:
        wrappedTxn.rollback();
        break;
    }
    // TransactionStack is cleaned up. No leak.
    assertThat(data.txns).isEmpty();
    assertThat(data.txnIdToTransactionData).isEmpty();
  }

  @Test
  public void testWrappedTransaction_WithFuture_Commit() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_FUTURE, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testWrappedTransaction_WithPut_Commit() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_PUT, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testWrappedTransaction_WithDelete_Commit() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_DELETE, TransactionCloseStrategy.COMMIT);
  }

  @Test
  public void testWrappedTransaction_WithFuture_Rollback() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_FUTURE, TransactionCloseStrategy.ROLLBACK);
  }

  @Test
  public void testWrappedTransaction_WithPut_Rollback() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_PUT, TransactionCloseStrategy.ROLLBACK);
  }

  @Test
  public void testWrappedTransaction_WithDelete_Rollback() throws Exception {
    doTestWrappedTransaction(TransactionDataOp.ADD_DELETE, TransactionCloseStrategy.ROLLBACK);
  }

  private TransactionState getState(Transaction txn) {
    return ((TransactionImpl) txn).state;
  }

  /**
   * A {@link Transaction} implementation that delegates to another transaction. Objectify, for
   * example, uses a class like this. See <a
   * href=https://github.com/objectify/objectify/blob/master/src/main/java/com/googlecode/
   * objectify/util/cmd/TransactionWrapper.java>.
   */
  private static class TransactionWrapper implements Transaction {
    private final Transaction delegate;

    public TransactionWrapper(Transaction delegate) {
      this.delegate = delegate;
    }

    @Override
    public void commit() {
      delegate.commit();
    }

    @Override
    public Future<Void> commitAsync() {
      return delegate.commitAsync();
    }

    @Override
    public void rollback() {
      delegate.rollback();
    }

    @Override
    public Future<Void> rollbackAsync() {
      return delegate.rollbackAsync();
    }

    @Override
    public String getId() {
      return delegate.getId();
    }

    @Override
    public String getApp() {
      return delegate.getApp();
    }

    @Override
    public boolean isActive() {
      return delegate.isActive();
    }
  }
}
