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

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.datastore.v1.QueryResultBatch;
import com.google.datastore.v1.RunQueryRequest;
import com.google.datastore.v1.RunQueryResponse;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

class QueryResultsSourceCloudDatastoreV1
    extends BaseQueryResultsSource<RunQueryResponse, RunQueryRequest, RunQueryResponse> {

  private final CloudDatastoreV1Client cloudDatastoreV1Client;
  private final RunQueryRequest initialRequest;
  private int remainingLimit;

  QueryResultsSourceCloudDatastoreV1(
      DatastoreCallbacks callbacks,
      FetchOptions fetchOptions,
      Transaction txn,
      Query query,
      RunQueryRequest request,
      Future<RunQueryResponse> runQueryResponse,
      CloudDatastoreV1Client cloudDatastoreV1Client) {
    super(callbacks, fetchOptions, txn, query, runQueryResponse);
    this.initialRequest = request;
    this.cloudDatastoreV1Client = cloudDatastoreV1Client;
    remainingLimit = fetchOptions.getLimit() != null ? fetchOptions.getLimit() : -1;
  }

  @Override
  RunQueryRequest buildNextCallPrototype(RunQueryResponse initialResponse) {
    return initialRequest;
  }

  @Override
  Future<RunQueryResponse> makeNextCall(
      RunQueryRequest prototype,
      WrappedQueryResult latestResult,
      @Nullable Integer fetchCount,
      @Nullable Integer offset) {
    RunQueryRequest.Builder runQueryRequest = prototype.toBuilder();
    com.google.datastore.v1.Query.Builder query = runQueryRequest.getQueryBuilder();
    query.setStartCursor(latestResult.getEndCursor().toByteString());
    QueryResultBatch latestBatch = ((WrappedQueryResultCloudDatastoreV1) latestResult).getBatch();
    if (query.hasLimit()) {
      remainingLimit -= latestBatch.getEntityResultsCount();
      query.getLimitBuilder().setValue(Math.max(remainingLimit, 0));
    }
    if (offset != null) {
      query.setOffset(offset);
    } else {
      query.clearOffset();
    }
    return cloudDatastoreV1Client.runQuery(runQueryRequest.build());
  }

  @Override
  WrappedQueryResult wrapInitialResult(RunQueryResponse initialResponse) {
    return new WrappedQueryResultCloudDatastoreV1(initialResponse.getBatch());
  }

  @Override
  WrappedQueryResult wrapResult(RunQueryResponse res) {
    return new WrappedQueryResultCloudDatastoreV1(res.getBatch());
  }
}
