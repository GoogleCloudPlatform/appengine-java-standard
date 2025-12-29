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

import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Query.Order;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.EntityProto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Utilities for comparing {@link EntityProto}. This class is only public because the dev appserver
 * needs access to it. It should not be accessed by user code.
 *
 */
public final class EntityProtoComparators {

  public static final Comparator<Comparable<Object>> MULTI_TYPE_COMPARATOR =
      BaseEntityComparator.MULTI_TYPE_COMPARATOR;

  /**
   * A comparator for {@link EntityProto} objects with
   * the same ordering as {@link EntityComparator}.
   */
  public static final class EntityProtoComparator
      extends BaseEntityComparator<OnestoreEntity.EntityProto> {

    public EntityProtoComparator(List<Order> orders) {
      super(orders, Collections.<Filter>emptyList());
    }

    public EntityProtoComparator(List<Order> orders, List<Filter> filters) {
      super(orders, filters);
    }

    public List<Order> getAdjustedOrders() {
      return Collections.unmodifiableList(orders);
    }

    public boolean matches(OnestoreEntity.EntityProto proto) {
      for (String property : filters.keySet()) {
        List<Comparable<Object>> values = getComparablePropertyValues(proto, property);
        if (values == null || !filters.get(property).matches(values)) {
          return false;
        }
      }
      return true;
    }

    public boolean matches(OnestoreEntity.Property prop) {
      FilterMatcher filter = filters.get(prop.getName());
      if (filter == null) {
        return true;
      }
      return filter.matches(
          Collections.singletonList(DataTypeTranslator.getComparablePropertyValue(prop)));
    }

    @Override
    @Nullable
    List<Comparable<Object>> getComparablePropertyValues(
        OnestoreEntity.EntityProto entityProto, String propertyName) {
      Collection<OnestoreEntity.Property> entityProperties =
          DataTypeTranslator.findIndexedPropertiesOnPb(entityProto, propertyName);
      if (entityProperties.isEmpty()) {
        return null;
      }
      if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY) && !entityProto.hasKey()) {
        return null;
      }
      List<Comparable<Object>> values = new ArrayList<>(entityProperties.size());
      for (OnestoreEntity.Property prop : entityProperties) {
        values.add(DataTypeTranslator.getComparablePropertyValue(prop));
      }
      return values;
    }
  }

  private EntityProtoComparators() {}
}
