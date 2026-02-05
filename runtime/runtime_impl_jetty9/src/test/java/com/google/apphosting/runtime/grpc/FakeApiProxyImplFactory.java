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

package com.google.apphosting.runtime.grpc;

import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.ApiDeadlineOracle;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.AppVersion;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.TraceWriter;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.timer.CpuRatioTimer;
import com.google.apphosting.runtime.timer.TimerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Support for creating ApiProxyImpl instances.
 *
 */
public class FakeApiProxyImplFactory {
  public static ApiProxyImpl newApiProxyImpl(APIHostClientInterface apiHostClient) {
    return ApiProxyImpl.builder()
        .setApiHost(apiHostClient)
        .setDeadlineOracle(
            new ApiDeadlineOracle.Builder().initDeadlineMap().build())
        .setByteCountBeforeFlushing(8192)
        .setMaxLogFlushTime(Duration.ofSeconds(5))
        .build();
  }

  private static AppVersion fakeAppVersion() {
    return AppVersion.builder()
        .setAppInfo(AppInfo.getDefaultInstance())
        .setPublicRoot("")
        .build();
  }

  private static CpuRatioTimer fakeCpuRatioTimer() {
    ThreadGroup myThreadGroup = Thread.currentThread().getThreadGroup();
    long fakeCyclesPerSecond = 1_000_000_000L;
    return new TimerFactory(fakeCyclesPerSecond).getCpuRatioTimer(myThreadGroup);
  }

  public static ApiProxyImpl.EnvironmentImpl fakeEnvironment(
      ApiProxyImpl apiProxyImpl, String securityTicket) {
    return fakeEnvironment(apiProxyImpl, securityTicket, null);
  }

  public static ApiProxyImpl.EnvironmentImpl fakeEnvironment(
      ApiProxyImpl apiProxyImpl, String securityTicket, TraceWriter traceWriter) {
    Semaphore outstandingApiRpcSemaphore = new Semaphore(1);
    List<Future<?>> asyncFutures = new ArrayList<>();
    RuntimePb.UPRequest request =
        RuntimePb.UPRequest.newBuilder().setSecurityTicket(securityTicket).buildPartial();
    return apiProxyImpl.createEnvironment(
        fakeAppVersion(),
        request,
        new MutableUpResponse(),
        traceWriter,
        fakeCpuRatioTimer(),
        null,
        asyncFutures,
        outstandingApiRpcSemaphore,
        null,
        null,
        Long.MAX_VALUE);
  }
}
