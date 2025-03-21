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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.appengine.api.datastore.dev.LocalDatastoreService.LiveTxn;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile.EntityGroup;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Container for all known pseudo-kinds.
 *
 * <p>Tracks a set of pseudo-kinds (registered with {@link #register}). Provides a runQuery method
 * to execute pseudo-kind queries, and a get method to fetch pseudo-kind entities.
 *
 */
class PseudoKinds {
  // Marker to indicate get() was not called on a pseudo-kind
  static final EntityProto NOT_A_PSEUDO_KIND = new EntityProto();

  // Key is kind name
  private final Map<String, PseudoKind> pseudoKinds;

  PseudoKinds() {
    this.pseudoKinds = new ConcurrentHashMap<>();
  }

  /** Registers a new pseudo-kind */
  void register(PseudoKind implementation) {
    checkNotNull(implementation);
    PseudoKind previous = pseudoKinds.put(implementation.getKindName(), implementation);
    checkState(
        previous == null, "duplicate registration for pseudo-kind " + implementation.getKindName());
  }

  /**
   * Returns entities that match the query if it is for a pseudo-kind, otherwise returns {@code
   * null}. Any orders or filters that have already been applied must be removed from {@code query}.
   */
  @Nullable
  List<EntityProto> runQuery(Query query) {
    checkNotNull(query);
    PseudoKind pseudoKind = pseudoKinds.get(query.getKind());
    if (pseudoKind == null) {
      return null;
    }

    // We've handled kind.
    query.clearKind();

    List<EntityProto> results = pseudoKind.runQuery(query);
    checkNotNull(results, "pseudo-kind " + pseudoKind.getKindName() + " returned invalid result");
    return results;
  }

  /**
   * If {@code key} is for a pseudo-kind, return entity with the given {@code key}, or {@code null}
   * if the pseudo-entity doesn't exist. If {@code key} is not for a pseudo-kind, return {@code
   * NOT_A_PSEUDO_KIND}. The get() is being executed within the given transaction if {@code txn} is
   * not null. {@code eventualConsistency} is true if the user set a failover time on the get().
   */
  @Nullable
  EntityProto get(
      @Nullable LiveTxn txn, EntityGroup eg, Reference key, boolean eventualConsistency) {
    PseudoKind pseudoKind = pseudoKinds.get(Utils.getKind(key));
    if (pseudoKind == null) {
      return NOT_A_PSEUDO_KIND;
    }
    return pseudoKind.get(txn, eg, key, eventualConsistency);
  }
}
