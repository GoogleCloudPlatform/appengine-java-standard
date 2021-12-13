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

import java.util.List;
import java.util.Map;

/**
 * Concrete {@link CallbackContext} implementation that is specific to intercepted get() operations.
 * Methods annotated with {@link PreGet} that receive instances of this class may modify the result
 * of the get() operation by calling {@link #setResultForCurrentElement(Entity)}. Keys that receive
 * results via this method will not be fetched from the datastore. This is an effective way to
 * inject cached results.
 *
 */
public final class PreGetContext extends BaseCallbackContext<Key> {

  /** The Map that wil ultimately be populated with the result of the get() RPC. */
  private final Map<Key, Entity> resultMap;

  PreGetContext(
      CurrentTransactionProvider currentTransactionProvider,
      List<Key> keys,
      Map<Key, Entity> resultMap) {
    super(currentTransactionProvider, keys);
    this.resultMap = resultMap;
  }

  @Override
  String getKind(Key key) {
    return key.getKind();
  }

  /**
   * Set the {@link Entity} that will be associated with the {@link Key} returned by {@link
   * #getCurrentElement()} in the result of the get() operation. This will prevent the get()
   * operation from fetching the Entity from the datastore. This is an effective way to inject
   * cached results.
   *
   * @param entity The entity to provide as the result for the current element.
   * @throws IllegalArgumentException If the key of the provided entity is not equal to the key
   *     returned by {@link #getCurrentElement()}.
   */
  public void setResultForCurrentElement(Entity entity) {
    if (entity == null) {
      throw new NullPointerException("entity cannot be null");
    }
    Key curKey = getCurrentElement();
    if (!curKey.equals(entity.getKey())) {
      throw new IllegalArgumentException("key of provided entity must be equal to current element");
    }
    resultMap.put(getCurrentElement(), entity);
  }
}
