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
package com.google.appengine.tools.development;

import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;

import com.google.apphosting.testing.PortPicker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DevAppServerMainTest extends DevAppServerTestBase {
  private static final String TOOLS_JAR =
      getSdkRoot().getAbsolutePath() + "/lib/appengine-tools-api.jar";

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE11"}});
  }

  public DevAppServerMainTest(String version) {
    switch (version) {
      case "EE6":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE11", "false");
        break;
      case "EE8":
        System.setProperty("appengine.use.EE8", "true");
        System.setProperty("appengine.use.EE11", "false");
        break;
      case "EE11":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE11", "true");
        break;
      default:
        // fall through
    }
  }

  @Before
  public void setUpClass() throws IOException, InterruptedException {
    PortPicker portPicker = PortPicker.create();
    int jettyPort = portPicker.pickUnusedPort();
    File appDir =
        Boolean.getBoolean("appengine.use.EE11")
            ? createApp("allinone_jakarta")
            : createApp("allinone");

    ArrayList<String> runtimeArgs = new ArrayList<>();
    runtimeArgs.add(JAVA_HOME.value() + "/bin/java");
    runtimeArgs.add("-Dappengine.sdk.root=" + getSdkRoot());
    if (!JAVA_SPECIFICATION_VERSION.value().equals("1.8")) {
      // Java11 or later need more flags:
      runtimeArgs.add("--add-opens");
      runtimeArgs.add("java.base/java.net=ALL-UNNAMED");
      runtimeArgs.add("--add-opens");
      runtimeArgs.add("java.base/sun.net.www.protocol.http=ALL-UNNAMED");
      runtimeArgs.add("--add-opens");
      runtimeArgs.add("java.base/sun.net.www.protocol.https=ALL-UNNAMED");
    } else {
      // Jetty12 does not support java8.
      System.setProperty("appengine.use.EE8", "false");
      System.setProperty("appengine.use.EE11", "false");
    }
    runtimeArgs.add("-Dappengine.use.EE8=" + System.getProperty("appengine.use.EE8"));
    runtimeArgs.add("-Dappengine.use.EE11=" + System.getProperty("appengine.use.EE11"));
    runtimeArgs.add("-cp");
    runtimeArgs.add(TOOLS_JAR);
    runtimeArgs.add("com.google.appengine.tools.development.DevAppServerMain");
    runtimeArgs.add("--address=" + new InetSocketAddress(jettyPort).getHostString());
    runtimeArgs.add("--port=" + jettyPort);
    runtimeArgs.add("--allow_remote_shutdown"); // Keep as used in Maven plugin
    runtimeArgs.add("--disable_update_check"); // Keep, as used in Maven plugin
    runtimeArgs.add("--no_java_agent"); // Keep, as used in Maven plugin

    runtimeArgs.add(appDir.toString());
    createRuntime(ImmutableList.copyOf(runtimeArgs), ImmutableMap.of(), jettyPort);
  }
}
