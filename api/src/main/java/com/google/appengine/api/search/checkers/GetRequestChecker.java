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

package com.google.appengine.api.search.checkers;

import com.google.appengine.api.search.proto.SearchServicePb.ListDocumentsParams;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Checks values of {@link com.google.appengine.api.search.GetRequest}.
 *
 */
public class GetRequestChecker {

  /**
   * Checks whether the number of documents to return is between 0 and the
   * maximum.
   *
   * @param limit the maximum number of documents to return in results list
   * @return the checked number of documents to return
   * @throws IllegalArgumentException if the number of documents to return
   * is out of range
   */
  public static int checkLimit(int limit) {
    Preconditions.checkArgument(
        limit >= 0 && limit <= SearchApiLimits.GET_RANGE_MAXIMUM_LIMIT,
        "The limit %s must be between 0 and %s",
        limit,
        SearchApiLimits.GET_RANGE_MAXIMUM_LIMIT);
    return limit;
  }

  /**
   * Checks whether the given start document Is legal.
   *
   * @param startDocId the start ocument Id to be checked.
   * @return the checked start document Id.
   * @throws IllegalArgumentException if the start document Id is illegal.
   */
  public static String checkStartDocId(String startDocId) {
    if (Strings.isNullOrEmpty(startDocId)) {
      return startDocId;
    }
    return DocumentChecker.checkDocumentId(startDocId);
  }

  /**
   * Checks the values of the {@link ListDocumentsParams} params.
   *
   * @param params The {@link ListDocumentsParams} to check
   * @return the checked params
   * @throws IllegalArgumentException if some of the values of params are
   * invalid
   */
  public static ListDocumentsParams checkListDocumentsParams(ListDocumentsParams params) {
    IndexChecker.checkName(params.getIndexSpec().getName());
    if (params.hasLimit()) {
      checkLimit(params.getLimit());
    }
    if (params.hasStartDocId()) {
      DocumentChecker.checkDocumentId(params.getStartDocId());
    }
    return params;
  }
}
