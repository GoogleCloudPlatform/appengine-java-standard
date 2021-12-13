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

package com.google.apphosting.utils.glob;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Glob} with explicit properties and no children.
 *
 */
class LeafGlob extends AbstractGlob {
  private final Map<String, Object> properties;

  LeafGlob(String pattern) {
    this(pattern, new HashMap<String, Object>());
  }

  LeafGlob(String pattern, Map<String, Object> properties) {
    super(pattern);
    this.properties = properties;
  }

  public void addProperty(String property, Object value) {
    properties.put(property, value);
  }

  @Override
  public Object getProperty(String property, ConflictResolver resolver) {
    return properties.get(property);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof LeafGlob) {
      LeafGlob glob = (LeafGlob) object;
      return super.equals(glob) && properties.equals(glob.properties);
    }
    return false;
  }

  @Override
  public String toString() {
    return pattern + properties;
  }
}
