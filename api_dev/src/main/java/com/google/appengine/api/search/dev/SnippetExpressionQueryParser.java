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
import com.google.appengine.api.search.query.QueryLexer;
import com.google.appengine.api.search.query.QueryParser;
import com.google.appengine.api.search.query.QueryParserFactory;
import com.google.appengine.api.search.query.QueryTreeBuilder;
import java.util.ArrayList;
import java.util.List;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.Tree;

/**
 * The class does parsing of query extracted all tokens from the query.
 * The tokens will be used for snippet construction.
 */
public class SnippetExpressionQueryParser {

  private final List<String> tokens;

  /**
   * Name of field we are going to create a snippet for. Token restricted to
   * other fields are ignored.
   */
  private final String fieldName;

  public SnippetExpressionQueryParser(String fieldName) {
    tokens = new ArrayList<String>();
    this.fieldName = (fieldName == null ? "" : fieldName);
  }

  /**
   * @return list of tokens from the specified query.
   */
  public List<String> parse(String query) {
    try {
      Tree tree = new QueryTreeBuilder(new QueryParserFactory()).parse(query);
      if (tree.getChildCount() == 0) {
        return null;
      }
      getTokens(tree);
      return this.tokens;
    } catch (RecognitionException e) {
      throw new SearchQueryException("Failed to parse " + query);
    }
  }

  private void textQuery(Tree tree) {
    switch (tree.getChild(0).getType()) {
      case QueryLexer.TEXT:
        tokens.add(tree.getChild(1).getText());
        break;
      case QueryLexer.STRING:
        for (int i = 0; i < tree.getChild(1).getChildCount(); ++i) {
          String token = tree.getChild(1).getChild(i).getText().trim();
          if (!tokens.isEmpty()) {
            tokens.add(token);
          }
        }
        break;
      default:
        // Ignore this
        break;
    }
  }

  private void getTokens(Tree tree) {
    if (tree == null) {
      throw new SearchQueryException("Unexpected null node encountered");
    }
    switch (tree.getType()) {
      case QueryLexer.CONJUNCTION:
      case QueryLexer.DISJUNCTION:
      case QueryLexer.NEGATION:
      case QueryLexer.SEQUENCE:
        for (int i = 0; i < tree.getChildCount(); ++i) {
          getTokens(tree.getChild(i));
        }
        break;
      case QueryLexer.VALUE:
        textQuery(tree);
        break;
      case QueryLexer.EQ:
      case QueryLexer.HAS: {
        Tree lhs = tree.getChild(0);
        if (lhs.getType() == QueryParser.VALUE) {
          if (lhs.getChild(1).getText().equals(fieldName)) {
            getTokens(tree.getChild(1));
          }
        } else if (lhs.getType() == QueryParser.GLOBAL) {
          getTokens(tree.getChild(1));
        }
        break;
      }
      case QueryLexer.LESSTHAN:
      case QueryLexer.LE:
      case QueryLexer.GT:
      case QueryLexer.GE:
        // ignore
        break;
      default:
        throw new SearchQueryException("Not yet implemented: " + tree.getType());
    }
  }
}
