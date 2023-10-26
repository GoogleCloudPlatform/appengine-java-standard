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

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;

import com.google.apphosting.api.AppEngineInternal;
import com.google.common.collect.Sets;
import com.google.common.reflect.Reflection;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for the api compatibility framework.
 *
 */
public final class Utils {

  private Utils() {
    // do not instantiate
  }

  /**
   * Helper method for allocating a Set of classes.
   *
   * @param classes Initial values.
   * @return a Set of classes.
   */
  public static Set<Class<?>> classes(Class<?>... classes) {
    return Sets.newHashSet(classes);
  }

  /**
   * Helper method for allocating a Set of constructors.
   *
   * @param ctors Initial values.
   * @return a Set of constructors.
   */
  public static Set<Constructor<?>> ctors(Constructor<?>... ctors) {
    return Sets.newHashSet(ctors);
  }

  /**
   * Helper method for allocating a Set of methods.
   *
   * @param methods Initial values.
   * @return a Set of methods.
   */
  public static Set<Method> methods(Method... methods) {
    return Sets.newHashSet(methods);
  }

  /**
   * Helper method for allocating a Set of fields.
   *
   * @param fields Initial values.
   * @return a Set of fields.
   */
  public static Set<Field> fields(Field... fields) {
    return Sets.newHashSet(fields);
  }

  /**
   * Assembles a set of the Constructors described by the input args.
   *
   * @param cls             The class to which the Constructors belong.
   * @param paramTypesLists An array consisting of constructor signatures. If an element in the
   *                        array is a {@link Class}, it represents a one-arg constructor where the
   *                        type of the arg is that class. If the element is not a {@link Class} it
   *                        is assumed to be a {@code List<Class>}, where the list is the entire
   *                        signature of the constructor. For no-arg constructors, include a null
   *                        element.
   * @return The set of Constructors described by the input args.
   * @throws NoSuchMethodException If a described constructor does not exist.
   */
  static Set<Constructor<?>> getDeclaredConstructors(Class<?> cls, Object... paramTypesLists)
      throws NoSuchMethodException {
    Set<Constructor<?>> result = ctors();
    if (paramTypesLists.length == 0) {
      paramTypesLists = new Object[]{null};
    }
    for (Object paramTypes : paramTypesLists) {
      Class<?>[] types;
      if (paramTypes == null) {
        // no arg constructor
        types = new Class<?>[0];
      } else if (paramTypes instanceof Class) {
        // 1 arg constructor
        types = new Class<?>[] {(Class<?>) paramTypes};
      } else {
        // multi-arg constructor
        @SuppressWarnings("unchecked")
        List<Class<?>> typeList = (List<Class<?>>) paramTypes;
        types = typeList.toArray(new Class<?>[typeList.size()]);
      }
      result.add(cls.getDeclaredConstructor(types));
    }
    return result;
  }

  /**
   * Assembles a set of the Fields described by the input args.
   *
   * @param cls        The class to which the Fields belong.
   * @param fieldNames The names of the fields.
   * @return The set of Fields described by the input args.
   * @throws NoSuchFieldException If a described field does not exist.
   */
  static Set<Field> getDeclaredFields(Class<?> cls, String... fieldNames)
      throws NoSuchFieldException {
    Set<Field> result = fields();
    for (String field : fieldNames) {
      result.add(cls.getDeclaredField(field));
    }
    return result;
  }

  /**
   * Assembles a set of the Methods described by the input args.
   *
   * @param cls                 The class to which the Fields belong.
   * @param methodNamesAndTypes An array consisting of method names and signatures. If an element in
   *                            the array is a {@link String}, it represents the name of the method.
   *                            If an element in the array is a {@link Class} it represents a
   *                            one-arg method where the type of the arg is that class. If the
   *                            element is neither a {@link String} nor a {@link Class} it is
   *                            assumed to be a {@code List<Class>}, where the list is the entire
   *                            signature of the method.
   * @return The set of Methods described by the input args.
   * @throws NoSuchMethodException If a described method does not exist
   */
  @SuppressWarnings("unchecked")
  static Set<Method> getDeclaredMethods(Class<?> cls, Object... methodNamesAndTypes)
      throws NoSuchMethodException {
    Set<Method> result = methods();
    for (int index = 0; index < methodNamesAndTypes.length; ) {
      String methodName = (String) methodNamesAndTypes[index];
      if (index + 1 == methodNamesAndTypes.length ||
          methodNamesAndTypes[index + 1] instanceof String) {
        // no-arg method
        result.add(cls.getDeclaredMethod(methodName));
        index += 1;
      } else {
        // arg method
        List<Class<?>> paramTypes;
        Object typeOrTypes = methodNamesAndTypes[index + 1];
        if (typeOrTypes instanceof Class) {
          // 1 arg method
          paramTypes = Arrays.<Class<?>>asList((Class<?>) typeOrTypes);
        } else {
          // multi-arg method
          paramTypes = (List<Class<?>>) typeOrTypes;
        }
        result.add(
            cls.getDeclaredMethod(methodName, paramTypes.toArray(new Class<?>[paramTypes.size()])));
        index += 2;
      }
    }
    return result;
  }

  /**
   * Determine whether or not the given class is publicly accessible.
   * If a class or its package is annotated with AppEngineInternal then it is also considered
   * as not accessible.
   *
   * @param apiClass The class.
   * @return {@code true} if the given class is accessible, {@code false} otherwise.
   */
  public static boolean isAccessible(Class<?> apiClass) {
    while (apiClass != null) {
      int modifiers = apiClass.getModifiers();
      if (apiClass.isAnnotationPresent(AppEngineInternal.class)
          || (!isPublic(modifiers) && (!isProtected(modifiers) || isFinal(modifiers)))) {
        return false;
      }
      // If the enclosing class isn't accessible it doesn't matter that this class is accessible.
      Class<?> enclosingClass = apiClass.getEnclosingClass();
      if (enclosingClass == null) {
        // For top level classes check if package is annotated with AppEngineInternal
        Package pkg = apiClass.getPackage();
        if (pkg != null) {
          if (pkg.isAnnotationPresent(AppEngineInternal.class)) {
            return false;
          }
        } else {
          // Package may be null if no class from the same package was loaded yet.
          try {
            Class<?> pkgClass =
                Class.forName(Reflection.getPackageName(apiClass) + ".package-info");
            if (pkgClass.isAnnotationPresent(AppEngineInternal.class)) {
              return false;
            }
          } catch (ClassNotFoundException cnfe) {
            // Package-info class does not exists
          }                  
        }        
      }
      apiClass = enclosingClass; 
    }
    return true;
  }

  /**
   * @param member The member
   * @return {@code true} if the {@link Member} with the given modifiers is accessible,
   *         {@code false} otherwise.
   */
  public static boolean isAccessible(Member member) {
    int modifiers = member.getModifiers();
    return Modifier.isPublic(modifiers) ||
        (Modifier.isProtected(modifiers) &&
            !Modifier.isFinal(member.getDeclaringClass().getModifiers()));
  }
}
