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
package com.google.apphosting.runtime.jetty9;

import static com.google.common.truth.Truth.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SpringBootTest extends JavaRuntimeViaHttpBase {

  private static File appRoot;

  @BeforeClass
  public static void beforeClass() throws IOException, InterruptedException {
    File currentDirectory = new File("").getAbsoluteFile();
    Process process =
        new ProcessBuilder(
                "mvn",
                "install",
                "appengine:stage",
                "-f",
                new File(currentDirectory, "../../applications/springboot/pom.xml")
                    .getAbsolutePath())
            .start();
    List<String> results = readOutput(process.getInputStream());
    System.out.println("mvn process output:" + results);
    int exitCode = process.waitFor();
    assertThat(0).isEqualTo(exitCode);
    appRoot = new File(currentDirectory, "../../applications/springboot/target/appengine-staging");
    assertThat(appRoot.isDirectory()).isTrue();
  }

  private RuntimeContext<DummyApiServer> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder().setApplicationPath(appRoot.toString()).build();
    return RuntimeContext.create(config);
  }

  private static List<String> readOutput(InputStream inputStream) throws IOException {
    try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
      return output.lines().collect(Collectors.toList());
    }
  }

  @Test
  public void testSpringBootCanBoot() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      runtime.executeHttpGet("/", "Hello world - springboot-appengine-standard!", 200);
    }
  }
}
