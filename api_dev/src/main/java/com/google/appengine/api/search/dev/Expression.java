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

import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import java.util.Comparator;
import java.util.List;
import org.apache.lucene.document.Document;

/**
 * Abstract base class for all expression evaluators.
 *
 */
public abstract class Expression {

  /**
   * Evaluate the expression to field value proto for the specified document.
   */
  public abstract FieldValue eval(Document doc) throws EvaluationException;

  /** Helper function to make field value proto from specified content. */
  public static final FieldValue makeValue(ContentType type, String stringValue) {
    return FieldValue.newBuilder().setType(type).setStringValue(stringValue).build();
  }

  /** Sort class for potential multi dimensional sorting of the expression. */
  public static interface Sorter extends Comparator<Object> {

    /** Evaluate expression to intermediate value suitable for sorting. */
    public Object eval(Document doc);

    /** Sort intermediate values. */
    @Override
    public int compare(Object left, Object right);
  }

  /**
   * Get list of sort classes for the expression. Usually it contains just
   * one element, but for field expressions it can potentially return 2
   * sorters when both numeric and text fields exist with a field name.
   */
  public abstract List<Sorter> getSorters(
      int sign, double defaultValueNumeric, String defaultValueText);
}
