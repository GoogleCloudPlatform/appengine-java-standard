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
 * Thrown to indicate that a search service failure occurred while deleting
 * objects.
 *
 */
public class DeleteException extends SearchBaseException {
  private static final long serialVersionUID = 4601083521504723236L;

  private final List<OperationResult> results;

  /**
   * Constructs an exception when some error occurred in
   * the search service whilst processing a delete operation.
   *
   * @param operationResult the error code and message detail associated with
   * the failure
   */
  public DeleteException(OperationResult operationResult) {
    this(operationResult, new ArrayList<OperationResult>());
  }

  /**
   * Constructs an exception when some error occurred in
   * the search service whilst processing a delete operation.
   *
   * @param operationResult the error code and message detail associated with
   * the failure
   * @param results the list of {@link OperationResult} where each result is
   * associated with the id of the object that was requested to be deleted
   */
  public DeleteException(OperationResult operationResult,
      List<OperationResult> results) {
    super(operationResult);
    this.results = Repackaged.copyIfRepackagedElseOriginal(results);
  }

  /**
   * @return the list of {@link OperationResult} where each result is
   * associated with the id of the object that was requested to be deleted
   */
  public List<OperationResult> getResults() {
    return results;
  }
}
