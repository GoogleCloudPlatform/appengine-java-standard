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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.apphosting.api.AppEngineInternal;

/**
 * {@link TransactionHelper} enables the task queue API to serialize a datastore transaction without
 * knowing the details of how it is implemented.
 */
@AppEngineInternal
public final class TransactionHelper {

  private TransactionHelper() {}

  /**
   * Sets either the transaction or datastore_transaction field in a TaskQueueAddRequest depending
   * on what kind of transaction is provided.
   */
  public static void setTransaction(Transaction txn, TaskQueueAddRequest.Builder request) {
    checkNotNull(txn);
    checkNotNull(request);

    // Transaction is a public interface, so the implementation may not be ours. We can only tell
    // v3 from v1 by checking if the transaction is in the v1 register.
    if (InternalTransactionCloudDatastoreV1.isV1Transaction(txn)) {
      request.setDatastoreTransaction(
          InternalTransactionCloudDatastoreV1.get(txn).getTransactionBytes());
    } else {
      request.setTransaction(InternalTransactionV3.toProto2(txn));
    }
  }
}
