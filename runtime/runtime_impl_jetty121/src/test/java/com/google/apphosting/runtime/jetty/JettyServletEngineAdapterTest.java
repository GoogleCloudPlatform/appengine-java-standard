/*
 * Copyright 2026 Google LLC
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

package com.google.apphosting.runtime.jetty;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JettyServletEngineAdapterTest {

  @Test
  public void testGetMaxSafeCarrierParallelism_boundaries() {
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism(null)).isEqualTo(4);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("")).isEqualTo(4);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("invalid")).isEqualTo(4);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("256")).isEqualTo(1);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("512")).isEqualTo(1);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("600")).isEqualTo(2);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("1024")).isEqualTo(2);
    assertThat(JettyServletEngineAdapter.getMaxSafeCarrierParallelism("2048")).isEqualTo(4);
  }
}
