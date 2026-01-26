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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SpringBootTest extends JavaRuntimeViaHttpBase {

  private File appRoot;

  public void initialize() throws IOException, InterruptedException {
    File currentDirectory = new File("").getAbsoluteFile();
    File appBasedir =
        new File(currentDirectory.getParentFile().getParentFile(), "applications/springboot");
    Process process =
        new ProcessBuilder(
                "../../mvnw"
                    + (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                        ? ".cmd" // Windows OS
                        : ""), // Linux OS, no extension for command name.
                "clean",
                "install",
                "-DskipTests",
                "-f",
                new File(appBasedir, "pom.xml").getAbsolutePath())
            .start();
    List<String> results = readOutput(process.getInputStream());
    System.out.println("mvn process output:" + results);
    int exitCode = process.waitFor();
    assertThat(0).isEqualTo(exitCode);

    File targetDir = new File(appBasedir, "target");
    // The build process creates an exploded web application directory in the target/
    // directory, with a name like 'springboot-1.0-SNAPSHOT'. We find that
    // directory to pass to the staging command.
    List<File> appDirs = new ArrayList<>();
    File[] files = targetDir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()
            && f.getName().startsWith("springboot-")
            && !f.getName().equals("appengine-staging")) {
          appDirs.add(f);
        }
      }
    }
    // The maven build is expected to produce only one such directory.
    assertWithMessage("The maven build is expected to produce only one such directory.")
        .that(appDirs)
        .hasSize(1);
    File appDir = appDirs.get(0);
    assertThat(appDir).isNotNull();
    assertThat(appDir.isDirectory()).isTrue();

    appRoot = new File(targetDir, "appengine-staging");
    stageApp(appDir, appRoot);
    assertThat(appRoot.isDirectory()).isTrue();
  }

  private void stageApp(File appDir, File stagingDir) throws IOException, InterruptedException {
    String javaHome = JAVA_HOME.value();
    String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
    String classpath = JAVA_CLASS_PATH.value();

    File currentDirectory = new File("").getAbsoluteFile();
    File sdkRoot =
        new File(currentDirectory.getParentFile().getParentFile(), "sdk_assembly/target/appengine-java-sdk");
    ProcessBuilder pb =
        new ProcessBuilder(
            javaBin,
            "-Dappengine.sdk.root=" + sdkRoot.getAbsolutePath(),
            "-cp",
            classpath,
            "com.google.appengine.tools.admin.AppCfg",
            "stage",
            appDir.getAbsolutePath(),
            stagingDir.getAbsolutePath());
    // Inherit IO for debugging purposes. Output will be visible in the test logs.
    pb.inheritIO();
    Process process = pb.start();
    int exitCode = process.waitFor();
    assertThat(exitCode).isEqualTo(0);
  }

  public SpringBootTest() {
    super("java17", "12.1", "EE11", false);
  }

  private RuntimeContext<DummyApiServer> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder().setApplicationPath(appRoot.toString()).build();
    return createRuntimeContext(config);
  }

  private static List<String> readOutput(InputStream inputStream) throws IOException {
    try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
      return output.lines().map(l -> l + "\n").toList();
    }
  }

  @Test
  public void testSpringBootCanBoot() throws Exception {
    initialize();
    Thread.sleep(1000);
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      runtime.executeHttpGet("/", "Hello world - springboot-appengine-standard!", 200);
    }
  }
}
