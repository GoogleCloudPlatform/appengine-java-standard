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

import com.google.appengine.api.search.DateUtil;
import java.util.Date;

/**
 * Contains limits on field values, document sizes, and other properties of objects in the Search
 * API.
 */
public class SearchApiLimits {

  /**
   * The pattern each document field name should match.
   */
  public static final String FIELD_NAME_PATTERN = "^[A-Za-z][A-Za-z0-9_]*$";

  /**
   * The default number of indexes we will try to return.
   */
  public static final int GET_INDEXES_DEFAULT_LIMIT = 20;

  /**
   * The maximum number of indexes that can be requested to be returned in
   * list indexes results.
   */
  public static final int GET_INDEXES_MAXIMUM_LIMIT = 1000;

  /**
   * The maximum offset of the first index returned in list indexes results.
   */
  public static final int GET_INDEXES_MAXIMUM_OFFSET = 1000;

  /**
   * The default number of documents requested for get range requests.
   */
  public static final int GET_RANGE_DEFAULT_LIMIT = 100;

  /**
   * The maximum number of documents which can be requested for get range requests.
   */
  public static final int GET_RANGE_MAXIMUM_LIMIT = 1000;

  /**
   * The maximum length of an atom in bytes (500).
   */
  public static final int MAXIMUM_ATOM_LENGTH = 500;

  /**
   * The maximum length of a prefix field in bytes (500).
   */
  public static final int MAXIMUM_PREFIX_LENGTH = 500;

  /**
   * The maximum length of a cursor.
   */
  public static final int MAXIMUM_CURSOR_LENGTH = 10000;

  /**
   * The maximum date that can be stored in a date field.
   */
  public static final Date MAXIMUM_DATE_VALUE =
      DateUtil.getEpochPlusDays(
          Integer.MAX_VALUE, DateUtil.MILLISECONDS_IN_DAY - 1);

  /**
   * The maximum length of a document id.
   */
  public static final int MAXIMUM_DOCUMENT_ID_LENGTH = 500;

  /**
   * The maximum length of a document.
   */
  public static final int MAXIMUM_DOCUMENT_LENGTH = 1 << 20; // 1 MB

  /**
   * The maximum length for an index name.
   */
  public static final int MAXIMUM_INDEX_NAME_LENGTH = 100;

  /**
   * The maximum length of a field or facet name in bytes (500).
   */
  public static final int MAXIMUM_NAME_LENGTH = 500;

  /**
   * The maximum negative degrees for latitude.
   */
  public static final double MAXIMUM_NEGATIVE_LATITUDE = -90.0;

  /**
   * The maximum negative degrees for longitude.
   */
  public static final double MAXIMUM_NEGATIVE_LONGITUDE = -180.0;

  /**
   * The maximum value that can be stored in a number field  (2147483647).
   */
  public static final double MAXIMUM_NUMBER_VALUE = 2147483647;

  /**
   * The maximum positive degrees for latitude.
   */
  public static final double MAXIMUM_POSITIVE_LATITUDE = 90.0;

  /**
   * The maximum positive degrees for longitude.
   */
  public static final double MAXIMUM_POSITIVE_LONGITUDE = 180.0;

  /**
   * The maximum length of a query string.
   */
  public static final int MAXIMUM_QUERY_LENGTH = 2000;

  /**
   * The maximum length of a text or HTML in bytes (1048576).
   */
  public static final int MAXIMUM_TEXT_LENGTH = 1024 * 1024;

  /**
   * The minimum date that can be stored in a date field.
   */
  public static final Date MINIMUM_DATE_VALUE =
      DateUtil.getEpochPlusDays(Integer.MIN_VALUE, 0);

  /**
   * The minimum value that can be stored in a number field  (-2147483647).
   */
  public static final double MINIMUM_NUMBER_VALUE = -2147483647;

  /**
   * The maximum number of documents allowed per index and delete request.
   */
  public static final int PUT_MAXIMUM_DOCS_PER_REQUEST = 200;

  /**
   * The default limit on the number of documents to return in results.
   */
  public static final int SEARCH_DEFAULT_LIMIT = 20;

  /**
   * The default found count accuracy is the limit.
   */
  public static final int SEARCH_DEFAULT_NUMBER_FOUND_ACCURACY = -1;

  /**
   * The default number of documents to score.
   */
  public static final int SEARCH_DEFAULT_SORTED_LIMIT = 1000;

  /**
   * The maximum number of documents that can be requested
   * to be returned in search results.
   */
  public static final int SEARCH_MAXIMUM_LIMIT = 1000;

  /**
   * The maximum number found accuracy that can be requested.
   */
  public static final int SEARCH_MAXIMUM_NUMBER_FOUND_ACCURACY = 25000;

  /**
   * The maximum number of names of fields to return in results.
   */
  public static final int SEARCH_MAXIMUM_NUMBER_OF_FIELDS_TO_RETURN = 1000;

  /**
   * The maximum offset into all search results to return results from.
   */
  public static final int SEARCH_MAXIMUM_OFFSET = 1000;

  /**
   * The maximum number of documents that can be requested to be scored.
   */
  public static final int SEARCH_MAXIMUM_SORTED_LIMIT = 10000;

  /**
   * The maximum number of facet discoveries that can be requested.
   */
  public static final int FACET_MAXIMUM_DISCOVERY_LIMIT = 100;

  /**
   * The default value for number of discovered facets.
   */
  public static final int FACET_DEFAULT_DISCOVERY_LIMIT = 10;

  /**
   * The maximum number for facet depth option.
   */
  public static final int FACET_MAXIMUM_DEPTH = 10000;

  /** The maximum value for facet value limit option. */
  public static final int FACET_MAXIMUM_VALUE_LIMIT = 100;

  /**
   * The maximum number of facet value constraint can be requested.
   */
  public static final int FACET_MAXIMUM_CONSTRAINTS = 50;

  /**
   * The maximum number of facet ranges can be requested.
   */
  public static final int FACET_MAXIMUM_RANGES = 50;

  /**
   * The maximum size of a vector field.
   */
  public static final int VECTOR_FIELD_MAX_SIZE = 10000;

  /**
   * This class cannot be instantiated.
   */
  private SearchApiLimits() {
  }
}
