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

import java.util.Objects;

/**
 * A base class for service factory creation that can be registered with the ProviderRegistry.
 *
 * @param <I> is the interface the provided factory must implement.
 */
public abstract class FactoryProvider<I> implements Comparable<FactoryProvider<?>> {
  private final Class<I> baseInterface;

  protected FactoryProvider(Class<I> baseInterface) {
    this.baseInterface = baseInterface;
  }

  protected Class<I> getBaseInterface() {
    return baseInterface;
  }

  /**
   * Return an instance of the factory
   */
  protected abstract I getFactoryInstance();

  private int getPrecedence() {
    ServiceProvider annotation = getClass().getAnnotation(ServiceProvider.class);
    return (annotation != null) ? annotation.precedence() : ServiceProvider.DEFAULT_PRECEDENCE;
  }

  /**
   * This ensures that a list of these will be sorted so that higher precedence entries come later
   * in the list.
   */
  @Override
  public int compareTo(FactoryProvider<?> o) {
    int result =
        getBaseInterface().getCanonicalName().compareTo(o.getBaseInterface().getCanonicalName());
    return result != 0 ? result : Integer.compare(getPrecedence(), o.getPrecedence());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBaseInterface().getCanonicalName(), getPrecedence());
  }

  /**
   * Included to support sorting by precedence (@see #compareTo(FactoryProvider))
   */
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof FactoryProvider)) {
      return false;
    }
    return (compareTo((FactoryProvider<I>) o) == 0);
  }

}
