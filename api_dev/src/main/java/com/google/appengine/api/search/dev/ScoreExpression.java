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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;

/** Expression which evaluates to the score of a document.
 *
 * For now, this is a stub that always returns zero. Changing this to return
 * the actual score would require a fairly significant rearchitecture of the
 * expression evaluation in the dev server, which may come later.
 */
class ScoreExpression extends NumericExpression {
  static final Logger LOG = Logger.getLogger(ScoreExpression.class.getCanonicalName());

  ScoreExpression() {
  }

  public static ScoreExpression makeScoreExpression() {
    return new ScoreExpression();
  }

  @Override
  public double evalDouble(Document doc) throws EvaluationException {
    LOG.info(
        "Score expressions are not supported on the Java dev server; " +
        "_score in expressions will evaluate to zero.");
    return 0.;
  }

  @Override
  public List<Sorter> getSorters(
      final int sign, double defaultValueNumeric, final String defaultValueText) {
    List<Sorter> sorters = new ArrayList<Sorter>(1);
    sorters.add(getNumericSorter(sign, defaultValueNumeric));
    return sorters;
  }
}
