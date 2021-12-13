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

import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.query.ExpressionLexer;
import com.google.appengine.api.search.query.ExpressionParser;
import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.collect.ImmutableList;
import com.google.common.geometry.S2LatLng;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.tree.Tree;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

/** Builder class for construction Expression objects used to evaluate expressions per document. */
public class ExpressionBuilder {
  private static final Logger log = Logger.getLogger(ExpressionBuilder.class.getName());

  private final Map<String, Set<ContentType>> fieldTypes;

  public ExpressionBuilder(Map<String, Set<ContentType>> fieldTypes) {
    this.fieldTypes = fieldTypes;
  }

  /**
   * Constructs Expression object given string representation.
   */
  public Expression parse(String expr) {
    if (expr == null) {
      // This should never happen, as we get the
      // expr string from a required protocol buffer field.
      throw new IllegalArgumentException("Unexpected null expression");
    }
    String trimmed = expr.trim();
    if (trimmed.isEmpty()) {
      return new EmptyExpression();
    }
    ANTLRStringStream stream = new ANTLRStringStream(expr);
    ExpressionLexer lexer = new ExpressionLexer(stream);
    TokenRewriteStream tokens = new TokenRewriteStream(lexer);
    ExpressionParser parser = new ExpressionParser(tokens);

    Tree tree;
    try {
      tree = (Tree) parser.expression().getTree();
    } catch (RecognitionException cause) {
      String message = String.format("parse error at line %d position %d",
          cause.line, cause.charPositionInLine);
      throw new IllegalArgumentException(message);
    }

    if (!tree.isNil()) {
      throw new IllegalArgumentException("AST is missing nil root " + expr);
    }

    // Uncomment to dump expression tree:
    // print(0, "Tree for " + expr);
    // dumpTree(tree.getChild(0), 0);
    return makeExpression(tree.getChild(0));
  }

  // for tree debugging
  private void print(int offset, String msg) {
    for (int i = 0; i < offset; i++) {
      System.err.print(" ");
    }
    System.err.println(msg);
  }

  // for tree debugging
  @SuppressWarnings("unused")
  private void dumpTree(Tree tree, int offset) {
    print(offset, String.format("%s", getTokenName(tree.getType())));
    if (!tree.getText().isEmpty()) {
      print(offset + 2, String.format("TEXT: %s", tree.getText()));
    }
    for (int i = 0; i < tree.getChildCount(); ++i) {
      print(offset, String.format("%s[%d]", getTokenName(tree.getType()), i));
      dumpTree(tree.getChild(i), offset + 2);
    }
  }

  /** Empty Expression, which is evaluated if the input string was empty. */
  public static class EmptyExpression extends Expression {
    @Override
    public FieldValue eval(Document doc) throws EvaluationException {
      throw new EvaluationException("empty expression");
    }

    @Override
    public List<Expression.Sorter> getSorters(int sign, double dfltD, String dfltT) {
      return new ArrayList<Expression.Sorter>();
    }
  }

  /** Expression which counts number of fields in document. */
  private static class CountFieldsFunction extends NumericExpression {
    private final List<String> luceneFieldNames;
    private final String fieldName;

    CountFieldsFunction(List<String> luceneFieldNames, String fieldName) {
      this.luceneFieldNames = luceneFieldNames;
      this.fieldName = fieldName;
    }

    @Override
    public double evalDouble(Document doc) {
      int result = 0;
      for (String fieldName : luceneFieldNames) {
        result += doc.getFields(fieldName).length;
      }
      return result;
    }

    @Override
    public List<Sorter> getSorters(
      int sign, double defaultValueNumeric, String defaultValueText) {
      throw new SearchException("Failed to parse sort expression \'count(" + fieldName + ")\': " +
          "count() is not supported in sort expressions");
    }
  }

  /** Expression which evaluates to numeric constant. */
  public static class IntValueExpression extends NumericExpression {
    private final Double value;

    IntValueExpression(double value) {
      this.value = value;
    }

    @Override
    public double evalDouble(Document doc) {
      return value;
    }
  }

  /** Expression which negates input expression. */
  private static class NegExpression extends NumericExpression {
    private final NumericExpression input;

    NegExpression(NumericExpression input) {
      this.input = input;
    }

    @Override
    public double evalDouble(Document doc) throws EvaluationException {
      return -input.evalDouble(doc);
    }
  }

  private String getText(Tree tree) {
    if (tree.getType() == ExpressionLexer.NAME) {
      return tree.getText();
    }
    if (tree.getType() == ExpressionLexer.PHRASE) {
      String text = tree.getText();
      // Strip quotes, TODO: implement proper unescape once Expression.g
      // grammar allows escaped quotes
      return text.substring(1, text.length() - 1);
    }
    throw new IllegalArgumentException(
        "text expression expected instead of " + getTokenName(tree.getType()));
  }

  private CountFieldsFunction makeCountFieldsFunction(Tree tree) {
    if (tree.getChildCount() != 1) {
      throw new IllegalArgumentException("count() requires exactly 1 argument");
    }
    Tree field = tree.getChild(0);
    if (field.getType() != ExpressionLexer.NAME) {
      throw new IllegalArgumentException("Field name expected");
    }
    String fieldName = field.getText();
    Set<ContentType> types = fieldTypes.get(fieldName);
    List<String> luceneFieldNames;
    if (types == null) {
      luceneFieldNames = ImmutableList.of();
    } else {
      luceneFieldNames = new ArrayList<String>(types.size());
      for (ContentType type : types) {
        luceneFieldNames.add(LuceneUtils.makeLuceneFieldName(fieldName, type));
      }
    }
    return new CountFieldsFunction(luceneFieldNames, fieldName);
  }

  private NumericExpression makeAbsoluteValueFunction(Tree tree) {
    if (tree.getChildCount() != 1) {
      throw new IllegalArgumentException("abs() requires exactly 1 argument");
    }
    final NumericExpression arg = makeNumericExpression(tree.getChild(0));
    return new NumericExpression() {
      @Override
      public double evalDouble(Document doc) throws EvaluationException {
        return Math.abs(arg.evalDouble(doc));
      }
    };
  }

  private NumericExpression makeLogFunction(Tree tree) {
    if (tree.getChildCount() != 1) {
      throw new IllegalArgumentException("log() requires exactly 1 argument");
    }
    final NumericExpression arg = makeNumericExpression(tree.getChild(0));
    return new NumericExpression() {
      @Override
      public double evalDouble(Document doc) throws EvaluationException {
        return Math.log(arg.evalDouble(doc));
      }
    };
  }

  private NumericExpression makeMinFunction(Tree tree) {
    final int n = tree.getChildCount();
    if (n < 2) {
      throw new IllegalArgumentException("min() requires at least 2 arguments");
    }
    final NumericExpression[] args = new NumericExpression[n];
    for (int i = 0; i < n; i++) {
      args[i] = makeNumericExpression(tree.getChild(i));
    }
    return new NumericExpression() {
      @Override
      public double evalDouble(Document doc) throws EvaluationException {
        double value = args[0].evalDouble(doc);
        for (int i = 1; i < n; i++) {
          value = Math.min(value, args[i].evalDouble(doc));
        }
        return value;
      }
    };
  }

  private NumericExpression makeMaxFunction(Tree tree) {
    final int n = tree.getChildCount();
    if (n < 2) {
      throw new IllegalArgumentException("max() requires at least 2 arguments");
    }
    final NumericExpression[] args = new NumericExpression[n];
    for (int i = 0; i < n; i++) {
      args[i] = makeNumericExpression(tree.getChild(i));
    }
    return new NumericExpression() {
      @Override
      public double evalDouble(Document doc) throws EvaluationException {
        double value = args[0].evalDouble(doc);
        for (int i = 1; i < n; i++) {
          value = Math.max(value, args[i].evalDouble(doc));
        }
        return value;
      }
    };
  }

  private NumericExpression makeDistanceFunction(Tree tree) {
    if (tree.getChildCount() != 2) {
      throw new IllegalArgumentException("distance() requires exactly 2 arguments");
    }
    final Expression arg0;
    final Expression arg1;
    try {
      arg0 = makeGeoExpression(tree.getChild(0));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("distance argument 1 must be geo", e);
    }
    try {
      arg1 = makeGeoExpression(tree.getChild(1));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("distance argument 2 must be geo", e);
    }
    return new NumericExpression() {
      @Override
      public double evalDouble(Document doc) throws EvaluationException {
        FieldValue value0 = arg0.eval(doc);
        FieldValue value1 = arg1.eval(doc);
        S2LatLng p0 = S2LatLng.fromDegrees(value0.getGeo().getLat(), value0.getGeo().getLng());
        S2LatLng p1 = S2LatLng.fromDegrees(value1.getGeo().getLat(), value1.getGeo().getLng());
        return p0.getDistance(p1).radians() * GeometricField.EARTH_RADIUS_METERS;
      }
    };
  }

  private Expression makeSnippetFunction(Tree tree) {
    int nchildren = tree.getChildCount();

    if (nchildren < 2) {
      throw new IllegalArgumentException("Missing required arguments: query and fieldName");
    }
    String query = getText(tree.getChild(0));
    Tree field = tree.getChild(1);
    if (field.getType() != ExpressionLexer.NAME) {
      throw new IllegalArgumentException("Field name expected");
    }
    String fieldName = field.getText();
    Set<ContentType> types = fieldTypes.get(fieldName);
    if (types == null) {
      throw new IllegalArgumentException("Unknown field: " + fieldName);
    }
    NumericExpression maxCharsExpression;
    NumericExpression maxSnippetsExpression;
    if (nchildren < 3) {
      maxCharsExpression = new IntValueExpression(160.);
    } else {
      maxCharsExpression = makeNumericExpression(tree.getChild(2));
    }
    if (nchildren < 4) {
      maxSnippetsExpression = new IntValueExpression(3.);
    } else {
      maxSnippetsExpression = makeNumericExpression(tree.getChild(3));
    }
    return SnippetExpression.makeSnippetExpression(
        query, fieldName, types, maxCharsExpression, maxSnippetsExpression);
  }

  /**
   * @return string token name for token type. For debugging.
   */
  static String getTokenName(int tokenType) {
    return (new ExpressionParser(null)).getTokenNames()[tokenType];
  }

  private BinaryNumericExpression makeNumericBinaryExpression(Tree tree) {
    return BinaryNumericExpression.make(
        tree.getType(),
        makeNumericExpression(tree.getChild(0)),
        makeNumericExpression(tree.getChild(1)));
  }

  /**
   * Constructs typed, numeric expression. Invoked on constructing expressions
   * which require numeric arguments as input.
   */
  private NumericExpression makeNumericExpression(Tree tree) {
    if (tree == null) {
      throw new IllegalArgumentException("Unexpected null node encountered");
    }

    switch (tree.getType()) {
      case ExpressionLexer.COUNT:
        return makeCountFieldsFunction(tree);
      case ExpressionLexer.ABS:
        return makeAbsoluteValueFunction(tree);
      case ExpressionLexer.DISTANCE:
        return makeDistanceFunction(tree);
      case ExpressionLexer.LOG:
        return makeLogFunction(tree);
      case ExpressionLexer.MAX:
        return makeMaxFunction(tree);
      case ExpressionLexer.MIN:
        return makeMinFunction(tree);
      case ExpressionLexer.SWITCH:
        throw new IllegalArgumentException("Function " + tree.getText() + " not yet implemented");
      case ExpressionLexer.GEOPOINT:
      case ExpressionLexer.SNIPPET:
        throw new IllegalArgumentException(
            "Function " + tree.getText() + " does not return numeric value");
      case ExpressionLexer.TIMES:
      case ExpressionLexer.DIV:
      case ExpressionLexer.PLUS:
      case ExpressionLexer.MINUS:
      case ExpressionLexer.POW:
      case ExpressionLexer.LT:
      case ExpressionLexer.GT:
      case ExpressionLexer.LE:
      case ExpressionLexer.EQ:
      case ExpressionLexer.NE:
      case ExpressionLexer.GE:
        return makeNumericBinaryExpression(tree);

      case ExpressionLexer.INT:
      case ExpressionLexer.FLOAT:
        return makeNumericValueExpression(tree);

      case ExpressionLexer.NEG:
        return new NegExpression(makeNumericExpression(tree.getChild(0)));

      case ExpressionLexer.NAME:
        if (SortExpression.SCORE_FIELD_NAME.equals(tree.getText())) {
          return new ScoreExpression();
        }
        FieldExpression e = FieldExpression.makeFieldExpression(
            tree.getText(), fieldTypes.get(tree.getText()));
        e.checkType(ContentType.NUMBER);
        return e;

      default:
         throw new IllegalArgumentException(
             "Not yet implemented or unexpected: " + getTokenName(tree.getType()));
    }
  }

  private IntValueExpression makeNumericValueExpression(Tree tree) {
    String value = tree.getText();
    try {
      return new IntValueExpression(Double.parseDouble(value));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Wrong number format: " + value);
    }
  }

  private Expression makeGeopointFunction(Tree tree) {
    if (tree.getChildCount() != 2) {
      throw new IllegalArgumentException("geopoint() requires exactly 2 arguments");
    }
    final NumericExpression lat = makeNumericExpression(tree.getChild(0));
    final NumericExpression lng = makeNumericExpression(tree.getChild(1));
    return new Expression() {
      @Override
      public FieldValue eval(Document doc) throws EvaluationException {
        FieldValue.Builder b = FieldValue.newBuilder().setType(ContentType.GEO);
        b.getGeoBuilder().setLat(lat.evalDouble(doc));
        b.getGeoBuilder().setLng(lng.evalDouble(doc));
        return b.build();
      }

      @Override
      public List<Sorter> getSorters(
          int sign, double defaultValueNumeric, String defaultValueText) {
        throw new SearchException("geopoint() is not supported in sort expressions");
      }
    };
  }

  private Expression makeGeoFieldExpression(Tree tree) {
    final String fieldName = tree.getText();
    final String luceneFieldName = LuceneUtils.makeLuceneFieldName(fieldName, ContentType.GEO);
    return new Expression() {
      @Override
      public FieldValue eval(Document doc) throws EvaluationException {
        Field[] fields = doc.getFields(luceneFieldName);
        if (fields.length == 0) {
          throw new EvaluationException("geo field was not found");
        }
        double[] value = (double[]) LuceneUtils.luceneFieldToValue(fields[0], ContentType.GEO);
        FieldValue.Builder b = FieldValue.newBuilder().setType(ContentType.GEO);
        b.getGeoBuilder().setLat(value[0]);
        b.getGeoBuilder().setLng(value[1]);
        return b.build();
      }

      @Override
      public List<Sorter> getSorters(
          int sign, double defaultValueNumeric, String defaultValueText) {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Constructs typed, geo expression. Invoked for constructing expressions
   * which require geo arguments as input.
   */
  private Expression makeGeoExpression(Tree tree) {
    switch (tree.getType()) {
      case ExpressionLexer.GEOPOINT:
        return makeGeopointFunction(tree);
      case ExpressionLexer.NAME:
        return makeGeoFieldExpression(tree);
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Constructed untyped expression. Invoked when constructing expressions
   * which type cannot be inferred.
   */
  private Expression makeExpression(Tree tree) {
    if (tree == null) {
      throw new IllegalArgumentException("Unexpected null node encountered");
    }

    switch (tree.getType()) {
      case ExpressionLexer.COUNT:
        return makeCountFieldsFunction(tree);
      case ExpressionLexer.SNIPPET:
        return makeSnippetFunction(tree);
      case ExpressionLexer.ABS:
        return makeAbsoluteValueFunction(tree);
      case ExpressionLexer.LOG:
        return makeLogFunction(tree);
      case ExpressionLexer.DISTANCE:
        return makeDistanceFunction(tree);
      case ExpressionLexer.GEOPOINT:
        return makeGeopointFunction(tree);
      case ExpressionLexer.MAX:
        return makeMaxFunction(tree);
      case ExpressionLexer.MIN:
        return makeMinFunction(tree);
      case ExpressionLexer.SWITCH:
        log.warning(
            String.format("Function %s not implemented. Using dummy expression.", tree.getText()));
        return new EmptyExpression();
      case ExpressionLexer.TIMES:
      case ExpressionLexer.DIV:
      case ExpressionLexer.PLUS:
      case ExpressionLexer.MINUS:
      case ExpressionLexer.POW:
      case ExpressionLexer.LT:
      case ExpressionLexer.GT:
      case ExpressionLexer.LE:
      case ExpressionLexer.EQ:
      case ExpressionLexer.NE:
      case ExpressionLexer.GE:
        return makeNumericBinaryExpression(tree);

      case ExpressionLexer.INT:
      case ExpressionLexer.FLOAT:
        return makeNumericValueExpression(tree);

      case ExpressionLexer.NEG:
        return new NegExpression(makeNumericExpression(tree.getChild(0)));

      case ExpressionLexer.NAME:
        switch (tree.getText()) {
          case SortExpression.SCORE_FIELD_NAME:
            return new ScoreExpression();
          case SortExpression.RANK_FIELD_NAME:
            return new RankExpression();
          default:
            return FieldExpression.makeFieldExpression(
                tree.getText(), fieldTypes.get(tree.getText()));
        }

      // Not yet handled token types
      case ExpressionLexer.DOLLAR:
      case ExpressionLexer.EXPONENT:
      case ExpressionLexer.LSQUARE:
      case ExpressionLexer.ASCII_LETTER:
      case ExpressionLexer.NAME_START:
      case ExpressionLexer.EOF:
      case ExpressionLexer.LPAREN:
      case ExpressionLexer.INDEX:
      case ExpressionLexer.RPAREN:
      case ExpressionLexer.DIGIT:
      case ExpressionLexer.UNDERSCORE:
      case ExpressionLexer.RSQUARE:
      case ExpressionLexer.PHRASE:
      case ExpressionLexer.WS:
      default:
        throw new IllegalArgumentException("Not yet implemented: " + getTokenName(tree.getType()));
    }
  }
}
