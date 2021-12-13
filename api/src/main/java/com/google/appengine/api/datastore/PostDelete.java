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

package com.google.appengine.api.datastore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a callback method to be invoked after an {@link Entity} of any of the specified kinds
 * is deleted from the datastore. If {@link #kinds()} is not provided the callback will execute for
 * all kinds. Methods with this annotation must return {@code void}, must accept a single argument
 * of type {@link DeleteContext}, must not throw any checked exceptions, must not be static, and
 * must belong to a class with a no-arg constructor. Neither the method nor the no-arg constructor
 * of the class to which it belongs are required to be public. Methods with this annotation are free
 * to throw any unchecked exception they like. Throwing an unchecked exception will prevent
 * callbacks that have not yet been executed from running, and the exception will propagate to the
 * code that invoked the datastore operation that resulted in the invocation of the callback.
 * Throwing an unchecked exception from a callback will <b>not</b> have any impact on the result of
 * the datastore operation. Methods with this annotation will not be invoked if the datastore
 * operation fails.
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostDelete {

  /**
   * The kinds to which this callback applies. The default value is an empty array, which indicates
   * that the callback should run for all kinds.
   *
   * @return The kinds to which this callback applies.
   */
  String[] kinds() default {};
}
