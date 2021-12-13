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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link QueryTreeContext} */
@RunWith(JUnit4.class)
public class QueryTreeContextTest {

  private static class TestableQueryTreeContext extends QueryTreeContext<TestableQueryTreeContext> {
    @Override
    protected TestableQueryTreeContext newChildContext() {
      return new TestableQueryTreeContext();
    }
  }

  private TestableQueryTreeContext context;

  @Before
  public void setUp() {
    context = new TestableQueryTreeContext();
  }

  @Test
  public void testText() {
    assertThat(context.getText()).isNull();
    context.setText("foo");
    assertThat(context.getText()).isEqualTo("foo");
  }

  @Test
  public void testChildren() {
    assertThat(context.getChildCount()).isEqualTo(0);
    TestableQueryTreeContext child = context.addChild();
    assertThat(context.getChildCount()).isEqualTo(1);
    assertThat(context.getChild(0)).isEqualTo(child);
    for (TestableQueryTreeContext c : context.children()) {
      assertThat(c).isEqualTo(child);
    }
  }

  @Test
  public void testTypes() {
    for (QueryTreeContext.Type type : QueryTreeContext.Type.values()) {
      assertThat(context.isCompatibleWith(type)).isFalse();
    }
    for (QueryTreeContext.Type type : QueryTreeContext.Type.values()) {
      context.setReturnType(type);
      assertThat(context.isCompatibleWith(type)).isTrue();
    }
    for (QueryTreeContext.Type secondary : QueryTreeContext.Type.values()) {
      context.setReturnType(secondary);
      for (QueryTreeContext.Type type : QueryTreeContext.Type.values()) {
        context.addReturnType(type);
        assertThat(context.isCompatibleWith(type)).isTrue();
      }
    }
  }

  @Test
  public void testKind() {
    assertThat(context.isField()).isFalse();
    context.setKind(QueryTreeContext.Kind.FIELD);
    assertThat(context.isField()).isTrue();

    assertThat(context.isFunction()).isFalse();
    context.setKind(QueryTreeContext.Kind.FUNCTION);
    assertThat(context.isFunction()).isTrue();

    assertThat(context.isPhrase()).isFalse();
    context.setKind(QueryTreeContext.Kind.PHRASE);
    assertThat(context.isPhrase()).isTrue();
  }
}
