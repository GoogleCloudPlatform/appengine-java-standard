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

import com.google.appengine.api.datastore.FutureHelper.FakeFuture;
import com.google.appengine.api.datastore.TransactionStackImpl.TransactionDataMap;
import com.google.common.collect.ImmutableList;
import com.google.datastore.v1.AllocateIdsRequest;
import com.google.datastore.v1.AllocateIdsResponse;
import com.google.datastore.v1.BeginTransactionRequest;
import com.google.datastore.v1.BeginTransactionResponse;
import com.google.datastore.v1.CommitResponse;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.RollbackRequest;
import com.google.datastore.v1.RollbackResponse;
import com.google.datastore.v1.RunQueryRequest;
import com.google.datastore.v1.RunQueryResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.Future;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TransactionImpl} and {@link InternalTransactionCloudDatastoreV1}. */
@RunWith(JUnit4.class)
public class TransactionImplCloudDatastoreV1Test extends TransactionImplTest {
  @Override
  protected Transaction newStubTxn() {
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        InternalTransactionCloudDatastoreV1.create(
            new CloudDatastoreV1Client() {
              @Override
              public Future<BeginTransactionResponse> beginTransaction(
                  BeginTransactionRequest request) {
                return new FakeFuture<>(null);
              }

              @Override
              public Future<RollbackResponse> rollback(RollbackRequest request) {
                return new FakeFuture<>(null);
              }

              @Override
              public Future<RunQueryResponse> runQuery(RunQueryRequest request) {
                return new FakeFuture<>(null);
              }

              @Override
              public Future<LookupResponse> lookup(LookupRequest request) {
                return new FakeFuture<>(null);
              }

              @Override
              public Future<AllocateIdsResponse> allocateIds(AllocateIdsRequest request) {
                return new FakeFuture<>(null);
              }

              @Override
              public Future<CommitResponse> rawCommit(byte[] request)
                  throws InvalidProtocolBufferException {
                return new FakeFuture<>(null);
              }
            },
            newBeginTransactionFuture(),
            false /* isReadOnly */));
  }

  @Override
  protected Transaction newThrowingTxn() {
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        InternalTransactionCloudDatastoreV1.create(
            newFailingClient(), newBeginTransactionFuture(), false /* isReadOnly */));
  }

  @Override
  protected Transaction newBeginTransactionThrowsTxn() {
    return new TransactionImpl(
        APP,
        txnStack,
        callbacks,
        true,
        InternalTransactionCloudDatastoreV1.create(
            newFailingClient(), newFailedBeginTransactionFuture(), false /* isReadOnly */));
  }

  private static Future<BeginTransactionResponse> newFailedBeginTransactionFuture() {
    return newImmediateFailedFuture();
  }

  private static Future<BeginTransactionResponse> newBeginTransactionFuture() {
    return new FutureHelper.FakeFuture<>(
        BeginTransactionResponse.newBuilder()
            .setTransaction(ByteString.copyFromUtf8("123"))
            .build());
  }

  private static CloudDatastoreV1Client newFailingClient() {
    return new CloudDatastoreV1Client() {
      @Override
      public Future<BeginTransactionResponse> beginTransaction(BeginTransactionRequest request) {
        return newImmediateFailedFuture();
      }

      @Override
      public Future<RollbackResponse> rollback(RollbackRequest request) {
        return newImmediateFailedFuture();
      }

      @Override
      public Future<RunQueryResponse> runQuery(RunQueryRequest request) {
        return newImmediateFailedFuture();
      }

      @Override
      public Future<LookupResponse> lookup(LookupRequest request) {
        return newImmediateFailedFuture();
      }

      @Override
      public Future<AllocateIdsResponse> allocateIds(AllocateIdsRequest request) {
        return newImmediateFailedFuture();
      }

      @Override
      public Future<CommitResponse> rawCommit(byte[] request)
          throws InvalidProtocolBufferException {
        return newImmediateFailedFuture();
      }
    };
  }

  @Override
  protected void doTestBeginTransactionThrows(
      TransactionDataOp dataOp, TransactionCloseStrategy closeStrategy) throws Exception {
    TransactionDataMap data = ((TransactionStackImpl) txnStack).getStack();
    txn = newBeginTransactionThrowsTxn();

    txnStack.push(txn);
    assertThat(data.txns).hasSize(1);
    // Transaction data is created lazily.
    assertThat(data.txnIdToTransactionData).isEmpty();

    // IDs for v1 transactions are generated client-side, and mutations are cached client-side,
    // so the failed beginTransaction() doesn't cause an exception at this stage.
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
    assertThat(data.txns).hasSize(1);
    assertThat(data.txnIdToTransactionData).hasSize(1);

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
}
