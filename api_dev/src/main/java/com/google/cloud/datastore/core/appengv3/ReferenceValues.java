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

package com.google.cloud.datastore.core.appengv3;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;

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
    Reference key = new Reference();

    if (ref.hasApp()) {
      key.setAppAsBytes(ref.getAppAsBytes());
    }

    if (ref.hasDatabaseId()) {
      key.setDatabaseIdAsBytes(ref.getDatabaseIdAsBytes());
    }

    if (ref.hasNameSpace()) {
      key.setNameSpaceAsBytes(ref.getNameSpaceAsBytes());
    }

    for (PropertyValue.ReferenceValuePathElement refElem : ref.pathElements()) {
      Path.Element keyElem = key.getMutablePath().addElement();
      if (refElem.hasType()) {
        keyElem.setTypeAsBytes(refElem.getTypeAsBytes());
      }
      if (refElem.hasId()) {
        keyElem.setId(refElem.getId());
      }
      if (refElem.hasName()) {
        keyElem.setNameAsBytes(refElem.getNameAsBytes());
      }
    }

    return key;
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
    PropertyValue prop = new PropertyValue();
    PropertyValue.ReferenceValue ref = prop.getMutableReferenceValue();

    if (key.hasApp()) {
      ref.setAppAsBytes(key.getAppAsBytes());
    }

    if (key.hasDatabaseId()) {
      ref.setDatabaseIdAsBytes(key.getDatabaseIdAsBytes());
    }

    if (key.hasNameSpace()) {
      ref.setNameSpaceAsBytes(key.getNameSpaceAsBytes());
    }

    for (Path.Element keyElem : key.getPath().elements()) {
      PropertyValue.ReferenceValuePathElement refElem = ref.addPathElement();
      if (keyElem.hasType()) {
        refElem.setTypeAsBytes(keyElem.getTypeAsBytes());
      }
      if (keyElem.hasId()) {
        refElem.setId(keyElem.getId());
      }
      if (keyElem.hasName()) {
        refElem.setNameAsBytes(keyElem.getNameAsBytes());
      }
    }

    return prop;
  }
}
