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

import com.google.apphosting.api.AppEngineInternal;
import com.google.apphosting.api.search.DocumentPb;
import com.google.common.base.Preconditions;

/**
 * Provides checks for Facet names and values: atom or number.
 */
@AppEngineInternal
public final class FacetChecker {
  /*
   * Note: Because a Facet is considered a special kind of Field, most of the checker
   * methods in this class will call appropriate methods in {@link FieldChecker} class.
   * If the facet checkers needs special treatment in future, this behaviour will
   * be changed.
   */

  /**
   * Checks whether a facet name is valid. The facet name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} inclusive, and it should match
   * {@link SearchApiLimits#FIELD_NAME_PATTERN}.
   *
   * @param name the facet name to check
   * @return the checked facet name
   * @throws IllegalArgumentException if the facet name is null or empty
   * or is longer than {@literal SearchApiLimits#MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FIELD_NAME_PATTERN}.
   */
  public static String checkFacetName(String name) {
    return checkFacetName(name, "facet name");
  }

  /**
   * Checks whether a facet name is valid. The facet name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} inclusive, and it should match
   * {@link SearchApiLimits#FIELD_NAME_PATTERN}.
   *
   * @param name the facet name to check
   * @param callerContext the caller context used for creating error message in case of a failure.
   * @return the checked facet name
   * @throws IllegalArgumentException if the facet name is empty
   * or is longer than {@literal SearchApiLimits#MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FIELD_NAME_PATTERN}.
   */
  public static String checkFacetName(String name, String callerContext) {
    Preconditions.checkNotNull(name, "Name is null");
    return FieldChecker.checkFieldName(name, callerContext);
  }

  /**
   * Checks whether an atom is valid. An atom can be null or a string between
   * 1 and {@literal SearchApiLimits.MAXIMUM_ATOM_LENGTH} in length, inclusive.
   *
   * @param value the atom value to check
   * @return the checked atom
   * @throws IllegalArgumentException if atom is too long or too short (i.e. empty)
   */
  public static String checkAtom(String value) {
    Preconditions.checkNotNull(value, "Value is null");
    FieldChecker.checkAtom(value);
    // facet atoms also cannot be empty string. This is an additional check to field atom checker.
    Preconditions.checkArgument(!value.isEmpty(), "Facet value is empty");
    return value;
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
    return FieldChecker.checkNumber(value);
  }

  /**
   * Checks whether a facet value is valid.
   *
   * @param value the facet value to check
   * @return the checked facet value
   * @throws IllegalArgumentException if the facet value type is not recognized or
   * if the facet value string is not valid based on the type. See {@link #checkNumber}
   *     and {@link #checkAtom}.
   */
  public static DocumentPb.FacetValue checkFacetValue(DocumentPb.FacetValue value) {
    if (value != null) {
      switch (value.getType()) {
        case ATOM:
          checkAtom(value.getStringValue());
          break;
        case NUMBER:
          checkNumber(Double.parseDouble(value.getStringValue()));
          break;
        default:
          throw new IllegalArgumentException("Unsupported facet type: " + value.getType());
      }
    }
    return value;
  }

  public static DocumentPb.Facet checkValid(DocumentPb.Facet facet) {
    checkFacetName(facet.getName());
    checkFacetValue(facet.getValue());
    return facet;
  }

  private FacetChecker() {}
}
