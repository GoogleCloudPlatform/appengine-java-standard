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

package com.google.appengine.tools.development.testing;

import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.capabilities.dev.LocalCapabilitiesService;
import com.google.appengine.tools.development.ApiProxyLocal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Config for accessing the local capabilities service in tests.
 *
 */
public class LocalCapabilitiesServiceTestConfig implements LocalServiceTestConfig {

  Map<String, String> properties = Collections.synchronizedMap(new HashMap<String, String>());

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.appendProperties(properties);
    // next call is needed to force an init call to the LocalCapabilitiesService
    LocalCapabilitiesService capabilityService = LocalCapabilitiesServiceTestConfig
        .getLocalCapabilitiesService();
  }

  @Override
  public void tearDown() {
    properties.clear();
  }


  /**
   * Controls the state of a capability in testing mode.
   *
   * @param capability the {@link Capability} to change the status of
   * @param status     the {@CapabilityStatus} to set for the given Capability
   * @return {@code this} (for chaining)
   */
  public LocalCapabilitiesServiceTestConfig setCapabilityStatus(Capability capability,
      CapabilityStatus status) {
    String key = LocalCapabilitiesService.geCapabilityPropertyKey(capability.getPackageName(),
        capability.getName());
    properties.put(key, status.name());
    return this;
  }

  public static LocalCapabilitiesService getLocalCapabilitiesService() {
    return (LocalCapabilitiesService) LocalServiceTestHelper
        .getLocalService(LocalCapabilitiesService.PACKAGE);
  }
}
