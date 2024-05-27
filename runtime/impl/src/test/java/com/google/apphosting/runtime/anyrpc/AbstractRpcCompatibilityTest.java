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

package com.google.apphosting.runtime.anyrpc;

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.ClonePb.CloneSettings;
import com.google.apphosting.base.protos.ClonePb.PerformanceData;
import com.google.apphosting.base.protos.Codes.Code;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo;
import com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.anyrpc.ClientInterfaces.CloneControllerClient;
import com.google.apphosting.runtime.anyrpc.ClientInterfaces.EvaluationRuntimeClient;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.reflect.Reflection;
import com.google.common.testing.TestLogHandler;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mockito;

/**
 * Round-trip tests for the AnyRpc layer. This is an abstract class that should be subclassed for
 * the particular configuration of client and server implementations that is being tested.
 */
public abstract class AbstractRpcCompatibilityTest {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // Status codes from Google-internal RpcErrorCode class:
  private static final int RPC_SERVER_ERROR = 3;
  private static final int RPC_DEADLINE_EXCEEDED = 4;
  private static final int RPC_CANCELLED = 6;

  abstract AnyRpcClientContextFactory newRpcClientContextFactory();

  abstract EvaluationRuntimeClient newEvaluationRuntimeClient();

  abstract CloneControllerClient newCloneControllerClient();

  abstract ClockHandler getClockHandler();

  ClockHandler clockHandler;
  private AnyRpcClientContextFactory rpcClientContextFactory;

  private TestLogHandler testLogHandler;

  private final List<String> asynchronousFailures =
      Collections.synchronizedList(new ArrayList<String>());

  abstract AnyRpcPlugin getClientPlugin();

  abstract AnyRpcPlugin getServerPlugin();

  abstract int getPacketSize();

  abstract static class ClockHandler {
    final Clock clock;

    ClockHandler(Clock clock) {
      this.clock = clock;
    }

    long getMillis() {
      return clock.millis();
    }

    abstract void advanceClock();

    abstract void assertStartTime(long expectedStartTime, long reportedStartTime);
  }

  @Before
  public void setUpAbstractRpcCompatibilityTest() throws IOException {
    clockHandler = getClockHandler();

    rpcClientContextFactory = newRpcClientContextFactory();

    testLogHandler = new TestLogHandler();
    Logger.getLogger("").addHandler(testLogHandler);
  }

  @After
  public void tearDown() {
    // If the subclass defines its own @After method, that will run before this one.
    // So it shouldn't shut down these plugins or doing anything else that might interfere
    // with what we do here.
    AnyRpcPlugin clientRpcPlugin = getClientPlugin();
    AnyRpcPlugin serverRpcPlugin = getServerPlugin();
    if (serverRpcPlugin != null && serverRpcPlugin.serverStarted()) {
      serverRpcPlugin.stopServer();
    }
    if (clientRpcPlugin != null) {
      clientRpcPlugin.shutdown();
    }
    if (serverRpcPlugin != null) {
      serverRpcPlugin.shutdown();
    }
    assertThat(asynchronousFailures).isEmpty();
  }

  private boolean checkLogMessages = true;
  private final List<String> expectedLogMessages = new ArrayList<>();

  void dontCheckLogMessages() {
    checkLogMessages = false;
  }

  void addExpectedLogMessage(String message) {
    expectedLogMessages.add(message);
  }

  /**
   * Log checking rule. The way {@code @Rule} works is that it is invoked for every test method and
   * can insert behaviour before and after the execution of the method. Here, we want to check that
   * there have been no unexpected log messages, but only if the test method otherwise succeeded. So
   * instead of using {@code @After}, which would risk masking test failures with the log check
   * failure, we use {@link TestWatcher} to run the check only when the test method has succeeded.
   */
  @Rule
  public TestRule logCheckerRule =
      new TestWatcher() {
        @Override
        protected void succeeded(Description description) {
          if (checkLogMessages) {
            List<String> messages = new ArrayList<>();
            for (LogRecord logRecord : testLogHandler.getStoredLogRecords()) {
              if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
                messages.add(new SimpleFormatter().formatMessage(logRecord));
              }
            }
            assertThat(messages).isEqualTo(expectedLogMessages);
          }
        }
      };

  private static class TestEvaluationRuntimeServer implements EvaluationRuntimeServerInterface {
    final AtomicInteger handleRequestCount = new AtomicInteger();
    final Semaphore addAppVersionReceived = new Semaphore(0);
    AtomicLong latestGlobalId = new AtomicLong();

    @Override
    public void handleRequest(AnyRpcServerContext ctx, UPRequest req) {
      latestGlobalId.set(ctx.getGlobalId());
      handleRequestCount.getAndIncrement();
      String appId = req.getAppId();
      // We abuse the error_message field in the response to echo the app id and also the
      // remaining time as seen by this thread and as seen by another thread.
      // The message looks like "my-app-id/5.23/5.23".
      UPResponse resp =
          UPResponse.newBuilder()
              .setError(UPResponse.ERROR.OK_VALUE)
              .setErrorMessage(
                  appId
                      + "/"
                      + ctx.getTimeRemaining().getSeconds()
                      + "/"
                      + timeRemainingInAnotherThread(ctx).getSeconds())
              .build();
      ctx.finishWithResponse(resp);
    }

    private static Duration timeRemainingInAnotherThread(final AnyRpcServerContext ctx) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Callable<Duration> getTimeRemaining = ctx::getTimeRemaining;
      try {
        return executor.submit(getTimeRemaining).get();
      } catch (InterruptedException | ExecutionException e) {
        throw new AssertionError(e);
      } finally {
        executor.shutdown();
      }
    }

    @Override
    public void addAppVersion(AnyRpcServerContext ctx, AppinfoPb.AppInfo req) {
      // This doesn't return ctx.finishWithResponse, so a caller should eventually time out.
      // We signal a semaphore so that tests can wait until the server has indeed received this
      // request. Otherwise there is a danger that the test will shut down the server before it
      // receives the request, which would generate a spurious log message.
      addAppVersionReceived.release();
    }

    @Override
    public void addAppVersion(AnyRpcServerContext ctx, AppVersion appVersion) {
      // This doesn't return ctx.finishWithResponse, so a caller should eventually time out.
      // We signal a semaphore so that tests can wait until the server has indeed received this
      // request. Otherwise there is a danger that the test will shut down the server before it
      // receives the request, which would generate a spurious log message.
      addAppVersionReceived.release();
    }

    @Override
    public void deleteAppVersion(AnyRpcServerContext ctx, AppinfoPb.AppInfo req) {
      throw new UnsupportedOperationException("deleteAppVersion");
    }

    long getLatestGlobalId() {
      return latestGlobalId.get();
    }
  }

  class TestCallback<T extends MessageLite> implements AnyRpcCallback<T> {
    private final BlockingQueue<Optional<T>> resultQueue = new ArrayBlockingQueue<>(1);

    Optional<T> result() {
      try {
        Optional<T> result = resultQueue.poll(5, SECONDS);
        if (result == null) {
          fail("Timeout waiting for RPC result");
        }
        return result;
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }

    void assertFailureOrNoResult() {
      Optional<T> result = resultQueue.poll();
      if (result != null) {
        assertThat(result).isEmpty();
      }
    }

    private void resultIs(Optional<T> result) {
      try {
        resultQueue.offer(result, 5, SECONDS);
      } catch (InterruptedException e) {
        logger.atSevere().withCause(e).log("Interrupted while sending result %s", result);
        asynchronousFailures.add("Interrupted while sending result " + result);
      }
    }

    @Override
    public void success(T response) {
      resultIs(Optional.of(response));
    }

    @Override
    public void failure() {
      resultIs(Optional.empty());
    }
  }

  @Test
  public void testRpc() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    UPRequest request = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = callback.result();
    assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();
    assertThat(result.get().getErrorMessage()).startsWith("hello/");
  }

  @Test
  public void testStartTime() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    UPRequest request = makeUPRequest("hello");
    long rpcStartTime = clockHandler.getMillis();
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    clockHandler.advanceClock();
    long reportedStartTime = clientContext.getStartTimeMillis();
    clockHandler.assertStartTime(rpcStartTime, reportedStartTime);
    callback.result();
    assertThat(clientContext.getStartTimeMillis()).isEqualTo(reportedStartTime);
  }

  @Test
  public void testRepeatedRpcs() throws Exception {
    TestEvaluationRuntimeServer runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    Set<Long> globalIds = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
      String testString = createRandomString(10);
      UPRequest request = makeUPRequest(testString);
      evaluationRuntimeClient.handleRequest(clientContext, request, callback);
      Optional<UPResponse> result = callback.result();
      assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();

      long globalId = runtimeServer.getLatestGlobalId();
      assertThat(globalIds).doesNotContain(globalId);
      globalIds.add(globalId);
    }
  }

  ImmutableList<String> expectedLogMessagesForUnimplemented() {
    return ImmutableList.of();
  }

  @Test
  public void testUnimplemented() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    CloneControllerClient cloneControllerClient = newCloneControllerClient();
    TestCallback<EmptyMessage> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    CloneSettings request = CloneSettings.getDefaultInstance();
    cloneControllerClient.applyCloneSettings(clientContext, request, callback);
    Optional<EmptyMessage> result = callback.result();
    assertWithMessage("RPC should not succeed").about(optionals()).that(result).isEmpty();
    StatusProto status = clientContext.getStatus();
    assertThat(status.getCode()).isNotEqualTo(0);
    assertThat(status.getMessage()).contains("UnsupportedOperationException: applyCloneSettings");
    StatusProto expectedStatus =
        StatusProto.newBuilder()
            .setSpace("RPC")
            .setCode(RPC_SERVER_ERROR)
            .setMessage(status.getMessage())
            .setCanonicalCode(Code.INTERNAL_VALUE)
            .build();
    assertThat(status).isEqualTo(expectedStatus);

    for (String message : expectedLogMessagesForUnimplemented()) {
      addExpectedLogMessage(message);
    }

    // Do another RPC to make sure that the exception hasn't killed the server.
    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> successCallback = new TestCallback<>();

    AnyRpcClientContext successClientContext = rpcClientContextFactory.newClientContext();
    UPRequest successRequest = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(successClientContext, successRequest, successCallback);
    Optional<UPResponse> successResult = successCallback.result();
    assertWithMessage("RPC should succeed").about(optionals()).that(successResult).isPresent();
    assertThat(successResult.get().getErrorMessage()).startsWith("hello/");
  }

  @Test
  public void testDeadline() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<EmptyMessage> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    clientContext.setDeadline(0.5);
    AppInfo request = makeAppInfo();
    evaluationRuntimeClient.addAppVersion(clientContext, request, callback);
    Optional<EmptyMessage> result = callback.result();
    assertThat(result).isEmpty();
    StatusProto status = clientContext.getStatus();
    assertThat(status.getSpace()).isEqualTo("RPC");
    assertThat(status.getCode()).isEqualTo(RPC_DEADLINE_EXCEEDED);
  }

  @Test
  public void testDeadlineRemaining() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    final BlockingQueue<Optional<UPResponse>> resultQueue = new ArrayBlockingQueue<>(1);
    AnyRpcCallback<UPResponse> callback =
        new AnyRpcCallback<UPResponse>() {
          @Override
          public void success(UPResponse response) {
            resultQueue.add(Optional.of(response));
          }

          @Override
          public void failure() {
            resultQueue.add(Optional.empty());
          }
        };

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    double fakeDeadline = 1234.0;
    clientContext.setDeadline(fakeDeadline);
    UPRequest request = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = resultQueue.take();
    assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();
    String message = result.get().getErrorMessage();
    // Now check that we got a correct deadline in the request handler.
    // See TestEvaluationRuntimeServer.handleRequest for how we construct this string.
    Pattern pattern = Pattern.compile("(.*)/(.*)/(.*)");
    assertThat(message).matches(pattern);
    Matcher matcher = pattern.matcher(message);
    assertThat(matcher.matches()).isTrue();
    assertThat(matcher.group(1)).isEqualTo("hello");
    double remainingThisThread = Double.parseDouble(matcher.group(2));
    assertThat(remainingThisThread).isLessThan(fakeDeadline);
    assertThat(remainingThisThread).isGreaterThan(fakeDeadline - 30);
    double remainingOtherThread = Double.parseDouble(matcher.group(3));
    assertThat(remainingOtherThread).isLessThan(fakeDeadline);
    assertThat(remainingOtherThread).isGreaterThan(fakeDeadline - 30);
  }

  @Test
  public void testCancelled() throws Exception {
    TestEvaluationRuntimeServer runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<EmptyMessage> callback = new TestCallback<>();

    long rpcStartTime = clockHandler.getMillis();
    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    AppInfo request = makeAppInfo();
    evaluationRuntimeClient.addAppVersion(clientContext, request, callback);

    // Wait until the server has received the request. Since it doesn't reply, if we didn't cancel
    // the request the client would time out.
    runtimeServer.addAppVersionReceived.acquire();

    clockHandler.advanceClock();
    clientContext.startCancel();
    Optional<EmptyMessage> result = callback.result();
    assertThat(result).isEmpty();
    StatusProto status = clientContext.getStatus();
    assertThat(status.getSpace()).isEqualTo("RPC");
    assertThat(status.getCode()).isEqualTo(RPC_CANCELLED);

    clockHandler.assertStartTime(rpcStartTime, clientContext.getStartTimeMillis());
  }

  @Test
  public void testCancelAlreadyCompleted() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    UPRequest request = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = callback.result();
    assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();

    // This is essentially just checking that there's no exception or deadlock.
    clientContext.startCancel();
  }

  @Test
  public void testLargeRoundTrip() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    final String requestText = createRandomString(getPacketSize());
    UPRequest request = makeUPRequest(requestText);
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = callback.result();
    assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();
    assertThat(result.get().getErrorMessage()).startsWith(requestText);
  }

  @Test
  public void testConcurrency_smallRequest() throws Exception {
    doTestConcurrency(10);
  }

  @Test
  public void testConcurrency_largeRequest() throws Exception {
    doTestConcurrency(getPacketSize());

    // TODO: enable log checking. Currently we get messages like this:
    //    User called setEventCallback() when a previous upcall was still pending!
    // http://google3/java/com/google/net/eventmanager/DescriptorImpl.java&l=312&rcl=20829669
    dontCheckLogMessages();
  }

  private void doTestConcurrency(int requestSize) throws InterruptedException {
    final int concurrentThreads = 5;

    EvaluationRuntimeServerInterface runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();

    Semaphore done = new Semaphore(0);
    CountDownLatch countDownLatch = new CountDownLatch(concurrentThreads);
    Queue<Throwable> exceptions = new LinkedBlockingQueue<>();
    @SuppressWarnings("InterruptedExceptionSwallowed")
    Runnable runClient =
        () -> runClient(requestSize, countDownLatch, evaluationRuntimeClient, done, exceptions);
    for (int i = 0; i < concurrentThreads; i++) {
      new Thread(runClient, "Client " + i).start();
    }
    boolean acquired = done.tryAcquire(concurrentThreads, 20, SECONDS);
    assertThat(exceptions).isEmpty();
    assertThat(acquired).isTrue();
  }

  @SuppressWarnings("InterruptedExceptionSwallowed")
  private void runClient(
      int requestSize,
      CountDownLatch countDownLatch,
      EvaluationRuntimeClient evaluationRuntimeClient,
      Semaphore done,
      Queue<Throwable> exceptions) {
    try {
      AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
      String text = createRandomString(requestSize);
      UPRequest request = makeUPRequest(text);
      countDownLatch.countDown();
      countDownLatch.await();
      TestCallback<UPResponse> callback = new TestCallback<>();
      evaluationRuntimeClient.handleRequest(clientContext, request, callback);
      Optional<UPResponse> result = callback.result();
      assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();
      assertThat(result.get().getErrorMessage()).startsWith(text);
      done.release();
    } catch (Throwable t) {
      exceptions.add(t);
    }
  }

  private static class AppErrorEvaluationRuntimeServer extends TestEvaluationRuntimeServer {
    @Override
    public void handleRequest(AnyRpcServerContext ctx, UPRequest req) {
      ctx.finishWithAppError(7, "oh noes!");
    }
  }

  @Test
  public void testAppError() throws Exception {
    EvaluationRuntimeServerInterface runtimeServer = new AppErrorEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    UPRequest request = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = callback.result();
    assertWithMessage("RPC should fail").about(optionals()).that(result).isEmpty();
    assertThat(clientContext.getApplicationError()).isEqualTo(7);
    assertThat(clientContext.getErrorDetail()).isEqualTo("oh noes!");
  }

  /**
   * Allows us to check the {@link AnyRpcPlugin#blockUntilShutdown()} method. This is a thread that
   * calls that method and then exits. So we can check that the method blocks (because the thread is
   * alive) and then unblocks when we stop the server (because the thread is dead).
   */
  private static class ServerWatcher extends Thread {
    private final AnyRpcPlugin rpcPlugin;

    ServerWatcher(AnyRpcPlugin rpcPlugin) {
      this.rpcPlugin = rpcPlugin;
    }

    @Override
    public void run() {
      rpcPlugin.blockUntilShutdown();
    }
  }

  @Test
  public void testStopServer() throws Exception {
    TestEvaluationRuntimeServer runtimeServer = new TestEvaluationRuntimeServer();
    CloneControllerServerInterface controllerServer =
        implementAsUnsupported(CloneControllerServerInterface.class);
    getServerPlugin().startServer(runtimeServer, controllerServer);

    ServerWatcher serverWatcher = new ServerWatcher(getServerPlugin());
    serverWatcher.start();

    assertThat(runtimeServer.handleRequestCount.get()).isEqualTo(0);

    EvaluationRuntimeClient evaluationRuntimeClient = newEvaluationRuntimeClient();
    TestCallback<UPResponse> callback = new TestCallback<>();

    AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
    UPRequest request = makeUPRequest("hello");
    evaluationRuntimeClient.handleRequest(clientContext, request, callback);
    Optional<UPResponse> result = callback.result();
    assertWithMessage("RPC should succeed").about(optionals()).that(result).isPresent();
    assertThat(result.get().getErrorMessage()).startsWith("hello/");

    assertThat(runtimeServer.handleRequestCount.get()).isEqualTo(1);
    assertThat(serverWatcher.isAlive()).isTrue();
    assertThat(getServerPlugin().serverStarted()).isTrue();

    getServerPlugin().stopServer();

    // The ServerWatcher thread should now die, so wait for it to do so.
    serverWatcher.join(1000);
    assertThat(serverWatcher.isAlive()).isFalse();
    assertThat(getServerPlugin().serverStarted()).isFalse();

    // A request to the server should not be handled there.
    AnyRpcClientContext clientContext2 = rpcClientContextFactory.newClientContext();
    TestCallback<UPResponse> callback2 = new TestCallback<>();
    evaluationRuntimeClient.handleRequest(clientContext2, request, callback2);
    // Now wait a second to make sure the server method didn't get called, and that the client
    // either got no response or got a failure.
    Thread.sleep(1000);
    assertWithMessage("Server should not handle requests")
        .that(runtimeServer.handleRequestCount.get())
        .isEqualTo(1);
    callback2.assertFailureOrNoResult();
  }

  // Round trip test for every method defined in each of the two server interfaces.
  @Test
  public void testAllServerMethods() throws Exception {
    EvaluationRuntimeServerInterface evaluationRuntimeServer =
        Mockito.mock(EvaluationRuntimeServerInterface.class);
    CloneControllerServerInterface cloneControllerServer =
        Mockito.mock(CloneControllerServerInterface.class);
    AnyRpcPlugin serverPlugin = getServerPlugin();
    serverPlugin.startServer(evaluationRuntimeServer, cloneControllerServer);
    testServerMethods(
        EvaluationRuntimeServerInterface.class,
        evaluationRuntimeServer,
        newEvaluationRuntimeClient());
    testServerMethods(
        CloneControllerServerInterface.class, cloneControllerServer, newCloneControllerClient());
  }

  // To follow what is going on here, consider the example of EvaluationRuntime. Then `server`
  // will be a mock for EvaluationRuntimeServerInterface and `client` will be a real client
  // implementing ClientInterfaces.EvaluationRuntimeClient. `serverInterface` will be
  // EvaluationRuntimeServerInterface.class. We iterate over the methods of that interface,
  // for example:
  //   void handleRequest(AnyRpcServerContext ctx, UPRequest req);
  // From the method signature, we can tell that we need a UPRequest as input, and that the
  // corresponding method in the client interface must look like this:
  //   void handleRequest(AnyRpcClientContext ctx, UPRequest req, AnyRpcCallback<SOMETHING> cb);
  // We don't need to know SOMETHING to find that method, and once we do we can use reflection
  // to find that SOMETHING is UPResponse.
  // We set up the mock to expect the server to be called with our fake UPRequest, and to call
  // ctx.finishResponse(fakeUPResponse) when it is.
  // We then invoke client.handleRequest with the UPRequest and a callback that will collect the
  // UPResponse. We check that the UPResponse is our fake one, and that the correct server method
  // was called.

  private <T> void testServerMethods(Class<T> serverInterface, T server, Object client)
      throws ReflectiveOperationException {
    for (Method serverMethod : serverInterface.getMethods()) {
      Class<? extends Message> requestType = getRequestTypeFromServerMethod(serverMethod);
      Method clientMethod =
          client
              .getClass()
              .getMethod(
                  serverMethod.getName(),
                  AnyRpcClientContext.class,
                  requestType,
                  AnyRpcCallback.class);
      Class<? extends Message> responseType = getResponseTypeFromClientMethod(clientMethod);
      Message fakeRequest = getFakeMessage(requestType);
      final Message fakeResponse = getFakeMessage(responseType);
      when(serverMethod.invoke(server, any(AnyRpcServerContext.class), eq(fakeRequest)))
          .thenAnswer(
              invocationOnMock -> {
                AnyRpcServerContext serverContext =
                    (AnyRpcServerContext) invocationOnMock.getArguments()[0];
                serverContext.finishWithResponse(fakeResponse);
                return null;
              });
      AnyRpcClientContext clientContext = rpcClientContextFactory.newClientContext();
      TestCallback<MessageLite> callback = new TestCallback<>();
      clientMethod.invoke(client, clientContext, fakeRequest, callback);
      Optional<MessageLite> result = callback.result();
      assertWithMessage(clientMethod.getName()).that(result).isEqualTo(Optional.of(fakeResponse));
      Object serverVerify = verify(server);
      serverMethod.invoke(serverVerify, any(AnyRpcServerContext.class), eq(fakeRequest));
      Mockito.verifyNoMoreInteractions(server);
    }
  }

  // Reminder: the server method looks like this:
  //   void handleRequest(AnyRpcServerContext ctx, UPRequest req);
  // This method returns UPRequest for that example.
  private static Class<? extends Message> getRequestTypeFromServerMethod(Method serverMethod) {
    Class<?>[] parameterTypes = serverMethod.getParameterTypes();
    assertThat(parameterTypes).hasLength(2);
    assertThat(parameterTypes[0]).isEqualTo(AnyRpcServerContext.class);
    assertThat(parameterTypes[1]).isAssignableTo(Message.class);
    @SuppressWarnings("unchecked")
    Class<? extends Message> requestType = (Class<? extends Message>) parameterTypes[1];
    return requestType;
  }

  // Reminder: the client method looks like this:
  //   void handleRequest(AnyRpcClientContext ctx, UPRequest req, AnyRpcCallback<UPResponse> cb);
  // This method returns UPResponse for that example.
  private static Class<? extends Message> getResponseTypeFromClientMethod(Method clientMethod) {
    Class<?>[] parameterTypes = clientMethod.getParameterTypes();
    assertThat(parameterTypes[2]).isEqualTo(AnyRpcCallback.class);
    ParameterizedType anyRpcCallbackType =
        (ParameterizedType) clientMethod.getGenericParameterTypes()[2];
    Class<?> typeArgument = (Class<?>) anyRpcCallbackType.getActualTypeArguments()[0];
    assertThat(typeArgument).isAssignableTo(Message.class);
    @SuppressWarnings("unchecked")
    Class<? extends Message> responseType = (Class<? extends Message>) typeArgument;
    return responseType;
  }

  private static <T extends Message> T getFakeMessage(Class<T> messageType) {
    T message = FAKE_MESSAGES.getInstance(messageType);
    assertWithMessage("Expected fake message for " + messageType.getName())
        .that(message)
        .isNotNull();
    assertWithMessage(messageType.getName() + " " + message.getInitializationErrorString())
        .that(message.isInitialized())
        .isTrue();
    return message;
  }

  private static final ImmutableClassToInstanceMap<Message> FAKE_MESSAGES =
      ImmutableClassToInstanceMap.<Message>builder()
          .put(EmptyMessage.class, EmptyMessage.getDefaultInstance())
          .put(UPRequest.class, makeUPRequest("blim"))
          .put(UPResponse.class, UPResponse.newBuilder().setError(23).build())
          .put(AppInfo.class, makeAppInfo())
          .put(
              CloneSettings.class,
              CloneSettings.newBuilder().setCloneKey(ByteString.copyFrom("blam", UTF_8)).build())
          .put(PerformanceData.class, makePerformanceData())
          .put(
              PerformanceDataRequest.class,
              PerformanceDataRequest.newBuilder()
                  .setType(PerformanceData.Type.PERIODIC_SAMPLE)
                  .build())
          .put(
              DeadlineInfo.class,
              DeadlineInfo.newBuilder().setSecurityTicket("tickety boo").setHard(true).build())
          .build();

  private static <T> T implementAsUnsupported(Class<T> interfaceToImplement) {
    InvocationHandler unsupportedInvocationHandler =
        (proxy, method, args) -> {
          throw new UnsupportedOperationException(method.getName());
        };
    return Reflection.newProxy(interfaceToImplement, unsupportedInvocationHandler);
  }

  private static UPRequest makeUPRequest(String appId) {
    AppinfoPb.Handler handler = AppinfoPb.Handler.newBuilder().setPath("foo").build();
    return UPRequest.newBuilder()
        .setAppId(appId)
        .setVersionId("world")
        .setNickname("foo")
        .setSecurityTicket("bar")
        .setHandler(handler)
        .build();
  }

  private static AppInfo makeAppInfo() {
    return AppInfo.newBuilder().setAppId("foo").build();
  }

  private static PerformanceData makePerformanceData() {
    return PerformanceData.newBuilder()
        .addEntries(
            PerformanceData.Entry.newBuilder().setPayload(ByteString.copyFrom("payload", UTF_8)))
        .build();
  }

  private String createRandomString(int size) {
    Random random = new Random();
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; ++i) {
      bytes[i] = (byte) (random.nextInt(127 - 32) + 32);
    }
    return new String(bytes, US_ASCII);
  }
}
