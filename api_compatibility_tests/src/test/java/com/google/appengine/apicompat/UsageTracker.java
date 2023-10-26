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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Primitives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Tracks invocations of methods and references to fields. This class maintains state in a static
 * member variable because its methods are only invoked via injected bytecode, and invoking static
 * methods from bytecode is simpler than invoking member methods. This does force a certain amount
 * of static cling, but if it ever becomes a real issue we can get rid of the static state and
 * enhance our bytecode to deal with tracking an instance of this class and invoking a member method
 * on it.
 *
 * Users must call {@link #setExercisedApi(Api)} before any tracking methods are invoked.
 *
 */
public final class UsageTracker { // Needs to be public so that the injected bytecode can access it.

  /**
   * Marker for constructors whose invocation should not be tracked.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.CONSTRUCTOR})
  public @interface DoNotTrackConstructorInvocation { }

  private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP;

  static {
    PRIMITIVE_TYPE_MAP = Maps.newHashMap();
    for (Class<?> primitiveClass : Primitives.allPrimitiveTypes()) {
      PRIMITIVE_TYPE_MAP.put(primitiveClass.getName(), primitiveClass);
    }
  }

  private static Api exercisedApi = null;

  public static void setExercisedApi(Api exercisedApi) {
    UsageTracker.exercisedApi = exercisedApi;
  }

  public static Api getExercisedApi() {
    return exercisedApi;
  }

  /**
   * Register the invocation of the constructor identified by the given owner and parameter types.
   *
   * @param owner      The fully qualified name of the class on which the constructor is declared.
   * @param paramTypes The types of the constructor parameters, represented as a comma-separated
   *                   list of fully-qualified classnames (or for primitive types, the short name of
   *                   the type, ie int, short, byte, etc.). We pass these values in this form
   *                   because the method is invoked via bytecode, and passing a string is much more
   *                   straightforward than passing an array or an Object (like a Set).
   * @throws ClassNotFoundException If the class identified by the given class name cannot be
   *                                found.
   * @throws NoSuchMethodException  If the constructor identified by the given class and signature
   *                                cannot be found.
   */
  @SuppressWarnings("unused") // Not unused, we invoke it directly via bytecode
  public static void trackConstructorInvocation(String owner, String paramTypes)
      throws ClassNotFoundException, NoSuchMethodException {
    Class<?> cls = Class.forName(owner);
    Constructor<?> constructor = cls.getDeclaredConstructor(paramTypesToClassArray(paramTypes));
    if (constructor.getAnnotation(DoNotTrackConstructorInvocation.class) == null) {
      exercisedApi.getConstructors().add(constructor);
    }
  }

  /**
   * Register the invocation of the method identified by the given owner, method name, and parameter
   * types.
   *
   * @param className  The fully qualified name of the class on which the method is declared.
   * @param methodName The name of the method.
   * @param paramTypes The types of the method parameters, represented as a comma-separated list of
   *                   fully-qualified classnames (or for primitive types, the short name of the
   *                   type, ie int, short, byte, etc.). We pass these values in this form because
   *                   the method is invoked via bytecode, and passing a string is much more
   *                   straightforward than passing an array or an Object (like a Set).
   * @throws ClassNotFoundException If the class identified by the given class name cannot be
   *                                found.
   * @throws NoSuchMethodException  If the method identified by the given class name, method name,
   *                                and signature cannot be found.
   */
  @SuppressWarnings({"unused", "unchecked"}) // Not unused, we invoke it directly via bytecode
  public static void trackMethodInvocation(String className, String methodName, String paramTypes)
      throws ClassNotFoundException, NoSuchMethodException {
    List<Class<?>> classes = Lists
        .newArrayList(Class.forName(className), exercisedApi.getApiClass());
    // The only way we access protected methods is via a subclass, in which case the method isn't
    // declared on the class that is passed in. We'll iterate up the inheritance chain until we
    // reach the class where the method is declared.
    classes.addAll(exercisedApi.getInheritanceSet());
    NoSuchMethodException nsme = null;
    for (Class<?> cls : classes) {
      try {
        Method method = cls.getDeclaredMethod(methodName, paramTypesToClassArray(paramTypes));
        exercisedApi.getMethods().add(method);
        return;
      } catch (NoSuchMethodException e) {
        if (nsme == null) {
          // keep the first exception around
          nsme = e;
        }
      }
    }
    // We've iterated over the entire inheritance set and not found a single class on which the
    // method is declared. That's an error.
    throw nsme;
  }

  /**
   * Register a reference to the field identified by the given owner and field name.
   *
   * @param className The fully qualified name of the class on which the field is declared.
   * @param fieldName The name of the field.
   * @throws ClassNotFoundException If the class identified by given class name cannot be found.
   * @throws NoSuchFieldException   If the field identified by the given class and field name cannot
   *                                be found.
   */
  @SuppressWarnings({"unused", "unchecked"}) // Not unused, we invoke it directly via bytecode
  public static void trackFieldReference(String className, String fieldName)
      throws ClassNotFoundException, NoSuchFieldException {
    List<Class<?>> classes = Lists.newArrayList(
        Class.forName(className), exercisedApi.getApiClass());
    // The only way we access protected fields is via a subclass, in which case the field isn't
    // declared on the class that is passed in. We'll iterate up the inheritance chain until we
    // reach the class where the field is declared.
    classes.addAll(exercisedApi.getInheritanceSet());
    NoSuchFieldException nsfe = null;
    for (Class<?> cls : classes) {
      try {
        Field field = cls.getDeclaredField(fieldName);
        exercisedApi.getFields().add(field);
        return;
      } catch (NoSuchFieldException e) {
        if (nsfe == null) {
          // keep the first exception around
          nsfe = e;
        }
      }
    }
    throw nsfe;
  }

  /**
   * Converts a comma-separated list of fully-qualified classnames to an array of {@link Class}
   * objects.
   *
   * @param paramTypesStr A comma-separated list of fully-qualified classnames. For primitive types
   *                      we require the short name of the type, ie int, short, byte, etc.
   * @return An array of Classes represented by the comma-separated list.
   * @throws ClassNotFoundException If any of the types in the comma-separated list cannot be
   *                                found.
   */
  static Class<?>[] paramTypesToClassArray(String paramTypesStr) throws ClassNotFoundException {
    Preconditions.checkNotNull(paramTypesStr, "paramTypesStr cannot be null");
    if (paramTypesStr.isEmpty()) {
      return new Class<?>[0];
    }
    List<Class<?>> classes = Lists.newArrayList();
    for (String typeStr : paramTypesStr.split(",")) {
      Class<?> cls = primitiveType(typeStr);
      if (cls == null) {
        cls = Class.forName(typeStr);
      }
      classes.add(cls);
    }
    return classes.toArray(new Class<?>[classes.size()]);
  }

  /**
   * Maps primitive type names to the Class objects representing those types.
   *
   * @param typeStr A primitive type name, ie int, short, byte, etc.
   * @return The Class representing that primitive type, or {@code null} if the typeStr does not
   *         represent a primitive.
   */
  private static Class<?> primitiveType(String typeStr) {
    return PRIMITIVE_TYPE_MAP.get(typeStr);
  }

  private UsageTracker() {
    // do not instantiate
  }
}
