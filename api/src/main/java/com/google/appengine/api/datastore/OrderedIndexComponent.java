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

import com.google.common.collect.Lists;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index.Property.Direction;
import java.util.List;
import java.util.ListIterator;

/**
 * Implements an IndexComponent for an ordered list of properties.
 *
 * <p>Currently, this is used for ordering constraints.
 *
 */
class OrderedIndexComponent implements IndexComponent {
  private final List<Property> matcherProperties;

  public OrderedIndexComponent(List<Property> orderedGroup) {
    this.matcherProperties = orderedGroup;
  }

  @Override
  public boolean matches(List<Property> indexProperties) {
    // Go through the index in reverse since we don't know where the prefix/postfix split is.
    ListIterator<Property> indexItr = indexProperties.listIterator(indexProperties.size());
    ListIterator<Property> matcherItr = matcherProperties.listIterator(matcherProperties.size());
    while (indexItr.hasPrevious() && matcherItr.hasPrevious()) {
      if (!propertySatisfies(matcherItr.previous(), indexItr.previous())) {
        return false;
      }
    }
    return !matcherItr.hasPrevious();
  }

  @Override
  public List<Property> preferredIndexProperties() {
    List<Property> indexProps = Lists.newArrayListWithExpectedSize(matcherProperties.size());
    for (Property prop : matcherProperties) {
      if (!prop.hasDirection()) {
        prop = prop.toBuilder().clone().build();
        prop = prop.toBuilder().setDirection(Direction.ASCENDING).build();
      }
      indexProps.add(prop);
    }
    return indexProps;
  }

  @Override
  public int size() {
    return matcherProperties.size();
  }

  @Override
  public String toString() {
    return "OrderedIndexComponent: " + matcherProperties;
  }

  /**
   * A {@link Property} satisfies a constraint if it is equal to the constraint. However, if the
   * constraint does not specify a direction, then the direction of the actual property does not
   * matter.
   */
  private boolean propertySatisfies(Property constraint, Property property) {
    return constraint.equals(property)
        || (!constraint.hasDirection() && constraint.getName().equals(property.getName()));
  }
}
