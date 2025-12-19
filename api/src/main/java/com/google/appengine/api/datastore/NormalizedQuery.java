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

import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Order;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Property;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class NormalizedQuery {
  static final ImmutableSet<Operator> INEQUALITY_OPERATORS =
      ImmutableSet.of(
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL);

  protected final Query.Builder query;

  public NormalizedQuery(Query.Builder query) {
    this.query = query;
    normalizeQuery();
  }

  public Query.Builder getQuery() {
    return query;
  }

  private void normalizeQuery() {
    // TODO: consider sharing this code with MegastoreQueryPlanner

    // NOTE: Keep in sync with MegastoreQueryPlanner#normalize()

    Set<Property> equalityFilterProperties = new HashSet<Property>();
    Set<String> equalityProperties = new HashSet<String>();
    Set<String> inequalityProperties = new HashSet<String>();

    List<Filter> filters = new ArrayList<>(query.getFilterList());
    query.clearFilter();

    for (Filter f : filters) {
      Filter.Builder fb = f.toBuilder();
      /* Normalize IN filters to EQUAL. */
      if (fb.getPropertyCount() == 1 && fb.getOp() == Operator.IN) {
        fb.setOp(Operator.EQUAL);
      }
      if (fb.getPropertyCount() >= 1) {
        String name = fb.getProperty(0).getName();
        if (fb.getOp() == Operator.EQUAL) {
          if (equalityFilterProperties.add(fb.getProperty(0))) {
            query.addFilter(fb);
            equalityProperties.add(name);
          }
        } else {
          query.addFilter(fb);
          if (INEQUALITY_OPERATORS.contains(fb.getOp())) {
            inequalityProperties.add(name);
          }
        }
      } else {
        query.addFilter(fb);
      }
    }

    equalityProperties.removeAll(inequalityProperties);

    // Strip repeated orders and orders coinciding with EQUAL filters.
    List<Order> orders = new ArrayList<>(query.getOrderList());
    query.clearOrder();
    for (Order o : orders) {
      if (equalityProperties.add(o.getProperty())) {
        query.addOrder(o);
      }
    }

    Set<String> allProperties = new HashSet<>(equalityProperties);
    allProperties.addAll(inequalityProperties);

    // Removing redundant exists filters.
    List<Filter> filtersAfterLoop1 = new ArrayList<>(query.getFilterList());
    query.clearFilter();
    for (Filter f : filtersAfterLoop1) {
      boolean keep = true;
      if (f.getOp() == Operator.EXISTS
          && f.getPropertyCount() >= 1
          && !allProperties.add(f.getProperty(0).getName())) {
        keep = false;
      }
      if (keep) {
        query.addFilter(f);
      }
    }

    // Adding exist filters for any requested properties or group by properties that need them.
    for (String propName : Iterables.concat(query.getPropertyNameList(), query.getGroupByPropertyNameList())) {
      if (allProperties.add(propName)) {
        query
            .addFilterBuilder()
            .setOp(Operator.EXISTS)
            .addPropertyBuilder()
            .setName(propName)
            .setMultiple(false)
            .getValueBuilder();
      }
    }
    // NOTE: Keep in sync with MegastoreQueryPlanner#normalizeForKeyComponents()

    /* Strip all orders if filtering on __key__ with equals. */
    for (Filter f : query.getFilterList()) {
      if (f.getOp() == Operator.EQUAL
          && f.getPropertyCount() >= 1
          && f.getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY)) {
        query.clearOrder();
        break;
      }
    }

    /* Strip all orders that follow a ordering on __key__ as keys are unique
     * thus additional ordering has no effect. */
    boolean foundKeyOrder = false;
    List<Order> ordersAfterLoop2 = new ArrayList<>(query.getOrderList());
    query.clearOrder();
    for (Order o : ordersAfterLoop2) {
      if (!foundKeyOrder) {
        query.addOrder(o);
        if (o.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)) {
          foundKeyOrder = true;
        }
      }
    }
  }
}
