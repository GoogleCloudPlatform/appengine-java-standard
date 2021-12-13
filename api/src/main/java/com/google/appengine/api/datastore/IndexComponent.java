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

import com.google.storage.onestore.v3.OnestoreEntity.Index.Property;
import java.util.List;

/**
 * An interface that represents a collection of index constraints. It can validate if an index
 * satisfies the constraints.
 *
 */
interface IndexComponent {

  /**
   * Given a list of index {@link Property}s, returns true if the index matches the constraints
   * given by the {@link IndexComponent}.
   *
   * @param indexProperties the index properties to match against
   * @return true if the index satisfies the component
   */
  boolean matches(List<Property> indexProperties);

  /** Returns the list of {@link Property}s that best satisfy this index component. */
  List<Property> preferredIndexProperties();

  /**
   * Returns the number of index constraints in this component.
   *
   * <p>If a {@code List<Property>} satisfies this {@link IndexComponent}, and thus {@link
   * IndexComponent#matches} returns true, the number of properties satisfied will be equal to the
   * size of the {@link IndexComponent}.
   *
   * @return the number of index constraints.
   */
  int size();
}
