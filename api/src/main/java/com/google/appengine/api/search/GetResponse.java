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

package com.google.appengine.api.search;

import com.google.appengine.api.internal.Repackaged;
import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a result of executing a {@link GetRequest}. The
 * response contains a list of T.
 *
 * @param <T> The type of object to be listed from an index
 */
public class GetResponse<T> implements Iterable<T>, Serializable {
  private static final long serialVersionUID = 7050146612334976140L;

  private final List<T> results;

  /**
   * Creates a {@link GetResponse} by specifying a list of T.
   *
   * @param results a list of T returned from the index
   */
  protected GetResponse(List<T> results) {
    Preconditions.checkNotNull(results, "results cannot be null");
    this.results = Repackaged.copyIfRepackagedElseUnmodifiable(results);
  }

  @Override
  public Iterator<T> iterator() {
    return results.iterator();
  }

  /**
   * @return an unmodifiable list of T from the index
   */
  public List<T> getResults() {
    return results;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("GetResponse")
        .addIterableField("results", results, 0)
        .finish();
  }
}
