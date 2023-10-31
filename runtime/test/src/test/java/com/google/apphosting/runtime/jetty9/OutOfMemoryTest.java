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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that {@code -XX:ExitOnOutOfMemoryError} gets through to the runtime and has the intended
 * effect.
 */
@RunWith(Parameterized.class)
public class OutOfMemoryTest extends JavaRuntimeViaHttpBase {
  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE10"}});
  }

  public OutOfMemoryTest(String version) {
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
        // TODO  System.setProperty("appengine.use.EE8", "false");
        // TODO  System.setProperty("appengine.use.EE10", "true");
        break;
      default:
        // fall through
    }
    if (Boolean.getBoolean("test.running.internally")) { // Internal can only do EE6
      System.setProperty("appengine.use.EE8", "false");
      System.setProperty("appengine.use.EE10", "false");
    }
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void copyAppToTemp() throws IOException {
    copyAppToDir("outofmemoryapp", temp.getRoot().toPath());
  }

  @Test
  public void outOfMemoryBehaviour() throws Exception {
    try (RuntimeContext<?> runtime = startApp()) {
      try {
        runtime.executeHttpGet("/", 200);
        fail("Did not get expected exception");
      } catch (IOException expected) {
        // We expect the read of the HTTP result to fail because the process on the other end has
        // died, or to time out because it is stuck. Now check that the process exited, and with the
        // status we expected.
        // http://google3/third_party/java_src/jdk/openjdk8/u60/trunk/hotspot/src/share/vm/utilities/debug.cpp?l=333&rcl=218939537
        Process process = runtime.runtimeProcess();
        boolean exited = process.waitFor(20, SECONDS);
        assertThat(exited).isTrue();
        int status = process.exitValue();
        assertThat(status).isEqualTo(3);
      }
    }
  }

  private RuntimeContext<?> startApp() throws IOException, InterruptedException {
    RuntimeContext.Config<DummyApiServer> config =
        RuntimeContext.Config.builder()
            .setApplicationPath(temp.getRoot().getAbsolutePath())
            .setEnvironmentEntries(ImmutableMap.of("GAE_JAVA_OPTS", "-XX:+ExitOnOutOfMemoryError"))
            .build();
    return RuntimeContext.create(config);
  }
}
