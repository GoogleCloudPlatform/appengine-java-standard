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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.api.search.DateTestHelper;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ParserUtils}.
 *
 */
@RunWith(JUnit4.class)
public class ParserUtilsTest {

  @Test
  public void testTrimLast() {
    assertThat(ParserUtils.trimLast("abc")).isEqualTo("ab");
  }

  @Test
  public void testPreserveSpace() {
    assertThat(ParserUtils.unescapePhraseText("\"\"")).isEmpty();
    assertThat(ParserUtils.unescapePhraseText("\"a\"")).isEqualTo("a");
    assertThat(ParserUtils.unescapePhraseText("\" a\"")).isEqualTo(" a");
    assertThat(ParserUtils.unescapePhraseText("\" a \"")).isEqualTo(" a ");
    assertThat(ParserUtils.unescapePhraseText("\"a \"")).isEqualTo("a ");
    assertThat(ParserUtils.unescapePhraseText("\" a b\"")).isEqualTo(" a b");
    assertThat(ParserUtils.unescapePhraseText("\" a   b\"")).isEqualTo(" a   b");
    assertThat(ParserUtils.unescapePhraseText("\" a   b  \"")).isEqualTo(" a   b  ");
  }

  @Test
  public void testEscapeSequences() {
    assertThat(ParserUtils.unescapePhraseText("\"\"\"")).isEqualTo("\"");
    assertThat(ParserUtils.unescapePhraseText("\"\\'\"")).isEqualTo("'");
    assertThat(ParserUtils.unescapePhraseText("\"\\u340d\"")).isEqualTo("\u340d");
    assertThat(ParserUtils.unescapePhraseText("\"\\145\"")).isEqualTo("e");
    assertThat(ParserUtils.unescapePhraseText("\"\\145dit\"")).isEqualTo("edit");
    assertThat(ParserUtils.unescapePhraseText("\"\\41\"")).isEqualTo("!");
    assertThat(ParserUtils.unescapePhraseText("\"\\41no\"")).isEqualTo("!no");
  }

  @Test
  public void testBrokenEscapeSequences() {
    assertThat(ParserUtils.unescapePhraseText("\"\\u\"")).isEqualTo("\\u");
    assertThat(ParserUtils.unescapePhraseText("\"\\uzzz\"")).isEqualTo("\\uzzz");
    assertThat(ParserUtils.unescapePhraseText("\"\\umolt\"")).isEqualTo("\\umolt");
    assertThat(ParserUtils.unescapePhraseText("\"\\888\"")).isEqualTo("\\888");
  }

  @Test
  public void testSimpleInteger() {
    for (int i = -11000; i < 11000; ++i) {
      assertWithMessage("Should be recognized as a number " + i)
          .that(ParserUtils.isNumber(Integer.toString(i)))
          .isTrue();
    }
  }

  @Test
  public void testFloats() {
    assertWithMessage("PI should be recognized as a number")
        .that(ParserUtils.isNumber(Double.toString(Math.PI)))
        .isTrue();
    assertWithMessage("E should be recognized as a number")
        .that(ParserUtils.isNumber(Double.toString(Math.E)))
        .isTrue();
    assertWithMessage("-2.17 should be recognized as a number")
        .that(ParserUtils.isNumber("-2.17"))
        .isTrue();
    assertWithMessage("-0.17 should be recognized as a number")
        .that(ParserUtils.isNumber("-0.17"))
        .isTrue();
    assertWithMessage("-.17 should be recognized as a number")
        .that(ParserUtils.isNumber(".17"))
        .isTrue();
    assertWithMessage("0.17 should be recognized as a number")
        .that(ParserUtils.isNumber("0.17"))
        .isTrue();
    assertWithMessage(".17 should be recognized as a number")
        .that(ParserUtils.isNumber("-.17"))
        .isTrue();
  }

  @Test
  public void testExponentialNotation() {
    String[] numbers = {
      "1e6", "1.1e6", "-1.1e6", "-1.1e+6", "-1.1e-6",
      "1E6", "1.1E6", "-1.1E6", "-1.1E+6", "-1.1E-6",
    };
    for (String n : numbers) {
      assertWithMessage(n + " should be recognized as a number")
          .that(ParserUtils.isNumber(n))
          .isTrue();
    }
  }

  @Test
  public void testNonNumbers() {
    String[] strings = {
      null, "", "abc", "17a", "a17", "-aa", "3E4f", "3..3", "--3", "3E", "", "-", "-E", "1.3E-"
    };
    for (String s : strings) {
      assertWithMessage(s + " should not be recognized as a number")
          .that(ParserUtils.isNumber(s))
          .isFalse();
    }
  }

  @Test
  public void testDates() {
    Calendar cal = DateTestHelper.getCalendar();
    cal.set(1969, 0, 1, 0, 0, 0);
    for (int i = 0; i < 365 * 5; ++i) {
      String longDateStr =
          String.format(
              "%04d-%02d-%02d",
              cal.get(Calendar.YEAR), 1 + cal.get(Calendar.MONTH), cal.get(Calendar.DATE));
      assertWithMessage("Should be recognized as a date: " + longDateStr)
          .that(ParserUtils.isDate(longDateStr))
          .isTrue();
      String shortDateStr =
          String.format(
              "%d-%d-%d",
              cal.get(Calendar.YEAR), 1 + cal.get(Calendar.MONTH), cal.get(Calendar.DATE));
      assertWithMessage("Should be recognized as a date: " + shortDateStr)
          .that(ParserUtils.isDate(shortDateStr))
          .isTrue();
      cal.add(Calendar.DATE, 1);
    }

    assertWithMessage("-1970-2-1 is considered a date")
        .that(ParserUtils.isDate("-1970-2-1"))
        .isTrue();
  }

  @Test
  public void testNonDates() {
    String[] strings = {
      null,
      "",
      "1700-",
      "1700-1-",
      "1700-1-12a",
      "1692a",
      "1923-1a",
      "1921-1-99",
      "1970-33-1",
      "1970-0-1",
      "1970-1-1a",
      "1970-1-0",
      "1970/1/1",
      "1970-1/1",
      "10000-01-01",
      "999999999999999999999999999999999999999999999-01-01",
    };
    for (String s : strings) {
      assertWithMessage(s + " should not be recognized as a date")
          .that(ParserUtils.isDate(s))
          .isFalse();
    }
  }
}
