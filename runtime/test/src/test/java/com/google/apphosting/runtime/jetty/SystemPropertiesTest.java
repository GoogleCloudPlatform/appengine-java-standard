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

package com.google.apphosting.runtime.jetty;

import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableSortedMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SystemPropertiesTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void copyAppToTemp() throws IOException {
    copyAppToDir("syspropsapp", temp.getRoot().toPath());
  }

  @Test
  public void expectedSystemProperties() throws Exception {
    Path appRoot = temp.getRoot().toPath();
    try (RuntimeContext<?> context = startApp(appRoot)) {
      String properties = context.executeHttpGet("/", 200);
      ImmutableSortedMap<String, String> propertyMap;
      try (BufferedReader propertiesReader = new BufferedReader(new StringReader(properties))) {
        propertyMap =
            propertiesReader
                .lines()
                .filter(line -> line.contains(" = "))
                .collect(
                    toImmutableSortedMap(
                        naturalOrder(),
                        line -> line.substring(0, line.indexOf(" = ")),
                        line -> line.substring(line.indexOf(" = ") + 3)));
      }
      String expectedRelease = "mainwithdefaults";
      assertThat(propertyMap).containsAtLeast(
          // Set by flags, see JavaRuntimeFactory.startRuntime.
          "appengine.jetty.also_log_to_apiproxy", "true",
          "appengine.urlfetch.deriveResponseMessage", "true",
          // Set automatically, see AppVersionFactory.createSystemProperties.
          "com.google.appengine.application.id", "testapp",
          "com.google.appengine.application.version", "1.0",
          "com.google.appengine.runtime.environment", "Production",
          "com.google.appengine.runtime.version", "Google App Engine/" + expectedRelease,
          // Set from appengine-web.xml.
          "sysprops.test.foo", "bar",
          "javax.xml.parsers.DocumentBuilderFactoryTest", "foobar",
          // Should be set by default.
          "user.dir", appRoot.toString(),
          // Also check that SystemProperty.environment.value() returns the right thing.
          "SystemProperty.environment.value()", "Production");
    }
  }

  private RuntimeContext<?> startApp(Path appRoot) throws IOException, InterruptedException {
    assertThat(Files.isDirectory(appRoot)).isTrue();
    assertThat(Files.isDirectory(appRoot.resolve("WEB-INF"))).isTrue();
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder().setApplicationPath(appRoot.toString()).build();
    return RuntimeContext.create(config);
  }
}
