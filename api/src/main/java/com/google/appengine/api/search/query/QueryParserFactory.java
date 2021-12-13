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

import org.antlr.runtime.TokenRewriteStream;

/**
 * A factory which produces {@link QueryParser QueryParsers} for a given
 * token rewrite stream.
 */
public class QueryParserFactory {
  private static final ThreadLocal<QueryParser> PARSER_POOL =
      new ThreadLocal<QueryParser>() {
        @Override
        protected QueryParser initialValue() {
          return new QueryParser(null);
        }
      };

  public QueryParser newParser(TokenRewriteStream tokens) {
    QueryParser parser = PARSER_POOL.get();
    parser.setTokenStream(tokens);
    return parser;
  }
}
