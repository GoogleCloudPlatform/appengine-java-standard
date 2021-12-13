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
import com.google.appengine.api.search.proto.SearchServicePb.SearchParams;
import com.google.appengine.api.search.query.QueryParserFactory;
import com.google.appengine.api.search.query.QueryTreeBuilder;
import com.google.appengine.api.search.query.QueryTreeException;
import com.google.appengine.api.search.query.QueryTreeWalker;
import com.google.apphosting.api.search.DocumentPb;
import java.util.Map;
import java.util.Set;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.Tree;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * A builder of lucene queries. It uses the same parser as the one used by the
 * backend. Then, when traversing the resulting abstract search tree, it
 * generates a Lucene Query object.
 *
 */
class LuceneQueryBuilder {

  private final Map<String, Set<DocumentPb.FieldValue.ContentType>> allFieldTypes;

  LuceneQueryBuilder(Map<String, Set<DocumentPb.FieldValue.ContentType>> allFieldTypes) {
    this.allFieldTypes = allFieldTypes;
  }

  /**
   * Parses the user query and returns an equivalent Lucene query.
   *
   * @param searchParams search parameters (including query string) provided by the user.
   * @return an equivalent Lucene query
   * @throws SearchQueryException if the user query is invalid
   */
  Query parse(SearchParams searchParams) {
    String query = searchParams.getQuery();
    Query ret;
    try {
      Tree tree = new QueryTreeBuilder(new QueryParserFactory()).parse(query);
      if (tree == null) {
        throw new SearchQueryException("Null query not expected");
      }
      tree = QueryTreeWalker.simplify(tree);
      if (tree.getChildCount() == 0) {
        ret = LuceneUtils.getMatchAnyDocumentQuery();
      } else {
        LuceneQueryTreeVisitor visitor = new LuceneQueryTreeVisitor(allFieldTypes);
        QueryTreeWalker<LuceneQueryTreeContext> walker =
            new QueryTreeWalker<LuceneQueryTreeContext>(visitor);
        LuceneQueryTreeContext rootContext = LuceneQueryTreeContext.newRootContext();
        walker.walk(tree, rootContext);
        ret = rootContext.getQuery();
      }
    } catch (RecognitionException e) {
      String message = String.format("WARNING: query error at line %d:%d",
          e.line, e.charPositionInLine);
      SearchQueryException e2 = new SearchQueryException(message);
      e2.initCause(e);
      throw e2;
    } catch (QueryTreeException e) {
      SearchQueryException e2 = new SearchQueryException(e.getMessage());
      e2.initCause(e);
      throw e2;
    }
    if (searchParams.getFacetRefinementCount() != 0) {
      BooleanQuery combinedQuery = new BooleanQuery();
      combinedQuery.add(ret, BooleanClause.Occur.MUST);
      combinedQuery.add(SimpleFacet.getRefinementQuery(searchParams),
          BooleanClause.Occur.MUST);
      ret = combinedQuery;
    }
    return ret;
  }
}
