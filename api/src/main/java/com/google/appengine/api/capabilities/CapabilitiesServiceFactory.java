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

package com.google.appengine.api.capabilities;

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * Factory for creating a {@link CapabilitiesService}.
 *
 */
public class CapabilitiesServiceFactory {


  /**
   * Creates an implementation of the {@link CapabilitiesService}.
   * 
   * @return an instance of the capabilities service.
   */
  public static CapabilitiesService getCapabilitiesService() {
    return getFactory().getCapabilitiesService();
  }


  private static ICapabilitiesServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(ICapabilitiesServiceFactory.class);
  }
}
