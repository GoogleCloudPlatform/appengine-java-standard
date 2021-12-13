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

package com.google.apphosting.testing;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Verifies that all public Serializable classes in a jar have a serialVersionUID.
 *
 */
public final class SerialVersionVerifier {
  private final ClassLoader classLoader;
  private final Predicate<CharSequence> namePredicate;

  private final List<String> problems = new ArrayList<>();

  private int matchCount = 0;

  private SerialVersionVerifier(ClassLoader classLoader, Predicate<CharSequence> namePredicate) {
    this.classLoader = classLoader;
    this.namePredicate = namePredicate;
  }

  /**
   * For every public class defined by the given ClassLoader and whose fully-qualified name
   * satisfies the given predicate, check if it is {@link Serializable} and if so whether it
   * defines a <a
   * href="http://docs.oracle.com/javase/8/docs/platform/serialization/spec/class.html#a4100">
   * {@code serialVersionUID}</a>. Enums, interfaces, and abstract classes are exempt from this
   * check.
   *
   * @throws AssertionError if a class fails the check, or if no class satsifies the predicate,
   *     or if there is a problem scanning the classes.
   */
  public static void verify(ClassLoader classLoader, Predicate<CharSequence> namePredicate) {
    SerialVersionVerifier verifier = new SerialVersionVerifier(classLoader, namePredicate);
    try {
      verifier.verifyOrThrow();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    if (!verifier.problems.isEmpty()) {
      throw new AssertionError(String.join("\n", verifier.problems));
    }
    if (verifier.matchCount == 0) {
      throw new AssertionError("No classes matched the predicate");
    }
  }

  private void verifyOrThrow() throws Exception {
    ClassPath classPath = ClassPath.from(classLoader);
    for (ClassInfo classInfo : classPath.getAllClasses()) {
      String className = classInfo.getName();
      if (namePredicate.test(className)) {
        matchCount++;
        try {
          Optional<String> problem = checkClass(className);
          if (problem.isPresent()) {
            problems.add(problem.get());
          }
        } catch (NoClassDefFoundError ignored) {
          // This can happen for example if the parent class or the type of one of the fields
          // is not visible to the ClassLoader. Just ignore it; at worst we'll miss some class that
          // should have had serialVersionUID.
        }
      }
    }
  }

  private Optional<String> checkClass(String className) throws ReflectiveOperationException {
    Class<?> c = Class.forName(className, false, classLoader);
    if (Modifier.isPublic(c.getModifiers())
        && !Modifier.isAbstract(c.getModifiers())
        && Serializable.class.isAssignableFrom(c)
        && !Enum.class.isAssignableFrom(c)) {
      try {
        Field serialVersionUID = c.getDeclaredField("serialVersionUID");
        if (!Modifier.isStatic(serialVersionUID.getModifiers())) {
          return Optional.of("Field " + className + ".serialVersionUID is not static");
        }
        Class<?> type = serialVersionUID.getType();
        if (type != long.class) {
          return Optional.of(
              "Field " + className + ".serialVersionUID should have type long, not " + type);
        }
      } catch (NoSuchFieldException e) {
        return Optional.of(
            "Public serializable class " + className + " does not have a serialVersionUID");
      }
    }
    return Optional.empty();
  }
}
