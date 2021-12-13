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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A property value containing embedded entity properties (and optionally a {@link Key}).
 *
 * <p>This class is similar to {@link Entity}, but differs in the following ways:
 *
 * <ul>
 *   <li>{@link #equals(Object)} and {@link #hashCode()} compare the embedded properties in addition
 *       to the {@link Key}.
 *   <li>It is not queryable when stored in the datastore.
 *   <li>A {@link Key} is optional.
 *   <li>{@link Key Keys} without a name or id are considered equal if all other aspects of the keys
 *       are equal (as they will not be assigned IDs by the datastore when embedded).
 * </ul>
 *
 * To convert from an {@link Entity} use:
 *
 * <pre>{@code
 * EmbeddedEntity sv = new EmbeddedEntity();
 * sv.setKey(entity.getKey())
 * sv.setPropertiesFrom(entity)
 * }</pre>
 *
 * To convert to an {@link Entity} use:
 *
 * <pre>{@code
 * Entity entity = new Entity(sv.getKey())
 * entity.setPropertiesFrom(sv);
 * }</pre>
 *
 */
public final class EmbeddedEntity extends PropertyContainer {
  private static final long serialVersionUID = -3204136387360930156L;

  private @Nullable Key key = null;
  private final Map<String, @Nullable Object> propertyMap = new HashMap<>();

  /** Returns the key or {@code null}. */
  public @Nullable Key getKey() {
    return key;
  }

  /** @param key the key to set */
  public void setKey(@Nullable Key key) {
    this.key = key;
  }

  @Override
  Map<String, @Nullable Object> getPropertyMap() {
    return propertyMap;
  }

  @Override
  public EmbeddedEntity clone() {
    EmbeddedEntity result = new EmbeddedEntity();
    result.setKey(key);
    result.setPropertiesFrom(this);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("<EmbeddedEntity");
    if (key != null) {
      builder.append(" [").append(key).append(']');
    }
    builder.append(":\n");
    appendPropertiesTo(builder);
    builder.append(">\n");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, propertyMap);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof EmbeddedEntity)) {
      return false;
    }
    EmbeddedEntity other = (EmbeddedEntity) obj;
    if (!propertyMap.equals(other.propertyMap)) {
      return false;
    }
    return key == null ? other.key == null : key.equals(other.key, false);
  }
}
