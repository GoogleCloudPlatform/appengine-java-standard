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
import com.google.appengine.api.search.query.ParserUtils;
import com.google.appengine.api.search.query.QueryLexer;
import com.google.appengine.api.search.query.QueryTreeContext;
import com.google.appengine.api.search.query.QueryTreeContext.Kind;
import com.google.appengine.api.search.query.QueryTreeVisitor;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.antlr.runtime.tree.Tree;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * A query tree visitor that builds a Lucene query.
 *
 */
class LuceneQueryTreeVisitor implements QueryTreeVisitor<LuceneQueryTreeContext> {
  enum Function {
    GEOPOINT,
    DISTANCE;

    Function() {
      this.token = name().toLowerCase();
    }

    final String token;

    static Function fromToken(String token) {
      for (Function f : values()) {
        if (f.token.equals(token)) {
          return f;
        }
      }
      throw new SearchQueryException("unknown function '" + token + "'");
    }
  }

  private static final ImmutableMap<DocumentPb.FieldValue.ContentType, QueryTreeContext.Type>
      TYPE_MAP =
          Maps.immutableEnumMap(
              ImmutableMap.<DocumentPb.FieldValue.ContentType, QueryTreeContext.Type>builder()
                  .put(DocumentPb.FieldValue.ContentType.ATOM, QueryTreeContext.Type.TEXT)
                  .put(DocumentPb.FieldValue.ContentType.DATE, QueryTreeContext.Type.DATE)
                  .put(DocumentPb.FieldValue.ContentType.GEO, QueryTreeContext.Type.LOCATION)
                  .put(DocumentPb.FieldValue.ContentType.HTML, QueryTreeContext.Type.TEXT)
                  .put(DocumentPb.FieldValue.ContentType.NUMBER, QueryTreeContext.Type.NUMBER)
                  .put(DocumentPb.FieldValue.ContentType.TEXT, QueryTreeContext.Type.TEXT)
                  .put(
                      DocumentPb.FieldValue.ContentType.UNTOKENIZED_PREFIX,
                      QueryTreeContext.Type.TEXT)
                  .put(
                      DocumentPb.FieldValue.ContentType.TOKENIZED_PREFIX,
                      QueryTreeContext.Type.TEXT)
                  .buildOrThrow());

  private static final ImmutableSet<DocumentPb.FieldValue.ContentType> TEXT_TYPES =
      Sets.immutableEnumSet(
          DocumentPb.FieldValue.ContentType.TEXT,
          DocumentPb.FieldValue.ContentType.ATOM,
          DocumentPb.FieldValue.ContentType.HTML,
          DocumentPb.FieldValue.ContentType.UNTOKENIZED_PREFIX,
          DocumentPb.FieldValue.ContentType.TOKENIZED_PREFIX);

  private final Map<String, Set<DocumentPb.FieldValue.ContentType>> allFieldTypes;

  LuceneQueryTreeVisitor(Map<String, Set<DocumentPb.FieldValue.ContentType>> allFieldTypes) {
    this.allFieldTypes = allFieldTypes;
  }

  private void buildContextTextRecursive(StringBuffer builder,
      LuceneQueryTreeContext context) {
    if (context.getText() != null && !context.getText().equals(LuceneUtils.FIELDLESS_FIELD_NAME)) {
      builder.append(" ").append(context.isFuzzy() ? "~" : context.isStrict() ? "+" : "")
          .append(context.getText());
    } else {
      for (LuceneQueryTreeContext child : context.children()) {
        buildContextTextRecursive(builder, child);
      }
    }
  }

  @Override
  public void visitSequence(Tree node, LuceneQueryTreeContext context) {
    visitConjunction(node, context);
    boolean found = false;
    for (int i = 0; i < node.getChildCount(); ++i) {
      Tree child = node.getChild(i);
      int type = child.getType();
      if (!(type == QueryLexer.HAS && child.getChild(0).getType() == QueryLexer.GLOBAL)) {
        found = true;
        break;
      }
    }
    if (!found) {
      StringBuffer builder = new StringBuffer();
      buildContextTextRecursive(builder, context);
      String text = builder.substring(1);
      BooleanQuery disjunction = new BooleanQuery();
      disjunction.add(new TermQuery(new Term(LuceneUtils.FIELDLESS_FIELD_NAME, text)),
          BooleanClause.Occur.SHOULD);
      disjunction.add(context.getQuery(), BooleanClause.Occur.SHOULD);
      context.setQuery(disjunction);
    }
  }

  @Override
  public void visitConjunction(Tree node, LuceneQueryTreeContext context) {
    context.setQuery(
        visitBooleanQueryChildren(new BooleanQuery(), BooleanClause.Occur.MUST, context));
  }

  @Override
  public void visitDisjunction(Tree node, LuceneQueryTreeContext context) {
    context.setQuery(
        visitBooleanQueryChildren(new BooleanQuery(), BooleanClause.Occur.SHOULD, context));
  }

  @Override
  public void visitNegation(Tree node, LuceneQueryTreeContext context) {
    BooleanQuery query = new BooleanQuery();
    query.add(LuceneUtils.getMatchAnyDocumentQuery(), BooleanClause.Occur.MUST);
    context.setQuery(visitBooleanQueryChildren(query, BooleanClause.Occur.MUST_NOT, context));
  }

  private Query visitBooleanQueryChildren(BooleanQuery parentQuery, BooleanClause.Occur occur,
      LuceneQueryTreeContext context) {
    for (LuceneQueryTreeContext childContext : context.children()) {
      parentQuery.add(childContext.getQuery(), occur);
    }
    return parentQuery;
  }

  @Override
  public void visitFuzzy(Tree node, LuceneQueryTreeContext context) {
    context.setRewriteMode(QueryTreeContext.RewriteMode.FUZZY);
  }

  @Override
  public void visitLiteral(Tree node, LuceneQueryTreeContext context) {
    context.setRewriteMode(QueryTreeContext.RewriteMode.STRICT);
  }

  @Override
  public void visitLessThan(Tree node, LuceneQueryTreeContext context) {
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.LT);
  }

  @Override
  public void visitLessOrEqual(Tree node, LuceneQueryTreeContext context) {
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.LE);
  }

  @Override
  public void visitGreaterThan(Tree node, LuceneQueryTreeContext context) {
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.GT);
  }

  @Override
  public void visitGreaterOrEqual(Tree node, LuceneQueryTreeContext context) {
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.GE);
  }

  @Override
  public void visitEqual(Tree node, LuceneQueryTreeContext context) {
    // TODO: Use ComparisonOp.EQ and adjust text query generation.
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.HAS);
  }

  @Override
  public void visitContains(Tree node, LuceneQueryTreeContext context) {
    visitComparison(node, context, LuceneQueryTreeContext.ComparisonOp.HAS);
  }

  @SuppressWarnings("unused")
  private void visitComparison(Tree node, LuceneQueryTreeContext context,
      LuceneQueryTreeContext.ComparisonOp op) {
    LuceneQueryTreeContext lhs = context.getChild(0);
    LuceneQueryTreeContext rhs = context.getChild(1);
    List<Query> children = new ArrayList<Query>();
    for (LuceneQueryTreeContext.Type type : lhs.getCommonReturnTypes(rhs)) {
      children.addAll(newQuery(type, lhs, op, rhs));
    }
    // Remove all null queries generated by incompatible comparisons.
    // For example, for a field that is both text and number, query
    // such as foo > 123 generates one numeric query and one null query.
    for (Iterator<Query> iter = children.iterator(); iter.hasNext(); ) {
      Query query = iter.next();
      if (query == null) {
        iter.remove();
      }
    }
    if (children.isEmpty()) {
      context.setQuery(LuceneUtils.getMatchNoneQuery());
    } else if (children.size() == 1) {
      context.setQuery(children.get(0));
    } else {
      BooleanQuery or = new BooleanQuery();
      for (Query query : children) {
        or.add(query, BooleanClause.Occur.SHOULD);
      }
      context.setQuery(or);
    }
  }

  @Override
  public void visitValue(Tree node, LuceneQueryTreeContext context) {
    if (node.getChild(0).getType() == QueryLexer.STRING) {
      // Four child nodes: STRING quote <some text> quote
      String text = node.getChild(2).getText();
      context.setRawText(text);
      StringBuilder builder = new StringBuilder();
      for (int i = 1; i < node.getChildCount(); ++i) {
        builder.append(node.getChild(i).getText());
      }

      context.setText(builder.toString());
      context.setKind(QueryTreeContext.Kind.PHRASE);
      context.setReturnType(LuceneQueryTreeContext.Type.TEXT);

      if (ParserUtils.isNumber(text)) {
        context.addReturnType(LuceneQueryTreeContext.Type.NUMBER);
      }
      if (LuceneUtils.isDateString(text)) {
        context.addReturnType(LuceneQueryTreeContext.Type.DATE);
      }
    } else {
      String text = node.getChild(1).getText();
      Set<DocumentPb.FieldValue.ContentType> types = allFieldTypes.get(text);
      if (!(types == null || types.isEmpty())) {
        for (DocumentPb.FieldValue.ContentType type : types) {
          context.addReturnType(TYPE_MAP.get(type));
        }
        context.setKind(QueryTreeContext.Kind.FIELD);
        context.setFieldTypes(types);
      } else {
        context.setKind(QueryTreeContext.Kind.LITERAL);
        context.setReturnType(LuceneQueryTreeContext.Type.TEXT);

        if (ParserUtils.isNumber(text)) {
            context.addReturnType(LuceneQueryTreeContext.Type.NUMBER);
            context.addReturnType(LuceneQueryTreeContext.Type.DISTANCE);
        }
        if (LuceneUtils.isDateString(text)) {
          context.addReturnType(LuceneQueryTreeContext.Type.DATE);
        }
      }
      context.setText(text);
    }
  }

  @Override
  public void visitFunction(Tree node, LuceneQueryTreeContext context) {
    String token = node.getChild(0).getText();
    Function fn = Function.fromToken(token);
    List<LuceneQueryTreeContext> children = Lists.newArrayList(context.children());

    // TODO: if we ever add more functions, this simple rule may not suffice
    if (children.size() != 2) {
      throw new SearchQueryException(
          String.format("%s() function requires exactly 2 arguments", token));
    }
    switch (fn) {
      case DISTANCE:
        /*
         * Juggle children in case order given in query is reversed (Dexter is this permissive).
         */
        LuceneQueryTreeContext arg0 = children.get(0);
        LuceneQueryTreeContext arg1 = children.get(1);
        if (!arg0.isField() && arg1.isField()) {
          LuceneQueryTreeContext tmp = arg0;
          arg0 = arg1;
          arg1 = tmp;
          children.set(0, arg0);
          children.set(1, arg1);
        }
        if (!arg0.isField() || !arg1.isLiteral()) {
          throw new SearchQueryException(
              "distance() function requires field-name and geopoint() arguments");
        }
        String field = arg0.getText();
        String point = arg1.getText();
        context.setReturnType(LuceneQueryTreeContext.Type.DISTANCE);
        context.setKind(Kind.FUNCTION);
        context.setText(field + ":" + point);
        break;
      case GEOPOINT:
        context.setReturnType(LuceneQueryTreeContext.Type.LOCATION);
        context.setKind(Kind.LITERAL);
        double lat = Double.parseDouble(children.get(0).getText());
        double lng = Double.parseDouble(children.get(1).getText());
        context.setText(lat + ":" + lng);
        break;
    }
  }

  @Override
  public void visitGlobal(Tree node, LuceneQueryTreeContext context) {
    // Global can be of any type.
    context.setReturnType(LuceneQueryTreeContext.Type.TEXT);
    context.addReturnType(LuceneQueryTreeContext.Type.DATE);
    context.addReturnType(LuceneQueryTreeContext.Type.NUMBER);
    context.setText(LuceneUtils.FIELDLESS_FIELD_NAME);
  }

  @Override
  public void visitOther(Tree node, LuceneQueryTreeContext context) {
    throw new SearchQueryException("Unexpected query found at " + node.getCharPositionInLine()
        + ": \"" + node.getText() + "\"");
  }

  private List<Query> newQuery(LuceneQueryTreeContext.Type type, LuceneQueryTreeContext lhs,
      LuceneQueryTreeContext.ComparisonOp op, LuceneQueryTreeContext rhs) {
    List<Query> queries = new ArrayList<Query>();
    switch (type) {
      case TEXT:
        Set<DocumentPb.FieldValue.ContentType> types;
        if (lhs.getFieldTypes() == null) {
          types = EnumSet.of(DocumentPb.FieldValue.ContentType.TEXT);
        } else {
          types = EnumSet.copyOf(lhs.getFieldTypes());
          types.retainAll(TEXT_TYPES);
        }
        for (DocumentPb.FieldValue.ContentType subType : types) {
          queries.add(newTextQuery(subType, lhs, op, rhs));
        }
        break;
      case NUMBER:
        if (ParserUtils.isNumber(rhs.getUnquotedText())) {
          queries.add(newNumericQuery(lhs, op, rhs));
        }
        break;
      case DATE:
        if (ParserUtils.isDate(rhs.getUnquotedText())) {
          queries.add(newDateQuery(lhs, op, rhs));
        }
        break;
      case DISTANCE:
        queries.add(newDistanceQuery(lhs, op, rhs));
        break;
      case LOCATION:
        throw new SearchQueryException("Comparison operator not available for Geo type");
      default:
        throw new SearchQueryException("Unknown field type " + type.name().toLowerCase());
    }
    return queries;
  }

  private static Query newNumericQuery(
      LuceneQueryTreeContext lhs,
      LuceneQueryTreeContext.ComparisonOp op,
      LuceneQueryTreeContext rhs) {
    try {
      double value = Double.parseDouble(rhs.getUnquotedText());
      boolean minInclusive = true;
      boolean maxInclusive = true;
      double min = value;
      double max = value;

      switch (op) {
        case EQ:
        case HAS:
          if (LuceneUtils.FIELDLESS_FIELD_NAME.equals(lhs.getText())) {
            return new TermQuery(new Term(lhs.getText(), Double.toString(min)));
          }
          // Use the defaults setup above.
          break;
        case LT:
          maxInclusive = false;
          min = -Double.MAX_VALUE;
          break;
        case LE:
          maxInclusive = true;
          min = -Double.MAX_VALUE;
          break;
        case GT:
          minInclusive = false;
          max = Double.MAX_VALUE;
          break;
        case GE:
          minInclusive = true;
          max = Double.MAX_VALUE;
          break;
        default:
          return null;
      }
      String text = lhs.getText();
      if (lhs.isField()) {
        text = LuceneUtils.makeLuceneFieldName(text, DocumentPb.FieldValue.ContentType.NUMBER);
      }
      return NumericRangeQuery.newDoubleRange(text, min, max, minInclusive, maxInclusive);
    } catch (NumberFormatException e) {
      throw new SearchQueryException(lhs.getText() + op + rhs.getText());
    }
  }

  private static Query newDistanceQuery(LuceneQueryTreeContext lhs,
      LuceneQueryTreeContext.ComparisonOp op, LuceneQueryTreeContext rhs) {
    if (op == LuceneQueryTreeContext.ComparisonOp.HAS) {
      throw new SearchQueryException("Equality comparison not available for Geo type");
    }
    String[] parts = lhs.getText().split(Pattern.quote(":"));
    String fieldName = parts[0];
    double latitude = Double.parseDouble(parts[1]);
    double longitude = Double.parseDouble(parts[2]);
    double distance = Double.parseDouble(rhs.getText());
    return GeometricQuery.create(fieldName, latitude, longitude, op, distance);
  }

  private static Query newDateQuery(LuceneQueryTreeContext lhs,
      LuceneQueryTreeContext.ComparisonOp op, LuceneQueryTreeContext rhs) {
    long time;
    try {
      time = LuceneUtils.dateStringToLong(rhs.getUnquotedText());
    } catch (ParseException e) {
      throw new SearchQueryException("Could not parse date");
    }
    boolean minInclusive = true;
    boolean maxInclusive = true;
    long min = time / 86400000L;
    long max = min;

    switch (op) {
      case EQ:
      case HAS:
        // We can't do the standard range query globally for dates. This is a hack to get global
        // search to work for date equality.
        if (LuceneUtils.FIELDLESS_FIELD_NAME.equals(lhs.getText())) {
          // We can still match globally on strings, even on date fields. Just create a new text
          // query to match the string representation of the date.
          return new TermQuery(new Term(lhs.getText(), Long.toString(min)));
        }
        // Else, just search within one day
        max = min + 1L;
        maxInclusive = false;
        break;
      case LT:
        maxInclusive = false;
        min = Long.MIN_VALUE;
        break;
      case LE:
        maxInclusive = true;
        min = Long.MIN_VALUE;
        break;
      case GT:
        minInclusive = false;
        max = Long.MAX_VALUE;
        break;
      case GE:
        minInclusive = true;
        max = Long.MAX_VALUE;
        break;
      default:
        return null;
    }
    return NumericRangeQuery.newLongRange(
        LuceneUtils.makeLuceneFieldName(lhs.getText(), DocumentPb.FieldValue.ContentType.DATE),
        min, max, minInclusive, maxInclusive);
  }

  private static Query newTextQuery(
      DocumentPb.FieldValue.ContentType subType,
      LuceneQueryTreeContext lhs,
      LuceneQueryTreeContext.ComparisonOp op,
      LuceneQueryTreeContext rhs) {
    Query base = newTextMatchQuery(subType, lhs, rhs);
    switch (op) {
      case EQ:
      case HAS:
        return base;
      case NE:
        BooleanQuery boolQuery = new BooleanQuery();
        boolQuery.add(LuceneUtils.getMatchAnyDocumentQuery(), BooleanClause.Occur.MUST);
        boolQuery.add(base, BooleanClause.Occur.MUST_NOT);
        return boolQuery;
      default:
        // For text values we only support equality and inequality.
        return null;
    }
  }

  private static Query newTextMatchQuery(DocumentPb.FieldValue.ContentType subType,
      LuceneQueryTreeContext lhs, LuceneQueryTreeContext rhs) {
    String field = lhs.getText();
    String value = rhs.getText();
    if (rhs.isPhrase()) {
      // Strip the quotes
      value = value.substring(1, value.length() - 1);
    }
    if (lhs.isField()) {
      field = LuceneUtils.makeLuceneFieldNameWithExtractedText(field, subType);
    } else if (!LuceneUtils.FIELDLESS_FIELD_NAME.equals(field)) {
      value = lhs + ":" + value;
      field = LuceneUtils.FIELDLESS_FIELD_NAME;
    }
    switch (subType) {
      case ATOM:
        return new TermQuery(new Term(field, value.toLowerCase()));
      case TEXT:
      case HTML:
        value = WordSeparatorAnalyzer.removeDiacriticals(value);
        break;
      case UNTOKENIZED_PREFIX:
        return new TermQuery(new Term(field, PrefixFieldAnalyzerUtil.normalizePrefixField(value)));
      case TOKENIZED_PREFIX:
        break;
      default:
        throw new IllegalArgumentException("type " + subType + " cannot match text");
    }
    List<String> tokens;
    if (subType == DocumentPb.FieldValue.ContentType.TOKENIZED_PREFIX) {
      tokens = PrefixFieldAnalyzerUtil.tokenizePrefixFieldQuery(value);
    } else {
      tokens = WordSeparatorAnalyzer.tokenList(value);
    }
    if (!rhs.isPhrase()) {
      // This gets a little tricky. The value that they're searching for could actually correspond
      // to multiple tokens (like "query:this.is.a.few.words"). This uses WordSeparatorAnalyzer to
      // tokenize the query, and if necessary, emits an AND query that requires all of the tokens to
      // exist in the document.
      if (tokens.size() == 1) {
        return new TermQuery(new Term(field, tokens.get(0)));
      } else if (tokens.isEmpty()) {
        return LuceneUtils.getMatchAnyDocumentQuery();
      } else {
        BooleanQuery conjunction = new BooleanQuery();
        for (String token : tokens) {
          conjunction.add(new TermQuery(new Term(field, token)), BooleanClause.Occur.MUST);
        }
        BooleanQuery disjunction = new BooleanQuery();
        disjunction.add(new TermQuery(new Term(field, value.toLowerCase())),
            BooleanClause.Occur.SHOULD);
        disjunction.add(conjunction, BooleanClause.Occur.SHOULD);
        return disjunction;
      }
    }

    // For a query like "just-a-test", there are two options: either we should search for "just a
    // test" on text fields, or we should search for "just-a-test" on atom fields. We solve this
    // problem by searching for ("just a test" OR "just-a-test")
    PhraseQuery phraseQuery = new PhraseQuery();
    // TODO: Fix this for CJK text.
    for (String token : tokens) {
      phraseQuery.add(new Term(field, token));
    }

    BooleanQuery disjunction = new BooleanQuery();
    disjunction.add(phraseQuery, BooleanClause.Occur.SHOULD);

    PhraseQuery literalPhraseQuery = new PhraseQuery();
    literalPhraseQuery.add(new Term(field, value.toLowerCase()));
    disjunction.add(literalPhraseQuery, BooleanClause.Occur.SHOULD);
    return disjunction;
  }
}
