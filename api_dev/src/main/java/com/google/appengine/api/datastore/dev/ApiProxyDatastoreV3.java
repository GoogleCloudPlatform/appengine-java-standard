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

package com.google.appengine.api.datastore.dev;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.api.ApiBasePb.Integer64Proto;
import com.google.apphosting.base.protos.api.ApiBasePb.StringProto;
import com.google.apphosting.base.protos.api.ApiBasePb.VoidProto;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Cursor;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Error.ErrorCode;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Transaction;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.google.storage.onestore.v3.OnestoreEntity.CompositeIndex;

/** A {@link DatastoreV3} which delegates to V3 using ApiProxy. */
class ApiProxyDatastoreV3 implements DatastoreV3 {

  @Override
  public void updateIndex(CompositeIndex req) {
    makeV3Call(Method.UpdateIndex, req, VoidProto.parser());
  }

  @Override
  public DatastoreV3Pb.QueryResult runQuery(Query req) {
    return makeV3Call(Method.RunQuery, req, DatastoreV3Pb.QueryResult.parser());
  }

  @Override
  public void rollback(Transaction req) {
    makeV3Call(Method.Rollback, req, VoidProto.parser());
  }

  @Override
  public DatastoreV3Pb.PutResponse put(PutRequest req) {
    return makeV3Call(Method.Put, req, DatastoreV3Pb.PutResponse.parser());
  }

  @Override
  public DatastoreV3Pb.QueryResult next(DatastoreV3Pb.NextRequest req) {
    return makeV3Call(Method.Next, req, DatastoreV3Pb.QueryResult.parser());
  }

  @Override
  public CompositeIndices getIndices(String appId) {
    return makeV3Call(
        Method.GetIndices,
        StringProto.newBuilder().setValue(appId).build(),
        DatastoreV3Pb.CompositeIndices.parser());
  }

  @Override
  public DatastoreV3Pb.GetResponse get(DatastoreV3Pb.GetRequest req) {
    return makeV3Call(Method.Get, req, DatastoreV3Pb.GetResponse.parser());
  }

  @Override
  public void deleteIndex(CompositeIndex req) {
    makeV3Call(Method.DeleteIndex, req, VoidProto.parser());
  }

  @Override
  public void deleteCursor(Cursor req) {
    makeV3Call(Method.DeleteCursor, req, VoidProto.parser());
  }

  @Override
  public DatastoreV3Pb.DeleteResponse delete(DatastoreV3Pb.DeleteRequest req) {
    return makeV3Call(Method.Delete, req, DatastoreV3Pb.DeleteResponse.parser());
  }

  @Override
  public long createIndex(CompositeIndex req) {
    return makeV3Call(Method.CreateIndex, req, Integer64Proto.parser()).getValue();
  }

  @Override
  public DatastoreV3Pb.CommitResponse commit(Transaction req) {
    return makeV3Call(Method.Commit, req, DatastoreV3Pb.CommitResponse.parser());
  }

  @Override
  public Transaction beginTransaction(DatastoreV3Pb.BeginTransactionRequest req) {
    return makeV3Call(Method.BeginTransaction, req, DatastoreV3Pb.Transaction.parser());
  }

  @Override
  public DatastoreV3Pb.AllocateIdsResponse allocateIds(DatastoreV3Pb.AllocateIdsRequest req) {
    return makeV3Call(Method.AllocateIds, req, DatastoreV3Pb.AllocateIdsResponse.parser());
  }

  @Override
  public void addActions(TaskQueueBulkAddRequest req) {
    makeV3Call(Method.AddActions, req, VoidProto.parser());
  }

  private <T> T makeV3Call(Method method, MessageLite req, Parser<T> respParser) {
    byte[] responseBytes =
        ApiProxy.makeSyncCall(LocalDatastoreService.PACKAGE, method.name(), req.toByteArray());

    try {
      return respParser.parseFrom(responseBytes);
    } catch (InvalidProtocolBufferException e) {
      throw new ApiProxy.ApplicationException(ErrorCode.INTERNAL_ERROR.getValue(), e.toString());
    }
  }
}
