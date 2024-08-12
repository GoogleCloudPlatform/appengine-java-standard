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

import static com.google.appengine.api.datastore.BaseEntityComparator.KEY_ASC_ORDER;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.datastore.EntityProtoComparators.EntityProtoComparator;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter.Operator;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order.Direction;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EntityComparatorTests {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();

  @Test
  public void testGetPropertyValue() throws Exception {
    Key key = KeyFactory.createKey("flower", 23);
    Entity e = new Entity(key);
    e.setProperty("nullProp", null);
    e.setProperty("prop1", "val1");
    e.setProperty("listProp1", Arrays.asList());
    e.setProperty("listProp2", Arrays.asList("listval1"));
    e.setProperty("listProp3", Arrays.asList("listval1", "listval2"));
    e.setProperty("shortByteProp1", new ShortBlob("shortBytes".getBytes()));
    List<Comparable<Object>> listWithNull = Lists.newArrayList();
    listWithNull.add(null);

    assertEqComparable(e, listWithNull, "nullProp");
    assertEqComparable(e, Arrays.asList(byteArray("val1")), "prop1");
    assertEqComparable(e, listWithNull, "listProp1");
    assertEqComparable(e, null, "prop2");
    assertEqComparable(e, Arrays.asList(key), Entity.KEY_RESERVED_PROPERTY);
    assertEqComparable(e, null, "__unknown__");
    assertEqComparable(e, Arrays.asList(byteArray("listval1")), "listProp2");
    assertEqComparable(e, Arrays.asList(byteArray("listval1"), byteArray("listval2")), "listProp3");
    assertEqComparable(e, Arrays.asList(byteArray("shortBytes")), "shortByteProp1");
  }

  private void assertEqComparable(Entity entity, Object expected, String prop) {
    EntityComparator comp = new EntityComparator(Collections.<Order>emptyList());
    assertThat(comp.getComparablePropertyValues(entity, prop)).isEqualTo(expected);

    OnestoreEntity.EntityProto proto = EntityTranslator.convertToPb(entity);
    EntityProtoComparator protoComp = new EntityProtoComparator(Collections.<Order>emptyList());
    assertThat(protoComp.getComparablePropertyValues(proto, prop)).isEqualTo(expected);
  }

  @Test
  public void testMultiTypeExtremeWithFilter() {
    FilterMatcher matcher = new FilterMatcher();
    Filter filter = new Filter().setOp(Operator.GREATER_THAN);
    filter.addProperty().setName("a").getMutableValue().setInt64Value(3);
    matcher.addFilter(filter);
    filter.setOp(Operator.LESS_THAN).getProperty(0).getValue().setInt64Value(5);
    matcher.addFilter(filter);

    assertThat(BaseEntityComparator.multiTypeExtreme(nastyCast(5L, 4L, 3L), true, matcher))
        .isEqualTo(4L);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BaseEntityComparator.multiTypeExtreme(
                nastyCast(byteArray("5"), byteArray("4"), byteArray("3")), true, matcher));
    assertThat(BaseEntityComparator.multiTypeExtreme(nastyCast(4L, true, 4L, false), true, matcher))
        .isEqualTo(4L);
    assertThat(BaseEntityComparator.multiTypeExtreme(nastyCast(5L, 4L, 3L), false, matcher))
        .isEqualTo(4L);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BaseEntityComparator.multiTypeExtreme(
                nastyCast(byteArray("5"), byteArray("4"), byteArray("3")), false, matcher));
    assertThat(
            BaseEntityComparator.multiTypeExtreme(nastyCast(4L, true, 4L, false), false, matcher))
        .isEqualTo(4L);
  }

  @Test
  public void testDefaultSort() {
    // if no sorts provided we end up with the default sort
    List<Order> orders = Lists.newArrayList();
    List<? extends BaseEntityComparator<?>> entityComps =
        Arrays.asList(new EntityProtoComparator(orders), new EntityComparator(orders));
    for (BaseEntityComparator<?> comp : entityComps) {
      assertThat(comp.orders).containsExactly(KEY_ASC_ORDER);
    }

    // if a sort a non-key field is provided we add the default sort to the
    // end.
    Order notAKeySort = new Order().setProperty("not a key");
    orders.add(notAKeySort);
    entityComps = Arrays.asList(new EntityProtoComparator(orders), new EntityComparator(orders));
    for (BaseEntityComparator<?> comp : entityComps) {
      assertThat(comp.orders).containsExactly(notAKeySort, KEY_ASC_ORDER).inOrder();
    }

    // if we explicitly add the default sort to the end we don't end up with
    // more than one default sort
    orders.add(KEY_ASC_ORDER);
    entityComps = Arrays.asList(new EntityProtoComparator(orders), new EntityComparator(orders));
    for (BaseEntityComparator<?> comp : entityComps) {
      assertThat(comp.orders).containsExactly(notAKeySort, KEY_ASC_ORDER).inOrder();
    }
  }

  @Test
  public void testDefaultSortWithInequality() {
    // if no sorts provided we end up with the default sort
    List<Order> orders = Lists.newArrayList();
    Filter filter = new Filter().setOp(Operator.GREATER_THAN);
    String ineq = "ineq";
    filter.addProperty().setName(ineq).getMutableValue().setInt64Value(0);
    List<Filter> filters = Arrays.asList(filter);

    // Should default to having the ineq prop first that key asc
    EntityProtoComparator epc = new EntityProtoComparator(orders, filters);
    assertThat(epc.orders).hasSize(2);
    assertThat(epc.orders.get(0).getProperty()).isEqualTo(ineq);
    assertThat(epc.orders.get(0).getDirectionEnum()).isEqualTo(Direction.ASCENDING);
    assertThat(epc.orders.get(1)).isEqualTo(KEY_ASC_ORDER);

    // if a sort a non-key field is provided we add the default sort to the
    // end.
    orders.add(new Order().setProperty(ineq).setDirection(Direction.DESCENDING));
    epc = new EntityProtoComparator(orders, filters);
    assertThat(epc.orders).hasSize(2);
    assertThat(epc.orders.get(0).getProperty()).isEqualTo(ineq);
    assertThat(epc.orders.get(0).getDirectionEnum()).isEqualTo(Direction.DESCENDING);
    assertThat(epc.orders.get(1)).isEqualTo(KEY_ASC_ORDER);

    // if we explicitly ad the default sort to the end we don't end up with
    // more than one default sort
    orders.add(KEY_ASC_ORDER);
    epc = new EntityProtoComparator(orders);
    assertThat(epc.orders).hasSize(2);
    assertThat(epc.orders.get(0).getProperty()).isEqualTo(ineq);
    assertThat(epc.orders.get(0).getDirectionEnum()).isEqualTo(Direction.DESCENDING);
    assertThat(epc.orders.get(1)).isEqualTo(KEY_ASC_ORDER);
  }

  @Test
  public void testMultiValuePropertySort() {
    Entity e1 = new Entity("foo");
    e1.setProperty("a", Arrays.asList(null, 4L));
    OnestoreEntity.EntityProto p1 = EntityTranslator.convertToPb(e1);

    Entity e2 = new Entity("foo");
    e2.setProperty("a", Arrays.asList(2L, 3L));
    OnestoreEntity.EntityProto p2 = EntityTranslator.convertToPb(e2);

    Order desc = new Order().setProperty("a").setDirection(Direction.DESCENDING);
    EntityComparator comp = new EntityComparator(Collections.singletonList(desc));
    assertThat(comp.compare(e1, e2)).isEqualTo(-1);
    EntityProtoComparator protoComp = new EntityProtoComparator(Collections.singletonList(desc));
    assertThat(protoComp.compare(p1, p2)).isEqualTo(-1);

    Order asc = new Order().setProperty("a").setDirection(Direction.ASCENDING);
    comp = new EntityComparator(Collections.singletonList(asc));
    assertThat(comp.compare(e1, e2)).isEqualTo(-1);
    protoComp = new EntityProtoComparator(Collections.singletonList(asc));
    assertThat(protoComp.compare(p1, p2)).isEqualTo(-1);
  }

  @Test
  public void testMultiValuePropertySortWithInequality() {
    OnestoreEntity.EntityProto p1 = new OnestoreEntity.EntityProto();
    p1.addProperty()
        .setMultiple(true)
        .setName("a")
        .setValue(new OnestoreEntity.PropertyValue().setInt64Value(1L));
    p1.addProperty()
        .setMultiple(true)
        .setName("a")
        .setValue(new OnestoreEntity.PropertyValue().setInt64Value(4L));
    OnestoreEntity.EntityProto p2 = new OnestoreEntity.EntityProto();
    p2.addProperty()
        .setMultiple(true)
        .setName("a")
        .setValue(new OnestoreEntity.PropertyValue().setInt64Value(2L));
    p2.addProperty()
        .setMultiple(true)
        .setName("a")
        .setValue(new OnestoreEntity.PropertyValue().setInt64Value(3L));

    Filter filter = new Filter().setOp(Operator.GREATER_THAN);
    filter.addProperty().setName("a").getMutableValue().setInt64Value(2);

    Order desc = new Order().setProperty("a").setDirection(Direction.DESCENDING);
    EntityProtoComparator epc =
        new EntityProtoComparator(
            Collections.singletonList(desc), Collections.singletonList(filter));
    assertThat(epc.compare(p1, p2)).isEqualTo(-1);

    Order asc = new Order().setProperty("a").setDirection(Direction.ASCENDING);
    epc =
        new EntityProtoComparator(
            Collections.singletonList(asc), Collections.singletonList(filter));
    assertThat(epc.compare(p1, p2)).isEqualTo(1);
  }

  private static DataTypeTranslator.ComparableByteArray byteArray(String val) {
    return new DataTypeTranslator.ComparableByteArray(val.getBytes());
  }

  @SuppressWarnings("rawtypes")
  private static Collection<Comparable<Object>> nastyCast(Comparable... comps) {
    Collection<Comparable<Object>> coll = Lists.newArrayList();
    for (Comparable comp : comps) {
      @SuppressWarnings("unchecked")
      Comparable<Object> typeComp = comp;
      coll.add(typeComp);
    }
    return coll;
  }
}
