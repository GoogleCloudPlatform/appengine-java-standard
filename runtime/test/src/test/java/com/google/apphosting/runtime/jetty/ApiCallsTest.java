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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Expect;
import com.google.appengine.repackaged.com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests of the behaviour of API calls in the runtime. */
@RunWith(Parameterized.class)
public class ApiCallsTest extends JavaRuntimeViaHttpBase {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public TestName testName = new TestName();
  @Rule public Expect expect = Expect.create();

  private Path appPath;

  @Before
  public void copyAppToTemp() throws IOException {
    appPath = temporaryFolder.newFolder("app").toPath();
    copyAppToDir("apicallsapp", appPath);
  }

  public enum HttpApi {
    JETTY,
    JDK
  }

  @Parameters(name = "{0}")
  public static HttpApi[] parameters() {
    return HttpApi.values();
  }

  private final HttpApi httpApi;

  public ApiCallsTest(HttpApi httpApi) {
    this.httpApi = httpApi;
  }

  private static final int CONCURRENT_REQUESTS = 200;

  /**
   * Tests that we can make N API calls concurrently, where N is the value specified by the {@code
   * --clone_max_outstanding_api_rpcs} flag. If the runtime is imposing some limit other than N,
   * whether lower or higher, the test will fail.
   */
  @Test
  public void canMakeSpecifiedNumberOfConcurrentApiCalls() throws Exception {
    // We're going to make a request to a servlet that will make N+1 API calls concurrently.
    // So if CONCURRENT_REQUESTS is 200, it will make 201 calls. We start that request off, but
    // first we "lock" the API server so that each API call will block when it arrives. Then we
    // wait for the number of requests blocked in the API server to equal CONCURRENT_REQUESTS. If
    // the runtime is imposing a lower limit on concurrent API calls, we'll never reach that many
    // requests. When we reach CONCURRENT_REQUESTS, we wait a little longer to make sure that no
    // further requests arrive, meaning that the runtime is limiting concurrent requests to exactly
    // the stated value. Then we "unlock" the API server, which will allow all the blocked
    // requests to complete, as well as the extra one. Finally we wait for the request to the
    // servlet to complete, which includes a check that the servlet reported "OK". That means that
    // all CONCURRENT_REQUESTS + 1 API calls succeeded.
    ExecutorService getThread =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread t = Executors.defaultThreadFactory().newThread(runnable);
              t.setName("Single Thread");
              return t;
            });
    try (RuntimeContext<ApiServer> context = startApp()) {
      ApiServer apiServer = context.getApiServer();
      apiServer.lock();
      Future<Void> getDone =
          getThread.submit(
              () -> {
                context.executeHttpGet("/?count=" + (CONCURRENT_REQUESTS + 1), "OK", HTTP_OK);
                return null;
              });
      long deadline = System.currentTimeMillis() + 10_000;
      while (System.currentTimeMillis() < deadline
          && apiServer.currentRequestCount() < CONCURRENT_REQUESTS) {
        Thread.sleep(10);
      }
      assertThat(apiServer.currentRequestCount()).isEqualTo(CONCURRENT_REQUESTS);
      Thread.sleep(500);
      assertThat(apiServer.currentRequestCount()).isEqualTo(CONCURRENT_REQUESTS);
      apiServer.unlock();
      getDone.get();
    }
  }

  /**
   * Tests that we can make many requests in parallel to the runtime, where each request makes
   * several API calls in parallel. The idea is to uncover concurrency bugs that might be lurking in
   * the HTTP client used for API calls.
   */
  @Test
  public void stressTest() throws Exception {
    // We'll make totalAppRequests calls to the app in all, and each one will make
    // apiCallsPerRequest API calls. To avoid overwhelming the runtime process, we limit the number
    // of app calls at one time to concurrentAppRequests (the real runtime has a similar limit
    // imposed by its environment).
    int totalAppRequests = 200;
    int apiCallsPerRequest = 10;
    int concurrentAppRequests = 20;
    ExecutorService executor = Executors.newFixedThreadPool(concurrentAppRequests);
    CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
    try (RuntimeContext<ApiServer> context = startApp()) {
      for (int i = 0; i < totalAppRequests; i++) {
        completionService.submit(() -> makeApiCalls(context, apiCallsPerRequest));
      }
      for (int i = 0; i < totalAppRequests; i++) {
        Future<String> result = completionService.poll(5, SECONDS);
        assertWithMessage("Expected result before timeout").that(result).isNotNull();
        expect.that(result.get()).startsWith("OK");
      }
      assertThat(context.getApiServer().totalRequestCount())
          .isEqualTo(totalAppRequests * apiCallsPerRequest);
    }
    executor.shutdown();
  }

  /**
   * Tests that when we get {@code ApiProxy.FeatureNotEnabledException}, it has a message that tells
   * us which specific API call was rejected, and that has a stack trace that includes the line in
   * the servlet that made the API call.
   */
  @Test
  public void featureNotEnabledExceptionMessage() throws Exception {
    ApiServerFactory<ErrorApiServer> apiServerFactory =
        (apiPort, runtimePort) ->
            ErrorApiServer.create(apiPort, RemoteApiPb.RpcError.ErrorCode.FEATURE_DISABLED);
    try (RuntimeContext<ErrorApiServer> context = startApp(apiServerFactory)) {
      // The servlet should get a FeatureNotEnabledException, which it should translate into an
      // exception stack trace that we retrieve here. The API call is testpackage.testmethod, which
      // we expect to see in the stack trace, probably like this:
      //   Caused by: com.google.apphosting.api.ApiProxy$FeatureNotEnabledException: testpackage.testmethod
      // We also expect that somewhere in the stack trace we'll see something like this:
      //   at com.google.apphosting.runtime.jetty.apicallsapp.ApiCallsServlet.handle(ApiCallsServlet.java:75)
      // The servlet does a synchronous API call so users should be able to see where that call was.
      String result = context.executeHttpGet("/?count=1", HTTP_OK);
      assertThat(result).contains("testpackage.testmethod");
      assertThat(result).containsMatch("ApiCallsServlet\\.java:\\d+");
    }
  }

  private static String makeApiCalls(RuntimeContext<?> context, int apiCallsPerRequest)
      throws Exception {
    return context.executeHttpGet("/?count=" + apiCallsPerRequest, HTTP_OK);
  }

  private RuntimeContext<ApiServer> startApp() throws IOException, InterruptedException {
    ApiServerFactory<ApiServer> apiServerFactory =
        (apiPort, runtimePort) -> ApiServer.create(apiPort);
    return startApp(apiServerFactory);
  }

  private <ApiServerT extends Closeable> RuntimeContext<ApiServerT> startApp(
      ApiServerFactory<ApiServerT> apiServerFactory) throws IOException, InterruptedException {
    RuntimeContext.Config.Builder<ApiServerT> config =
        RuntimeContext.Config.builder(apiServerFactory);
    config.setApplicationPath(appPath.toString());
    config.launcherFlagsBuilder().add("--clone_max_outstanding_api_rpcs=" + CONCURRENT_REQUESTS);
    if (httpApi == HttpApi.JDK) {
      config.setEnvironmentEntries(ImmutableMap.of("APPENGINE_API_CALLS_USING_JDK_CLIENT", "true"));
    }
    return RuntimeContext.create(config.build());
  }

  /**
   * A trivial API server that accepts any API call, regardless of its package, method, or payload,
   * and returns an empty payload. The API server can be locked, so incoming requests are blocked
   * until the server is unlocked.
   */
  private static class ApiServer extends DummyApiServer {
    static ApiServer create(int apiPort) throws IOException {
      InetSocketAddress address = new InetSocketAddress(apiPort);
      HttpServer httpServer = HttpServer.create(address, 0);
      ApiServer apiServer = new ApiServer(httpServer);
      httpServer.createContext("/", apiServer::handle);
      httpServer.setExecutor(Executors.newCachedThreadPool());
      httpServer.start();
      return apiServer;
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger totalRequestCount = new AtomicInteger();

    private ApiServer(HttpServer httpServer) {
      super(httpServer, method -> (bytes -> ByteString.EMPTY));
    }

    /**
     * Locks the handler so that any other arriving request will block until {@link #unlock} is
     * called. Every arriving request calls this method, and then {@link #unlock} when it completes.
     * The method can also be called from outside the server, in order to block all arriving
     * requests.
     */
    void lock() {
      lock.lock();
    }

    void unlock() {
      lock.unlock();
    }

    /**
     * Returns the number of concurrent requests that are waiting for the lock. We don't require
     * {@link ReentrantLock#getQueueLength()} to report the exact number of blocked threads at every
     * moment in time, but we do require it to report that number at some point after new threads
     * have stopped arriving.
     */
    int currentRequestCount() {
      return lock.getQueueLength();
    }

    int totalRequestCount() {
      return totalRequestCount.get();
    }

    @Override
    void handle(HttpExchange exchange) throws IOException {
      totalRequestCount.incrementAndGet();
      lock();
      try {
        super.handle(exchange);
      } finally {
        unlock();
      }
    }
  }

  /** A trivial API server that rejects any API call with the given RPC error. */
  private static class ErrorApiServer extends DummyApiServer {
    static ErrorApiServer create(int apiPort, RemoteApiPb.RpcError.ErrorCode error)
        throws IOException {
      InetSocketAddress address = new InetSocketAddress(apiPort);
      HttpServer httpServer = HttpServer.create(address, 0);
      ErrorApiServer apiServer = new ErrorApiServer(httpServer, error);
      httpServer.createContext("/", apiServer::handle);
      httpServer.setExecutor(Executors.newCachedThreadPool());
      httpServer.start();
      return apiServer;
    }

    private final RemoteApiPb.RpcError.ErrorCode error;

    private ErrorApiServer(HttpServer httpServer, RemoteApiPb.RpcError.ErrorCode error) {
      super(httpServer, method -> (bytes -> ByteString.EMPTY));
      this.error = error;
    }

    @Override
    RemoteApiPb.Response.Builder newResponseBuilder() {
      return RemoteApiPb.Response.newBuilder()
          .setRpcError(RemoteApiPb.RpcError.newBuilder().setCode(error.getNumber()));
    }
  }
}
