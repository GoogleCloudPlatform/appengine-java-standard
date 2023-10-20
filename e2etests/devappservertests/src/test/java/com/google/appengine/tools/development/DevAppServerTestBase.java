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

import com.google.apphosting.testing.PortPicker;
import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

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
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class DevAppServerTestBase {
  private int jettyPort;
  private Process runtimeProc;
  private CountDownLatch serverStarted;

  protected static final int NUMBER_OF_RETRIES = 5;

  private static HttpClient httpClient;
  protected static final int RESPONSE_200 = 200;
  private static final String TOOLS_JAR =
      getSdkRoot().getAbsolutePath() + "/lib/appengine-tools-api.jar";

  @Parameterized.Parameters
  public static Collection EEVersion() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE10"}});
  }

  public DevAppServerTestBase(String EEVersion) {
    if (EEVersion.equals("EE6")) {
        System.setProperty("appengine.use.jetty12", "false");
        System.setProperty("appengine.use.EE10", "false");
    } else if (EEVersion.equals("EE8")) {
        System.setProperty("appengine.use.jetty12", "true");
        System.setProperty("appengine.use.EE10", "false");
    }  else if (EEVersion.equals("EE10")) {
        System.setProperty("appengine.use.jetty12", "true");
        System.setProperty("appengine.use.EE10", "true");
    }
  }
  abstract  public File getAppDir() ;
  
  @Before
  public void setUpClass() throws IOException, InterruptedException {
    PortPicker portPicker = PortPicker.create();
    jettyPort = portPicker.pickUnusedPort();
    File appDir = getAppDir();

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
        System.setProperty("appengine.use.jetty12", "false");
        System.setProperty("appengine.use.EE10", "false");
    }
    runtimeArgs.add("-Dappengine.use.jetty12=" + System.getProperty("appengine.use.jetty12"));
    runtimeArgs.add("-Dappengine.use.EE10=" + System.getProperty("appengine.use.EE10"));
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
  
  static File createApp(String directoryName) {
    File currentDirectory = new File("").getAbsoluteFile();
    File appRoot =
        new File(
            currentDirectory,
            "../testlocalapps/" + directoryName + "/target/" + directoryName + "-2.0.22-SNAPSHOT");
    return appRoot;
  }

  static File getSdkRoot() {
    File currentDirectory = new File("").getAbsoluteFile();
    return new File(currentDirectory, "../../sdk_assembly/target/appengine-java-sdk");
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

  protected void executeHttpGet(String url, String expectedResponseBody, int expectedReturnCode)
      throws Exception {
    executeHttpGetWithRetries(
        url, expectedResponseBody, expectedReturnCode, /* numberOfRetries= */ 1);
  }

  protected void executeHttpGetContains(String url, String containsResponse, int expectedReturnCode)
      throws Exception {
    executeHttpGetWithRetriesContains(
        url, containsResponse, expectedReturnCode, /* numberOfRetries= */ 1);
  }
  
  protected void executeHttpGetWithRetries(
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

  protected void executeHttpGetWithRetriesContains(
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
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
