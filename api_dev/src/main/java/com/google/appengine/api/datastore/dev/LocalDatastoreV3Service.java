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

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.tools.development.LatencyPercentiles;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.api.ApiBasePb.Integer64Proto;
import com.google.apphosting.base.protos.api.ApiBasePb.StringProto;
import com.google.apphosting.base.protos.api.ApiBasePb.VoidProto;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.AllocateIdsRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.AllocateIdsResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.BeginTransactionRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CommitResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Cursor;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DeleteResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.PutResponse;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.QueryResult;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Transaction;
import com.google.auto.service.AutoService;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.CompositeIndex;

/**
 * Provides an implementation of {@link LocalDatastoreService} that is compatible with API Proxy.
 *
 * <p>While this method uses a delegation pattern, it must extend {@link LocalDatastoreService} to
 * maintain backwards compatibility for users who request the Datastore stub from the APIProxy and
 * expect the {@link LocalDatastoreService} type.
 *
 * <p>Many of the public API methods are overridden to provide latency annotations for APIProxy.
 * Other Datastore API methods are overridden for consistency.
 */
@AutoService(LocalRpcService.class)
public final class LocalDatastoreV3Service extends LocalDatastoreService
    implements LocalRpcService {

  @Override
  protected void addActionImpl(TaskQueueAddRequest action) {
    ApiProxy.makeSyncCall("taskqueue", "Add", action.toByteArray());
  }

  @Override
  @LatencyPercentiles(latency50th = 10)
  public GetResponse get(Status status, GetRequest request) {
    return super.get(status, request);
  }

  @Override
  @LatencyPercentiles(latency50th = 30, dynamicAdjuster = WriteLatencyAdjuster.class)
  public PutResponse put(Status status, PutRequest request) {
    return super.put(status, request);
  }

  @Override
  @LatencyPercentiles(latency50th = 40, dynamicAdjuster = WriteLatencyAdjuster.class)
  public DeleteResponse delete(Status status, DeleteRequest request) {
    return super.delete(status, request);
  }

  @Override
  @LatencyPercentiles(latency50th = 1)
  public VoidProto addActions(Status status, TaskQueueBulkAddRequest request) {
    return super.addActions(status, request);
  }

  @Override
  @LatencyPercentiles(latency50th = 20)
  public QueryResult runQuery(Status status, Query query) {
    return super.runQuery(status, query);
  }

  @Override
  @LatencyPercentiles(latency50th = 50)
  public QueryResult next(Status status, NextRequest request) {
    return super.next(status, request);
  }

  @Override
  public VoidProto deleteCursor(Status status, Cursor request) {
    return super.deleteCursor(status, request);
  }

  @Override
  @LatencyPercentiles(latency50th = 1)
  public Transaction beginTransaction(Status status, BeginTransactionRequest req) {
    return super.beginTransaction(status, req);
  }

  @Override
  @LatencyPercentiles(latency50th = 20, dynamicAdjuster = WriteLatencyAdjuster.class)
  public CommitResponse commit(Status status, final Transaction req) {
    return super.commit(status, req);
  }

  @Override
  @LatencyPercentiles(latency50th = 1)
  public VoidProto rollback(Status status, Transaction req) {
    return super.rollback(status, req);
  }

  @Override
  public Integer64Proto createIndex(Status status, CompositeIndex req) {
    return super.createIndex(status, req);
  }

  @Override
  public VoidProto updateIndex(Status status, CompositeIndex req) {
    return super.updateIndex(status, req);
  }

  @Override
  public CompositeIndices getIndices(Status status, StringProto req) {
    return super.getIndices(status, req);
  }

  @Override
  public VoidProto deleteIndex(Status status, CompositeIndex req) {
    return super.deleteIndex(status, req);
  }

  @Override
  @LatencyPercentiles(latency50th = 1)
  public AllocateIdsResponse allocateIds(Status status, AllocateIdsRequest req) {
    return super.allocateIds(status, req);
  }

  @Override
  public Integer getMaxApiRequestSize() {
    return null;
  }
}
