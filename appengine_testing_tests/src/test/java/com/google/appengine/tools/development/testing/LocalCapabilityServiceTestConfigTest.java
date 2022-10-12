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
import com.google.appengine.api.capabilities.CapabilityServicePb;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse.SummaryStatus;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.capabilities.dev.LocalCapabilitiesService;
import com.google.appengine.tools.development.LocalRpcService;
import junit.framework.TestCase;

/**
 */
public class LocalCapabilityServiceTestConfigTest extends TestCase {

  private final LocalServiceTestHelper helper = new LocalServiceTestHelper(
      new LocalCapabilitiesServiceTestConfig().
          setCapabilityStatus(Capability.MEMCACHE, CapabilityStatus.DISABLED).
          setCapabilityStatus(Capability.DATASTORE, CapabilityStatus.UNKNOWN).
          setCapabilityStatus(Capability.DATASTORE_WRITE, CapabilityStatus.SCHEDULED_MAINTENANCE).
          setCapabilityStatus(Capability.BLOBSTORE, CapabilityStatus.ENABLED));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    helper.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    helper.tearDown();
    super.tearDown();
  }

  public void testCapabilityChanges() {

    LocalCapabilitiesService capabilityService = LocalCapabilitiesServiceTestConfig
        .getLocalCapabilitiesService();
    LocalRpcService.Status status = new LocalRpcService.Status();
    //Memcache test
    CapabilityServicePb.IsEnabledRequest request = buildRequest(
        Capability.MEMCACHE.getPackageName(), Capability.MEMCACHE.getName());
    CapabilityServicePb.IsEnabledResponse response = capabilityService.isEnabled(status, request);
    assertEquals(SummaryStatus.DISABLED, response.getSummaryStatus());

    // datastore test with unknown
    request = buildRequest(
        Capability.DATASTORE.getPackageName(), Capability.DATASTORE.getName());
    response = capabilityService.isEnabled(status, request);
    assertEquals(SummaryStatus.UNKNOWN, response.getSummaryStatus());

    // datastore write with schedule now
    request = buildRequest(
        Capability.DATASTORE_WRITE.getPackageName(), Capability.DATASTORE_WRITE.getName());
    response = capabilityService.isEnabled(status, request);
    assertEquals(SummaryStatus.SCHEDULED_NOW, response.getSummaryStatus());

    //BLOBSTORE service should be enabled
    request = buildRequest(
        Capability.BLOBSTORE.getPackageName(), Capability.BLOBSTORE.getName());
    response = capabilityService.isEnabled(status, request);
    assertEquals(SummaryStatus.ENABLED, response.getSummaryStatus());

    //other services should be enabled by default:
    request = buildRequest(
        Capability.MAIL.getPackageName(), Capability.MAIL.getName());
    response = capabilityService.isEnabled(status, request);
    assertEquals(SummaryStatus.ENABLED, response.getSummaryStatus());
  }

  private CapabilityServicePb.IsEnabledRequest buildRequest(String packageName, String capability) {
    CapabilityServicePb.IsEnabledRequest.Builder builder = CapabilityServicePb
        .IsEnabledRequest.newBuilder();
    builder.setPackage(packageName);

    builder.addCapability(capability);
    return builder.build();
  }

}
