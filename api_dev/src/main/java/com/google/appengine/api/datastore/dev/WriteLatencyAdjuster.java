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

import com.google.appengine.tools.development.DynamicLatencyAdjuster;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.apphosting.datastore.DatastoreV3Pb;

/**
 * {@link DynamicLatencyAdjuster} that adds the paxos penalty for writes and takes the fact that
 * transactional puts and deletes are super-fast into account.
 *
 */
public class WriteLatencyAdjuster implements DynamicLatencyAdjuster {

  static final int HIGH_REP_WRITE_PENALTY_MS = 30;

  @Override
  public int adjust(LocalRpcService service, Object request, int latencyMs) {
    if (request instanceof DatastoreV3Pb.PutRequest) {
      if (((DatastoreV3Pb.PutRequest) request).hasTransaction()) {
        // Transactional puts are super fast because they don't actually hit
        // bigtable.
        return 1;
      }
    } else if (request instanceof DatastoreV3Pb.DeleteRequest) {
      if (((DatastoreV3Pb.DeleteRequest) request).hasTransaction()) {
        // Transactional deletes are super fast because they don't actually hit
        // bigtable.
        return 1;
      }
    } else {
      // Op doesn't support transactions.
    }

    // Both Spanner and Megastore are high replication - add write penalty
    latencyMs += HIGH_REP_WRITE_PENALTY_MS;
    return latencyMs;
  }
}
