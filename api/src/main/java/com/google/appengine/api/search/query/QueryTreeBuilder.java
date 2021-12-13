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

package com.google.appengine.api.search.query;

import com.google.common.base.Preconditions;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeAdaptor;

/**
 * A generator of AST representation of a query. This class uses the given factory
 * to produce a query parser which parses user specified query. If successful it
 * returns the root of an AST representing the parsed query.
 */
public class QueryTreeBuilder {
  private static final ThreadLocal<QueryLexer> LEXER_POOL = new ThreadLocal<QueryLexer>() {
    @Override
    protected QueryLexer initialValue() {
      return new QueryLexer();
    }
  };

  private final QueryParserFactory parserFactory;
  private final CommonTreeAdaptor adaptor = new CommonTreeAdaptor();

  public QueryTreeBuilder() {
    this.parserFactory = new QueryParserFactory();
  }

  public QueryTreeBuilder(QueryParserFactory parserFactory) {
    this.parserFactory = parserFactory;
  }

  /**
   * Parses the user query and returns its AST.
   *
   * @param query the user query to be parsed
   * @return a CommonTree constructed from the query
   * @throws RecognitionException if the user query is invalid
   * @throws NullPointerException if query is null
   */
  public CommonTree parse(String query) throws RecognitionException {
    Preconditions.checkNotNull(query, "query must not be null");
    ANTLRStringStream stream = new ANTLRStringStream(query);
    QueryLexer lexer = LEXER_POOL.get();
    lexer.setCharStream(stream);
    TokenRewriteStream tokens = new TokenRewriteStream(lexer);
    QueryParser parser = parserFactory.newParser(tokens);
    CommonTree tree =  (CommonTree) parser.query().getTree();
    if (tree.getType() == QueryParser.EMPTY && tree.getChildCount() == 0) {
      tree = (CommonTree) adaptor.nil();
    }
    return tree;
  }
}
