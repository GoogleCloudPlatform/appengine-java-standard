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

package com.google.appengine.api.search.checkers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Preconditions} */
@RunWith(JUnit4.class)
public class PreconditionsTest {
  @Test
  public void testNullMessage() {
    assertThat(getCheckArgumentMessage(null)).isNull();
    assertThat(getCheckArgumentMessage(null, "")).isNull();
    assertThat(getCheckNotNullMessage(null)).isNull();

    assertThat(getCheckArgumentMessage("%s", (Void) null)).isEqualTo("null");
  }

  @Test
  public void testSimpleStringMessage() {
    assertThat(getCheckArgumentMessage("the message")).isEqualTo("the message");
    assertThat(getCheckArgumentMessage("%s", "the message")).isEqualTo("the message");
    assertThat(getCheckArgumentMessage("%s %s", "the", "message")).isEqualTo("the message");
    assertThat(getCheckNotNullMessage("the message")).isEqualTo("the message");
  }

  @Test
  public void testSimpleNumberMessage() {
    assertThat(getCheckArgumentMessage(23)).isEqualTo("23");
    assertThat(getCheckArgumentMessage("%d", 23)).isEqualTo("23");
    assertThat(getCheckArgumentMessage("%s", 23)).isEqualTo("23");
    assertThat(getCheckNotNullMessage(23)).isEqualTo("23");
  }

  @Test
  public void testComplexMessage() {
    assertThat(
            getCheckArgumentMessage("int:%d, float:%.1f, str:%s, bool:%b", 342, 0.1, "hello", true))
        .isEqualTo("int:342, float:0.1, str:hello, bool:true");
    assertThat(getCheckArgumentMessage("str:%s, hex:%x", "abc", 0xcad))
        .isEqualTo("str:abc, hex:cad");
  }

  @Test
  public void testObjectMessage() {
    Object object =
        new Object() {
          @Override
          public String toString() {
            return "from toString()";
          }
        };

    assertThat(getCheckArgumentMessage(object)).isEqualTo("from toString()");
    assertThat(getCheckArgumentMessage("%s", object)).isEqualTo("from toString()");
    assertThat(getCheckNotNullMessage(object)).isEqualTo("from toString()");
  }

  @Test
  public void testCheckArgument() {
    Preconditions.checkArgument(true, "this test does not fail");
    assertThrows(
        IllegalArgumentException.class,
        () -> Preconditions.checkArgument(false, "this test fails"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Preconditions.checkArgument(false, "str:%s, hex:%x", "abc", 0xcad));
  }

  @Test
  public void testNotNull() {
    assertThat(Preconditions.checkNotNull("foobar", "test passes")).isEqualTo("foobar");
    assertThrows(
        NullPointerException.class, () -> Preconditions.checkNotNull(null, "This value is null"));
  }

  private static String getCheckArgumentMessage(Object errorMessage) {
    try {
      Preconditions.checkArgument(false, errorMessage);
      throw new RuntimeException("exception expected");
    } catch (IllegalArgumentException ex) {
      return ex.getMessage();
    }
  }

  private static String getCheckArgumentMessage(String template, Object... args) {
    try {
      Preconditions.checkArgument(false, template, args);
      throw new RuntimeException("exception expected");
    } catch (IllegalArgumentException ex) {
      return ex.getMessage();
    }
  }

  private static String getCheckNotNullMessage(Object errorMessage) {
    try {
      Preconditions.checkNotNull(null, errorMessage);
      throw new RuntimeException("exception expected");
    } catch (NullPointerException ex) {
      return ex.getMessage();
    }
  }
}
