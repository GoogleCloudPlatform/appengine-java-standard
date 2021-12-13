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

package com.google.appengine.api.quota;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiStats;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for the QuotaServiceImpl class
 *
 */
@RunWith(JUnit4.class)
public class QuotaServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private Delegate<?> delegate;
  private MockEnvironment environment;
  private QuotaService quotaService;

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);

    // Setup the environment, stats, and quota service
    environment = new MockEnvironment("some-app", "v1");
    ApiProxy.setEnvironmentForCurrentThread(environment);
    new ApiStats(environment) {
      @Override
      public long getApiTimeInMegaCycles() {
        return 13;
      }
      @Override
      public long getCpuTimeInMegaCycles() {
        return 14;
      }
    };
    quotaService = QuotaServiceFactory.getQuotaService();
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test public void testWithNoEnvironment() {
    ApiProxy.clearEnvironmentForCurrentThread();
    verifyBehaviorForMissingStats();
  }

  @Test public void testWithNoStats() {
    environment.getAttributes().clear();
    assertThat(ApiStats.get(environment)).isNull();
    verifyBehaviorForMissingStats();
  }

  @Test public void testRegularCase() {
    assertThat(quotaService.supports(null)).isTrue();
    for (QuotaService.DataType type : QuotaService.DataType.values()) {
      assertThat(quotaService.supports(type)).isTrue();
    }

    // Make sure that api and cpu time reach the right ApiStats object
    assertThat(quotaService.getApiTimeInMegaCycles()).isEqualTo(13L);
    assertThat(quotaService.getCpuTimeInMegaCycles()).isEqualTo(14L);

    // Make sure the cycle/seconds conversion works
    verifyConversions();
  }

  /**
   * Helper: verify that the cpu/mcycle conversion is correct in the current mode
   */
  private void verifyConversions() {
    assertThat(quotaService.convertMegacyclesToCpuSeconds(0)).isEqualTo(0.0);
    assertThat(quotaService.convertMegacyclesToCpuSeconds(1800)).isEqualTo(1.5);
    assertThat(quotaService.convertCpuSecondsToMegacycles(0)).isEqualTo(0L);
    assertThat(quotaService.convertCpuSecondsToMegacycles(1.5)).isEqualTo(1800L);
  }

  /**
   * Helper: verifies the expected behavior of the service if it cannot get to its api stats.
   */
  private void verifyBehaviorForMissingStats() {

    // Make sure the service correctly identifies what it supports (only wall time)
    assertThat(quotaService.supports(null)).isFalse();
    assertThat(quotaService.supports(QuotaService.DataType.CPU_TIME_IN_MEGACYCLES)).isFalse();
    assertThat(quotaService.supports(QuotaService.DataType.API_TIME_IN_MEGACYCLES)).isFalse();

    // Make sure that the class returns 0 for api cycles and cpu cycles
    assertThat(quotaService.getApiTimeInMegaCycles()).isEqualTo(0L);
    assertThat(quotaService.getCpuTimeInMegaCycles()).isEqualTo(0L);

    // Make sure that conversion logic is not affected
    verifyConversions();
  }
}
