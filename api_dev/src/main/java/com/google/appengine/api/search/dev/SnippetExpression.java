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

import com.google.apphosting.api.search.DocumentPb.FieldValue;
import com.google.apphosting.api.search.DocumentPb.FieldValue.ContentType;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.document.Document;

/**
 * Expression which generates snippets from specified document.
 *
 */
public class SnippetExpression extends Expression {

  /** Marker for start and end truncation point for snippet. */
  private static final String ELLIPSIS = "<b>...</b>";

  /** Start marker of highlighted token. */
  private static final String TOKEN_START = "<b>";

  /** End marker of highlighted token. */
  private static final String TOKEN_END = "</b>";

  /**
   * Large enough value outside of input text, but also less enough so that sum
   * of the value and token size is less than Integer.MAX_VALUE.
   */
  private static final int INVALID = Integer.MAX_VALUE / 2;

  /** Pattern matching special html characters, which require escaping. */
  private static final Pattern HTML_SPECIAL_CHARS_PATTERN = Pattern.compile("['\"&<>]");

  /** Array of full lucene names of the field */
  private final List<String> luceneFields;

  /**
   * Expression which evaluates to the maximum character limit for the snippet.
   */
  private final NumericExpression maxCharsExpression;

  /**
   * Expression which evaluates to the maximum number of snippet parts in
   * the snippet.
   */
  private final NumericExpression maxSnippetsExpression;

  /** Mapping from string tokens to token integer ids. */
  private final Map<String, Integer> tokenIds;

  /**
   * Token state for iteration of token occurencies in text. It contains
   * current token position and the offset in list of token positions.
   */
  private final TokenState[] tokenStates;

  private static class TokenState implements Comparable<TokenState> {
    private final int size;
    private final List<Integer> tokenOffsets;

    private int currentTokenOffsetsPosition;
    private int currentOffset;

    public TokenState(String text) {
      this.size = text.length();
      tokenOffsets = new ArrayList<Integer>();
    }

    public void reset() {
      tokenOffsets.clear();
      currentOffset = INVALID;
      currentTokenOffsetsPosition = INVALID;
    }

    /**
     * Add an offset to the list offsets the token appears in current document.
     */
    public void addOffset(int offset) {
      tokenOffsets.add(offset);
    }

    /**
     * Initializes token state for iteration and returns token end offset.
     * @return first token occurence end offset or INVALID if token
     * was not found.
     */
    public int startIteration() {
      if (tokenOffsets.isEmpty()) {
        return INVALID;
      }
      currentTokenOffsetsPosition = 0;
      currentOffset = tokenOffsets.get(currentTokenOffsetsPosition);
      return currentOffset + size;
    }

    /**
     * @return current token start offset or INVALID if no more tokens.
     */
    public int getCurrentOffset() {
      return currentOffset;
    }

    /**
     * @return current token end offset or INVALID + size if no more tokens.
     */
    public int getCurrentEndOffset() {
      return currentOffset + size;
    }

    /**
     * Advances internal pointer to next token occurence and returns the token
     * end offset.
     * @return next token end offset or INVALID if no more token
     * occurences found.
     */
    public int nextEndOffset() {
      currentTokenOffsetsPosition++;
      if (currentTokenOffsetsPosition < tokenOffsets.size()) {
        currentOffset = tokenOffsets.get(currentTokenOffsetsPosition);
        return currentOffset + size;
      } else {
        currentOffset = INVALID;
        return currentOffset;
      }
    }

    @Override
    public int compareTo(TokenState otherToken) {
      return Integer.compare(currentOffset, otherToken.currentOffset);
    }
  }

  private SnippetExpression(
      List<String> tokens,
      List<String> luceneFields,
      NumericExpression maxCharsExpression,
      NumericExpression maxSnippetsExpression) {
    this.tokenIds = new HashMap<String, Integer>();
    this.tokenStates = new TokenState[tokens.size()];
    int id = 0;
    for (String token : tokens) {
      // TODO: language dependent
      this.tokenIds.put(token.toUpperCase(), id);
      this.tokenStates[id] = new TokenState(token);
      id++;
    }
    this.luceneFields = luceneFields;
    this.maxCharsExpression = maxCharsExpression;
    this.maxSnippetsExpression = maxSnippetsExpression;
  }

  public static Expression makeSnippetExpression(
      String query,
      String fieldName,
      Set<ContentType> fieldTypes,
      NumericExpression maxCharsExpression,
      NumericExpression maxSnippetsExpression) {
    List<String> luceneFields = new ArrayList<String>(fieldTypes.size());
    if (fieldTypes.contains(ContentType.TEXT)) {
      luceneFields.add(LuceneUtils.makeLuceneFieldNameWithExtractedText(
          fieldName, ContentType.TEXT));
    }
    if (fieldTypes.contains(ContentType.HTML)) {
      luceneFields.add(LuceneUtils.makeLuceneFieldNameWithExtractedText(
          fieldName, ContentType.HTML));
    }
    if (fieldTypes.contains(ContentType.ATOM)) {
      luceneFields.add(LuceneUtils.makeLuceneFieldNameWithExtractedText(
          fieldName, ContentType.ATOM));
    }
    if (luceneFields.isEmpty()) {
      throw new IllegalArgumentException("Can only snippet TEXT, HTML, and ATOM fields");
    }

    List<String> tokens = new SnippetExpressionQueryParser(fieldName).parse(query);
    if (tokens == null) {
      return new ExpressionBuilder.EmptyExpression();
    }
    return new SnippetExpression(
        tokens, luceneFields, maxCharsExpression, maxSnippetsExpression);
  }

  private String findField(Document doc) throws EvaluationException {
    for (String luceneFieldName : luceneFields) {
      String[] values = doc.getValues(luceneFieldName);
      if (values.length != 0) {
        return values[0];
      }
    }
    throw new EvaluationException("no text or html field found in the document");
  }

  @VisibleForTesting
  void addHtmlEscaped(StringBuilder result, String text, int start, int end) {
    if (start > end) {
      return;
    }

    Matcher matcher = HTML_SPECIAL_CHARS_PATTERN.matcher(text).region(start, end);
    while (matcher.find()) {
      int matchStart = matcher.start();
      result.append(text, start, matchStart);
      String replaceWith = null;
      switch (text.charAt(matchStart)) {
        case '\'': replaceWith = "&#39;"; break;
        case '"': replaceWith = "&quot;"; break;
        case '&': replaceWith = "&amp;"; break;
        case '<': replaceWith = "&lt;"; break;
        case '>': replaceWith = "&gt;"; break;
        default: throw new RuntimeException("internal error");
      }
      result.append(replaceWith);
      start = matchStart + 1;
    }
    if (start < end) {
      result.append(text, start, end);
    }
  }

  private void addText(StringBuilder result, String text, int start, int end, int limit) {
    addHtmlEscaped(result, text, start, Math.min(end, limit));
  }

  private void addHighlighted(StringBuilder result, String text, int start, int end, int limit) {
    if (start > limit) {
      return;
    }
    result.append(TOKEN_START);
    addText(result, text, start, end, limit);
    result.append(TOKEN_END);
  }

  private String formatSnippet(
      String text, int startPos, int size, int maxChars, int maxSnippets) {
    StringBuilder result = new StringBuilder();
    PriorityQueue<TokenState> tokenMinHeap = new PriorityQueue<TokenState>();

    for (TokenState tokenState : tokenStates) {
      tokenState.startIteration();
      tokenMinHeap.add(tokenState);
    }

    int endPos = startPos + size;

    // Increase snippet size if it smaller than maximum allowed size
    if (size < maxChars) {
      int extra = (maxChars - size) / 2;
      startPos -= extra;
      endPos += extra;
    }

    // Clip new starting and ending position to be within document and snippet
    // size limits.
    if (startPos < 0) {
      startPos = 0;
    }
    if (endPos - startPos > maxChars) {
      endPos = startPos + maxChars;
    }
    if (endPos > text.length()) {
      endPos = text.length();
    }

    if (startPos != 0) {
      result.append(ELLIPSIS);
    }

    int currentPos = startPos;

    while (true) {
      TokenState minToken = tokenMinHeap.poll();
      int tokenStartOffset = minToken.getCurrentOffset();
      int tokenEndOffset = minToken.getCurrentEndOffset();

      if (currentPos > tokenEndOffset) {
        // ignore token at left of currentPos
      } else if (currentPos > tokenStartOffset) {
        addHighlighted(result, text, currentPos, tokenEndOffset, endPos);
        currentPos = tokenEndOffset;
      } else { // currentPos <= tokenStartOffset
        addText(result, text, currentPos, tokenStartOffset, endPos);
        addHighlighted(result, text, tokenStartOffset, tokenEndOffset, endPos);
        currentPos = tokenEndOffset;
      }
      if (currentPos >= endPos) {
        break;
      }

      minToken.nextEndOffset();
      tokenMinHeap.add(minToken);
    }

    if (endPos != text.length()) {
      result.append(ELLIPSIS);
    }
    return result.toString();
  }

  @VisibleForTesting
  String makeSnippet(String text, int maxChars, int maxSnippets) {
    for (int i = 0; i < tokenStates.length; i++) {
      tokenStates[i].reset();
    }

    @SuppressWarnings("deprecation")
    StandardTokenizer tokenStream = new StandardTokenizer(new StringReader(text));
    OffsetAttribute offsetAttribute =
        (OffsetAttribute) tokenStream.getAttribute(OffsetAttribute.class);
    TermAttribute termAttribute = (TermAttribute) tokenStream.getAttribute(TermAttribute.class);

    try {
      while (tokenStream.incrementToken()) {
        // TODO: language dependent
        String term = termAttribute.term().toUpperCase();
        Integer id = tokenIds.get(term);
        if (id == null) {
          // Unknown token
          continue;
        }
        int startOffset = offsetAttribute.startOffset();
        tokenStates[id].addOffset(startOffset);
      }
    } catch (IOException e) {
      throw new RuntimeException("internal error");
    }

    PriorityQueue<TokenState> tokenMinHeap = new PriorityQueue<TokenState>();
    int maxEndOffset = 0;
    int minSnippetSize = text.length();
    int minSnippetOffset = 0;

    for (TokenState tokenState : tokenStates) {
      int endOffset = tokenState.startIteration();
      // ignore not found tokens
      if (endOffset == INVALID) {
        continue;
      }
      maxEndOffset = Math.max(maxEndOffset, endOffset);
      tokenMinHeap.add(tokenState);
    }

    // None of the tokens found
    if (tokenMinHeap.peek() == null) {
      return "";
    }

    while (true) {
      TokenState minToken = tokenMinHeap.poll();
      int minOffset = minToken.getCurrentOffset();
      int snippetSize = maxEndOffset - minOffset;

      if (minSnippetSize > snippetSize) {
        minSnippetSize = snippetSize;
        minSnippetOffset = minOffset;
      }

      maxEndOffset = Math.max(maxEndOffset, minToken.nextEndOffset());
      if (maxEndOffset == INVALID) {
        break;
      }
      tokenMinHeap.add(minToken);
    }
    return formatSnippet(text, minSnippetOffset, minSnippetSize, maxChars, maxSnippets);
  }

  public String evalHtml(Document doc) throws EvaluationException {
    String fieldText = findField(doc);
    if (fieldText == null) {
      return null;
    }
    int maxChars = (int) maxCharsExpression.evalDouble(doc);
    int maxSnippets = (int) maxSnippetsExpression.evalDouble(doc);
    if (maxChars <= 0) {
      return null;
    }
    return makeSnippet(fieldText, maxChars, maxSnippets);
  }

  @Override
  public FieldValue eval(Document doc) throws EvaluationException {
    String html = evalHtml(doc);
    if (html == null) {
      return null;
    }
    return makeValue(ContentType.HTML, html);
  }

  @Override
  public List<Sorter> getSorters(
      final int sign, double defaultValueNumeric, final String defaultValueText) {
    List<Sorter> sorters = new ArrayList<Sorter>(1);
    sorters.add(new Sorter() {
      @Override
      public Object eval(Document doc) {
        try {
          return evalHtml(doc);
        } catch (EvaluationException e) {
          return defaultValueText;
        }
      }

      @Override
      public int compare(Object left, Object right) {
        String leftHtml = (String) left;
        String rightHtml = (String) right;
        return sign * leftHtml.compareToIgnoreCase(rightHtml);
      }
    });
    return sorters;
  }
}
