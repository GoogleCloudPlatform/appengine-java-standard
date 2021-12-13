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

package com.google.appengine.api.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Makes immutable copies of collections using only standard Java classes. */
public final class ImmutableCopy {
  private ImmutableCopy() {}

  /**
   * Returns an immutable List with the same contents as the given collection. The List is
   * guaranteed not to change even if the collection does.
   */
  public static <E> List<E> list(Collection<E> collection) {
    switch (collection.size()) {
      case 0:
        return Collections.emptyList();
      case 1:
        return Collections.singletonList(collection.iterator().next());
      default:
        List<E> copy = new ArrayList<>(collection.size());
        copy.addAll(collection);
        return Collections.unmodifiableList(copy);
    }
  }
}
