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
import static org.junit.Assert.assertThrows;

import com.google.common.truth.Expect;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JavaRuntimeParamsTest {
  @Rule public Expect expect = Expect.create();

  @Test
  public void testUnknownArgumentsAllowed() {
    String[] args = {"--xyz=abc"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.unknownParams()).containsExactly("--xyz=abc");
  }
  @Test
  public void testDefaults() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs();
    assertThat(params.trustedHost()).isEmpty();
    assertThat(params.fixedApplicationPath()).isNull();
    assertThat(params.jettyHttpPort()).isEqualTo(8080);
  }

  @Test
  public void testGetUnknownParams() {
    String[] args = {"--unknown1=xyz", "--trusted_host=abc", "--unknown2=xyz"};
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs(args);
    assertThat(params.unknownParams())
        .containsExactly("--unknown1=xyz", "--unknown2=xyz")
        .inOrder();
  }

  @Test
  public void testTrustedHost() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs("--trusted_host=foo");
    assertThat(params.trustedHost()).isEqualTo("foo");
  }

  @Test
  public void testFixedApplicationPath() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs("--fixed_application_path=bar");
    assertThat(params.fixedApplicationPath()).isEqualTo("bar");
  }

  @Test
  public void testFixedApplicationPath_missingValue_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaRuntimeParams.parseArgs("--fixed_application_path"));
    assertThat(exception).hasMessageThat().contains("Missing value for --fixed_application_path");
  }

  @Test
  public void testJettyHttpPort() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs("--jetty_http_port=1234");
    assertThat(params.jettyHttpPort()).isEqualTo(1234);
  }

  @Test
  public void testJettyHttpPort_invalidValue_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaRuntimeParams.parseArgs("--jetty_http_port=abc"));
    assertThat(exception).hasMessageThat().contains("Invalid value for --jetty_http_port=abc");
  }

  @Test
  public void testTrustedHost_missingValue_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaRuntimeParams.parseArgs("--trusted_host"));
    assertThat(exception).hasMessageThat().contains("Missing value for --trusted_host");
  }

  @Test
  public void testCloneMaxOutstandingApiRpcs() {
    JavaRuntimeParams params = JavaRuntimeParams.parseArgs("--clone_max_outstanding_api_rpcs=50");
    assertThat(params.maxOutstandingApiRpcs()).isEqualTo(50);
  }

  @Test
  public void testCloneMaxOutstandingApiRpcs_missingValue_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaRuntimeParams.parseArgs("--clone_max_outstanding_api_rpcs"));
    assertThat(exception)
        .hasMessageThat()
        .contains("Missing value for --clone_max_outstanding_api_rpcs");
  }

  @Test
  public void testCloneMaxOutstandingApiRpcs_invalidValue_throwsException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaRuntimeParams.parseArgs("--clone_max_outstanding_api_rpcs=xyz"));
    assertThat(exception)
        .hasMessageThat()
        .contains("Invalid value for --clone_max_outstanding_api_rpcs=xyz");
  }
}
