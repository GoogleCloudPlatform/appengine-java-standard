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

import static com.google.appengine.api.search.checkers.DocumentChecker.mandatoryCheckValid;

import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * This class performs {@link DocumentPb.Document} validity checks for search customers that only
 * use the query API.
 */
public class QueryOnlyCustomerDocumentChecker {

  /**
   * Checks whether a {@link DocumentPb.Document} has a valid set of fields for clients that only
   * use the search query API.
   *
   * @param pb the {@link DocumentPb.Document} protocol buffer to check.
   * @throws IllegalArgumentException if the document is invalid.
   */
  public static void checkValid(DocumentPb.Document pb) {
    Preconditions.checkArgument(pb.hasId(), "Document id is not specified");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pb.getId()), "Document id is null or empty");
    mandatoryCheckValid(pb);
  }

}
