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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.HttpPb.HttpRequest;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.runtime.jetty.JettyServletEngineAdapter;
import com.google.apphosting.runtime.test.MockAnyRpcServerContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RequestRunnerTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private static final Duration RPC_DEADLINE = Duration.ofSeconds(3);
  private static final String APP_ID = "app123";
  private static final String ENGINE_ID = "engine";
  private static final String VERSION_ID = "v456";
  private static final String BACKGROUND_REQUEST_ID = "asdf";

  private AppVersion appVersion;
  private MutableUpResponse upResponse;
  BackgroundRequestCoordinator coordinator;
  private BlockingQueue<Throwable> caughtException;
  private ThreadGroupPool threadGroupPool;
  @Mock private RequestManager requestManager;
  @Mock private RequestManager.RequestToken requestToken;

  MockAnyRpcServerContext createRpc() {
    return new MockAnyRpcServerContext(RPC_DEADLINE);
  }

  @Before
  public void setUp() throws IOException {
    upResponse = new MutableUpResponse();

    File rootDirectory = Files.createTempDirectory("appengine").toFile();
    ApplicationEnvironment appEnv =
        new ApplicationEnvironment(
            APP_ID,
            VERSION_ID,
            ImmutableMap.of(),
            ImmutableMap.of(),
            rootDirectory,
            ApplicationEnvironment.RuntimeConfiguration.DEFAULT_FOR_TEST);
    appVersion =
        AppVersion.builder()
            .setAppVersionKey(AppVersionKey.of(APP_ID, VERSION_ID))
            .setAppInfo(AppInfo.getDefaultInstance())
            .setRootDirectory(rootDirectory)
            .setEnvironment(appEnv)
            .setSessionsConfig(new SessionsConfig(false, false, null))
            .setPublicRoot("")
            .build();

    coordinator = new BackgroundRequestCoordinator();

    ThreadGroup root = new ThreadGroup("root");
    caughtException = new ArrayBlockingQueue<>(100);
    UncaughtExceptionHandler uncaughtExceptionHandler =
        (thread, throwable) -> {
          throwable.printStackTrace();
          caughtException.offer(throwable);
        };
    threadGroupPool =
        ThreadGroupPool.builder()
            .setParentThreadGroup(root)
            .setThreadGroupNamePrefix("subgroup-")
            .setUncaughtExceptionHandler(uncaughtExceptionHandler)
            .setIgnoreDaemonThreads(false)
            .build();
  }

  /** Verify the RequestRunner processes an ordinary servlet request. */
  @Test
  public void run_dispatchesServletRequest() throws InterruptedException {
    MockAnyRpcServerContext rpc = createRpc();

    when(requestManager.startRequest(any(), any(), any(), any(), any())).thenReturn(requestToken);

    ServletEngineAdapter servletEngine =
        new JettyServletEngineAdapter() {
          @Override
          public void serviceRequest(UPRequest upRequest, MutableUpResponse upResponse) {
            upResponse.setError(UPResponse.ERROR.OK_VALUE);
          }
        };

    UPRequest upRequest =
        UPRequest.newBuilder()
            .setAppId(APP_ID)
            .setModuleId(ENGINE_ID)
            .setModuleVersionId(VERSION_ID)
            .buildPartial();

    RequestRunner requestRunner =
        RequestRunner.builder()
            .setAppVersion(appVersion)
            .setRpc(rpc)
            .setUpRequest(upRequest)
            .setUpResponse(upResponse)
            .setRequestManager(requestManager)
            .setCoordinator(coordinator)
            .setCompressResponse(true)
            .setUpRequestHandler(servletEngine)
            .build();

    threadGroupPool.start("test-thread", requestRunner);

    rpc.waitForCompletion();

    UPResponse response = (UPResponse) rpc.assertSuccess();
    assertThat(response.getError()).isEqualTo(UPResponse.ERROR.OK.getNumber());

    verify(requestManager, times(1))
        .startRequest(same(appVersion), same(rpc), same(upRequest), same(upResponse), any());
    verify(requestManager, times(1)).finishRequest(same(requestToken));

    // The above request should create and leave a thread in the thread pool:
    Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));
    assertThat(threadGroupPool.waitingThreadCount()).isEqualTo(1);
  }

  /** Verify the RequestRunner processes a servlet request which throws an exception. */
  @Test
  public void run_handlesDispatchServletRequestException() throws InterruptedException {
    MockAnyRpcServerContext rpc = createRpc();

    when(requestManager.startRequest(any(), any(), any(), any(), any())).thenReturn(requestToken);

    ServletEngineAdapter servletEngine =
        new JettyServletEngineAdapter() {
          @Override
          public void serviceRequest(UPRequest upRequest, MutableUpResponse upResponse) {
            throw new OutOfMemoryError("this is a simulated OOM in the servletEngine");
          }
        };

    UPRequest upRequest =
        UPRequest.newBuilder()
            .setAppId(APP_ID)
            .setModuleId(ENGINE_ID)
            .setModuleVersionId(VERSION_ID)
            .buildPartial();

    RequestRunner requestRunner =
        RequestRunner.builder()
            .setAppVersion(appVersion)
            .setRpc(rpc)
            .setUpRequest(upRequest)
            .setUpResponse(upResponse)
            .setRequestManager(requestManager)
            .setCoordinator(coordinator)
            .setCompressResponse(true)
            .setUpRequestHandler(servletEngine)
            .build();

    threadGroupPool.start("test-thread", requestRunner);

    rpc.waitForCompletion();

    UPResponse response = (UPResponse) rpc.assertSuccess();
    assertThat(response.getError()).isEqualTo(UPResponse.ERROR.APP_FAILURE.getNumber());
    assertThat(response.getErrorMessage())
        .isEqualTo(
            "Unexpected exception from servlet: java.lang.OutOfMemoryError: this is a simulated OOM"
                + " in the servletEngine");
    assertThat(response.getTerminateClone()).isTrue();

    verify(requestManager, times(1))
        .startRequest(same(appVersion), same(rpc), same(upRequest), same(upResponse), any());
    verify(requestManager, times(1)).finishRequest(same(requestToken));

    // The above request should create and leave a thread in the thread pool:
    Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));
    assertThat(threadGroupPool.waitingThreadCount()).isEqualTo(1);
  }

  /** Verify the RequestRunner processes a request to connect a background thread. */
  @Test
  public void run_backgroundRequest()
      throws InterruptedException, TimeoutException, ExecutionException {
    when(requestManager.startRequest(any(), any(), any(), any(), any())).thenReturn(requestToken);

    ExecutorService executor = Executors.newCachedThreadPool();

    // There are three parallel activities involved in the creation of an App Engine background
    // thread:
    // 1) The initial app thread which, in the course of handling a request, asks for a new
    //    background thread from the AppServer,
    // 2) The server receiving a callback from the AppServer to start a background thread, and
    // 3) The app's resulting background thread.

    // Simulates #1 and #3, requesting a background thread and running some code in it:
    Future<Boolean> app =
        executor.submit(
            () -> {
              // Act as if the app tried to run this empty runnable as a background thread, and
              // ApiProxyImpl initiated a background thread request and got back the id we're
              // looking for:
              ArrayList<String> threadReadyToStart = new ArrayList<>();
              ArrayList<String> threadFinished = new ArrayList<>();
              Thread bgThread =
                  coordinator.waitForThreadStart(
                      BACKGROUND_REQUEST_ID,
                      () -> {
                        assertThat(threadReadyToStart).containsExactly("ready to start");

                        // Since we don't set a classloader in the AppVersion, it should be null:
                        assertThat(Thread.currentThread().getContextClassLoader()).isNull();

                        // Sleep a bit to encourage race conditions:
                        try {
                          Thread.sleep(Duration.ofSeconds(1).toMillis());
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          return;
                        }
                        threadFinished.add("thread complete");
                      },
                      RPC_DEADLINE.toMillis());

              // Sleep a bit to encourage race conditions:
              try {
                Thread.sleep(Duration.ofSeconds(1).toMillis());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
              }
              threadReadyToStart.add("ready to start");
              bgThread.start();

              bgThread.join();
              assertThat(threadFinished).containsExactly("thread complete");

              return true;
            });

    // Simulates #2, the app's servlet engine, waiting on callbacks from the AppServer to kick off
    // any requested background threads, and kicking off the app's background thread:
    Future<Boolean> server =
        executor.submit(
            () -> {
              MockAnyRpcServerContext rpc = createRpc();

              UPRequest.Builder upRequestBuilder =
                  UPRequest.newBuilder()
                      .setAppId(APP_ID)
                      .setModuleId(ENGINE_ID)
                      .setModuleVersionId(VERSION_ID)
                      .setRequestType(UPRequest.RequestType.BACKGROUND);

              HttpRequest.Builder httpRequest = upRequestBuilder.getRequestBuilder();
              httpRequest.addHeaders(
                  ParsedHttpHeader.newBuilder()
                      .setKey("X-AppEngine-BackgroundRequest")
                      .setValue(BACKGROUND_REQUEST_ID));

              UPRequest upRequest = upRequestBuilder.buildPartial();

              ServletEngineAdapter servletEngine = new JettyServletEngineAdapter();

              RequestRunner requestRunner =
                  RequestRunner.builder()
                      .setAppVersion(appVersion)
                      .setRpc(rpc)
                      .setUpRequest(upRequest)
                      .setUpResponse(upResponse)
                      .setRequestManager(requestManager)
                      .setCoordinator(coordinator)
                      .setCompressResponse(true)
                      .setUpRequestHandler(servletEngine)
                      .build();

              threadGroupPool.start("test-thread", requestRunner);
              rpc.waitForCompletion();
              UPResponse response = (UPResponse) rpc.assertSuccess();
              assertThat(response.getError()).isEqualTo(UPResponse.ERROR.OK.getNumber());

              assertThat(response.getHttpResponse().getResponsecode()).isEqualTo(200);
              assertThat(response.getHttpResponse().getResponse())
                  .isEqualTo(ByteString.copyFromUtf8("OK"));

              verify(requestManager, times(1))
                  .startRequest(
                      same(appVersion), same(rpc), same(upRequest), same(upResponse), any());
              verify(requestManager, times(1)).finishRequest(same(requestToken));

              return true;
            });

    server.get();
    app.get();

    // Make sure the background thread doesn't get returned to the thread group
    // (the RequestRunner interrupts the thread at the end to prevent this):
    assertThat(threadGroupPool.waitingThreadCount()).isEqualTo(0);
  }

  /** Verify the setFailure doesn't overwrite an existing error message. */
  @Test
  public void setFailure_doesntOverwriteError() {
    RequestRunner.setFailure(upResponse, UPResponse.ERROR.APP_FAILURE, "This is a test error");
    RequestRunner.setFailure(
        upResponse, UPResponse.ERROR.UNKNOWN_APP, "This should not overwritten");
    assertThat(upResponse.getError()).isEqualTo(UPResponse.ERROR.APP_FAILURE.getNumber());
  }
}
