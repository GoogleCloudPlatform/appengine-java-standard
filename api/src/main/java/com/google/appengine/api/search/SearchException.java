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

/**
 * Thrown to indicate that a search service failure occurred while performing
 * a search request.
 *
 */
public class SearchException extends SearchBaseException {
  private static final long serialVersionUID = 3608247775865189592L;

  /**
   * Constructs an exception when some error occurred in
   * the search service when processing a search request.
   *
   * @param message the error detail associated with the failure
   */
  public SearchException(String message) {
    this(new OperationResult(StatusCode.INTERNAL_ERROR, message));
  }

  /**
   * Constructs an exception when some error occurred in
   * the search service when processing a search request.
   *
   * @param operationResult the error code and message detail associated with
   * the failure
   */
  public SearchException(OperationResult operationResult) {
    super(operationResult);
  }
}
