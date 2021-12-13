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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.search.DateUtil;
import com.google.appengine.api.search.query.ExpressionTreeBuilder;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.antlr.runtime.RecognitionException;

/**
 * Provides checks for Field names, language code, and values: text, HTML, atom
 * or date.
 *
 */
public final class FieldChecker {

  /**
   * Checks whether a field name is valid. The field name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} and it should match
   * {@link SearchApiLimits#FIELD_NAME_PATTERN}.
   *
   * @param name the field name to check
   * @return the checked field name
   * @throws IllegalArgumentException if the field name is null or empty
   * or is longer than {@literal SearchApiLimits#MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FIELD_NAME_PATTERN}.
   */
  public static String checkFieldName(String name) {
    return checkFieldName(name, "field name");
  }

  /**
   * Checks whether a field name is valid. The field name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} and it should match
   * {@link SearchApiLimits#FIELD_NAME_PATTERN}.
   *
   * @param name the field name to check
   * @param fieldName the name of the Java field name of the class where
   * name is checked
   * @return the checked field name
   * @throws IllegalArgumentException if the field name is null or empty
   * or is longer than {@literal Field.MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FIELD_NAME_PATTERN}.
   */
  public static String checkFieldName(String name, String fieldName) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "%s cannot be null or empty",
        fieldName);
    Preconditions.checkArgument(
        bytesInString(name) <= SearchApiLimits.MAXIMUM_NAME_LENGTH,
        "%s longer than %s: %s",
        fieldName,
        SearchApiLimits.MAXIMUM_NAME_LENGTH,
        name);
    Preconditions.checkArgument(name.matches(SearchApiLimits.FIELD_NAME_PATTERN),
        "%s should match pattern %s: %s", fieldName, SearchApiLimits.FIELD_NAME_PATTERN, name);
    return name;
  }

  /**
   * Returns the number of bytes in the given string when UTF-8 encoded.
   */
  static int bytesInString(String str) {
    return str.getBytes(UTF_8).length;
  }

  /**
   * Returns true if name matches {@link SearchApiLimits#FIELD_NAME_PATTERN}.
   */
  static boolean nameMatchesPattern(String name) {
    return name.matches(SearchApiLimits.FIELD_NAME_PATTERN);
  }

  /**
   * Checks whether a text is valid. A text can be null, or a string between
   * 0 and {@literal SearchApiLimits.MAXIMUM_TEXT_LENGTH} in length.
   *
   * @param text the text to check
   * @return the checked text
   * @throws IllegalArgumentException if text is too long
   */
  public static String checkText(String text) {
    if (text != null) {
      Preconditions.checkArgument(
          bytesInString(text) <= SearchApiLimits.MAXIMUM_TEXT_LENGTH,
          "Field text longer than maximum length %s",
          SearchApiLimits.MAXIMUM_TEXT_LENGTH);
    }
    return text;
  }

  /**
   * Checks whether a html is valid. A html can be null or a string between
   * 0 and {@literal SearchApiLimits.MAXIMUM_TEXT_LENGTH} in length.
   *
   * @param html the html to check
   * @return the checked html
   * @throws IllegalArgumentException if html is too long
   */
  public static String checkHTML(String html) {
    if (html != null) {
      Preconditions.checkArgument(
          bytesInString(html) <= SearchApiLimits.MAXIMUM_TEXT_LENGTH,
          "html longer than maximum length %s",
          SearchApiLimits.MAXIMUM_TEXT_LENGTH);
    }
    return html;
  }

  /**
   * Checks whether an atom is valid. An atom can be null or a string between
   * 1 and {@literal SearchApiLimits.MAXIMUM_ATOM_LENGTH} in length.
   *
   * @param atom the atom to check
   * @return the checked atom
   * @throws IllegalArgumentException if atom is too long
   */
  public static String checkAtom(String atom) {
    if (atom != null) {
      Preconditions.checkArgument(
          bytesInString(atom) <= SearchApiLimits.MAXIMUM_ATOM_LENGTH,
          "Field atom longer than maximum length %s",
          SearchApiLimits.MAXIMUM_ATOM_LENGTH);
    }
    return atom;
  }

  /**
   * Checks whether a prefix field is valid. A prefix field can be null or a string between
   * 1 and {@literal SearchApiLimits.MAXIMUM_PREFIX_LENGTH} in length.
   *
   * @param prefix the prefix to check
   * @return the checked prefix
   * @throws IllegalArgumentException if prefix is too long
   */
  public static String checkPrefix(String prefix) {
    if (prefix != null) {
      Preconditions.checkArgument(
          bytesInString(prefix) <= SearchApiLimits.MAXIMUM_PREFIX_LENGTH,
          "Field prefix longer than maximum length %s",
          SearchApiLimits.MAXIMUM_PREFIX_LENGTH);
    }
    return prefix;
  }

  /**
   * Checks whether a vector field is valid. A vector field can be an array of any number with size
   * less than SearchApiLimits.VECTOR_FIELD_MAX_SIZE.
   *
   * @param vector the vector to check
   * @throws IllegalArgumentException if vector is too long.
   */
  public static List<Double> checkVector(List<Double> vector) {
    Preconditions.checkArgument(
        vector.size() <= SearchApiLimits.VECTOR_FIELD_MAX_SIZE,
        "Vector size greater than %s",
        SearchApiLimits.VECTOR_FIELD_MAX_SIZE);
    for (Double d : vector) {
      Preconditions.checkNotNull(d, "Vector element cannot be null");
      Preconditions.checkArgument(!d.isNaN() && !d.isInfinite(),
          "Vector element should be a finite number");
    }
    return vector;
  }

  /**
   * Checks whether a number is valid. A number can be null or a value between
   * {@link SearchApiLimits#MINIMUM_NUMBER_VALUE} and {@link SearchApiLimits#MAXIMUM_NUMBER_VALUE},
   * inclusive.
   *
   * @param value the value to check
   * @return the checked number
   * @throws IllegalArgumentException if number is out of range
   */
  public static Double checkNumber(Double value) {
    if (value != null) {
      Preconditions.checkArgument(
          SearchApiLimits.MINIMUM_NUMBER_VALUE <= value,
          "number value, %s, must be greater than or equal to %s",
          value,
          SearchApiLimits.MINIMUM_NUMBER_VALUE);
      Preconditions.checkArgument(
          value <= SearchApiLimits.MAXIMUM_NUMBER_VALUE,
          "number value, %s, must be less than or equal to %s",
          value,
          SearchApiLimits.MAXIMUM_NUMBER_VALUE);
    }
    return value;
  }

  /**
   * Checks whether a date is within range. Date is nullable.
   *
   * @param date the date to check
   * @return the checked date
   * @throws IllegalArgumentException if date is out of range
   */
  public static Date checkDate(Date date) throws IllegalArgumentException {
    if (date != null) {
      Preconditions.checkArgument(
          SearchApiLimits.MINIMUM_DATE_VALUE.compareTo(date) <= 0,
          "date %s must be after %s",
          DateUtil.formatDateTime(date),
          DateUtil.formatDateTime(SearchApiLimits.MINIMUM_DATE_VALUE));
      Preconditions.checkArgument(
          date.compareTo(SearchApiLimits.MAXIMUM_DATE_VALUE) <= 0,
          "date %s must be before %s",
          DateUtil.formatDateTime(date),
          DateUtil.formatDateTime(SearchApiLimits.MAXIMUM_DATE_VALUE));
    }
    return date;
  }

  private static String checkExpressionHelper(String expression, String mode) {
    Preconditions.checkNotNull(expression, "expression cannot be null");
    ExpressionTreeBuilder parser = new ExpressionTreeBuilder();
    try {
      parser.parse(expression);
    } catch (RecognitionException e) {
      String message = String.format("Failed to parse %s expression '%s': "
          + "parse error at line %d position %d",
          mode, expression, e.line, e.charPositionInLine);
      throw new IllegalArgumentException(message);
    }
    return expression;
  }

  /**
   * Checks whether a field expression is not null and is parsable.
   *
   * @param expression the expression to check
   * @return the checked expression
   * @throws IllegalArgumentException if the expression is null, or
   * cannot be parsed
   */
  public static String checkExpression(String expression) {
    return checkExpressionHelper(expression, "field");
  }

  /**
   * Checks whether a sort bexpression is not null and is parsable.
   *
   * @param expression the expression to check
   * @return the checked expression
   * @throws IllegalArgumentException if the expression is null, or
   * cannot be parsed
   */
  public static String checkSortExpression(String expression) {
    return checkExpressionHelper(expression, "sort");
  }

  public static DocumentPb.Field checkValid(DocumentPb.Field field) {
    checkFieldName(field.getName());
    DocumentPb.FieldValue value = field.getValue();
    switch (value.getType()) {
      case TEXT:
        checkText(value.getStringValue());
        break;
      case HTML:
        checkHTML(value.getStringValue());
        break;
      case DATE:
        checkDate(DateUtil.deserializeDate(value.getStringValue()));
        break;
      case ATOM:
        checkAtom(value.getStringValue());
        break;
      case NUMBER:
        checkNumber(Double.parseDouble(value.getStringValue()));
        break;
      case GEO:
        GeoPointChecker.checkValid(value.getGeo());
        break;
      case UNTOKENIZED_PREFIX:
      case TOKENIZED_PREFIX:
        checkPrefix(value.getStringValue());
        break;
      case VECTOR:
        checkVector(value.getVectorValueList());
        break;
      default:
        throw new IllegalArgumentException("Unsupported field type " + value.getType());
    }
    return field;
  }

  /**
   * Returns a {@link Locale} parsed from the given locale string.
   *
   * @param locale a string representation of a {@link Locale}
   * @return a {@link Locale} parsed from the given locale string
   * @throws IllegalArgumentException if the locale cannot be parsed
   */
  public static Locale parseLocale(String locale) {
    if (locale == null) {
      return null;
    }
    String[] parts = locale.split("_", 3);
    if (parts.length == 1) {
      return new Locale(parts[0]);
    }
    if (parts.length == 2) {
      return new Locale(parts[0], parts[1]);
    }
    if (parts.length == 3) {
      return new Locale(parts[0], parts[1], parts[2]);
    }
    throw new IllegalArgumentException("Cannot parse locale " + locale);
  }
}
