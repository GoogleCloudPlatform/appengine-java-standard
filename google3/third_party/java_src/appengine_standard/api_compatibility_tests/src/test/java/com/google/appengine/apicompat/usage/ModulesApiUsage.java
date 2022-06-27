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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;

import com.google.appengine.api.modules.IModulesServiceFactory;
import com.google.appengine.api.modules.IModulesServiceFactoryProvider;
import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Exhaustive usage of the modules service API. Used for backward compatibility checks.
 */
public class ModulesApiUsage {

  /**
   * Exhaustive use of {@link ModulesServiceFactory}.
   */
  public static class ModulesServiceFactoryUsage extends ExhaustiveApiUsage<ModulesServiceFactory> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      ModulesServiceFactory.getModulesService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IModulesServiceFactory}.
   */
  public static class IModulesServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IModulesServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IModulesServiceFactory iModulesServiceFactory) {
      iModulesServiceFactory.getModulesService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IModulesServiceFactoryProvider}.
   */
  public static class IModulesServiceFactoryProviderUsage extends
  ExhaustiveApiUsage<IModulesServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IModulesServiceFactoryProvider unused = new IModulesServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ModulesService}.
   */
  public static class ModulesServiceUsage extends ExhaustiveApiInterfaceUsage<ModulesService> {

    @Override
    protected Set<Class<?>> useApi(ModulesService modulesService) {
      modulesService.getCurrentModule();
      modulesService.getCurrentVersion();
      modulesService.getCurrentInstanceId();
      modulesService.getModules();
      modulesService.getVersions("module1");
      modulesService.getDefaultVersion("module1");
      modulesService.getNumInstances("module1", "version1");
      modulesService.setNumInstances("module1", "version1", 1L);
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError =
          modulesService.setNumInstancesAsync("module1", "version1", 1L);
      modulesService.startVersion("module1", "version1");
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError1 = modulesService.startVersionAsync("module1", "version1");
      modulesService.stopVersion("module1", "version1");
      @SuppressWarnings("unused") // go/futurereturn-lsc
      Future<?> possiblyIgnoredError2 = modulesService.stopVersionAsync("module1", "version1");
      modulesService.getVersionHostname("module1", "version1");
      modulesService.getInstanceHostname("module1", "version1", "1");
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link ModulesException}.
   */
  public static class ModulesExceptionUsage extends ExhaustiveApiUsage<ModulesException> {
    @Override
    public Set<Class<?>> useApi() {
      ModulesException unused1 = new ModulesException("this");
      ModulesException unused2 = new ModulesException("this", new Exception());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }
}
