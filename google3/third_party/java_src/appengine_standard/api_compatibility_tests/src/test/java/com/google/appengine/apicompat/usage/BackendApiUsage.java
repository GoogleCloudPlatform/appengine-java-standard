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

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.backends.IBackendServiceFactory;
import com.google.appengine.api.backends.IBackendServiceFactoryProvider;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import java.util.Set;

/** Exhaustive usage of the Backends Api. Used for backward compatibility checks. */
public class BackendApiUsage {

  /**
   * Exhaustive use of {@link BackendServiceFactory}.
   */
  public static class BackendServiceFactoryUsage extends ExhaustiveApiUsage<BackendServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      BackendService bs = BackendServiceFactory.getBackendService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IBackendServiceFactory}.
   */
  public static class IBackendServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IBackendServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IBackendServiceFactory iBackendServiceFactory) {
      iBackendServiceFactory.getBackendService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IBackendServiceFactoryProvider}.
   */
  public static class IBackendServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IBackendServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IBackendServiceFactoryProvider iBackendServiceFactoryProvider
          = new IBackendServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link BackendService}.
   */
  public static class BackendServiceUsage extends ExhaustiveApiInterfaceUsage<BackendService> {

    String ___apiConstant_INSTANCE_ID_ENV_ATTRIBUTE;
    String ___apiConstant_REQUEST_HEADER_INSTANCE_REDIRECT;
    String ___apiConstant_DEVAPPSERVER_PORTMAPPING_KEY;
    String ___apiConstant_REQUEST_HEADER_BACKEND_REDIRECT;
    String ___apiConstant_BACKEND_ID_ENV_ATTRIBUTE;

    @Override
    protected Set<Class<?>> useApi(BackendService backendService) {
      String strVal = backendService.getBackendAddress("yar");
      strVal = backendService.getBackendAddress("yar", 8080);
      strVal = backendService.getCurrentBackend();
      int intVal = backendService.getCurrentInstance();
      ___apiConstant_INSTANCE_ID_ENV_ATTRIBUTE = BackendService.INSTANCE_ID_ENV_ATTRIBUTE;
      ___apiConstant_REQUEST_HEADER_INSTANCE_REDIRECT =
          BackendService.REQUEST_HEADER_BACKEND_REDIRECT;
      ___apiConstant_DEVAPPSERVER_PORTMAPPING_KEY = BackendService.DEVAPPSERVER_PORTMAPPING_KEY;
      ___apiConstant_REQUEST_HEADER_BACKEND_REDIRECT =
          BackendService.REQUEST_HEADER_BACKEND_REDIRECT;
      ___apiConstant_BACKEND_ID_ENV_ATTRIBUTE = BackendService.BACKEND_ID_ENV_ATTRIBUTE;
      return classes();
    }
  }
}
