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

import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unittest for SnippetExpression.
 *
 */
@RunWith(JUnit4.class)
public class SnippetExpressionTest {
  public Set<ContentType> contentTypes;

  @Before
  public void setUp() throws Exception {
    contentTypes = EnumSet.noneOf(ContentType.class);
    contentTypes.add(ContentType.HTML);
  }

  @Test
  public void testMakeSnippetExpression() {
    Expression e = SnippetExpression.makeSnippetExpression("", "foo", contentTypes, null, null);
    assertThat(e).isInstanceOf(ExpressionBuilder.EmptyExpression.class);
    e =
        SnippetExpression.makeSnippetExpression(
            "hello foo:world bar:ignore", "foo", contentTypes, null, null);
    assertThat(e).isInstanceOf(SnippetExpression.class);
  }

  @Test
  public void testMakeSnippet() {
    String input = "123 hello stars and hello world 456 ignore";
    SnippetExpression e =
        (SnippetExpression)
            SnippetExpression.makeSnippetExpression(
                "hello foo:world bar:ignore", "foo", contentTypes, null, null);
    assertThat(e.makeSnippet(input, 5, 1)).isEqualTo("<b>...</b><b>hello</b><b>...</b>");
    assertThat(e.makeSnippet(input, 4, 1)).isEqualTo("<b>...</b><b>hell</b><b>...</b>");
    assertThat(e.makeSnippet(input, 11, 1))
        .isEqualTo("<b>...</b><b>hello</b> <b>world</b><b>...</b>");
    assertThat(e.makeSnippet(input, 19, 1))
        .isEqualTo("<b>...</b>and <b>hello</b> <b>world</b> 456<b>...</b>");
    assertThat(e.makeSnippet(input, 200, 1))
        .isEqualTo("123 <b>hello</b> stars and <b>hello</b> <b>world</b> 456 ignore");
  }

  @Test
  public void testCaseInsensitive() {
    String input = "Hello World";
    SnippetExpression e =
        (SnippetExpression)
            SnippetExpression.makeSnippetExpression("hello world", "foo", contentTypes, null, null);
    assertThat(e.makeSnippet(input, 160, 1)).isEqualTo("<b>Hello</b> <b>World</b>");
  }
}
