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

import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PrefixFieldAnalyzerUtil} */
@RunWith(JUnit4.class)
public class PrefixFieldAnalyzerUtilTest {

  @Test
  public void testTokenizedPrefixFieldsTokenStream() throws Exception {
    String[] tokens = {"qui", "q", "qu", "brown", "b", "br", "bro", "brow"};
    int[] positionIncs = {1, 0, 0, 1, 0, 0, 0, 0};
    int[] startOffsets = {0, 0, 0, 4, 4, 4, 4, 4};
    int[] endOffsets = {3, 3, 3, 9, 9, 9, 9, 9};
    
    String fieldValue = "Qui Brown";
    TokenStream stream = PrefixFieldAnalyzerUtil.getTokenizedPrefixTokenStreamForIndexing(
        new StringReader(fieldValue));
    TermAttribute tokenTerm = (TermAttribute) stream.addAttribute(TermAttribute.class);
    OffsetAttribute offsetAttribute = (OffsetAttribute) stream.getAttribute(OffsetAttribute.class);
    PositionIncrementAttribute posIncAtt =
        (PositionIncrementAttribute) stream.addAttribute(PositionIncrementAttribute.class);

    int i = 0;
    while (stream.incrementToken()) {
      String term = tokenTerm.term();
      assertThat(term).isEqualTo(tokens[i]);
      assertThat(posIncAtt.getPositionIncrement()).isEqualTo(positionIncs[i]);
      assertThat(offsetAttribute.startOffset()).isEqualTo(startOffsets[i]);
      assertThat(offsetAttribute.endOffset()).isEqualTo(endOffsets[i]);
      i++;
    }
  }
}

