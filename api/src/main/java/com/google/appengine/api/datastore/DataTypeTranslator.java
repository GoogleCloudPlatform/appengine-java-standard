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

import static java.util.Objects.requireNonNull;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.Entity.UnindexedValue;
import com.google.appengine.api.datastore.Entity.WrappedValue;
import com.google.appengine.api.datastore.Entity.WrappedValueImpl;
import com.google.appengine.api.users.User;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.datastore.v1.ArrayValue;
import com.google.datastore.v1.EntityOrBuilder;
import com.google.datastore.v1.Key.PathElement;
import com.google.datastore.v1.Key.PathElement.IdTypeCase;
import com.google.datastore.v1.KeyOrBuilder;
import com.google.datastore.v1.PartitionId;
import com.google.datastore.v1.PartitionIdOrBuilder;
import com.google.datastore.v1.Value;
import com.google.datastore.v1.Value.ValueTypeCase;
import com.google.datastore.v1.ValueOrBuilder;
import com.google.datastore.v1.client.DatastoreHelper;
// import com.google.io.protocol.ProtocolSupport;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.NullValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Property.Meaning;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue.PointValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue.ReferenceValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.PropertyValue.UserValue;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Reference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code DataTypeTranslator} is a utility class for converting between the data store's {@code
 * Property} protocol buffers and the user-facing classes ({@code String}, {@code User}, etc.).
 *
 */
public final class DataTypeTranslator {
  /**
   * This key points to an (optional) map from project id to app id in the {@link
   * com.google.apphosting.api.ApiProxy.Environment} attribute map.
   */
  static final String ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY =
      "com.google.appengine.datastore.DataTypeTranslator.AdditionalAppIdsMap";

  // TODO Like {@link EntityTranslator}, we'd like to make this
  // class package protected, but it must be public for outside use.
  // At the minimum we need to make sure this class is excluded from
  // javadoc.

  private static final Int64Type INT_64_VALUE_TYPE = new Int64Type();
  private static final DoubleType DOUBLE_VALUE_TYPE = new DoubleType();
  private static final StringType STRING_VALUE_TYPE = new StringType();
  private static final BoolType BOOL_VALUE_TYPE = new BoolType();
  private static final KeyType KEY_VALUE_TYPE = new KeyType();
  private static final UserType USER_VALUE_TYPE = new UserType();
  private static final RawValueType RAW_VALUE_TYPE = new RawValueType();

  /**
   * The list of supported types.
   *
   * <p>Note: If you're going to modify this list, also update DataTypeUtils. We're not building
   * {@link DataTypeUtils#getSupportedTypes} directly from this TYPE_MAP, because we want {@link
   * DataTypeUtils} to be translatable by GWT, so that {@link Entity Entities} can be easily sent
   * via GWT RPC. Also, if you add a type here that is not immutable you'll need to add special
   * handling for it in {@link Entity#clone()}.
   */
  /* @VisibleForTesting */
  static final Map<Class<?>, Type<?>> TYPE_MAP = Maps.newHashMap();

  static {
    TYPE_MAP.put(RawValue.class, RAW_VALUE_TYPE);

    // Doubles
    TYPE_MAP.put(Float.class, DOUBLE_VALUE_TYPE);
    TYPE_MAP.put(Double.class, DOUBLE_VALUE_TYPE);

    // Integers
    TYPE_MAP.put(Byte.class, INT_64_VALUE_TYPE);
    TYPE_MAP.put(Short.class, INT_64_VALUE_TYPE);
    TYPE_MAP.put(Integer.class, INT_64_VALUE_TYPE);
    TYPE_MAP.put(Long.class, INT_64_VALUE_TYPE);
    TYPE_MAP.put(Date.class, new DateType());
    TYPE_MAP.put(Rating.class, new RatingType());

    // Strings
    TYPE_MAP.put(String.class, STRING_VALUE_TYPE);
    TYPE_MAP.put(Link.class, new LinkType());
    TYPE_MAP.put(ShortBlob.class, new ShortBlobType());
    TYPE_MAP.put(Category.class, new CategoryType());
    TYPE_MAP.put(PhoneNumber.class, new PhoneNumberType());
    TYPE_MAP.put(PostalAddress.class, new PostalAddressType());
    TYPE_MAP.put(Email.class, new EmailType());
    TYPE_MAP.put(IMHandle.class, new IMHandleType());
    TYPE_MAP.put(BlobKey.class, new BlobKeyType());
    TYPE_MAP.put(Blob.class, new BlobType());
    TYPE_MAP.put(Text.class, new TextType());
    TYPE_MAP.put(EmbeddedEntity.class, new EmbeddedEntityType());

    // Unique
    TYPE_MAP.put(Boolean.class, BOOL_VALUE_TYPE);
    TYPE_MAP.put(User.class, USER_VALUE_TYPE);
    TYPE_MAP.put(Key.class, KEY_VALUE_TYPE);
    TYPE_MAP.put(GeoPt.class, new GeoPtType());

    // Double check that we're in sync with DataTypeUtils.
    assert TYPE_MAP.keySet().equals(DataTypeUtils.getSupportedTypes())
        : "Warning:  DataTypeUtils and DataTypeTranslator do not agree "
            + "about supported classes: "
            + TYPE_MAP.keySet()
            + " vs. "
            + DataTypeUtils.getSupportedTypes();
  }

  /* @VisibleForTesting */
  static final Type<?>[] TYPES_WITHOUT_MEANING_IN_EXPECTED_FREQUENCY_ORDER = {
    INT_64_VALUE_TYPE,
    DOUBLE_VALUE_TYPE,
    STRING_VALUE_TYPE,
    BOOL_VALUE_TYPE,
    KEY_VALUE_TYPE,
    USER_VALUE_TYPE
  };

  /* @VisibleForTesting */
  static final Type<?>[] MEANING_INT_TO_TYPE;

  static {
    int meaningMax = 0;
    for (Meaning meaning : Meaning.values()) {
      if (meaning.getNumber() > meaningMax) {
        meaningMax = meaning.getNumber();
      }
    }
    Type<?>[] meaningIntToType = new Type<?>[meaningMax];
    Set<Type<?>> typesWithoutMeaning = new HashSet<>();
    for (Type<?> type : TYPE_MAP.values()) {
      int meaningInt = type.getV3Meaning().getNumber();
      if (meaningInt != 0) {
        meaningIntToType[meaningInt] = type;
      }
    }
    MEANING_INT_TO_TYPE = meaningIntToType;
  }

  /**
   * A map with the {@link Comparable} classes returned by all the instances of {@link
   * Type#asComparable(Object)} as keys and the pb code point as the value. Used for comparing
   * values that don't map to the same pb code point.
   */
  private static final Map<Class<? extends Comparable<?>>, Integer> comparableTypeMap =
      new HashMap<Class<? extends Comparable<?>>, Integer>();

  static {
    // hardcoding the tag numbers from appengine-java-standard/protobuf/api/entity.proto
    comparableTypeMap.put(ComparableByteArray.class, 3); // stringValue = 3
    comparableTypeMap.put(Long.class, 1); // int64Value = 1
    comparableTypeMap.put(Double.class, 4); // doubleValue = 4
    comparableTypeMap.put(Boolean.class, 2); // booleanValue = 2
    comparableTypeMap.put(User.class, 8); // UserValue = 8
    comparableTypeMap.put(Key.class, 12); // ReferenceValue = 12
    comparableTypeMap.put(GeoPt.class, 5); // PointValue = 5
  }

  /**
   * Add all of the properties in the specified map to an {@code EntityProto}. This involves
   * determining the type of each property and creating the proper type-specific protocol buffer.
   *
   * <p>If the property value is an {@link UnindexedValue}, or if it's a type that is never indexed,
   * e.g. {@code Text} and {@code Blob}, it's added to {@code EntityProto.raw_property}. Otherwise
   * it's added to {@code EntityProto.property}.
   *
   * @param map A not {@code null} map of all the properties which will be set on {@code proto}
   * @param proto A not {@code null} protocol buffer
   */
  public static void addPropertiesToPb(Map<String, ?> map, EntityProto proto) {
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      String name = entry.getKey();

      boolean forceIndexedEmbeddedEntity = false;
      boolean indexed = true;
      Object value = entry.getValue();

      if (entry.getValue() instanceof WrappedValue) {
        WrappedValue wrappedValue = (WrappedValue) entry.getValue();
        forceIndexedEmbeddedEntity = wrappedValue.getForceIndexedEmbeddedEntity();
        indexed = wrappedValue.isIndexed();
        value = wrappedValue.getValue();
      }

      if (value instanceof Collection<?>) {
        Collection<?> values = (Collection<?>) value;
        addListPropertyToPb(proto, name, indexed, values, forceIndexedEmbeddedEntity);
      } else {
        addPropertyToPb(name, value, indexed, forceIndexedEmbeddedEntity, false, proto);
      }
    }
  }

  /** @see #addPropertiesToPb(Map, EntityProto) */
  static void addPropertiesToPb(Map<String, ?> map, com.google.datastore.v1.Entity.Builder proto) {
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      proto.putProperties(entry.getKey(), toV1Value(entry.getValue()).build());
    }
  }

  private static void addListPropertyToPb(
      EntityProto proto,
      String name,
      boolean indexed,
      Collection<?> values,
      boolean forceIndexedEmbeddedEntity) {
    if (values.isEmpty()) {
      Property.Builder property = Property.newBuilder();
      property.setName(name);
      property.setMultiple(false);
      if (DatastoreServiceConfig.getEmptyListSupport()) {
        // DS now supports empty lists, so we write a real empty list
        property.setMeaning(Meaning.EMPTY_LIST);
      } else {
        // Backward compatible behavior: Write an empty collection as null.
        // If the value is indexed it appears in queries, but distinction between
        // null and empty list is lost.
      }
      property.getValue(); // Indicate to the proto that we have set this field
      if (indexed) {
        proto = proto.toBuilder().addProperty(property.build()).build();
      } else {
        proto = proto.toBuilder().addRawProperty(property.build()).build();
      }
    } else {
      // Write every element to the PB
      for (Object listValue : values) {
        addPropertyToPb(
            name, listValue, indexed, forceIndexedEmbeddedEntity, true /* multiple */, proto);
      }
    }
  }

  /**
   * Adds a property to {@code entity}.
   *
   * @param name the property name
   * @param value the property value
   * @param indexed whether this property should be indexed. This may be overridden by property
   *     types like Blob and Text that are never indexed.
   * @param forceIndexedEmbeddedEntity whether indexed embedded entities should actually be indexed,
   *     as opposed to silently moved to unindexed properties (legacy behavior)
   * @param multiple whether this property has multiple values
   * @param entity the entity to populate
   */
  private static void addPropertyToPb(
      String name,
      @Nullable Object value,
      boolean indexed,
      boolean forceIndexedEmbeddedEntity,
      boolean multiple,
      EntityProto entity) {
    Property.Builder property = Property.newBuilder();
    property.setName(name);
    property.setMultiple(multiple);
    PropertyValue.Builder newValue = property.getValueBuilder();
    if (value != null) {
      Type<?> type = getType(value.getClass());
      Meaning meaning = type.getV3Meaning();
      if (meaning != property.getMeaning()) {
        property.setMeaning(meaning);
      }
      type.toV3Value(value, newValue);
      if (indexed
          && forceIndexedEmbeddedEntity
          && DataTypeUtils.isUnindexableType(value.getClass())) {
        // This piece of code repeats the same validation performed in setIndexedProperty to deal
        // with collections whose contents have been changed in the meantime.
        throw new UnsupportedOperationException("Value must be indexable.");
      }
      if (!forceIndexedEmbeddedEntity || !(value instanceof EmbeddedEntity)) {
        // If client was trying to index a type that they shouldn't then clear the index flag for
        // them.
        indexed &= type.canBeIndexed();
      }
    }
    if (indexed) {
      entity = entity.toBuilder().addProperty(property).build();
    } else {
      entity = entity.toBuilder().addRawProperty(property).build();
    }
  }

  // Used by QueryTranslator.
  static PropertyValue toV3Value(Object value) {
    PropertyValue.Builder propertyValue = PropertyValue.newBuilder();
    if (value != null) {
      getType(value.getClass()).toV3Value(value, propertyValue);
    }
    return propertyValue.build();
  }

  /** Copy all of the indexed properties present on {@code proto} into {@code map}. */
  public static void extractIndexedPropertiesFromPb(
      EntityProto proto, Map<String, @Nullable Object> map) {
    for (Property property : proto.getPropertyList()) {
      addPropertyToMap(property, true, map);
    }
  }

  /** Copy all of the unindexed properties present on {@code proto} into {@code map}. */
  private static void extractUnindexedPropertiesFromPb(
      EntityProto proto, Map<String, @Nullable Object> map) {
    for (Property property : proto.getRawPropertyList()) {
      addPropertyToMap(property, false, map);
    }
  }

  /** Copy all of the properties present on {@code proto} into {@code map}. */
  public static void extractPropertiesFromPb(EntityProto proto, Map<String, @Nullable Object> map) {
    extractIndexedPropertiesFromPb(proto, map);
    extractUnindexedPropertiesFromPb(proto, map);
  }

  /**
   * Copy all of the properties present on {@code proto} into {@code map}.
   *
   * <p>Cloud Datastore v1 entity must know if the proto came from an index-only query as the User
   * type overwrites the INDEX_ONLY meaning.
   *
   * @param proto the proto from which to extract properties
   * @param indexOnly if the proto is from an index only query (a projection query)
   * @param map the map to populate
   */
  static void extractPropertiesFromPb(
      EntityOrBuilder proto, boolean indexOnly, Map<String, @Nullable Object> map) {
    if (indexOnly) {
      for (Map.Entry<String, Value> prop : proto.getPropertiesMap().entrySet()) {
        map.put(prop.getKey(), new RawValue(prop.getValue()));
      }
    } else {
      for (Map.Entry<String, Value> prop : proto.getPropertiesMap().entrySet()) {
        addPropertyToMap(prop.getKey(), prop.getValue(), map);
      }
    }
  }

  /** Copy all of the implicit properties present on {@code proto} into {@code map}. */
  public static void extractImplicitPropertiesFromPb(
      EntityProto proto, Map<String, @Nullable Object> map) {
    for (Property property : getImplicitProperties(proto)) {
      addPropertyToMap(property, true, map);
    }
  }

  private static Iterable<Property> getImplicitProperties(EntityProto proto) {
    return Collections.singleton(buildImplicitKeyProperty(proto));
  }

  private static Property buildImplicitKeyProperty(EntityProto proto) {
    Property.Builder keyProp = Property.newBuilder();
    keyProp.setName(Entity.KEY_RESERVED_PROPERTY);
    PropertyValue.Builder propVal = PropertyValue.newBuilder();
    propVal.setReferenceValue(KeyType.toReferenceValue(proto.getKey()));
    keyProp.setValue(propVal.build());
    return keyProp.build();
  }

  /**
   * Locates and returns all indexed properties with the given name on the given proto. If there are
   * a mix of matching multiple and non-multiple properties, the collection will contain the first
   * non-multiple property.
   *
   * @return A list, potentially empty, containing matching properties.
   */
  public static Collection<Property> findIndexedPropertiesOnPb(
      EntityProto proto, String propertyName) {
    if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY)) {
      return Collections.singleton(buildImplicitKeyProperty(proto));
    }
    List<Property> matchingMultipleProps = new ArrayList<>();
    for (Property prop : proto.getPropertyList()) {
      if (prop.getName().equals(propertyName)) {
        if (!prop.getMultiple()) {
          return Collections.singleton(prop);
        } else {
          matchingMultipleProps.add(prop);
        }
      }
    }
    return matchingMultipleProps;
  }

  private static @Nullable Object wrapIfUnindexed(boolean indexed, @Nullable Object value) {
    return indexed ? value : new UnindexedValue(value);
  }

  private static void addPropertyToMap(
      Property property, boolean indexed, Map<String, @Nullable Object> map) {
    String name = property.getName();

    if (property.getMeaning() == Meaning.EMPTY_LIST) {
      // Read an empty list, but user hasn't enabled empty list support.  In order to be
      // backward compatible, return null because thats what they used to get for empty lists.
      Object emptyListValue =
          DatastoreServiceConfig.getEmptyListSupport() ? new ArrayList<Object>() : null;
      map.put(name, wrapIfUnindexed(indexed, emptyListValue));
    } else {
      Object value = getPropertyValue(property);
      if (property.getMultiple()) {
        @SuppressWarnings({"unchecked"})
        List<Object> resultList = (List<Object>) PropertyContainer.unwrapValue(map.get(name));
        if (resultList == null) {
          resultList = new ArrayList<Object>();
          map.put(name, indexed ? resultList : new UnindexedValue(resultList));
        }
        if (indexed && value instanceof EmbeddedEntity) {
          map.put(name, new WrappedValueImpl(resultList, true, true));
        }
        resultList.add(value);
      } else {
        if (indexed && value instanceof EmbeddedEntity) {
          value = new WrappedValueImpl(value, true, true);
        } else if (!indexed) {
          value = new UnindexedValue(value);
        }
        map.put(name, value);
      }
    }
  }

  private static void addPropertyToMap(
      String name, Value value, Map<String, @Nullable Object> map) {
    boolean isOrContainsIndexedEntityValue = false;
    boolean indexed;
    Object result;

    if (DatastoreServiceConfig.getEmptyListSupport()
        && value.getValueTypeCase() == Value.ValueTypeCase.ARRAY_VALUE
        && value.getArrayValue().getValuesCount() == 0) {
      result = new ArrayList<Object>();
      indexed = !value.getExcludeFromIndexes();
    } else if (value.getArrayValue().getValuesCount() > 0) {
      indexed = false;
      // Must be ArrayList for backwards compatibility.
      ArrayList<@Nullable Object> resultList =
          new ArrayList<>(value.getArrayValue().getValuesCount());
      for (Value subValue : value.getArrayValue().getValuesList()) {
        if (subValue.getValueTypeCase() == ValueTypeCase.ARRAY_VALUE) {
          throw new IllegalArgumentException("Invalid Entity PB: list within a list.");
        }
        result = getValue(subValue);
        if (!subValue.getExcludeFromIndexes()) {
          indexed = true; // This is not optimal but it's needed to be "bug compatible" with v3.
          if (result instanceof EmbeddedEntity) {
            isOrContainsIndexedEntityValue = true;
          }
        }
        resultList.add(result);
      }
      result = resultList;
    } else {
      // Empty list becomes null.
      indexed = !value.getExcludeFromIndexes();
      result = getValue(value);
      if (indexed && result instanceof EmbeddedEntity) {
        isOrContainsIndexedEntityValue = true;
      }
    }

    if (isOrContainsIndexedEntityValue) {
      result = new WrappedValueImpl(result, true, true);
    } else if (!indexed) {
      result = new UnindexedValue(result);
    }

    map.put(name, result);
  }

  /**
   * Returns the value for the property as its canonical type.
   *
   * @param property a not {@code null} property
   * @return {@code null} if no value was set for {@code property}
   */
  public static @Nullable Object getPropertyValue(Property property) {
    int meaningInt = property.getMeaning().getNumber();
    PropertyValue value = property.getValue();
    if (meaningInt == 0) {
      // The value has no meaning.  Check possible types, most likely first.
      for (Type<?> type : TYPES_WITHOUT_MEANING_IN_EXPECTED_FREQUENCY_ORDER) {
        if (type.hasValue(value)) {
          return type.getValue(value);
        }
      }
      // The value is null or malformed.
    } else if ((meaningInt > 0) && (meaningInt < MEANING_INT_TO_TYPE.length)) {
      // The value has a meaning.  Check that meaning's type.
      Type<?> type = MEANING_INT_TO_TYPE[meaningInt];
      if ((type != null) && type.hasValue(value)) {
        return type.getValue(value);
      }
      // The value is malformed.
    } else {
      // The value is malformed.
    }
    return null;
  }

  /**
   * Converts a single value to a {@link com.google.datastore.v1.Value.Builder} for use in a query.
   * {@link WrappedValue} and {@link Collection}s are not supported.
   */
  static Value.Builder toV1ValueForQuery(Object value) {
    // Datastore v3 queries have no notion of indexed values vs. unindexed values, since this
    // information is only encoded by the two separate property fields in the entity proto.
    // Additionally, the meaning field is not populated when this client constructs query protos.
    // As a result, it's possible to query by non-indexable values (Text, Blob).
    //
    // Cloud Datastore v1 makes indexing explicit in the query proto (and sets meaning in query
    // protos). For backwards compatibility, we clear the exclude_form_indexes field here (and unset
    // meaning if it would result in the query being invalid).
    Value.Builder valueBuilder = toV1Value(value, true, false);
    valueBuilder.clearExcludeFromIndexes();
    if (value != null && DataTypeUtils.isUnindexableType(value.getClass())) {
      valueBuilder.clearMeaning();
    }
    return valueBuilder;
  }

  /* @VisibleForTesting */
  static Value.Builder toV1Value(
      @Nullable Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
    if (value == null) {
      Value.Builder builder = Value.newBuilder();
      builder.setNullValue(NullValue.NULL_VALUE);
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    } else if (indexed
        && forceIndexedEmbeddedEntity
        && DataTypeUtils.isUnindexableType(value.getClass())) {
      // This piece of code is needed to deal with collections whose contents have been changed
      // after having been passed to setIndexedProperty().
      throw new UnsupportedOperationException("Value must be indexable.");
    }
    return getType(value.getClass()).toV1Value(value, indexed, forceIndexedEmbeddedEntity);
  }

  private static Value.Builder toV1Value(@Nullable Object value) {
    boolean indexed = true;
    boolean forceIndexedEmbeddedEntity = false;

    if (value instanceof WrappedValue) {
      WrappedValue wrappedValue = (WrappedValue) value;
      indexed = wrappedValue.isIndexed();
      forceIndexedEmbeddedEntity = wrappedValue.getForceIndexedEmbeddedEntity();
      value = wrappedValue.getValue();
    }

    if (value instanceof Collection<?>) {
      Collection<?> values = (Collection<?>) value;
      if (values.isEmpty()) {
        if (DatastoreServiceConfig.getEmptyListSupport()) {
          return Value.newBuilder()
              .setExcludeFromIndexes(!indexed)
              .setArrayValue(ArrayValue.getDefaultInstance());
        } else {
          // Backward compatible behavior: Write an empty collection as null.
          // If the value is indexed it appears in queries, but distinction between
          // null and empty list is lost.
          return toV1Value(null, indexed, forceIndexedEmbeddedEntity);
        }
      } else {
        Value.Builder valueBuilder = Value.newBuilder();
        for (Object listValue : values) {
          valueBuilder
              .getArrayValueBuilder()
              .addValues(toV1Value(listValue, indexed, forceIndexedEmbeddedEntity));
        }
        return valueBuilder;
      }
    } else {
      return toV1Value(value, indexed, forceIndexedEmbeddedEntity);
    }
  }

  private static @Nullable Object getValue(Value value) {
    for (Type<?> type : TYPE_MAP.values()) {
      if (type.isType(value)) {
        return type.getValue(value);
      }
    }
    return null;
  }

  private static Meaning getV3MeaningOf(ValueOrBuilder value) {
    return Meaning.valueOf(value.getMeaning());
  }

  private static AppIdNamespace toAppIdNamespace(PartitionIdOrBuilder partitionId) {
    if (partitionId.getProjectId().equals(DatastoreApiHelper.getCurrentProjectId())) {
      // Not a foreign key.
      return new AppIdNamespace(DatastoreApiHelper.getCurrentAppId(), partitionId.getNamespaceId());
    }
    Map<String, String> additionalProjectIdToAppIdMap = getAdditionalProjectIdToAppIdMap();
    if (additionalProjectIdToAppIdMap.containsKey(partitionId.getProjectId())) {
      // Known foreign key.
      return new AppIdNamespace(
          additionalProjectIdToAppIdMap.get(partitionId.getProjectId()),
          partitionId.getNamespaceId());
    } else {
      // Unknown foreign key.
      throw new IllegalStateException(
          String.format(
              "Could not determine app id corresponding to project id \"%s\". Please add the app "
                  + "id to %s.",
              partitionId.getProjectId(), DatastoreServiceGlobalConfig.ADDITIONAL_APP_IDS_VAR));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> getAdditionalProjectIdToAppIdMap() {
    if (ApiProxy.getCurrentEnvironment() != null) {
      Object attribute =
          ApiProxy.getCurrentEnvironment()
              .getAttributes()
              .get(ADDITIONAL_APP_IDS_MAP_ATTRIBUTE_KEY);
      if (attribute != null) {
        return (Map<String, String>) attribute;
      }
    }
    return Collections.emptyMap();
  }

  private static PartitionId.Builder toV1PartitionId(AppIdNamespace appNs) {
    PartitionId.Builder builder = PartitionId.newBuilder();
    builder.setProjectId(DatastoreApiHelper.toProjectId(appNs.getAppId()));
    if (!appNs.getNamespace().isEmpty()) {
      builder.setNamespaceId(appNs.getNamespace());
    }
    return builder;
  }

  static com.google.datastore.v1.Key.Builder toV1Key(Key key) {
    com.google.datastore.v1.Key.Builder builder = com.google.datastore.v1.Key.newBuilder();
    builder.setPartitionId(toV1PartitionId(key.getAppIdNamespace()));
    List<PathElement> pathElementList = new ArrayList<>();
    for (Key key2 = key; key2 != null; key2 = key2.getParent()) {
      PathElement.Builder pathElement = PathElement.newBuilder();
      pathElement.setKind(key2.getKind());
      if (key2.getName() != null) {
        pathElement.setName(key2.getName());
      } else if (key2.getId() != Key.NOT_ASSIGNED) {
        pathElement.setId(key2.getId());
      }
      pathElementList.add(pathElement.build());
    }
    builder.addAllPath(Lists.reverse(pathElementList));
    return builder;
  }

  static Key toKey(KeyOrBuilder proto) {
    // Check that the reference contains elements first.
    if (proto.getPathCount() == 0) {
      throw new IllegalArgumentException("Invalid Key PB: no elements.");
    }
    AppIdNamespace appIdNamespace = toAppIdNamespace(proto.getPartitionId());
    Key key = null;
    for (PathElement e : proto.getPathList()) {
      String kind = e.getKind();
      key =
          new Key(
              kind,
              key,
              e.getId(),
              e.getIdTypeCase() == IdTypeCase.NAME ? e.getName() : null,
              appIdNamespace);
    }
    return requireNonNull(key);
  }

  /**
   * Deserializes a Cloud Datastore V1 {@code Entity} protobuf and converts it to an App Engine
   * Datastore {@code Entity}. {@code serializedV1Entity} should be an output of {@link
   * #toSerializedV1Proto}; no guarantees for other inputs.
   *
   * @throws IllegalArgumentException if the argument could not be deserialized
   */
  public static Entity toEntityFromSerializedV1Proto(byte[] serializedV1Entity) {
    try {
      return toEntity(com.google.datastore.v1.Entity.parseFrom(serializedV1Entity));
    } catch (InvalidProtocolBufferException exception) {
      throw new IllegalArgumentException(exception);
    }
  }

  static Entity toEntity(EntityOrBuilder entityV1) {
    Entity entity = new Entity(DataTypeTranslator.toKey(entityV1.getKey()));
    DataTypeTranslator.extractPropertiesFromPb(
        entityV1, /* indexOnly= */ false, entity.getPropertyMap());
    return entity;
  }

  static Entity toEntity(EntityOrBuilder entityV1, Collection<Projection> projections) {
    Entity entity = new Entity(DataTypeTranslator.toKey(entityV1.getKey()));

    Map<String, @Nullable Object> values = Maps.newHashMap();
    DataTypeTranslator.extractPropertiesFromPb(entityV1, /* indexOnly= */ true, values);
    for (Projection projection : projections) {
      entity.setProperty(projection.getName(), projection.getValue(values));
    }
    return entity;
  }

  /**
   * Converts an App Engine Datastore {@code Entity} to a Cloud Datastore {@code Entity} V1 protobuf
   * and serializes it.
   *
   * <p>Note that the conversion may be lossy or fail; for example, the Cloud Datastore V1 API
   * supports only UTF-8 strings, so App Engine Datastore entities with string properties that are
   * not UTF-8 encoded may behave in unexpected ways if supplied to Cloud Datastore. You can verify
   * that the conversion was successful for entity {@code x} by checking {@code
   * x.equals(toEntityFromSerializedV1Proto(toSerializedV1Proto(x))}.
   */
  public static byte[] toSerializedV1Proto(Entity entity) {
    return toV1Entity(entity).build().toByteArray();
  }

  static com.google.datastore.v1.Entity.Builder toV1Entity(Entity entity) {
    com.google.datastore.v1.Entity.Builder entityV1 = com.google.datastore.v1.Entity.newBuilder();
    entityV1.setKey(toV1Key(entity.getKey()));
    addPropertiesToPb(entity.getPropertyMap(), entityV1);
    return entityV1;
  }

  /**
   * Returns the value for the property as its comparable representation type.
   *
   * @param property a not {@code null} property
   * @return {@code null} if no value was set for {@code property}
   */
  @SuppressWarnings("unchecked")
  public static @Nullable Comparable<Object> getComparablePropertyValue(Property property) {
    // Returning the raw datastore index value.
    return (Comparable<Object>) RAW_VALUE_TYPE.asComparable(new RawValue(property.getValue()));
  }

  /**
   * Converts the given {@link Object} into a supported value then returns it as a comparable object
   * so it can be compared to other data types.
   *
   * @param value any Object that can be converted into a supported DataType
   * @return {@code null} if value is null
   * @throws UnsupportedOperationException if value is not supported
   */
  @SuppressWarnings("unchecked")
  static @Nullable Comparable<Object> getComparablePropertyValue(@Nullable Object value) {
    return value == null
        ? null
        : (Comparable<Object>) getType(value.getClass()).asComparable(value);
  }

  /**
   * Get the rank of the given datastore type relative to other datastore types. Note that datastore
   * types do not necessarily have unique ranks.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static int getTypeRank(Class<? extends Comparable> datastoreType) {
    // We just use the protobuf tag id.
    return requireNonNull(comparableTypeMap.get(datastoreType));
  }

  /**
   * Gets the {@link Type} that knows how to translate objects of type {@code clazz} into protocol
   * buffers that the data store can handle.
   *
   * @throws UnsupportedOperationException if clazz is not supported
   */
  @SuppressWarnings("unchecked")
  private static <T> Type<T> getType(Class<T> clazz) {
    if (TYPE_MAP.containsKey(clazz)) {
      return (Type<T>) TYPE_MAP.get(clazz);
    } else {
      throw new UnsupportedOperationException("Unsupported data type: " + clazz.getName());
    }
  }

  /**
   * {@code Type} is an abstract class that knows how to convert Java objects of one or more types
   * into datastore representations.
   *
   * @param <T> The canonical Java class for this type.
   */
  abstract static class Type<T> {

    /** Returns {@code true} if the given value matches this {@link Type}. */
    public boolean isType(Value propertyValue) {
      // TODO: I believe this is sometimes incorrect.  In this method type Value is Cloud
      // Datastore V1.  The Type instances are designed for App Eng V3, and have App Eng V3
      // meanings.  Several Cloud Datastore V1 value types without meanings correspond to App Eng V3
      // value types with meanings.  Any values of those types will fail this initial comparison.
      return getV3MeaningOf(propertyValue) == getV3Meaning() && hasValue(propertyValue);
    }

    /**
     * Returns the {@link Comparable} for the given value, or {@code null} if values of this type
     * are not comparable.
     */
    public abstract @Nullable Comparable<?> asComparable(Object value);

    /** Sets the value of {@code propertyValue} to {@code value}. */
    public abstract void toV3Value(Object value, PropertyValue.Builder propertyValue);

    /** @return Whether the value is indexable */
    public abstract boolean canBeIndexed();

    /**
     * Returns a new Cloud Datastore v1 Value for the given parameters.
     *
     * @param value the Java native value to convert
     * @param indexed the desired indexing, ignored for types that are not indexable
     * @return the Cloud Datastore v1 representation of the given value and desired indexing
     */
    public abstract Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity);

    /**
     * Returns the value of {@code propertyValue} as its canonical Java type.
     *
     * <p>Use {@link #isType} first to determine if the property has a value of the given type.
     *
     * @param propertyValue a not {@code null} value representing this {@code Type}
     * @return the canonical Java representation of {@code propertyValue}.
     */
    public abstract T getValue(PropertyValue propertyValue);

    /** See {@link Type#getValue(PropertyValue)}. */
    public abstract T getValue(Value value);

    /** Returns {@code true} if a value of this {@code Type} is set on the given propertyValue. */
    public abstract boolean hasValue(PropertyValue propertyValue);

    /** Returns {@code true} if a value of this {@code Type} is set on the given propertyValue. */
    public abstract boolean hasValue(Value propertyValue);

    /** Returns the {@link Meaning} for this {@link Type}. */
    protected Meaning getV3Meaning() {
      return Meaning.NO_MEANING;
    }
  }

  /**
   * A base class with common functions for types that have the same datastore representation.
   *
   * @param <S> the datastore type
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseVariantType<S, T> extends Type<T> {
    /** Returns the datastore representation of the given value. */
    protected abstract S toDatastoreValue(Object value);
    /** Returns the native representation of the given value. */
    protected abstract T fromDatastoreValue(S datastoreValue);
  }

  /**
   * Base class for types that store strings in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseStringType<T> extends BaseVariantType<String, T> {
    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      propertyValue.setStringValue(toDatastoreValue(value));
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setStringValue(toDatastoreValue(value));
      builder.setExcludeFromIndexes(!indexed); // Assume indexable.
      builder.setMeaning(getV3Meaning().getNumber()); // Assume v1 Meaning == v3 Meaning.
      return builder;
    }

    @Override
    public final T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValue());
    }

    @Override
    public T getValue(Value propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValue());
    }

    @Override
    public final boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.STRING_VALUE;
    }

    @Override
    public @Nullable ComparableByteArray asComparable(Object value) {
      return new ComparableByteArray(toBytesUtf8(toDatastoreValue(value)));
    }

    private static byte[] toBytesUtf8(String str) {
      return toBytes(str, StandardCharsets.UTF_8);
    }

    private static byte @Nullable [] toBytes(String str, Charset charset) {
      return str != null ? str.getBytes(charset) : null;
    }
  }

  /**
   * Base class for types that store bytes in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseBlobType<T> extends BaseVariantType<byte[], T> {
    protected abstract boolean isIndexable();

    @Override
    public final boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public final void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      propertyValue.setStringValueBytes(ByteString.copyFrom(toDatastoreValue(value)));
    }

    @Override
    public boolean canBeIndexed() {
      return isIndexable();
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setBlobValue(ByteString.copyFrom(toDatastoreValue(value)));
      builder.setExcludeFromIndexes(!indexed || !isIndexable());
      return builder;
    }

    @Override
    public final T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValueBytes().toByteArray());
    }

    @Override
    public final @Nullable ComparableByteArray asComparable(Object value) {
      return isIndexable() ? new ComparableByteArray(toDatastoreValue(value)) : null;
    }
  }

  /**
   * Base class for types that store predefined entities in Cloud Datastore v1.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BasePredefinedEntityType<T> extends Type<T> {
    /** Returns the predefined entity meaning to use in Cloud Datastore v1. */
    protected abstract int getV1Meaning();

    /** Returns the Cloud Datastore v1 Entity representation for the given value. */
    protected abstract com.google.datastore.v1.Entity getEntity(Object value);

    @Override
    public final boolean isType(Value propertyValue) {
      return propertyValue.getMeaning() == getV1Meaning() && hasValue(propertyValue);
    }

    @Override
    public final boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.ENTITY_VALUE;
    }

    @Override
    public final Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setEntityValue(getEntity(value));
      builder.setExcludeFromIndexes(!indexed);
      builder.setMeaning(getV1Meaning());
      return builder;
    }
  }

  /** Returns the Cloud Datastore v1 value representation for the given value, unindexed. */
  private static Value makeUnindexedValue(String value) {
    return Value.newBuilder().setStringValue(value).setExcludeFromIndexes(true).build();
  }

  /**
   * Base class for types that int64 values in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseInt64Type<T> extends BaseVariantType<Long, T> {
    @Override
    public final void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      propertyValue.setInt64Value(toDatastoreValue(value));
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setIntegerValue(toDatastoreValue(value));
      builder.setExcludeFromIndexes(!indexed);
      builder.setMeaning(getV3Meaning().getNumber());
      return builder;
    }

    @Override
    public T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getInt64Value());
    }

    @Override
    public T getValue(Value propertyValue) {
      return fromDatastoreValue(propertyValue.getIntegerValue());
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasInt64Value();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.INTEGER_VALUE;
    }

    @Override
    public Long asComparable(Object value) {
      return toDatastoreValue(value);
    }
  }

  /** The type for projected index values. */
  private static final class RawValueType extends Type<RawValue> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.INDEX_VALUE;
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return true;
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return true;
    }

    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canBeIndexed() {
      return false;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RawValue getValue(PropertyValue propertyValue) {
      return new RawValue(propertyValue);
    }

    @Override
    public RawValue getValue(Value propertyValue) {
      return new RawValue(propertyValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable Comparable<?> asComparable(Object value) {
      Object value2 = ((RawValue) value).getValue();
      // All possible values except byte[] are already comparable.
      if (value2 instanceof byte[]) {
        return new ComparableByteArray((byte[]) value2);
      }
      return (Comparable<?>) value2;
    }
  }

  /** The raw String type. */
  private static final class StringType extends BaseStringType<String> {
    @Override
    protected String toDatastoreValue(Object value) {
      return value.toString();
    }

    @Override
    protected String fromDatastoreValue(String datastoreValue) {
      return datastoreValue;
    }
  }

  /** The raw int64 type. */
  private static final class Int64Type extends BaseInt64Type<Long> {
    @Override
    protected Long toDatastoreValue(Object value) {
      return ((Number) value).longValue();
    }

    @Override
    protected Long fromDatastoreValue(Long datastoreValue) {
      return datastoreValue;
    }
  }

  /** The raw double type. */
  private static final class DoubleType extends Type<Double> {
    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      propertyValue.setDoubleValue(((Number) value).doubleValue());
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setDoubleValue(((Number) value).doubleValue());
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public Double getValue(PropertyValue propertyValue) {
      return propertyValue.getDoubleValue();
    }

    @Override
    public Double getValue(Value propertyValue) {
      return propertyValue.getDoubleValue();
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasDoubleValue();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.DOUBLE_VALUE;
    }

    @Override
    public Double asComparable(Object value) {
      return ((Number) value).doubleValue();
    }
  }

  /** The raw boolean type. */
  private static final class BoolType extends Type<Boolean> {
    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      propertyValue.setBooleanValue((Boolean) value);
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setBooleanValue((Boolean) value);
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public Boolean getValue(PropertyValue propertyValue) {
      return propertyValue.getBooleanValue();
    }

    @Override
    public Boolean getValue(Value propertyValue) {
      return propertyValue.getBooleanValue();
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasBooleanValue();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.BOOLEAN_VALUE;
    }

    @Override
    public Boolean asComparable(Object value) {
      return (Boolean) value;
    }
  }

  /**
   * The user type.
   *
   * <p>Stored as an entity with a special meaning in v1.
   */
  private static final class UserType extends BasePredefinedEntityType<User> {
    public static final int MEANING_PREDEFINED_ENTITY_USER = 20;
    public static final String PROPERTY_NAME_EMAIL = "email";
    public static final String PROPERTY_NAME_AUTH_DOMAIN = "auth_domain";
    public static final String PROPERTY_NAME_USER_ID = "user_id";

    @Override
    public int getV1Meaning() {
      return MEANING_PREDEFINED_ENTITY_USER;
    }

    @Override
    public com.google.datastore.v1.Entity getEntity(Object value) {
      User user = (User) value;
      com.google.datastore.v1.Entity.Builder builder = com.google.datastore.v1.Entity.newBuilder();
      builder.putProperties(PROPERTY_NAME_EMAIL, makeUnindexedValue(user.getEmail()));
      builder.putProperties(PROPERTY_NAME_AUTH_DOMAIN, makeUnindexedValue(user.getAuthDomain()));
      if (user.getUserId() != null) {
        builder.putProperties(PROPERTY_NAME_USER_ID, makeUnindexedValue(user.getUserId()));
      }
      return builder.build();
    }

    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      User user = (User) value;
      UserValue.Builder userValue = UserValue.newBuilder();
      userValue.setEmail(user.getEmail());
      userValue.setAuthDomain(user.getAuthDomain());
      if (user.getUserId() != null) {
        userValue.setObfuscatedGaiaid(user.getUserId());
      }
      // This value is filled in by the app server.  The runtime process doesn't know it.
      userValue.setGaiaid(0);
      propertyValue.setUserValue(userValue).build();
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public User getValue(PropertyValue propertyValue) {
      UserValue userValue = propertyValue.getUserValue();
      String userId = userValue.hasObfuscatedGaiaid() ? userValue.getObfuscatedGaiaid() : null;
      return new User(userValue.getEmail(), userValue.getAuthDomain(), userId);
    }

    @Override
    public User getValue(Value propertyValue) {
      String email = "";
      String authDomain = "";
      String userId = null;
      for (Map.Entry<String, Value> prop :
          propertyValue.getEntityValueOrBuilder().getPropertiesMap().entrySet()) {
        if (prop.getKey().equals(PROPERTY_NAME_EMAIL)) {
          email = prop.getValue().getStringValue();
        } else if (prop.getKey().equals(PROPERTY_NAME_AUTH_DOMAIN)) {
          authDomain = prop.getValue().getStringValue();
        } else if (prop.getKey().equals(PROPERTY_NAME_USER_ID)) {
          userId = prop.getValue().getStringValue();
        }
      }
      return new User(email, authDomain, userId);
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasUserValue();
    }

    @Override
    public final Comparable<User> asComparable(Object value) {
      return (User) value;
    }
  }

  /**
   * The GeoPt type.
   *
   * <p>Stored as a GeoPoint value with no meaning in Cloud Datastore v1.
   */
  private static class GeoPtType extends Type<GeoPt> {
    @Override
    public boolean isType(Value propertyValue) {
      // Geo point values with a meaning do not match this type or any type which causes them to
      // show up as null.
      return propertyValue.getValueTypeCase() == ValueTypeCase.GEO_POINT_VALUE
          && propertyValue.getMeaning() == 0;
    }

    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      GeoPt geoPt = (GeoPt) value;
      PropertyValue.PointValue pv =
          PointValue.newBuilder().setX(geoPt.getLatitude()).setY(geoPt.getLongitude()).build();
      propertyValue.setPointValue(pv);
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public final Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      GeoPt geoPt = (GeoPt) value;
      Value.Builder builder = Value.newBuilder();
      builder
          .getGeoPointValueBuilder()
          .setLatitude(geoPt.getLatitude())
          .setLongitude(geoPt.getLongitude());
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public GeoPt getValue(PropertyValue propertyValue) {
      PropertyValue.PointValue pv = propertyValue.getPointValue();
      return new GeoPt((float) pv.getX(), (float) pv.getY());
    }

    @Override
    public GeoPt getValue(Value propertyValue) {
      // Note that we lose precision with this conversion.
      return new GeoPt(
          (float) propertyValue.getGeoPointValue().getLatitude(),
          (float) propertyValue.getGeoPointValue().getLongitude());
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasPointValue();
    }

    @Override
    public final boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.GEO_POINT_VALUE;
    }

    @Override
    public Meaning getV3Meaning() {
      return Meaning.GEORSS_POINT;
    }

    @Override
    public final Comparable<GeoPt> asComparable(Object value) {
      return (GeoPt) value;
    }
  }

  /** The key/reference type. */
  private static final class KeyType extends Type<Key> {
    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      Reference keyRef = KeyTranslator.convertToPb((Key) value);
      propertyValue.setReferenceValue(toReferenceValue(keyRef));
    }

    @Override
    public boolean canBeIndexed() {
      return true;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setKeyValue(toV1Key((Key) value));
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public Key getValue(PropertyValue propertyValue) {
      // Translate the ReferenceValue PB to a Reference PB,
      // since that's what KeyTranslator understands.
      return KeyTranslator.createFromPb(toReference(propertyValue.getReferenceValue()));
    }

    @Override
    public Key getValue(Value propertyValue) {
      return toKey(propertyValue.getKeyValue());
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasReferenceValue();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.KEY_VALUE;
    }

    @Override
    public Key asComparable(Object value) {
      return (Key) value;
    }

    private static ReferenceValue toReferenceValue(Reference keyRef) {
      ReferenceValue.Builder refValue = ReferenceValue.newBuilder();
      refValue.setApp(keyRef.getApp());
      if (keyRef.hasNameSpace()) {
        refValue.setNameSpace(keyRef.getNameSpace());
      }
      Path.Builder path = keyRef.getPath().toBuilder();
      for (Element element : path.getElementList()) {
        ReferenceValue.PathElement.Builder newElement = ReferenceValue.PathElement.newBuilder();
        newElement.setType(element.getType());
        if (element.hasName()) {
          newElement.setName(element.getName());
        }
        if (element.hasId()) {
          newElement.setId(element.getId());
        }
        refValue.addPathElement(newElement.build());
      }

      return refValue.build();
    }

    private static Reference toReference(ReferenceValue refValue) {
      Reference.Builder reference = Reference.newBuilder();
      reference.setApp(refValue.getApp());
      if (refValue.hasNameSpace()) {
        reference.setNameSpace(refValue.getNameSpace());
      }
      Path.Builder path = Path.newBuilder();
      for (ReferenceValue.PathElement element : refValue.getPathElementList()) {
        Element.Builder newElement = Element.newBuilder();
        newElement.setType(element.getType());
        if (element.hasName()) {
          newElement.setName(element.getName());
        }
        if (element.hasId()) {
          newElement.setId(element.getId());
        }
        path.addElement(newElement);
      }
      reference.setPath(path);
      return reference.build();
    }
  }

  /** The non-indexable blob type. */
  private static class BlobType extends BaseBlobType<Blob> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BLOB;
    }

    @Override
    public boolean isType(Value propertyValue) {
      return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING
          && propertyValue.getExcludeFromIndexes() // Only matches non-indexed values.
          && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.BLOB_VALUE;
    }

    @Override
    public Blob getValue(Value propertyValue) {
      return fromDatastoreValue(propertyValue.getBlobValue().toByteArray());
    }

    @Override
    protected Blob fromDatastoreValue(byte[] datastoreValue) {
      return new Blob(datastoreValue);
    }

    @Override
    protected byte[] toDatastoreValue(Object value) {
      return ((Blob) value).getBytes();
    }

    @Override
    public boolean isIndexable() {
      return false;
    }
  }

  /** The indexable blob type. */
  private static class ShortBlobType extends BaseBlobType<ShortBlob> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BYTESTRING;
    }

    @Override
    public boolean isType(Value propertyValue) {
      if (!hasValue(propertyValue)) {
        return false;
      }

      // Match non-indexed blobs only if they have the right meaning.
      if (propertyValue.getExcludeFromIndexes()) {
        return getV3MeaningOf(propertyValue) == getV3Meaning();
      } else {
        return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING;
      }
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = super.toV1Value(value, indexed, forceIndexedEmbeddedEntity);
      if (!indexed) {
        // If a short blob was not indexed, the meaning needs to be set to disambiguate it from
        // the non-indexable blob type.
        builder.setMeaning(getV3Meaning().getNumber());
      }
      return builder;
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.BLOB_VALUE
          || (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE
              && propertyValue.getValueTypeCase() == ValueTypeCase.STRING_VALUE);
    }

    @Override
    public ShortBlob getValue(Value propertyValue) {
      if (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE
          && propertyValue.getValueTypeCase() == ValueTypeCase.STRING_VALUE) {
        return fromDatastoreValue(propertyValue.getStringValueBytes().toByteArray());
      } else {
        return fromDatastoreValue(propertyValue.getBlobValue().toByteArray());
      }
    }

    @Override
    protected byte[] toDatastoreValue(Object value) {
      return ((ShortBlob) value).getBytes();
    }

    @Override
    protected ShortBlob fromDatastoreValue(byte[] datastoreValue) {
      return new ShortBlob(datastoreValue);
    }

    @Override
    public boolean isIndexable() {
      return true;
    }
  }

  /**
   * The entity type.
   *
   * <p>Stored as a partially serialized EntityProto in V3.
   */
  private static final class EmbeddedEntityType extends Type<EmbeddedEntity> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ENTITY_PROTO;
    }

    @Override
    public boolean isType(Value propertyValue) {
      return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.ENTITY_VALUE;
    }

    @Override
    public EmbeddedEntity getValue(PropertyValue propertyValue) {
      EntityProto.Builder proto = EntityProto.newBuilder();
      try{
        proto.mergeFrom(propertyValue.getStringValueBytes());
      } catch (InvalidProtocolBufferException e){
        throw new IllegalArgumentException("Could not parse EntityProto value");
      }
      EmbeddedEntity result = new EmbeddedEntity();
      // NOTE: Java proto1 encodes the immutable default
      // instance when a required field is not set, so we check the app which
      // is required to not be empty, but is empty on the default instance.
      if (proto.hasKey() && !proto.getKey().getApp().isEmpty()) {
        result.setKey(KeyTranslator.createFromPb(proto.getKey()));
      }
      extractPropertiesFromPb(proto.build(), result.getPropertyMap());
      return result;
    }

    @Override
    public EmbeddedEntity getValue(Value propertyValue) {
      EmbeddedEntity result = new EmbeddedEntity();
      com.google.datastore.v1.Entity proto = propertyValue.getEntityValue();
      if (proto.hasKey()) {
        result.setKey(toKey(proto.getKey()));
      }
      extractPropertiesFromPb(proto, false, result.getPropertyMap());
      return result;
    }

    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      EmbeddedEntity structProp = (EmbeddedEntity) value;
      EntityProto.Builder proto = EntityProto.newBuilder();
      if (structProp.getKey() != null) {
        proto.setKey(KeyTranslator.convertToPb(structProp.getKey()));
      }
      addPropertiesToPb(structProp.getPropertyMap(), proto.build());
      // TODO: Figure out how to do partial serialization.
      propertyValue.setStringValueBytes(proto.build().toByteString()).build();
    }

    @Override
    public boolean canBeIndexed() {
      return false;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      EmbeddedEntity structProp = (EmbeddedEntity) value;
      Value.Builder builder = Value.newBuilder();
      com.google.datastore.v1.Entity.Builder proto = builder.getEntityValueBuilder();
      if (structProp.getKey() != null) {
        proto.setKey(toV1Key(structProp.getKey()));
      }
      addPropertiesToPb(structProp.getPropertyMap(), proto);
      builder.setExcludeFromIndexes(!indexed || !forceIndexedEmbeddedEntity);
      return builder;
    }

    @Override
    public @Nullable Comparable<?> asComparable(Object value) {
      return null;
    }
  }

  /** The non-indexable {@link Text} type. */
  private static final class TextType extends BaseStringType<Text> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.TEXT;
    }

    @Override
    public void toV3Value(Object value, PropertyValue.Builder propertyValue) {
      super.toV3Value(value, propertyValue);
    }

    @Override
    public boolean canBeIndexed() {
      return false;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      return super.toV1Value(value, false, false);
    }

    @Override
    protected Text fromDatastoreValue(String datastoreString) {
      return new Text(datastoreString);
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Text) value).getValue();
    }

    @Override
    public @Nullable ComparableByteArray asComparable(Object value) {
      return null;
    }
  }

  /** The {@link BlobKey} type. Blob keys are just strings with a special meaning. */
  private static final class BlobKeyType extends BaseStringType<BlobKey> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BLOBKEY;
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Value.Builder builder = Value.newBuilder();
      builder.setStringValue(toDatastoreValue(value));
      builder.setMeaning(Meaning.BLOBKEY.getNumber());
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public BlobKey getValue(Value propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValue());
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((BlobKey) value).getKeyString();
    }

    @Override
    protected BlobKey fromDatastoreValue(String datastoreString) {
      return new BlobKey(datastoreString);
    }
  }

  /**
   * The date type.
   *
   * <p>In V3 dates are just int64s with a special meaning.
   */
  private static final class DateType extends BaseInt64Type<Date> {
    // Copied from com.google.apphosting.datastore.shared.ValidationUtils (truncated to milliseconds
    // for use with java.util.Date).
    //
    // 0001-01-01T00:00:00.000Z
    private static final long RFC_3339_MIN_MILLISECONDS_INCLUSIVE = -62135596800L * 1000;
    // 9999-12-31T23:59:59.999Z
    private static final long RFC_3339_MAX_MILLISECONDS_INCLUSIVE = 253402300799L * 1000 + 999;

    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_WHEN;
    }

    @Override
    public boolean isType(Value propertyValue) {
      return isTimestampValue(propertyValue) || isNonRfc3339Value(propertyValue);
    }

    @Override
    public boolean hasValue(Value propertyValue) {
      // Stored in a separate field in v1.
      return propertyValue.getValueTypeCase() == ValueTypeCase.TIMESTAMP_VALUE
          || isNonRfc3339Value(propertyValue)
          || isIndexValue(propertyValue);
    }

    @Override
    public Value.Builder toV1Value(
        Object value, boolean indexed, boolean forceIndexedEmbeddedEntity) {
      Date date = (Date) value;
      Value.Builder builder;
      if (isInRfc3339Bounds(date)) {
        // NOTE: Logic for handling non-RFC 3339 Dates is intentionally not propagated
        // into DatastoreHelper in low-level client.
        builder = DatastoreHelper.makeValue(date);
      } else {
        builder = Value.newBuilder();
        builder.setIntegerValue(toDatastoreValue(date));
        builder.setMeaning(Meaning.GD_WHEN.getNumber());
      }
      builder.setExcludeFromIndexes(!indexed);
      return builder;
    }

    @Override
    public Date getValue(Value propertyValue) {
      if (isNonRfc3339Value(propertyValue) || isIndexValue(propertyValue)) {
        return fromDatastoreValue(propertyValue.getIntegerValue());
      } else {
        long datastoreValue = DatastoreHelper.getTimestamp(propertyValue);
        return fromDatastoreValue(datastoreValue);
      }
    }

    @Override
    protected Long toDatastoreValue(Object value) {
      // Date(long) expects milliseconds, Datastore uses micros.
      return ((Date) value).getTime() * 1000L;
    }

    @Override
    protected Date fromDatastoreValue(Long datastoreValue) {
      // Date(long) expects milliseconds, Datastore uses micros.
      return new Date(datastoreValue / 1000L);
    }

    private static boolean isTimestampValue(Value propertyValue) {
      // No meaning in v1.
      return propertyValue.getValueTypeCase() == ValueTypeCase.TIMESTAMP_VALUE
          && propertyValue.getMeaning() == 0;
    }

    private static boolean isNonRfc3339Value(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.INTEGER_VALUE
          && propertyValue.getMeaning() == Meaning.GD_WHEN.getNumber();
    }

    private static boolean isIndexValue(Value propertyValue) {
      return propertyValue.getValueTypeCase() == ValueTypeCase.INTEGER_VALUE
          && getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE;
    }

    private static boolean isInRfc3339Bounds(Date date) {
      return date.getTime() >= RFC_3339_MIN_MILLISECONDS_INCLUSIVE
          && date.getTime() <= RFC_3339_MAX_MILLISECONDS_INCLUSIVE;
    }
  }

  /** Internally a link is just a string with a special meaning. */
  private static final class LinkType extends BaseStringType<Link> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ATOM_LINK;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Link) value).getValue();
    }

    @Override
    protected Link fromDatastoreValue(String datastoreValue) {
      return new Link(datastoreValue);
    }
  }

  /** Internally a category is just a string with a special meaning. */
  private static final class CategoryType extends BaseStringType<Category> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ATOM_CATEGORY;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Category) value).getCategory();
    }

    @Override
    protected Category fromDatastoreValue(String datastoreString) {
      return new Category(datastoreString);
    }
  }

  /** Internally a rating is just an int64 with a special meaning. */
  private static final class RatingType extends BaseInt64Type<Rating> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_RATING;
    }

    @Override
    protected Long toDatastoreValue(Object value) {
      return (long) ((Rating) value).getRating();
    }

    @Override
    protected Rating fromDatastoreValue(Long datastoreLong) {
      return new Rating(datastoreLong.intValue());
    }
  }

  /** Internally an email is just a string with a special meaning. */
  private static final class EmailType extends BaseStringType<Email> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_EMAIL;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Email) value).getEmail();
    }

    @Override
    protected Email fromDatastoreValue(String datastoreString) {
      return new Email(datastoreString);
    }
  }

  /** Internally a postal address is just a string with a special meaning. */
  private static final class PostalAddressType extends BaseStringType<PostalAddress> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_POSTALADDRESS;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((PostalAddress) value).getAddress();
    }

    @Override
    protected PostalAddress fromDatastoreValue(String datastoreString) {
      return new PostalAddress(datastoreString);
    }
  }

  /** Internally a phone number is just a string with a special meaning. */
  private static final class PhoneNumberType extends BaseStringType<PhoneNumber> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_PHONENUMBER;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((PhoneNumber) value).getNumber();
    }

    @Override
    protected PhoneNumber fromDatastoreValue(String datastoreString) {
      return new PhoneNumber(datastoreString);
    }
  }

  /** Internally an IM handle is just a string with a special meaning and a well known format. */
  private static final class IMHandleType extends BaseStringType<IMHandle> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_IM;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((IMHandle) value).toDatastoreString();
    }

    @Override
    protected IMHandle fromDatastoreValue(String datastoreString) {
      return IMHandle.fromDatastoreString(datastoreString);
    }
  }

  // for testing
  static Map<Class<?>, Type<?>> getTypeMap() {
    return TYPE_MAP;
  }

  /**
   * A wrapper for a {@code byte[]} that implements {@link Comparable}. Comparison algorithm is the
   * same as the prod datastore.
   */
  public static final class ComparableByteArray implements Comparable<ComparableByteArray> {
    private final byte[] bytes;

    public ComparableByteArray(byte[] bytes) {
      this.bytes = bytes;
    }

    // performs an unsigned comparison
    // thanks to chrisn for this!
    @Override
    public int compareTo(ComparableByteArray other) {
      byte[] otherBytes = other.bytes;
      for (int i = 0; i < Math.min(bytes.length, otherBytes.length); i++) {
        int v1 = bytes[i] & 0xFF; // zero-extended
        int v2 = otherBytes[i] & 0xFF; // zero-extended
        if (v1 != v2) {
          return v1 - v2;
        }
      }
      return bytes.length - otherBytes.length;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == null) {
        return false;
      }
      return Arrays.equals(bytes, ((ComparableByteArray) obj).bytes);
    }

    @Override
    public int hashCode() {
      int result = 1;
      for (byte b : bytes) {
        result = 31 * result + b;
      }
      return result;
    }
  }

  // All methods are static.  Do not instantiate.
  private DataTypeTranslator() {}
}
