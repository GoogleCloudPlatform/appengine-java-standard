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
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Direction;
import java.util.List;

/**
 * Base class for {@link IndexComponent} tests.
 *
 */
public class IndexComponentTestCase {

  protected List<Property> newIndex(Object... properties) {
    List<Property> index = Lists.newArrayListWithExpectedSize(properties.length);
    for (Object prop : properties) {
      if (prop instanceof Property) {
        index.add((Property) prop);
      } else if (prop instanceof String) {
        index.add(newProperty((String) prop));
      }
    }
    return index;
  }

  protected Property newProperty(String propertyName) {
    return newProperty(propertyName, Direction.ASCENDING);
  }

  protected Property newProperty(String propertyName, Direction direction) {
    return Property.newBuilder().setName(propertyName).setDirection(direction).build();
  }
}
