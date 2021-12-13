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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Ascii;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link WordSeparatorAnalyzer}.
 *
 */
@RunWith(JUnit4.class)
public class WordSeparatorAnalyzerTest {
  /**
   * Tokenize the given string and check the length of the stream.
   *
   * <p>Also checks the generated tokens to ensure they do not contain any word separators.
   */
  List<String> checkTokenStreamLength(int length, String tokenizeString) throws Exception {
    List<String> tokens = WordSeparatorAnalyzer.tokenList(tokenizeString);
    for (String term : tokens) {
      for (int i = 0; i < term.length(); i++) {
        assertWithMessage(
                "Term should not contain word separator character '%s'", term.substring(i, i + 1))
            .that(LuceneUtils.WORD_SEPARATORS)
            .doesNotContain(term.charAt(i));
      }
    }
    assertThat(tokens).hasSize(length);
    return tokens;
  }

  /** Check that a list and an array have the same elements, in the same order. */
  void checkListsEqual(String[] expect, List<String> actual) {
    assertThat(actual).hasSize(expect.length);
    for (int i = 0; i < expect.length; i++) {
      assertThat(actual.get(i)).isEqualTo(expect[i]);
    }
  }

  @Test
  public void testSplitString() throws Exception {
    checkTokenStreamLength(
        14, "This!is\"a%test(of)the*tokenizer,which.ensures/that:it=splits>strings properly");
  }

  @Test
  public void testToLowercase() throws Exception {
    List<String> tokens = checkTokenStreamLength(4, "Some MIXED CaSe tOKENs");
    for (String token : tokens) {
      assertThat(Ascii.toLowerCase(token)).isEqualTo(token);
    }
  }

  @Test
  public void testNonSeparatorSpecialChars() throws Exception {
    checkTokenStreamLength(1, "test-word");
    checkTokenStreamLength(1, "test1word");
  }

  @Test
  public void testEmptyStream() throws Exception {
    checkTokenStreamLength(0, "!%\n\r\n&#\t   ");
    checkTokenStreamLength(0, "");
  }

  @Test
  public void testNormalize() throws Exception {
    assertThat(
            WordSeparatorAnalyzer.normalize(
                "THERE|once?was#a$man\n"
                    + "from   NantUckEt\n\r"
                    + "who,livEd(all*His)LIFe\"in a%bucket\"\"\""))
        .isEqualTo("there once was a man from nantucket who lived all his life in a bucket");
    assertThat(WordSeparatorAnalyzer.normalize("%^\n\r\f\t@#$%^()[]")).isEmpty();
  }

  @Test
  public void testTokenList() throws Exception {
    List<String> tokens =
        WordSeparatorAnalyzer.tokenList(
            "THERE|once?was#a$man\nfrom   NantUckEt\n\rwho,livEd(all*His)LIFe\"in a%bucket\"\"\"");
    String[] expect = {
      "there",
      "once",
      "was",
      "a",
      "man",
      "from",
      "nantucket",
      "who",
      "lived",
      "all",
      "his",
      "life",
      "in",
      "a",
      "bucket"
    };

    checkListsEqual(expect, tokens);
  }

  @Test
  public void testRemoveDiacriticals() throws Exception {
    assertThat(WordSeparatorAnalyzer.removeDiacriticals("ONE Two three"))
        .isEqualTo("ONE Two three");
    assertThat(WordSeparatorAnalyzer.removeDiacriticals("son siège social est basé au café"))
        .isEqualTo("son siege social est base au cafe");
  }

  public void checkIsCjk(String string, boolean shouldBeCjk) throws Exception {
    boolean isCjk = LuceneUtils.isProbablyCjk(new StringReader(string), new StringBuilder());
    if (shouldBeCjk) {
      assertThat(isCjk).isTrue();
    } else {
      assertThat(isCjk).isFalse();
    }
  }

  @Test
  public void testProbablyCjk() throws Exception {
    checkIsCjk("挨拶", true);
    checkIsCjk("测试测试测试", true);
    checkIsCjk("테스트 테스트 테스트", true);
    checkIsCjk("こんにちは、私の名前は意志である", true);
    checkIsCjk("test test test 测试测试测试", true);
    checkIsCjk("test test test 테스트 테스트 테스트", true);
    checkIsCjk("one two three four! hello! 挨拶.", false);
    checkIsCjk("xy挨拶", true);
    checkIsCjk("xxxx拶", false);
    checkIsCjk("わたしはうぃるぶらんです", true);
    checkIsCjk("遭難:越後駒ケ岳で３人 連絡取れ、救助へ", true);
  }

  @Test
  public void testCjkTokenList() throws Exception {
    List<String> tokens = WordSeparatorAnalyzer.tokenList("遭難:越後駒ケ岳で３人 連絡取れ、救助へ");
    String[] expectJp = {
      "遭難", "越後", "後駒", "駒ケ", "ケ岳", "岳で", "3", "人", "連絡", "絡取", "取れ", "救助", "助へ"
    };
    checkListsEqual(expectJp, tokens);

    tokens = WordSeparatorAnalyzer.tokenList("第 1 条：以用户为中心，其他一切水到渠成。");
    String[] expectCn = {
      "第", "1", "条", "以用", "用户", "户为", "为中", "中心", "其他", "他一", "一切", "切水", "水到", "到渠", "渠成"
    };
    checkListsEqual(expectCn, tokens);

    tokens = WordSeparatorAnalyzer.tokenList("오늘도 전력비상…전력경보 '주의' 발령될 듯");
    String[] expectKr = {"오늘", "늘도", "전력", "력비", "비상", "전력", "력경", "경보", "주의", "발령", "령될", "듯"};
    checkListsEqual(expectKr, tokens);
  }
}
