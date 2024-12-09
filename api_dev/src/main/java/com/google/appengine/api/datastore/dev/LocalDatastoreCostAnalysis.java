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

package com.google.appengine.api.datastore.dev;

import static com.google.appengine.api.datastore.dev.LocalDatastoreService.equalProperties;

import com.google.appengine.api.datastore.Entity;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Cost;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Index.Property.Direction;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Utility class that can calculate the cost of writing (put or delete) a given {@link Entity}.
 *
 */
public class LocalDatastoreCostAnalysis {

  private static final BigDecimal ONE_MILLION = new BigDecimal(1000000);

  // $1 per million writes
  private static final BigDecimal DOLLARS_PER_WRITE = BigDecimal.ONE.divide(ONE_MILLION);

  // divide by 100 to get to pennies per write
  private static final BigDecimal PENNIES_PER_WRITE = DOLLARS_PER_WRITE.divide(new BigDecimal(100));

  private final LocalCompositeIndexManager indexManager;

  public LocalDatastoreCostAnalysis(LocalCompositeIndexManager indexManager) {
    this.indexManager = indexManager;
  }

  static BigDecimal writesToPennies(int writes) {
    return PENNIES_PER_WRITE.multiply(new BigDecimal(writes));
  }

  /**
   * Determines the cost of writing this entity, assuming no previous value exists.
   *
   * @param newEntity The entity whose write cost we are determining.
   * @return The cost of writing the given entity.
   */
  public Cost getWriteCost(EntityProto newEntity) {
    return getWriteOps(null, newEntity);
  }

  /**
   * Determines the cost of writing {@code newEntity}, assuming its current state in the datastore
   * matches {@code oldEntity}.
   *
   * @param oldEntity Entity representing the current state in the datastore. Can be {@code null}.
   * @param newEntity Entity representing the desired state in the datastore.
   * @return The cost of writing {@code newEntity}.
   */
  public Cost getWriteOps(@Nullable EntityProto oldEntity, EntityProto newEntity) {
    Cost.Builder cost = Cost.newBuilder().setEntityWrites(0).setIndexWrites(0);

    // check for a no-op write, only possible if an entity already existed
    if (equalProperties(oldEntity, newEntity)) {
      return cost.build();
    }
    // Not a no-op write, so one write for the entity.
    cost.setEntityWrites(1);

    int indexWrites = changedIndexRows(oldEntity, newEntity);
    if (oldEntity == null) {
      // 1 additional index write for the EntitiesByKind index, which is only written to when we're
      // writing an entity for the first time (kind is immutable).
      indexWrites++;
    }
    return cost.setIndexWrites(indexWrites).build();
  }

  /**
   * Determine the number of index rows that need to change when writing {@code newEntity}, assuming
   * {@code oldEntity} represents the current state of the Datastore.
   *
   * @param oldEntity Entity representing the current state in the datastore.
   * @param newEntity Entity representing the desired state in the datastore.
   * @return The number of index rows that need to change.
   */
  private int changedIndexRows(EntityProto oldEntity, EntityProto newEntity) {
    // All properties that are unique within the old entity, not all properties that are unique
    // to the old entity. Declaring the specific subtype because we rely on the fact that
    // HashMultimap enforces uniqueness of key-value pairs.
    SetMultimap<String, Property> uniqueOldProperties = HashMultimap.create();
    if (oldEntity != null) {
      for (Property oldProp : oldEntity.getPropertyList()) {
        // A given name may only have one property value on the old entity but multiple values on
        // the new entity. If that's the case, two Properties that are equal will be considered not
        // equal due to different values of the "multiple" attribute. We want these Properties to be
        // considered equal so we hard-code "multiple" to be false in the map.
        oldProp = oldProp.hasMultiple() ? oldProp.toBuilder().clone().setMultiple(false).build() : oldProp;
        uniqueOldProperties.put(oldProp.getName(), oldProp);
      }
    }
    // All properties that are unique within the new entity, not all properties that are unique
    // to the new entity. Declaring the specific subtype because we rely on the fact that
    // HashMultimap enforces uniqueness of key-value pairs.
    SetMultimap<String, Property> uniqueNewProperties = HashMultimap.create();

    // Number of properties per name that have not changed between old and new.
    Multiset<String> unchanged = HashMultiset.create();
    for (Property newProp : newEntity.getPropertyList()) {
      // See the comment in the loop where we populate uniqueOldProperties for an explanation of why
      // we do this.
      newProp = newProp.hasMultiple() ? newProp.toBuilder().clone().setMultiple(false).build() : newProp;
      uniqueNewProperties.put(newProp.getName(), newProp);
      if (uniqueOldProperties.containsEntry(newProp.getName(), newProp)) {
        unchanged.add(newProp.getName());
      }
    }
    // We're going to build Index objects that correspond to the single-property, built-in indexes
    // that the Datastore maintains. In order to do this we need a unique list of all the property
    // names on both the old and new entities.
    Set<String> allPropertyNames =
        Sets.newHashSet(
            Iterables.concat(uniqueOldProperties.keySet(), uniqueNewProperties.keySet()));
    Iterable<Index> allIndexes =
        Iterables.concat(
            indexManager.getIndexesForKind(Utils.getKind(newEntity.getKey())),
            getEntityByPropertyIndexes(allPropertyNames));
    Multiset<String> uniqueOldPropertyNames = uniqueOldProperties.keys();
    Multiset<String> uniqueNewPropertyNames = uniqueNewProperties.keys();
    int pathSize = newEntity.getKey().getPath().getElementCount();
    int writes = 0;
    for (Index index : allIndexes) {
      // Take ancestor indexes into account.
      // Ancestor doesn't matter for EntityByProperty indexes, and these are the only indexes that
      // have a single property.
      int ancestorMultiplier = index.hasAncestor() && index.getPropertyCount() > 1 ? pathSize : 1;
      writes +=
          (calculateWritesForCompositeIndex(
                  index, uniqueOldPropertyNames, uniqueNewPropertyNames, unchanged)
              * ancestorMultiplier);
    }
    return writes;
  }

  /**
   * Calculate the number of writes required to maintain a specific {@link Index}.
   *
   * @param index The index to be maintained.
   * @param uniqueOldProperties {@link Multiset} containing the names and counts of unique Property
   *     objects on the old entity (as opposed to Property objects that are unique to the old entity
   *     as compared to the new entity).
   * @param uniqueNewProperties {@link Multiset} containing the names and counts of unique Property
   *     objects on the new entity (as opposed to Property objects that are unique to the new entity
   *     as compared to the old entity).
   * @param commonProperties {@link Multiset} containing the names and counts of Property objects
   *     that are present on both the old and new entities.
   * @return The number of writes required to maintain the given {@link Index}.
   */
  private int calculateWritesForCompositeIndex(
      Index index,
      Multiset<String> uniqueOldProperties,
      Multiset<String> uniqueNewProperties,
      Multiset<String> commonProperties) {
    int oldCount = 1;
    int newCount = 1;
    int commonCount = 1;
    for (Index.Property prop : index.getPropertyList()) {
      oldCount *= uniqueOldProperties.count(prop.getName());
      newCount *= uniqueNewProperties.count(prop.getName());
      commonCount *= commonProperties.count(prop.getName());
    }
    return (oldCount - commonCount) + (newCount - commonCount);
  }

  /**
   * Given a set of property names, generates a {@code List} of {@link Index} objects that can be
   * used to generate index rows that would be appear in EntitiesByProperty and
   * EntitiesByPropertyDesc
   *
   * @param propertyNames The property names.
   * @return The indexes.
   */
  static List<Index> getEntityByPropertyIndexes(Set<String> propertyNames) {
    List<String> sortedPropertyNames = Ordering.natural().sortedCopy(propertyNames);
    List<Index> indexes = Lists.newArrayList();
    for (String propName : sortedPropertyNames) {
      // EnititiesByProperty
      Index.Builder index = Index.newBuilder();
      index.addProperty(Index.Property.newBuilder().setName(propName).setDirection(Direction.ASCENDING));
      indexes.add(index.buildPartial());
      // EnititiesByPropertyDesc
      index = Index.newBuilder();
      index.addProperty(Index.Property.newBuilder().setName(propName).setDirection(Direction.DESCENDING));
      indexes.add(index.buildPartial());
    }
    return indexes;
  }
}
