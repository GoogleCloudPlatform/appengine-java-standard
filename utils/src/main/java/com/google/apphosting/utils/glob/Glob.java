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

package com.google.apphosting.utils.glob;

import java.util.regex.Pattern;

/**
 * Encapsulates a string pattern that contains zero or more wildcard
 * characters ({@code '*'}).  These characters can appear at the
 * beginning of the string, the end of the string, or at arbitrary
 * points in the middle of the string.
 *
 * <p>Each {@code Glob} also contains zero or more properties
 * expressed as a property name and an {@link Object} value.  "Leaf
 * globs", that is globs provided as inputs to {@link GlobInterceptor}
 * have properties that are specified explicitly by clients.  "Branch
 * globs", that is the outputs from {@link GlobInterceptor} which may
 * each represent one or more leaf globs, have dynamic properties that
 * are resolved from the leaf globs properties using a {@link
 * ConflictResolver}.
 *
 */
public interface Glob {
  /**
   * Returns this pattern as a string.
   */
  String getPattern();

  /**
   * Returns this pattern as a regular expression.
   */
  Pattern getRegularExpression();

  /**
   * Returns true if this glob pattern is a literal string that
   * contains no wildcard characters.
   */
  boolean isLiteral();

  /**
   * Returns true if {@code string} matches this glob pattern.
   */
  boolean matches(String string);

  /**
   * Returns true if for every possible string that this pattern
   * matches, the supplied glob also matches that string.  In other
   * words, return true if this glob is a strict superset of {@code
   * glob}.
   */
  boolean matchesAll(Glob glob);

  /**
   * Returns true if for every possible string that this pattern
   * matches, the supplied pattern also matches that string.  In other
   * words, return true if this glob's pattern is a strict superset of
   * {@code pattern}.
   */
  boolean matchesAll(String pattern);

  /**
   * Chooses a value for {@code property} from the properties that the
   * user supplied when creating this {@code Glob} (or its children).
   * If there are conflicting values for {@code property} across this
   * glob's children, {@code resolver} is used to decide on a single
   * value.
   */
  Object getProperty(String property, ConflictResolver resolver);
}
