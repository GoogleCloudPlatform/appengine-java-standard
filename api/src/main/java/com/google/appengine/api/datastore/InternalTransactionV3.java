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
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CommitResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DatastoreService_3;
// import com.google.io.protocol.ProtocolMessage;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of the V3-specific logic to handle a {@link Transaction}.
 *
 * <p>All calls are routed through the {@link ApiProxy}.
 */
class InternalTransactionV3 implements TransactionImpl.InternalTransaction {

  private final ApiConfig apiConfig;
  private final String app;
  /**
   * The {@link Future} associated with the BeginTransaction RPC we sent to the datastore server.
   */
  private final Future<DatastoreV3Pb.Transaction> beginTxnFuture;

  InternalTransactionV3(
      ApiConfig apiConfig, String app, Future<DatastoreV3Pb.Transaction> beginTxnFuture) {
    this.apiConfig = apiConfig;
    this.app = app;
    this.beginTxnFuture = beginTxnFuture;
  }

  /**
   * Provides the unique identifier for the txn. Blocks on the future since the handle comes back
   * from the datastore server.
   */
  private long getHandle() {
    return FutureHelper.quietGet(beginTxnFuture).getHandle();
  }

  // extracted method to facilitate testing
  <T extends Message, S extends Message.Builder> Future<Void> makeAsyncCall(
      DatastoreService_3.Method method, Message.Builder request, S response) {
    Future<T> resultProto = DatastoreApiHelper.makeAsyncCall(apiConfig, method, request, response);
    return new FutureWrapper<T, Void>(resultProto) {
      @Override
      protected Void wrap(T ignore) throws Exception {
        return null;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }

  private <T extends Message.Builder> Future<Void> makeAsyncTxnCall(
      DatastoreService_3.Method method, T response) {
    DatastoreV3Pb.Transaction.Builder txn = DatastoreV3Pb.Transaction.newBuilder();
    txn.setApp(app);
    txn.setHandle(getHandle());
    return makeAsyncCall(method, txn, response);
  }

  @Override
  public Future<Void> doCommitAsync() {
    return makeAsyncTxnCall(DatastoreService_3.Method.Commit, CommitResponse.newBuilder());
  }

  @Override
  public Future<Void> doRollbackAsync() {
    return makeAsyncTxnCall(DatastoreService_3.Method.Rollback, null);
  }

  @Override
  public String getId() {
    // We're exposing the id as a String so that we have the flexibility to
    // change the representation later on.
    return Long.toString(getHandle());
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    InternalTransactionV3 that = (InternalTransactionV3) o;

    return getHandle() == that.getHandle();
  }

  @Override
  public int hashCode() {
    return (int) (getHandle() ^ (getHandle() >>> 32));
  }

  static DatastoreV3Pb.Transaction toProto(Transaction txn) {
    DatastoreV3Pb.Transaction.Builder txnProto = DatastoreV3Pb.Transaction.newBuilder();
    txnProto.setApp(txn.getApp());
    txnProto.setHandle(Long.parseLong(txn.getId()));
    return txnProto.build();
  }

  static com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Transaction toProto2(
      Transaction txn) {
    return com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Transaction.newBuilder()
        .setApp(txn.getApp())
        .setHandle(Long.parseLong(txn.getId()))
        .build();
  }
}
