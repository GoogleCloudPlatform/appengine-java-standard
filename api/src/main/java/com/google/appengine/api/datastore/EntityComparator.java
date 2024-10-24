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

import static com.google.appengine.api.datastore.DataTypeTranslator.getComparablePropertyValue;

import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query.Order;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A comparator with the same ordering as {@link EntityProtoComparators} which uses Entity objects
 * rather than protos.
 */
class EntityComparator extends BaseEntityComparator<Entity> {

  EntityComparator(List<Order> orders) {
    super(orders, Collections.<Filter>emptyList());
  }

  @Override
  @Nullable
  List<Comparable<Object>> getComparablePropertyValues(Entity entity, String propertyName) {
    Object prop;
    if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY)) {
      prop = entity.getKey();
    } else if (!entity.hasProperty(propertyName)) {
      return null;
    } else {
      prop = entity.getProperty(propertyName);
    }
    if (prop instanceof Collection<?>) {
      Collection<?> props = (Collection<?>) prop;
      if (props.isEmpty()) {
        return Collections.singletonList(null);
      }
      List<Comparable<Object>> comparableProps = new ArrayList<>(props.size());
      for (Object curProp : props) {
        comparableProps.add(getComparablePropertyValue(curProp));
      }
      return comparableProps;
    } else {
      return Collections.singletonList(getComparablePropertyValue(prop));
    }
  }

  // Called from jt/c/g/ae/api/datastore/dev/QueryTest.java using reflection.
  static EntityComparator fromSortPredicates(List<SortPredicate> sortPredicates) {
    List<DatastoreV3Pb.Query.Order> orders = new ArrayList<>(sortPredicates.size());
    for (SortPredicate predicate : sortPredicates) {
      orders.add(QueryTranslator.convertSortPredicateToPb(predicate));
    }
    return new EntityComparator(orders);
  }
}
