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

import com.google.common.collect.ImmutableMap;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the {@link ApplicationEnvironment} class. */
@RunWith(JUnit4.class)
public class ApplicationEnvironmentTest {
  private void getUseGoogleConnectorJDoesNotRequireSetterFuzz(boolean val) throws Exception {
    ApplicationEnvironment ae =
        new ApplicationEnvironment(
            "an_appId",
            "a_versionId",
            ImmutableMap.of("a_sysprop_name", "a_sysprop_value"),
            ImmutableMap.of("an_env_name", "an_env_value"),
            new File("/foo/bar"),
            ApplicationEnvironment.RuntimeConfiguration.builder()
                .setCloudSqlJdbcConnectivityEnabled(true)
                .setUseGoogleConnectorJ(val)
                .build());

    assertThat(ae.getUseGoogleConnectorJ()).isEqualTo(val);

    ae.setUseGoogleConnectorJ(Boolean.valueOf(!val));
    assertThat(ae.getUseGoogleConnectorJ()).isEqualTo(!val);

    ae.setUseGoogleConnectorJ(null); // go back to the ctor default
    assertThat(ae.getUseGoogleConnectorJ()).isEqualTo(val);
  }

  /**
   * Verifies the getUseGoogleConnectorJ method does not require a call to the corresponding setter
   * before it honors the constructor defaults.
   */
  @Test
  public void getUseGoogleConnectorJ_doesNotRequireSetter() throws Exception {
    // Try true and false just for good measure:
    getUseGoogleConnectorJDoesNotRequireSetterFuzz(true);
    getUseGoogleConnectorJDoesNotRequireSetterFuzz(false);
  }
}
