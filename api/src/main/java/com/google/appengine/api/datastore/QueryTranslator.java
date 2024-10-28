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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order.Direction;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Reference;
import java.util.List;

/**
 * {@code QueryTranslator} contains the logic to translate a {@code Query} into the protocol buffers
 * that are used to pass it to the implementation of the API.
 */
final class QueryTranslator {

  @SuppressWarnings("deprecation")
  public static DatastoreV3Pb.Query convertToPb(Query query, FetchOptions fetchOptions) {
    Key ancestor = query.getAncestor();
    List<Query.SortPredicate> sortPredicates = query.getSortPredicates();

    DatastoreV3Pb.Query.Builder proto = DatastoreV3Pb.Query.newBuilder();

    if (query.getKind() != null) {
      proto.setKind(query.getKind());
    }

    proto.setApp(query.getAppIdNamespace().getAppId());
    String nameSpace = query.getAppIdNamespace().getNamespace();
    if (nameSpace.length() != 0) {
      proto.setNameSpace(nameSpace);
    }

    if (fetchOptions.getOffset() != null) {
      proto.setOffset(fetchOptions.getOffset());
    }

    if (fetchOptions.getLimit() != null) {
      proto.setLimit(fetchOptions.getLimit());
    }

    if (fetchOptions.getPrefetchSize() != null) {
      proto.setCount(fetchOptions.getPrefetchSize());
    } else if (fetchOptions.getChunkSize() != null) {
      proto.setCount(fetchOptions.getChunkSize());
    }

    if (fetchOptions.getStartCursor() != null) {
      try{
        proto
            .getCompiledCursor()
            .getParserForType()
            .parseFrom(fetchOptions.getStartCursor().toByteString());
      } catch (InvalidProtocolBufferException e){
        throw new IllegalArgumentException("Invalid cursor");
      }
    }

    if (fetchOptions.getEndCursor() != null) {
      try{
        proto
            .getEndCompiledCursor()
            .getParserForType()
            .parseFrom(fetchOptions.getEndCursor().toByteString());
      }catch (InvalidProtocolBufferException e){
        throw new IllegalArgumentException("Invalid cursor");
      }
    }

    if (fetchOptions.getCompile() != null) {
      proto.setCompile(fetchOptions.getCompile());
    }

    if (ancestor != null) {
      Reference ref = KeyTranslator.convertToPb(ancestor);
      if (!ref.getApp().equals(proto.getApp())) {
        throw new IllegalArgumentException("Query and ancestor appid/namespace mismatch");
      }
      proto.setAncestor(ref);
    }

    if (query.getDistinct()) {
      if (query.getProjections().isEmpty()) {
        throw new IllegalArgumentException(
            "Projected properties must be set to " + "allow for distinct projections");
      }
      for (Projection projection : query.getProjections()) {
        proto.addGroupByPropertyName(projection.getPropertyName());
      }
    }

    proto.setKeysOnly(query.isKeysOnly());

    Query.Filter filter = query.getFilter();
    if (filter != null) {
      // At this point, all non-geo queries have had their filters
      // converted to sets of FilterPredicate objects; so this must be
      // a geo query.
      copyGeoFilterToPb(filter, proto);
    } else {
      for (Query.FilterPredicate filterPredicate : query.getFilterPredicates()) {
        Filter.Builder filterPb = proto.addFilterBuilder();
        filterPb.mergeFrom(convertFilterPredicateToPb(filterPredicate));
      }
    }

    for (Query.SortPredicate sortPredicate : sortPredicates) {
      Order.Builder order = proto.addOrderBuilder();
      order.mergeFrom(convertSortPredicateToPb(sortPredicate));
    }

    for (Projection projection : query.getProjections()) {
      proto.addPropertyName(projection.getPropertyName());
    }

    return proto.build();
  }

  static Order convertSortPredicateToPb(Query.SortPredicate predicate) {
    Order.Builder order = Order.newBuilder();
    order.setProperty(predicate.getPropertyName());
    order.setDirection(getSortOp(predicate.getDirection()));
    return order.build();
  }

  private static Direction getSortOp(Query.SortDirection direction) {
    switch (direction) {
      case ASCENDING:
        return Direction.ASCENDING;
      case DESCENDING:
        return Direction.DESCENDING;
      default:
        throw new UnsupportedOperationException("direction: " + direction);
    }
  }

  /**
   * Converts the filter from a geo-spatial query into proto-buf form. Should only be called when
   * the filter indeed has a geo-spatial term; but the filter as a whole has not yet been entirely
   * validated, so we complete the validation here.
   */
  private static void copyGeoFilterToPb(Query.Filter filter, DatastoreV3Pb.Query.Builder proto) {
    if (filter instanceof Query.CompositeFilter) {
      Query.CompositeFilter conjunction = (Query.CompositeFilter) filter;
      checkArgument(
          conjunction.getOperator() == Query.CompositeFilterOperator.AND,
          "Geo-spatial filters may only be composed with CompositeFilterOperator.AND");
      for (Query.Filter f : conjunction.getSubFilters()) {
        copyGeoFilterToPb(f, proto);
      }
    } else if (filter instanceof Query.StContainsFilter) {
      Query.StContainsFilter containmentFilter = (Query.StContainsFilter) filter;
      Filter.Builder f = proto.addFilterBuilder();
      f.setOp(Operator.CONTAINED_IN_REGION);
      f.setGeoRegion(convertGeoRegionToPb(containmentFilter.getRegion()));
      // It's a bit weird to add a Value with nothing in it; but we
      // need Property in order to convey the property name, and Value
      // is required in Property.  But in our case there is no value:
      // the geo region acts as the thing to which we "compare" the property.
      f.addPropertyBuilder()
          .setName(containmentFilter.getPropertyName())
          .setMultiple(false)
          .setValue(PropertyValue.getDefaultInstance());
    } else {
      checkArgument(filter instanceof Query.FilterPredicate);
      Query.FilterPredicate predicate = (Query.FilterPredicate) filter;
      checkArgument(
          predicate.getOperator() == Query.FilterOperator.EQUAL,
          "Geo-spatial filters may only be combined with equality comparisons");
      Filter.Builder f = proto.addFilterBuilder();
      f.mergeFrom(convertFilterPredicateToPb(predicate));
    }
  }

  private static Filter convertFilterPredicateToPb(Query.FilterPredicate predicate) {
    Filter.Builder filterPb = Filter.newBuilder();
    filterPb.setOp(getFilterOp(predicate.getOperator()));

    if (predicate.getValue() instanceof Iterable<?>) {
      if (predicate.getOperator() != Query.FilterOperator.IN) {
        throw new IllegalArgumentException("Only the IN operator supports multiple values");
      }
      for (Object value : (Iterable<?>) predicate.getValue()) {
        filterPb
            .addPropertyBuilder()
            .setName(predicate.getPropertyName())
            .setValue(DataTypeTranslator.toV3Value(value));
      }
    } else {
      filterPb
          .addPropertyBuilder()
          .setName(predicate.getPropertyName())
          .setValue(DataTypeTranslator.toV3Value(predicate.getValue()));
    }

    return filterPb.build();
  }

  private static DatastoreV3Pb.GeoRegion convertGeoRegionToPb(Query.GeoRegion region) {
    DatastoreV3Pb.GeoRegion.Builder geoRegion = DatastoreV3Pb.GeoRegion.newBuilder();
    if (region instanceof Query.GeoRegion.Circle) {
      Query.GeoRegion.Circle circle = (Query.GeoRegion.Circle) region;
      DatastoreV3Pb.CircleRegion.Builder circlePb = DatastoreV3Pb.CircleRegion.newBuilder();
      circlePb.setCenter(convertGeoPtToPb(circle.getCenter()));
      circlePb.setRadiusMeters(circle.getRadius());
      geoRegion.setCircle(circlePb);
    } else if (region instanceof Query.GeoRegion.Rectangle) {
      Query.GeoRegion.Rectangle rect = (Query.GeoRegion.Rectangle) region;
      DatastoreV3Pb.RectangleRegion.Builder rectPb = DatastoreV3Pb.RectangleRegion.newBuilder();
      rectPb.setSouthwest(convertGeoPtToPb(rect.getSouthwest()));
      rectPb.setNortheast(convertGeoPtToPb(rect.getNortheast()));
      geoRegion.setRectangle(rectPb);
    } else {
      throw new IllegalArgumentException("missing or unknown-type region in StContainsFilter");
    }
    return geoRegion.build();
  }

  private static DatastoreV3Pb.RegionPoint convertGeoPtToPb(GeoPt point) {
    DatastoreV3Pb.RegionPoint.Builder pointPb = DatastoreV3Pb.RegionPoint.newBuilder();
    pointPb.setLatitude(point.getLatitude());
    pointPb.setLongitude(point.getLongitude());
    return pointPb.build();
  }

  private static Operator getFilterOp(Query.FilterOperator operator) {
    switch (operator) {
      case LESS_THAN:
        return Operator.LESS_THAN;
      case LESS_THAN_OR_EQUAL:
        return Operator.LESS_THAN_OR_EQUAL;
      case GREATER_THAN:
        return Operator.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL:
        return Operator.GREATER_THAN_OR_EQUAL;
      case EQUAL:
        return Operator.EQUAL;
      case IN:
        return Operator.IN;
      default:
        throw new UnsupportedOperationException("operator: " + operator);
    }
  }

  // All methods are static.  Do not instantiate.
  private QueryTranslator() {}
}
