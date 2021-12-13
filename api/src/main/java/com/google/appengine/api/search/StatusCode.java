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

import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;

/**
 * Status code returned by various index operations.
 */
public enum StatusCode {
  /**
   * The last operation was successfully completed.
   */
  OK,

  /**
   * The last operation failed due to invalid, user specified parameters.
   */
  INVALID_REQUEST,

  /**
   * The last operation failed due to a transient backend error.
   */
  TRANSIENT_ERROR,

  /**
   * The last operation failed due to a internal backend error.
   */
  INTERNAL_ERROR,

  /**
   * The last operation failed due to user not having permission.
   */
  PERMISSION_DENIED_ERROR,

  /**
   * The last operation failed to finish before its deadline.
   */
  TIMEOUT_ERROR,

  /**
   * The last operation failed due to conflicting operations.
   */
  CONCURRENT_TRANSACTION_ERROR;

  /**
   * Provides a mapping from protocol buffer enum to user API enum.
   *
   * @param code the proto buffer enum
   * @return the corresponding user API enum
   */
  static StatusCode fromErrorCode(ErrorCode code) {
    switch (code) {
      case OK: return StatusCode.OK;
      case INVALID_REQUEST: return StatusCode.INVALID_REQUEST;
      case TRANSIENT_ERROR: return StatusCode.TRANSIENT_ERROR;
      case INTERNAL_ERROR: return StatusCode.INTERNAL_ERROR;
      case PERMISSION_DENIED: return StatusCode.PERMISSION_DENIED_ERROR;
      case TIMEOUT: return StatusCode.TIMEOUT_ERROR;
      case CONCURRENT_TRANSACTION: return StatusCode.CONCURRENT_TRANSACTION_ERROR;
    }
    throw new IllegalArgumentException("Failed to convert error code to status enum " + code);
  }
}
