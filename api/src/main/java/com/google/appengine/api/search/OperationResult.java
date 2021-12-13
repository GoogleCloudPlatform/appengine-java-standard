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

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Objects;
import java.io.Serializable;

/**
 * The result of an operation involving the search service.
 *
 */
public class OperationResult implements Serializable {
  private static final long serialVersionUID = 3608247775865189592L;

  private final StatusCode code;
  private final String message;

  OperationResult(SearchServicePb.RequestStatus status) {
    this(status.getCode(), status.hasErrorDetail() ? status.getErrorDetail() : null);
  }

  /**
   * @param code the status code of the request extracted from proto buffers
   * @param errorDetail detailed error message or {@code null}
   */
  OperationResult(ErrorCode code, String errorDetail) {
    this(StatusCode.fromErrorCode(code), errorDetail);
  }

  /**
   * @param code the status code of the request
   * @param errorDetail detailed error message or {@code null}
   */
  public OperationResult(StatusCode code, String errorDetail) {
    this.code = code;
    this.message = errorDetail;
  }

  /**
   * @return the status code
   */
  public StatusCode getCode() {
    return code;
  }

  /**
   * @return the detailed message or {@code null}
   */
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    if (message == null) {
      return code.name();
    }
    return code.name() + ": " + message;
  }

  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    if (object == this) {
      return true;
    }
    if (!getClass().isAssignableFrom(object.getClass())) {
      return false;
    }
    OperationResult result = (OperationResult) object;
    return code.equals(result.code) && Objects.equal(message, result.message);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code, message);
  }

  /**
   * Converts a {@Throwable} into an OperationResult if it is an
   * {@link com.google.apphosting.api.ApiProxy.ApplicationException} with an error which is not OK.
   * If the error is not known, the OperationResult will default to INTERNAL_ERROR.
   */
  static OperationResult convertToOperationResult(Throwable cause) {
    if (cause instanceof ApiProxy.ApplicationException) {
      ApiProxy.ApplicationException exception = ((ApiProxy.ApplicationException) cause);
      int applicationError = exception.getApplicationError();
      if (applicationError != 0) {
        ErrorCode code = ErrorCode.forNumber(applicationError);
        if (code == null) {
          return new OperationResult(StatusCode.INTERNAL_ERROR, exception.getErrorDetail());
        } else {
          return new OperationResult(StatusCode.fromErrorCode(code), exception.getErrorDetail());
        }
      }
    }
    return null;
  }
}
