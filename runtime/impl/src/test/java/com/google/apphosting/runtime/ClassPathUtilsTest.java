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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClassPathUtilsTest {
  private String runtimeLocation = null;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    System.clearProperty("classpath.runtime-impl");
    System.clearProperty("classpath.appengine-api-legacy");
    System.clearProperty("classpath.connector-j");
    runtimeLocation = temporaryFolder.getRoot().getAbsolutePath();
    System.setProperty("classpath.runtimebase", runtimeLocation);
  }

  @Test
  public void verifyJava11PropertiesAreConfigured() throws Exception {
    ClassPathUtils cpu = new ClassPathUtils();
    assertThat(cpu.getConnectorJUrls()).hasLength(0);
    if (Boolean.getBoolean("appengine.use.EE8")|| Boolean.getBoolean("appengine.use.EE10")) {
        assertThat(System.getProperty("classpath.runtime-impl"))
                .isEqualTo(runtimeLocation + "/runtime-impl-jetty12.jar");
        assertThat(System.getProperty("classpath.runtime-shared"))
                .isEqualTo(runtimeLocation + "/runtime-shared-jetty12.jar");
    } else {
        assertThat(System.getProperty("classpath.runtime-impl"))
                .isEqualTo(runtimeLocation + "/runtime-impl-jetty9.jar");
        assertThat(System.getProperty("classpath.runtime-shared"))
                .isEqualTo(runtimeLocation + "/runtime-shared-jetty9.jar");
    }
    assertThat(System.getProperty("classpath.connector-j")).isNull();
  }
}
