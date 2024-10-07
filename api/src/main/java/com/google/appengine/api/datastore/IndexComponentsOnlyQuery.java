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

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A query as it is actually planned on the datastore indices.
 *
 * <p>This query should not be used to actually run a query. It is only useful when determining what
 * indices are needed to satisfy a Query
 *
 * <p>In the production datastore, some components of the query can be fulfilled natively, so before
 * we try to determine what composite indexes this query requires we want to strip out those
 * components. An example of this is sort by __key__ ascending. This can always be stripped out as
 * all tables are sorted by __key__ ascending natively
 *
 * <p>This class also categorizes query components into groups that are useful for discerning what
 * indices are needed to satisfy it. Specifically it constructs a set of the properties involved in
 * equality filters, and also constructs a list of index properties.
 */
class IndexComponentsOnlyQuery extends ValidatedQuery {
  private final List<String> equalityProps = Lists.newArrayList();
  private final List<Property> orderProps = Lists.newArrayList();
  private final Set<String> existsProps = Sets.newHashSet();
  private final Set<String> groupByProps = Sets.newHashSet();
  private final List<String> containmentProps = new ArrayList<>();

  private boolean hasKeyProperty = false;

  public IndexComponentsOnlyQuery(DatastoreV3Pb.Query query) {
    super(query);
    removeNativelySupportedComponents();
    categorizeQuery();
  }

  private void removeNativelySupportedComponents() {
    /* NOTE: Keep in sync with datastore_index.py:RemoveNativelySupportedComponents() */

    for (Filter filter : query.getFilterList()) {
      if (filter.getOp() == Operator.EXISTS) {
        // Exists filters cause properties to appear after the sort order specified
        // in the query, so the native key sort is actually not the next order in
        // the index (and thus cannot be removed).
        return;
      }
    }

    // Pulling out __key__ asc orders since is supported natively for perfect plans
    boolean hasKeyDescOrder = false;
    if (query.getOrderCount() > 0) {
      Order lastOrder = query.getOrder(query.getCount() - 1);
      if (lastOrder.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)) {
        if (lastOrder.getDirection() == Order.Direction.ASCENDING) {
          query.toBuilder().removeOrder(query.getOrderCount() - 1).build();
        } else {
          hasKeyDescOrder = true;
        }
      }
    }

    /* Pulling out __key__ filters for queries that support this natively
     * NOTE: this is all queries except those that have non-key
     * inequality filters or have __key__ DESC order. i.e.:
     *   WHERE X > y AND __key__ = k
     *   WHERE __key__ > k ORDER BY __key__ DESC
     */
    if (!hasKeyDescOrder) {
      boolean hasNonKeyInequality = false;
      for (Filter f : query.getFilterList()) {
        if (ValidatedQuery.INEQUALITY_OPERATORS.contains(f.getOp())
            && !Entity.KEY_RESERVED_PROPERTY.equals(f.getProperty(0).getName())) {
          hasNonKeyInequality = true;
          break;
        }
      }

      if (!hasNonKeyInequality) {
        // __key__ filters can be planned natively, so remove them
        Iterator<Filter> itr = query.getFilterList().iterator();
        while (itr.hasNext()) {
          if (itr.next().getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY)) {
            itr.remove();
          }
        }
      }
    }
  }

  private void categorizeQuery() {
    Set<String> ineqProps = Sets.newHashSet();
    hasKeyProperty = false;
    for (Filter filter : query.getFilterList()) {
      String propName = filter.getProperty(0).getName();
      switch (filter.getOp()) {
        case EQUAL:
          equalityProps.add(propName);
          break;
        case EXISTS:
          existsProps.add(propName);
          break;
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL:
        case LESS_THAN:
        case LESS_THAN_OR_EQUAL:
          ineqProps.add(propName);
          break;
        case CONTAINED_IN_REGION:
          containmentProps.add(propName);
          break;
        default:
          throw new IllegalArgumentException(
              "Unable to categorize query using filter operator " + filter.getOp());
      }
      if (propName.equals(Entity.KEY_RESERVED_PROPERTY)) {
        hasKeyProperty = true;
      }
    }

    // Add the inequality filter properties, if any.
    if (query.getOrderCount() == 0 && !ineqProps.isEmpty()) {
      // We do not add an index property for the inequality filter because
      // it will be taken care of when we add the sort on that same property
      // down below.
      orderProps.add(Property.newBuilder().setName(ineqProps.iterator().next()).build());
    }

    groupByProps.addAll(query.getGroupByPropertyNameList());
    // If a property is included in the group by, its existance will be satisfied.
    existsProps.removeAll(groupByProps);

    // Add orders.
    for (Order order : query.getOrderList()) {
      if (order.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)) {
        hasKeyProperty = true;
      }
      // If a property is in the ordering, it has already been satisfied.
      groupByProps.remove(order.getProperty());
      orderProps.add(
          Property.newBuilder()
              .setName(order.getProperty())
              .setDirection(order.getDirection())
              .build());
    }
  }

  /**
   * Returns a list of {@link IndexComponent}s that represent the postfix constraints for this
   * query.
   */
  public List<IndexComponent> getPostfix() {
    return Lists.newArrayList(
        new OrderedIndexComponent(orderProps),
        new UnorderedIndexComponent(groupByProps),
        new UnorderedIndexComponent(existsProps));
  }

  /**
   * Returns the set of names of properties that represent the unordered property constraints of the
   * prefix for this query.
   */
  public List<String> getPrefix() {
    return equalityProps;
  }

  public List<String> getGeoProperties() {
    return containmentProps;
  }

  public boolean hasKeyProperty() {
    return hasKeyProperty;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IndexComponentsOnlyQuery that = (IndexComponentsOnlyQuery) o;

    if (!query.equals(that.query)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return query.hashCode();
  }
}
