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

import static com.google.appengine.api.datastore.Entities.NAMESPACE_METADATA_KIND;
import static com.google.appengine.api.datastore.dev.Utils.checkRequest;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Extent;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pseudo-kind named __namespace__ that returns namespaces used by an application.
 *
 */
class NamespacePseudoKind extends KeyFilteredPseudoKind {
  static final long EMPTY_NAMESPACE_ID = 1;

  NamespacePseudoKind(LocalDatastoreService localDatastore) {
    super(localDatastore);
  }

  @Override
  public String getKindName() {
    return NAMESPACE_METADATA_KIND;
  }

  /**
   * Returns the namespace specified by {@code key}, or throws an exception if the key's format is
   * invalid.
   */
  private String namespaceKeyToString(Key key) {
    if (key == null) {
      return null;
    }
    checkRequest(
        key.getParent() == null,
        String.format(
            "Key filter on %s is invalid (key has parent) - received %s",
            NAMESPACE_METADATA_KIND, key));
    checkRequest(
        key.getKind().equals(NAMESPACE_METADATA_KIND),
        String.format(
            "Key filter on %s is invalid (must be a key for %s) - received %s",
            NAMESPACE_METADATA_KIND, NAMESPACE_METADATA_KIND, key));
    if (key.getName() != null) {
      return key.getName();
    } else {
      checkRequest(
          key.getId() == EMPTY_NAMESPACE_ID,
          String.format(
              "Key filter on %s is invalid (key must be a name or the number %d)"
                  + " - received %s",
              NAMESPACE_METADATA_KIND, EMPTY_NAMESPACE_ID, key));
      return "";
    }
  }

  @Override
  List<EntityProto> runQuery(
      Query query, Key startKey, boolean startInclusive, Key endKey, boolean endInclusive) {
    /* Ancestor has no meaning in namespace queries. This also has the desirable side effect that
     * schema queries cannot live in transactions. */
    checkRequest(
        !query.hasAncestor(), "ancestor queries on " + NAMESPACE_METADATA_KIND + " not allowed");

    String app = query.getApp();
    String startNamespace = namespaceKeyToString(startKey);
    String endNamespace = namespaceKeyToString(endKey);
    Profile profile = getDatastore().getOrCreateProfile(app);
    Map<String, Extent> extents = profile.getExtents();
    Set<String> namespaceSet = new HashSet<String>();

    synchronized (extents) {
      // Just collect all namespaces that are in the selected range
      for (Map.Entry<String, Extent> entry : extents.entrySet()) {
        for (EntityProto entity : entry.getValue().getAllEntityProtos()) {
          String namespace = entity.getKey().getNameSpace();

          // Apply filters.
          if (startNamespace != null) {
            int namespacesCompared = namespace.compareTo(startNamespace);
            if ((startInclusive && namespacesCompared < 0)
                || (!startInclusive && namespacesCompared <= 0)) {
              continue;
            }
          }
          if (endNamespace != null) {
            int namespacesCompared = namespace.compareTo(endNamespace);
            if ((endInclusive && namespacesCompared > 0)
                || (!endInclusive && namespacesCompared >= 0)) {
              continue;
            }
          }
          namespaceSet.add(namespace);
        }
      }
    }
    return makeNamespaceEntities(namespaceSet, app, query.getNameSpace());
  }

  private List<EntityProto> makeNamespaceEntities(
      Set<String> namespaceSet, String app, String executionNamespace) {
    List<EntityProto> namespaces = Lists.newArrayListWithCapacity(namespaceSet.size());
    for (String namespace : namespaceSet) {
      // Create namespace entity and set its key based on the namespace
      EntityProto namespaceEntity = new EntityProto();
      namespaces.add(namespaceEntity);

      Path path = new Path();
      // Empty namespaces use an EMPTY_NAMESPACE_ID key
      if (namespace.isEmpty()) {
        path.addElement().setType(NAMESPACE_METADATA_KIND).setId(EMPTY_NAMESPACE_ID);
      } else {
        path.addElement().setType(NAMESPACE_METADATA_KIND).setName(namespace);
      }
      Reference key = new Reference().setApp(app).setPath(path);
      if (executionNamespace.length() > 0) {
        key.setNameSpace(executionNamespace);
      }
      namespaceEntity.setKey(key);
      // EntityProto.entity_group is a required PB field.
      namespaceEntity.getMutableEntityGroup().addElement(path.getElement(0));
    }
    return namespaces;
  }
}
