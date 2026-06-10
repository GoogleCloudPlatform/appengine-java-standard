/*
 * Copyright 2026 Google LLC
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
package com.google.appengine.tools.development;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DevAppServerFullScanTest extends DevAppServerTestBase {

  public DevAppServerFullScanTest(String runtimeVersion, String jettyVersion, String jakartaVersion) {
    super(runtimeVersion, jettyVersion, jakartaVersion);
  }

  private File appDir;

  @Before
  public void setUpClass() throws IOException, InterruptedException {
    appDir =
        Boolean.getBoolean("appengine.use.EE10") || Boolean.getBoolean("appengine.use.EE11")
            ? createApp("allinone_jakarta")
            : createApp("allinone");
    setUpClass(appDir);
  }

  @Override
  protected List<String> getExtraJvmArgs() {
    return ImmutableList.of("-Dappengine.fullscan.seconds=1");
  }

  @Test
  public void testFullScanStartAndReload() throws Exception {
    // Basic request to ensure server is running and hot reload scanner starts up fine
    executeHttpGet("/?memcache_loops=1&memcache_size=1", "Running memcache for 1 loops with value size 1\nCache hits: 1\nCache misses: 0\n", RESPONSE_200);

    // Clear logs to ensure we only search for the subsequent reload event
    serverLogs.clear();

    // Touch web.xml to trigger a reload
    File webXml = new File(appDir, "WEB-INF/web.xml");
    com.google.common.truth.Truth.assertThat(webXml.exists()).isTrue();
    long oldLastModified = webXml.lastModified();
    long newTime = oldLastModified + 2000;
    boolean modified = webXml.setLastModified(newTime);
    com.google.common.truth.Truth.assertThat(modified).isTrue();

    // Verify that the hot-reload scanner initiates a reload
    boolean reloaded = awaitLogContains("A file has changed, reloading the web application.", 10);
    com.google.common.truth.Truth.assertThat(reloaded).isTrue();

    // Clear logs again to test a deeply nested resource file (depth > 3)
    serverLogs.clear();

    File nestedClassFile = new File(appDir, "WEB-INF/classes/allinone/deeper/package/test/Dummy.properties");
    nestedClassFile.getParentFile().mkdirs();
    java.nio.file.Files.write(nestedClassFile.toPath(), new byte[]{0, 1, 2, 3});

    // Verify that the hot-reload scanner initiates a reload for the deeply nested resource file
    boolean nestedReloaded = awaitLogContains("A file has changed, reloading the web application.", 10);
    com.google.common.truth.Truth.assertThat(nestedReloaded).isTrue();
  }
}
