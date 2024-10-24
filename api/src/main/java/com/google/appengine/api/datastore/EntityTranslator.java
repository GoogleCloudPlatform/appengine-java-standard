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

import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Reference;
import java.util.Collection;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code EntityTranslator} contains the logic to translate an {@code Entity} into the protocol
 * buffers that are used to pass it to the implementation of the API.
 *
 */
public class EntityTranslator {

  // Note: We'd like to make {@code EntityTranslator} package protected, but
  // we need to use it from {@link LocalDatastoreService}, so it's public.
  // We attempted to move {@code EntityTranslator} (and other classes) into
  // an impl sub-package, but they use package-protected methods on
  // classes such as {@link Entity} and {@link Key} which we can not move.

  public static Entity createFromPb(EntityProto proto, Collection<Projection> projections) {
    Key key = KeyTranslator.createFromPb(proto.getKey());

    Entity entity = new Entity(key);
    Map<String, @Nullable Object> values = Maps.newHashMap();
    DataTypeTranslator.extractPropertiesFromPb(proto, values);
    for (Projection projection : projections) {
      entity.setProperty(projection.getName(), projection.getValue(values));
    }
    return entity;
  }

  public static Entity createFromPb(EntityProto proto) {
    Key key = KeyTranslator.createFromPb(proto.getKey());

    Entity entity = new Entity(key);
    DataTypeTranslator.extractPropertiesFromPb(proto, entity.getPropertyMap());
    return entity;
  }

  public static Entity createFromPbBytes(byte[] pbBytes) {
    EntityProto.Builder proto = EntityProto.newBuilder();
    boolean parsed = true;
    try{
      proto.mergeFrom(pbBytes);
    }catch (InvalidProtocolBufferException e){
      parsed = false;
    }
    if (!parsed || !proto.isInitialized()) {
      throw new IllegalArgumentException("Could not parse EntityProto bytes");
    }
    return createFromPb(proto.build());
  }

  public static EntityProto convertToPb(Entity entity) {
    Reference reference = KeyTranslator.convertToPb(entity.getKey());

    EntityProto.Builder proto = EntityProto.newBuilder();
    proto.setKey(reference);

    // If we've already been stored, make sure the entity group is set
    // to match our key.
    Path.Builder entityGroup = proto.getEntityGroup().toBuilder();
    Key key = entity.getKey();
    if (key.isComplete()) {
      entityGroup.addElement(reference.getPath().getElement(0));
    }

    DataTypeTranslator.addPropertiesToPb(entity.getPropertyMap(), proto.build());
    return proto.build();
  }

  // All methods are static.  Do not instantiate.
  private EntityTranslator() {}
}
