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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Concrete {@link CallbackContext} implementation that is specific to intercepted queries. Methods
 * annotated with {@link PreQuery} that receive instances of this class may modify the {@link Query}
 * returned by calling {@link #getCurrentElement()}. This is an effective way to modify queries
 * prior to execution.
 *
 */
// TODO: Allow users to add entities to the result set and have them merged
// in as the results are streamed back.
public final class PreQueryContext extends BaseCallbackContext<Query> {

  PreQueryContext(CurrentTransactionProvider currentTransactionProvider, Query query) {
    // Pass in a copy of the provided query so that hooks can mutate without
    // side effects.
    super(currentTransactionProvider, Arrays.asList(new Query(query)));
  }

  @Override
  @Nullable
  String getKind(Query query) {
    return query.getKind();
  }
}
