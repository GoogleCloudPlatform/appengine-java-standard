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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.GeoRegion;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.OnestoreEntity.Property;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue.ReferenceValue;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Wrapper around {@link Query} that performs validation. */
class ValidatedQuery extends NormalizedQuery {
  static final ImmutableSet<Operator> UNSUPPORTED_OPERATORS = ImmutableSet.of(Operator.IN);

  private boolean isGeo;

  /** @throws IllegalQueryException If the provided query fails validation. */
  ValidatedQuery(Query query) {
    super(query);
    validateQuery();
  }

  /**
   * Determines if a given query is supported by the datastore.
   *
   * @throws IllegalQueryException If the provided query fails validation.
   */
  private void validateQuery() {
    if (query.propertyNameSize() > 0 && query.isKeysOnly()) {
      throw new IllegalQueryException(
          "projection and keys_only cannot both be set", IllegalQueryType.ILLEGAL_PROJECTION);
    }

    Set<String> projectionProperties = new HashSet<String>(query.propertyNameSize());
    for (String property : query.propertyNames()) {
      if (!projectionProperties.add(property)) {
        throw new IllegalQueryException(
            "cannot project a property multiple times", IllegalQueryType.ILLEGAL_PROJECTION);
      }
    }

    Set<String> groupBySet = Sets.newHashSetWithExpectedSize(query.groupByPropertyNameSize());
    for (String name : query.groupByPropertyNames()) {
      if (!groupBySet.add(name)) {
        throw new IllegalQueryException(
            "cannot group by a property multiple times", IllegalQueryType.ILLEGAL_GROUPBY);
      }
      // TODO: Consider removing this check and relying on the backend to do this sort
      // of validation.  Note that there are use cases for special properties in projections and
      // filters.
      if (Entity.RESERVED_NAME.matcher(name).matches()) {
        throw new IllegalQueryException(
            "group by is not supported for the property: " + name,
            IllegalQueryType.ILLEGAL_GROUPBY);
      }
    }

    /* Validate group by properties in orderings. */
    Set<String> groupByInOrderSet =
        Sets.newHashSetWithExpectedSize(query.groupByPropertyNameSize());
    for (Order order : query.orders()) {
      if (groupBySet.contains(order.getProperty())) {
        groupByInOrderSet.add(order.getProperty());
      } else if (groupByInOrderSet.size() != groupBySet.size()) {
        throw new IllegalQueryException(
            "must specify all group by orderings before any non group by orderings",
            IllegalQueryType.ILLEGAL_GROUPBY);
      }
    }

    // Filters and sort orders require kind.
    if (!query.hasKind()) {
      for (Filter filter : query.filters()) {
        if (!filter.getProperty(0).getName().equals(Entity.KEY_RESERVED_PROPERTY)) {
          throw new IllegalQueryException(
              "kind is required for non-__key__ filters", IllegalQueryType.KIND_REQUIRED);
        }
      }
      for (Order order : query.orders()) {
        if (!(order.getProperty().equals(Entity.KEY_RESERVED_PROPERTY)
            && order.getDirection() == Order.Direction.ASCENDING.getValue())) {
          throw new IllegalQueryException(
              "kind is required for all orders except __key__ ascending",
              IllegalQueryType.KIND_REQUIRED);
        }
      }
    }

    /* Validate ancestor, if it exists. */
    if (query.hasAncestor()) {
      Reference ancestor = query.getAncestor();
      if (!ancestor.getApp().equals(query.getApp())) {
        throw new IllegalQueryException(
            "The query app is " + query.getApp() + " but ancestor app is " + ancestor.getApp(),
            IllegalQueryType.ILLEGAL_VALUE);
      }
      if (!ancestor.getNameSpace().equals(query.getNameSpace())) {
        throw new IllegalQueryException(
            "The query namespace is "
                + query.getNameSpace()
                + " but ancestor namespace is "
                + ancestor.getNameSpace(),
            IllegalQueryType.ILLEGAL_VALUE);
      }
    }

    // Check for unsupported filter values.  We only support one property
    // per filter and one property with an inequality filter.
    String ineqProp = null;
    this.isGeo = false;
    for (Filter filter : query.filters()) {
      int numProps = filter.propertySize();
      if (numProps != 1) {
        throw new IllegalQueryException(
            String.format("Filter has %s properties, expected 1", numProps),
            IllegalQueryType.FILTER_WITH_MULTIPLE_PROPS);
      }

      Property prop = filter.getProperty(0);
      String propName = prop.getName();

      /* Extra validation for __key__. The filter value must be a key,
       * if the query has a kind, the key's kind must match, and the
       * app and namespace must match the query. */
      if (Entity.KEY_RESERVED_PROPERTY.equals(propName)) {
        PropertyValue value = prop.getValue();
        if (!value.hasReferenceValue()) {
          throw new IllegalQueryException(
              Entity.KEY_RESERVED_PROPERTY + " filter value must be a Key",
              IllegalQueryType.ILLEGAL_VALUE);
        }
        ReferenceValue refVal = value.getReferenceValue();
        if (!refVal.getApp().equals(query.getApp())) {
          throw new IllegalQueryException(
              Entity.KEY_RESERVED_PROPERTY
                  + " filter app is "
                  + refVal.getApp()
                  + " but query app is "
                  + query.getApp(),
              IllegalQueryType.ILLEGAL_VALUE);
        }
        if (!refVal.getNameSpace().equals(query.getNameSpace())) {
          throw new IllegalQueryException(
              Entity.KEY_RESERVED_PROPERTY
                  + " filter namespace is "
                  + refVal.getNameSpace()
                  + " but query namespace is "
                  + query.getNameSpace(),
              IllegalQueryType.ILLEGAL_VALUE);
        }
      }

      if (INEQUALITY_OPERATORS.contains(filter.getOpEnum())) {
        if (ineqProp == null) {
          ineqProp = propName;
        } else if (!ineqProp.equals(propName)) {
          throw new IllegalQueryException(
              String.format(
                  "Only one inequality filter per query is supported.  "
                      + "Encountered both %s and %s",
                  ineqProp, propName),
              IllegalQueryType.MULTIPLE_INEQ_FILTERS);
        }
      } else if (filter.getOpEnum() == Operator.EQUAL) {
        if (projectionProperties.contains(propName)) {
          throw new IllegalQueryException(
              "cannot use projection on a property with an equality filter",
              IllegalQueryType.ILLEGAL_PROJECTION);
        } else if (groupBySet.contains(propName)) {
          throw new IllegalQueryException(
              "cannot use group by on a property with an equality filter",
              IllegalQueryType.ILLEGAL_GROUPBY);
        }
      } else if (filter.getOpEnum() == Operator.CONTAINED_IN_REGION) {
        isGeo = true;
        if (!filter.hasGeoRegion() || prop.getValue().hasPointValue()) {
          throw new IllegalQueryException(
              String.format(
                  "Geo-spatial filter on %s should specify GeoRegion rather than Property Value",
                  propName),
              IllegalQueryType.UNSUPPORTED_FILTER);
        }
        GeoRegion region = filter.getGeoRegion();
        if ((region.hasCircle() && region.hasRectangle())
            || (!region.hasCircle() && !region.hasRectangle())) {
          throw new IllegalQueryException(
              String.format(
                  "Geo-spatial filter on %s should specify Circle or Rectangle, but not both",
                  propName),
              IllegalQueryType.UNSUPPORTED_FILTER);
        }
      } else if (UNSUPPORTED_OPERATORS.contains(filter.getOpEnum())) {
        throw new IllegalQueryException(
            String.format("Unsupported filter operator: %s", filter.getOp()),
            IllegalQueryType.UNSUPPORTED_FILTER);
      }
    }

    if (isGeo) {
      if (ineqProp != null) {
        throw new IllegalQueryException(
            "Inequality filter with geo-spatial query is not supported.",
            IllegalQueryType.UNSUPPORTED_FILTER);
      }

      if (query.hasAncestor()) {
        throw new IllegalQueryException(
            "Geo-spatial filter on ancestor query is not supported.",
            IllegalQueryType.UNSUPPORTED_FILTER);
      }

      if (query.hasCompiledCursor() || query.hasEndCompiledCursor()) {
        throw new IllegalQueryException(
            "Start and end cursors are not supported on geo-spatial queries.",
            IllegalQueryType.CURSOR_NOT_SUPPORTED);
      }
    }

    if (ineqProp != null && query.groupByPropertyNameSize() > 0) {
      if (!groupBySet.contains(ineqProp)) {
        throw new IllegalQueryException(
            String.format(
                "Inequality filter on %s must also be a group by property when "
                    + "group by properties are set.",
                ineqProp),
            IllegalQueryType.ILLEGAL_GROUPBY);
      }
    }

    if (ineqProp != null) {
      if (query.orderSize() > 0) {
        if (!ineqProp.equals(query.getOrder(0).getProperty())) {
          // First order must match the inequality filter.
          throw new IllegalQueryException(
              String.format(
                  "The first sort property must be the same as the property to which "
                      + "the inequality filter is applied.  In your query the first sort property "
                      + "is %s but the inequality filter is on %s",
                  query.getOrder(0).getProperty(), ineqProp),
              IllegalQueryType.FIRST_SORT_NEQ_INEQ_PROP);
        }
      }
    }
  }

  public boolean isGeo() {
    return isGeo;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ValidatedQuery that = (ValidatedQuery) o;

    if (!query.equals(that.query)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return query.hashCode();
  }

  // Just used for testing.
  enum IllegalQueryType {
    KIND_REQUIRED,
    UNSUPPORTED_FILTER,
    FILTER_WITH_MULTIPLE_PROPS,
    MULTIPLE_INEQ_FILTERS,
    FIRST_SORT_NEQ_INEQ_PROP,
    ILLEGAL_VALUE,
    ILLEGAL_PROJECTION,
    ILLEGAL_GROUPBY,
    CURSOR_NOT_SUPPORTED,
  }

  // Just used for testing.
  static class IllegalQueryException extends ApiProxy.ApplicationException {
    private static final long serialVersionUID = -2398830747594327420L;

    private final IllegalQueryType illegalQueryType;

    IllegalQueryException(String errorDetail, IllegalQueryType illegalQueryType) {
      super(DatastoreV3Pb.Error.ErrorCode.BAD_REQUEST.getValue(), errorDetail);
      this.illegalQueryType = illegalQueryType;
    }

    IllegalQueryType getIllegalQueryType() {
      return illegalQueryType;
    }
  }
}
