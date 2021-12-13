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

package com.google.cloud.datastore.core.names;

import com.google.common.collect.ImmutableSet;

// TODO: Move the property name/path stuff to its own class.

/** Constants describing kinds and property names and property paths. */
public class Kinds {

  private Kinds() {}

  public static final String KEY_NAME = "__key__";

  // Special kind for which no entity can exist
  public static final String NONE_KIND_NAME = "__none__";
  public static final String BLOBINFO_KIND_NAME = "__BlobInfo__";
  public static final String KIND_KIND_NAME = "__kind__";
  public static final String PROPERTY_KIND_NAME = "__property__";
  public static final String NAMESPACE_KIND_NAME = "__namespace__";
  public static final String COLLECTION_KIND_NAME = "__collection__";
  public static final String DOCUMENT_KIND_NAME = "__document__";
  public static final String STATS_PREFIX = "__Stat";

  // Property names, and paths for pseudo-kinds
  public static final String DOCUMENT_KIND_DOCUMENT_KEY_PROPERTY_NAME_STRING = "document_key";
  public static final String DOCUMENT_KIND_DOCUMENT_FIELDS_PROPERTY_NAME_STRING = "document";
  public static final String DOCUMENT_KIND_CREATED_VERSION_PROPERTY_NAME_STRING = "created";
  public static final String DOCUMENT_KIND_UPDATED_VERSION_PROPERTY_NAME_STRING = "updated";
  public static final ImmutableSet<String> STATS_KIND_NAMES =
      ImmutableSet.of(
          "__Stat_Total__",
          "__Stat_Namespace__",
          "__Stat_Kind_CompositeIndex__",
          "__Stat_Kind__",
          "__Stat_Kind_IsRootEntity__",
          "__Stat_Kind_NotRootEntity__",
          "__Stat_PropertyType__",
          "__Stat_PropertyType_Kind__",
          "__Stat_PropertyName_Kind__",
          "__Stat_PropertyType_PropertyName_Kind__",
          "__Stat_Ns_Total__",
          "__Stat_Ns_Kind_CompositeIndex__",
          "__Stat_Ns_Kind__",
          "__Stat_Ns_Kind_IsRootEntity__",
          "__Stat_Ns_Kind_NotRootEntity__",
          "__Stat_Ns_PropertyType__",
          "__Stat_Ns_PropertyType_Kind__",
          "__Stat_Ns_PropertyName_Kind__",
          "__Stat_Ns_PropertyType_PropertyName_Kind__");
  // Special value to represent all namespaces.  Used for queries across an entire dataset.
  public static final String ALL_NAMESPACES = "__all__";
  // Delimiters
  public static final String PROPERTY_PATH_DELIMITER = ".";
  /** Basically {@code ".__key__"}. */
  public static final String PROPERTY_PATH_DELIMITER_AND_KEY_SUFFIX =
      PROPERTY_PATH_DELIMITER + KEY_NAME;

  public static final String PROPERTY_PATH_DELIMITER_REGEX = "\\.";
  public static final byte PROPERTY_PATH_DELIMITER_BYTE = '.';
  public static final char PROPERTY_PATH_DELIMITER_CHAR = (char) PROPERTY_PATH_DELIMITER_BYTE;
  public static final int PROPERTY_PATH_DELIMITER_CHAR_NUM_UTF8_BYTES = 1;
  public static final byte PROPERTY_PATH_ESCAPE_BYTE = '\\';
  public static final char PROPERTY_PATH_ESCAPE_CHAR = (char) PROPERTY_PATH_ESCAPE_BYTE;
}
