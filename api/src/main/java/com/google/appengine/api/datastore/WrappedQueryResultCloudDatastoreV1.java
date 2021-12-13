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

import static com.google.common.base.Preconditions.checkState;

import com.google.appengine.api.datastore.BaseQueryResultsSource.WrappedQueryResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.datastore.v1.EntityResult;
import com.google.datastore.v1.QueryResultBatch;
import com.google.datastore.v1.QueryResultBatch.MoreResultsType;
import java.util.Collection;
import java.util.List;

/** An implementation of {@link WrappedQueryResult} in terms of Cloud Datastore v1. */
class WrappedQueryResultCloudDatastoreV1 implements WrappedQueryResult {
  private final QueryResultBatch batch;

  WrappedQueryResultCloudDatastoreV1(QueryResultBatch batch) {
    this.batch = batch;
  }

  @Override
  public Cursor getEndCursor() {
    checkState(!batch.getEndCursor().isEmpty(), "Batch contained no end cursor.");
    return new Cursor(batch.getEndCursor());
  }

  @Override
  public List<Entity> getEntities(Collection<Projection> projections) {
    List<Entity> entityList = Lists.newArrayListWithCapacity(batch.getEntityResultsCount());
    if (projections.isEmpty()) {
      for (EntityResult entityResult : batch.getEntityResultsList()) {
        entityList.add(DataTypeTranslator.toEntity(entityResult.getEntity()));
      }
    } else {
      for (EntityResult entityResult : batch.getEntityResultsList()) {
        entityList.add(DataTypeTranslator.toEntity(entityResult.getEntity(), projections));
      }
    }
    return entityList;
  }

  @Override
  public List<Cursor> getResultCursors() {
    List<Cursor> cursors = Lists.newArrayListWithCapacity(batch.getEntityResultsCount());
    for (EntityResult result : batch.getEntityResultsList()) {
      cursors.add(result.getCursor().isEmpty() ? null : new Cursor(result.getCursor()));
    }
    return cursors;
  }

  @Override
  public Cursor getSkippedResultsCursor() {
    return batch.getSkippedCursor().isEmpty() ? null : new Cursor(batch.getSkippedCursor());
  }

  @Override
  public boolean hasMoreResults() {
    return batch.getMoreResults() == MoreResultsType.NOT_FINISHED;
  }

  @Override
  public int numSkippedResults() {
    return batch.getSkippedResults();
  }

  @Override
  public List<Index> getIndexInfo(Collection<Index> monitoredIndexBuffer) {
    // Not part of the v1 spec.
    return ImmutableList.of();
  }

  @Override
  public boolean madeProgress(WrappedQueryResult previousResult) {
    if (!hasMoreResults()) {
      // We finished.
      return true;
    }
    // If the cursor didn't change, we didn't make progress.
    return !getEndCursor().equals(previousResult.getEndCursor());
  }

  QueryResultBatch getBatch() {
    return batch;
  }
}
