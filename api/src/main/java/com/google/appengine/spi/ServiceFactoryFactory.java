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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>This class is not intended for end users.</b>
 *
 * <p>Provide factory instances for AppEngine APIs. Each API will have an associated
 * {@link FactoryProvider} registered with this class. N.B. <i>Once {@link #getFactory(Class)} has
 * been called, no further mappings may be registered with this class.</i>
 *
 * <p>To construct the runtime mapping, this class first uses {@code java.util.ServiceLoader} to
 * find all registered {@link FactoryProvider} entities using the {@code ClassLoader} of
 * {@code ServiceFactoryFactory}. Finally, the explicitly registered providers
 * {@link #register(FactoryProvider)} are merged in.
 *
 * <p>If {@code ServiceLoader} locates multiple providers for a given factory interface, the
 * ambiguity can be resolved by using the {@link ServiceProvider#precedence} annotation property
 * (higher precedence wins; <i>the google implementations all have precedence
 * Integer.MIN_VALUE</i>). An exception is raised if the ambiguity cannot be resolved. Note that
 * explicit registration ({@link #register(FactoryProvider)}) always takes precedence (it does not
 * honor the {@link ServiceProvider#precedence} annotation property).
 *
 */
public final class ServiceFactoryFactory {
  /**
   * If this system property is set to "true" the thread context classloader is used (if non-null)
   * when looking up API service implementations. Otherwise the class loader of this class is used.
   */
  public static final String USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY =
      "appengine.spi.useThreadContextClassLoader";

  /**
   * Providers should be registered with this entity prior to the first call to getFactory().
   */
  private static AtomicReference<FactoryProviderRegistry> explicitRegistry =
      new AtomicReference<FactoryProviderRegistry>(new FactoryProviderRegistry());

  /**
   * Used by AppEngine service factories. Returns an instance of the factory implementing the
   * interface provide by {@code base}. Since there must always be a provider registered for a given
   * base, an error will be raised if no appropriate registration is found.
   *
   * @param base The returned factory must extend this class.
   * @param <T> The type of the factory
   *
   * @throws IllegalArgumentException raised if the client requests a factory that does not have a
   *         provider registered for it.
   * @throws ServiceConfigurationError raised if there is a problem creating the factory instance
   */
  public static <T> T getFactory(Class<T> base) {

    FactoryProvider<T> p = RuntimeRegistry.runtimeRegistry.getFactoryProvider(base);

    if (p == null) {
      throw new IllegalArgumentException(
          "No provider was registered for " + base.getCanonicalName());
    }

    try {
      return p.getFactoryInstance();
    } catch (Exception e) {
      throw new ServiceConfigurationError(
          "Exception while getting factory instance for " + base.getCanonicalName(), e);
    }

  }

  /**
   * Explicitly register a provider. This does not take the precedence (see
   * {@link FactoryProvider#getPrecedence()}) of the provider into consideration; subsequent
   * registrations will always override previous ones.
   *
   * @param p The provider to register
   *
   * @throws IllegalStateException raised if calls to getFactoryProvider have already been made.
   */
  public static synchronized <I> void register(FactoryProvider<I> p) {

    FactoryProviderRegistry temp = explicitRegistry.get();

    Preconditions.checkState(
        temp != null, "No modifications allowed after calls to getFactoryProvider");

    temp.register(p);
  }

  // N.B. This is done as an inner class to force synchronization of the
  // initialization of the provider map. By doing it this way, I can ensure
  // that no synchronization will be necessary for the getFactory() calls
  // (only read calls are permitted post-initialization)
  //
  private static final class RuntimeRegistry {
    static final FactoryProviderRegistry runtimeRegistry = new FactoryProviderRegistry();

    static {

      FactoryProviderRegistry explicitRegistrations = explicitRegistry.getAndSet(null);

      List<FactoryProvider<?>> providers = getProvidersUsingServiceLoader();

      Collections.sort(providers); // ensures higher precedence providers appear later in the list.

      for (FactoryProvider<?> provider : providers) {
        FactoryProvider<?> previous = runtimeRegistry.register(provider);
        Preconditions.checkState(!provider.equals(previous),
            "Ambiguous providers: " + provider + " versus " + previous);
      }

      for (FactoryProvider<?> provider : explicitRegistrations.getAllProviders()) {
        runtimeRegistry.register(provider);
      }
    }
  }

  /** Retrieves the list of factory providers from the classpath */
  private static List<FactoryProvider<?>> getProvidersUsingServiceLoader() {
    List<FactoryProvider<?>> result = new ArrayList<FactoryProvider<?>>();

    // Sandboxed applications use the classloader of this class (ServiceFactoryFactory).
    // VM runtime applications use the thread context classloader (if not null) and fall
    // back to
    // the ServiceFactoryFactory classloader otherwise.
    //
    ClassLoader classLoader = null;
    if (Boolean.getBoolean(USE_THREAD_CONTEXT_CLASSLOADER_PROPERTY)) {
      classLoader = Thread.currentThread().getContextClassLoader();
    }
    if (classLoader == null) {
      // If the classloader isn't set (or set to null). Use the classloader of this class.
      classLoader = ServiceFactoryFactory.class.getClassLoader();
    }

    // Can't use parameterized types in ServiceLoader.load
    //
    @SuppressWarnings("rawtypes")
    ServiceLoader<FactoryProvider> providers =
        ServiceLoader.load(FactoryProvider.class, classLoader);

    if (providers != null) {
      for (FactoryProvider<?> provider : providers) {
        result.add(provider);
      }
    }

    return result;
  }
}
