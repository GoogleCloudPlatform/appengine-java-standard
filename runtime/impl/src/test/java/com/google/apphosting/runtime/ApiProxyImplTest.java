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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.memcache.MemcacheServicePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.UnknownException;
import com.google.apphosting.api.ApiStats;
import com.google.apphosting.api.CloudTrace;
import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.Codes.Code;
import com.google.apphosting.base.protos.HttpPb.HttpRequest;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb.APIRequest;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.SpanKindOuterClass;
import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.base.protos.TraceEvents.SpanEventProto;
import com.google.apphosting.base.protos.TraceEvents.SpanEventsProto;
import com.google.apphosting.base.protos.TraceEvents.StartSpanProto;
import com.google.apphosting.base.protos.TraceEvents.TraceEventsProto;
import com.google.apphosting.base.protos.TraceId;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.apphosting.base.protos.api.ApiBasePb.DoubleProto;
import com.google.apphosting.base.protos.api.ApiBasePb.Integer32Proto;
import com.google.apphosting.base.protos.api.ApiBasePb.StringProto;
import com.google.apphosting.base.protos.api.ApiBasePb.VoidProto;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.apphosting.runtime.anyrpc.AnyRpcClientContext;
import com.google.apphosting.runtime.timer.CpuRatioTimer;
import com.google.apphosting.runtime.timer.Timer;
import com.google.apphosting.utils.runtime.ApiProxyUtils;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the ApiProxyImpl implementation.
 *
 */
@RunWith(JUnit4.class)
public class ApiProxyImplTest {

  private static final double DEFAULT_API_DEADLINE = 5.0;
  private static final double DEFAULT_OFFLINE_API_DEADLINE = 7.0;
  private static final double MAX_API_DEADLINE = 10.0;
  private static final String APP_ID = "app123";
  private static final String ENGINE_ID = "non-default";
  private static final String ENGINE_VERSION_ID = "v456";
  private static final String VERSION_ID = "non-default:v456.123";
  private static final String REQUEST_ID = "test-request-id";
  private static final long DEFAULT_API_MCYCLES_PER_REQUEST = 1;
  // Keep this in sync with other occurrences.
  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";
  // This matches byteCountBeforeFlushing in JavaRuntimeParams.java.
  private static final long BYTE_COUNT_BEFORE_FLUSHING = 100 * 1024L;
  // This matches maxAppLogLineSize in JavaRuntimeParams.java.
  private static final int MAX_LOG_LINE_SIZE = 16 * 1024;

  private Semaphore sleepSemaphore;

  private AppVersion appVersion;
  private ApiProxyImpl delegate;
  private ApiProxyImpl.EnvironmentImpl environment;
  private UPRequest upRequest;
  private MutableUpResponse upResponse;
  private long cpuCycles;
  private CpuRatioTimer mockTimer;
  private long elapsedWallclockNanoseconds;
  private ApiDeadlineOracle oracle;
  private final SessionsConfig sessionsConfig = new SessionsConfig(false, false, null);
  private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<Future<?>>());
  private int maxConcurrentApiCalls;
  private File rootDirectory;
  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    rootDirectory = temporaryFolder.newFolder("appengine" + System.nanoTime());
    maxConcurrentApiCalls = 10;
    oracle =
        new ApiDeadlineOracle.Builder()
            .initDeadlineMap(
                DEFAULT_API_DEADLINE, "",
                MAX_API_DEADLINE, "")
            .initOfflineDeadlineMap(
                DEFAULT_OFFLINE_API_DEADLINE, "",
                MAX_API_DEADLINE, "")
            .build();
    sleepSemaphore = new Semaphore(0);
    APIHostClientInterface apiHost = createAPIHost();
    delegate =
        ApiProxyImpl.builder()
            .setApiHost(apiHost)
            .setDeadlineOracle(oracle)
            .setByteCountBeforeFlushing(BYTE_COUNT_BEFORE_FLUSHING)
            .setMaxLogLineSize(MAX_LOG_LINE_SIZE)
            .build();
    upRequest =
        UPRequest.newBuilder()
            .setAppId(APP_ID)
            .setModuleId(ENGINE_ID)
            .setModuleVersionId(ENGINE_VERSION_ID)
            .setVersionId(VERSION_ID)
            .setNickname("foo")
            .setEmail("foo@foo.com")
            .setAuthDomain("foo.com")
            .setObfuscatedGaiaId("xxx")
            .setPeerUsername("foo_peer")
            .setSecurityLevel("foo_level")
            .setEventIdHash("0000002A")
            .setRequestLogId("0000003B")
            .setGaiaId(12345)
            .setAuthuser("1")
            .setGaiaSession("SESSION")
            .setAppserverDatacenter("yq")
            .setAppserverTaskBns("/bns/yq/appserver/321")
            .buildPartial();
    upResponse = new MutableUpResponse();

    // A mock timer for cpu time. Passes in null for all inner objects, thus causing
    // NullPointerException for pretty much anything except the three methods that are
    // expected to be called (startCpuTiming, stopCpuTiming, getCycleCount)
    elapsedWallclockNanoseconds = 0;
    mockTimer =
        new CpuRatioTimer(null, null, null, null) {
          @Override
          public long getCycleCount() {
            return cpuCycles;
          }

          @Override
          public Timer getWallclockTimer() {
            return new Timer() {
              @Override
              public void start() {}

              @Override
              public void stop() {}

              @Override
              public long getNanoseconds() {
                return elapsedWallclockNanoseconds;
              }

              @Override
              public void update() {}
            };
          }
        };

    AppInfo appInfo =
        AppInfo.newBuilder()
            .setAppId(APP_ID)
            .setVersionId(VERSION_ID)
            .build();


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
            .setAppInfo(appInfo)
            .setRootDirectory(rootDirectory)
            .setEnvironment(appEnv)
            .setSessionsConfig(sessionsConfig)
            .setPublicRoot("")
            .build();

    environment = createEnvironment();
  }

  @After
  public void tearDown() {
    delegate = null;
    upRequest = null;
    environment = null;
  }

  String getGoogleAppsNamespace() {
    return getGoogleAppsNamespace(environment);
  }

  static String getGoogleAppsNamespace(ApiProxy.Environment env) {
    Map<String, Object> attributes = env.getAttributes();
    String appsNamespace = (String) attributes.get(APPS_NAMESPACE_KEY);
    if (appsNamespace == null) {
      return "";
    }
    return appsNamespace;
  }

  @Test
  public void testStatusException_Cancelled() {
    StatusProto proto =
        StatusProto.newBuilder().setCode(Code.CANCELLED_VALUE).setSpace("generic").build();
    Optional<ApiProxyException> optionalException =
        ApiProxyUtils.statusException(proto, "packageName", "methodName", null);
    assertThat(optionalException).isPresent();
    Exception exception = optionalException.get();
    assertThat(exception).isInstanceOf(ApiProxy.CancelledException.class);
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("The API call packageName.methodName() was explicitly cancelled.");
  }

  @Test
  public void testStatusException_DeadlineExceeded() {
    StatusProto proto =
        StatusProto.newBuilder()
            .setCode(Code.DEADLINE_EXCEEDED_VALUE)
            .setSpace("RPC")
            .setMessage("Deadline exceeded")
            .build();
    Optional<ApiProxyException> optionalException =
        ApiProxyUtils.statusException(proto, "packageName", "methodName", null);
    assertThat(optionalException).isPresent();
    Exception exception = optionalException.get();
    assertThat(exception).isInstanceOf(ApiProxy.ApiDeadlineExceededException.class);
    assertThat(exception).hasMessageThat().containsMatch("packageName.*methodName");
  }

  @Test
  public void testStatusException_OtherRpc() {
    Throwable cause = new Error("Something broke");
    StatusProto proto =
        StatusProto.newBuilder()
            .setCode(Code.INTERNAL_VALUE)
            .setSpace("RPC")
            .setMessage("Something broke")
            .build();
    Optional<ApiProxyException> optionalException =
        ApiProxyUtils.statusException(proto, "packageName", "methodName", cause);
    assertThat(optionalException).isPresent();
    Exception exception = optionalException.get();
    assertThat(exception).isInstanceOf(ApiProxy.UnknownException.class);
    assertThat(exception).hasMessageThat().containsMatch("packageName.*methodName");
    assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
  }

  @Test
  public void testUnknownException_CauseNotSerialized() throws Exception {
    Throwable cause = new Error("Something broke");
    ApiProxy.UnknownException exception = new UnknownException("packageName", "methodName", cause);
    assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
    byte[] serializedException;
    try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout)) {
      oout.writeObject(exception);
      oout.flush();
      serializedException = bout.toByteArray();
    }
    ApiProxy.UnknownException deserializedException;
    try (ByteArrayInputStream bin = new ByteArrayInputStream(serializedException);
        ObjectInputStream oin = new ObjectInputStream(bin)) {
      deserializedException = (ApiProxy.UnknownException) oin.readObject();
    }
    assertThat(deserializedException).hasCauseThat().isNull();
    assertThat(deserializedException).hasMessageThat().isEqualTo(exception.getMessage());
    assertThat(deserializedException.getStackTrace()).isEqualTo(exception.getStackTrace());
  }

  @Test
  public void testStatusException_NotRpc() {
    StatusProto proto =
        StatusProto.newBuilder().setCode(Code.UNKNOWN_VALUE).setSpace("generic").build();
    Optional<ApiProxyException> optionalException =
        ApiProxyUtils.statusException(proto, "packageName", "methodName", null);
    assertThat(optionalException).isEmpty();
  }

  @Test
  public void testCurrentEnvironment() {
    assertThat(environment.getAppId()).isEqualTo(APP_ID);
    assertThat(environment.getModuleId()).isEqualTo(ENGINE_ID);
    assertThat(environment.getVersionId()).isEqualTo(ENGINE_VERSION_ID);
    assertThat(environment.getAuthDomain()).isEqualTo("foo.com");
    @SuppressWarnings("deprecation") // testing deprecated method
    String namespace = environment.getRequestNamespace();
    assertThat(namespace).isEmpty();
    assertThat(getGoogleAppsNamespace()).isEmpty();
    assertThat(environment.getEmail()).isEqualTo("foo@foo.com");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ID_KEY)).isEqualTo("xxx");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ORGANIZATION_KEY)).isEqualTo("");
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_PEER_USERNAME)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_SECURITY_LEVEL)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.DATACENTER)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.REQUEST_ID_HASH)).isEqualTo("0000002A");
    assertThat(environment.getAttributes().get(ApiProxyImpl.REQUEST_LOG_ID)).isEqualTo("0000003B");
    assertThat(environment.getAttributes().get(ApiProxyImpl.DEFAULT_VERSION_HOSTNAME))
        .isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_ID)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_AUTHUSER)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_SESSION)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.APPSERVER_DATACENTER)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.APPSERVER_TASK_BNS)).isEqualTo(null);
    assertThat(
            environment.getAttributes().get(ApiProxyImpl.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY))
        .isEqualTo(false);
    assertThat(environment.getTraceId()).isEmpty();
  }

  @Test
  public void testCloudSqlJdbcConnectivityEnabled() {
    APIHostClientInterface apiHost = createAPIHost();
    delegate =
        ApiProxyImpl.builder()
            .setApiHost(apiHost)
            .setDeadlineOracle(oracle)
            .setByteCountBeforeFlushing(BYTE_COUNT_BEFORE_FLUSHING)
            .setMaxLogLineSize(MAX_LOG_LINE_SIZE)
            .setCloudSqlJdbcConnectivityEnabled(true)
            .build();
    assertThat(
            createEnvironment()
                .getAttributes()
                .get(ApiProxyImpl.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY))
        .isEqualTo(true);

    delegate =
        ApiProxyImpl.builder()
            .setApiHost(apiHost)
            .setDeadlineOracle(oracle)
            .setByteCountBeforeFlushing(BYTE_COUNT_BEFORE_FLUSHING)
            .setMaxLogLineSize(MAX_LOG_LINE_SIZE)
            .build();
    assertThat(
            createEnvironment()
                .getAttributes()
                .get(ApiProxyImpl.CLOUD_SQL_JDBC_CONNECTIVITY_ENABLED_KEY))
        .isEqualTo(false);
  }

  @Test
  public void testMultiDomainApp() {
    upRequest =
        upRequest.toBuilder()
            .setAuthDomain("example.com")
            .setEmail("foo@example.com")
            .buildPartial();
    environment = createEnvironment();

    assertThat(environment.getAppId()).isEqualTo(APP_ID);
    assertThat(environment.getVersionId()).isEqualTo(ENGINE_VERSION_ID);
    assertThat(environment.getAuthDomain()).isEqualTo("example.com");
    @SuppressWarnings("deprecation") // testing deprecated method
    String namespace = environment.getRequestNamespace();
    assertThat(namespace).isEmpty();
    assertThat(getGoogleAppsNamespace()).isEmpty();
    assertThat(environment.getEmail()).isEqualTo("foo@example.com");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ID_KEY)).isEqualTo("xxx");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ORGANIZATION_KEY)).isEqualTo("");
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_PEER_USERNAME)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_SECURITY_LEVEL)).isEqualTo(null);
  }

  @Test
  public void testDatacenterAttribute() {
    APIHostClientInterface apiHost = createAPIHost();
    delegate =
        ApiProxyImpl.builder()
            .setApiHost(apiHost)
            .setDeadlineOracle(oracle)
            .setExternalDatacenterName("na1")
            .build();
    environment = createEnvironment();
    assertThat(environment.getAttributes().get(ApiProxyImpl.DATACENTER)).isEqualTo("na1");
  }

  @Test
  public void testDefaultVersionHostname() {
    upRequest =
        upRequest.toBuilder().setDefaultVersionHostname("foo.bar.com").buildPartial();
    environment = createEnvironment();
    assertThat(environment.getAttributes().get(ApiProxyImpl.DEFAULT_VERSION_HOSTNAME))
        .isEqualTo("foo.bar.com");
  }

  @Test
  public void testGaPlusManagedUser() {
    upRequest = upRequest.toBuilder().setUserOrganization("foo.com").buildPartial();
    environment = createEnvironment();

    assertThat(environment.getAppId()).isEqualTo(APP_ID);
    assertThat(environment.getVersionId()).isEqualTo(ENGINE_VERSION_ID);
    assertThat(environment.getAuthDomain()).isEqualTo("foo.com");
    @SuppressWarnings("deprecation") // testing deprecated method
    String namespace = environment.getRequestNamespace();
    assertThat(namespace).isEmpty();
    assertThat(getGoogleAppsNamespace()).isEmpty();
    assertThat(environment.getEmail()).isEqualTo("foo@foo.com");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ID_KEY)).isEqualTo("xxx");
    assertThat(environment.getAttributes().get(ApiProxyImpl.USER_ORGANIZATION_KEY))
        .isEqualTo("foo.com");
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_PEER_USERNAME)).isEqualTo(null);
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_SECURITY_LEVEL)).isEqualTo(null);
  }

  @Test
  public void testTrustedApp() {
    upRequest = upRequest.toBuilder().setIsTrustedApp(true).buildPartial();
    environment = createEnvironment();
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_PEER_USERNAME))
        .isEqualTo("foo_peer");
    assertThat(environment.getAttributes().get(ApiProxyImpl.LOAS_SECURITY_LEVEL))
        .isEqualTo("foo_level");
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_ID)).isEqualTo("12345");
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_AUTHUSER)).isEqualTo("1");
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_SESSION)).isEqualTo("SESSION");
    assertThat(environment.getAttributes().get(ApiProxyImpl.APPSERVER_DATACENTER)).isEqualTo("yq");
    assertThat(environment.getAttributes().get(ApiProxyImpl.APPSERVER_TASK_BNS))
        .isEqualTo("/bns/yq/appserver/321");
  }

  @Test
  public void testGaiaIdIsZero() {
    upRequest = upRequest.toBuilder().setIsTrustedApp(true).setGaiaId(0).buildPartial();
    environment = createEnvironment();
    assertThat(environment.getAttributes().get(ApiProxyImpl.GAIA_ID)).isEqualTo("");
  }

  @Test
  public void testTraceDisabled() {
    StringProto request = StringProto.getDefaultInstance();
    delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray());
    assertThat(upResponse.hasSerializedTrace()).isFalse();
  }

  @Test
  public void testTraceEnabled() throws Exception {
    // Enable trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(1)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(context).buildPartial();
    environment = createEnvironment();

    // Make 1st API call with default proto.
    delegate.makeSyncCall(
        environment, "get.deadline", "Get", StringProto.getDefaultInstance().toByteArray());

    // Make 2nd API call.
    StringProto request = StringProto.newBuilder().setValue("pi").build();
    delegate.makeSyncCall(environment, "google.math", "LookupSymbol", request.toByteArray());

    // Make 3rd API call with errors.
    final StringProto request3 = StringProto.newBuilder().setValue("not-pi").build();
    assertThrows(
        ApiProxy.ApplicationException.class,
        () ->
            delegate.makeSyncCall(
                environment, "google.math", "LookupSymbol", request3.toByteArray()));

    environment.getTraceWriter().flushTrace();
    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(3);

    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/get.deadline.Get");
    long requestSpanId = startSpan.getParentSpanId().getId();
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());

    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/google.math.LookupSymbol");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());

    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/google.math.LookupSymbol");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
  }

  @Test
  public void testTraceContextResetsBetweenRequests() {
    TraceContextProto requestContext1 =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace_id1"))
            .setSpanId(1L)
            .setTraceMask(1)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(requestContext1).buildPartial();

    CloudTraceContext traceContext1 = CloudTrace.getCurrentContext(createEnvironment());

    TraceContextProto requestContext2 =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace_id2"))
            .setSpanId(2L)
            .setTraceMask(3)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(requestContext2).buildPartial();

    CloudTraceContext traceContext2 = CloudTrace.getCurrentContext(createEnvironment());

    assertThat(traceContext1.getTraceId()).isNotEqualTo(traceContext2.getTraceId());
    assertThat(traceContext1.getSpanId()).isNotEqualTo(traceContext2.getSpanId());
    assertThat(traceContext1.getTraceMask()).isNotEqualTo(traceContext2.getTraceMask());
  }

  @Test
  public void testStackTraceEnabled() throws Exception {
    // Enable trace and stack trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(3)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(context).buildPartial();
    environment = createEnvironment();

    StringProto request = StringProto.getDefaultInstance();
    // Make API call.
    delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray());

    environment.getTraceWriter().flushTrace();
    // Verify traces.
    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(1);

    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(3);
    assertThat(spanEvents.getEvent(0).hasStartSpan()).isTrue();
    assertThat(spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().hasStackTraceHashId())
        .isTrue();
    assertThat(spanEvents.getEvent(2).hasEndSpan()).isTrue();
  }

  @Test
  public void testTraceId() {
    // Test Trace ID must be 16 bytes (using 8a5711592032447e3c749a67493b9edb as example)
    TraceId.TraceIdProto traceId =
        TraceId.TraceIdProto.newBuilder()
            .setHi(0x0102030405060708L)
            .setLo(0x0910111213141516L)
            .build();

    TraceContextProto context =
        TraceContextProto.newBuilder().setTraceId(traceId.toByteString()).build();

    upRequest = upRequest.toBuilder().setTraceContext(context).buildPartial();
    environment = createEnvironment();

    assertThat(environment.getTraceId()).hasValue("01020304050607080910111213141516");
  }

  @Test
  public void testSuccess() throws InvalidProtocolBufferException {
    cpuCycles = 1000000L;
    assertThat(ApiStats.get(environment).getCpuTimeInMegaCycles()).isEqualTo(1L);
    StringProto.Builder request = StringProto.newBuilder();
    DoubleProto.Builder response = DoubleProto.newBuilder();
    assertThat(ApiStats.get(environment).getApiTimeInMegaCycles()).isEqualTo(0L); // Precondition

    request.setValue("pi");
    response.mergeFrom(
        delegate.makeSyncCall(
            environment, "google.math", "LookupSymbol", request.build().toByteArray()),
        ExtensionRegistry.getEmptyRegistry());

    assertThat(ApiStats.get(environment).getApiTimeInMegaCycles())
        .isEqualTo(DEFAULT_API_MCYCLES_PER_REQUEST);
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(Math.PI);
    cpuCycles = 23000000L;
    assertThat(ApiStats.get(environment).getCpuTimeInMegaCycles()).isEqualTo(23L);
  }

  /** How to signal that a task associated with a Future should be terminated. */
  private enum Signal {
    CANCEL, // Call cancel() on the Future.
    INTERRUPT // Interrupt the thread executing the Future's task.
  };

  /**
   * Tests error propagation for the case where the Future is cancelled or interrupted during a
   * synchronous API call.
   */
  @Test
  public void testCancellation() throws InterruptedException {
    String expectedDeadlineMessage =
        "The API call hang.forever.() was cancelled because the overall HTTP request deadline "
            + "was reached.";
    String expectedCancellationMessage = "The API call hang.forever.() was explicitly cancelled.";
    String expectedInterruptionMessage =
        "The API call hang.forever.() was cancelled because the thread was interrupted.";
    doCancellationTest(600L, expectedDeadlineMessage, Signal.CANCEL);
    doCancellationTest(800L, expectedDeadlineMessage, Signal.CANCEL);
    doCancellationTest(560L, expectedDeadlineMessage, Signal.CANCEL);
    doCancellationTest(300L, expectedCancellationMessage, Signal.CANCEL);

    doCancellationTest(600L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(800L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(560L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(300L, expectedInterruptionMessage, Signal.INTERRUPT);
  }

  /**
   * Attempts to make an API call that would hang forever, and then uses a separate thread to cancel
   * or interrupt it. Verifies that a ApiProxy.CancelledException is thrown with the expected
   * message.
   */
  private void doCancellationTest(
      long millisBeforeCancellation, String expectedMessage, final Signal signal)
      throws InterruptedException {
    // Set value that will be returned by mocked-out timer.
    elapsedWallclockNanoseconds = millisBeforeCancellation * 1000000L;
    Thread testThread = Thread.currentThread();
    Thread otherThread =
        new Thread(
            () -> {
              try {
                // Doesn't particularly matter how long this thread sleeps, since timer for other
                // thread is mocked out.
                // Just needs to be long enough to let the test thread start waiting on the future.
                Thread.sleep(600);
              } catch (InterruptedException e) {
                System.err.println("Other thread unexpectedly interrupted: " + e);
              }
              switch (signal) {
                case CANCEL:
                  synchronized (futures) {
                    for (Future<?> future : futures) {
                      future.cancel(true);
                    }
                  }
                  break;
                case INTERRUPT:
                  testThread.interrupt();
                  break;
              }
            });
    otherThread.start();
    ApiProxy.CancelledException ex =
        assertThrows(
            ApiProxy.CancelledException.class,
            () -> delegate.makeSyncCall(environment, "hang.forever", "", new byte[0]));
    assertWithMessage(
            String.format(
                "millisBeforeCancellation: %d; signal: %s", millisBeforeCancellation, signal))
        .that(ex)
        .hasMessageThat()
        .isEqualTo(expectedMessage);
    otherThread.join();
  }

  /**
   * Tests error propagation for the case where we are waiting for an API slot and the thread is
   * interrupted.
   */
  @Test
  public void testApiSlotCancellationTest() throws InterruptedException {
    String expectedDeadlineMessage =
        "The API call hang.forever.() was cancelled because the overall HTTP request deadline "
            + "was reached while waiting for concurrent API calls.";
    String expectedInterruptionMessage =
        "The API call hang.forever.() was cancelled because the thread was interrupted while "
            + "waiting for concurrent API calls.";
    // Pretend there are too many concurrent API calls to allow another to start.
    maxConcurrentApiCalls = 0;
    environment = createEnvironment();
    doCancellationTest(800L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(600L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(560L, expectedDeadlineMessage, Signal.INTERRUPT);
    doCancellationTest(300L, expectedInterruptionMessage, Signal.INTERRUPT);
  }

  @Test
  public void testDefaultDeadline() throws InvalidProtocolBufferException {
    StringProto request = StringProto.getDefaultInstance();
    DoubleProto response =
        DoubleProto.parseFrom(
            delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray()),
            ExtensionRegistry.getEmptyRegistry());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(DEFAULT_API_DEADLINE);
  }

  @Test
  public void testDeadlineOverride() throws InvalidProtocolBufferException {
    double userDeadline = 7.0;
    environment.getAttributes().put(ApiProxyImpl.API_DEADLINE_KEY, userDeadline);

    StringProto request = StringProto.getDefaultInstance();
    DoubleProto response =
        DoubleProto.parseFrom(
            delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray()),
            ExtensionRegistry.getEmptyRegistry());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(userDeadline);
  }

  @Test
  public void testMaxDeadline() throws InvalidProtocolBufferException {
    double userDeadline = 20.0;
    environment.getAttributes().put(ApiProxyImpl.API_DEADLINE_KEY, userDeadline);

    StringProto request = StringProto.getDefaultInstance();
    DoubleProto response =
        DoubleProto.parseFrom(
            delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray()),
            ExtensionRegistry.getEmptyRegistry());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(MAX_API_DEADLINE);
  }

  @Test
  public void testDefaultOfflineDeadline() throws InvalidProtocolBufferException {
    UPRequest.Builder builder = upRequest.toBuilder();
    builder.getRequestBuilder().setIsOffline(true);
    upRequest = builder.buildPartial();
    environment = createEnvironment();
    StringProto request = StringProto.getDefaultInstance();
    DoubleProto response =
        DoubleProto.parseFrom(
            delegate.makeSyncCall(environment, "get.deadline", "Get", request.toByteArray()),
            ExtensionRegistry.getEmptyRegistry());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(DEFAULT_OFFLINE_API_DEADLINE);
  }

  @Test
  public void testRemainingMillisNoTimeElapsed() {
    assertThat(environment.getRemainingMillis()).isEqualTo(600);
  }

  @Test
  public void testRemainingMillisSomeTimeElapsed() {
    elapsedWallclockNanoseconds = 3500000L; // 3.5 milliseconds
    assertThat(environment.getRemainingMillis()).isEqualTo(597);
  }

  /**
   * Asserts that the top of the stack trace of the given ApiProxyException is
   * "ApiProxyImpl.doSyncCall()". We use this in order to test the logic in
   * ApiProxyImpl.doSyncCall() which re-writes the stack trace of an ApiProxyException.
   */
  private static void assertStackTraceIsCorrect(ApiProxy.ApiProxyException e) {
    // The very top of the stack trace is actually
    // Thread.currentThread().getStackTrace()
    // We want the one just below that.
    String methodName = e.getStackTrace()[1].getMethodName();
    assertThat(methodName).isEqualTo("doSyncCall");
  }

  @Test
  public void testApplicationError() throws InvalidProtocolBufferException {
    StringProto request = StringProto.newBuilder().setValue("not_pi").build();

    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "google.math", "LookupSymbol", request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  /** Test the special case for memcache unavailability. */
  @Test
  public void testMethodWithMemcacheUnavailable() throws InvalidProtocolBufferException {
    StringProto request = StringProto.getDefaultInstance();

    ApiProxy.CapabilityDisabledException ex =
        assertThrows(
            ApiProxy.CapabilityDisabledException.class,
            () ->
                delegate.makeSyncCall(environment, "memcache", "DontCare", request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  /** Make sure special case does not trigger for non-memcache. */
  @Test
  public void testMethodWithMemcacheUnavailable_nonMemcache()
      throws InvalidProtocolBufferException {
    VoidProto request = VoidProto.getDefaultInstance();

    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () ->
                delegate.makeSyncCall(
                    environment,
                    "generate.same.val.as.memcache.unavailable",
                    "DontCare",
                    request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  @Test
  public void testRpcError() throws InvalidProtocolBufferException {

    StringProto request = StringProto.newBuilder().setValue("not_pi").build();

    ApiProxy.ApplicationException ex =
        assertThrows(
            ApiProxy.ApplicationException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.rpc.error", "LookupSymbol", request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  @Test
  public void testWrongPackage() throws InvalidProtocolBufferException {

    StringProto request = StringProto.getDefaultInstance();

    ApiProxy.CallNotFoundException ex =
        assertThrows(
            ApiProxy.CallNotFoundException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "non.existent", "LookupSymbol", request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  @Test
  public void testWrongMethod() throws InvalidProtocolBufferException {

    StringProto request = StringProto.getDefaultInstance();

    ApiProxy.CallNotFoundException ex =
        assertThrows(
            ApiProxy.CallNotFoundException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "google.math", "NonExistentCall", request.toByteArray()));
    assertStackTraceIsCorrect(ex);
  }

  @Test
  public void testParseError() {
    ApiProxy.ArgumentException e =
        assertThrows(
            ApiProxy.ArgumentException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.parse.error", "", "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testCapabilityDisabledError() {
    ApiProxy.CapabilityDisabledException e =
        assertThrows(
            ApiProxy.CapabilityDisabledException.class,
            () ->
                delegate.makeSyncCall(
                    environment,
                    "generate.capability.disabled.error",
                    "",
                    "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testFeatureNotEnabledError() {
    ApiProxy.FeatureNotEnabledException e =
        assertThrows(
            ApiProxy.FeatureNotEnabledException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.feature.disabled.error", "", "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("generate.feature.disabled.error. You need to turn on billing!");
  }

  @Test
  public void testOverQuotaError() {
    ApiProxy.OverQuotaException e =
        assertThrows(
            ApiProxy.OverQuotaException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.over.quota.error", "", "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testRequestTooLargeError() {
    ApiProxy.RequestTooLargeException e =
        assertThrows(
            ApiProxy.RequestTooLargeException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.too.large.error", "", "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testResponseTooLargeError() {
    ApiProxy.ResponseTooLargeException e =
        assertThrows(
            ApiProxy.ResponseTooLargeException.class,
            () ->
                delegate.makeSyncCall(
                    environment,
                    "generate.too.large.response.error",
                    "",
                    "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testSecurityViolationError() {
    ApiProxy.UnknownException e =
        assertThrows(
            ApiProxy.UnknownException.class,
            () ->
                delegate.makeSyncCall(
                    environment, "generate.security.violation", "", "garbage".getBytes(UTF_8)));
    assertStackTraceIsCorrect(e);
  }

  @Test
  public void testAllAPIResponseErrorsHandled() {
    APIResponse.ERROR[] currentError = new APIResponse.ERROR[1];
    APIHostClientInterface host =
        new APIHostClientInterface() {
          @Override
          public void call(
              AnyRpcClientContext ctx, APIRequest req, AnyRpcCallback<APIResponse> cb) {
            APIResponse reply =
                APIResponse.newBuilder().setError(currentError[0].getNumber()).build();
            cb.success(reply);
          }

          @Override
          public void disable() {
            throw new UnsupportedOperationException();
          }

          @Override
          public void enable() {
            throw new UnsupportedOperationException();
          }

          @Override
          public AnyRpcClientContext newClientContext() {
            return mock(AnyRpcClientContext.class);
          }
        };
    delegate = ApiProxyImpl.builder().setApiHost(host).setDeadlineOracle(oracle).build();
    for (APIResponse.ERROR error : APIResponse.ERROR.values()) {
      if (error.equals(APIResponse.ERROR.OK)) {
        continue;
      }
      currentError[0] = error;
      Throwable e =
          assertThrows(
              Throwable.class,
              () -> delegate.makeSyncCall(environment, "whatever", "", "garbage".getBytes(UTF_8)));
      assertWithMessage("Wrong type exception: %s", e)
          .that(e)
          .isInstanceOf(ApiProxy.ApiProxyException.class);
    }
  }

  @Test
  public void testRequestNamespace() {
    UPRequest.Builder builder = upRequest.toBuilder();
    HttpRequest.Builder httpRequest = builder.getRequestBuilder();
    httpRequest.addHeaders(
        ParsedHttpHeader.newBuilder()
            .setKey(ApiProxyImpl.EnvironmentImpl.DEFAULT_NAMESPACE_HEADER)
            .setValue("ns"));
    upRequest = builder.buildPartial();
    ApiProxy.Environment localEnvironment = createEnvironment();
    @SuppressWarnings("deprecation") // testing deprecated method
    String namespace = localEnvironment.getRequestNamespace();
    assertThat(namespace).isEqualTo("ns");
    assertThat(getGoogleAppsNamespace(localEnvironment)).isEqualTo("ns");
    Map<String, Object> attributes = localEnvironment.getAttributes();
    assertThat(attributes).doesNotContainKey(CURRENT_NAMESPACE_KEY);
  }

  @Test
  public void testCurrentNamespace() {
    HttpRequest httpRequest =
        upRequest.getRequest().toBuilder()
            .addHeaders(
                ParsedHttpHeader.newBuilder()
                    .setKey(ApiProxyImpl.EnvironmentImpl.DEFAULT_NAMESPACE_HEADER)
                    .setValue("request-ns"))
            .addHeaders(
                ParsedHttpHeader.newBuilder()
                    .setKey(ApiProxyImpl.EnvironmentImpl.CURRENT_NAMESPACE_HEADER)
                    .setValue("current-ns"))
            .buildPartial();
    upRequest = upRequest.toBuilder().setRequest(httpRequest).buildPartial();
    ApiProxy.Environment localEnvironment = createEnvironment();
    Map<String, Object> attributes = localEnvironment.getAttributes();
    String namespace = (String) attributes.get(CURRENT_NAMESPACE_KEY);
    assertThat(namespace).isEqualTo("current-ns");
    // Special case for Java, the request namespace is set to the
    // current namespace.
    namespace = (String) attributes.get(APPS_NAMESPACE_KEY);
    assertThat(namespace).isEqualTo("request-ns");
  }

  @Test
  public void testAsync_traceDisabled() throws ExecutionException, InterruptedException {
    StringProto request = StringProto.getDefaultInstance();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    delegate
        .makeAsyncCall(environment, "get.deadline", "Get", request.toByteArray(), apiConfig)
        .get();
    assertThat(upResponse.hasSerializedTrace()).isFalse();
  }

  @Test
  public void testAsync_traceEnabled() throws InvalidProtocolBufferException {
    // Enable trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(1)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(context).buildPartial();
    environment = createEnvironment();

    // Make 1st API call.
    StringProto request = StringProto.getDefaultInstance();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> response1 =
        delegate.makeAsyncCall(
            environment, "get.deadline", "Get", request.toByteArray(), apiConfig);

    // Make 2nd API call.
    StringProto request2 = StringProto.newBuilder().setValue("pi").build();
    Future<byte[]> response2 =
        delegate.makeAsyncCall(
            environment, "google.math", "LookupSymbol", request2.toByteArray(), apiConfig);

    // Make 3rd API call with errors.
    StringProto request3 = StringProto.newBuilder().setValue("not_pi").build();
    Future<byte[]> response3 =
        delegate.makeAsyncCall(
            environment, "google.math", "LookupSymbol", request3.toByteArray(), apiConfig);

    try {
      response1.get();
      response2.get();
      response3.get();
      fail();
    } catch (Exception expected) {
    }

    environment.getTraceWriter().flushTrace();
    // Verify traces.
    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(3);

    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/get.deadline.Get");
    long requestSpanId = startSpan.getParentSpanId().getId();
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());

    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/google.math.LookupSymbol");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());

    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/google.math.LookupSymbol");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
  }

  @Test
  public void testAsync_StackTraceEnabled() throws Exception {
    // Enable trace and stack trace.
    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(ByteString.copyFromUtf8("trace id"))
            .setSpanId(1L)
            .setTraceMask(3)
            .build();
    upRequest = upRequest.toBuilder().setTraceContext(context).buildPartial();
    environment = createEnvironment();

    // Make API call.
    StringProto request = StringProto.getDefaultInstance();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> response1 =
        delegate.makeAsyncCall(
            environment, "get.deadline", "Get", request.toByteArray(), apiConfig);
    response1.get();

    environment.getTraceWriter().flushTrace();
    // Verify traces.
    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(1);

    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(3);
    assertThat(spanEvents.getEvent(0).hasStartSpan()).isTrue();
    assertThat(spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().hasStackTraceHashId())
        .isTrue();
    assertThat(spanEvents.getEvent(2).hasEndSpan()).isTrue();
  }

  @Test
  public void testAsync_success()
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    StringProto request = StringProto.newBuilder().setValue("pi").build();
    DoubleProto.Builder response = DoubleProto.newBuilder();

    delegate.makeSyncCall(environment, "google.math", "LookupSymbol", request.toByteArray());
    assertThat(ApiStats.get(environment).getApiTimeInMegaCycles())
        .isEqualTo(DEFAULT_API_MCYCLES_PER_REQUEST);

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> bytes =
        delegate.makeAsyncCall(
            environment, "google.math", "LookupSymbol", request.toByteArray(), apiConfig);
    response.mergeFrom(bytes.get());
    assertThat(bytes).isInstanceOf(ApiProxy.ApiResultFuture.class);
    assertThat(((ApiProxy.ApiResultFuture) bytes).getCpuTimeInMegaCycles())
        .isEqualTo(DEFAULT_API_MCYCLES_PER_REQUEST);
    assertThat(ApiStats.get(environment).getApiTimeInMegaCycles())
        .isEqualTo(2 * DEFAULT_API_MCYCLES_PER_REQUEST);
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(Math.PI);
  }

  @Test
  public void testAsync_notFinished() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> future =
        delegate.makeAsyncCall(environment, "hang.forever", "", new byte[0], apiConfig);
    ApiProxy.ApiResultFuture<?> resultFuture = (ApiProxy.ApiResultFuture<?>) future;
    assertThat(future.isDone()).isFalse();
    assertThrows(IllegalStateException.class, resultFuture::getWallclockTimeInMillis);
    assertThrows(IllegalStateException.class, resultFuture::getCpuTimeInMegaCycles);
  }

  @Test
  public void testAsync_sleep() throws ExecutionException, InterruptedException {
    Integer32Proto request = Integer32Proto.newBuilder().setValue(100).build();

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    // Default deadline is 5 seconds (which is >> 100 milliseconds). Thus, the Future is
    // expected to complete in about 100 milliseconds before the deadline.
    Future<byte[]> future =
        delegate.makeAsyncCall(environment, "sleep", "Sleep", request.toByteArray(), apiConfig);
    ApiProxy.ApiResultFuture<?> resultFuture = (ApiProxy.ApiResultFuture<?>) future;
    future.get();
    long time = resultFuture.getWallclockTimeInMillis();
    assertWithMessage("API call time in milliseconds").that(time).isAtLeast(50L);
    assertWithMessage("API call time in milliseconds").that(time).isLessThan(500L);
  }

  @Test
  public void testAsync_defaultDeadline()
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    StringProto request = StringProto.getDefaultInstance();
    DoubleProto.Builder response = DoubleProto.newBuilder();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> bytes =
        delegate.makeAsyncCall(
            environment, "get.deadline", "Get", request.toByteArray(), apiConfig);
    response.mergeFrom(bytes.get());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(DEFAULT_API_DEADLINE);
  }

  @Test
  public void testAsync_deadlineExceeded() {
    double userDeadline = 0.250;
    int sleepTime = 10_000; // 10 seconds

    Integer32Proto request = Integer32Proto.newBuilder().setValue(sleepTime).build();

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(userDeadline);
    Future<byte[]> future =
        delegate.makeAsyncCall(environment, "sleep", "Sleep", request.toByteArray(), apiConfig);

    ExecutionException e = assertThrows(ExecutionException.class, future::get);
    sleepSemaphore.release();
    Throwable cause = e.getCause();
    assertThat(cause).isInstanceOf(ApiProxy.ApiDeadlineExceededException.class);
    assertThat(future.isDone()).isTrue();

    ApiProxy.ApiResultFuture<?> result = (ApiProxy.ApiResultFuture<?>) future;
    // The 'Sleep' call should take at least 10 seconds to complete. We expect the RPC to
    // time out after at least 0.25 seconds. The Future's deadline will be set to 0.750 seconds.
    assertThat(result.getWallclockTimeInMillis()).isAtLeast(250);
    assertThat(result.getWallclockTimeInMillis()).isAtMost(5000);
    assertThat(result.getCpuTimeInMegaCycles()).isNotEqualTo(-1L);
  }

  @Test
  @SuppressWarnings("InstantNow") // com.google.common.time is not open source yet
  public void testAsync_deadlineExceededWhileWaitingForApiSlot() throws Exception {
    maxConcurrentApiCalls = 1;
    ApiProxyImpl.EnvironmentImpl nonConcurrentEnvironment = createEnvironment();
    int sleepTime = 10_000; // 10 seconds
    Integer32Proto request = Integer32Proto.newBuilder().setValue(sleepTime).build();

    double firstDeadline = 20.0;
    ApiProxy.ApiConfig firstApiConfig = new ApiProxy.ApiConfig();
    firstApiConfig.setDeadlineInSeconds(firstDeadline);

    // Issue the first sleep call, to use up our one concurrent API call.
    Future<?> firstFuture =
        delegate.makeAsyncCall(
            nonConcurrentEnvironment, "sleep", "Sleep", request.toByteArray(), firstApiConfig);

    // Wait until we are sure that the sleep call has actually arrived at the point where it waits
    // for the semaphore, which means that it has taken the one API slot.
    Instant stopTime = Instant.now().plusSeconds(5);
    while (Instant.now().isBefore(stopTime) && sleepSemaphore.getQueueLength() == 0) {
      Thread.sleep(10);
    }

    // Now issue a second sleep call with a much shorter deadline. This should time out while
    // waiting for the API slot.
    double secondDeadline = 0.250;
    ApiProxy.ApiConfig secondApiConfig = new ApiProxy.ApiConfig();
    secondApiConfig.setDeadlineInSeconds(secondDeadline);
    Instant asyncCallStart = Instant.now();
    Future<?> secondFuture =
        delegate.makeAsyncCall(
            nonConcurrentEnvironment, "sleep", "Sleep", request.toByteArray(), secondApiConfig);
    // If we didn't time out while waiting for the API slot, then we will have waited the full 10
    // seconds while the first API call was using that slot. So if elapsed time is < 2 seconds then
    // that didn't happen.
    Instant asyncCallEnd = Instant.now();
    assertThat(Duration.between(asyncCallStart, asyncCallEnd).getSeconds()).isLessThan(2);

    // Give it a couple of seconds to time out. If we don't respect the timeout while waiting for
    // the API slot, then we won't succeed in timing out.
    ApiProxy.CancelledException ex =
        assertThrows(ApiProxy.CancelledException.class, () -> secondFuture.get(2, SECONDS));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "The API call sleep.Sleep() was cancelled because the thread was interrupted"
                + " while waiting for concurrent API calls.");

    firstFuture.cancel(true);
  }

  @Test
  public void testAsync_deadlineOverride()
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    double userDeadline = 7.0;
    StringProto request = StringProto.getDefaultInstance();
    DoubleProto.Builder response = DoubleProto.newBuilder();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(userDeadline);
    Future<byte[]> bytes =
        delegate.makeAsyncCall(
            environment, "get.deadline", "Get", request.toByteArray(), apiConfig);
    response.mergeFrom(bytes.get());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(userDeadline);
  }

  @Test
  public void testAsync_rpcDeadlineExceeded() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(10.0);

    Future<byte[]> task =
        delegate.makeAsyncCall(
            environment,
            "generate.deadline.exceeded.error",
            "",
            "garbage".getBytes(UTF_8),
            apiConfig);
    ExecutionException ex = assertThrows(ExecutionException.class, task::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.ApiDeadlineExceededException.class);
    assertThat(task.isDone()).isTrue();
    ApiProxy.ApiResultFuture<?> result = (ApiProxy.ApiResultFuture<?>) task;
    // We expect the wall clock time to be less than Future's deadline of 10 seconds.
    assertThat(result.getWallclockTimeInMillis()).isLessThan(5000);
    assertThat(result.getCpuTimeInMegaCycles()).isNotEqualTo(-1L);
  }

  @Test
  public void testAsync_rpcCancelled() {
    Future<byte[]> task =
        delegate.makeAsyncCall(
            environment,
            "generate.cancelled.rpc",
            "",
            "garbage".getBytes(UTF_8),
            new ApiProxy.ApiConfig());
    ExecutionException ex = assertThrows(ExecutionException.class, task::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.CancelledException.class);
    assertThat(ex)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("The API call generate.cancelled.rpc.() was explicitly cancelled.");
  }

  @Test
  public void testAsync_rpcServerError() {
    Future<byte[]> task =
        delegate.makeAsyncCall(
            environment,
            "generate.rpc.server.error",
            "",
            "garbage".getBytes(UTF_8),
            new ApiProxy.ApiConfig());
    ExecutionException ex = assertThrows(ExecutionException.class, task::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.UnknownException.class);
  }

  @Test
  public void testAsync_maxDeadline()
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    double userDeadline = 20.0;
    StringProto request = StringProto.getDefaultInstance();
    DoubleProto.Builder response = DoubleProto.newBuilder();
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    apiConfig.setDeadlineInSeconds(userDeadline);
    Future<byte[]> bytes =
        delegate.makeAsyncCall(
            environment, "get.deadline", "Get", request.toByteArray(), apiConfig);
    response.mergeFrom(bytes.get());
    assertThat(response.hasValue()).isTrue();
    assertThat(response.getValue()).isWithin(0.00001).of(MAX_API_DEADLINE);
  }

  @Test
  public void testAsync_applicationError() {
    StringProto request = StringProto.newBuilder().setValue("not_pi").build();

    Future<byte[]> task =
        delegate.makeAsyncCall(
            environment,
            "google.math",
            "LookupSymbol",
            request.toByteArray(),
            new ApiProxy.ApiConfig());
    ExecutionException ex = assertThrows(ExecutionException.class, task::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.ApplicationException.class);
  }

  @Test
  public void testAsync_capabilityDisabledError() {
    Future<byte[]> task =
        delegate.makeAsyncCall(
            environment,
            "generate.capability.disabled.error",
            "",
            "garbage".getBytes(UTF_8),
            new ApiProxy.ApiConfig());
    ExecutionException ex = assertThrows(ExecutionException.class, task::get);
    assertThat(ex).hasCauseThat().isInstanceOf(ApiProxy.CapabilityDisabledException.class);
  }

  @Test
  public void testDefaultLogsSetting() throws IOException {
    AppInfo appInfo =
        AppInfo.newBuilder()
            .setAppId(APP_ID)
            .setVersionId(VERSION_ID)
            .build();

    appVersion = createAppVersion(appInfo, rootDirectory);

    environment = createEnvironment();

    assertThat(environment.getAppLogsWriter().getByteCountBeforeFlushing())
        .isEqualTo(BYTE_COUNT_BEFORE_FLUSHING);
    assertThat(environment.getAppLogsWriter().getMaxLogMessageLength())
        .isEqualTo(MAX_LOG_LINE_SIZE);
  }

  @Test
  public void testCurrentRequestThreadFactory() throws InterruptedException, IOException {
    // Override the regular setup for this test to enable request threads.
    ApplicationEnvironment appEnv =
        new ApplicationEnvironment(
            APP_ID,
            VERSION_ID,
            ImmutableMap.of(),
            ImmutableMap.of(),
            rootDirectory,
            ApplicationEnvironment.RuntimeConfiguration.DEFAULT_FOR_TEST);
    AppInfo appInfo =
        AppInfo.newBuilder()
            .setAppId(APP_ID)
            .setVersionId(VERSION_ID)
            .build();
    appVersion =
        AppVersion.builder()
            .setAppVersionKey(AppVersionKey.of(APP_ID, VERSION_ID))
            .setAppInfo(appInfo)
            .setRootDirectory(rootDirectory)
            .setEnvironment(appEnv)
            .setSessionsConfig(sessionsConfig)
            .setPublicRoot("")
            .build();
    environment = createEnvironment();
    ApiProxy.setEnvironmentForCurrentThread(environment);
    try {
      ThreadFactory factory = ThreadManager.currentRequestThreadFactory();
      Runnable r = () -> {};
      // First check that thread creation from a request thread works.
      assertThat(factory.newThread(r)).isNotNull();
      // Then check that doing the same from a non-request thread fails with
      // a NullPointerException with a specific message.
      AtomicBoolean caughtExpectedException = new AtomicBoolean();
      Thread t =
          new Thread(
              () -> {
                try {
                  factory.newThread(r);
                } catch (NullPointerException e) {
                  if (e.getMessage()
                      .equals(
                          "Operation not allowed in a thread that is neither the original"
                              + " request thread nor a thread created by ThreadManager")) {
                    caughtExpectedException.set(true);
                  }
                }
              });
      t.start();
      t.join();
      assertWithMessage("Was expecting a NPE with a specific message to be thrown")
          .that(caughtExpectedException.get())
          .isTrue();
    } finally {
      ApiProxy.clearEnvironmentForCurrentThread();
    }
  }

  @Test
  public void testExceptionInRpcCallDoesNotCountAsOngoingApiCall() {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    APIHostClientInterface apiHost =
        new MockAPIHost(null) {
          @Override
          public void call(
              AnyRpcClientContext anyCtx, APIRequest req, AnyRpcCallback<APIResponse> callback) {
            throw new UnsupportedOperationException();
          }
        };
    maxConcurrentApiCalls = 1;
    delegate =
        ApiProxyImpl.builder()
            .setApiHost(apiHost)
            .setDeadlineOracle(oracle)
            .setExternalDatacenterName("na1")
            .build();
    environment = createEnvironment();
    Future<?> result1 =
        delegate.makeAsyncCall(
            environment, "ignored.package", "IgnoredMethod", new byte[0], apiConfig);
    ExecutionException exception1 = assertThrows(ExecutionException.class, result1::get);
    assertThat(exception1).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
    Future<?> result2 =
        delegate.makeAsyncCall(
            environment, "ignored.package", "IgnoredMethod", new byte[0], apiConfig);
    ExecutionException exception2 = assertThrows(ExecutionException.class, result2::get);
    assertThat(exception2).hasCauseThat().isInstanceOf(UnsupportedOperationException.class);
  }

  private AppVersion createAppVersion(String versionId, AppInfo appInfo, File rootDirectory) {
    ApplicationEnvironment appEnv =
        new ApplicationEnvironment(
            APP_ID,
            versionId,
            ImmutableMap.of(),
            ImmutableMap.of(),
            rootDirectory,
            ApplicationEnvironment.RuntimeConfiguration.DEFAULT_FOR_TEST);

    return AppVersion.builder()
        .setAppVersionKey(AppVersionKey.of(APP_ID, versionId))
        .setAppInfo(appInfo)
        .setRootDirectory(rootDirectory)
        .setEnvironment(appEnv)
        .setSessionsConfig(sessionsConfig)
        .setPublicRoot("")
        .build();
  }

  private AppVersion createAppVersion(AppInfo appInfo, File rootDirectory) {
    return createAppVersion(VERSION_ID, appInfo, rootDirectory);
  }

  private ApiProxyImpl.EnvironmentImpl createEnvironment() {
    return createEnvironment(this.upRequest, this.upResponse);
  }

  private ApiProxyImpl.EnvironmentImpl createEnvironment(
      UPRequest upRequest, MutableUpResponse upResponse) {
    return delegate.createEnvironment(
        appVersion,
        upRequest,
        upResponse,
        TraceWriter.getTraceWriterForRequest(upRequest, upResponse),
        mockTimer,
        REQUEST_ID,
        futures,
        new Semaphore(maxConcurrentApiCalls),
        new ThreadGroup("test"),
        new RequestState(),
        600L);
  }

  private APIHostClientInterface createAPIHost() {
    return new MockAPIHost(sleepSemaphore);
  }

  /**
   * {@link APIHostClientInterface} implementation that just implements a fixed set of (fake)
   * methods locally. A real {@link APIHostClientInterface} will do some kind of RPC call, but for
   * the purposes of the test we just need something that will execute code asynchronously. So we
   * just make a new thread for every call, with the contract that it should invoke the callback
   * when the call is complete.
   */
  private static class MockAPIHost implements APIHostClientInterface {

    private final Semaphore latch;

    MockAPIHost(Semaphore latch) {
      this.latch = latch;
    }

    @Override
    public void call(
        AnyRpcClientContext anyCtx, APIRequest req, AnyRpcCallback<APIResponse> callback) {
      MockRpcClientContext ctx = (MockRpcClientContext) anyCtx;
      ctx.setStartTimeMillis(System.currentTimeMillis());
      ctx.setCallback(callback);
      new Thread(() -> call(ctx, req)).start();
    }

    private void call(MockRpcClientContext ctx, APIRequest req) {
      final APIResponse.Builder reply = APIResponse.newBuilder();
      if (req.getApiPackage().equals("google.math")) {
        if (req.getCall().equals("LookupSymbol")) {
          StringProto.Builder reqProto = StringProto.newBuilder();
          try {
            reqProto.mergeFrom(req.getPb(), ExtensionRegistry.getEmptyRegistry());
          } catch (InvalidProtocolBufferException e) {
            throw new AssertionError("InvalidProtocolBufferException", e);
          }
          final String symbol = reqProto.getValue();

          DoubleProto.Builder replyProto = DoubleProto.newBuilder();
          if (symbol.equals("pi")) {
            replyProto.setValue(Math.PI);
          } else {
            ctx.finishWithAppError(42, "SymbolNotFound: " + symbol);
            return;
          }
          reply
              .setError(APIResponse.ERROR.OK_VALUE)
              .setPb(replyProto.build().toByteString())
              .setCpuUsage(DEFAULT_API_MCYCLES_PER_REQUEST);
        } else {
          reply
              .setError(APIResponse.ERROR.CALL_NOT_FOUND_VALUE)
              .setCpuUsage(DEFAULT_API_MCYCLES_PER_REQUEST);
        }
      } else if (req.getApiPackage().equals("sleep")) {
        Integer32Proto.Builder reqProto = Integer32Proto.newBuilder();
        try {
          reqProto.mergeFrom(req.getPb(), ExtensionRegistry.getEmptyRegistry());
        } catch (InvalidProtocolBufferException e) {
          throw new AssertionError("InvalidProtocolBufferException", e);
        }
        try {
          // This will either time out after the given time (which is what the sleep is supposed to
          // do), or it will time out early because we've released the semaphore. Either way we
          // don't check the returned boolean.
          latch.tryAcquire(reqProto.getValue(), MILLISECONDS);
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
        reply.setError(APIResponse.ERROR.OK_VALUE).setPb(ByteString.EMPTY);
      } else if (req.getApiPackage().equals("get.deadline")) {
        DoubleProto replyProto =
            DoubleProto.newBuilder().setValue(ctx.getDeadlineInSeconds()).build();
        reply.setError(APIResponse.ERROR.OK_VALUE).setPb(replyProto.toByteString());
      } else if (req.getApiPackage().equals("generate.parse.error")) {
        reply.setError(APIResponse.ERROR.PARSE_ERROR_VALUE);
      } else if (req.getApiPackage().equals("generate.capability.disabled.error")) {
        reply.setError(APIResponse.ERROR.CAPABILITY_DISABLED_VALUE);
      } else if (req.getApiPackage().equals("generate.feature.disabled.error")) {
        reply
            .setError(APIResponse.ERROR.FEATURE_DISABLED_VALUE)
            .setErrorMessage("You need to turn on billing!");
      } else if (req.getApiPackage().equals("generate.over.quota.error")) {
        reply.setError(APIResponse.ERROR.OVER_QUOTA_VALUE);
      } else if (req.getApiPackage().equals("generate.too.large.error")) {
        reply.setError(APIResponse.ERROR.REQUEST_TOO_LARGE_VALUE);
      } else if (req.getApiPackage().equals("generate.too.large.response.error")) {
        reply.setError(APIResponse.ERROR.RESPONSE_TOO_LARGE_VALUE);
      } else if (req.getApiPackage().equals("generate.security.violation")) {
        reply.setError(APIResponse.ERROR.SECURITY_VIOLATION_VALUE);
      } else if (req.getApiPackage().equals("generate.rpc.error")) {
        reply
            .setError(APIResponse.ERROR.RPC_ERROR_VALUE)
            .setErrorMessage("Error detail")
            .setRpcError(APIResponse.RpcError.APPLICATION_ERROR)
            .setRpcApplicationError(6);
      } else if (req.getApiPackage().equals("memcache")
          || req.getApiPackage().equals("generate.same.val.as.memcache.unavailable")) {
        // For either of these two packages generate an application error code that is UNAVAILABLE
        // in the case of the memcache package.
        ctx.finishWithAppError(
            MemcacheServicePb.MemcacheServiceError.ErrorCode.UNAVAILABLE_VALUE,
            "Pretend unavailable");
        return;
      } else if (req.getApiPackage().equals("generate.deadline.exceeded.error")) {
        ctx.finishWithError(
            "RPC", Code.DEADLINE_EXCEEDED_VALUE, Code.DEADLINE_EXCEEDED_VALUE, "Deadline exceeded");
        return;
      } else if (req.getApiPackage().equals("generate.cancelled.rpc")) {
        ctx.finishWithError("generic", Code.CANCELLED_VALUE, Code.CANCELLED_VALUE, "Cancelled");
        return;
      } else if (req.getApiPackage().equals("generate.rpc.server.error")) {
        ctx.finishWithError("RPC", Code.OK_VALUE, Code.OK_VALUE, "Server error");
        return;
      } else if (req.getApiPackage().equals("hang.forever")) {
        return; // Don't call rpcFinished().
      } else {
        reply.setError(APIResponse.ERROR.CALL_NOT_FOUND_VALUE);
      }

      ctx.finishWithResponse(reply.build());
    }

    @Override
    public void disable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void enable() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AnyRpcClientContext newClientContext() {
      return new MockRpcClientContext();
    }
  }

  private static class MockRpcClientContext implements AnyRpcClientContext {
    private long startTimeMillis;
    private AnyRpcCallback<APIResponse> callback;
    private double deadlineSeconds;
    private int applicationError;
    private String errorDetail;
    private StatusProto status;

    void setStartTimeMillis(long startTimeMillis) {
      this.startTimeMillis = startTimeMillis;
    }

    void setCallback(AnyRpcCallback<APIResponse> callback) {
      this.callback = callback;
    }

    void finishWithResponse(APIResponse response) {
      callback.success(response);
    }

    void finishWithAppError(int applicationError, String errorDetail) {
      this.applicationError = applicationError;
      this.errorDetail = errorDetail;
      this.status =
          StatusProto.newBuilder()
              .setSpace("AppError")
              .setCode(applicationError)
              .setCanonicalCode(applicationError)
              .setMessage(errorDetail)
              .build();
      callback.failure();
    }

    void finishWithError(String space, int code, int canonicalCode, String errorDetail) {
      this.applicationError = 0;
      this.errorDetail = errorDetail;
      this.status =
          StatusProto.newBuilder()
              .setSpace(space)
              .setCode(code)
              .setCanonicalCode(canonicalCode)
              .setMessage(errorDetail)
              .build();
      callback.failure();
    }

    @Override
    public int getApplicationError() {
      return applicationError;
    }

    @Override
    public String getErrorDetail() {
      return errorDetail;
    }

    @Override
    public StatusProto getStatus() {
      return status;
    }

    @Override
    public long getStartTimeMillis() {
      return startTimeMillis;
    }

    @Override
    public Throwable getException() {
      return null;
    }

    @Override
    public void setDeadline(double seconds) {
      this.deadlineSeconds = seconds;
    }

    double getDeadlineInSeconds() {
      return deadlineSeconds;
    }

    @Override
    public void startCancel() {
      finishWithError("generic", Code.CANCELLED_VALUE, Code.CANCELLED_VALUE, "Cancelled");
    }
  }
}
