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

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.collect.FluentIterable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;

/**
 * Scorer for CUSTOM sorting algorithm, which uses expression evaluation for
 * multidimensional sorting.
 */
public class GenericScorer extends Scorer implements Comparator<GenericScorer.Result> {

  /** The regular expression that matches a field name. */
  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^\\w+$");

  /** Lucene native Sort if convertable or default Sort. */
  private final Sort nativeSort;

  /** Expressions for scoring. */
  private final NumericExpression[] expressions;

  /** Sort strategy classes for multi-dimensional sorting. */
  private final Expression.Sorter[] sorters;

  /** Maximium number of documents to score. */
  private final int scorerLimit;

  public GenericScorer(
      int scorerLimit,
      Sort nativeSort,
      NumericExpression[] expressions,
      Expression.Sorter[] sorters) {
    this.scorerLimit = scorerLimit;
    this.nativeSort = nativeSort;
    this.expressions = expressions;
    this.sorters = sorters;
  }

  public static Scorer newInstance(
      SearchServicePb.SearchParams searchParams,
      Map<String, Set<DocumentPb.FieldValue.ContentType>> fieldTypes) {
    int count = searchParams.getSortSpecCount();
    if (count == 0) {
      throw new IllegalArgumentException("no sort exporessions found");
    }

    boolean isNative = true;
    SortField[] fields = new SortField[count];
    int index = 0;

    for (SearchServicePb.SortSpec spec : searchParams.getSortSpecList()) {
      String expression = spec.getSortExpression();
      if (!FIELD_NAME_PATTERN.matcher(expression).matches()) {
        // unable to use lucene native sort
        isNative = false;
        break;
      } else {
        fields[index++] = new SortField(expression, SortField.STRING, spec.getSortDescending());
      }
    }
    Sort nativeSort;

    if (isNative) {
      nativeSort = new Sort(fields);
    } else {
      nativeSort = SimpleScorer.naturalOrder();
    }

    ExpressionBuilder builder = new ExpressionBuilder(fieldTypes);
    NumericExpression[] expressions = new NumericExpression[count];
    List<Expression.Sorter> sorters = new ArrayList<Expression.Sorter>();

    for (int i = 0; i < count; i++) {
      SearchServicePb.SortSpec spec = searchParams.getSortSpec(i);
      Expression expression = null;
      try {
        expression = builder.parse(spec.getSortExpression());
      } catch (IllegalArgumentException e) {
        String errorMessage = String.format("Failed to parse sort expression \'%s\': %s",
            spec.getSortExpression(), e.getMessage());
        throw new SearchException(errorMessage);
      }
      double numericDefault = spec.getDefaultValueNumeric();
      sorters.addAll(expression.getSorters(
          spec.getSortDescending() ? 1 : -1,
          numericDefault, spec.getDefaultValueText()));
      if (expression instanceof NumericExpression) {
        expressions[i] = new NumericDefaultExpression(
            (NumericExpression) expression, numericDefault);
      } else {
        expressions[i] = new ExpressionBuilder.IntValueExpression(numericDefault);
      }
    }
    sorters.add(new Expression.Sorter() {
      /** Evaluate expression to intermediate value suitable for sorting. */
      @Override
      public Object eval(Document doc) {
        Field f = doc.getField(LuceneUtils.ORDER_ID_FIELD_NAME);
        return Integer.parseInt(f.stringValue());
      }

      /** Sort intermediate values. */
      @Override
      public int compare(Object left, Object right) {
        return Integer.compare((int) left, (int) right);
      }
    });
    Expression.Sorter[] sortersArray = sorters.toArray(new Expression.Sorter[sorters.size()]);
    return new GenericScorer(
        searchParams.getScorerSpec().getLimit(), nativeSort, expressions, sortersArray);
  }

  /**
   * Result class for GenericScorer. Keeps intermediate values produced by
   * sorters.
   */
  public class Result extends Scorer.Result {
    private final Object[] intermediate;

    public Result(Document doc) {
      super(doc);
      this.intermediate = new Object[sorters.length];
    }

    public Object getValue(int idx) {
      if (intermediate[idx] != null) {
        return intermediate[idx];
      }
      return intermediate[idx] = sorters[idx].eval(doc);
    }

    @Override
    public void addScores(
        SearchServicePb.SearchResult.Builder resultBuilder) {
      for (int i = 0; i < expressions.length; i++) {
        try {
          resultBuilder.addScore(expressions[i].evalDouble(doc));
        } catch (EvaluationException e) {
          throw new RuntimeException("internal error, the exception should be caught already", e);
        }
      }
    }
  }

  @Override
  public int compare(Result left, Result right) {
    for (int i = 0; i < sorters.length; i++) {
      int compare = sorters[i].compare(left.getValue(i), right.getValue(i));
      if (compare != 0) {
        return compare;
      }
    }
    return 0;
  }

  @Override
  public SearchResults search(
      IndexSearcher indexSearcher,
      Query q,
      int offset,
      int limit) throws IOException {
    int docsToFind = offset + limit;
    int numDocs = Math.max(scorerLimit, docsToFind);
    TopDocs topDocs = indexSearcher.search(q, null, numDocs, nativeSort);
    Result[] resultsArray = new Result[topDocs.scoreDocs.length];
    if (sorters.length != 0) {
      ArrayList<Result> results = new ArrayList<>();
      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        results.add(new Result(indexSearcher.doc(scoreDoc.doc)));
      }
      // the call to reverseOrder is to make the highest-ranked items sort first (rather than the
      // lowest-ranked items)
      Collections.sort(results, Collections.reverseOrder(this));
      resultsArray = FluentIterable.from(results).skip(offset).limit(limit).toArray(Result.class);
    } else {
      // There are no valid sort specs so we default to the sort order of nativeSort.
      for (int i = 0; i < topDocs.scoreDocs.length; i++) {
        resultsArray[i] = new Result(indexSearcher.doc(topDocs.scoreDocs[i].doc));
      }
    }
    return new SearchResults(resultsArray, topDocs.totalHits);
  }
}
