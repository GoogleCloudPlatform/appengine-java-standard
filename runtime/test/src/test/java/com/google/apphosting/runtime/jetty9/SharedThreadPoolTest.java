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

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assume.assumeTrue;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is an attempt to reproduce the customer environment that led to a problem when we enabled
 * thread-pool shutdown by default. There is some background in <a href="http://b/123425165">this
 * bug</a>. The situation is that we have a special code for dealing with the situation where the
 * app creates a thread pool using {@code ThreadManager.currentRequestThreadFactory()} and does not
 * shut that thread pool down before the request terminates. If a thread pool has ever been used, it
 * will have at least one idle thread until it is shut down. When the servlet code for a request
 * completes, we wait for all request threads to complete too, and these idle threads prevent that
 * from happening. So we introduced a hack that detects this situation and shuts the thread pool
 * down. The hack is enabled by setting the system property {@code
 * com.google.appengine.force.thread.pool.shutdown} to {@code "true"}. We thought we could
 * reasonably make that the default and have customers set it to {@code "false"} if they didn't want
 * it, but the experiences reported on the bug above and its duplicate convinced us otherwise.
 *
 * <p>As we understand it, the application in the bug used a {@code currentRequestThreadFactory}
 * thread pool that was shared between a set of requests that run at about the same time. The first
 * of these requests makes the thread pool and returns from the servlet code. Then the request
 * blocks because of the wait for request threads above. It may be that it is never shut down but
 * the client doesn't care, or it may be that one of the threads in the thread pool shuts it down.
 * Anyway, some later requests arrive and submit their own tasks to this thread pool. If the
 * auto-shutdown logic is enabled, those later requests will get {@code RejectedExecutionException}
 * when they try to submit tasks.
 *
 * <p>In this test, we have a simple servlet that submits an empty task to a shared thread pool.
 * The first request to this servlet will create the thread pool and every later request will
 * reuse it. By default, we expect that the first request will block until it times out, because
 * it will be waiting for the idle thread to complete which will never happen. The second
 * request should successfully submit a new task to the queue and return, since there are no threads
 * in the pool that belong to it (they all belong to the first thread).
 *
 * <p>We also check that if the system property is set, the first thread will return without
 * timing out.
 *
 */
@RunWith(JUnit4.class)
public class SharedThreadPoolTest extends JavaRuntimeViaHttpBase {
  private static File appRoot;

  private boolean isBeforeJava20() {
    int currentVersion = JAVA_SPECIFICATION_VERSION.value().charAt(0);
    return (currentVersion < 2);
  }

  @BeforeClass
  public static void beforeClass() throws IOException, InterruptedException {
    Path appPath = temporaryFolder.newFolder("app").toPath();
    copyAppToDir("sharedthreadpoolapp", appPath);
    appRoot = appPath.toFile();
  }

  @Test
  public void sharedThreadPoolWorks() throws Exception {
    assumeTrue(isBeforeJava20());
    ExecutorService executor = Executors.newCachedThreadPool();
    try (RuntimeContext<?> runtime = startApp()) {
      Future<?> future1 = executor.submit(() -> makeRequest(runtime, "/"));
      Thread.sleep(3000);
      Future<?> future2 = executor.submit(() -> makeRequest(runtime, "/"));
      future2.get(10, SECONDS);
      // Does not work anymore with JDK20:
      assertThat(future1.isDone()).isFalse();
    }
  }

  @Test
  public void threadPoolShutDownWithProperty() throws Exception {
    ExecutorService executor = Executors.newCachedThreadPool();
    try (RuntimeContext<?> runtime = startApp()) {
      Future<?> future1 = executor.submit(() -> makeRequest(runtime, "/?setShutdownProperty=true"));
      future1.get(2, SECONDS);
    }
  }

  void makeRequest(RuntimeContext<?> runtime, String urlPath) {
    try {
      String url = runtime.jettyUrl(urlPath);
      // We use the JDK's HttpURLConnection because the methods in JavaRuntimeViaHttpBase use
      // a shared Apache HttpClient and that would mean that a second request would be blocked
      // waiting for the first one to complete.
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      String body = new String(ByteStreams.toByteArray(connection.getInputStream()), UTF_8);
      assertWithMessage(body)
          .that(connection.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private RuntimeContext<?> startApp() throws IOException, InterruptedException {
    return RuntimeContext.create(RuntimeContext.Config.builder()
        .setApplicationPath(appRoot.getAbsolutePath())
        .build());
  }
}
