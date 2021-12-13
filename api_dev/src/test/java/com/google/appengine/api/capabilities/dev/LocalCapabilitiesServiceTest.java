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

package com.google.appengine.api.capabilities.dev;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityServicePb.IsEnabledResponse.SummaryStatus;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.tools.development.testing.LocalCapabilitiesServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the {@link LocalCapabilitiesService} implementation.
 *
 */
@RunWith(JUnit4.class)
public class LocalCapabilitiesServiceTest {

  private CapabilitiesService service;

  private LocalServiceTestHelper helper;

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalCapabilitiesServiceTestConfig());
    helper.setUp();
    service = CapabilitiesServiceFactory.getCapabilitiesService();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  /** Tests that calls to getStatus return ENABLED for supported services. */
  @Test
  public void testGetStatusEnabled() throws Exception {
    assertThat(service.getStatus(Capability.BLOBSTORE).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.DATASTORE).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.DATASTORE_WRITE).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.IMAGES).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.MAIL).getStatus()).isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.MEMCACHE).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.TASKQUEUE).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.URL_FETCH).getStatus())
        .isEqualTo(CapabilityStatus.ENABLED);
    assertThat(service.getStatus(Capability.XMPP).getStatus()).isEqualTo(CapabilityStatus.ENABLED);
  }

  /** Tests that our mapping from CapabilityStatus to SummaryStatus enums is correct */
  @Test
  public void testStatusMapping() throws Exception {
    assertThat(SummaryStatus.values().length)
        .isEqualTo(LocalCapabilitiesService.CAPABILITY_STATUS_TO_SUMMARY_STATUS.size());
  }
}
