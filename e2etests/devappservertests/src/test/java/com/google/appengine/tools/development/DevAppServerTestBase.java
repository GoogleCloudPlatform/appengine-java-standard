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
import static com.google.common.base.StandardSystemProperty.JAVA_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.apphosting.testing.PortPicker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.runners.Parameterized;

public abstract class DevAppServerTestBase {
  int jettyPort;
  private Process runtimeProc;
  private CountDownLatch serverStarted;

  static final int NUMBER_OF_RETRIES = 5;

  static HttpClient httpClient;
  static final int RESPONSE_200 = 200;
  private static final String TOOLS_JAR =
      getSdkRoot().getAbsolutePath() + "/lib/appengine-tools-api.jar";

  @Parameterized.Parameters
  public static List<Object[]> version() {
    List<Object[]> allVersions =
        Arrays.asList(
            new Object[][] {
              {"java17", "9.4", "EE6"},
              {"java17", "12.0", "EE8"},
              {"java17", "12.0", "EE10"},
              {"java17", "12.1", "EE11"},
              {"java21", "12.0", "EE8"},
              {"java21", "12.0", "EE10"},
              {"java21", "12.1", "EE11"},
              {"java25", "12.1", "EE8"},
              {"java25", "12.1", "EE11"}
            });
    String version = JAVA_VERSION.value();
    String majorVersion;
    // Major version parsing in java.version property can be "1.8.0_201" for java8, "11.0.17" for
    // java11+, or "25-ea+35" for early access versions.
    if (version.startsWith("1.")) {
      majorVersion = version.substring(2, 3);
    } else {
      int dash = version.indexOf("-");
      if (dash != -1) {
        majorVersion = version.substring(0, dash);
      } else {
        int dot = version.indexOf(".");
        if (dot != -1) {
          majorVersion = version.substring(0, dot);
        } else {
          majorVersion = version;
        }
      }
    }
    // We only run the tests for the current JDK version.
    // So we filter the list of versions based on the current `java.version` property.
    // We bucket versions into 17, 21, or 25.
    int numVersion = Integer.parseInt(majorVersion);
    if ((numVersion > 21) && (numVersion < 25)) {
      numVersion = 21;
    } else if ((numVersion > 25)) {
      numVersion = 25;
    } else if ((numVersion < 21)) {
      numVersion = 17;
    }
    String javaVersionForTest = "java" + numVersion;
    System.out.println("javaVersionForTest " + javaVersionForTest);
    return allVersions.stream()
        .filter(v -> v[0].toString().equals(javaVersionForTest))
        .collect(toImmutableList());
  }

  public DevAppServerTestBase(String runtimeVersion, String jettyVersion, String jakartaVersion) {
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
    if (jettyVersion.equals("12.1")) {
      System.setProperty("appengine.use.jetty121", "true");
    } else {
      System.setProperty("appengine.use.jetty121", "false");
    }
  }

  static File createApp(String directoryName) {
    File currentDirectory = new File("").getAbsoluteFile();
    File appRoot =
        new File(
            currentDirectory,
            "../testlocalapps/"
                + directoryName
                + "/target/"
                + directoryName
                + "-"
                + System.getProperty("appengine.projectversion"));
    return appRoot;
  }

  static File getSdkRoot() {
    File currentDirectory = new File("").getAbsoluteFile();
    return new File(currentDirectory, "../../sdk_assembly/target/appengine-java-sdk");
  }

  public void setUpClass(File appDir) throws IOException, InterruptedException {
    PortPicker portPicker = PortPicker.create();
    int jettyPort = portPicker.pickUnusedPort();

    ArrayList<String> runtimeArgs = new ArrayList<>();
    runtimeArgs.add(JAVA_HOME.value() + "/bin/java");
    runtimeArgs.add("-Dappengine.sdk.root=" + getSdkRoot());
    // Java17 or later need more flags:
    runtimeArgs.add("--add-opens");
    runtimeArgs.add("java.base/java.net=ALL-UNNAMED");
    runtimeArgs.add("--add-opens");
    runtimeArgs.add("java.base/sun.net.www.protocol.http=ALL-UNNAMED");
    runtimeArgs.add("--add-opens");
    runtimeArgs.add("java.base/sun.net.www.protocol.https=ALL-UNNAMED");

    runtimeArgs.add("-Dappengine.use.EE8=" + System.getProperty("appengine.use.EE8"));
    runtimeArgs.add("-Dappengine.use.EE10=" + System.getProperty("appengine.use.EE10"));
    runtimeArgs.add("-Dappengine.use.EE11=" + System.getProperty("appengine.use.EE11"));
    runtimeArgs.add("-Dappengine.use.jetty121=" + System.getProperty("appengine.use.jetty121"));
    runtimeArgs.add("-cp");
    runtimeArgs.add(TOOLS_JAR);
    runtimeArgs.add("com.google.appengine.tools.development.DevAppServerMain");
    runtimeArgs.add("--address=" + new InetSocketAddress(jettyPort).getHostString());
    runtimeArgs.add("--port=" + jettyPort);
    runtimeArgs.add("--allow_remote_shutdown"); // Keep as used in Maven plugin
    runtimeArgs.add("--disable_update_check"); // Keep, as used in Maven plugin

    runtimeArgs.add(appDir.toString());
    createRuntime(ImmutableList.copyOf(runtimeArgs), ImmutableMap.of(), jettyPort);
  }

  void createRuntime(
      ImmutableList<String> runtimeArgs,
      ImmutableMap<String, String> extraEnvironmentEntries,
      int port)
      throws IOException, InterruptedException {
    serverStarted = new CountDownLatch(1);
    jettyPort = port;
    runtimeProc = launchRuntime(runtimeArgs, extraEnvironmentEntries);
    int connectTimeoutMs = 30_000;
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(connectTimeoutMs)
            .setConnectionRequestTimeout(connectTimeoutMs)
            .setSocketTimeout(connectTimeoutMs)
            .build();
    httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
  }

  @After
  public void destroyRuntime() throws Exception {
    runtimeProc.destroy();
  }

  private Process launchRuntime(
      ImmutableList<String> args, ImmutableMap<String, String> extraEnvironmentEntries)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(args);
    pb.environment().putAll(extraEnvironmentEntries);
    Process process = pb.start();

    OutputPump outPump = new OutputPump(process.getInputStream(), serverStarted);
    OutputPump errPump = new OutputPump(process.getErrorStream(), serverStarted);
    new Thread(outPump).start();
    new Thread(errPump).start();
    serverStarted.await();
    return process;
  }

  void executeHttpGet(String url, String expectedResponseBody, int expectedReturnCode)
      throws Exception {
    executeHttpGetWithRetries(
        url, expectedResponseBody, expectedReturnCode, /* numberOfRetries= */ 1);
  }

  void executeHttpGetWithRetries(
      String url, String expectedResponse, int expectedReturnCode, int numberOfRetries)
      throws Exception {
    HttpGet get =
        new HttpGet(
            String.format(
                "http://%s%s",
                HostAndPort.fromParts(new InetSocketAddress(jettyPort).getHostString(), jettyPort),
                url));
    String content = "";
    int retCode = 0;
    for (int i = 0; i < numberOfRetries; i++) {
      HttpResponse response = httpClient.execute(get);
      retCode = response.getStatusLine().getStatusCode();
      content = EntityUtils.toString(response.getEntity());
      if ((retCode == expectedReturnCode) && content.equals(expectedResponse)) {
        return;
      }
      Thread.sleep(1000);
    }
    assertThat(content).isEqualTo(expectedResponse);
    assertThat(retCode).isEqualTo(expectedReturnCode);
  }

  void executeHttpGetWithRetriesContains(
      String url, String expectedResponse, int expectedReturnCode, int numberOfRetries)
      throws Exception {
    HttpGet get =
        new HttpGet(
            String.format(
                "http://%s%s",
                HostAndPort.fromParts(new InetSocketAddress(jettyPort).getHostString(), jettyPort),
                url));
    String content = "";
    int retCode = 0;
    for (int i = 0; i < numberOfRetries; i++) {
      HttpResponse response = httpClient.execute(get);
      retCode = response.getStatusLine().getStatusCode();
      content = EntityUtils.toString(response.getEntity());
      if ((retCode == expectedReturnCode) && content.equals(expectedResponse)) {
        return;
      }
      Thread.sleep(1000);
    }
    assertThat(content).contains(expectedResponse);
    assertThat(retCode).isEqualTo(expectedReturnCode);
  }

  private static class OutputPump implements Runnable {
    private final BufferedReader stream;
    private final CountDownLatch serverStarted;

    public OutputPump(InputStream instream, CountDownLatch serverStarted) {
      this.serverStarted = serverStarted;
      this.stream = new BufferedReader(new InputStreamReader(instream, UTF_8));
    }

    @Override
    public void run() {
      String line = null;
      try {
        while ((line = stream.readLine()) != null) {
          System.out.println(line);
          if (line.contains("INFO: Dev App Server is now running")) {
            serverStarted.countDown();
          }
        }
      } catch (IOException ignored) {
        // ignored
      }
    }
  }
}
