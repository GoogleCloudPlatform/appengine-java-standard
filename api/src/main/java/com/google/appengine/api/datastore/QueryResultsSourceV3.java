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

import static com.google.appengine.api.datastore.DatastoreApiHelper.makeAsyncCall;

import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.CompiledCursor;
import com.google.apphosting.datastore.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.apphosting.datastore.DatastoreV3Pb.NextRequest;
import com.google.apphosting.datastore.DatastoreV3Pb.QueryResult;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity.CompositeIndex;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * V3 service specific code for iterating query results and requesting more results. Instances can
 * be shared between queries using the same ApiConfig.
 */
class QueryResultsSourceV3 extends BaseQueryResultsSource<QueryResult, NextRequest, QueryResult> {

  private final ApiConfig apiConfig;

  QueryResultsSourceV3(
      DatastoreCallbacks callbacks,
      FetchOptions fetchOptions,
      Transaction txn,
      Query query,
      Future<QueryResult> initialQueryResultFuture,
      ApiConfig apiConfig) {
    super(callbacks, fetchOptions, txn, query, initialQueryResultFuture);
    this.apiConfig = apiConfig;
  }

  @Override
  public NextRequest buildNextCallPrototype(QueryResult initialResult) {
    DatastoreV3Pb.NextRequest req = new DatastoreV3Pb.NextRequest();
    req.setCursor(initialResult.getCursor());
    if (initialResult.hasCompiledCursor()) {
      // Compiled cursor setting should match original query.
      req.setCompile(true);
    }
    // This used to call .freeze() but that method has been deleted, see go/javaproto1freezeremoval
    return req;
  }

  @Override
  public Future<QueryResult> makeNextCall(
      NextRequest reqPrototype,
      WrappedQueryResult unused,
      @Nullable Integer fetchCount, /* Nullable */
      Integer offsetOrNull) {
    DatastoreV3Pb.NextRequest req = reqPrototype.clone();
    if (fetchCount != null) {
      req.setCount(fetchCount);
    }
    if (offsetOrNull != null) {
      req.setOffset(offsetOrNull);
    }
    return makeAsyncCall(apiConfig, Method.Next, req, new DatastoreV3Pb.QueryResult());
  }

  @Override
  public WrappedQueryResult wrapResult(QueryResult result) {
    return new WrappedQueryResultV3(result);
  }

  @Override
  public WrappedQueryResult wrapInitialResult(QueryResult initialResult) {
    return new WrappedQueryResultV3(initialResult);
  }

  private static class WrappedQueryResultV3 implements WrappedQueryResult {
    private final DatastoreV3Pb.QueryResult res;

    WrappedQueryResultV3(DatastoreV3Pb.QueryResult res) {
      this.res = res;
    }

    @Override
    public List<Entity> getEntities(Collection<Projection> projections) {
      List<Entity> entities = Lists.newArrayListWithCapacity(res.resultSize());
      if (projections.isEmpty()) {
        for (EntityProto entityProto : res.results()) {
          entities.add(EntityTranslator.createFromPb(entityProto));
        }
      } else {
        for (EntityProto entityProto : res.results()) {
          entities.add(EntityTranslator.createFromPb(entityProto, projections));
        }
      }
      return entities;
    }

    @Override
    public List<Cursor> getResultCursors() {
      List<Cursor> cursors = Lists.newArrayListWithCapacity(res.resultSize());

      for (CompiledCursor compiledCursor : res.resultCompiledCursors()) {
        cursors.add(new Cursor(compiledCursor.toByteString()));
      }
      cursors.addAll(Collections.<Cursor>nCopies(res.resultSize() - cursors.size(), null));
      return cursors;
    }

    @Override
    public int numSkippedResults() {
      return res.getSkippedResults();
    }

    @Override
    public Cursor getSkippedResultsCursor() {
      return res.hasSkippedResultsCompiledCursor()
          ? new Cursor(res.getSkippedResultsCompiledCursor().toByteString())
          : null;
    }

    @Override
    public boolean hasMoreResults() {
      return res.isMoreResults();
    }

    @Override
    public Cursor getEndCursor() {
      return res.hasCompiledCursor() ? new Cursor(res.getCompiledCursor().toByteString()) : null;
    }

    // Save only the translated index list, which is much smaller than the whole
    // query result, or the query result future, or even the index pb list.
    @Override
    public List<Index> getIndexInfo(Collection<Index> monitoredIndexBuffer) {
      List<Index> indexList = Lists.newArrayListWithCapacity(res.indexSize());
      for (CompositeIndex indexProtobuf : res.indexs()) {
        Index index = IndexTranslator.convertFromPb(indexProtobuf);
        indexList.add(index);
        if (indexProtobuf.isOnlyUseIfRequired()) {
          monitoredIndexBuffer.add(index);
        }
      }
      return indexList;
    }

    @Override
    public boolean madeProgress(WrappedQueryResult previousResult) {
      // The v3 API returns an error if a query fails to make progress, so no
      // special client-side logic is required.
      return true;
    }
  }
}
