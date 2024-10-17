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

package com.google.storage.onestore;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
// import com.google.io.protocol.ProtocolType;
import com.google.protobuf.Type;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue.PointValue;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An enum for all of the Onestore property types. Includes their tag numbers
 * and a couple PropertyValue representations, which are PropertyValue PBs
 * with the field for the given type set to minimum and human-readable
 * placeholder values.
 *
 * @see OnestoreEntity.PropertyValue
 *
 */
public enum PropertyType {

  NULL(PropertyValue.newBuilder().build(),
      PropertyValue.newBuilder().build()),

  INT64(PropertyValue.newBuilder().setInt64Value(Long.MIN_VALUE).build(),
      PropertyValue.newBuilder().setInt64Value(0L).build()),

  BOOLEAN(PropertyValue.newBuilder().setBooleanValue(false).build(),
      PropertyValue.newBuilder().setBooleanValue(false).build()),

  STRING(PropertyValue.newBuilder().setStringValue("").build(),
      PropertyValue.newBuilder().setStringValue("none").build()),

  DOUBLE(PropertyValue.newBuilder().setDoubleValue(Double.NEGATIVE_INFINITY).build(),
      PropertyValue.newBuilder().setDoubleValue(0.0).build()),

  POINT(PropertyValue.newBuilder().setPointValue(PointValue.newBuilder()
          .setX(Double.NEGATIVE_INFINITY)
          .setY(Double.NEGATIVE_INFINITY).build()).build(),
      PropertyValue.newBuilder().setPointValue(PointValue.newBuilder()
          .setX(0.0)
          .setY(0.0).build()).build()),

  USER(PropertyValue.newBuilder().setUserValue(
           PropertyValue.newBuilder().getUserValueBuilder().setEmail("")
                                        .setAuthDomain("")
                                        .setGaiaid(Long.MIN_VALUE)).build(),
       PropertyValue.newBuilder().setUserValue(
           PropertyValue.newBuilder().getUserValueBuilder().setEmail("none")
                                        .setAuthDomain("none")
                                        .setGaiaid(0)).build()),

  // These are filled in in the static initializer block.
  REFERENCE(PropertyValue.newBuilder().setReferenceValue(PropertyValue.newBuilder().getReferenceValue()).build(),
      PropertyValue.newBuilder().setReferenceValue(PropertyValue.newBuilder().getReferenceValue()).build());

  /**
   * Maps the tag numbers of top-level PropertyValue field to their
   * corresponding PropertyTypes.
   */
  private static SortedMap<Integer, PropertyType> types = new TreeMap<Integer, PropertyType>();

  static {
    type: for (PropertyType type : EnumSet.allOf(PropertyType.class)) {
      types.put(type.tag, type);
    }

    /* Fill in the reference property values. */
    REFERENCE.minValue.getReferenceValue().toBuilder().setApp("");
    REFERENCE.placeholderValue.getReferenceValue().toBuilder()
        .setApp("none")
        .addPathElementBuilder().setType("none").setName("none");
  }

  /**
   * Two {@code PropertyValue} representations of this property type, one for
   * the minimum possible value and one human-readable placeholder.
   *
   * Note that {@code MegastoreDatastoreImpl.getSchema()} depends on the fact
   * that minValue is the minimum possible value for this property type.
   */
  public final PropertyValue minValue;
  public final PropertyValue placeholderValue;

  /**
   * The tag number of the {@code PropertyValue} field for this property type.
   */
  public final int tag;


  /**
   * Constructor.
   *
   * @arg value the PropertyValue representation of this type
   */
  PropertyType(PropertyValue minValue, PropertyValue placeholderValue) {
    this.minValue = minValue;
    this.placeholderValue = placeholderValue;

    tag = findOnlyTag(minValue);
    checkArgument(tag == findOnlyTag(placeholderValue));
  }

  /**
   * @return the type of the given PropertyValue
   * @throws IllegalArgumentException if more than one type field is set
   */
  public static PropertyType getType(PropertyValue value) {
    return types.get(findOnlyTag(value));
  }

  /**
   * @return the next PropertyType in tag order, or null if this is the
   * highest.
   */
  public PropertyType next() {
    SortedMap<Integer, PropertyType> rest = types.tailMap(tag + 1);

    if (rest.isEmpty()) {
      return null;
    } else {
      return rest.get(rest.firstKey());
    }
  }


  /**
   * @return the tag number of the one top-level field that's set.  Returning
   *         -1 indicates the null value.
   * @throws IllegalArgumentException if more than one top-level field is set
   */
  private static int findOnlyTag(PropertyValue value) {
    List<Integer> tags = findTags(value);

    if (tags.isEmpty()) {
      return -1;
    } else {
      checkArgument(tags.size() == 1);
      return tags.get(0);
    }
  }

  /**
   * Finds the tags of the fields set in a PropertyValue.
   * This method only returns the tags for the top-level fields in the PropertyValue message as defined in appengine-java-standard/protobuf/api/entity.proto.
   * It does not include tags for fields nested within PointValue, UserValue, or ReferenceValue.
   * @return A list of the tags of the fields set in the PropertyValue.
   */
  private static List<Integer> findTags(PropertyValue value) {
    List<Integer> tags = Lists.newArrayList();

    if (value.hasInt64Value()) {
      tags.add(1);
    }
    if (value.hasBooleanValue()) {
      tags.add(2);
    }
    if(value.hasStringValue()) {
      tags.add(3);
    }
    if(value.hasDoubleValue()) {
      tags.add(4);
    }
    if(value.hasPointValue()) {
      tags.add(5);
    }
    // Missing tags 6 and 7 because they are fields within PointValue, not direct fields of PropertyValue.
    if(value.hasUserValue()) {
      tags.add(8);
    }
    // Missing tags 9, 10, 11, 18, 19, 21, and 22 because they are fields within UserValue.
    if(value.hasReferenceValue()) {
      tags.add(12);
    }
    // Missing tags 13, 14, 15, 16, 17, 20, and 23 because they are fields within ReferenceValue.

    return tags;
  }
}
