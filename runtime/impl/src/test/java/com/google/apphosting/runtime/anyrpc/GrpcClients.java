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

import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.CloneControllerGrpc;
import com.google.apphosting.base.protos.ClonePb;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.EvaluationRuntimeGrpc;
import com.google.apphosting.base.protos.ModelClonePb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.anyrpc.ClientInterfaces.CloneControllerClient;
import com.google.apphosting.runtime.anyrpc.ClientInterfaces.EvaluationRuntimeClient;
import com.google.apphosting.runtime.grpc.CallbackStreamObserver;
import com.google.apphosting.runtime.grpc.GrpcClientContext;
import io.grpc.Channel;
import io.grpc.stub.StreamObserver;

/**
 * gRPC implementations of the RPC client interfaces in {@link ClientInterfaces}. These are purely
 * for test purposes, since the real runtime is never a client of these RPC services. But having
 * both client and server implementations allows us to test round-trip behaviour.
 *
 */
class GrpcClients {
  // There are no instances of this class.
  private GrpcClients() {}

  static class GrpcEvaluationRuntimeClient implements EvaluationRuntimeClient {
    private final Channel channel;

    GrpcEvaluationRuntimeClient(Channel channel) {
      this.channel = channel;
    }

    @Override
    public void handleRequest(
        AnyRpcClientContext ctx,
        RuntimePb.UPRequest req,
        AnyRpcCallback<RuntimePb.UPResponse> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<RuntimePb.UPResponse> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, EvaluationRuntimeGrpc.getHandleRequestMethod(), req, streamObserver);
    }

    @Override
    public void addAppVersion(
        AnyRpcClientContext ctx, AppinfoPb.AppInfo req, AnyRpcCallback<EmptyMessage> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<EmptyMessage> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, EvaluationRuntimeGrpc.getAddAppVersionMethod(), req, streamObserver);
    }

    @Override
    public void deleteAppVersion(
        AnyRpcClientContext ctx, AppinfoPb.AppInfo req, AnyRpcCallback<EmptyMessage> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<EmptyMessage> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, EvaluationRuntimeGrpc.getDeleteAppVersionMethod(), req, streamObserver);
    }
  }

  static class GrpcCloneControllerClient implements CloneControllerClient {
    private static final EmptyMessage GRPC_EMPTY_MESSAGE =
        EmptyMessage.getDefaultInstance();

    private final Channel channel;

    GrpcCloneControllerClient(Channel channel) {
      this.channel = channel;
    }

    @Override
    public void waitForSandbox(
        AnyRpcClientContext ctx,
        EmptyMessage req,
        AnyRpcCallback<EmptyMessage> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<EmptyMessage> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, 
          CloneControllerGrpc.getWaitForSandboxMethod(), 
          GRPC_EMPTY_MESSAGE, 
          streamObserver);
    }

    @Override
    public void applyCloneSettings(
        AnyRpcClientContext ctx,
        ClonePb.CloneSettings req,
        AnyRpcCallback<EmptyMessage> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<EmptyMessage> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, CloneControllerGrpc.getApplyCloneSettingsMethod(), req, streamObserver);
    }

    @Override
    public void sendDeadline(
        AnyRpcClientContext ctx,
        ModelClonePb.DeadlineInfo req,
        AnyRpcCallback<EmptyMessage> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<EmptyMessage> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, CloneControllerGrpc.getSendDeadlineMethod(), req, streamObserver);
    }

    @Override
    public void getPerformanceData(
        AnyRpcClientContext ctx,
        ModelClonePb.PerformanceDataRequest req,
        AnyRpcCallback<ClonePb.PerformanceData> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<ClonePb.PerformanceData> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel,
          CloneControllerGrpc.getGetPerformanceDataMethod(),
          req,
          streamObserver);
    }

    @Override
    public void updateActiveBreakpoints(
        AnyRpcClientContext ctx,
        ClonePb.CloudDebuggerBreakpoints req,
        AnyRpcCallback<ClonePb.CloudDebuggerBreakpoints> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<ClonePb.CloudDebuggerBreakpoints> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, CloneControllerGrpc.getUpdateActiveBreakpointsMethod(), req, streamObserver);
    }

    @Override
    public void getDebuggeeInfo(
        AnyRpcClientContext ctx,
        ClonePb.DebuggeeInfoRequest req,
        AnyRpcCallback<ClonePb.DebuggeeInfoResponse> callback) {
      GrpcClientContext grpcContext = (GrpcClientContext) ctx;
      StreamObserver<ClonePb.DebuggeeInfoResponse> streamObserver =
          CallbackStreamObserver.of(grpcContext, callback);
      grpcContext.call(
          channel, CloneControllerGrpc.getGetDebuggeeInfoMethod(), req, streamObserver);
    }
  }
}
