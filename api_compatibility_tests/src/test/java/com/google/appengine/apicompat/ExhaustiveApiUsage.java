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

import java.lang.reflect.ParameterizedType;
import java.util.Set;

/**
 * A class that exercises every accessible method and field declared on a given class.
 *
 * @param <T> The class whose accessible methods and fields are exercised.
 */
public abstract class ExhaustiveApiUsage<T> {

  @SuppressWarnings("unchecked")
  public Class<T> getApiClass() {
    // We'll just sniff out the class that we exercise from the generic signature.
    Object typeArg = ((ParameterizedType) getClass()
        .getGenericSuperclass()).getActualTypeArguments()[0];
    if (typeArg instanceof Class) {
      return (Class<T>) typeArg;
    } else if (typeArg instanceof ParameterizedType) {
      // The api class is itself generic. This won't work if we have multiple levels of generics,
      // but we don't have any of those in our public api at the moment and we can add support later
      // if needed.
      return (Class<T>) ((ParameterizedType) typeArg).getRawType();
    }
    throw new RuntimeException("Unsupported type argument: " + typeArg);
  }


  /**
   * Template method. Within the implementation, subclasses must exercise every accessible method
   * and field belonging to type T. This includes protected methods and fields if T is not final.
   * Field references must assign the value of the field to some other object. Static final fields
   * must be assigned to a member variable named {@value ApiVisitor#API_CONSTANT_PREFIX}XXXX, where
   * XXXX is the name of the static final field. This is a workaround for the fact that static final
   * fields are inlined by the compiler so we have no way of tracking references to them at the
   * bytecode level.
   *
   * @return A Set containing all classes that the api class extends or implements, either directly
   *         or indirectly.
   */
  @SuppressWarnings("unused") // Invoked via reflection only.
  public abstract Set<Class<?>> useApi();
}
