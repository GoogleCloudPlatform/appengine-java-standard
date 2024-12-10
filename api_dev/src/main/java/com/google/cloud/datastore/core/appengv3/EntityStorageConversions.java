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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property.Meaning.ENTITY_PROTO;

import com.google.cloud.datastore.core.names.Kinds;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property.Meaning;
import java.util.Iterator;

/** Business logic for preparing entities for storage. */
public final class EntityStorageConversions {

  private EntityStorageConversions() {
    // Only contains static methods.
  }

  /** Handles any moves / creations related to indexes. */
  public static void preprocessIndexes(EntityProto diskEntity) {
    // This call may add EMPTY_LIST meanings to properties.  In order to keep code
    // somewhat orthogonal, we remove any EMPTY_LISTs with computed set from properties
    // below. (We never want to index EMPTY_LIST)
    createComputedAndStashedValuesForEntityValues(diskEntity.toBuilder());
    // Must happen after createComputedAndStashedValuesForEntityValues has already flattened
    // indexed properties
    markEmptyListsForStashing(diskEntity);
    // Must happen after all computed properties are generated and all stashed properties have been
    // marked for stashing.
    stashProperties(diskEntity.toBuilder());
  }

  /**
   * Same as {@link #preprocessIndexes(EntityProto)} except empty list are not stashed, this is the
   * current behavior of the emulators.
   *
   * <p>NOTE: there is no clear reason why the emulators are not stashing empty lists, this may be a
   * bug.
   */
  public static void preprocessIndexesWithoutEmptyListSupport(EntityProto.Builder diskEntity) {
    // This call may add EMPTY_LIST meanings to properties.  In order to keep code
    // somewhat orthogonal, we remove any EMPTY_LISTs with computed set from properties
    // below. (We never want to index EMPTY_LIST)
    createComputedAndStashedValuesForEntityValues(diskEntity);
    // Must happen after all computed properties are generated and all stashed properties have been
    // marked for stashing.
    stashProperties(diskEntity);
  }

  /**
   * Reverts most of the changes done by preprocessIndexes. The ordering of values of repeated
   * properties is stable, but the ordering of properties within the entity is not. In other words,
   * the resulting EntityProto will not be an exact copy of the original, but will be functionally
   * equivalent.
   *
   * @param storageEntity The EntityProto to modify.
   */
  public static void postprocessIndexes(EntityProto.Builder storageEntity) {
    checkNotNull(storageEntity);

    // Computed properties have to be removed before stashed properties are restored.
    removeComputedProperties(storageEntity.buildPartial());
    restoreStashedProperties(storageEntity);
  }

  /**
   * Removes all properties with the computed flag.
   *
   * @param storageEntity The EntityProto to modify.
   */
  private static void removeComputedProperties(EntityProto storageEntity) {
    for (Iterator<Property> propertyIterator = storageEntity.getPropertyList().iterator();
        propertyIterator.hasNext(); ) {
      if (propertyIterator.next().hasComputed()) {
        propertyIterator.remove();
      }
    }
  }

  /**
   * Moves stashed properties back from raw properties to properties.
   *
   * <p>Note: this piece of code assumes that stashed properties appear in the raw property list in
   * stashed index order.
   *
   * @param storageEntity The EntityProto to modify.
   */
  static void restoreStashedProperties(EntityProto.Builder storageEntity) {
    ImmutableList<Property> properties = ImmutableList.copyOf(storageEntity.getPropertyList());
    ImmutableList<Property> rawProperties = ImmutableList.copyOf(storageEntity.getRawPropertyList());
    ImmutableList.Builder<Property> badlyStashedProperties = ImmutableList.builder();
    int propertyListIndex = 0;

    storageEntity.clearProperty();
    storageEntity.clearRawProperty();

    for (Property rawProperty : rawProperties) {
      if (rawProperty.hasStashed()) {
        int stashed = rawProperty.getStashed();
        int advance = stashed - storageEntity.getPropertyCount();

        if (stashed < storageEntity.getPropertyCount() // Not at the end (out-of-order, duplicate)
            || propertyListIndex + advance > properties.size()) { // Past the end
          // The value of stashed is bad.
          badlyStashedProperties.add(rawProperty.toBuilder().clearStashed().build());
        } else {
          // Copy until before the position where the stashed property needs to be restored.
          if (advance > 0) {
            storageEntity
                .getPropertyList()
                .addAll(properties.subList(propertyListIndex, propertyListIndex + advance));
            propertyListIndex = propertyListIndex + advance;
          }
          storageEntity.addProperty(rawProperty.toBuilder().clearStashed().build());
        }
      } else {
        storageEntity.addRawProperty(rawProperty);
      }
    }

    storageEntity.addAllProperty(properties.subList(propertyListIndex, properties.size()));
    storageEntity.addAllProperty(badlyStashedProperties.build());
  }

  /**
   * We need to prevent EMPTY_LIST from being indexed. EMPTY_LIST can appear because EntityValue
   * code flattened an EntityValue and created one (marked as computed), or a user sent us an
   * indexed EMPTY_LIST.
   *
   * <p>If the user sent us EMPTY_LIST, we want to move the property to rawProperties with stashed
   * set so that it won't be indexed, and upon read will be moved back to properties.
   *
   * <p>If EntityValue created the entry, it will be removed in the stashing step (as all computed
   * properties marked for stashing are removed).
   */
  private static void markEmptyListsForStashing(EntityProto diskEntity) {
    for (int i = 0; i < diskEntity.getPropertyCount(); i++) {
      Property.Builder property = diskEntity.getProperty(i).toBuilder();
      if (property.getMeaning() == Meaning.EMPTY_LIST) {
        property.setStashed(i); // Mark for stashing.
      }
    }
  }

  /**
   * Finds all properties marked for stashing: if they are computed, removes them, moves them to raw
   * properties.
   *
   * @param diskEntity The EntityProto to modify.
   */
  private static void stashProperties(EntityProto.Builder diskEntity) {
    ImmutableList<Property> properties = ImmutableList.copyOf(diskEntity.getPropertyList());
    diskEntity.clearProperty();

    for (Property property : properties) {
      if (property.hasStashed()) {
        if (!property.hasComputed()) {
          diskEntity.addRawProperty(property);
        }
      } else {
        diskEntity.addProperty(property);
      }
    }
  }

  /**
   * Modifies an entity by marking all indexed entity values for stashing, and flattening all leaf
   * sub-properties into top-level properties. Does not perform any kind of validation.
   *
   * @param diskEntity The EntityProto to modify.
   */
  private static void createComputedAndStashedValuesForEntityValues(EntityProto.Builder diskEntity) {
    ImmutableList.Builder<Property> computedProperties = ImmutableList.builder();

    for (int i = 0; i < diskEntity.getPropertyCount(); i++) {
      Property.Builder property = diskEntity.getProperty(i).toBuilder();
      if (isEntityValue(property.build())) {
        property.setStashed(i); // Mark for stashing.
        flattenIndexedEntityValues(
            property.getName(),
            property.hasMultiple(),
            property.getValue().getStringValueBytes().toByteArray(),
            computedProperties);
      }
    }

    diskEntity.addAllProperty(computedProperties.build());
  }

  /**
   * Deserializes an embedded EntityProto.
   *
   * @param value The serialized form of the EntityProto.
   * @param propertyName The property name, for debugging purposes.
   * @return The corresponding entity proto.
   * @throws IllegalArgumentException If the deserialization fails.
   */
  private static EntityProto deserializeEmbeddedEntityProto(byte[] value, String propertyName) {
    try {
      // We use parsePartialFrom() because serialized entity values can have required fields unset.
      return EntityProto.parser().parsePartialFrom(value);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("Unable to parse " + propertyName, e);
    }
  }

  /**
   * Recursively walk the given entity value and flattens all leaf sub-properties into
   * flattenedProperties. In the top level call pathPrefix and multiplePath should be respectively
   * the name and the multiple bit of the property enclosing the entity value. This method is
   * designed for internal use, do not make it public.
   *
   * @param pathPrefix Prefix to be added to property names for the current recursion level.
   * @param multiplePath Whether a multiple property has been encountered in the path.
   * @param currentSerializedEntityValue The serialized entity value to inspect recursively.
   * @param flattenedProperties The list where flattened sub-properties are to be added.
   */
  private static void flattenIndexedEntityValues(
      String pathPrefix,
      boolean multiplePath,
      byte[] currentSerializedEntityValue,
      ImmutableList.Builder<Property> flattenedProperties) {
    checkArgument(!Strings.isNullOrEmpty(pathPrefix));

    EntityProto currentEntityValue =
        deserializeEmbeddedEntityProto(currentSerializedEntityValue, pathPrefix);

    if (currentEntityValue.getKey().getPath().getElementCount() > 0) {
      // Path is not empty
      flattenedProperties.add(
          Property.newBuilder()
              .setName(pathPrefix + Kinds.PROPERTY_PATH_DELIMITER_AND_KEY_SUFFIX)
              .setValue(ReferenceValues.toReferenceProperty(currentEntityValue.getKey()))
              .setMultiple(multiplePath)
              .setComputed(true)
              .build());
    }

    for (Property property : currentEntityValue.getPropertyList()) {
      if (isEntityValue(property)) {
        // This is a nested entity value.
        flattenIndexedEntityValues(
            pathPrefix + Kinds.PROPERTY_PATH_DELIMITER + property.getName(),
            multiplePath || property.hasMultiple(),
            property.getValue().getStringValueBytes().toByteArray(),
            flattenedProperties);
      } else {
        // This is a leaf sub-property.
        flattenedProperties.add(
            property
                .toBuilder()
                .setName(pathPrefix + Kinds.PROPERTY_PATH_DELIMITER + property.getName())
                .setMultiple(multiplePath || property.hasMultiple())
                .setComputed(true).
                build());
      }
    }
  }

  /**
   * Checks whether the given property has an entity value.
   *
   * @param property The Property to look at.
   * @return True if the meaning is ENTITY_PROTO, false otherwise.
   */
  static boolean isEntityValue(Property property) {
    return property.getMeaning().equals(ENTITY_PROTO);
  }
}
