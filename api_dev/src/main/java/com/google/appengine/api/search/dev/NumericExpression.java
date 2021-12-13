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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;

/**
 * Base class for all Expressions returning numeric value.
 *
 */
abstract class NumericExpression extends Expression {
  @Override
  public FieldValue eval(Document doc) throws EvaluationException {
    return makeValue(ContentType.NUMBER, Double.toString(evalDouble(doc)));
  }

  /**
   * Evaluate double value from specified document.
   */
  public abstract double evalDouble(Document doc) throws EvaluationException;

  @Override
  public List<Sorter> getSorters(
      final int sign, final double defaultValueNumeric, String defaultValueText) {
    List<Sorter> sorters = new ArrayList<Sorter>(1);
    sorters.add(getNumericSorter(sign, defaultValueNumeric));
    return sorters;
  }

  public Sorter getNumericSorter(final int sign, final double defaultValueNumeric) {
    return new Sorter() {
      @Override
      public Object eval(Document doc) {
        try {
          return evalDouble(doc);
        } catch (EvaluationException e) {
          return defaultValueNumeric;
        }
      }

      @Override
      public int compare(Object left, Object right) {
        Double leftDouble = (Double) left;
        Double rightDouble = (Double) right;
        return sign * leftDouble.compareTo(rightDouble);
      }
    };
  }
}
