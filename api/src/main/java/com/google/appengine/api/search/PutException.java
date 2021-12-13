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
import java.util.ArrayList;
import java.util.List;

/**
 * Thrown to indicate that a search service failure occurred while putting
 * objects into the index.
 *
 */
public class PutException extends SearchBaseException {
  private static final long serialVersionUID = 497939213658736428L;

  private final List<OperationResult> results;
  private final List<String> ids;

  /**
   * Constructs an exception when some error occurred in
   * the search service when putting some objects into the index.
   *
   * @param operationResult the error code and message detail associated with
   * the failure
   */
  public PutException(OperationResult operationResult) {
    this(operationResult, new ArrayList<OperationResult>(), new ArrayList<String>());
  }

  /**
   * Constructs an exception when some error occurred in
   * the search service when putting some objects to the index.
   *
   * @param operationResult the error code and message detail associated with
   * the failure
   * @param results the list of {@link OperationResult} where each result is
   * associated with an object that was requested to be put into the index
   * @param ids the list of Ids of the object requested to be
   * put into the index. The search service may provide an Id if none was given
   * for an object
   */
  public PutException(OperationResult operationResult,
      List<OperationResult> results, List<String> ids) {
    super(operationResult);
    this.results = Repackaged.copyIfRepackagedElseOriginal(results);
    this.ids = Repackaged.copyIfRepackagedElseOriginal(ids);
  }

  /**
   * @return the list of {@link OperationResult} where each result is
   * associated with a request to be put an object into the index
   */
  public List<OperationResult> getResults() {
    return results;
  }

  /**
   * @return the list of Ids of objects that were requested to be put into
   * the index
   */
  public List<String> getIds() {
    return ids;
  }
}
