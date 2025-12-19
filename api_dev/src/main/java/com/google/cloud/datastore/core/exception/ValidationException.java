
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

package com.google.cloud.datastore.core.exception;

import com.google.apphosting.datastore_bytes.proto2api.DatastoreV3Pb.Error.ErrorCode;
import com.google.cloud.datastore.logs.ProblemCode;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** An exception that indicates a validation failure. */
public class ValidationException extends DatastoreException {

  private static final ErrorCode DEFAULT_V3_ERROR_CODE = ErrorCode.BAD_REQUEST;

  public ValidationException(String message) {
    super(message, DEFAULT_V3_ERROR_CODE, null);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, DEFAULT_V3_ERROR_CODE, cause);
  }

  public ValidationException(String message, ErrorCode errorCode) {
    super(message, errorCode, null);
  }

  public ValidationException(String message, ProblemCode problemCode) {
    super(problemCode, message, DEFAULT_V3_ERROR_CODE);
  }

  public ValidationException(String message, ProblemCode problemCode, ErrorCode errorCode) {
    super(problemCode, message, errorCode);
  }

  public ValidationException(String message, ProblemCode problemCode, ValidationException cause) {
    super(problemCode, message, DEFAULT_V3_ERROR_CODE, cause);
  }

  /**
   * Validates an assertion.
   */
  @FormatMethod
  public static void validateAssertion(boolean assertion, String message, Object... messageArgs)
      throws ValidationException {
    if (!assertion) {
      throw new ValidationException(String.format(message, messageArgs));
    }
  }

  /** Throws a DatastoreException with an embedded ProblemCode if assertion is not true. */
  @FormatMethod
  public static void validateAssertion(
      boolean assertion, ProblemCode problemCode, String message, Object... messageArgs)
      throws ValidationException {
    if (!assertion) {
      throw new ValidationException(String.format(message, messageArgs), problemCode);
    }
  }

  /**
   * Throws a DatastoreException if the assertion is false and validation is enabled. If the
   * assertion is false and validation is not enabled, the problem code is recorded as being
   * suppressed.
   */
  @FormatMethod
  public static void validateAssertionIf(
      boolean assertion,
      ProblemCode problemCode,
      boolean validationEnabled,
      SuppressedValidationFailures suppressedFailures,
      String message,
      Object... messageArgs)
      throws ValidationException {
    if (assertion) {
      return;
    }

    if (validationEnabled) {
      throw new ValidationException(String.format(message, messageArgs), problemCode);
    }

    suppressedFailures.add(problemCode);
  }

  @FormatMethod
  public static void validatePrecondition(
      boolean precondition, String message, Object... messageArgs) throws ValidationException {
    if (!precondition) {
      throw new ValidationException(
          String.format(message, messageArgs), ErrorCode.FAILED_PRECONDITION);
    }
  }

  @FormatMethod
  public static void validatePrecondition(
      boolean precondition,
      ProblemCode problemCode,
      @FormatString String message,
      Object... messageArgs)
      throws ValidationException {
    if (!precondition) {
      throw new ValidationException(
          String.format(message, messageArgs), problemCode, ErrorCode.FAILED_PRECONDITION);
    }
  }

  // Following messages exposed for testing.

  public static final String DIFFERENT_APP_ID_MESSAGE = "mismatched app ids within request: ";

  public static final String DIFFERENT_DATABASE_MESSAGE = "mismatched databases within request: ";

  public static final String TRANSACTION_WITH_FAILOVER_MESSAGE =
      "read operations inside transactions can't allow failover";

  public static final String MISSING_APP_ID = "The app id is the empty string.";

  public static final String NEED_ANCESTOR_ERROR =
      "global queries do not support strong consistency";

  public static final String QUERIES_IN_TRANSACTION_NEED_ANCESTOR =
      "queries inside transactions must have ancestors";
}
