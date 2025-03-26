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

import java.io.Serializable;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A query projection.
 *
 * @see Query#getProjections()
 */
public abstract class Projection implements Serializable {
  private static final long serialVersionUID = -6906477812514063672L;

  // packgage private constructor
  Projection() {}

  /** Returns the name of the property this projection populates. */
  public abstract String getName();

  /** Returns the name of the original datastore property. */
  abstract String getPropertyName();

  /** Generates the projection value from the given entity values. */
  abstract @Nullable Object getValue(Map<String, @Nullable Object> values);
}
