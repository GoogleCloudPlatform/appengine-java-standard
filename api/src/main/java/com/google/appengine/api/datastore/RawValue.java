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
import static java.util.Objects.requireNonNull;

import com.google.appengine.api.users.User;
import com.google.datastore.v1.Value;
import com.google.datastore.v1.client.DatastoreHelper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property.Meaning;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A raw datastore value.
 *
 * <p>These are returned by projection queries when a {@link PropertyProjection} does not specify a
 * type.
 *
 * @see Query#getProjections()
 */
public final class RawValue implements Serializable {
  private static final long serialVersionUID = 8176992854378814427L;
  private transient @Nullable PropertyValue valueV3 = null;
  private transient @Nullable Value valueV1 = null;

  RawValue(PropertyValue propertyValue) {
    this.valueV3 = checkNotNull(propertyValue);
  }

  RawValue(Value value) {
    this.valueV1 = checkNotNull(value);
  }

  /**
   * Returns an object of the exact type passed in.
   *
   * @param type the class object for the desired type
   * @return an object of type T or {@code null}
   * @throws IllegalArgumentException if the raw value cannot be converted into the given type
   */
  @SuppressWarnings("unchecked")
  public <T> @Nullable T asStrictType(Class<T> type) {
    Object value = asType(type);
    if (value != null) {
      checkArgument(type.isAssignableFrom(value.getClass()), "Unsupported type: %s", type);
    }
    return type.cast(value);
  }

  /**
   * Returns the object normally returned by the datastore if given type is passed in.
   *
   * <p>All integer values are returned as {@link Long}. All floating point values are returned as
   * {@link Double}.
   *
   * @param type the class object for the desired type
   * @return an object of type T or {@code null}
   * @throws IllegalArgumentException if the raw value cannot be converted into the given type
   */
  public @Nullable Object asType(Class<?> type) {
    DataTypeTranslator.Type<?> typeAdapter = DataTypeTranslator.getTypeMap().get(type);
    checkArgument(typeAdapter != null, "Unsupported type: %s", type);

    if (valueV3 != null) {
      if (typeAdapter.hasValue(valueV3)) {
        return typeAdapter.getValue(valueV3);
      }
    } else if (valueV1 != null) {
      if (typeAdapter.hasValue(valueV1)) {
        return typeAdapter.getValue(valueV1);
      }
    }

    checkArgument(getValue() == null, "Type mismatch.");
    return null;
  }

  /**
   * Returns the raw value.
   *
   * @return An object of type {@link Boolean}, {@link Double}, {@link GeoPt}, {@link Key}, {@code
   *     byte[]}, {@link User} or {@code null}.
   */
  public @Nullable Object getValue() {
    if (valueV3 != null) {
      if (valueV3.hasBooleanValue()) {
        return valueV3.getBooleanValue();
      } else if (valueV3.hasDoubleValue()) {
        return valueV3.getDoubleValue();
      } else if (valueV3.hasInt64Value()) {
        return valueV3.getInt64Value();
      } else if (valueV3.hasPointValue()) {
        return asType(GeoPt.class);
      } else if (valueV3.hasReferenceValue()) {
        return asType(Key.class);
      } else if (valueV3.hasStringValue()) {
        return valueV3.getStringValueBytes();
      } else if (valueV3.hasUserValue()) {
        return asType(User.class);
      }
    } else if (valueV1 != null) {
      switch (valueV1.getValueTypeCase()) {
        case BOOLEAN_VALUE:
          return valueV1.getBooleanValue();
        case DOUBLE_VALUE:
          return valueV1.getDoubleValue();
        case INTEGER_VALUE:
          return valueV1.getIntegerValue();
        case ENTITY_VALUE:
          if (valueV1.getMeaning() == 20) {
            return asType(User.class);
          }
          throw new IllegalStateException("Raw entity value is not supported.");
        case KEY_VALUE:
          return asType(Key.class);
        case STRING_VALUE:
          return valueV1.getStringValueBytes().toByteArray();
        case BLOB_VALUE:
          // TODO: return a short blob? (not currently possible to get here).
          return valueV1.getBlobValue().toByteArray();
        case TIMESTAMP_VALUE:
          // TODO: return a Date? (not currently possible to get here).
          return DatastoreHelper.getTimestamp(valueV1);
        case GEO_POINT_VALUE:
          if (valueV1.getMeaning() == 0 || valueV1.getMeaning() == Meaning.INDEX_VALUE.getNumber()) {
            return asType(GeoPt.class);
          }
          break; // GeoPt with meaning becomes null.
        case ARRAY_VALUE:
          throw new IllegalStateException("Raw array value is not supported.");
        case NULL_VALUE:
        default:
          return null;
      }
    }
    return null;
  }

  // Serialization functions
  private void writeObject(ObjectOutputStream out) throws IOException {
    if (valueV3 != null) {
      out.write(1); // Writing version
      valueV3.writeTo(out);
    } else {
      out.write(2); // Writing version
      requireNonNull(valueV1).writeTo(out);
    }
    out.defaultWriteObject();
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.read();
    if (version == 1) {
      valueV3 = PropertyValue.newBuilder().build();
      boolean parsed = true;
      try{
        valueV3.getParserForType().parseFrom(in);
      } catch (InvalidProtocolBufferException e) {
        parsed = false;
      }
      if (!parsed || !valueV3.isInitialized()) {
        throw new InvalidProtocolBufferException("Could not parse PropertyValue");
      }
    } else if (version == 2) {
      valueV1 = Value.parseFrom(in);
    } else {
      throw new IllegalArgumentException("unknown RawValue format");
    }
    in.defaultReadObject();
  }

  @Override
  public int hashCode() {
    Object value = getValue();
    return value == null ? 0 : value.hashCode();
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
    RawValue other = (RawValue) obj;
    Object value = getValue();
    Object otherValue = other.getValue();
    if (value != null) {
      if (otherValue != null) {
        if (value instanceof byte[]) {
          if (otherValue instanceof byte[]) {
            return Arrays.equals((byte[]) value, (byte[]) otherValue);
          }
        } else {
          return value.equals(otherValue);
        }
      }
    } else if (otherValue == null) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "RawValue [value=" + getValue() + "]";
  }
}
