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

import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.proto2api.OnestoreEntity.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code KeyTranslator} contains the logic to translate a {@link Key} into the protocol buffers
 * that are used to pass it to the implementation of the API.
 *
 */
class KeyTranslator {

  public static Key createFromPb(Reference reference) {
    // Check that the reference contains elements first.
    Key parentKey = null;
    Path path = reference.getPath();
    List<Element> elements = path.getElementList();
    if (elements.isEmpty()) {
      throw new IllegalArgumentException("Invalid Key PB: no elements.");
    }

    AppIdNamespace appIdNamespace =
        new AppIdNamespace(
            reference.getApp(), reference.hasNameSpace() ? reference.getNameSpace() : "");

    for (Element e : elements) {
      String kind = e.getType();
      if (e.hasName() && e.hasId()) {
        throw new IllegalArgumentException("Invalid Key PB: both id and name are set.");
      } else if (e.hasName()) {
        parentKey = new Key(kind, parentKey, Key.NOT_ASSIGNED, e.getName(), appIdNamespace);
      } else if (e.hasId()) {
        parentKey = new Key(kind, parentKey, e.getId(), null, appIdNamespace);
      } else {
        // Incomplete key. Neither id nor name are set.
        //
        // TODO: consider throwing an exception if this is not the last
        // path element.
        parentKey = new Key(kind, parentKey, Key.NOT_ASSIGNED, null, appIdNamespace);
      }
    }

    return parentKey;
  }

  public static Reference convertToPb(Key key) {
    Reference.Builder reference = Reference.newBuilder();

    reference.setApp(key.getAppId());
    String nameSpace = key.getNamespace();
    if (!nameSpace.isEmpty()) {
      reference.setNameSpace(nameSpace);
    }

    Path.Builder path = reference.buildPartial().getPath().toBuilder();
    while (key != null) {
      Element.Builder pathElement = Element.newBuilder();
      pathElement.setType(key.getKind());
      if (key.getName() != null) {
        pathElement.setName(key.getName());
      } else if (key.getId() != Key.NOT_ASSIGNED) {
        pathElement.setId(key.getId());
      }
      path.addElement(pathElement.build());
      key = key.getParent();
    }
    List<Element> elements = new ArrayList<>(path.getElementList());
    Collections.reverse(elements);
    Path.Builder reversedPath = Path.newBuilder();
    for(Element element : elements){
      reversedPath.addElement(element);
    }
    reference.setPath(reversedPath.build());
    return reference.buildPartial();
  }

  public static void updateKey(Reference reference, Key key) {
    // TODO: Assert that the rest of the key matched?

    // Can only have id or name, not both.
    if (key.getName() == null) {
      Path path = reference.getPath();
      key.setId(path.getElement(path.getElementCount() - 1).getId());
    }
  }

  // All methods are static.  Do not instantiate (constructor is not private because
  // we need to make this class visible in the dev datastore).
  KeyTranslator() {}
}
