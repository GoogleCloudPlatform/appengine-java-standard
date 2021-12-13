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

import java.util.Arrays;
import java.util.List;

/**
 * Concrete {@link CallbackContext} implementation that is specific to intercepted operations that
 * load {@link Entity Entities}, currently get and "query". It is important to note that when a
 * PostLoadContext is provided to a callback following a get operation, {@link #getElements()}
 * returns all retrieved Entities. However, when a PostLoadContext is provided to a callback
 * following a query, a separate PostLoadContext will be constructed for each Entity in the result
 * set so {@link #getElements()} will only return a {@link List} containing a single Entity. This is
 * due to the streaming nature of query responses.
 *
 */
public final class PostLoadContext extends BaseCallbackContext<Entity> {

  PostLoadContext(CurrentTransactionProvider currentTransactionProvider, List<Entity> results) {
    super(currentTransactionProvider, results);
  }

  PostLoadContext(CurrentTransactionProvider currentTransactionProvider, Entity result) {
    this(currentTransactionProvider, Arrays.asList(result));
  }

  @Override
  String getKind(Entity entity) {
    return entity.getKind();
  }
}
