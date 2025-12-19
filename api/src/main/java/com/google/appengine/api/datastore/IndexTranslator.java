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

import com.google.apphosting.api.AppEngineInternal;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity;
import com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.CompositeIndex.State;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to translate between {@link Index} to {@link
 * com.google.storage.onestore.v3_bytes.proto2api.OnestoreEntity.Index}.
 *
 */
@AppEngineInternal
public class IndexTranslator {
  // TODO: Support mode? and unspecified direction.

  public static OnestoreEntity.Index convertToPb(Index index) {
    OnestoreEntity.Index.Builder value =
        OnestoreEntity.Index.newBuilder()
            .setEntityType(index.getKind())
            .setAncestor(index.isAncestor());
    for (Index.Property property : index.getProperties()) {
      value.addProperty(convertToPb(property));
    }
    return value.build();
  }

  public static OnestoreEntity.Index.Property convertToPb(Index.Property property) {
    OnestoreEntity.Index.Property.Builder value =
        OnestoreEntity.Index.Property.newBuilder().setName(property.getName());
    Query.SortDirection dir = property.getDirection();
    if (dir != null) {
      value.setDirection(OnestoreEntity.Index.Property.Direction.valueOf(dir.name()));
    }
    return value.build();
  }

  public static Index convertFromPb(OnestoreEntity.CompositeIndex ci) {
    OnestoreEntity.Index index = ci.getDefinition();
    List<Index.Property> properties = new ArrayList<>();
    for (OnestoreEntity.Index.Property protoProperty : index.getPropertyList()) {
      properties.add(convertFromPb(protoProperty));
    }
    return new Index(
        ci.getId(),
        index.getEntityType(),
        index.getAncestor(),
        Collections.unmodifiableList(properties));
  }

  public static Index.Property convertFromPb(OnestoreEntity.Index.Property property) {
    Query.SortDirection dir =
        property.hasDirection()
            ? Query.SortDirection.valueOf(property.getDirection().name())
            : null;
    return new Index.Property(property.getName(), dir);
  }

  public static Index convertFromPb(OnestoreEntity.Index index) {
    return convertFromPb(OnestoreEntity.CompositeIndex.newBuilder().setId(0).setDefinition(index).setAppId("").setState(
            State.WRITE_ONLY).build());
  }
}
