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

import com.google.apphosting.base.protos.ClonePb.ApiPackageDeadlines;
import com.google.apphosting.base.protos.ClonePb.CloneSettings;
import com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints;
import com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest;
import com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse;
import com.google.apphosting.base.protos.ClonePb.PerformanceData;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo;
import com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest;
import com.google.apphosting.base.protos.SourceContext;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.apphosting.runtime.anyrpc.CloneControllerServerInterface;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

/**
 * {@code CloneControllerImpl} implements the {@link CloneControllerServerInterface} RPC interface.
 */
public class CloneControllerImpl implements CloneControllerServerInterface {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final Callback callback;
  private final ApiDeadlineOracle deadlineOracle;
  private final RequestManager requestManager;
  private final ByteBuffer hotspotPerformanceData;
  private final CloudDebuggerAgentWrapper cloudDebuggerAgent;

  public CloneControllerImpl(
      Callback callback,
      ApiDeadlineOracle deadlineOracle,
      RequestManager requestManager,
      ByteBuffer hotspotPerformanceData,
      CloudDebuggerAgentWrapper cloudDebuggerAgent) {
    this.callback = callback;
    this.deadlineOracle = deadlineOracle;
    this.requestManager = requestManager;
    this.hotspotPerformanceData = hotspotPerformanceData;
    this.cloudDebuggerAgent = cloudDebuggerAgent;
  }

  /**
   * Obsolete operation. This was used by an earlier sandboxing scheme, now obsolete.
   */
  @Override
  public void waitForSandbox(AnyRpcServerContext rpc, EmptyMessage unused) {
    rpc.finishWithAppError(1, "waitForSandbox is unimplemented");
  }

  /**
   * Applies the specified {@link CloneSettings} received from the
   * AppServer.  These settings cannot be known at clone start-up
   * because they may vary by application.
   */
  @Override
  public void applyCloneSettings(AnyRpcServerContext rpc, CloneSettings settings) {
    logger.atWarning().log("applyCloneSettings");
    try {
      // Historically we translated the max_virtual_memory_mb and max_cpu_seconds fields from the
      // CloneSettings into setrlimit calls here. But it no longer makes sense to depend on the
      // runtime to impose these limits. Instead, the containing sandbox should do it.

      if (settings.hasMaxOutstandingApiRpcs()) {
        // This must be less than --clone_max_outstanding_api_rpcs
        // because we specify that value when creating the Stubby
        // channel.
        requestManager.setMaxOutstandingApiRpcs(settings.getMaxOutstandingApiRpcs());
      }

      // Now apply any package deadline overrides that we received.
      for (ApiPackageDeadlines deadline : settings.getApiCallDeadlinesList()) {
        if (deadline.hasDefaultDeadlineS()) {
          deadlineOracle.addPackageDefaultDeadline(
              deadline.getApiPackage(), deadline.getDefaultDeadlineS());
        }
        if (deadline.hasMaxDeadlineS()) {
          deadlineOracle.addPackageMaxDeadline(
              deadline.getApiPackage(), deadline.getMaxDeadlineS());
        }
      }
      for (ApiPackageDeadlines deadline : settings.getOfflineApiCallDeadlinesList()) {
        if (deadline.hasDefaultDeadlineS()) {
          deadlineOracle.addOfflinePackageDefaultDeadline(
              deadline.getApiPackage(), deadline.getDefaultDeadlineS());
        }
        if (deadline.hasMaxDeadlineS()) {
          deadlineOracle.addOfflinePackageMaxDeadline(
              deadline.getApiPackage(), deadline.getMaxDeadlineS());
        }
      }

      // Switch network services to use the App Engine Socket API.
      callback.divertNetworkServices();
      rpc.finishWithResponse(EmptyMessage.getDefaultInstance());
      logger.atWarning().log("applyCloneSettings done");
    } catch (RuntimeException ex) {
      logger.atSevere().withCause(ex).log("oh noes");
      throw ex;
    }
  }

  @Override
  public void sendDeadline(final AnyRpcServerContext rpc, DeadlineInfo deadline) {
    logger.atInfo().log("Got a sendDeadline RPC.");
    requestManager.sendDeadline(deadline.getSecurityTicket(), deadline.getHard());
    rpc.finishWithResponse(EmptyMessage.getDefaultInstance());
  }

  @Override
  public void getPerformanceData(AnyRpcServerContext rpc, PerformanceDataRequest req) {
    logger.atInfo().log("Got a getPerformanceData RPC with type %s", req.getType());
    PerformanceData.Builder data = PerformanceData.newBuilder().setType(req.getType());
    if (hotspotPerformanceData != null) {
      PerformanceData.Entry.Builder entry =
          PerformanceData.Entry.newBuilder()
              .setFormat(PerformanceData.Format.JAVA_HOTSPOT_HSPERFDATA);
      ByteBuffer bb = hotspotPerformanceData.duplicate();
      bb.position(0);
      bb.limit(bb.capacity());
      entry.setPayload(ByteString.copyFrom(bb));
      data.addEntries(entry);
    }
    rpc.finishWithResponse(data.build());
  }

  @Override
  public void updateActiveBreakpoints(AnyRpcServerContext rpc, CloudDebuggerBreakpoints request) {
    // Give the updated list of breakpoints to the debuglet.
    // TODO: make this a List<ByteString> to avoid copying. That will require
    // updating JNI code in gae_jvmti_agent.cc.
    byte[][] requestData = new byte[request.getBreakpointDataCount()][];
    for (int i = 0; i < requestData.length; ++i) {
      requestData[i] = request.getBreakpointData(i).toByteArray();
    }

    cloudDebuggerAgent.setActiveBreakpoints(requestData);

    // Query any breakpoint updates it might have (typically that would be errors for invalid
    // breakpoints).
    CloudDebuggerBreakpoints.Builder response = CloudDebuggerBreakpoints.newBuilder();
    byte[][] responseData = cloudDebuggerAgent.dequeueBreakpointUpdates();
    if (responseData != null) {
      for (byte[] breakpointData : responseData) {
        response.addBreakpointData(ByteString.copyFrom(breakpointData));
      }
    }

    rpc.finishWithResponse(response.build());
  }

  @Override
  public void getDebuggeeInfo(AnyRpcServerContext rpc, DebuggeeInfoRequest request) {
    DebuggeeInfoResponse.Builder response = DebuggeeInfoResponse.newBuilder();

    // AppVersionId is required and expected to have AppId and VersionId separated by '/'
    String[] parts = request.getAppVersionId().split("/");
    if (parts.length == 2) {
      SourceContext sourceContext = getSourceContext(parts[0], parts[1]);
      if (sourceContext != null) {
        response.getDebuggeeInfoBuilder().setSourceContext(sourceContext);
      }
    } else {
      logger.atWarning().log("invalid AppVersionId : %s", request.getAppVersionId());
    }

    rpc.finishWithResponse(response.build());
  }

  SourceContext getSourceContext(String appId, String versionId) {
    AppVersion appVersion = callback.getAppVersion(appId, versionId);

    return (appVersion == null) ? null : appVersion.getSourceContext();
  }

  /**
   * Callback interface for rpc-specific and sandbox-specific functionality to be abstracted
   * over in this class.
   */
  public interface Callback {
    /**
     * Start re-routing the socket API in the JRE through the GAE socket API.
     */
    void divertNetworkServices();

    AppVersion getAppVersion(String appId, String versionId);
  }
}
