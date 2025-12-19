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

import com.google.appengine.api.datastore.dev.LocalDatastoreService.LiveTxn;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile.EntityGroup;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A virtual datastore kind implemented programmatically. Each kind is identified by a name;
 * accesses to that kind are diverted to the {@code PseudoKind} instance instead of the regular
 * datastore.
 *
 * <p>So far there is only support for queries on pseudo-kinds, but other kind operations (Get, Put,
 * etc) may be added in the future.
 *
 */
interface PseudoKind {
  /** Return the pseudo-kind's name */
  String getKindName();

  /**
   * Returns entities that match the query. Any orders or filters that have already been applied
   * must be removed from {@code query}.
   */
  List<EntityProto> runQuery(Query.Builder query);

  /**
   * Return entity with the given {@code key}, or {@code null} if the pseudo-entity doesn't exist.
   * The get() is being executed within the given transaction if {@code txn} is not null. {@code
   * eventualConsistency} is true if the user set a failover time on the get().
   */
  @Nullable
  EntityProto get(
      @Nullable LiveTxn txn, EntityGroup eg, Reference key, boolean eventualConsistency);
}
