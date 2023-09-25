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
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class AnnotationScanningTest extends JavaRuntimeViaHttpBase {

  private static File appRoot;

  @Parameterized.Parameters
  public static Collection jetty12() {
    return Arrays.asList(new Object[][] {{true}, {false}});
  }

  public AnnotationScanningTest(Boolean useJetty12) {
    System.setProperty("appengine.use.jetty12", useJetty12.toString());
  }

  @BeforeClass
  public static void beforeClass() throws IOException, InterruptedException {
    File currentDirectory = new File("").getAbsoluteFile();
    appRoot =
        new File(
            currentDirectory, "../annotationscanningwebapp/target/annotationscanningwebapp-1.0");
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
