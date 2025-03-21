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

import static com.google.appengine.api.datastore.dev.Utils.checkRequest;

import com.google.appengine.api.datastore.DataTypeTranslator;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.LiveTxn;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile.EntityGroup;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Generic pseudo-kind for pseudo-kinds that only understand filtering on __key__ and ordering by
 * __key__ ascending.
 *
 */
// Partially derived from {@link com.google.apphosting.datastore.KeyFilteredPseudoKind}.
abstract class KeyFilteredPseudoKind implements PseudoKind {
  private final LocalDatastoreService localDatastore;

  /** Setup a key-filtered pseudo-kind over a local datastore instance */
  KeyFilteredPseudoKind(LocalDatastoreService localDatastore) {
    this.localDatastore = localDatastore;
  }

  /** Returns the local datastore instance */
  LocalDatastoreService getDatastore() {
    return localDatastore;
  }

  @Override
  public List<EntityProto> runQuery(Query query) {
    Key startKey = null;
    Key endKey = null;
    boolean startInclusive = false;
    boolean endInclusive = false;

    /* We could leave all the filters and orders to LocalDatastoreService, but that would force
     * queries to examine all entities even when filters are present, and wouldn't
     * report the invalid-query errors of the real datastore.
     *
     * Explicit code to handle range, as {@link com.google.appengine.api.datastore.FilterMatcher}
     * is somewhat specialized to deal with {@link PropertyValue} in {@link EntityProto}s.
     */
    for (Filter filter : query.filters()) {
      Operator op = filter.getOpEnum();

      // Report error for filters we can't handle
      checkRequest(
          filter.propertySize() == 1
              && filter.getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY)
              && (op == Operator.LESS_THAN
                  || op == Operator.LESS_THAN_OR_EQUAL
                  || op == Operator.GREATER_THAN
                  || op == Operator.GREATER_THAN_OR_EQUAL
                  || op == Operator.EQUAL),
          "Only comparison filters on " + Entity.KEY_RESERVED_PROPERTY + " supported");

      Object filterVal = DataTypeTranslator.getPropertyValue(filter.getProperty(0));
      // Redundant with {@link com.google.com.appengine.api.datastore.ValidatedQuery}
      checkRequest(
          filterVal instanceof Key, Entity.KEY_RESERVED_PROPERTY + " must be compared to a key");

      Key keyLimit = (Key) filterVal;

      // Update our search limits based on the filters
      if (op == Operator.LESS_THAN) {
        if (endKey == null || keyLimit.compareTo(endKey) <= 0) {
          endKey = keyLimit;
          endInclusive = false;
        }
      } else if (op == Operator.LESS_THAN_OR_EQUAL || op == Operator.EQUAL) {
        if (endKey == null || keyLimit.compareTo(endKey) < 0) {
          endKey = keyLimit;
          endInclusive = true;
        }
      }
      if (op == Operator.GREATER_THAN) {
        if (startKey == null || keyLimit.compareTo(startKey) >= 0) {
          startKey = keyLimit;
          startInclusive = false;
        }
      } else if (op == Operator.GREATER_THAN_OR_EQUAL || op == Operator.EQUAL) {
        if (startKey == null || keyLimit.compareTo(startKey) > 0) {
          startKey = keyLimit;
          startInclusive = true;
        }
      }
    }
    query.clearFilter();

    // The only allowed order we can handle is an initial ascending on key
    if (query.orderSize() > 0) {
      Order order = query.getOrder(0);
      if (order.getDirectionEnum() == Order.Direction.ASCENDING
          && Entity.KEY_RESERVED_PROPERTY.equals(order.getProperty())) {
        query.removeOrder(0);
      }
    }
    checkRequest(
        query.orderSize() == 0,
        "Only ascending order on " + Entity.KEY_RESERVED_PROPERTY + " supported");

    return runQuery(query, startKey, startInclusive, endKey, endInclusive);
  }

  /**
   * Executes query over specified key range, in ascending key order. Query has no remaining filters
   * or orders.
   */
  abstract List<EntityProto> runQuery(
      Query query, Key startKey, boolean startInclusive, Key endKey, boolean endInclusive);

  @Override
  public @Nullable EntityProto get(
      @Nullable LiveTxn txn, EntityGroup eg, Reference key, boolean eventualConsistency) {
    // TODO: implement get using queries once we support resumable get in the real datastore
    // (probably in datastore_v4).  In the meantime, return null (no entity) as that is a closer
    // match to the current behaviour than failing with a bad request.
    return null;
  }
}
