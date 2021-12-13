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

import java.util.NoSuchElementException;

/**
 * A helper class that holds various, state-less utility
 * functions used by the query parser.
 *
 */
public class ParserUtils {

  /**
   * Keeps the number of days per month for {@link #isDate(CharSequence)} method.
   */
  private static int[] MONTH_LENGTH = {
    31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
  };

  /**
   * An iterator over characters of a quote-delimited phrase. If the phrase contains escaped
   * sequences, such as "\\\"", "\\\'", "\\t", etc. this iterator converts them to regular
   * characters.
   */
  private static class PhraseCharIterator {

    private final CharSequence text;
    /** Index within {@link text} of the next character to be returned. */
    private int i;
    /** Number of characters, excluding the (presumed) start/end quote marks. */
    private final int n;
    private char leftOver;

    public PhraseCharIterator(CharSequence text) {
      this.text = text;
      i = 1;
      n = text.length() - 2;
      leftOver = 0;
    }

    private static boolean isOctal(char c) {
      return '0' < c && c < '8';
    }

    public boolean hasNext() {
      return leftOver != 0 || i <= n;
    }

    public char next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      char c;
      if (leftOver != 0) {
        c = leftOver;
        leftOver = 0;
      } else {
        c = text.charAt(i++);
        if (c == '\\') {
          if (i <= n) {
            c = text.charAt(i++);
            switch (c) {
              case '\'':
                c = '\'';
                break;
              case '\"':
                c = '\"';
                break;
              case 'u':
                if (i + 3 <= n) {
                  try {
                    c = toChar(Integer.parseInt(text.subSequence(i, i + 4).toString(), 16));
                    i += 4;
                  } catch (NumberFormatException e) {
                    c = '\\';
                    leftOver = 'u';
                  }
                } else {
                  c = '\\';
                  leftOver = 'u';
                }
                break;
              default:
                if (!isOctal(c)) {
                  leftOver = c;
                  c = '\\';
                } else {
                  int codeSoFar = (c - '0');
                  int countSoFar = 1;
                  while (i <= n && countSoFar < 3) {
                    char nextChar = text.charAt(i++);
                    if (!isOctal(nextChar)) {
                      leftOver = nextChar;
                      break;
                    }
                    codeSoFar = codeSoFar * 8 + (nextChar - '0');
                    ++countSoFar;
                  }
                  c = toChar(codeSoFar);
                }
                break;
            }
          } else {
            c = '\\';
          }
        }
      }
      return c;
    }

    private static char toChar(int code) {
      char[] decoded = Character.toChars(code);
      if (decoded.length > 1) {
        throw new RuntimeException(
            "Decoded " + code + " does not return a single character");
      }
      return decoded[0];
    }
  }

  /** No instances of parser utils. */
  private ParserUtils() {}

  /** Removes the last character from the given text */
  public static String trimLast(String text) {
    return text.substring(0, text.length() - 1);
  }

  /** Extracts phrase text by removing quotes from either end, and interpreting escape sequences. */
  public static String unescapePhraseText(CharSequence phrase) {
    PhraseCharIterator iter = new PhraseCharIterator(phrase);
    StringBuilder builder = new StringBuilder(phrase.length());
    while (iter.hasNext()) {
      builder.append(iter.next());
    }
    return builder.toString();
  }

  /**
   * Returns whether or not the given text looks like a number.
   * The number is defined as
   * '-'? digit* ('.' digit* ('E' ('+' | '-')? digit+)?)?
   *
   * @param text the text tested if it looks like a number
   * @return whether or not the text represents a floating point number
   */
  public static boolean isNumber(CharSequence text) {
    if (text == null || text.length() == 0) {
      return false;
    }
    int i = 0;
    // Optional '-'
    if (text.charAt(0) == '-') {
      if (text.length() == 1) {
        return false;
      }
      ++i;
    }
    // Digits before decimal point.
    i = consumeDigits(i, text);
    if (i >= text.length()) {
      return true;
    }
    // Decimal point.
    if (text.charAt(i) == '.') {
      i = consumeDigits(i + 1, text);
    }
    if (i >= text.length()) {
      return true;
    }
    // Exponent.
    if (text.charAt(i) != 'E' && text.charAt(i) != 'e') {
      return false;
    }
    if (++i >= text.length()) {
      return false;
    }
    if (text.charAt(i) == '+' || text.charAt(i) == '-') {
      if (++i >= text.length()) {
        return false;
      }
    }
    return consumeDigits(i, text) >= text.length();
  }

  private static int consumeDigits(int i, CharSequence text) {
    while (i < text.length() && Character.isDigit(text.charAt(i))) {
      ++i;
    }
    return i;
  }

  /**
   * Returns if the given string looks like a date to us. We only accept
   * ISO 8601 dates, which have the dddd-dd-dd format.
   *
   * @param text text checked if it looks like a date
   * @return whether this could be an ISO 8601 date
   */
  // TODO: replace this with a call to some standard date-parsing library,
  // and consider making it sensitive to user's locale
  public static boolean isDate(CharSequence text) {
    if (text == null || text.length() == 0) {
      return false;
    }
    int year = 0;
    int i = 0;
    char c = '\0';
    if (text.charAt(i) == '-') {
      // Consume dash preceding year.
      i++;
    }
    while (i < text.length()) {
      c = text.charAt(i++);
      if (!Character.isDigit(c)) {
        break;
      }
      year = year * 10 + (c - '0');
      if (year > 9999) {
        return false;
      }
    }
    if (i >= text.length()) {
      return false;
    }
    if (c != '-') {
      return false;
    }
    int month = 0;
    while (i < text.length()) {
      c = text.charAt(i++);
      if (!Character.isDigit(c)) {
        break;
      }
      month = month * 10 + (c - '0');
      if (month > 12) {
        return false;
      }
    }
    if (month <= 0) {
      return false;
    }
    if (i >= text.length()) {
      return false;
    }
    if (c != '-') {
      return false;
    }
    int day = 0;
    while (i < text.length()) {
      c = text.charAt(i++);
      if (!Character.isDigit(c)) {
        return false;
      }
      day = day * 10 + (c - '0');
    }
    if (day <= 0) {
      return false;
    }
    if (month == 2) {
      if ((year % 400 == 0) || (year % 100 != 0 && year % 4 == 0)) {
        return day <= 29;
      }
    }
    return day <= MONTH_LENGTH[month - 1];
  }
}
