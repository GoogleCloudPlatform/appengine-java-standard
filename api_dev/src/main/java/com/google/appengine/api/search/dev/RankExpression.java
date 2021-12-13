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

import com.google.appengine.api.search.SearchQueryException;
import com.google.appengine.api.search.SortExpression;
import com.google.common.primitives.Doubles;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

class RankExpression extends NumericExpression {
  @Override
  public double evalDouble(Document doc) throws EvaluationException {
    Field field = doc.getField(SortExpression.RANK_FIELD_NAME);
    if (field != null) {
      Double val = Doubles.tryParse(field.stringValue());
      if (val != null) {
        return val;
      }
    }
    throw new EvaluationException(String.format("Could not determine rank from value %s", field));
  }

  @Override
  public Sorter getNumericSorter(int sign, double defaultValueNumeric) {
    if (sign >= 0) {
      return super.getNumericSorter(sign, defaultValueNumeric);
    } else {
      throw new SearchQueryException("Rank can only be used in descending-order searches");
    }
  }
}
