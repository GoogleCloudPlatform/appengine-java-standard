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

import org.jspecify.annotations.Nullable;

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state. This allows constructs such as
 *
 * <pre>{@code
 * if (count <= 0) {
 *   throw new IllegalArgumentException("must be positive: " + count);
 * }
 * }</pre>
 *
 * to be replaced with the more compact
 *
 * <pre>
 *     {@code checkArgument(count > 0, "must be positive: %s", count);}</pre>
 *
 * <p>Note that the sense of the expression is inverted; with {@code Preconditions} you declare what
 * you expect to be <i>true</i>, just as you do with an <a
 * href="http://java.sun.com/j2se/1.5.0/docs/guide/language/assert.html">{@code assert}</a> or a
 * JUnit {@code assertTrue} call.
 *
 * <p><b>Note:</b>This class is a copy of a very old version of Guava's Preconditions. Please use
 * the current Guava version instead.
 *
 * @see
 *     "https://google.github.io/guava/releases/21.0/api/docs/com/google/common/base/Preconditions.html"
 */
public final class Preconditions {
  private Preconditions() {}

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)} if not null (a null will not be converted).
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression, @Nullable Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(
          errorMessage != null ? String.valueOf(errorMessage) : null);
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed using {@link String#format(String, Object...)}. if not null (a null will
   *     not be converted).
   * @param errorMessageArgs the arguments to be substituted into the message template.
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(
      boolean expression,
      @Nullable String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(
          errorMessageTemplate != null
              ? String.format(errorMessageTemplate, errorMessageArgs)
              : null);
    }
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)}
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference, @Nullable Object errorMessage) {
    if (reference == null) {
      throw new NullPointerException(errorMessage != null ? String.valueOf(errorMessage) : null);
    }
    return reference;
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   *
   * @param expression a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *     message is formed using {@link String#format(String, Object...)}. if not null (a null will
   *     not be converted).
   * @param errorMessageArgs the arguments to be substituted into the message template.
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(
      boolean expression,
      @Nullable String errorMessageTemplate,
      @Nullable Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(
          errorMessageTemplate != null
              ? String.format(errorMessageTemplate, errorMessageArgs)
              : null);
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *     string using {@link String#valueOf(Object)} if not null (a null will not be converted).
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression, @Nullable String errorMessage) {
    if (!expression) {
      throw new IllegalStateException(errorMessage != null ? String.valueOf(errorMessage) : null);
    }
  }
}
