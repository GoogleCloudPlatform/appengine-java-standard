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
package com.google.apphosting.runtime.tests;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.HttpApiServer;
import com.google.apphosting.runtime.jetty9.JavaRuntimeViaHttpBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class GuestBookTest extends JavaRuntimeViaHttpBase {

  private static File appRoot;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(
        new Object[][] {
          {"9.4", "EE6"},
          {"12.0", "EE8"},
          {"12.0", "EE10"},
        });
  }

  public GuestBookTest(String jettyVersion, String jakartaVersion)
      throws IOException, InterruptedException {
    setupSystemProperties(jettyVersion, jakartaVersion);
    File currentDirectory = new File("").getAbsoluteFile();
    String appName = "guestbook";
    if (jakartaVersion.equals("EE10") || jakartaVersion.equals("EE11")) {
      appName = "guestbook_jakarta";
    }

    File appRootTarget =
        new File(currentDirectory.getParentFile().getParentFile(), "applications/" + appName);
    Process process =
        new ProcessBuilder(
                "../../mvnw"
                    + ((System.getProperty("os.name").toLowerCase().contains("windows"))
                        ? ".cmd" // Windows OS
                        : ""), // Linux OS, no extension for command name.
                "clean",
                "install",
                "-f",
                new File(appRootTarget, "pom.xml").getAbsolutePath())
            .start();
    List<String> results = readOutput(process.getInputStream());
    System.out.println("mvn process output:" + results);
    int exitCode = process.waitFor();
    assertThat(0).isEqualTo(exitCode);

    process =
        new ProcessBuilder(
                "../../sdk_assembly/target/appengine-java-sdk/bin/appcfg"
                    + ((System.getProperty("os.name").toLowerCase().contains("windows"))
                        ? ".cmd" // Windows OS
                        : ".sh"), // Linux OS.
                "stage",
                appRootTarget.getAbsolutePath() + "/target/" + appName + "-2.0.39-beta-SNAPSHOT",
                appRootTarget.getAbsolutePath() + "/target/appengine-staging")
            .start();
    results = readOutput(process.getInputStream());
    System.out.println("mvn process output:" + results);
    exitCode = process.waitFor();
    assertThat(0).isEqualTo(exitCode);
    appRoot = new File(appRootTarget, "target/appengine-staging").getAbsoluteFile();
    assertThat(appRoot.isDirectory()).isTrue();
  }

  public void setupSystemProperties(String jettyVersion, String jakartaVersion) {
    if (jettyVersion.equals("12.1")) {
      System.setProperty("appengine.use.jetty121", "true");
    } else {
      System.setProperty("appengine.use.jetty121", "false");
    }
    switch (jakartaVersion) {
      case "EE6":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "false");
        System.setProperty("appengine.use.EE11", "false");
        break;
      case "EE8":
        System.setProperty("appengine.use.EE8", "true");
        System.setProperty("appengine.use.EE10", "false");
        System.setProperty("appengine.use.EE11", "false");
        break;
      case "EE10":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "true");
        System.setProperty("appengine.use.EE11", "false");
        break;
      case "EE11":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "false");
        System.setProperty("appengine.use.EE11", "true");
        break;
      default:
        // fall through
    }
  }

  private RuntimeContext<?> runtimeContext() throws IOException, InterruptedException {
    ApiServerFactory<HttpApiServer> apiServerFactory =
        (apiPort, runtimePort) -> {
          HttpApiServer httpApiServer = new HttpApiServer(apiPort, "localhost", runtimePort);
          httpApiServer.start(false);
          return httpApiServer;
        };
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder(apiServerFactory)
            .setApplicationPath(appRoot.toString())
            .build();
    return RuntimeContext.create(config);
  }

  private static List<String> readOutput(InputStream inputStream) throws IOException {
    try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
      return output.lines().map(l -> l + "\n").collect(Collectors.toList());
    }
  }

  @Test
  public void testGuesttBookJSPStaged() throws Exception {
    try (RuntimeContext<?> runtime = runtimeContext()) {
      runtime.executeHttpGet("/guestbook.jsp", "<p>Guestbook 'default' has no messages.</p>", 200);
    }
  }
}
