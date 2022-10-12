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
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * A class that verifies that a {@link ExhaustiveApiUsage} implementation for a given api class
 * invokes every accessible method, references every accessible field, and reports every class that
 * the api class extends or implements, directly or indirectly. This assures us that our {@link
 * ExhaustiveApiUsage} implementations are correct, which in turn allows us to depend on them to
 * alert us to any backwards incompatible api changes we might introduce.
 *
 */
public class ExhaustiveApiUsageVerifier {

  /**
   * Predicate that tells us whether or not the provided fully-qualified classname should be treated
   * as a class that is responsible for the exhaustive usage of an api.
   */
  private final Predicate<String> isExhaustiveUsageClass;

  public ExhaustiveApiUsageVerifier(Predicate<String> isExhaustiveUsageClass) {
    this.isExhaustiveUsageClass = isExhaustiveUsageClass;
  }

  /**
   * Very that an instance of the given {@link ExhaustiveApiUsage} class invokes every accessible
   * method and references every accessible field of a given class.
   *
   * @param exhaustiveApiUsage The usage class.
   * @return A comparison object that describes the difference between the accessible api and the
   *     exercised api.
   */
  public ApiComparison verify(Class<? extends ExhaustiveApiUsage<?>> exhaustiveApiUsage) {
    try {
      // We need an instance of the usage class to find out which api class it uses and so that we
      // can get its inheritance set.
      ExhaustiveApiUsage<?> apiUsageInRegularClassLoader =
          exhaustiveApiUsage.getDeclaredConstructor().newInstance();
      Class<?> apiClass = apiUsageInRegularClassLoader.getApiClass();
      Api accessibleApi = new AccessibleApiDetector().detect(apiClass);

      // We're going to load the api usage class in a special classloader that instruments method
      // invocations and field references.
      UsageTrackingClassLoader trackingLoader =
          new UsageTrackingClassLoader(apiClass, isExhaustiveUsageClass);

      // Convert all the class/constructor/method/field references in the accessible api to their
      // equivalents in our tracking loader. We need to do this so that we can accurately compare
      // the accessible api to the exercised api (2 classes/constructors/methods/fields are only
      // considered equal if they belong to the same classloader).
      accessibleApi = convertToTrackingClassloader(trackingLoader, accessibleApi);

      // This is the inheritance set that the api class _should_ have. We'll compare this to the
      // inheritance set that it actually has.
      Set<Class<?>> inheritanceSet = apiUsageInRegularClassLoader.useApi();
      // Convert the publicInheritanceSet classes to the tracking loader as well.
      inheritanceSet = classes(loadInClassloader(
          trackingLoader, inheritanceSet.toArray(new Class<?>[inheritanceSet.size()])));

      Api exercisedApi = new Api(apiClass, inheritanceSet, ctors(), methods(), fields());

      Object obj =
          loadInClassloader(trackingLoader, exhaustiveApiUsage)
              .getDeclaredConstructor()
              .newInstance();
      // Prepare the usage tracker. Once we start loading classes and executing code in the special
      // classloader, the tracking methods will be invoked, and these tracking methods need an Api
      // object to write to.
      UsageTracker.setExercisedApi(exercisedApi);
      Method m = obj.getClass().getMethod("useApi");
      m.invoke(obj);
      // The code that is supposed to exercise the complete api of the api class is finished and
      // exercisedApi has been populated with all the exercised constructors/methods/fields.
      return new ApiComparison(accessibleApi, exercisedApi);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    } finally {
      UsageTracker.setExercisedApi(null);
    }
  }

  private static Class<?> loadInClassloader(ClassLoader loader, Class<?> cls)
      throws ClassNotFoundException {
    // Primitive classes belong to the bootstrap classloader so don't bother trying to look them up
    // somewhere else.
    return cls.isPrimitive() ? cls : Class.forName(cls.getName(), true, loader);
  }

  /**
   * Loads all the given classes in the given classloader.
   *
   * @param loader  The loader to use.
   * @param classes The classes to load.
   * @return The classes that were provided as input but belonging to the provided classloader.
   * @throws ClassNotFoundException If any of the classes do not exist.
   */
  private static Class<?>[] loadInClassloader(ClassLoader loader, Class<?>[] classes)
      throws ClassNotFoundException {
    List<Class<?>> trackingClasses = Lists.newArrayListWithExpectedSize(classes.length);
    for (Class<?> cls : classes) {
      trackingClasses.add(loadInClassloader(loader, cls));
    }
    return trackingClasses.toArray(new Class<?>[classes.length]);
  }

  /**
   * Creates a copy of the given {@link Api} with all classes/constructors/methods/fields replaced
   * by the same classes/constructors/methods/fields belonging to the given classloader.
   *
   * @param loader The classloader in which to load everything referenced in the given api.
   * @param api The api.
   * @return A copy of the give {@link Api} with all classes/constructors/methods/fields replaced by
   *         the same classes/constructors/methods/fields belonging to the given classloader.
   */
  private Api convertToTrackingClassloader(ClassLoader loader, Api api) {
    try {
      Set<Class<?>> convertedClasses = classes();
      for (Class<?> superclass : api.getInheritanceSet()) {
        convertedClasses.add(loadInClassloader(loader, superclass));
      }
      Set<Constructor<?>> convertedCtors = ctors();
      for (Constructor<?> ctor : api.getConstructors()) {
        convertedCtors.add(loadInClassloader(
            loader, ctor.getDeclaringClass()).getDeclaredConstructor(
            loadInClassloader(loader, ctor.getParameterTypes())));
      }
      Set<Method> convertedMethods = methods();
      for (Method method : api.getMethods()) {
        convertedMethods.add(loadInClassloader(
            loader, method.getDeclaringClass()).getDeclaredMethod(
            method.getName(), loadInClassloader(loader, method.getParameterTypes())));
      }
      Set<Field> convertedFields = fields();
      for (Field field : api.getFields()) {
        convertedFields.add(loadInClassloader(
            loader, field.getDeclaringClass()).getDeclaredField(field.getName()));
      }
      return new Api(loadInClassloader(loader, api.getApiClass()),
          convertedClasses, convertedCtors, convertedMethods, convertedFields);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
