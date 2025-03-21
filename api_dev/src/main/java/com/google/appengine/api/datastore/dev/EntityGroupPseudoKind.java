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

import static com.google.appengine.api.datastore.Entities.ENTITY_GROUP_METADATA_ID;
import static com.google.appengine.api.datastore.Entities.ENTITY_GROUP_METADATA_KIND;
import static com.google.appengine.api.datastore.Entity.VERSION_RESERVED_PROPERTY;
import static com.google.appengine.api.datastore.dev.Utils.checkRequest;

import com.google.appengine.api.datastore.dev.LocalDatastoreService.LiveTxn;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile.EntityGroup;
import com.google.apphosting.datastore.DatastoreV3Pb.Query;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Pseudo-kind that returns metadata about an entity group.
 *
 */
class EntityGroupPseudoKind implements PseudoKind {
  // NOTE: Entity group versions are not persisted in the dev server, so we can't provide a
  // stable value across dev server restarts. However, the __entity_group__ contract is only that
  // __version__ must increase on every entity group change, so it's also safe to increase it on
  // every restart by offseting the internal version (which counts change since the restart) with
  // the current time. Note that clock skew, or a write rate > 1000 / ms could break
  // this, but that is acceptable for this dev environment.
  private final long baseVersion = System.currentTimeMillis() * 1000;

  @Override
  public String getKindName() {
    return ENTITY_GROUP_METADATA_KIND;
  }

  @Override
  public List<EntityProto> runQuery(Query query) {
    checkRequest(false, "queries not supported on " + ENTITY_GROUP_METADATA_KIND);
    return null;
  }

  @Override
  public @Nullable EntityProto get(
      @Nullable LiveTxn txn, EntityGroup eg, Reference key, boolean eventualConsistency) {
    // We plan to add support to query the set of entity groups by querying this pseudo-kind.  Such
    // queries would return pseudo-entities as direct children of the entity-group root with
    // numeric key ID. Thus to make sure we can get() and queries are consistent, we require that
    // key match that format.
    Path path = key.getPath();
    if (path.elementSize() != 2 || path.getElement(1).getId() != ENTITY_GROUP_METADATA_ID) {
      return null;
    }
    long version;
    if (txn == null) {
      // Can just grab the entity-group version as its incremented eagerly rather than on job-apply.
      version = eg.getVersion();
    } else {
      version = txn.trackEntityGroup(eg).getEntityGroupVersion();
    }
    // NOTE: we can't distinguish new from empty-after-dev-server-restart entity groups,
    // so to avoid having entity group versions transition from non-null to null, we always
    // return a version even when the entity group is empty(new or not).
    return makeEntityGroupEntity(key, version + baseVersion);
  }

  /** Creates an __entity_group__ entity */
  private static EntityProto makeEntityGroupEntity(Reference key, long version) {
    EntityProto egEntity = new EntityProto().setKey(key);
    // EntityProto.entity_group is a required PB field.
    egEntity.getMutableEntityGroup().addElement(key.getPath().getElement(0));

    PropertyValue value = new PropertyValue().setInt64Value(version);
    egEntity.addProperty().setMultiple(false).setName(VERSION_RESERVED_PROPERTY).setValue(value);

    return egEntity;
  }
}
