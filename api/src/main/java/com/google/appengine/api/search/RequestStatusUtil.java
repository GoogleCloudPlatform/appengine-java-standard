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
import com.google.appengine.api.search.proto.SearchServicePb.RequestStatus;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.apphosting.api.AppEngineInternal;
import com.google.apphosting.base.protos.Codes.Code;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Collection of utility methods for SearchServicePb.RequestStatus.
 */
@AppEngineInternal
public final class RequestStatusUtil {

  /**
   * Mapping of search service error to general Canonical Errors.
   */
  private static final ImmutableMap<SearchServicePb.SearchServiceError.ErrorCode, Code>
      REQUEST_STATUS_TO_CANONICAL_ERROR_MAPPING =
      ImmutableMap.<SearchServicePb.SearchServiceError.ErrorCode, Code>builder()
      .put(SearchServicePb.SearchServiceError.ErrorCode.OK, Code.OK)
      .put(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, Code.INVALID_ARGUMENT)
      .put(SearchServicePb.SearchServiceError.ErrorCode.TRANSIENT_ERROR, Code.UNAVAILABLE)
      .put(SearchServicePb.SearchServiceError.ErrorCode.INTERNAL_ERROR, Code.INTERNAL)
      .put(SearchServicePb.SearchServiceError.ErrorCode.PERMISSION_DENIED, Code.PERMISSION_DENIED)
      .put(SearchServicePb.SearchServiceError.ErrorCode.TIMEOUT, Code.DEADLINE_EXCEEDED)
      .put(SearchServicePb.SearchServiceError.ErrorCode.CONCURRENT_TRANSACTION, Code.ABORTED)
      .build();

  /**
   * Converts SearchServicePb.SearchServiceError.ErrorCode to canonical error code.
   */
  public static Code toCanonicalCode(SearchServicePb.SearchServiceError.ErrorCode appCode) {
    return Preconditions.checkNotNull(REQUEST_STATUS_TO_CANONICAL_ERROR_MAPPING.get(appCode));
  }

  /**
   * Creates a SearchServicePb.RequestStatus.Builder from the given code and message.
   */
  public static SearchServicePb.RequestStatus.Builder newStatusBuilder(
      SearchServicePb.SearchServiceError.ErrorCode code, String message) {
    SearchServicePb.RequestStatus.Builder builder = SearchServicePb.RequestStatus.newBuilder();
    builder.setCode(code).setCanonicalCode(toCanonicalCode(code).getNumber());
    if (message != null) {
      builder.setErrorDetail(message);
    }
    return builder;
  }

  /**
   * Creates a SearchServicePb.RequestStatus from the given code and message.
   */
  public static SearchServicePb.RequestStatus newStatus(
      SearchServicePb.SearchServiceError.ErrorCode code, String message) {
    return newStatusBuilder(code, message).build();
  }

  /**
   * Creates a SearchServicePb.RequestStatus from the given code.
   */
  public static SearchServicePb.RequestStatus newStatus(
      SearchServicePb.SearchServiceError.ErrorCode code) {
    return newStatusBuilder(code, null).build();
  }

  /**
   * Creates a RequestStatus message suitable for reporting an invalid request.
   */
  public static SearchServicePb.RequestStatus newInvalidRequestStatus(IllegalArgumentException e) {
    Preconditions.checkNotNull(e.getMessage());
    return newStatus(SearchServicePb.SearchServiceError.ErrorCode.INVALID_REQUEST, e.getMessage());
  }

  /**
   * Creates a RequestStatus message suitable for reporting an unknown index. We use
   * {@link SearchServicePb.SearchServiceError.ErrorCode#OK} because the unknown index isn't
   * an error condition but just a notice to the user.
   */
  public static SearchServicePb.RequestStatus newUnknownIndexStatus(
      SearchServicePb.IndexSpec indexSpec) {
    return newStatus(SearchServicePb.SearchServiceError.ErrorCode.OK, String.format(
        "Index '%s' in namespace '%s' does not exist",
        indexSpec.getName(), indexSpec.getNamespace()));
  }

  /**
   * For a 'batch' request, determines a single status to stand for all. Code will be OK if and only
   * if the collection contains no non-OK statuses.
   *
   * <p>If the collection is empty, the result will be an OK status with no detail.
   *
   * <p>If the collection has one element, the result will be that element.
   *
   * <p>If the collection has multiple elements with the same error code, the result will have that
   * error code and the corresponding canonical code.
   *
   * <p>If there are multiple error statuses in the collection, the one with the highest numerical
   * code in the ErrorCode enum will be chosen as representative, and the errorDetail field of the
   * result will contain at least the errorDetail of that status.
   */
  public static RequestStatus reduce(Collection<RequestStatus> statuses) {
    if (statuses.isEmpty()) {
      return newStatus(ErrorCode.OK);
    } else if (statuses.size() == 1) {
      return Iterables.getOnlyElement(statuses);
    } else {
      ErrorCode errorCode = ErrorCode.OK;
      Set<String> errorDetails = null;
      for (RequestStatus status : statuses) {
        if (status.getCode() != ErrorCode.OK) {
          if (errorDetails == null) {
            errorDetails = new HashSet<>();
          }
          errorDetails.add(status.getErrorDetail());
          if (status.getCode().getNumber() > errorCode.getNumber()) {
            errorCode = status.getCode();
          }
        }
      }
      if (errorDetails == null) {
        return newStatus(errorCode);
      } else {
        return newStatus(errorCode, Joiner.on("; ").join(errorDetails));
      }
    }
  }
}
