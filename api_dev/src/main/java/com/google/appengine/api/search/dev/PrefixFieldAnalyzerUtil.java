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

import com.google.apphosting.api.AppEngineInternal;
import com.google.common.base.CharMatcher;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.LetterTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKTokenizer;
import org.apache.lucene.analysis.miscellaneous.EmptyTokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/** Utilities for tokenizing and handling prefix fields. */
@AppEngineInternal
final class PrefixFieldAnalyzerUtil {

  static final Logger LOG = Logger.getLogger(PrefixFieldAnalyzerUtil.class.getCanonicalName());

  static String normalizePrefixField(String value) {
    String normalizedString = Normalizer.normalize(value, Normalizer.Form.NFKC);
    return CharMatcher.whitespace()
        .trimAndCollapseFrom(normalizedString, ' ')
        .toLowerCase(Locale.ROOT);
  }
  
  static List<String> createUntokenizedPrefixes(String value) {
    List<String> prefixes = new ArrayList<>();
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) {
        continue;
      }
      prefixes.add(value.substring(0, i + 1));
    }
    return prefixes;
  }
 
  /**
   * A letter tokenizer that splits on a set of word separators and normalizes according to prefix
   * search rules.
   *
   * The custom set of word separators is chosen to match the word separators used by the Search API
   * backend and is consistent with separators for other text fields.
   */
  static final class PrefixWordSeparatorTokenizer extends LetterTokenizer {
    PrefixWordSeparatorTokenizer(Reader in) {
      super(in);
    }

    @Override
    protected char normalize(char c) {
      String cleaned = normalizePrefixField(Character.toString(c));
      if (cleaned.isEmpty()) {
        return '\'';
      }
      return cleaned.charAt(0);
    }

    /**
     * Collect characters that are not in our word separator set.
     */
    @Override
    protected boolean isTokenChar(char c) {
      return !LuceneUtils.WORD_SEPARATORS.contains(Character.valueOf(c));
    }
  }
  
  /*
   * A Token Stream filter that extracts the prefixes from each token and adds the prefixes as
   * additional tokens in the same position as the token. 
   */
  static final class TokenizedPrefixFilter extends TokenFilter {
    TokenizedPrefixFilter(TokenStream input) {
      super(input);
    }
    private final LinkedList<String> extraTokens = new LinkedList<String>();
    private final TermAttribute termAtt = (TermAttribute) addAttribute(TermAttribute.class);
    private final PositionIncrementAttribute posIncAtt =
        (PositionIncrementAttribute) addAttribute(PositionIncrementAttribute.class);
    private State savedState;

    @Override
    public boolean incrementToken() throws IOException {
      if (!extraTokens.isEmpty()) {
        restoreState(savedState);
        posIncAtt.setPositionIncrement(0);
        termAtt.setTermBuffer(extraTokens.remove());
        return true;
      }
      if (input.incrementToken()) {
        extraTokens.addAll(extractPrefixes(termAtt.term()));
        savedState = captureState();
        return true;
      }
      return false;
    }
  
    private List<String> extractPrefixes(String token) {
      List<String> prefixes = new ArrayList<>();
      for (int i = 0; i < token.length() - 1; i++) {
        prefixes.add(token.substring(0, i + 1));
      }
      return prefixes;
    }
  }
  
  static List<String> tokenizePrefixFieldQuery(String input) {
    List<String> output = new ArrayList<>();
    input = normalizePrefixField(input);
    TokenStream stream = getTokenizedPrefixWordSeparator(new StringReader(input));
    TermAttribute tokenTerm = (TermAttribute) stream.addAttribute(TermAttribute.class);
    try {
      while (stream.incrementToken()) {
        output.add(tokenTerm.term());
      }
    } catch (IOException e) {
        return new ArrayList<String>();
    }
    return output;
  }
  
  static TokenStream getTokenizedPrefixTokenStreamForIndexing(Reader reader) {
    return new TokenizedPrefixFilter(getTokenizedPrefixWordSeparator(reader));
  }

  private static TokenStream getTokenizedPrefixWordSeparator(Reader reader) {
    /** Duplicate reader for use after isProbablyCjk call */
    StringBuilder readerContents = new StringBuilder();
    boolean isCjk;
    try { 
      isCjk = LuceneUtils.isProbablyCjk(reader, readerContents);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to read stream for tokenization.", e);
      return new EmptyTokenStream();
    }
    reader = new StringReader(normalizePrefixField(readerContents.toString()));
    if (isCjk) {
      return new CJKTokenizer(reader);
    }
    return new PrefixWordSeparatorTokenizer(reader); 
  }
}

