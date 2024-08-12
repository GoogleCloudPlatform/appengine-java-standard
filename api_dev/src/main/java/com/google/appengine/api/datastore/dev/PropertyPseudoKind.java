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
import static com.google.appengine.api.datastore.Entities.PROPERTY_METADATA_KIND;
import static com.google.appengine.api.datastore.dev.Utils.checkRequest;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.LocalCompositeIndexManager.KeyTranslator;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Extent;
import com.google.appengine.api.datastore.dev.LocalDatastoreService.Profile;
import com.google.apphosting.datastore.proto2api.DatastoreV3Pb.Query;
import com.google.common.collect.Lists;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.storage.onestore.PropertyType;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Property;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import java.util.List;
import java.util.Map;

/**
 * Pseudo-kind named __property__ that queries the datastore's schema.
 *
 */
class PropertyPseudoKind extends KeyFilteredPseudoKind {
  PropertyPseudoKind(LocalDatastoreService localDatastore) {
    super(localDatastore);
  }

  @Override
  public String getKindName() {
    return PROPERTY_METADATA_KIND;
  }

  private static class KindProperty {
    String kind;
    String property;

    KindProperty(String kind, String property) {
      this.kind = kind;
      this.property = property;
    }
  }

  /**
   * Returns a KindProperty tuple with kind = property = null if {@code key} = null, kind = X,
   * property = "" if {@code key} = __kind__/X, kind = X, property = Y if {@code key} = __kind__/X,
   * __property__/Y and throws an appropriate ApplicationError otherwise.
   */
  private KindProperty propertyKeyToKindProperty(Key key) {
    if (key == null) {
      return new KindProperty(null, null);
    }
    Key parent = key.getParent();
    if (parent == null) { // __kind__/PROPERTY_METADATA_KIND only
      checkRequest(
          key.getKind().equals(KIND_METADATA_KIND) && key.getName() != null,
          String.format(
              "Key filter on %s is invalid (parent must be a named key for %s)" + " - received %s",
              PROPERTY_METADATA_KIND, KIND_METADATA_KIND, key));

      return new KindProperty(key.getName(), "");
    } else { // __kind__/PROPERTY_METADATA_KIND, __property__/PROPERTY_METADATA_KIND only
      checkRequest(
          parent.getParent() == null,
          String.format(
              "Key filter on %s is invalid (must have no parent or %s parent)" + " - received %s",
              PROPERTY_METADATA_KIND, KIND_METADATA_KIND, key));
      checkRequest(
          parent.getKind().equals(KIND_METADATA_KIND) && parent.getName() != null,
          String.format(
              "Key filter on %s is invalid (parent must be named key for %s)" + " - received %s",
              PROPERTY_METADATA_KIND, KIND_METADATA_KIND, key));
      checkRequest(
          key.getKind().equals(PROPERTY_METADATA_KIND) && key.getName() != null,
          String.format(
              "Key filter on %s is invalid (must be named key for %s)" + " - received %s",
              PROPERTY_METADATA_KIND, PROPERTY_METADATA_KIND, key));

      return new KindProperty(parent.getName(), key.getName());
    }
  }

  // Based on LocalDatastoreService.getSchema (now removed)
  @Override
  List<EntityProto> runQuery(
      Query query, Key startKey, boolean startInclusive, Key endKey, boolean endInclusive) {
    checkRequest(
        !query.hasTransaction(),
        "transactional queries on " + PROPERTY_METADATA_KIND + " not allowed");

    KindProperty start = propertyKeyToKindProperty(startKey);
    KindProperty end = propertyKeyToKindProperty(endKey);

    if (query.hasAncestor()) {
      KindProperty ancestor =
          propertyKeyToKindProperty(KeyTranslator.createFromPb(query.getAncestor()));

      /* The LocalDatastoreService will handle the ancestor property if we don't. So we don't
       * bother to handle the (less common) case of both a key filter and an ancestor (lots
       * of code for little benefit). */
      if (start.kind == null && end.kind == null) {

        start.kind = ancestor.kind;
        if (!ancestor.property.isEmpty()) {
          end.kind = ancestor.kind;
          start.property = end.property = ancestor.property;
          startInclusive = endInclusive = true;
        } else {
          // Implement restriction to a given kind as the range (kind, "") - (succ(kind), "")
          // where succ(kind) = kind + '\0' is the string lexicographically after kind
          // (inclusiveness is irrelevant as "" is not a valid property name)
          end.kind = ancestor.kind + "\0";
          start.property = end.property = "";
        }
        query.clearAncestor();
      }
    }

    return getProperties(
        query.getApp(),
        query.getNameSpace(),
        query.isKeysOnly(),
        start,
        startInclusive,
        end,
        endInclusive);
  }

  /** Get the results of a __property__ query over the specified range. */
  private List<EntityProto> getProperties(
      String app,
      String namespace,
      boolean keysOnly,
      KindProperty start,
      boolean startInclusive,
      KindProperty end,
      boolean endInclusive) {
    Profile profile = getDatastore().getOrCreateProfile(app);
    Map<String, Extent> extents = profile.getExtents();
    List<EntityProto> schema = Lists.newArrayList();

    synchronized (extents) {
      // We want to create one EntityProto per kind, with that EntityProto containing the
      // properties for all names and representations that exist on all EntityProtos of
      // that kind.
      for (Map.Entry<String, Extent> entry : extents.entrySet()) {
        String kind = entry.getKey();
        boolean startKindEqual = false;
        boolean endKindEqual = false;

        // Apply kind filter (inclusive is only meaningful at the property level).
        if (start.kind != null) {
          int kindsCompared = kind.compareTo(start.kind);

          startKindEqual = kindsCompared == 0;
          if (kindsCompared < 0) {
            continue;
          }
        }
        if (end.kind != null) {
          int kindsCompared = kind.compareTo(end.kind);

          endKindEqual = kindsCompared == 0;
          if (kindsCompared > 0) {
            continue;
          }
        }

        List<EntityProto> entities = getEntitiesForNamespace(entry.getValue(), namespace);
        // Skip kinds with no entities in the specified namespace
        if (entities.isEmpty()) {
          continue;
        }

        // Collect and add the indexed properties. (schema queries don't
        // report unindexed properties; details in http://b/1004244)
        SortedSetMultimap<String, String> allProps = TreeMultimap.create();
        for (EntityProto entity : entities) {
          for (Property prop : entity.propertys()) {
            String name = prop.getName();
            PropertyType type = PropertyType.getType(prop.getValue());

            // Apply start property filter if kind equal to start.kind
            if (startKindEqual) {
              int propertysCompared = name.compareTo(start.property);
              if ((startInclusive && propertysCompared < 0)
                  || (!startInclusive && propertysCompared <= 0)) {
                continue;
              }
            }
            // Apply end property filter if kind equal to end.kind
            if (endKindEqual) {
              int propertysCompared = name.compareTo(end.property);
              if ((endInclusive && propertysCompared > 0)
                  || (!endInclusive && propertysCompared >= 0)) {
                continue;
              }
            }

            // Skip invisible special properties.
            if (getDatastore().getSpecialPropertyMap().containsKey(name)
                && !getDatastore().getSpecialPropertyMap().get(name).isVisible()) {
              continue;
            }

            allProps.put(name, type.name());
          }
        }
        addPropertyEntitiesToSchema(schema, kind, allProps, app, namespace, keysOnly);
      }
    }
    return schema;
  }

  /** Get the entities from the extent that are in the specified namespace */
  private static List<EntityProto> getEntitiesForNamespace(Extent extent, String namespace) {
    // Apply namespace filter to list of entities
    List<EntityProto> entities = Lists.newArrayListWithCapacity(extent.getAllEntityProtos().size());
    for (EntityProto entity : extent.getAllEntityProtos()) {
      if (entity.getKey().getNameSpace().equals(namespace)) {
        entities.add(entity);
      }
    }
    return entities;
  }

  /** Build __property__ entities from results of scanning entities, and add them to the schema */
  private static void addPropertyEntitiesToSchema(
      List<EntityProto> schema,
      String kind,
      SortedSetMultimap<String, String> allProps,
      String app,
      String namespace,
      boolean keysOnly) {
    // Now build the property entities. Note that keySet() and get(...) both return
    // {@link SortedSet}s so the results will be ordered.
    for (String prop : allProps.keySet()) {
      // Create schema entity and set its key based on the kind
      EntityProto propEntity = new EntityProto();
      schema.add(propEntity);

      Path path = new Path();
      path.addElement().setType(KIND_METADATA_KIND).setName(kind);
      path.addElement().setType(PROPERTY_METADATA_KIND).setName(prop);
      Reference key = new Reference().setApp(app).setPath(path);
      if (namespace.length() > 0) {
        key.setNameSpace(namespace);
      }
      propEntity.setKey(key);
      // EntityProto.entity_group is a required PB field.
      propEntity.getMutableEntityGroup().addElement(path.getElement(0));

      if (!keysOnly) {
        for (String rep : allProps.get(prop)) {
          PropertyValue repValue = new PropertyValue().setStringValue(rep);
          propEntity
              .addProperty()
              .setName("property_representation")
              .setValue(repValue)
              .setMultiple(true);
        }
      }
    }
  }
}
