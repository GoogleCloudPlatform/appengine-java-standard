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

import com.google.apphosting.runtime.jetty9.JavaRuntimeViaHttpBase;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class AsyncServletAppTest extends JavaRuntimeViaHttpBase {

  private RuntimeContext<?> runtime;

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return allVersions();
  }

  public AsyncServletAppTest(
      String runtimeVersion, String jettyVersion, String version, boolean useHttpConnector) {
    super(runtimeVersion, jettyVersion, version, useHttpConnector);
  }

  @Before
  public void startRuntime() throws Exception {

    File currentDirectory = new File("").getAbsoluteFile();
    String appName = "servletasyncapp";
    if (isJakarta()) {
      appName = "servletasyncappjakarta";
    }
    File appRoot =
        new File(
            currentDirectory,
            "../../applications/"
                + appName
                + "/target/"
                + appName
                + "-"
                + System.getProperty("appengine.projectversion"));
    assertThat(appRoot.isDirectory()).isTrue();
    RuntimeContext.Config<?> config =
        RuntimeContext.Config.builder()
            .setApplicationPath(appRoot.getAbsolutePath())
            .setEnvironmentEntries(
                ImmutableMap.of(
                    "GAE_VERSION", "v1.1",
                    "GOOGLE_CLOUD_PROJECT", "test-servlets-async"))
            .build();
    runtime = createRuntimeContext(config);
  }

  @After
  public void stop() throws IOException {
    runtime.close();
  }

  @Test
  public void invokeServletUsingJettyHttpProxy() throws Exception {
    if (jettyVersion.equals("12.0") && (useHttpConnector == false)) {
      return; // TODO (Ludo) Async does not work on this mode.
    }
    runtime.executeHttpGet(
        "/asyncservlet?time=1000",
        "isAsyncStarted : true\n" + "PASS: 1000 milliseconds.",
        200);
  }
}
