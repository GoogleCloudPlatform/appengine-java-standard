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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ApiDeadlineOracle}.
 *
 */
@RunWith(JUnit4.class)
public class ApiDeadlineOracleTest {
  private ApiDeadlineOracle oracle;

  @Before
  public void setUp() throws Exception {
    oracle =
        new ApiDeadlineOracle.Builder()
            .initDeadlineMap(5, "high:60", 10, "high:120")
            .initOfflineDeadlineMap(15, "high:600", 20, "high:1200")
            .build();
  }

  @Test
  public void testDeadline_Default() {
    assertThat(oracle.getDeadline("foo", false, null)).isEqualTo(5.0);
    assertThat(oracle.getDeadline("foo", true, null)).isEqualTo(15.0);
    assertThat(oracle.getDeadline("foo", false, 50)).isEqualTo(10.0);
    assertThat(oracle.getDeadline("foo", true, 50)).isEqualTo(20.0);
    assertThat(oracle.getDeadline("foo", false, 1)).isEqualTo(1.0);
    assertThat(oracle.getDeadline("foo", true, 1)).isEqualTo(1.0);
  }

  @Test
  public void testDeadline_InitialPackageOverride() {
    assertThat(oracle.getDeadline("high", false, null)).isEqualTo(60.0);
    assertThat(oracle.getDeadline("high", true, null)).isEqualTo(600.0);
    assertThat(oracle.getDeadline("high", false, 3600)).isEqualTo(120.0);
    assertThat(oracle.getDeadline("high", true, 3600)).isEqualTo(1200.0);
  }

  @Test
  public void testDeadline_LaterPackageOverride() {
    oracle.addPackageDefaultDeadline("foo", 30);
    oracle.addPackageMaxDeadline("foo", 300);
    oracle.addOfflinePackageDefaultDeadline("foo", 60);
    oracle.addOfflinePackageMaxDeadline("foo", 600);
    assertThat(oracle.getDeadline("foo", false, null)).isEqualTo(30.0);
    assertThat(oracle.getDeadline("foo", true, null)).isEqualTo(60.0);
    assertThat(oracle.getDeadline("foo", false, 3600)).isEqualTo(300.0);
    assertThat(oracle.getDeadline("foo", true, 3600)).isEqualTo(600.0);
  }
}
