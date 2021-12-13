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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unittest for SnippetExpressionQueryParser.
 *
 */
@RunWith(JUnit4.class)
public class SnippetExpressionQueryParserTest {
  @Test
  public void testSimpleQuery() throws Exception {
    SnippetExpressionQueryParser parser = new SnippetExpressionQueryParser("foo");
    assertThat(parser.parse("query")).containsExactly("query").inOrder();
  }

  @Test
  public void testRestrictedQuery() throws Exception {
    SnippetExpressionQueryParser parser = new SnippetExpressionQueryParser("foo");
    assertThat(parser.parse("foo:first bar:second foo:third"))
        .containsExactly("first", "third")
        .inOrder();
  }

  @Test
  public void testComplexQuery() throws Exception {
    SnippetExpressionQueryParser parser = new SnippetExpressionQueryParser("foo");
    assertThat(parser.parse("(weather OR blah) AND NOT (blah AND NOT world)"))
        .containsExactly("weather", "blah", "blah", "world")
        .inOrder();
  }
}
