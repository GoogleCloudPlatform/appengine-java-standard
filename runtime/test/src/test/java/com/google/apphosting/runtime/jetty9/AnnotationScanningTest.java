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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class AnnotationScanningTest extends JavaRuntimeViaHttpBase {

  private static File appRoot;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE10"}});
  }

  public AnnotationScanningTest(String version) {
    switch (version) {
      case "EE6":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "false");
        break;
      case "EE8":
        System.setProperty("appengine.use.EE8", "true");
        System.setProperty("appengine.use.EE10", "false");
        break;
      case "EE10":
        //TODO System.setProperty("appengine.use.EE8", "false");
        //TODO  System.setProperty("appengine.use.EE10", "true");
        break;
      default:
        // fall through
    }
  }

  @BeforeClass
  public static void beforeClass() throws IOException, InterruptedException {
    File currentDirectory = new File("").getAbsoluteFile();
    appRoot =
        new File(
            currentDirectory,
            "../annotationscanningwebapp/target/annotationscanningwebapp-2.0.23-SNAPSHOT");
    assertThat(appRoot.isDirectory()).isTrue();
  }

  private RuntimeContext<DummyApiServer> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder().setApplicationPath(appRoot.toString()).build();
    return RuntimeContext.create(config);
  }

  @Test
  public void testAnnotationScanning() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      runtime.executeHttpGet("/", 200);
    }
  }
}
