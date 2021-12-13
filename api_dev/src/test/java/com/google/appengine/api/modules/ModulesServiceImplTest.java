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

package com.google.appengine.api.modules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsResponse;
import com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleResponse;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleResponse;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for {@link ModulesServiceImpl}.
 *
 * <p>New versions of the following methods are being provided.
 *
 * <ol>
 *   <li>startModule[Async] becomes startVersion[Async]
 *   <li>stopModule[Async] becomes stopVersion[Async]
 *   <li>getHostname[Async](String module, String version) becomes getVersionHostname[Async]
 *   <li>getHostname[Async](String module, String version, int instanceId) becomes
 *       getInstanceHostname[Async]
 * </ol>
 *
 * The old versions are being deprecated and will be removed in an upcoming release.
 *
 * <p>In these tests we take advantage of some implementation knowledge to avoid redundant tests
 *
 * <ol>
 *   <li>Synchronous API methods are implemented by calling their asynchronous counterparts. Hence
 *       testing a synchronous method also tests its asynchronous counterpart.
 *   <li>Deprecated API methods are implemented by calling the new version. Hence testing the
 *       deprecated API method also tests its new version. Only functionality provided by a new
 *       version of a method that is not provided by the deprecated version is directly tested here.
 * </ol>
 */
@RunWith(JUnit4.class)
public class ModulesServiceImplTest {

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  MockEnvironment env;
  ModulesService api;
  @Mock ApiProxy.Delegate<MockEnvironment> delegate;

  private static final String INSTANCE_ID_KEY = "com.google.appengine.instance.id";

  @Before
  public void setUp() {
    api = ModulesServiceFactory.getModulesService();
    ApiProxy.setDelegate(delegate);

    env = new MockEnvironment("testapp", "v1.1");
    ApiProxy.setEnvironmentForCurrentThread(env);
  }

  private void setModuleAndVersionId(String moduleId, String versionId) {
    env = new MockEnvironment("testapp", moduleId, versionId);
    ApiProxy.setEnvironmentForCurrentThread(env);
  }

  private void setupGoodTest(String method, Message.Builder request, Message.Builder response) {
    setupTestImpl(method, request, response.build().toByteArray());
  }

  private void setupTestImpl(String method, Message.Builder request, byte[] response) {
    Future<byte[]> future = immediateFuture(response);
    when(delegate.makeAsyncCall(
            eq(env),
            eq(ModulesServiceImpl.PACKAGE),
            eq(method),
            eq(request.build().toByteArray()),
            notNull()))
        .thenReturn(future);
  }

  private void setupApplicationExceptionTest(
      String method, Message.Builder request, ErrorCode error) {
    setupExceptionTest(method, request, new ApiProxy.ApplicationException(error.getNumber()));
  }

  private void setupExceptionTest(String method, Message.Builder request, Throwable throwable) {
    Future<byte[]> future = immediateFailedFuture(throwable);
    when(delegate.makeAsyncCall(
            eq(env),
            eq(ModulesServiceImpl.PACKAGE),
            eq(method),
            eq(request.build().toByteArray()),
            notNull()))
        .thenReturn(future);
  }

  @Test
  public void testGetCurrentModule_DefaultModule() {
    assertThat(api.getCurrentModule()).isEqualTo("default");
  }

  @Test
  public void testGetCurrentModule_NonDefaultModule() {
    setModuleAndVersionId("module1", "v1.123");
    assertThat(api.getCurrentModule()).isEqualTo("module1");
  }

  @Test
  public void testGetCurrentVersion_DefaultModule() {
    assertThat(api.getCurrentVersion()).isEqualTo("v1");
  }

  @Test
  public void testGetCurrentVersion_NonDefaultModule() {
    setModuleAndVersionId("module1", "v1.123");
    assertThat(api.getCurrentVersion()).isEqualTo("v1");
  }

  @Test
  public void testGetCurrentInstanceId() {
    setModuleAndVersionId("module1", "v1.123");
    env.getAttributes().put(INSTANCE_ID_KEY, "42");
    assertThat(api.getCurrentInstanceId()).isEqualTo("42");
  }

  @Test
  public void testGetCurrentInstanceId_ErrorIfThereIsNoInstance() {
    setModuleAndVersionId("module1", "v1.123");
    ModulesException e = assertThrows(ModulesException.class, () -> api.getCurrentInstanceId());
    assertThat(e).hasMessageThat().contains("Instance id unavailable");
  }

  @Test
  public void testGetModules_MultipleModules() {
    GetModulesResponse.Builder response = GetModulesResponse.newBuilder();
    response.addModule("default");
    response.addModule("module1");
    setupGoodTest("GetModules", GetModulesRequest.newBuilder(), response);
    assertThat(api.getModules()).isEqualTo(ImmutableSet.of("default", "module1"));
  }

  @Test
  public void testGetModules_TransientError() {
    setupApplicationExceptionTest(
        "GetModules", GetModulesRequest.newBuilder(), ErrorCode.TRANSIENT_ERROR);
    assertThrows(ModulesException.class, () -> api.getModules());
  }

  @Test
  public void testGetVersions() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("module1");
    GetVersionsResponse.Builder response = GetVersionsResponse.newBuilder();
    response.addVersion("v1");
    response.addVersion("v2");
    setupGoodTest("GetVersions", request, response);
    assertThat(api.getVersions("module1")).isEqualTo(ImmutableSet.of("v1", "v2"));
  }

  @Test
  public void testGetVersions_current() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    GetVersionsResponse.Builder response = GetVersionsResponse.newBuilder();
    response.addVersion("v1");
    response.addVersion("v2");
    setupGoodTest("GetVersions", request, response);
    assertThat(api.getVersions(null)).isEqualTo(ImmutableSet.of("v1", "v2"));
  }

  @Test
  public void testGetVersions_InvalidModuleError() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetVersions", request, ErrorCode.INVALID_MODULE);
    ModulesException e = assertThrows(ModulesException.class, () -> api.getVersions("bad-module"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetVersions_TransientError() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetVersions", request, ErrorCode.TRANSIENT_ERROR);
    assertThrows(ModulesException.class, () -> api.getVersions("bad-module"));
  }

  @Test
  public void testGetVersions_interruptedException() throws Exception {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("bad-module");
    InterruptedException interruptedException = new InterruptedException("Test interrupt.");
    // We want future.get() to throw InterruptedException, not an ExecutionException wrapping
    // InterruptedException, so we can't use Futures.immediateFailedFuture.
    @SuppressWarnings({"unchecked", "DoNotMock"})
    Future<byte[]> future = mock(Future.class);
    when(future.get()).thenThrow(interruptedException);
    when(delegate.makeAsyncCall(
            eq(env),
            eq(ModulesServiceImpl.PACKAGE),
            eq("GetVersions"),
            eq(request.build().toByteArray()),
            notNull()))
        .thenReturn(future);
    RuntimeException re = assertThrows(RuntimeException.class, () -> api.getVersions("bad-module"));
    assertThat(re).hasMessageThat().isEqualTo("Unexpected failure");
    assertThat(re).hasCauseThat().isEqualTo(interruptedException);
  }

  @Test
  public void testGetVersions_runtimeException() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("bad-module");
    RuntimeException runtimeException = new RuntimeException("test Bad");
    setupExceptionTest("GetVersions", request, runtimeException);
    RuntimeException re = assertThrows(RuntimeException.class, () -> api.getVersions("bad-module"));
    assertThat(re).isEqualTo(runtimeException);
  }

  @Test
  public void testGetVersions_error() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("bad-module");
    Error testError = new Error("test Bad");
    setupExceptionTest("GetVersions", request, testError);
    Error e = assertThrows(Error.class, () -> api.getVersions("bad-module"));
    assertThat(e).isEqualTo(testError);
  }

  @Test
  public void testGetVersions_invalidProtocolException() {
    GetVersionsRequest.Builder request = GetVersionsRequest.newBuilder();
    request.setModule("module1");
    byte[] badResponse = {6, 6, 6};
    setupTestImpl("GetVersions", request, badResponse);
    ModulesException me = assertThrows(ModulesException.class, () -> api.getVersions("module1"));
    assertThat(me).hasMessageThat().isEqualTo("Unexpected failure");
    assertThat(me.getCause()).isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  public void testGetDefaultVersion() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    request.setModule("module1");
    GetDefaultVersionResponse.Builder response = GetDefaultVersionResponse.newBuilder();
    response.setVersion("v1");
    setupGoodTest("GetDefaultVersion", request, response);
    assertThat(api.getDefaultVersion("module1")).isEqualTo("v1");
  }

  @Test
  public void testGetDefaultVersion_current() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    GetDefaultVersionResponse.Builder response = GetDefaultVersionResponse.newBuilder();
    response.setVersion("v1");
    setupGoodTest("GetDefaultVersion", request, response);
    assertThat(api.getDefaultVersion(null)).isEqualTo("v1");
  }

  @Test
  public void testGetDefaultVersion_InvalidModuleError() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetDefaultVersion", request, ErrorCode.INVALID_MODULE);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getDefaultVersion("bad-module"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetDefaultVersion_InvalidVersionError() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetDefaultVersion", request, ErrorCode.INVALID_VERSION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getDefaultVersion("bad-module"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testGetDefaultVersion_UnknownError() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetDefaultVersion", request, ErrorCode.TRANSIENT_ERROR);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getDefaultVersion("bad-module"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown error: '4'");
  }

  @Test
  public void testGetDefaultVersion_UnknownState() {
    GetDefaultVersionRequest.Builder request = GetDefaultVersionRequest.newBuilder();
    request.setModule("bad-module");
    setupApplicationExceptionTest("GetDefaultVersion", request, ErrorCode.UNEXPECTED_STATE);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getDefaultVersion("bad-module"));
    assertThat(e).hasMessageThat().isEqualTo("Unexpected state with method 'GetDefaultVersion'");
  }

  @Test
  public void testGetNumInstances() {
    GetNumInstancesRequest.Builder request = GetNumInstancesRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    GetNumInstancesResponse.Builder response = GetNumInstancesResponse.newBuilder();
    response.setInstances(42);
    setupGoodTest("GetNumInstances", request, response);
    assertThat(api.getNumInstances("module1", "v1")).isEqualTo(42);
  }

  @Test
  public void testGetNumInstances_current() {
    GetNumInstancesRequest.Builder request = GetNumInstancesRequest.newBuilder();
    GetNumInstancesResponse.Builder response = GetNumInstancesResponse.newBuilder();
    response.setInstances(42);
    setupGoodTest("GetNumInstances", request, response);
    assertThat(api.getNumInstances(null, null)).isEqualTo(42);
  }

  @Test
  public void testGetNumInstances_InvalidVersionError() {
    GetNumInstancesRequest.Builder request = GetNumInstancesRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("GetNumInstances", request, ErrorCode.INVALID_VERSION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getNumInstances("bad-module", "v1"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testGetNumInstances_instanceTooBig() {
    GetNumInstancesRequest.Builder request = GetNumInstancesRequest.newBuilder();
    GetNumInstancesResponse.Builder response = GetNumInstancesResponse.newBuilder();
    response.setInstances((long) Integer.MAX_VALUE + 1);
    setupGoodTest("GetNumInstances", request, response);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> api.getNumInstances(null, null));
    assertThat(e).hasMessageThat().isEqualTo("Invalid instances value: 2147483648");
  }

  @Test
  public void testGetNumInstances_instanceTooSmall() {
    GetNumInstancesRequest.Builder request = GetNumInstancesRequest.newBuilder();
    GetNumInstancesResponse.Builder response = GetNumInstancesResponse.newBuilder();
    response.setInstances(-1);
    setupGoodTest("GetNumInstances", request, response);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> api.getNumInstances(null, null));
    assertThat(e).hasMessageThat().isEqualTo("Invalid instances value: -1");
  }

  @Test
  public void testSetNumInstances() {
    SetNumInstancesRequest.Builder request = SetNumInstancesRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    request.setInstances(43);
    setupGoodTest("SetNumInstances", request, SetNumInstancesResponse.newBuilder());
    api.setNumInstances("module1", "v1", 43);
  }

  @Test
  public void testSetNumInstances_current() {
    SetNumInstancesRequest.Builder request = SetNumInstancesRequest.newBuilder();
    request.setInstances(43);
    setupGoodTest("SetNumInstances", request, SetNumInstancesResponse.newBuilder());
    api.setNumInstances(null, null, 43);
  }

  @Test
  public void testSetNumInstances_InvalidVersionError() {
    SetNumInstancesRequest.Builder request = SetNumInstancesRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    request.setInstances(43);
    setupApplicationExceptionTest("SetNumInstances", request, ErrorCode.INVALID_VERSION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.setNumInstances("bad-module", "v1", 43));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testSetNumInstances_TransientError() {
    SetNumInstancesRequest.Builder request = SetNumInstancesRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    request.setInstances(43);
    setupApplicationExceptionTest("SetNumInstances", request, ErrorCode.TRANSIENT_ERROR);
    assertThrows(ModulesException.class, () -> api.setNumInstances("module1", "v1", 43));
  }

  @SuppressWarnings("deprecation")
  private void startVersion(String module, String version) {
    api.startVersion(module, version);
  }

  @Test
  public void testStartVersion() {
    StartModuleRequest.Builder request = StartModuleRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    setupGoodTest("StartModule", request, StartModuleResponse.newBuilder());
    startVersion("module1", "v1");
  }

  @Test
  public void testStartModule_InvalidVersionError() {
    StartModuleRequest.Builder request = StartModuleRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("StartModule", request, ErrorCode.INVALID_VERSION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> startVersion("bad-module", "v1"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testStartModule_UnexpectedStateError() {
    StartModuleRequest.Builder request = StartModuleRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("StartModule", request, ErrorCode.UNEXPECTED_STATE);
    startVersion("bad-module", "v1");
  }

  @Test
  public void testStartModule_TransientError() {
    StartModuleRequest.Builder request = StartModuleRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    setupApplicationExceptionTest("StartModule", request, ErrorCode.TRANSIENT_ERROR);
    assertThrows(ModulesException.class, () -> startVersion("module1", "v1"));
  }

  private void stopVersion(String module, String version) {
    api.stopVersion(module, version);
  }

  @Test
  public void testStopVersion() {
    StopModuleRequest.Builder request = StopModuleRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    setupGoodTest("StopModule", request, StopModuleResponse.newBuilder());
    stopVersion("module1", "v1");
  }

  @Test
  public void testStopVersion_current() {
    StopModuleRequest.Builder request = StopModuleRequest.newBuilder();
    setupGoodTest("StopModule", request, StopModuleResponse.newBuilder());
    stopVersion(null, null);
  }

  @Test
  public void testStopVersion_InvalidVersionError() {
    StopModuleRequest.Builder request = StopModuleRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("StopModule", request, ErrorCode.INVALID_VERSION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> stopVersion("bad-module", "v1"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testStopVersion_UnexpectedStateError() {
    StopModuleRequest.Builder request = StopModuleRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("StopModule", request, ErrorCode.UNEXPECTED_STATE);
    stopVersion("bad-module", "v1");
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testStopVersion_TransientError() {
    StopModuleRequest.Builder request = StopModuleRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    setupApplicationExceptionTest("StopModule", request, ErrorCode.TRANSIENT_ERROR);
    assertThrows(ModulesException.class, () -> api.stopVersion("module1", "v1"));
  }

  String getVersionHostname(String module, String version) {
    return api.getVersionHostname(module, version);
  }

  @Test
  public void testGetVersionHostname() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    GetHostnameResponse.Builder response = GetHostnameResponse.newBuilder();
    response.setHostname("abc");
    setupGoodTest("GetHostname", request, response);
    assertThat(getVersionHostname("module1", "v1")).isEqualTo("abc");
  }

  @Test
  public void testGetVersionHostname_InvalidModuleError() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    setupApplicationExceptionTest("GetHostname", request, ErrorCode.INVALID_MODULE);
    ModulesException e =
        assertThrows(ModulesException.class, () -> getVersionHostname("bad-module", "v1"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @SuppressWarnings("deprecation")
  String getInstanceHostname(String module, String version, String instance) {
    return api.getInstanceHostname(module, version, instance);
  }

  @Test
  public void testGetInstanceHostname() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    request.setModule("module1");
    request.setVersion("v1");
    request.setInstance("34");
    GetHostnameResponse.Builder response = GetHostnameResponse.newBuilder();
    response.setHostname("abc");
    setupGoodTest("GetHostname", request, response);
    assertThat(getInstanceHostname("module1", "v1", "34")).isEqualTo("abc");
  }

  @Test
  public void testGetInstanceHostname_InvalidModuleError() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    request.setInstance("34");
    setupApplicationExceptionTest("GetHostname", request, ErrorCode.INVALID_MODULE);
    ModulesException e =
        assertThrows(ModulesException.class, () -> getInstanceHostname("bad-module", "v1", "34"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetVersionHostname_nullModuleAndVersion() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    GetHostnameResponse.Builder response = GetHostnameResponse.newBuilder();
    response.setHostname("abc");
    setupGoodTest("GetHostname", request, response);
    assertThat(getVersionHostname(null, null)).isEqualTo("abc");
  }

  @Test
  public void testGetInstanceHostname_nullModuleAndVersion() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    GetHostnameResponse.Builder response = GetHostnameResponse.newBuilder();
    request.setInstance("34");
    response.setHostname("abc");
    setupGoodTest("GetHostname", request, response);
    assertThat(getInstanceHostname(null, null, "34")).isEqualTo("abc");
  }

  @Test
  public void testGetInstanceHostname_InvalidInstance() {
    GetHostnameRequest.Builder request = GetHostnameRequest.newBuilder();
    request.setModule("bad-module");
    request.setVersion("v1");
    request.setInstance("34");
    setupApplicationExceptionTest("GetHostname", request, ErrorCode.INVALID_INSTANCES);
    ModulesException e =
        assertThrows(ModulesException.class, () -> getInstanceHostname("bad-module", "v1", "34"));
    assertThat(e).hasMessageThat().isEqualTo("Invalid instance");
  }
}
