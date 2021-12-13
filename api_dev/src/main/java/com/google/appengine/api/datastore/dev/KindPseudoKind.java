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

import static com.google.appengine.api.datastore.Entities.KIND_METADATA_KIND;
import static com.google.appengine.api.datastore.dev.Utils.checkRequest;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Extent;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.List;
import java.util.Map;

/**
 * Pseudo-kind named __kind__ that queries the datastore's schema.
 *
 */
// Partially derived from {@link com.google.apphosting.datastore.KindPseudoKind}.
class KindPseudoKind extends KeyFilteredPseudoKind {
  KindPseudoKind(LocalDatastoreService localDatastore) {
    super(localDatastore);
  }

  @Override
  public String getKindName() {
    return KIND_METADATA_KIND;
  }

  private String kindKeyToString(Key key) {
    if (key == null) {
      return null;
    }
    checkRequest(
        key.getParent() == null,
        String.format(
            "Key filter on %s is invalid (key has parent) - received %s", KIND_METADATA_KIND, key));
    checkRequest(
        key.getKind().equals(KIND_METADATA_KIND) && key.getName() != null,
        String.format(
            "Key filter on %s is invalid (must be a named key for %s)" + " - received %s",
            KIND_METADATA_KIND, KIND_METADATA_KIND, key));

    return key.getName();
  }

  // Based on LocalDatastoreService.getSchema (now removed)
  @Override
  List<EntityProto> runQuery(
      Query query, Key startKey, boolean startInclusive, Key endKey, boolean endInclusive) {
    /* Ancestor has no meaning in schema queries. This also has the desirable side effect that
     * schema queries cannot live in transactions. */
    checkRequest(
        !query.hasAncestor(), "ancestor queries on " + KIND_METADATA_KIND + " not allowed");

    String app = query.getApp();
    String namespace = query.getNameSpace();
    String startKind = kindKeyToString(startKey);
    String endKind = kindKeyToString(endKey);
    Profile profile = getDatastore().getOrCreateProfile(app);
    Map<String, Extent> extents = profile.getExtents();
    List<EntityProto> kinds = Lists.newArrayList();

    synchronized (extents) {
      // We create one EntityProto per kind with a key containing the kind name
      for (Map.Entry<String, Extent> entry : extents.entrySet()) {
        String kind = entry.getKey();

        // Apply filters.
        if (startKind != null) {
          int kindsCompared = kind.compareTo(startKind);
          if ((startInclusive && kindsCompared < 0) || (!startInclusive && kindsCompared <= 0)) {
            continue;
          }
        }
        if (endKind != null) {
          int kindsCompared = kind.compareTo(endKind);
          if ((endInclusive && kindsCompared > 0) || (!endInclusive && kindsCompared >= 0)) {
            continue;
          }
        }
        if (entry.getValue().getAllEntityProtos().isEmpty()) {
          // no entities of this kind
          continue;
        }

        // Add an entry only if entities exist in the requested namespace.
        if (isKindPresentInNamespace(entry.getValue(), namespace)) {
          kinds.add(makeKindEntity(kind, app, namespace));
        }
      }
    }
    return kinds;
  }

  /** Checks if extent has any entities in the namespace */
  private static boolean isKindPresentInNamespace(Extent extent, String namespace) {
    for (EntityProto entity : extent.getAllEntityProtos()) {
      if (entity.getKey().getNameSpace().equals(namespace)) {
        return true;
      }
    }
    return false;
  }

  /** Creates a __kind__ entity */
  private static EntityProto makeKindEntity(String kind, String app, String namespace) {
    EntityProto kindEntity = new EntityProto();
    Path path = new Path();
    path.addElement().setType(KIND_METADATA_KIND).setName(kind);
    Reference key = new Reference().setApp(app).setPath(path);
    if (namespace.length() > 0) {
      key.setNameSpace(namespace);
    }
    kindEntity.setKey(key);
    // EntityProto.entity_group is a required PB field.
    kindEntity.getMutableEntityGroup().addElement(path.getElement(0));

    return kindEntity;
  }
}
