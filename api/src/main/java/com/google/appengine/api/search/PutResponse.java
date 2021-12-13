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
 * Represents a result of putting a list of objects (documents or queries)
 * into an index. The response contains a list of {@link OperationResult}
 * indicating success or not of putting each of the objects into the index,
 * and a list of Id of the objects which are those given in the request or
 * allocated by the search service to those objects which do not have an
 * Id supplied.
 */
public class PutResponse implements Iterable<OperationResult>, Serializable {
  private static final long serialVersionUID = 3063892804095727768L;

  private final List<OperationResult> results;
  private final List<String> ids;

  /**
   * Creates a {@link PutResponse} by specifying a list of
   * {@link OperationResult} and a list of document or query ids.
   *
   * @param results a list of {@link OperationResult} that indicate the
   * success or not putting each object into the index
   * @param ids a list of Id of objects that were requested to be
   * put into the index. The search service may supply Ids for those objects
   * where none was supplied
   */
  protected PutResponse(List<OperationResult> results, List<String> ids) {
    this.results =
        Repackaged.copyIfRepackagedElseUnmodifiable(
            Preconditions.checkNotNull(results, "results cannot be null"));
    this.ids =
        Repackaged.copyIfRepackagedElseUnmodifiable(
            Preconditions.checkNotNull(ids, "ids cannot be null"));
  }

  @Override
  public Iterator<OperationResult> iterator() {
    return results.iterator();
  }

  /**
   * @return an unmodifiable list of {@link OperationResult} indicating
   * whether each {@link Document} was put or not
   */
  public List<OperationResult> getResults() {
    return results;
  }

  /**
   * @return an unmodifiable list of Ids
   */
  public List<String> getIds() {
    return ids;
  }

  @Override
  public String toString() {
    return new Util.ToStringHelper("PutResponse")
        .addIterableField("results", getResults(), 0)
        .addIterableField("ids", getIds(), 0)
        .finish();
  }
}
