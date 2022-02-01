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

import com.google.appengine.api.search.query.ExpressionLexer;
import com.google.common.collect.ImmutableMap;
import java.util.function.DoubleBinaryOperator;
import org.apache.lucene.document.Document;

/** Base class for binary numeric expression evaluators. */
final class BinaryNumericExpression extends NumericExpression {
  private final DoubleBinaryOperator op;
  private final NumericExpression left;
  private final NumericExpression right;

  private BinaryNumericExpression(
      DoubleBinaryOperator op, NumericExpression left, NumericExpression right) {
    this.left = left;
    this.right = right;
    this.op = op;
  }

  @Override
  public double evalDouble(Document doc) throws EvaluationException {
    double leftValue = left.evalDouble(doc);
    double rightValue = right.evalDouble(doc);
    return op.applyAsDouble(leftValue, rightValue);
  }

  // Simple binary expressions:

  private static final ImmutableMap<Integer, DoubleBinaryOperator> BINARY_OPS =
      ImmutableMap.<Integer, DoubleBinaryOperator>builder()
          .put(ExpressionLexer.TIMES, (a, b) -> a * b)
          .put(ExpressionLexer.DIV, (a, b) -> a / b)
          .put(ExpressionLexer.PLUS, (a, b) -> a + b)
          .put(ExpressionLexer.MINUS, (a, b) -> a - b)
          .put(ExpressionLexer.POW, Math::pow)
          .put(ExpressionLexer.LT, (a, b) -> truth(a < b))
          .put(ExpressionLexer.GT, (a, b) -> truth(a > b))
          .put(ExpressionLexer.LE, (a, b) -> truth(a <= b))
          .put(ExpressionLexer.EQ, (a, b) -> truth(a == b))
          .put(ExpressionLexer.NE, (a, b) -> truth(a != b))
          .put(ExpressionLexer.GE, (a, b) -> truth(a >= b))
          .buildOrThrow();

  private static double truth(boolean b) {
    return b ? 1 : 0;
  }

  static BinaryNumericExpression make(int type, NumericExpression left, NumericExpression right) {
    return new BinaryNumericExpression(BINARY_OPS.get(type), left, right);
  }
}
