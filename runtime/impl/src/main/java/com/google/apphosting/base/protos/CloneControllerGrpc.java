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

package com.google.apphosting.base.protos;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/** */
@io.grpc.stub.annotations.GrpcGenerated
public final class CloneControllerGrpc {

  private CloneControllerGrpc() {}

  public static final String SERVICE_NAME = "apphosting.CloneController";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.EmptyMessage,
          com.google.apphosting.base.protos.EmptyMessage>
      getWaitForSandboxMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WaitForSandbox",
      requestType = com.google.apphosting.base.protos.EmptyMessage.class,
      responseType = com.google.apphosting.base.protos.EmptyMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.EmptyMessage,
          com.google.apphosting.base.protos.EmptyMessage>
      getWaitForSandboxMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.EmptyMessage,
            com.google.apphosting.base.protos.EmptyMessage>
        getWaitForSandboxMethod;
    if ((getWaitForSandboxMethod = CloneControllerGrpc.getWaitForSandboxMethod) == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getWaitForSandboxMethod = CloneControllerGrpc.getWaitForSandboxMethod) == null) {
          CloneControllerGrpc.getWaitForSandboxMethod =
              getWaitForSandboxMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.EmptyMessage,
                          com.google.apphosting.base.protos.EmptyMessage>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WaitForSandbox"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("WaitForSandbox"))
                      .build();
        }
      }
    }
    return getWaitForSandboxMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.CloneSettings,
          com.google.apphosting.base.protos.EmptyMessage>
      getApplyCloneSettingsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ApplyCloneSettings",
      requestType = com.google.apphosting.base.protos.ClonePb.CloneSettings.class,
      responseType = com.google.apphosting.base.protos.EmptyMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.CloneSettings,
          com.google.apphosting.base.protos.EmptyMessage>
      getApplyCloneSettingsMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.ClonePb.CloneSettings,
            com.google.apphosting.base.protos.EmptyMessage>
        getApplyCloneSettingsMethod;
    if ((getApplyCloneSettingsMethod = CloneControllerGrpc.getApplyCloneSettingsMethod) == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getApplyCloneSettingsMethod = CloneControllerGrpc.getApplyCloneSettingsMethod)
            == null) {
          CloneControllerGrpc.getApplyCloneSettingsMethod =
              getApplyCloneSettingsMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.ClonePb.CloneSettings,
                          com.google.apphosting.base.protos.EmptyMessage>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ApplyCloneSettings"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.CloneSettings
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("ApplyCloneSettings"))
                      .build();
        }
      }
    }
    return getApplyCloneSettingsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getSendDeadlineMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendDeadline",
      requestType = com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo.class,
      responseType = com.google.apphosting.base.protos.EmptyMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getSendDeadlineMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo,
            com.google.apphosting.base.protos.EmptyMessage>
        getSendDeadlineMethod;
    if ((getSendDeadlineMethod = CloneControllerGrpc.getSendDeadlineMethod) == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getSendDeadlineMethod = CloneControllerGrpc.getSendDeadlineMethod) == null) {
          CloneControllerGrpc.getSendDeadlineMethod =
              getSendDeadlineMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo,
                          com.google.apphosting.base.protos.EmptyMessage>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendDeadline"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("SendDeadline"))
                      .build();
        }
      }
    }
    return getSendDeadlineMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest,
          com.google.apphosting.base.protos.ClonePb.PerformanceData>
      getGetPerformanceDataMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPerformanceData",
      requestType = com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest.class,
      responseType = com.google.apphosting.base.protos.ClonePb.PerformanceData.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest,
          com.google.apphosting.base.protos.ClonePb.PerformanceData>
      getGetPerformanceDataMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest,
            com.google.apphosting.base.protos.ClonePb.PerformanceData>
        getGetPerformanceDataMethod;
    if ((getGetPerformanceDataMethod = CloneControllerGrpc.getGetPerformanceDataMethod) == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getGetPerformanceDataMethod = CloneControllerGrpc.getGetPerformanceDataMethod)
            == null) {
          CloneControllerGrpc.getGetPerformanceDataMethod =
              getGetPerformanceDataMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest,
                          com.google.apphosting.base.protos.ClonePb.PerformanceData>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPerformanceData"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.PerformanceData
                                  .getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("GetPerformanceData"))
                      .build();
        }
      }
    }
    return getGetPerformanceDataMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints,
          com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
      getUpdateActiveBreakpointsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UpdateActiveBreakpoints",
      requestType = com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints.class,
      responseType = com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints,
          com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
      getUpdateActiveBreakpointsMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints,
            com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
        getUpdateActiveBreakpointsMethod;
    if ((getUpdateActiveBreakpointsMethod = CloneControllerGrpc.getUpdateActiveBreakpointsMethod)
        == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getUpdateActiveBreakpointsMethod =
                CloneControllerGrpc.getUpdateActiveBreakpointsMethod)
            == null) {
          CloneControllerGrpc.getUpdateActiveBreakpointsMethod =
              getUpdateActiveBreakpointsMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints,
                          com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(
                          generateFullMethodName(SERVICE_NAME, "UpdateActiveBreakpoints"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints
                                  .getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("UpdateActiveBreakpoints"))
                      .build();
        }
      }
    }
    return getUpdateActiveBreakpointsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest,
          com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
      getGetDebuggeeInfoMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDebuggeeInfo",
      requestType = com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest.class,
      responseType = com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest,
          com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
      getGetDebuggeeInfoMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest,
            com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
        getGetDebuggeeInfoMethod;
    if ((getGetDebuggeeInfoMethod = CloneControllerGrpc.getGetDebuggeeInfoMethod) == null) {
      synchronized (CloneControllerGrpc.class) {
        if ((getGetDebuggeeInfoMethod = CloneControllerGrpc.getGetDebuggeeInfoMethod) == null) {
          CloneControllerGrpc.getGetDebuggeeInfoMethod =
              getGetDebuggeeInfoMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest,
                          com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDebuggeeInfo"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse
                                  .getDefaultInstance()))
                      .setSchemaDescriptor(
                          new CloneControllerMethodDescriptorSupplier("GetDebuggeeInfo"))
                      .build();
        }
      }
    }
    return getGetDebuggeeInfoMethod;
  }

  /** Creates a new async stub that supports all call types for the service */
  public static CloneControllerStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CloneControllerStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<CloneControllerStub>() {
          @java.lang.Override
          public CloneControllerStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CloneControllerStub(channel, callOptions);
          }
        };
    return CloneControllerStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CloneControllerBlockingStub newBlockingStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CloneControllerBlockingStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<CloneControllerBlockingStub>() {
          @java.lang.Override
          public CloneControllerBlockingStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CloneControllerBlockingStub(channel, callOptions);
          }
        };
    return CloneControllerBlockingStub.newStub(factory, channel);
  }

  /** Creates a new ListenableFuture-style stub that supports unary calls on the service */
  public static CloneControllerFutureStub newFutureStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CloneControllerFutureStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<CloneControllerFutureStub>() {
          @java.lang.Override
          public CloneControllerFutureStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CloneControllerFutureStub(channel, callOptions);
          }
        };
    return CloneControllerFutureStub.newStub(factory, channel);
  }

  /** */
  public abstract static class CloneControllerImplBase implements io.grpc.BindableService {

    /**
     *
     *
     * <pre>
     * Asks the Clone to put itself into the stopped state, by sending
     * itself a SIGSTOP when it is safe to do so. The Clone will be
     * Sandboxed and resume from this point.
     * </pre>
     */
    public void waitForSandbox(
        com.google.apphosting.base.protos.EmptyMessage request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getWaitForSandboxMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Updates per-app settings for this clone.
     * </pre>
     */
    public void applyCloneSettings(
        com.google.apphosting.base.protos.ClonePb.CloneSettings request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getApplyCloneSettingsMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Notifies the clone that the soft or hard deadline for an active request
     * has expired.
     * </pre>
     */
    public void sendDeadline(
        com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getSendDeadlineMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Deprecated.
     * </pre>
     */
    public void getPerformanceData(
        com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.ClonePb.PerformanceData>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getGetPerformanceDataMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Updates a list of Cloud Debugger breakpoints on a clone.
     * </pre>
     */
    public void updateActiveBreakpoints(
        com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints request,
        io.grpc.stub.StreamObserver<
                com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getUpdateActiveBreakpointsMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Gets source context of the clone app.
     * </pre>
     */
    public void getDebuggeeInfo(
        com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getGetDebuggeeInfoMethod(), responseObserver);
    }

    @java.lang.Override
    public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
              getWaitForSandboxMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.EmptyMessage,
                      com.google.apphosting.base.protos.EmptyMessage>(
                      this, METHODID_WAIT_FOR_SANDBOX)))
          .addMethod(
              getApplyCloneSettingsMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.ClonePb.CloneSettings,
                      com.google.apphosting.base.protos.EmptyMessage>(
                      this, METHODID_APPLY_CLONE_SETTINGS)))
          .addMethod(
              getSendDeadlineMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo,
                      com.google.apphosting.base.protos.EmptyMessage>(
                      this, METHODID_SEND_DEADLINE)))
          .addMethod(
              getGetPerformanceDataMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest,
                      com.google.apphosting.base.protos.ClonePb.PerformanceData>(
                      this, METHODID_GET_PERFORMANCE_DATA)))
          .addMethod(
              getUpdateActiveBreakpointsMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints,
                      com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>(
                      this, METHODID_UPDATE_ACTIVE_BREAKPOINTS)))
          .addMethod(
              getGetDebuggeeInfoMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest,
                      com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>(
                      this, METHODID_GET_DEBUGGEE_INFO)))
          .build();
    }
  }

  /** */
  public static final class CloneControllerStub
      extends io.grpc.stub.AbstractAsyncStub<CloneControllerStub> {
    private CloneControllerStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CloneControllerStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CloneControllerStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Asks the Clone to put itself into the stopped state, by sending
     * itself a SIGSTOP when it is safe to do so. The Clone will be
     * Sandboxed and resume from this point.
     * </pre>
     */
    public void waitForSandbox(
        com.google.apphosting.base.protos.EmptyMessage request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getWaitForSandboxMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Updates per-app settings for this clone.
     * </pre>
     */
    public void applyCloneSettings(
        com.google.apphosting.base.protos.ClonePb.CloneSettings request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getApplyCloneSettingsMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Notifies the clone that the soft or hard deadline for an active request
     * has expired.
     * </pre>
     */
    public void sendDeadline(
        com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendDeadlineMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Deprecated.
     * </pre>
     */
    public void getPerformanceData(
        com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.ClonePb.PerformanceData>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPerformanceDataMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Updates a list of Cloud Debugger breakpoints on a clone.
     * </pre>
     */
    public void updateActiveBreakpoints(
        com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints request,
        io.grpc.stub.StreamObserver<
                com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUpdateActiveBreakpointsMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Gets source context of the clone app.
     * </pre>
     */
    public void getDebuggeeInfo(
        com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetDebuggeeInfoMethod(), getCallOptions()),
          request,
          responseObserver);
    }
  }

  /** */
  public static final class CloneControllerBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<CloneControllerBlockingStub> {
    private CloneControllerBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CloneControllerBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CloneControllerBlockingStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Asks the Clone to put itself into the stopped state, by sending
     * itself a SIGSTOP when it is safe to do so. The Clone will be
     * Sandboxed and resume from this point.
     * </pre>
     */
    public com.google.apphosting.base.protos.EmptyMessage waitForSandbox(
        com.google.apphosting.base.protos.EmptyMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getWaitForSandboxMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Updates per-app settings for this clone.
     * </pre>
     */
    public com.google.apphosting.base.protos.EmptyMessage applyCloneSettings(
        com.google.apphosting.base.protos.ClonePb.CloneSettings request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getApplyCloneSettingsMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Notifies the clone that the soft or hard deadline for an active request
     * has expired.
     * </pre>
     */
    public com.google.apphosting.base.protos.EmptyMessage sendDeadline(
        com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendDeadlineMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Deprecated.
     * </pre>
     */
    public com.google.apphosting.base.protos.ClonePb.PerformanceData getPerformanceData(
        com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPerformanceDataMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Updates a list of Cloud Debugger breakpoints on a clone.
     * </pre>
     */
    public com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints
        updateActiveBreakpoints(
            com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUpdateActiveBreakpointsMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Gets source context of the clone app.
     * </pre>
     */
    public com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse getDebuggeeInfo(
        com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetDebuggeeInfoMethod(), getCallOptions(), request);
    }
  }

  /** */
  public static final class CloneControllerFutureStub
      extends io.grpc.stub.AbstractFutureStub<CloneControllerFutureStub> {
    private CloneControllerFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CloneControllerFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CloneControllerFutureStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Asks the Clone to put itself into the stopped state, by sending
     * itself a SIGSTOP when it is safe to do so. The Clone will be
     * Sandboxed and resume from this point.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.EmptyMessage>
        waitForSandbox(com.google.apphosting.base.protos.EmptyMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getWaitForSandboxMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Updates per-app settings for this clone.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.EmptyMessage>
        applyCloneSettings(com.google.apphosting.base.protos.ClonePb.CloneSettings request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getApplyCloneSettingsMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Notifies the clone that the soft or hard deadline for an active request
     * has expired.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.EmptyMessage>
        sendDeadline(com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendDeadlineMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Deprecated.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.ClonePb.PerformanceData>
        getPerformanceData(
            com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPerformanceDataMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Updates a list of Cloud Debugger breakpoints on a clone.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>
        updateActiveBreakpoints(
            com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUpdateActiveBreakpointsMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Gets source context of the clone app.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>
        getDebuggeeInfo(com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetDebuggeeInfoMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_WAIT_FOR_SANDBOX = 0;
  private static final int METHODID_APPLY_CLONE_SETTINGS = 1;
  private static final int METHODID_SEND_DEADLINE = 2;
  private static final int METHODID_GET_PERFORMANCE_DATA = 3;
  private static final int METHODID_UPDATE_ACTIVE_BREAKPOINTS = 4;
  private static final int METHODID_GET_DEBUGGEE_INFO = 5;

  private static final class MethodHandlers<Req, Resp>
      implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final CloneControllerImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(CloneControllerImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_WAIT_FOR_SANDBOX:
          serviceImpl.waitForSandbox(
              (com.google.apphosting.base.protos.EmptyMessage) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>)
                  responseObserver);
          break;
        case METHODID_APPLY_CLONE_SETTINGS:
          serviceImpl.applyCloneSettings(
              (com.google.apphosting.base.protos.ClonePb.CloneSettings) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>)
                  responseObserver);
          break;
        case METHODID_SEND_DEADLINE:
          serviceImpl.sendDeadline(
              (com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>)
                  responseObserver);
          break;
        case METHODID_GET_PERFORMANCE_DATA:
          serviceImpl.getPerformanceData(
              (com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest) request,
              (io.grpc.stub.StreamObserver<
                      com.google.apphosting.base.protos.ClonePb.PerformanceData>)
                  responseObserver);
          break;
        case METHODID_UPDATE_ACTIVE_BREAKPOINTS:
          serviceImpl.updateActiveBreakpoints(
              (com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints) request,
              (io.grpc.stub.StreamObserver<
                      com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints>)
                  responseObserver);
          break;
        case METHODID_GET_DEBUGGEE_INFO:
          serviceImpl.getDebuggeeInfo(
              (com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest) request,
              (io.grpc.stub.StreamObserver<
                      com.google.apphosting.base.protos.ClonePb.DebuggeeInfoResponse>)
                  responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private abstract static class CloneControllerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier,
          io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CloneControllerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.google.apphosting.base.protos.ModelClonePb.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("CloneController");
    }
  }

  private static final class CloneControllerFileDescriptorSupplier
      extends CloneControllerBaseDescriptorSupplier {
    CloneControllerFileDescriptorSupplier() {}
  }

  private static final class CloneControllerMethodDescriptorSupplier
      extends CloneControllerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    CloneControllerMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (CloneControllerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor =
              result =
                  io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                      .setSchemaDescriptor(new CloneControllerFileDescriptorSupplier())
                      .addMethod(getWaitForSandboxMethod())
                      .addMethod(getApplyCloneSettingsMethod())
                      .addMethod(getSendDeadlineMethod())
                      .addMethod(getGetPerformanceDataMethod())
                      .addMethod(getUpdateActiveBreakpointsMethod())
                      .addMethod(getGetDebuggeeInfoMethod())
                      .build();
        }
      }
    }
    return result;
  }
}
