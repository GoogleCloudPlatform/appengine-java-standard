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

import static com.google.appengine.apicompat.Utils.classes;
import static com.google.appengine.apicompat.Utils.ctors;
import static com.google.appengine.apicompat.Utils.fields;
import static com.google.appengine.apicompat.Utils.methods;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Set;

/**
 * A utility class for detecting the accessible api of a given {@link Class}. The accessible api of
 * a package-protected, private, or final and protected class is empty. The accessible api of a
 * public or a non-final protected class is defined as:
 *
 * All declared public instance and static methods and members. For non-final classes, all declared
 * protected instance and static methods and members. All public constructors. For non-final
 * classes, all protected constructors. The set of all classes that the api class extends or
 * implements, either directly or indirectly - we call this the inheritanceSet.
 *
 * Note that we are detecting declared constructors, methods, and fields, which excludes anything
 * that is available via inheritance. See the class-level javadoc in {@link
 * ExhaustiveApiUsageVerifier} for an explanation of how this is used.
 *
 */
class AccessibleApiDetector {

  /**
   * Detect the accessible api for the given class.
   *
   * @param apiClass The class whose accessible api we want to detect.
   * @return The accessible api.
   */
  public Api detect(Class<?> apiClass) {
    Set<Class<?>> inheritanceSet = classes();
    Set<Constructor<?>> constructorSet = ctors();
    Set<Method> methodSet = methods();
    Set<Field> fieldSet = fields();
    // short circuit if the provided class is neither public nor protected and non-final
    if (Utils.isAccessible(apiClass)) {
      populateInheritanceSet(apiClass, inheritanceSet);
      int modifiers = apiClass.getModifiers();
      boolean isAbstractClass = !Modifier.isInterface(modifiers) && Modifier.isAbstract(modifiers);
      addAccessible(apiClass.getDeclaredConstructors(), constructorSet);
      addAccessible(apiClass.getDeclaredMethods(), methodSet);
      // If it's an abstract class without an accessible constructor, protected methods aren't
      // actually available to users.
      if (isAbstractClass && constructorSet.isEmpty()) {
        for (Iterator<Method> iter = methodSet.iterator(); iter.hasNext(); ) {
          if (Modifier.isProtected(iter.next().getModifiers())) {
            iter.remove();
          }
        }
      }
      if (isAbstractClass) {
        // If it's an abstract class and any of the abstract methods are not accessible, the
        // constructors aren't actually available to users.
        for (Method m : apiClass.getDeclaredMethods()) {
          if (Modifier.isAbstract(m.getModifiers())&& !methodSet.contains(m)) {
            constructorSet.clear();
            break;
          }
        }
      }
      addAccessible(apiClass.getDeclaredFields(), fieldSet);
    }
    return new Api(apiClass, inheritanceSet, constructorSet, methodSet, fieldSet);
  }

  /**
   * Adds all accessible elements in {@code declared} to {@code accessibleSet}
   *
   * @param declared The declared members (constructors, methods, fields).
   * @param accessibleSet The set to which each member will be added if it is accessible.
   * @param <T> The type of the Member.
   */
  <T extends Member> void addAccessible(T[] declared, Set<T> accessibleSet) {
    for (T member : declared) {
      // Implementations of methods with templatized arguments are duplicated on the implementing
      // class, one version with the templatized type and one version without the templatized type.
      // For example:
      // interface Foo<T> {
      //   void bar(T arg);
      // }
      //
      // class FooImpl implements Foo<String> {
      //   @Override
      //   void bar(String arg) { }
      // }
      //
      // FooImpl.class.getDeclared will return 2 methods:
      //   void bar(Object arg)
      //   void bar(String arg)
      //
      // The one that takes an Object is synthetic and the compiler won't let you call it, so we
      // exclude it from the set of accessible methods.
      if (Utils.isAccessible(member) && !member.isSynthetic()) {
        accessibleSet.add(member);
      }
    }
  }

  /**
   * Fill the provided set with all classes that the api class extends or implements, either
   * directly or indirectly.
   *
   * @param cls The {@link Class} whose inheritance set we will populate.
   * @param inheritanceSet Out param. The set to populate.
   */
  static void populateInheritanceSet(Class<?> cls, Set<Class<?>> inheritanceSet) {
    // We can safely exclude a package protected class from the inheritance set if it doesn't have
    // any public or protected methods because none of its methods will be accessible on its
    // subclasses. However, we're not implementing this right now. It's possible (although unlikely)
    // that we'll flag a change that _should_ be backwards compatible as incompatible as the result
    // of this, and if that happens we'll make the necessary adjustment here.
    for (Class<?> anInterface : cls.getInterfaces()) {
      inheritanceSet.add(anInterface);
      populateInheritanceSet(anInterface, inheritanceSet);
    }

    if (cls.getSuperclass() != null) {
      inheritanceSet.add(cls.getSuperclass());
      populateInheritanceSet(cls.getSuperclass(), inheritanceSet);
    }
  }
}
