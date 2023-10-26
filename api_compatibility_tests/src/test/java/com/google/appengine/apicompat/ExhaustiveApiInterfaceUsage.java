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

import com.google.common.base.Defaults;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * A specialization of {@link ExhaustiveApiUsage} for interface types.
 *
 * @param <T> The interface type.
 */
public abstract class ExhaustiveApiInterfaceUsage<T> extends ExhaustiveApiUsage<T> {

  /**
   * Proxy implementation of the interface that returns a default object for every method. This will
   * generate an {@link IllegalArgumentException} if the api class is not an interface.
   */
  private final T theInterface = newProxy(getApiClass());

  @Override
  public final Set<Class<?>> useApi() {
    return useApi(theInterface);
  }

  /**
   * Template method for subclasses to implement.
   *
   * @param theInterface A proxy implementation of the interface.
   */
  protected abstract Set<Class<?>> useApi(T theInterface);

  private static <T> T newProxy(Class<T> interfaceToProxy) {
    return interfaceToProxy.cast(
        Proxy.newProxyInstance(
            interfaceToProxy.getClassLoader(), new Class<?>[] {interfaceToProxy}, HANDLER));
  }

  private static final InvocationHandler HANDLER = new InvocationHandler() {
    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
      // We could add to invokedMethods here, but it would be redundant since our bytecode
      // instrumentation is going to register the method invocation in the code that invokes the
      // method.
      return Defaults.defaultValue(method.getReturnType());
    }
  };
}
