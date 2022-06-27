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
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A collection of constructors, methods, and fields on a class, as well as all classes that the
 * class extends or implements, either directly or indirectly.
 *
 */
public final class Api {

  /**
   * The class whose Api this object describes.
   */
  private final Class<?> apiClass;

  /**
   * All classes that the api class extends or implements, either directly or indirectly.
   */
  private final Set<Class<?>> inheritanceSet = classes();

  /**
   * All accessible constructors declared on the api class.
   */
  private final Set<Constructor<?>> constructors = ctors();

  /**
   * All accessible methods declared on the api class.
   */
  private final Set<Method> methods = methods();

  /**
   * All accessible fields declared on the api class.
   */
  private final Set<Field> fields = fields();

  public Api(Class<?> apiClass, Set<Class<?>> inheritanceSet, Set<Constructor<?>> constructors,
      Set<Method> methods, Set<Field> fields) {
    this.apiClass = checkNotNull(apiClass, "apiClass cannot be null");
    this.inheritanceSet.addAll(checkNotNull(inheritanceSet, "inheritanceSet cannot be null"));
    this.constructors.addAll(checkNotNull(constructors, "constructors cannot be null"));
    this.methods.addAll(checkNotNull(methods, "methods cannot be null"));
    this.fields.addAll(checkNotNull(fields, "fields cannot be null"));
  }

  public Class<?> getApiClass() {
    return apiClass;
  }

  public Set<Class<?>> getInheritanceSet() {
    return inheritanceSet;
  }

  public Set<Constructor<?>> getConstructors() {
    return constructors;
  }

  public Set<Method> getMethods() {
    return methods;
  }

  public Set<Field> getFields() {
    return fields;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Api api = (Api) o;

    if (!apiClass.equals(api.apiClass)) {
      return false;
    }
    if (!constructors.equals(api.constructors)) {
      return false;
    }
    if (!fields.equals(api.fields)) {
      return false;
    }
    if (!inheritanceSet.equals(api.inheritanceSet)) {
      return false;
    }
    if (!methods.equals(api.methods)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = apiClass.hashCode();
    result = 31 * result + inheritanceSet.hashCode();
    result = 31 * result + constructors.hashCode();
    result = 31 * result + methods.hashCode();
    result = 31 * result + fields.hashCode();
    return result;
  }
}
