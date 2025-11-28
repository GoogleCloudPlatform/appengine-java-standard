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
import com.google.appengine.tools.development.LocalRpcService.Status;
import com.google.apphosting.api.proto2api.ApiBasePb.StringProto;
import com.google.apphosting.datastore.DatastoreV3Pb.AllocateIdsRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.AllocateIdsResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.BeginTransactionRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.CommitResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.CompositeIndices;
import com.google.apphosting.datastore.DatastoreV3Pb.Cursor;
import com.google.apphosting.datastore.DatastoreV3Pb.DeleteRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.DeleteResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.GetRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.GetResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.PutRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.PutResponse;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.DatastoreV3Pb.QueryResult;
import com.google.apphosting.datastore.DatastoreV3Pb.Transaction;
import com.google.storage.onestore.v3.OnestoreEntity.CompositeIndex;

/**
 * An implementation {@link DatastoreV3} which wraps a {@link LocalDatastoreService}.
 *
 * <p>{@link LocalDatastoreService} should but cannot implement {@link DatastoreV3} directly because
 * the ApiProxy uses method-named based reflection to call stubs. It doesn't properly check the
 * method signature so the single-parameter version may be called by ApiProxy instead of the
 * expected two parameter version.
 */
public class DatastoreV3Impl implements DatastoreV3 {

  private final LocalDatastoreService delegate;

  public DatastoreV3Impl(LocalDatastoreService delegate) {
    this.delegate = delegate;
  }

  @Override
  public void addActions(TaskQueueBulkAddRequest req) {
    delegate.addActions(new Status(), req);
  }

  @Override
  public AllocateIdsResponse allocateIds(AllocateIdsRequest req) {
    return delegate.allocateIds(new Status(), req);
  }

  @Override
  public Transaction beginTransaction(BeginTransactionRequest req) {
    return delegate.beginTransaction(new Status(), req);
  }

  @Override
  public CommitResponse commit(Transaction req) {
    return delegate.commit(new Status(), req);
  }

  @Override
  public long createIndex(CompositeIndex req) {
    return delegate.createIndex(new Status(), req).getValue();
  }

  @Override
  public DeleteResponse delete(DeleteRequest req) {
    return delegate.delete(new Status(), req);
  }

  @Override
  public void deleteCursor(Cursor req) {
    delegate.deleteCursor(new Status(), req);
  }

  @Override
  public void deleteIndex(CompositeIndex req) {
    delegate.deleteIndex(new Status(), req);
  }

  @Override
  public GetResponse get(GetRequest req) {
    return delegate.get(new Status(), req);
  }

  @Override
  public CompositeIndices getIndices(String appId) {
    return delegate.getIndices(new Status(), StringProto.newBuilder().setValue(appId).build());
  }

  @Override
  public QueryResult next(NextRequest req) {
    return delegate.next(new Status(), req);
  }

  @Override
  public PutResponse put(PutRequest req) {
    return delegate.put(new Status(), req);
  }

  @Override
  public void rollback(Transaction req) {
    delegate.rollback(new Status(), req);
  }

  @Override
  public QueryResult runQuery(Query req) {
    return delegate.runQuery(new Status(), req);
  }

  @Override
  public void updateIndex(CompositeIndex req) {
    delegate.updateIndex(new Status(), req);
  }
}
