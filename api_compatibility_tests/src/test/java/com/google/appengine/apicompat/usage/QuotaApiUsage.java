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

import com.google.appengine.api.quota.IQuotaServiceFactory;
import com.google.appengine.api.quota.IQuotaServiceFactoryProvider;
import com.google.appengine.api.quota.QuotaService;
import com.google.appengine.api.quota.QuotaServiceFactory;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import java.io.Serializable;
import java.util.Set;

/** Exhaustive usage of the Quota Api. Used for backward compatibility checks. */
public class QuotaApiUsage {

  /**
   * Exhaustive use of {@link QuotaServiceFactory}.
   */
  public static class QuotaServiceFactoryUsage extends ExhaustiveApiUsage<QuotaServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      QuotaServiceFactory factory = new QuotaServiceFactory(); // ugh
      QuotaService svc = QuotaServiceFactory.getQuotaService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IQuotaServiceFactory}.
   */
  public static class IQuotaServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IQuotaServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IQuotaServiceFactory iQuotaServiceFactory) {
      iQuotaServiceFactory.getQuotaService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IQuotaServiceFactoryProvider}.
   */
  public static class IQuotaServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IQuotaServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IQuotaServiceFactoryProvider iQuotaServiceFactoryProvider
          = new IQuotaServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link QuotaService}.
   */
  public static class QuotaServiceUsage extends ExhaustiveApiInterfaceUsage<QuotaService> {

    @Override
    protected Set<Class<?>> useApi(QuotaService svc) {
      long longVal = svc.convertCpuSecondsToMegacycles(23.3d);
      double doubleVal = svc.convertMegacyclesToCpuSeconds(23L);
      longVal = svc.getApiTimeInMegaCycles();
      longVal = svc.getCpuTimeInMegaCycles();
      QuotaService.DataType dataType = QuotaService.DataType.API_TIME_IN_MEGACYCLES;
      boolean boolVal = svc.supports(dataType);
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link QuotaService.DataType}.
   */
  public static class DataTypeUsage extends ExhaustiveApiUsage<QuotaService.DataType> {

    @Override
    public Set<Class<?>> useApi() {
      QuotaService.DataType type = QuotaService.DataType.API_TIME_IN_MEGACYCLES;
      type = QuotaService.DataType.CPU_TIME_IN_MEGACYCLES;
      type = QuotaService.DataType.valueOf("API_TIME_IN_MEGACYCLES");
      QuotaService.DataType[] types = QuotaService.DataType.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }
}
