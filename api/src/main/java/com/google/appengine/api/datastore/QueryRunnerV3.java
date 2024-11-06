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

import com.google.appengine.api.datastore.CompositeIndexManager.IndexComponentsOnlyQuery;
import com.google.appengine.api.datastore.CompositeIndexManager.IndexSource;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.DatastoreService_3.Method;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity;
import java.util.concurrent.Future;

/**
 * V3 service specific code for constructing and sending queries. This class is threadsafe and has
 * no state.
 */
final class QueryRunnerV3 implements QueryRunner {

  private final DatastoreServiceConfig datastoreServiceConfig;
  private final ApiConfig apiConfig;

  QueryRunnerV3(DatastoreServiceConfig datastoreServiceConfig, ApiConfig apiConfig) {
    this.datastoreServiceConfig = datastoreServiceConfig;
    this.apiConfig = apiConfig;
  }

  @Override
  public QueryResultsSource runQuery(FetchOptions fetchOptions, Query query, Transaction txn) {
    final DatastoreV3Pb.Query.Builder queryProto = convertToPb(query, txn, fetchOptions);
    if (datastoreServiceConfig.getReadPolicy().getConsistency() == Consistency.EVENTUAL) {
      queryProto.setFailoverMs(BaseAsyncDatastoreServiceImpl.ARBITRARY_FAILOVER_READ_MS);
      queryProto.setStrong(false); // Allows the datastore to always use READ_CONSISTENT.
    }

    Future<DatastoreV3Pb.QueryResult> result =
        DatastoreApiHelper.makeAsyncCall(
            apiConfig, Method.RunQuery, queryProto, DatastoreV3Pb.QueryResult.newBuilder());

    // Adding more info to DatastoreNeedIndexException if thrown
    result =
        new FutureWrapper<DatastoreV3Pb.QueryResult, DatastoreV3Pb.QueryResult>(result) {
          @Override
          protected Throwable convertException(Throwable cause) {
            if (cause instanceof DatastoreNeedIndexException) {
              addMissingIndexData(queryProto.build(), (DatastoreNeedIndexException) cause);
            }
            return cause;
          }

          @Override
          protected DatastoreV3Pb.QueryResult wrap(DatastoreV3Pb.QueryResult result)
              throws Exception {
            return result;
          }
        };
    return new QueryResultsSourceV3(
        datastoreServiceConfig.getDatastoreCallbacks(),
        fetchOptions,
        txn,
        query,
        result,
        apiConfig);
  }

  private void addMissingIndexData(DatastoreV3Pb.Query queryProto, DatastoreNeedIndexException e) {
    IndexComponentsOnlyQuery indexQuery = new IndexComponentsOnlyQuery(queryProto.toBuilder());
    CompositeIndexManager mgr = new CompositeIndexManager();
    OnestoreEntity.Index.Builder index = mgr.compositeIndexForQuery(indexQuery);
    if (index != null) {
      String xml = mgr.generateXmlForIndex(index.build(), IndexSource.manual);
      e.setMissingIndexDefinitionXml(xml);
    } else {
      // Prod says we need an index but the index manager says we don't.
      // Probably a bug in the index manager.  DatastoreNeedIndexException
      // will report this in the exception message.
    }
  }

  private DatastoreV3Pb.Query.Builder convertToPb(Query q, Transaction txn, FetchOptions fetchOptions) {
    DatastoreV3Pb.Query.Builder queryProto = QueryTranslator.convertToPb(q, fetchOptions);
    if (txn != null) {
      TransactionImpl.ensureTxnActive(txn);
      queryProto.setTransaction(InternalTransactionV3.toProto(txn));
    }
    return queryProto;
  }
}
