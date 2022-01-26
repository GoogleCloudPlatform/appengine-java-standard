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

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClassPathUtilsTest {
  private static final String PATH_SEPARATOR = System.getProperty("path.separator");

  @Before
  public void setUp() {
    System.clearProperty("use.jetty93");
    System.clearProperty("use.jetty94");
    System.clearProperty("use.java11");
    System.clearProperty("classpath.runtime-impl");
    System.clearProperty("use.mavenjars");
    System.clearProperty("classpath.appengine-api-legacy");

    System.setProperty("classpath.runtimebase", "/tmp");
  }

  @Test
  public void verifyDefaultPropertiesAreConfigured() throws Exception {
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
  }

  @Test
  public void verifyJava11PropertiesAreConfigured() throws Exception {
    System.setProperty("use.java11", "true");
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl11.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
  }

  @Test
  public void verifyJetty93PropertiesAreConfigured() throws Exception {
    System.setProperty("use.jetty93", "true");
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
  }

  @Test
  public void verifyJetty93WinsOverJetty94PropertiesAreConfigured() throws Exception {
    // Set both of them and verify that 9.3 is winning:
    System.setProperty("use.jetty93", "true");
    System.setProperty("use.jetty94", "true");
    assertThat(Boolean.getBoolean("use.jetty94")).isTrue();
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    // Check that the jetty94 property is correctly resetted to false.
    assertThat(Boolean.getBoolean("use.jetty94")).isFalse();
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
  }

  @Test
  public void verifyJetty94PropertiesAreConfigured() throws Exception {

    System.setProperty("use.jetty94", "true");
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-jetty94.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party-jetty94.jar"
                + PATH_SEPARATOR
                + "/tmp/appengine-api-1.0-sdk.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
  }

  @Test
  public void verifyMavenJarsPropertiesAreConfigured() throws Exception {
    System.setProperty("use.jetty94", "true");
    System.setProperty("use.mavenjars", "true");

    ClassPathUtils cpu = new ClassPathUtils(new File("/my_app_root"));
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/jars/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/jars/appengine-api-1.0-sdk.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar");

    assertThat(System.getProperty("classpath.runtime-shared"))
        .isEqualTo("/tmp/jars/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map"))
        .isEqualTo("1.0=/tmp/jars/appengine-api-1.0-sdk.jar");
    assertThat(System.getProperty("classpath.appengine-api-legacy"))
        .isEqualTo("/tmp/jars/appengine-api-legacy.jar");

    assertThat(cpu.getAppengineApiLegacyJar().getAbsolutePath())
        .isEqualTo("/my_app_root/tmp/jars/appengine-api-legacy.jar");
    assertThat(cpu.getApiJarForVersion("1.0").getAbsolutePath())
        .isEqualTo("/my_app_root/tmp/jars/appengine-api-1.0-sdk.jar");

  }

  @Test
  public void verifyJetty93WinsOverMavenPropertiesAreConfigured() throws Exception {
    // Set both of them and verify that 9.3 is winning:
    System.setProperty("use.jetty93", "true");
    System.setProperty("use.mavenjars", "true");
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(1);
    assertThat(System.getProperty("classpath.runtime-impl"))
        .isEqualTo(
            "/tmp/runtime-impl.jar"
                + PATH_SEPARATOR
                + "/tmp/frozen_debugger.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-impl-third-party.jar"
                + PATH_SEPARATOR
                + "/tmp/runtime-appengine-api.jar");

    assertThat(System.getProperty("classpath.runtime-shared")).isEqualTo("/tmp/runtime-shared.jar");
    assertThat(System.getProperty("classpath.connector-j"))
        .isEqualTo("/tmp/jdbc-mysql-connector.jar");
    assertThat(System.getProperty("classpath.api-map")).isEqualTo("1.0=/tmp/appengine-api.jar");
    assertThat(System.getProperty("classpath.appengine-api-legacy"))
        .isNull();
  }
}
