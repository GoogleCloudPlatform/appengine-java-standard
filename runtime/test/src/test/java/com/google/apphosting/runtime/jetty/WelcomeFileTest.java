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

import static com.google.common.base.StandardSystemProperty.FILE_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WelcomeFileTest extends JavaRuntimeViaHttpBase {
  private static File appRoot;

  @BeforeClass
  public static void beforeClass() throws IOException, InterruptedException {
    Path appPath = temporaryFolder.newFolder("app").toPath();
    copyAppToDir("welcomefile", appPath);
    appRoot = appPath.toFile();
  }

  private RuntimeContext<DummyApiServer> runtimeContext() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config = RuntimeContext.Config.builder()
        .setApplicationPath(appRoot.toString())
        .build();
    return RuntimeContext.create(config);
  }

  @Test
  public void testIndex() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      if (FILE_SEPARATOR.value().equals("/")) {
        runtime.executeHttpGet("/dirWithIndex/", "<h1>Index</h1>\n", RESPONSE_200);
      } else {
        // Windows.
        runtime.executeHttpGet("/dirWithIndex/", "<h1>Index</h1>\r\n", RESPONSE_200);
      }
    }
  }

  @Test
  public void testNoIndex() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
     runtime.executeHttpGet(
        "/dirWithoutIndex/",
        404);
    }
  }

  @Test
  public void testDoNoServeSystemFiles() throws Exception {
    try (RuntimeContext<DummyApiServer> runtime = runtimeContext()) {
      runtime.executeHttpGet("/WEB-INF/appengine-web.xml", 404);
    }
  }
}
