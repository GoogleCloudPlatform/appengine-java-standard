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
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LetterTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKTokenizer;
import org.apache.lucene.analysis.miscellaneous.EmptyTokenStream;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * A custom analyzer to tokenize text like the Search API backend.
 *
 * It detects when provided text is in a CJK language and uses {@link
 * CJKTokenizer} to tokenize it if it is. {@link CJKTokenizer} tokenizes based
 * on bigrams, so a string like "ABCD" will be tokenized to ["A", "AB", "BC",
 * "CD", "D"]. If the string is not CJK, we assume that it uses standard latin
 * word separators.  For latin text, this uses a slightly-customized
 * LetterTokenizer and passes tokens through StandardFilter and
 * LowerCaseFilter.  The LetterTokenizer is customized to use the same word
 * separators as ST-BTI.
 */
@AppEngineInternal
public class WordSeparatorAnalyzer extends Analyzer {

  static final Logger LOG = Logger.getLogger(WordSeparatorAnalyzer.class.getCanonicalName());


  /**
   * A letter tokenizer that splits on a set of word separators.
   *
   * The custom set of word separators is chosen to match the word separators used by the Search API
   * backend.
   */
  private class WordSeparatorTokenizer extends LetterTokenizer {
    public WordSeparatorTokenizer(Reader in) {
      super(in);
    }

    @Override
    protected char normalize(char c) {
      String cleaned = removeDiacriticals(Character.toString(c));
      if (cleaned.isEmpty()) {
        return '\'';
      }
      return Character.toLowerCase(cleaned.charAt(0));
    }

    /** Collect characters that are not in our word separator set. */
    @Override
    protected boolean isTokenChar(char c) {
      return !LuceneUtils.WORD_SEPARATORS.contains(c);
    }
  }

  private final boolean detectCjk;

  /**
   * Create a new WordSeparatorAnalyzer.
   *
   * @param detectCjk If true, will attempt to detect and segment CJK. If false, assumes all text
   * can be segmented using word separators.
   */
  public WordSeparatorAnalyzer(boolean detectCjk) {
    this.detectCjk = detectCjk;
  }

  /**
   * Create a new WordSeparatorAnalyzer that always tries to detect CJK.
   */
  public WordSeparatorAnalyzer() {
    this(true);
  }

  /**
   * Constructs a tokenizer that can tokenize CJK or latin text.
   *
   * @param fieldName Ignored.
   * @param reader A stream to tokenize. mark() and reset() support is not needed.
   *
   * @return A {@link TokenStream} that represents the tokenization of the data in reader.
   */
  @Override
  public TokenStream tokenStream(String fieldName, Reader reader) {
    /* We need to create a duplicate of the reader, because we need to read it twice; once for
     * determining whether its contents are CJK, and then again for tokenization. isProbablyCjk does
     * this by copying bytes from the reader to a StringBuilder as it reads the reader. */
    StringBuilder readerContents = new StringBuilder();

    if (detectCjk) {
      boolean isCjk;
      try {
        isCjk = LuceneUtils.isProbablyCjk(reader, readerContents);
      } catch (IOException e) {
        /* As the reader is a view to an in-memory document, IOExceptions shouldn't happen. If they
         * do, log and return an empty stream. */
        LOG.log(Level.SEVERE, "Failed to read stream for tokenization.", e);
        return new EmptyTokenStream();
      }

      /* Replace reader with a different Reader that has the same state that reader did when the
       * method was called. */
      reader = new StringReader(readerContents.toString());
      if (isCjk) {
        return new CJKTokenizer(reader);
      }
    }

    WordSeparatorTokenizer tokenStream = new WordSeparatorTokenizer(reader);
    return new StandardFilter(tokenStream);
  }

  /**
   * Returns a list of tokens for a string.
   */
  public static List<String> tokenList(String tokenizeString) {
    WordSeparatorAnalyzer analyzer = new WordSeparatorAnalyzer();
    TokenStream stream = analyzer.tokenStream("", new StringReader(tokenizeString));
    TermAttribute tokenTerm = (TermAttribute) stream.addAttribute(TermAttribute.class);

    ArrayList<String> tokens = new ArrayList<String>();
    try {
      while (stream.incrementToken()) {
        String term = tokenTerm.term();
        tokens.add(term);
      }
    } catch (IOException e) {
      return new ArrayList<String>();
    }

    return tokens;
  }

  /**
   * Transforms to lowercase and replaces all word separators with spaces.
   */
  public static String normalize(String tokenizeString) {
    StringBuilder builder = new StringBuilder();
    List<String> tokens = tokenList(tokenizeString);
    for (int i = 0; i < tokens.size(); i++) {
      builder.append(tokens.get(i));
      if (i != tokens.size() - 1) {
        builder.append(" ");
      }
    }
    return builder.toString();
  }

  /**
   * Removes all diacritical marks from the input.
   *
   * This has the effect of transforming marked glyphs into their "equivalent" non-marked form. For
   * example, "éøç" becomes "eoc".
   */
  public static String removeDiacriticals(String input) {
    return DIACRITICIAL_MARKS
        .matcher(Normalizer.normalize(input, Normalizer.Form.NFD))
        .replaceAll("");
  }

  private static final Pattern DIACRITICIAL_MARKS =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
}
