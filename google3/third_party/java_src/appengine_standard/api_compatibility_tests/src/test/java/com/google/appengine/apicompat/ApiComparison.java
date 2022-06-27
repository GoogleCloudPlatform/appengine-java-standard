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

package com.google.appengine.apicompat;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * A class that knows how to compare two {@link Api} objects and report the results of that
 * comparison in a human-readable way.
 *
 */
public final class ApiComparison {

  /**
   * The expected api.
   */
  private final Api expected;

  /**
   * The actual api.
   */
  private final Api actual;

  /**
   * A human-readable representation of the difference between the expect and actual
   * {@link Api Apis}.
   */
  private final String diffString;

  public ApiComparison(Api expected, Api actual) {
    this.expected = checkNotNull(expected, "expected cannot be null");
    this.actual = checkNotNull(actual, "actual cannot be null");
    if (!expected.getApiClass().equals(actual.getApiClass())) {
      throw new IllegalArgumentException(
          String.format("Expected class is %s but actual class is %s",
              expected.getApiClass().getName(), actual.getApiClass().getName()));
    }
    ApiDiff<Class<?>> inheritanceSetDiff = getDiff("elements of inheritance hierarchy",
        expected.getInheritanceSet(), actual.getInheritanceSet());
    ApiDiff<Constructor<?>> constructorDiff = getDiff("constructor invocations",
        expected.getConstructors(), actual.getConstructors());
    ApiDiff<Method> methodDiff = getDiff("method invocations", expected.getMethods(),
        actual.getMethods());
    FieldApiDiff fieldDiff = new FieldApiDiff(getDiff("field references", expected.getFields(),
        actual.getFields()));
    this.diffString = buildDiffString(inheritanceSetDiff, constructorDiff, methodDiff, fieldDiff);
  }

  public boolean hasDifference() {
    return !expected.equals(actual);
  }

  /**
   * Build an {@link ApiDiff} that describes the differences between the two provided sets.
   *
   * @param label String label describing the objects we are comparing.
   * @param expected The expected set.
   * @param actual The actual set.
   * @param <T> The type of the elements in the set.
   * @return An {@link ApiDiff}.
   */
  @VisibleForTesting
  static <T> ApiDiff<T> getDiff(String label, Set<T> expected, Set<T> actual) {
    Set<T> missing = new HashSet<T>(expected);
    // We're left with everything we expected but didn't receive.
    missing.removeAll(actual);

    Set<T> extra = new HashSet<T>(actual);
    // We're left with everything we received but didn't expect.
    extra.removeAll(expected);
    return new ApiDiff<T>(label, missing, extra);
  }

  /**
   * @return A human-readable string describing the difference between the expected and actual
   *         apis.
   * @param inheritanceSetDiff An {@link ApiDiff} describing the difference in the inheritance sets
   *        of the expected and actual {@link Api Apis}.
   * @param constructorDiff An {@link ApiDiff} describing the difference in the constructor sets of
   *        the expected and actual {@link Api Apis}.
   * @param methodDiff An {@link ApiDiff} describing the difference in the method sets of the
   *        expected and actual {@link Api Apis}.
   * @param fieldDiff An {@link ApiDiff} describing the difference in the field sets of the
   *        expected and actual {@link Api Apis}.
   */
  private String buildDiffString(ApiDiff<Class<?>> inheritanceSetDiff,
      ApiDiff<Constructor<?>> constructorDiff, ApiDiff<Method> methodDiff,
      ApiDiff<Field> fieldDiff) {
    StringBuilder builder = new StringBuilder("\nResults for " + expected.getApiClass().getName());
    if (inheritanceSetDiff.hasDifference()) {
      builder.append(inheritanceSetDiff);
    }
    if (constructorDiff.hasDifference()) {
      builder.append(constructorDiff);
    }
    if (methodDiff.hasDifference()) {
      builder.append(methodDiff);
    }
    if (fieldDiff.hasDifference()) {
      builder.append(fieldDiff);
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return diffString;
  }

  /**
   * Helper class that describes the difference between two sets.
   *
   * @param <T> The type of the elements in the sets we are comparing.
   */
  @VisibleForTesting
  static class ApiDiff<T> {

    final String label;
    final Set<T> missing;
    final Set<T> extra;
    final String diffString;

    private ApiDiff(String label, Set<T> missing, Set<T> extra) {
      this.label = label;
      this.missing = missing;
      this.extra = extra;
      this.diffString = buildDiffString();
    }

    private String buildDiffString() {
      if (missing.isEmpty() && extra.isEmpty()) {
        return "";
      }

      String missingStr = "";
      if (!missing.isEmpty()) {
        missingStr = String.format(
            "\nMissing %s:\n%s", label, Joiner.on("\n").join(decorateMissing(missing)));
      }

      String extraStr = "";
      if (!extra.isEmpty()) {
        extraStr = String.format("\nUnexpected %s:\n%s", label, Joiner.on("\n").join(extra));
      }
      return String.format("%s%s", missingStr, extraStr);
    }

    /**
     * Template method that allows subclasses to customize how certain errors are reported.
     *
     * @param missing The set of missing elements.
     * @return A set that will be used to build a string describing the missing objects.
     */
    Set<Object> decorateMissing(Set<T> missing) {
      return Sets.<Object>newHashSet(missing);
    }

    boolean hasDifference() {
      return !missing.isEmpty() || !extra.isEmpty();
    }

    @Override
    public String toString() {
      return diffString;
    }
  }

  /**
   * Specialization of {@link ApiDiff} that provides additional information about why certain fields
   * may be reported as missing from the actual set of referenced fields.
   */
  private static class FieldApiDiff extends ApiDiff<Field> {

    private FieldApiDiff(ApiDiff<Field> diff) {
      super(diff.label, diff.missing, diff.extra);
    }

    @Override
    Set<Object> decorateMissing(Set<Field> missing) {
      Set<Object> result = Sets.newHashSet();
      for (Field f : missing) {
        if (Modifier.isFinal(f.getModifiers()) && Modifier.isStatic(f.getModifiers())) {
          String msg = String.format("%s\nThis is a static final field. In order for the usage to "
              + "register, you must assign its value to a member variable "
              + "named\n %s%s. Yes, this is absurd, but the compiler "
              + "inlines references to static final fields so this is our "
              + "next best option.)",
              f.toString(),
              ApiVisitor.API_CONSTANT_PREFIX,
              f.getName());
          result.add(msg);
        } else {
          result.add(f);
        }
      }
      return result;
    }
  }
}
