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

package com.google.appengine.api.search.dev;

import com.google.appengine.api.search.query.QueryTreeContext;
import com.google.apphosting.api.search.DocumentPb;
import java.util.Set;
import org.apache.lucene.search.Query;

/**
 * A context of a tree used to build Lucene queries.
 *
 */
class LuceneQueryTreeContext extends QueryTreeContext<LuceneQueryTreeContext>  {

  enum ComparisonOp {
    // NOTE: These operators must have the names that match
    // those of ST numeric comparison operators.
    NE, EQ, LT, LE, GT, GE, HAS;
  }

  private Query query;
  private Set<DocumentPb.FieldValue.ContentType> fieldTypes;

  /**
   * The essential text of a PHRASE value, unencumbered by quote marks.  Currently used
   * only for PHRASE Kinds, but could be used by others as necessary.
   */
  private String rawText;

  private LuceneQueryTreeContext() {
  }

  /** Creates a default context. */
  public static LuceneQueryTreeContext newRootContext() {
    return new LuceneQueryTreeContext();
  }

  public void setQuery(Query query) {
    this.query = query;
  }

  public Query getQuery() {
    return query;
  }

  public String getUnquotedText() {
    if (isPhrase()) {
      return rawText;
    }
    return getText();
  }

  public void setRawText(String rawText) {
    this.rawText = rawText;
  }

  @Override
  protected LuceneQueryTreeContext newChildContext() {
    return new LuceneQueryTreeContext();
  }

  /**
   * @return the set of possible field types
   */
  public Set<DocumentPb.FieldValue.ContentType> getFieldTypes() {
    return fieldTypes;
  }

  /**
   * @param fieldTypes the set of types the field represented by this context may have
   */
  public void setFieldTypes(Set<DocumentPb.FieldValue.ContentType> fieldTypes) {
    this.fieldTypes = fieldTypes;
  }
}
