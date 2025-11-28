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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.base.protos.SpanKindOuterClass;
import com.google.apphosting.base.protos.TraceEvents.SpanEventProto;
import com.google.apphosting.base.protos.TraceEvents.SpanEventsProto;
import com.google.apphosting.base.protos.TraceEvents.StartSpanProto;
import com.google.apphosting.base.protos.TraceEvents.TraceEventsProto;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.test.MockAnyRpcServerContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the RequestManager.
 *
 */
@RunWith(JUnit4.class)
public class RequestManagerTest {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final double RPC_DEADLINE = 3.0;
  private static final long SOFT_DEADLINE_DELAY = 750;
  private static final long MAX_RUNTIME_LOG_PER_REQUEST = 3000L * 1024L;
  private static final String APP_ID = "app123";
  private static final String ENGINE_ID = "engine";
  private static final String VERSION_ID = "v456";
  private static final long CYCLES_PER_SECOND = 2333414000L;
  private static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";
  private static final String INSTANCE_ID = "abc123";

  private AppVersion appVersion;
  private UPRequest upRequest;
  private MutableUpResponse upResponse;
  private RuntimeLogSink logSink;
  @Mock private APIHostClientInterface mockApiHost;

  // Ensure that Truth is loaded. Otherwise we can get weird errors if the exceptions we are
  // flinging about with Thread.stop0 end up hitting a thread that is running the Truth static
  // initializer. Likewise for Mockito.
  @BeforeClass
  public static void initClasses() {
    assertThat(true).isTrue();
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    upRequest =
        UPRequest.newBuilder()
            .setAppId(APP_ID)
            .setModuleId(ENGINE_ID)
            .setModuleVersionId(VERSION_ID)
            .buildPartial();

    upResponse = new MutableUpResponse();
    logSink = new RuntimeLogSink(MAX_RUNTIME_LOG_PER_REQUEST);

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
  }

  private RequestManager.Builder requestManagerBuilder() {
    return RequestManager.builder()
        .setSoftDeadlineDelay(SOFT_DEADLINE_DELAY)
        .setRuntimeLogSink(Optional.of(logSink))
        .setApiProxyImpl(ApiProxyImpl.builder().setApiHost(mockApiHost).build())
        .setMaxOutstandingApiRpcs(10)
        .setCyclesPerSecond(CYCLES_PER_SECOND)
        .setWaitForDaemonRequestThreads(true)
        .setThreadStopTerminatesClone(true)
        .setInterruptFirstOnSoftDeadline(false);
  }

  private RequestManager createRequestManager() {
    return requestManagerBuilder().build();
  }

  @Test
  public void testApiEnvironment() {
    RequestManager requestManager = createRequestManager();
    assertThat(ApiProxy.getCurrentEnvironment()).isEqualTo(null);

    MockAnyRpcServerContext rpc = createRpc();
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    try {
      assertThat(ApiProxy.getCurrentEnvironment().getAppId()).isEqualTo(APP_ID);
      assertThat(ApiProxy.getCurrentEnvironment().getModuleId()).isEqualTo(ENGINE_ID);
      assertThat(ApiProxy.getCurrentEnvironment().getVersionId()).isEqualTo(VERSION_ID);
      assertThat(ApiProxy.getCurrentEnvironment().getAttributes().get(INSTANCE_ID_ENV_ATTRIBUTE))
          .isNull();
    } finally {
      requestManager.finishRequest(token);
    }
    assertThat(ApiProxy.getCurrentEnvironment()).isEqualTo(null);
  }

  @Test
  public void testApiEnvironmentWithInstanceIdFromEnvironmentVariables() {
    RequestManager requestManager =
        requestManagerBuilder()
            .setEnvironment(ImmutableMap.of("GAE_INSTANCE", INSTANCE_ID))
            .build();
    assertThat(ApiProxy.getCurrentEnvironment()).isEqualTo(null);

    MockAnyRpcServerContext rpc = createRpc();
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    try {
      assertThat(ApiProxy.getCurrentEnvironment().getAttributes().get(INSTANCE_ID_ENV_ATTRIBUTE))
          .isEqualTo(INSTANCE_ID);
    } finally {
      requestManager.finishRequest(token);
    }
    assertThat(ApiProxy.getCurrentEnvironment()).isEqualTo(null);
  }

  // Outcome of the request thread in the next test (testSoftExceptionWithInterruption).
  enum TestOutcome {
    NONE("Unexpected outcome"),
    OK("OK"),
    THREAD_NOT_INTERRUPTED("Thread slept past the allotted deadline"),
    ASYNC_FUTURE_NOT_CANCELLED("Async future was not cancelled"),
    DEADLINE_THROWN("Thread was not interrupted, instead got a DeadlineExceededException");

    TestOutcome(String message) {
      this.message = message;
    }

    String getMessage() {
      return message;
    }

    private final String message;
  }

  @Test
  public void testTraceDisabled() {
    RequestManager requestManager = createRequestManager();
    MockAnyRpcServerContext rpc = createRpc();
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    requestManager.finishRequest(token);
    assertThat(upResponse.hasSerializedTrace()).isFalse();
  }

  @Test
  public void testTraceEnabled() throws InvalidProtocolBufferException {
    RequestManager requestManager = createRequestManager();
    // Enable trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(1)
            .build();
    UPRequest.Builder upRequestBuilder = upRequest.toBuilder().setTraceContext(context);
    upRequestBuilder.getRequestBuilder().setUrl("http://foo.com/request?a=1");
    upRequest = upRequestBuilder.buildPartial();

    MockAnyRpcServerContext rpc = createRpc();
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    // Construct failed response.
    upResponse.setError(UPResponse.ERROR.LOG_FATAL_DEATH_VALUE);
    upResponse.setErrorMessage("Error message");
    requestManager.finishRequest(token);

    TraceEventsProto traceEvents = TraceEventsProto.parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(1);

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_SERVER);
    assertThat(startSpan.getName()).isEqualTo("/request");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(1L);
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
  }

  @Test
  public void testTraceEnabledBadURL() throws InvalidProtocolBufferException {
    RequestManager requestManager = createRequestManager();
    // Enable trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(1)
            .build();
    UPRequest.Builder upRequestBuilder = upRequest.toBuilder().setTraceContext(context);
    upRequestBuilder.getRequestBuilder().setUrl("foo.com/request?a=1");
    upRequest = upRequestBuilder.buildPartial();

    MockAnyRpcServerContext rpc = createRpc();
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    requestManager.finishRequest(token);

    TraceEventsProto traceEvents = TraceEventsProto.parseFrom(upResponse.getSerializedTrace());
    StartSpanProto startSpan = traceEvents.getSpanEvents(0).getEvent(0).getStartSpan();
    assertThat(startSpan.getName()).isEqualTo("Unparsable URL");
  }

  @Test
  public void testRuntimeLogging() {
    RequestManager requestManager = createRequestManager();
    MockAnyRpcServerContext rpc = createRpc();
    String prefix = "com.google.apphosting.runtime.RequestManagerTest testRuntimeLogging: ";
    Deque<String> messages =
        new ArrayDeque<>(
            ImmutableList.of(
                "Before startRequest.\n",
                "INFO During request.\n",
                "WARNING During request.\n",
                "ERROR During request.\n",
                "After finishRequest.\n"));
    Deque<Integer> levels =
        new ArrayDeque<>(
            ImmutableList.of(
                0, // INFO
                0, // INFO
                1, // WARNING
                2, // ERROR
                0 // INFO
                ));

    logger.atInfo().log("Before startRequest.");
    RequestManager.RequestToken token =
        requestManager.startRequest(
            appVersion, rpc, upRequest, upResponse, new ThreadGroup("test"));
    logger.atInfo().log("INFO During request.");
    logger.atWarning().log("WARNING During request.");
    logger.atSevere().log("ERROR During request.");
    requestManager.finishRequest(token);
    logger.atInfo().log("After finishRequest.");

    for (int i = 0; i < upResponse.getRuntimeLogLineCount(); i++) {
      String message = upResponse.getRuntimeLogLine(i).getMessage();
      if (message.startsWith(prefix)) {
        assertThat(message.substring(prefix.length())).isEqualTo(messages.removeFirst());
        assertThat(upResponse.getRuntimeLogLine(i).getSeverity())
            .isEqualTo(((int) levels.removeFirst()));
      }
    }
  }

  private MockAnyRpcServerContext createRpc() {
    return new MockAnyRpcServerContext(Duration.ofNanos(Math.round(RPC_DEADLINE * 1e9)));
  }
}
