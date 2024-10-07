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
import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Direction;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedSet;

/**
 * Implements an {@link IndexComponent} that is unordered.
 *
 * <p>This is currently used for exists and group by filters.
 *
 */
class UnorderedIndexComponent implements IndexComponent {

  private final SortedSet<String> matcherProperties;

  public UnorderedIndexComponent(Set<String> unorderedGroup) {
    this.matcherProperties = Sets.newTreeSet(unorderedGroup);
  }

  @Override
  public boolean matches(List<Property> indexProperties) {
    Set<String> unorderedProps = Sets.newHashSet(matcherProperties);
    // Go through the index in reverse because we don't know where the prefix/postfix split is.
    ListIterator<Property> indexItr = indexProperties.listIterator(indexProperties.size());
    while (!unorderedProps.isEmpty() && indexItr.hasPrevious()) {
      // We don't allow duplicates, so remove seen properties from our set.
      if (!unorderedProps.remove(indexItr.previous().getName())) {
        return false;
      }
    }
    return unorderedProps.isEmpty();
  }

  @Override
  public List<Property> preferredIndexProperties() {
    List<Property> indexProps = Lists.newArrayListWithExpectedSize(matcherProperties.size());
    for (String name : matcherProperties) {
      Property.Builder indexProperty = Property.newBuilder();
      indexProperty.setName(name);
      indexProperty.setDirection(Direction.ASCENDING);
      indexProps.add(indexProperty.build());
    }
    return indexProps;
  }

  @Override
  public int size() {
    return matcherProperties.size();
  }

  @Override
  public String toString() {
    return "UnorderedIndexComponent: " + matcherProperties;
  }
}
