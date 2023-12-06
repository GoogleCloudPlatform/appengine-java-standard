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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Test;

public abstract class DevAppServerTestBase {
  private int jettyPort;
  private Process runtimeProc;
  private CountDownLatch serverStarted;

  private static final int NUMBER_OF_RETRIES = 5;

  private static HttpClient httpClient;
  private static final int RESPONSE_200 = 200;

  static File createApp(String directoryName) {
    File currentDirectory = new File("").getAbsoluteFile();
    File appRoot =
        new File(
            currentDirectory,
            "../testlocalapps/" + directoryName + "/target/" + directoryName + "-2.0.24-SNAPSHOT");
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

  @Test
  public void useMemcache() throws Exception {
    // App Engine Memcache access.
    executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 10\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    executeHttpGet(
        "/?memcache_loops=10&memcache_size=10",
        "Running memcache for 10 loops with value size 10\n"
            + "Cache hits: 20\n"
            + "Cache misses: 0\n",
        RESPONSE_200);

    executeHttpGet(
        "/?memcache_loops=5&memcache_size=10",
        "Running memcache for 5 loops with value size 10\n"
            + "Cache hits: 25\n"
            + "Cache misses: 0\n",
        RESPONSE_200);
  }

  @Test
  public void useUserApi() throws Exception {
    // App Engine User API access.
    executeHttpGet("/?user", "Sign in with /_ah/login?continue=%2F\n", RESPONSE_200);
  }

  @Test
  public void useDatastoreAndTaskQueue() throws Exception {
    // First, populate Datastore entities
    executeHttpGet("/?datastore_entities=3", "Added 3 entities\n", RESPONSE_200);

    // App Engine Taskqueue usage, queuing the addition of 7 entities.
    executeHttpGet(
        "/?add_tasks=1&task_url=/?datastore_entities=7",
        "Adding 1 tasks for URL /?datastore_entities=7\n",
        RESPONSE_200);

    // After a while, we should have 10 or more entities.
    executeHttpGetWithRetriesContains(
        "/?datastore_count", "Found ", RESPONSE_200, NUMBER_OF_RETRIES);
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

  private void executeHttpGet(String url, String expectedResponseBody, int expectedReturnCode)
      throws Exception {
    executeHttpGetWithRetries(
        url, expectedResponseBody, expectedReturnCode, /* numberOfRetries= */ 1);
  }

  private void executeHttpGetWithRetries(
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

  private void executeHttpGetWithRetriesContains(
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

  private static void copyTree(Path fromRoot, Path toRoot) throws IOException {
    try (Stream<Path> stream = Files.walk(fromRoot)) {
      stream.forEach(
          fromPath -> {
            try {
              copyFile(fromRoot, fromPath, toRoot);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (UncheckedIOException e) {
      throw new IOException(e);
    }
  }

  private static void copyFile(Path fromRoot, Path fromPath, Path toRoot) throws IOException {
    if (!Files.isDirectory(fromPath)) {
      Path relative = fromRoot.relativize(fromPath);
      if (relative.getParent() != null) {
        Path toDir = toRoot.resolve(relative.getParent());
        Files.createDirectories(toDir);
        Path toPath = toRoot.resolve(relative);
        Files.copy(fromPath, toPath);
      }
    }
  }
}
