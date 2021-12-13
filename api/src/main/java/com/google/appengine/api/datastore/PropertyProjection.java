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
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A property projection.
 *
 * <p>If specified on a query, this will cause the query return the specified property.
 *
 * @see Query#getProjections()
 */
public final class PropertyProjection extends Projection {
  private static final long serialVersionUID = 8890147735615203656L;

  private final String propertyName;
  private final @Nullable Class<?> type;

  /**
   * Constructs a property projection.
   *
   * <p>If type is specified, {@link RawValue#asType(Class)} will be used to restore the original
   * value of the property. Otherwise instances of {@link RawValue} will be returned.
   *
   * @param propertyName The name of the property to project
   * @param type The type of values stored in the projected properties or {@code null} if the type
   *     is not known or variable. If {@code null}, {@link RawValue RawValues} are returned.
   */
  public PropertyProjection(String propertyName, @Nullable Class<?> type) {
    checkArgument(
        type == null || DataTypeTranslator.getTypeMap().containsKey(type),
        "Unsupported type: %s",
        type);
    this.propertyName = checkNotNull(propertyName);
    this.type = type;
  }

  @Override
  public String getName() {
    return propertyName;
  }

  /** Returns the type specified for this projection. */
  public @Nullable Class<?> getType() {
    return type;
  }

  @Override
  String getPropertyName() {
    return propertyName;
  }

  @Override
  @Nullable
  Object getValue(Map<String, Object> values) {
    checkArgument(values.containsKey(propertyName));
    Object value = values.get(propertyName);
    if (type != null && value != null) {
      checkArgument(value instanceof RawValue);
      value = ((RawValue) value).asType(type);
    }
    return value;
  }

  @Override
  public String toString() {
    return propertyName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(propertyName, type);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    PropertyProjection other = (PropertyProjection) obj;
    if (!propertyName.equals(other.propertyName)) {
      return false;
    }
    if (type == null) {
      if (other.type != null) {
        return false;
      }
    } else if (!type.equals(other.type)) {
      return false;
    }
    return true;
  }
}
