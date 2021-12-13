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
 * Thrown to indicate that a search service failure occurred.
 *
 */
public class SearchServiceException extends RuntimeException {
  private static final long serialVersionUID = -3400183609064651906L;

  /**
   * Constructs an exception which indicates an internal error
   * occurred in the search service.
   *
   * @param detail the error message detail to associate with the
   * internal error
   */
  public SearchServiceException(String detail) {
    super(detail);
  }

  /**
   * Constructs an exception which indicates an internal error
   * occurred in the search service.
   *
   * @param detail the error message detail to associate with the
   * internal error
   * @param e the causing {@link Exception}
   */
  public SearchServiceException(String detail, Exception e) {
    super(detail, e);
  }
}
