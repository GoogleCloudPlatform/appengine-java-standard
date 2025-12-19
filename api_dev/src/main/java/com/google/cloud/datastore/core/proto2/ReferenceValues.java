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
// LINT.IfChange

package com.google.cloud.datastore.core.proto2;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Reference;

/** Helper class for dealing with key values in V3. */
public class ReferenceValues {

  private ReferenceValues() {}

  /**
   * Converts a {@link PropertyValue.ReferenceValue} to a {@link Reference}.
   *
   * <p>Doesn't do any validation!
   *
   * @return the corresponding {@link Reference}
   */
  public static Reference toReference(PropertyValue.ReferenceValue ref) {
    Reference.Builder key = Reference.newBuilder();

    if (ref.hasApp()) {
      key.setAppBytes(ref.getAppBytes());
    }

    if (ref.hasDatabaseId()) {
      key.setDatabaseIdBytes(ref.getDatabaseIdBytes());
    }

    if (ref.hasNameSpace()) {
      key.setNameSpaceBytes(ref.getNameSpaceBytes());
    }

    for (PropertyValue.ReferenceValue.PathElement refElem : ref.getPathElementList()) {
      Path.Element.Builder keyElem = key.getPathBuilder().addElementBuilder();
      if (refElem.hasType()) {
        keyElem.setTypeBytes(refElem.getTypeBytes());
      }
      if (refElem.hasId()) {
        keyElem.setId(refElem.getId());
      }
      if (refElem.hasName()) {
        keyElem.setNameBytes(refElem.getNameBytes());
      }
    }

    return key.build();
  }

  /**
   * Converts a {@link PropertyValue} with a {@link PropertyValue.ReferenceValue} to a {@link
   * Reference}.
   *
   * <p>Doesn't do any validation!
   *
   * @return the corresponding {@link Reference}
   * @throws IllegalArgumentException if value doesn't have a {@link PropertyValue.ReferenceValue}.
   */
  public static Reference toReference(PropertyValue value) {
    checkArgument(value.hasReferenceValue());
    return toReference(value.getReferenceValue());
  }

  /**
   * Converts a {@link Reference} to a {@link PropertyValue}
   *
   * <p>with the same {@link PropertyValue.ReferenceValue}.
   *
   * <p>Doesn't do any validation!
   *
   * @return the corresponding {@link PropertyValue}
   */
  public static PropertyValue toReferenceProperty(Reference key) {
    PropertyValue.Builder prop = PropertyValue.newBuilder();
    PropertyValue.ReferenceValue.Builder ref = prop.getReferenceValueBuilder();

    if (key.hasApp()) {
      ref.setAppBytes(key.getAppBytes());
    }

    if (key.hasDatabaseId()) {
      ref.setDatabaseIdBytes(key.getDatabaseIdBytes());
    }

    if (key.hasNameSpace()) {
      ref.setNameSpaceBytes(key.getNameSpaceBytes());
    }

    for (Path.Element keyElem : key.getPath().getElementList()) {
      PropertyValue.ReferenceValue.PathElement.Builder refElem = ref.addPathElementBuilder();
      if (keyElem.hasType()) {
        refElem.setTypeBytes(keyElem.getTypeBytes());
      }
      if (keyElem.hasId()) {
        refElem.setId(keyElem.getId());
      }
      if (keyElem.hasName()) {
        refElem.setNameBytes(keyElem.getNameBytes());
      }
    }

    return prop.build();
  }
}
// LINT.ThenChange(//depot/google3/java/com/google/cloud/datastore/core/appengv3/ReferenceValues.java)
