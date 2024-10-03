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

  NULL(new PropertyValue(),
       new PropertyValue()),

  INT64(new PropertyValue().setInt64Value(Long.MIN_VALUE),
        new PropertyValue().setInt64Value(0L)),

  BOOLEAN(new PropertyValue().setBooleanValue(false),
          new PropertyValue().setBooleanValue(false)),

  STRING(new PropertyValue().setStringValue(""),
         new PropertyValue().setStringValue("none")),

  DOUBLE(new PropertyValue().setDoubleValue(Double.NEGATIVE_INFINITY),
         new PropertyValue().setDoubleValue(0.0)),

  POINT(new PropertyValue().setPointValue(
            new PropertyValue.PointValue().setX(Double.NEGATIVE_INFINITY)
                                          .setY(Double.NEGATIVE_INFINITY)),
        new PropertyValue().setPointValue(
            new PropertyValue.PointValue().setX(0.0).setY(0.0))),

  USER(new PropertyValue().setUserValue(
           new PropertyValue.UserValue().setEmail("")
                                        .setAuthDomain("")
                                        .setGaiaid(Long.MIN_VALUE)),
       new PropertyValue().setUserValue(
           new PropertyValue.UserValue().setEmail("none")
                                        .setAuthDomain("none")
                                        .setGaiaid(0))),

  // These are filled in in the static initializer block.
  REFERENCE(new PropertyValue().setReferenceValue(new PropertyValue.ReferenceValue()),
            new PropertyValue().setReferenceValue(new PropertyValue.ReferenceValue()));

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
    REFERENCE.minValue.getMutableReferenceValue().setApp("");
    REFERENCE.placeholderValue.getMutableReferenceValue()
        .setApp("none")
        .addPathElement().setType("none").setName("none");
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
   * @return the tag numbers of the top-level field that are set.
   */
  private static List<Integer> findTags(PropertyValue value) {
    List<Integer> tags = Lists.newArrayList();

    for (Type.FieldType field : Type.getTags(value)) {
      if (field.size(value) == 1) {
        tags.add(field.getTag());
      }
    }

    return tags;
  }
}
