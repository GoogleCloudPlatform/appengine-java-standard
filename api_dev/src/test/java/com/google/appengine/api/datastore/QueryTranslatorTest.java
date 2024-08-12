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

import static com.google.appengine.api.datastore.FetchOptions.Builder.withChunkSize;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withDefaults;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withLimit;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withOffset;
import static com.google.appengine.api.datastore.FetchOptions.Builder.withPrefetchSize;
import static com.google.appengine.api.datastore.NamespaceUtils.getAppId;
import static com.google.appengine.api.datastore.NamespaceUtils.getAppIdWithNamespace;
import static com.google.appengine.api.datastore.NamespaceUtils.setNonEmptyDefaultApiNamespace;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order.Direction;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the DatastoreService QueryTranslator class. */
@RunWith(JUnit4.class)
public class QueryTranslatorTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testKind() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withLimit(42));
    assertThat(proto.getKind()).isEqualTo("foo");
  }

  @Test
  public void testAppId() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withLimit(42));
    assertThat(proto.getApp()).isEqualTo(getAppId());
  }

  @Test
  public void testAncestor() {
    Key key = new Key("foo");
    key.simulatePutForTesting(12345L);

    Query query = new Query("foo");
    query.setAncestor(key);

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withLimit(42));
    assertThat(proto.hasAncestor()).isTrue();
    assertThat(proto.getAncestor().getPath().elementSize()).isEqualTo(1);
    assertThat(proto.getAncestor().getPath().elements().get(0).getType()).isEqualTo("foo");
    assertThat(proto.getAncestor().getPath().elements().get(0).getId()).isEqualTo(12345L);
  }

  @Test
  public void testPrefetchChunkSize() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.hasCount()).isFalse();

    proto = QueryTranslator.convertToPb(query, withChunkSize(5));
    assertThat(proto.getCount()).isEqualTo(5);

    proto = QueryTranslator.convertToPb(query, withPrefetchSize(6));
    assertThat(proto.getCount()).isEqualTo(6);

    QueryTranslator.convertToPb(query, withChunkSize(5).prefetchSize(6));
    assertThat(proto.getCount()).isEqualTo(6);
  }

  @Test
  public void testSelectDistinct() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto;

    query.setDistinct(true);
    assertThrows(
        IllegalArgumentException.class, () -> QueryTranslator.convertToPb(query, withDefaults()));
    query.addProjection(new PropertyProjection("hi", null));
    proto = QueryTranslator.convertToPb(query, withDefaults());

    assertThat(proto.groupByPropertyNameSize()).isEqualTo(1);
    assertThat(proto.getGroupByPropertyName(0)).isEqualTo("hi");

    query.addProjection(new PropertyProjection("bye", String.class));
    proto = QueryTranslator.convertToPb(query, withDefaults());

    assertThat(proto.groupByPropertyNameSize()).isEqualTo(2);
    assertThat(proto.groupByPropertyNames()).containsExactly("hi", "bye");
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testFilter() {
    Query query = new Query("foo");
    query.addFilter("stringProp", Query.FilterOperator.EQUAL, "stringValue");
    query.addFilter("doubleProp", Query.FilterOperator.GREATER_THAN, 42.0);
    query.addFilter("nullProp", Query.FilterOperator.EQUAL, null);
    query.addFilter("multiValuedProp1", Query.FilterOperator.IN, Lists.newArrayList(31));
    query.addFilter("multiValuedProp2", Query.FilterOperator.IN, Lists.newArrayList(31, 32));

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.filterSize()).isEqualTo(5);

    Filter filter1 = proto.getFilter(0);
    assertThat(filter1.getProperty(0).getName()).isEqualTo("stringProp");
    assertThat(filter1.getProperty(0).getValue().getStringValue()).isEqualTo("stringValue");
    assertThat(filter1.getOpEnum()).isEqualTo(Operator.EQUAL);

    Filter filter2 = proto.getFilter(1);
    assertThat(filter2.getProperty(0).getName()).isEqualTo("doubleProp");
    assertThat(filter2.getProperty(0).getValue().getDoubleValue()).isWithin(0.00001).of(42.0);
    assertThat(filter2.getOpEnum()).isEqualTo(Operator.GREATER_THAN);

    Filter filter3 = proto.getFilter(2);
    assertThat(filter3.getProperty(0).getName()).isEqualTo("nullProp");
    assertThat(filter3.getProperty(0).getValue()).isEqualTo(new PropertyValue());
    assertThat(filter3.getOpEnum()).isEqualTo(Operator.EQUAL);

    Filter filter4 = proto.getFilter(3);
    assertThat(filter4.getProperty(0).getName()).isEqualTo("multiValuedProp1");
    assertThat(filter4.getProperty(0).getValue().getInt64Value()).isEqualTo(31L);
    assertThat(filter4.getOpEnum()).isEqualTo(Operator.IN);

    Filter filter5 = proto.getFilter(4);
    assertThat(filter5.getProperty(0).getName()).isEqualTo("multiValuedProp2");
    assertThat(filter5.getProperty(0).getValue().getInt64Value()).isEqualTo(31L);
    assertThat(filter5.getProperty(1).getValue().getInt64Value()).isEqualTo(32L);
    assertThat(filter5.getOpEnum()).isEqualTo(Operator.IN);
  }

  @Test
  public void testGeoFilter_Circle() {
    Query query = new Query("foo");
    float lat = 1;
    float lng = 2;
    GeoPt point = new GeoPt(lat, lng);
    double radius = 1609.34;
    query.setFilter(
        new Query.StContainsFilter("location", new Query.GeoRegion.Circle(point, radius)));

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.filterSize()).isEqualTo(1);

    Filter filter1 = proto.getFilter(0);
    assertThat(filter1.getProperty(0).getName()).isEqualTo("location");
    assertThat(filter1.getOpEnum()).isEqualTo(Operator.CONTAINED_IN_REGION);
    // it "has a value" only because "value" is a required field.  But the value has
    // nothing in it: i.e., no point_value (nor any other kind either).
    assertThat(filter1.getProperty(0).getValue().hasPointValue()).isFalse();
    assertThat(filter1.hasGeoRegion()).isTrue();
    DatastoreV3Pb.GeoRegion geoRegion = filter1.getGeoRegion();
    assertThat(geoRegion.hasCircle()).isTrue();
    assertThat(geoRegion.hasRectangle()).isFalse();
    assertThat(geoRegion.getCircle().getRadiusMeters()).isEqualTo(radius);
    assertThat((float) geoRegion.getCircle().getCenter().getLatitude()).isEqualTo(lat);
    assertThat((float) geoRegion.getCircle().getCenter().getLongitude()).isEqualTo(lng);
  }

  @Test
  public void testGeoFilter_Rectangle() {
    Query query = new Query("foo");
    GeoPt point1 = new GeoPt(1, 2);
    GeoPt point2 = new GeoPt(3, 4);
    query.setFilter(
        new Query.StContainsFilter("location", new Query.GeoRegion.Rectangle(point1, point2)));

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.filterSize()).isEqualTo(1);

    Filter filter1 = proto.getFilter(0);
    assertThat(filter1.getProperty(0).getName()).isEqualTo("location");
    assertThat(filter1.getOpEnum()).isEqualTo(Operator.CONTAINED_IN_REGION);
    DatastoreV3Pb.GeoRegion geoRegion = filter1.getGeoRegion();
    assertThat(geoRegion.hasRectangle()).isTrue();
    assertThat(geoRegion.hasCircle()).isFalse();
    assertThat((float) geoRegion.getRectangle().getSouthwest().getLatitude())
        .isEqualTo(point1.getLatitude());
    assertThat((float) geoRegion.getRectangle().getSouthwest().getLongitude())
        .isEqualTo(point1.getLongitude());
    assertThat((float) geoRegion.getRectangle().getNortheast().getLatitude())
        .isEqualTo(point2.getLatitude());
    assertThat((float) geoRegion.getRectangle().getNortheast().getLongitude())
        .isEqualTo(point2.getLongitude());
  }

  @Test
  public void testGeoFilter_withPreintersection() {
    Query query = new Query("foo");
    float lat = 1;
    float lng = 2;
    GeoPt point = new GeoPt(lat, lng);
    double radius = 1609.34;
    String rating = "3-stars";
    query.setFilter(
        new Query.CompositeFilter(
            Query.CompositeFilterOperator.AND,
            Arrays.asList(
                new Query.StContainsFilter("location", new Query.GeoRegion.Circle(point, radius)),
                Query.FilterOperator.EQUAL.of("rating", rating))));

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.filterSize()).isEqualTo(2);

    Filter filter1 = proto.getFilter(0);
    assertThat(filter1.getProperty(0).getName()).isEqualTo("location");
    assertThat(filter1.getOpEnum()).isEqualTo(Operator.CONTAINED_IN_REGION);

    Filter filter2 = proto.getFilter(1);
    assertThat(filter2.getProperty(0).getName()).isEqualTo("rating");
    assertThat(filter2.getOpEnum()).isEqualTo(Operator.EQUAL);
    assertThat(filter2.hasGeoRegion()).isFalse();
    PropertyValue value = filter2.getProperty(0).getValue();
    assertThat(value.getStringValue()).isEqualTo(rating);
  }

  /**
   * Ensures that we can handle nested composite filters. There's no real need for these (they don't
   * provide any more flexibility than just a single level of composite); but they're easy to
   * support, and would be more trouble than worth to prohibit.
   */
  @Test
  public void testGeoFilter_nestedTree() {
    Query query = new Query("foo");
    float lat = 1;
    float lng = 2;
    GeoPt point = new GeoPt(lat, lng);
    double radius = 1609.34;
    String rating = "3-stars";
    String bar = "quux";
    query.setFilter(
        new Query.CompositeFilter(
            Query.CompositeFilterOperator.AND,
            Arrays.asList(
                new Query.CompositeFilter(
                    Query.CompositeFilterOperator.AND,
                    Arrays.asList(
                        new Query.StContainsFilter(
                            "location", new Query.GeoRegion.Circle(point, radius)),
                        Query.FilterOperator.EQUAL.of("rating", rating))),
                Query.FilterOperator.EQUAL.of("bar", bar))));

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.filterSize()).isEqualTo(3);

    Filter filter1 = proto.getFilter(0);
    assertThat(filter1.getProperty(0).getName()).isEqualTo("location");
    assertThat(filter1.getOpEnum()).isEqualTo(Operator.CONTAINED_IN_REGION);

    Filter filter2 = proto.getFilter(1);
    assertThat(filter2.getProperty(0).getName()).isEqualTo("rating");

    Filter filter3 = proto.getFilter(2);
    assertThat(filter3.getProperty(0).getName()).isEqualTo("bar");
  }

  /** Ensures that we reject a composite geo filter that tries to use OR opeartor. */
  @Test
  public void testGeoFilter_BadOrComposite() {
    Query query = new Query("foo");
    float lat = 1;
    float lng = 2;
    GeoPt point = new GeoPt(lat, lng);
    double radius = 1609.34;
    String rating = "3-stars";
    query.setFilter(
        new Query.CompositeFilter(
            Query.CompositeFilterOperator.OR,
            Arrays.asList(
                new Query.StContainsFilter("location", new Query.GeoRegion.Circle(point, radius)),
                Query.FilterOperator.EQUAL.of("rating", rating))));

    assertThrows(
        IllegalArgumentException.class, () -> QueryTranslator.convertToPb(query, withDefaults()));
  }

  @Test
  public void testGeoFilter_BadInequality() {
    Query query = new Query("foo");
    float lat = 1;
    float lng = 2;
    GeoPt point = new GeoPt(lat, lng);
    double radius = 1609.34;
    String rating = "3-stars";
    query.setFilter(
        new Query.CompositeFilter(
            Query.CompositeFilterOperator.AND,
            Arrays.asList(
                new Query.StContainsFilter("location", new Query.GeoRegion.Circle(point, radius)),
                Query.FilterOperator.LESS_THAN.of("rating", rating))));

    assertThrows(
        IllegalArgumentException.class, () -> QueryTranslator.convertToPb(query, withDefaults()));
  }

  @Test
  public void testSort() {
    Query query = new Query("foo");
    query.addSort("stringProp");
    query.addSort("doubleProp", Query.SortDirection.DESCENDING);

    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.orderSize()).isEqualTo(2);

    Order order1 = proto.getOrder(0);
    assertThat(order1.getProperty()).isEqualTo("stringProp");
    assertThat(order1.getDirectionEnum()).isEqualTo(Direction.ASCENDING);

    Order order2 = proto.getOrder(1);
    assertThat(order2.getProperty()).isEqualTo("doubleProp");
    assertThat(order2.getDirectionEnum()).isEqualTo(Direction.DESCENDING);
  }

  @Test
  public void testConvertToPbNoLimitNoOffset() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.hasLimit()).isFalse();
    assertThat(proto.hasOffset()).isFalse();
  }

  @Test
  public void testConvertToPbWithLimit() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withLimit(42));
    assertThat(proto.getLimit()).isEqualTo(42);
    assertThat(proto.getOffset()).isEqualTo(0);
  }

  @Test
  public void testConvertToPbWithOffset() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withOffset(10));
    assertThat(proto.hasLimit()).isFalse();
    assertThat(proto.getOffset()).isEqualTo(10);
  }

  @Test
  public void testKeysOnly() {
    Query query = new Query("foo");
    assertThat(query.isKeysOnly()).isFalse();
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withChunkSize(10));
    assertThat(proto.isKeysOnly()).isFalse();

    query.setKeysOnly();
    assertThat(query.isKeysOnly()).isTrue();
    proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.isKeysOnly()).isTrue();

    query.clearKeysOnly();
    assertThat(query.isKeysOnly()).isFalse();
    proto = QueryTranslator.convertToPb(query, withChunkSize(10));
    assertThat(proto.isKeysOnly()).isFalse();
  }

  @Test
  public void testPropertyNames() {
    Query query = new Query("foo");
    assertThat(query.getProjections()).isEmpty();
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withChunkSize(10));
    assertThat(proto.propertyNameSize()).isEqualTo(0);

    query
        .addProjection(new PropertyProjection("hi", null))
        .addProjection(new PropertyProjection("bye", String.class));
    assertThat(query.getProjections())
        .containsExactly(
            new PropertyProjection("hi", null), new PropertyProjection("bye", String.class));
    proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.propertyNameSize()).isEqualTo(2);
    assertThat(proto.propertyNames()).contains("hi");
    assertThat(proto.propertyNames()).contains("bye");
  }

  @Test
  public void testPrefetch() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(proto.hasCount()).isFalse();

    proto = QueryTranslator.convertToPb(query, withPrefetchSize(10));
    assertThat(proto.hasCount()).isTrue();
    assertThat(proto.getCount()).isEqualTo(10);
  }

  @Test
  public void testNamespaceDefault() {
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    assertThat(getAppId()).isEqualTo(proto.getApp());
    assertThat(proto.hasNameSpace()).isFalse();
  }

  @Test
  public void testNamespaceNonDefault() {
    setNonEmptyDefaultApiNamespace();
    Query query = new Query("foo");
    DatastoreV3Pb.Query proto = QueryTranslator.convertToPb(query, withDefaults());
    AppIdNamespace appIdNamespace =
        AppIdNamespace.parseEncodedAppIdNamespace(getAppIdWithNamespace());
    assertThat(appIdNamespace.getAppId()).isEqualTo(proto.getApp());
    assertThat(proto.hasNameSpace()).isTrue();
    assertThat(appIdNamespace.getNamespace()).isEqualTo(proto.getNameSpace());
  }
}
