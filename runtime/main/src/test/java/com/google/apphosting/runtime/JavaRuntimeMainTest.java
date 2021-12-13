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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link JavaRuntimeMain}.
 */
@RunWith(JUnit4.class)
public class JavaRuntimeMainTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private JavaRuntimeMain main;

  @Before
  public void setUp() {
    System.clearProperty("use.jetty94");
    System.clearProperty("disable_api_call_logging_in_apiproxy");
    System.clearProperty("gae.do_not_use_bundled_jdbc_driver");
    System.clearProperty("gae.do_not_use_bundled_conscrypt");
    main = new JavaRuntimeMain();
  }

  @Test
  public void getFlag_missing() {
    String[] args = {"a", "--b", "-c"};
    String v = main.getFlag(args, "f", "");
    assertThat(v).isNull();
  }

  @Test
  public void getFlag_valueSetUsingEquals() {
    String[] args = {"a", "--b", "--f=foo", "-c"};
    String v = main.getFlag(args, "f", "");
    assertThat(v).isEqualTo("foo");
  }

  @Test
  public void getFlag_valueSetWithoutEquals() {
    String[] args = {"a", "--b", "--f", "foo", "-c"};
    String v = main.getFlag(args, "f", "");
    assertThat(v).isEqualTo("foo");
  }

  @Test
  public void getFlag_missingValue() {
    String[] args = {"a", "--b", "--f"};
    String v = main.getFlag(args, "f", "");
    assertThat(v).isNull();
  }

  @Test
  public void testNoOptionalProperties() {
    String appRoot = temporaryFolder.getRoot().toString();
    String[] args = {"--fixed_application_path=" + appRoot};
    main.processOptionalProperties(args);
    assertThat(main.getApplicationPath(args)).isEqualTo(appRoot);
    assertThat(System.getProperty("use.jetty94")).isNull();
    assertThat(System.getProperty("gae.do_not_use_bundled_jdbc_driver")).isNull();
    assertThat(System.getProperty("gae.do_not_use_bundled_conscrypt")).isNull();
    assertThat(System.getProperty("disable_api_call_logging_in_apiproxy")).isNull();
  }

  @Test
  public void testWithOptionalProperties() throws IOException {
    File webInf = temporaryFolder.newFolder("WEB-INF");
    File properties = new File(webInf, "appengine_optional.properties");
    try (PrintWriter writer = new PrintWriter(properties, UTF_8.name())) {
      writer.println("use.jetty94=true");
      writer.println("disable_api_call_logging_in_apiproxy=true");
      writer.println("gae.do_not_use_bundled_jdbc_driver=true");
      writer.println("gae.do_not_use_bundled_conscrypt=true");
    }
    String appRoot = temporaryFolder.getRoot().toString();
    String[] optionalProperties = {"--fixed_application_path=" + appRoot};
    main.processOptionalProperties(optionalProperties);
    assertThat(main.getApplicationPath(optionalProperties)).isEqualTo(appRoot);
    assertThat(System.getProperty("use.jetty94")).isEqualTo("true");
    assertThat(System.getProperty("gae.do_not_use_bundled_jdbc_driver")).isEqualTo("true");
    assertThat(System.getProperty("gae.do_not_use_bundled_conscrypt")).isEqualTo("true");
    assertThat(System.getProperty("disable_api_call_logging_in_apiproxy")).isEqualTo("true");
  }
}
