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
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class FailureFilterTest extends JavaRuntimeViaHttpBase {

  private File appRoot;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  public FailureFilterTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector)
      throws IOException, InterruptedException {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
    if (Boolean.getBoolean("test.running.internally")) { // Internal can only do EE6
      System.setProperty("appengine.use.EE8", "false");
      System.setProperty("appengine.use.EE10", "false");
      System.setProperty("appengine.use.EE11", "false");
    }
    String appName = "failinitfilterwebapp";
    if (version.equals("EE10") || version.equals("EE11")) {
      appName = "failinitfilterwebappjakarta";
    }
    File currentDirectory = new File("").getAbsoluteFile();
    appRoot =
        new File(
            currentDirectory,
            "../"
                + appName
                + "/target/"
                + appName
                + "-"
                + System.getProperty("appengine.projectversion"));
    assertThat(appRoot.isDirectory()).isTrue();
  }

  private RuntimeContext<DummyApiServer> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder().setApplicationPath(appRoot.toString()).build();
    return createRuntimeContext(config);
  }

  @Test
  public void testFilterInitFailed() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      assertThat(runtime.executeHttpGet("/", 500))
          .contains("servlet.ServletException: Intentionally failing to initialize.");
    }
  }
}
