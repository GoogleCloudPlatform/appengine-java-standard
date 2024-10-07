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

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class NormalizedQuery {
  static final ImmutableSet<Operator> INEQUALITY_OPERATORS =
      ImmutableSet.of(
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL);

  protected final Query.Builder query;

  public NormalizedQuery(Query query) {
    this.query = query.toBuilder().clone();
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

    for (Iterator<Filter> itr = query.getFilterList().iterator(); itr.hasNext(); ) {
      Filter.Builder f = itr.next().toBuilder();
      /* Normalize IN filters to EQUAL. */
      if (f.getPropertyCount() == 1 && f.getOp() == Operator.IN) {
        f.setOp(Operator.EQUAL);
      }
      if (f.getPropertyCount() >= 1) {
        String name = f.getProperty(0).getName();
        if (f.getOp() == Operator.EQUAL) {
          if (!equalityFilterProperties.add(f.getProperty(0))) {
            // The filter is an exact duplicate, remove it.
            itr.remove();
          } else {
            equalityProperties.add(name);
          }
        } else if (INEQUALITY_OPERATORS.contains(f.getOp())) {
          inequalityProperties.add(name);
        }
      }
    }

    equalityProperties.removeAll(inequalityProperties);

    // Strip repeated orders and orders coinciding with EQUAL filters.
    for (Iterator<Order> itr = query.getOrderList().iterator(); itr.hasNext(); ) {
      if (!equalityProperties.add(itr.next().getProperty())) {
        itr.remove();
      }
    }

    Set<String> allProperties = equalityProperties;
    allProperties.addAll(inequalityProperties);

    // Removing redundant exists filters.
    for (Iterator<Filter> itr = query.getFilterList().iterator(); itr.hasNext(); ) {
      Filter f = itr.next();
      if (f.getOp() == Operator.EXISTS
          && f.getPropertyCount() >= 1
          && !allProperties.add(f.getProperty(0).getName())) {
        itr.remove();
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
            .getValue();
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
    for (Iterator<Order> i = query.getOrderList().iterator(); i.hasNext(); ) {
      String property = i.next().getProperty();
      if (foundKeyOrder) {
        i.remove();
      } else if (property.equals(Entity.KEY_RESERVED_PROPERTY)) {
        foundKeyOrder = true;
      }
    }
  }
}
