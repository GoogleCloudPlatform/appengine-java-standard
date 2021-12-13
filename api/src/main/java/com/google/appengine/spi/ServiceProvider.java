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

package com.google.appengine.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the precedence that {@link ServiceFactoryFactory} gives to the annotated {@link
 * FactoryProvider}.
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceProvider {

  public static final int DEFAULT_PRECEDENCE = 0;

  /**
   * Returns the interface implemented by this ServiceProvider. This is present for compatibility
   * reasons but should always be {@link FactoryProvider}, which is the default.
   */
  Class<?> value() default FactoryProvider.class;

  /** Higher precedence will take priority over lower precedences for a given interface. */
  int precedence() default DEFAULT_PRECEDENCE;
}
