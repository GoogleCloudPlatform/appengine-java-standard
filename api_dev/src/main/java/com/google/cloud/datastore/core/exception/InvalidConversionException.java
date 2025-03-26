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

import com.google.cloud.datastore.logs.ProblemCode;
import com.google.errorprone.annotations.FormatMethod;
import org.jspecify.annotations.Nullable;

/**
 * An exception that indicates a conversion error.
 *
 * <p>This exception indicates that either a feature is not available in the destination format or
 * the value in the source format is invalid (for example an opaque byte field cannot be parsed).
 *
 * <p>When a conversion is performed on an input, this exception should be treated as a bad request.
 * When a conversion is performed on an output, it should be treated as an internal error.
 */
public class InvalidConversionException extends ValidationException {
  public InvalidConversionException(String message) {
    super(message);
  }

  public InvalidConversionException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidConversionException(Throwable cause) {
    this(cause.getMessage(), cause);
  }

  public InvalidConversionException(String message, ProblemCode problemCode) {
    super(message, problemCode);
  }

  public static void checkConversion(boolean assertion, String message)
      throws InvalidConversionException {
    if (!assertion) {
      throw new InvalidConversionException(message);
    }
  }

  @FormatMethod
  public static void checkConversion(boolean assertion, String message, Object... messageArgs)
      throws InvalidConversionException {
    if (!assertion) {
      throw new InvalidConversionException(String.format(message, messageArgs));
    }
  }

  @FormatMethod
  public static void checkConversion(
      boolean assertion, ProblemCode problemCode, String message, Object... messageArgs)
      throws InvalidConversionException {
    if (!assertion) {
      throw new InvalidConversionException(String.format(message, messageArgs), problemCode);
    }
  }

  /**
   * Throws a InvalidConversionException if the assertion is false and validation is enabled. If the
   * assertion is false, validation is not enabled, and a SuppressedValidationFailure is passed in,
   * the problem code is recorded as being suppressed.
   */
  @FormatMethod
  public static void checkConversionIf(
      boolean assertion,
      ProblemCode problemCode,
      boolean validationEnabled,
      @Nullable SuppressedValidationFailures suppressedFailures,
      String message,
      Object... messageArgs)
      throws InvalidConversionException {
    if (assertion) {
      return;
    }

    if (validationEnabled) {
      throw new InvalidConversionException(String.format(message, messageArgs), problemCode);
    }
    // For now, most callers of convertEntity don't care about suppressedFailures, so we allow them
    // to just pass null and ignore the result. They can start passing in an instance if they want
    // to use it.
    if (suppressedFailures != null) {
      suppressedFailures.add(problemCode);
    }
  }

  public static InvalidConversionException unrecognizedEnumValue(String field, Enum<?> enumValue) {
    return new InvalidConversionException("Unrecognized " + field + ": " + enumValue);
  }
}
