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

package com.google.appengine.api.datastore;

/**
 * {@code DatastoreNeedIndexException} is thrown when no matching index was found for a query
 * requiring an index. Check the Indexes page in the Admin Console and your datastore-indexes.xml
 * file.
 *
 */
public class DatastoreNeedIndexException extends RuntimeException {

  static final long serialVersionUID = 9218197931741583584L;

  // TODO: Remove the hint once we fix http://b/2330779
  static final String NO_XML_MESSAGE =
      "\nAn index is missing but we are unable to tell you which "
          + "one due to a bug in the App Engine SDK.  If your query only contains equality filters "
          + "you most likely need a composite index on all the properties referenced in those "
          + "filters.";

  String xml;

  public DatastoreNeedIndexException(String message) {
    super(message);
  }

  public DatastoreNeedIndexException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public String getMessage() {
    return super.getMessage()
        + (xml == null ? NO_XML_MESSAGE : "\nThe suggested index for this query is:\n" + xml);
  }

  /** Returns the xml defining the missing index. Can be {@code null}. */
  public String getMissingIndexDefinitionXml() {
    return xml;
  }

  void setMissingIndexDefinitionXml(String xml) {
    this.xml = xml;
  }
}
