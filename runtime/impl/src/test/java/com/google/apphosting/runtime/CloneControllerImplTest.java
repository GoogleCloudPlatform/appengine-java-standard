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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.protos.ClonePb.PerformanceData;
import com.google.apphosting.base.protos.GitSourceContext;
import com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest;
import com.google.apphosting.base.protos.SourceContext;
import com.google.apphosting.runtime.test.MockAnyRpcServerContext;
import com.google.common.io.ByteStreams;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the CloneControllerImpl class.
 *
 */
@RunWith(JUnit4.class)
public class CloneControllerImplTest {
  private static final double API_DEADLINE = 10.0;
  private static final Duration RPC_DEADLINE = Duration.ofSeconds(3);
  private static final long MAX_RUNTIME_LOG_PER_REQUEST = 3000L * 1024L;
  private static final long HARD_DEADLINE_DELAY = 250;
  private static final long SOFT_DEADLINE_DELAY = 750;
  private static final int HSPERFDATA_SIZE = 32768;
  private static final int FAKE_HSPERFDATA_SIZE = 100;
  private static final long CYCLES_PER_SECOND = 2333414000L;

  private final JavaRuntime javaRuntime = mock(JavaRuntime.class);
  private final AppVersion appVersion = mock(AppVersion.class);
  private final SourceContext sourceContext = SourceContext.newBuilder()
      .setGit(GitSourceContext.newBuilder().setUrl("http://foo/bar"))
      .build();

  @Before
  public void setUp() {
    when(javaRuntime.findAppVersion("app1", "1.1")).thenReturn(appVersion);
    when(appVersion.getSourceContext()).thenReturn(sourceContext);
  }

  @Test
  public void testPerformanceDataDisabled() {
    CloneControllerImpl cloneController = createCloneController(null);
    MockAnyRpcServerContext rpc = createRpc();
    cloneController.getPerformanceData(rpc, PerformanceDataRequest.getDefaultInstance());
    PerformanceData data = (PerformanceData) rpc.assertSuccess();
    assertThat(data.getEntriesCount()).isEqualTo(0);
  }

  @Test
  public void testPerformanceDataEnabled() {
    byte[] fakePerformanceData = new byte[FAKE_HSPERFDATA_SIZE];
    for (int i = 0; i < FAKE_HSPERFDATA_SIZE; ++i) {
      fakePerformanceData[i] = (byte) i;
    }
    ByteBuffer perfData = ByteBuffer.wrap(fakePerformanceData).order(ByteOrder.LITTLE_ENDIAN);
    CloneControllerImpl cloneController = createCloneController(perfData);
    MockAnyRpcServerContext rpc = createRpc();
    cloneController.getPerformanceData(rpc, PerformanceDataRequest.getDefaultInstance());
    PerformanceData data = (PerformanceData) rpc.assertSuccess();
    assertThat(data.getEntriesCount()).isEqualTo(1);
    PerformanceData.Entry entry = data.getEntries(0);
    assertThat(entry.getFormat()).isEqualTo(PerformanceData.Format.JAVA_HOTSPOT_HSPERFDATA);
    byte[] payload = entry.getPayload().toByteArray();
    assertThat(payload).isEqualTo(fakePerformanceData);
  }

  @Test
  public void testPerformanceDataEnabledAgainstRealPerfData() throws Exception {
    // This is just a file that I  fished out of /tmp/hsperfdata_emcmanus at some point.
    // It's from a Maven process.
    byte[] perfData;
    try (InputStream in = getClass().getResourceAsStream("hsperf.data")) {
      perfData = ByteStreams.toByteArray(in);
    }
    ByteBuffer realPerformanceData = ByteBuffer.wrap(perfData);
    CloneControllerImpl cloneController = createCloneController(realPerformanceData);
    MockAnyRpcServerContext rpc = createRpc();
    cloneController.getPerformanceData(rpc, PerformanceDataRequest.getDefaultInstance());
    PerformanceData data = (PerformanceData) rpc.assertSuccess();
    assertThat(data.getEntriesCount()).isEqualTo(1);
    PerformanceData.Entry entry = data.getEntries(0);
    assertThat(entry.getFormat()).isEqualTo(PerformanceData.Format.JAVA_HOTSPOT_HSPERFDATA);
    byte[] payload = entry.getPayload().toByteArray();
    assertThat(payload).hasLength(HSPERFDATA_SIZE);
    // Check the magic header (4 bytes).
    assertThat(Arrays.copyOf(payload, 4))
        .isEqualTo(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xC0, (byte) 0xC0});
  }

  private CloneControllerImpl createCloneController(ByteBuffer hotspotPerformanceData) {
    return new CloneControllerImpl(
        javaRuntime.new CloneControllerImplCallback(),
        new ApiDeadlineOracle.Builder()
            .initDeadlineMap(API_DEADLINE, "", 0.0, "")
            .initOfflineDeadlineMap(API_DEADLINE, "", 0.0, "")
            .build(),
        createRequestManager(false, true, false),
        hotspotPerformanceData);
  }

  private RequestManager createRequestManager(
      boolean disableTimers, boolean terminateClones, boolean interruptOnSoftDeadline) {
    return RequestManager.builder()
        .setSoftDeadlineDelay(SOFT_DEADLINE_DELAY)
        .setHardDeadlineDelay(HARD_DEADLINE_DELAY)
        .setDisableDeadlineTimers(disableTimers)
        .setRuntimeLogSink(Optional.of(new RuntimeLogSink(MAX_RUNTIME_LOG_PER_REQUEST)))
        .setApiProxyImpl(ApiProxyImpl.builder().build())
        .setMaxOutstandingApiRpcs(10)
        .setThreadStopTerminatesClone(terminateClones)
        .setInterruptFirstOnSoftDeadline(interruptOnSoftDeadline)
        .setCyclesPerSecond(CYCLES_PER_SECOND)
        .setWaitForDaemonRequestThreads(true)
        .build();
  }

  private MockAnyRpcServerContext createRpc() {
    return new MockAnyRpcServerContext(RPC_DEADLINE);
  }
}
