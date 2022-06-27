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

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityState;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.capabilities.ICapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.ICapabilitiesServiceFactoryProvider;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalCapabilitiesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/** Exhaustive usage of the Capabilities Api. Used for backward compatibility checks. */
public class CapabilitiesApiUsage {

  /**
   * Exhaustive use of {@link CapabilitiesServiceFactory}.
   */
  public static class CapabilitiesServiceFactoryUsage
      extends ExhaustiveApiUsage<CapabilitiesServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      CapabilitiesService cs = CapabilitiesServiceFactory.getCapabilitiesService();
      CapabilitiesServiceFactory csf = new CapabilitiesServiceFactory(); // TODO(maxr): deprecate
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ICapabilitiesServiceFactory}.
   */
  public static class ICapabilitiesServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<ICapabilitiesServiceFactory> {

    @Override
    public Set<Class<?>> useApi(ICapabilitiesServiceFactory iCapabilitiesServiceFactory) {
      iCapabilitiesServiceFactory.getCapabilitiesService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link ICapabilitiesServiceFactoryProvider}.
   */
  public static class ICapabilitiesServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<ICapabilitiesServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      ICapabilitiesServiceFactoryProvider iCapabilitiesServiceFactoryProvider
          = new ICapabilitiesServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link CapabilitiesService}.
   */
  public static class CapabilitiesServiceUsage
      extends ExhaustiveApiInterfaceUsage<CapabilitiesService> {

    @Override
    protected Set<Class<?>> useApi(CapabilitiesService cs) {
      CapabilityState state = cs.getStatus(Capability.IMAGES);
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link CapabilityState}.
   */
  public static class CapabilityStateUsage extends ExhaustiveApiUsage<CapabilityState> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper =
          new LocalServiceTestHelper(new LocalCapabilitiesServiceTestConfig());
      helper.setUp();
      try {
        CapabilitiesService cs = CapabilitiesServiceFactory.getCapabilitiesService();
        CapabilityState state = cs.getStatus(Capability.MAIL);
        Capability capability = state.getCapability();
        Date date = state.getScheduledDate();
        CapabilityStatus status = state.getStatus();
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link Capability}.
   */
  public static class CapabilityUsage extends ExhaustiveApiUsage<Capability> {

    @Override
    public Set<Class<?>> useApi() {
      Capability capability = Capability.BLOBSTORE;
      capability = Capability.DATASTORE;
      capability = Capability.DATASTORE_WRITE;
      capability = Capability.IMAGES;
      capability = Capability.MAIL;
      capability = Capability.PROSPECTIVE_SEARCH;
      capability = Capability.MEMCACHE;
      capability = Capability.TASKQUEUE;
      capability = Capability.URL_FETCH;
      capability = Capability.XMPP;
      capability = new Capability("yar");
      capability = new Capability("yar", "yam");
      String strVal = capability.getName();
      strVal = capability.getPackageName();
      strVal = capability.toString();
      boolean equal = Capability.MAIL.equals(Capability.IMAGES);
      int hashCode = Capability.MAIL.hashCode();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link CapabilityStatus}.
   */
  public static class CapabilityStatusUsage extends ExhaustiveApiUsage<CapabilityStatus> {

    @Override
    public Set<Class<?>> useApi() {
      CapabilityStatus status = CapabilityStatus.DISABLED;
      status = CapabilityStatus.ENABLED;
      status = CapabilityStatus.SCHEDULED_MAINTENANCE;
      status = CapabilityStatus.UNKNOWN;
      status = CapabilityStatus.valueOf("DISABLED");
      CapabilityStatus[] values = CapabilityStatus.values();
      return classes(Object.class, Enum.class, Comparable.class, Serializable.class);
    }
  }
}
