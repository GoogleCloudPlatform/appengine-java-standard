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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.tree.CommonTree;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link QueryParser}. */
@RunWith(JUnit4.class)
public class QueryParserTest {

  private QueryParser parser;
  private QueryLexer lexer;

  @Before
  public void setUp() {
    lexer = new QueryLexer();
    parser = new QueryParser(null);
  }

  private static final Function<Integer, String> TYPE_TO_NAME =
      input -> input == -1 ? "EOF" : QueryParser.tokenNames[input];

  private List<Token> lex(String text) {
    ANTLRStringStream stream = new ANTLRStringStream(text);
    lexer.setCharStream(stream);
    List<Token> tokens = new ArrayList<>();
    Token token;
    do {
      token = lexer.nextToken();
      tokens.add(token);
    } while (token.getType() != Token.EOF);
    return tokens;
  }

  private CommonTree parse(String text) throws RecognitionException {
    ANTLRStringStream stream = new ANTLRStringStream(text);
    lexer.setCharStream(stream);
    TokenRewriteStream tokens = new TokenRewriteStream(lexer);
    parser.setTokenStream(tokens);
    return ((CommonTree) parser.query().getTree());
  }

  private void assertParseTree(String expected, String query) {
    CommonTree tree;
    try {
      tree = parse(query);
    } catch (RecognitionException cause) {
      AssertionFailedError e = new AssertionFailedError();
      e.initCause(cause);
      throw e;
    }
    assertThat(tree.toStringTree()).isEqualTo(expected);
  }

  private void assertTokenTypes(String query, Integer... expected) {
    assertTokenTypes(query, Arrays.asList(expected));
  }

  private void assertTokenTypes(String query, List<Integer> expected) {
    List<Token> tokens = lex(query);
    List<Integer> actual = Lists.transform(tokens, Token::getType);
    if (!expected.equals(actual)) {
      throw new ComparisonFailure(
          "actual tokens: " + tokens,
          Lists.transform(expected, TYPE_TO_NAME).toString(),
          Lists.transform(actual, TYPE_TO_NAME).toString());
    }
  }

  @Test
  public void testLexText() {
    assertTokenTypes(
        "\"foo bar\"",
        QueryLexer.QUOTE,
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.QUOTE,
        QueryLexer.EOF);
    assertTokenTypes("foo,", QueryLexer.TEXT, QueryLexer.COMMA, QueryLexer.EOF);
    assertTokenTypes(
        "\"foo \\, bar\"",
        QueryLexer.QUOTE,
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.QUOTE,
        QueryLexer.EOF);
    assertTokenTypes(
        "\"foo \\\" bar\"",
        QueryLexer.QUOTE,
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.ESC,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.QUOTE,
        QueryLexer.EOF);
    assertTokenTypes(
        "\"foo \\ bar\"",
        QueryLexer.QUOTE,
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.BACKSLASH,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.QUOTE,
        QueryLexer.EOF);
    assertTokenTypes("\"\"", QueryLexer.QUOTE, QueryLexer.QUOTE, QueryLexer.EOF);
    assertTokenTypes(
        "\"\\u0058\"", QueryLexer.QUOTE, QueryLexer.ESC, QueryLexer.QUOTE, QueryLexer.EOF);
    assertTokenTypes(
        "\"\\120\"", QueryLexer.QUOTE, QueryLexer.ESC, QueryLexer.QUOTE, QueryLexer.EOF);
    assertTokenTypes(
        "foo != 1",
        QueryLexer.TEXT,
        QueryLexer.WS,
        QueryLexer.NE,
        QueryLexer.WS,
        QueryLexer.TEXT,
        QueryLexer.EOF);
    assertTokenTypes(
        "foo!= 1", QueryLexer.TEXT, QueryLexer.NE, QueryLexer.WS, QueryLexer.TEXT, QueryLexer.EOF);
    assertTokenTypes(
        "foo !=1", QueryLexer.TEXT, QueryLexer.WS, QueryLexer.NE, QueryLexer.TEXT, QueryLexer.EOF);
    assertTokenTypes("foo!=1", QueryLexer.TEXT, QueryLexer.NE, QueryLexer.TEXT, QueryLexer.EOF);
  }

  @Test
  public void testParseText() {
    // Typical text.
    assertParseTree("(HAS GLOBAL (VALUE TEXT foo))", "foo");
    assertParseTree("(HAS GLOBAL (VALUE TEXT foo-bar))", "foo-bar");

    // Field restrictions.
    assertParseTree("(: (VALUE TEXT foo) (VALUE TEXT bar))", "foo:bar");
    assertParseTree("(: (VALUE TEXT foo.bar) (VALUE TEXT baz))", "foo.bar:baz");

    // Field comparisons.
    assertParseTree("(!= (VALUE TEXT foo) (VALUE TEXT 1))", "foo != 1");
    assertParseTree("(!= (VALUE TEXT foo) (VALUE TEXT 1))", "foo !=1");
    assertParseTree("(!= (VALUE TEXT foo) (VALUE TEXT 1))", "foo!= 1");
    assertParseTree("(!= (VALUE TEXT foo) (VALUE TEXT 1))", "foo!=1");

    // Text also should consume numbers.
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123))", "123");
    assertParseTree("(HAS GLOBAL (VALUE TEXT -123))", "-123");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123.))", "123.");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123.0))", "123.0");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123e1))", "123e1");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123e+1))", "123e+1");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123e-55))", "123e-55");
    assertParseTree("(HAS GLOBAL (VALUE TEXT 123.567e-55))", "123.567e-55");
    assertParseTree("(HAS GLOBAL (VALUE TEXT -123.567e-55))", "-123.567e-55");

    // See http://b/7142182
    assertParseTree(
        "(HAS GLOBAL (VALUE TEXT 12402102-AAA5-480D-B26E-6B955D97685A))",
        "12402102-AAA5-480D-B26E-6B955D97685A");

    // http://b/6630822
    assertParseTree("(HAS GLOBAL (VALUE TEXT 조선))", "조선");

    // http://b/7023526
    assertParseTree("(HAS GLOBAL (VALUE TEXT あああ１２３))", "あああ１２３");

    // Explicit disjunction with CJK.
    assertParseTree(
        "(DISJUNCTION (HAS GLOBAL (VALUE TEXT world)) " + "(HAS GLOBAL (VALUE TEXT 你好)))",
        "world OR 你好");

    // Function calls.
    assertParseTree(
        "(< (FUNCTION distance (ARGS (VALUE TEXT home) "
            + "(FUNCTION geopoint (ARGS (VALUE TEXT 35.2) "
            + "(VALUE TEXT 40.5))))) (VALUE TEXT 100))",
        "distance(home, geopoint(35.2, 40.5)) < 100");
    assertParseTree(
        "(< (FUNCTION foo (ARGS (VALUE TEXT bar))) (VALUE TEXT 100))", "foo(bar) < 100");

    // Quoted phrases.
    assertParseTree(
        "(SEQUENCE (HAS GLOBAL (VALUE TEXT foo)) " + "(HAS GLOBAL (VALUE STRING \" bar   baz \")))",
        "foo \"bar baz\"");
    assertParseTree("(HAS GLOBAL (VALUE STRING \" bar \"))", "\"bar\"");
    assertParseTree("(HAS GLOBAL (VALUE STRING \" bar   baz \"))", "\"bar baz\"");

    // Empty query.
    assertParseTree("EMPTY", "");
    assertParseTree("EMPTY", "   ");

    // Invalid query fails at the comma.
    NoViableAltException cause = assertThrows(NoViableAltException.class, () -> parse("foo,"));
    if (cause.token.getType() != QueryLexer.COMMA) {
      ComparisonFailure e =
          new ComparisonFailure(
              cause + " " + cause.grammarDecisionDescription,
              QueryParser.tokenNames[QueryLexer.COMMA],
              QueryParser.tokenNames[cause.token.getType()]);
      e.initCause(cause);
      throw e;
    }
    assertThat(cause.line).isEqualTo(1);
    assertThat(cause.charPositionInLine).isEqualTo(3);
  }
}
