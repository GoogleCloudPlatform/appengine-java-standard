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

/** An interface for an implementation of a local version of the Datastore V3 service. */
public interface DatastoreV3 {

  void addActions(TaskQueueBulkAddRequest req);

  AllocateIdsResponse allocateIds(AllocateIdsRequest req);

  Transaction beginTransaction(BeginTransactionRequest req);

  CommitResponse commit(Transaction req);

  long createIndex(CompositeIndex req);

  DeleteResponse delete(DeleteRequest req);

  void deleteCursor(Cursor req);

  void deleteIndex(CompositeIndex req);

  GetResponse get(GetRequest req);

  CompositeIndices getIndices(String appId);

  QueryResult next(NextRequest req);

  PutResponse put(PutRequest req);

  void rollback(Transaction req);

  QueryResult runQuery(Query req);

  void updateIndex(CompositeIndex req);
}
