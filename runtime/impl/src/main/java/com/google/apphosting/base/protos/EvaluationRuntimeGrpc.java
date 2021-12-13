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

/**
 *
 *
 * <pre>
 * A service for evaluating HTTP requests. This service is implemented by
 * all the App Engine runtimes. Note that all our existing sandbox/VM
 * environments only support a single app version at a time, despite the
 * multi-app-version capability implied by this interface.
 * TODO: Consider changing the interface to not suggest that it can
 * support multiple app versions. This would probably make the code less
 * confusing. Related to that, there's no reason why the AppServer-side of
 * the runtime needs to inherit from this interface. To the extent that it
 * really does need similar methods, it can define its own local (non-RPC)
 * versions of those interfaces.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class EvaluationRuntimeGrpc {

  private EvaluationRuntimeGrpc() {}

  public static final String SERVICE_NAME = "apphosting.EvaluationRuntime";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.RuntimePb.UPRequest,
          com.google.apphosting.base.protos.RuntimePb.UPResponse>
      getHandleRequestMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HandleRequest",
      requestType = com.google.apphosting.base.protos.RuntimePb.UPRequest.class,
      responseType = com.google.apphosting.base.protos.RuntimePb.UPResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.RuntimePb.UPRequest,
          com.google.apphosting.base.protos.RuntimePb.UPResponse>
      getHandleRequestMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.RuntimePb.UPRequest,
            com.google.apphosting.base.protos.RuntimePb.UPResponse>
        getHandleRequestMethod;
    if ((getHandleRequestMethod = EvaluationRuntimeGrpc.getHandleRequestMethod) == null) {
      synchronized (EvaluationRuntimeGrpc.class) {
        if ((getHandleRequestMethod = EvaluationRuntimeGrpc.getHandleRequestMethod) == null) {
          EvaluationRuntimeGrpc.getHandleRequestMethod =
              getHandleRequestMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.RuntimePb.UPRequest,
                          com.google.apphosting.base.protos.RuntimePb.UPResponse>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HandleRequest"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.RuntimePb.UPRequest
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.RuntimePb.UPResponse
                                  .getDefaultInstance()))
                      .setSchemaDescriptor(
                          new EvaluationRuntimeMethodDescriptorSupplier("HandleRequest"))
                      .build();
        }
      }
    }
    return getHandleRequestMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.AppinfoPb.AppInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getAddAppVersionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AddAppVersion",
      requestType = com.google.apphosting.base.protos.AppinfoPb.AppInfo.class,
      responseType = com.google.apphosting.base.protos.EmptyMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.AppinfoPb.AppInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getAddAppVersionMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.AppinfoPb.AppInfo,
            com.google.apphosting.base.protos.EmptyMessage>
        getAddAppVersionMethod;
    if ((getAddAppVersionMethod = EvaluationRuntimeGrpc.getAddAppVersionMethod) == null) {
      synchronized (EvaluationRuntimeGrpc.class) {
        if ((getAddAppVersionMethod = EvaluationRuntimeGrpc.getAddAppVersionMethod) == null) {
          EvaluationRuntimeGrpc.getAddAppVersionMethod =
              getAddAppVersionMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.AppinfoPb.AppInfo,
                          com.google.apphosting.base.protos.EmptyMessage>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AddAppVersion"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.AppinfoPb.AppInfo
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setSchemaDescriptor(
                          new EvaluationRuntimeMethodDescriptorSupplier("AddAppVersion"))
                      .build();
        }
      }
    }
    return getAddAppVersionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.AppinfoPb.AppInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getDeleteAppVersionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeleteAppVersion",
      requestType = com.google.apphosting.base.protos.AppinfoPb.AppInfo.class,
      responseType = com.google.apphosting.base.protos.EmptyMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<
          com.google.apphosting.base.protos.AppinfoPb.AppInfo,
          com.google.apphosting.base.protos.EmptyMessage>
      getDeleteAppVersionMethod() {
    io.grpc.MethodDescriptor<
            com.google.apphosting.base.protos.AppinfoPb.AppInfo,
            com.google.apphosting.base.protos.EmptyMessage>
        getDeleteAppVersionMethod;
    if ((getDeleteAppVersionMethod = EvaluationRuntimeGrpc.getDeleteAppVersionMethod) == null) {
      synchronized (EvaluationRuntimeGrpc.class) {
        if ((getDeleteAppVersionMethod = EvaluationRuntimeGrpc.getDeleteAppVersionMethod) == null) {
          EvaluationRuntimeGrpc.getDeleteAppVersionMethod =
              getDeleteAppVersionMethod =
                  io.grpc.MethodDescriptor
                      .<com.google.apphosting.base.protos.AppinfoPb.AppInfo,
                          com.google.apphosting.base.protos.EmptyMessage>
                          newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteAppVersion"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.AppinfoPb.AppInfo
                                  .getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.ProtoUtils.marshaller(
                              com.google.apphosting.base.protos.EmptyMessage.getDefaultInstance()))
                      .setSchemaDescriptor(
                          new EvaluationRuntimeMethodDescriptorSupplier("DeleteAppVersion"))
                      .build();
        }
      }
    }
    return getDeleteAppVersionMethod;
  }

  /** Creates a new async stub that supports all call types for the service */
  public static EvaluationRuntimeStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeStub>() {
          @java.lang.Override
          public EvaluationRuntimeStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new EvaluationRuntimeStub(channel, callOptions);
          }
        };
    return EvaluationRuntimeStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static EvaluationRuntimeBlockingStub newBlockingStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeBlockingStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeBlockingStub>() {
          @java.lang.Override
          public EvaluationRuntimeBlockingStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new EvaluationRuntimeBlockingStub(channel, callOptions);
          }
        };
    return EvaluationRuntimeBlockingStub.newStub(factory, channel);
  }

  /** Creates a new ListenableFuture-style stub that supports unary calls on the service */
  public static EvaluationRuntimeFutureStub newFutureStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeFutureStub> factory =
        new io.grpc.stub.AbstractStub.StubFactory<EvaluationRuntimeFutureStub>() {
          @java.lang.Override
          public EvaluationRuntimeFutureStub newStub(
              io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new EvaluationRuntimeFutureStub(channel, callOptions);
          }
        };
    return EvaluationRuntimeFutureStub.newStub(factory, channel);
  }

  /**
   *
   *
   * <pre>
   * A service for evaluating HTTP requests. This service is implemented by
   * all the App Engine runtimes. Note that all our existing sandbox/VM
   * environments only support a single app version at a time, despite the
   * multi-app-version capability implied by this interface.
   * TODO: Consider changing the interface to not suggest that it can
   * support multiple app versions. This would probably make the code less
   * confusing. Related to that, there's no reason why the AppServer-side of
   * the runtime needs to inherit from this interface. To the extent that it
   * really does need similar methods, it can define its own local (non-RPC)
   * versions of those interfaces.
   * </pre>
   */
  public abstract static class EvaluationRuntimeImplBase implements io.grpc.BindableService {

    /**
     *
     *
     * <pre>
     * Given information an application and an HTTP request, execute the
     * request and prepare a response for the user.
     * </pre>
     */
    public void handleRequest(
        com.google.apphosting.base.protos.RuntimePb.UPRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.RuntimePb.UPResponse>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getHandleRequestMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Add an app version to the runtime.
     * </pre>
     */
    public void addAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getAddAppVersionMethod(), responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Delete an app version from the runtime.
     * NOTE: Here, AppInfo will be an AppInfo-lite.
     * </pre>
     */
    public void deleteAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(
          getDeleteAppVersionMethod(), responseObserver);
    }

    @java.lang.Override
    public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
              getHandleRequestMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.RuntimePb.UPRequest,
                      com.google.apphosting.base.protos.RuntimePb.UPResponse>(
                      this, METHODID_HANDLE_REQUEST)))
          .addMethod(
              getAddAppVersionMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.AppinfoPb.AppInfo,
                      com.google.apphosting.base.protos.EmptyMessage>(
                      this, METHODID_ADD_APP_VERSION)))
          .addMethod(
              getDeleteAppVersionMethod(),
              io.grpc.stub.ServerCalls.asyncUnaryCall(
                  new MethodHandlers<
                      com.google.apphosting.base.protos.AppinfoPb.AppInfo,
                      com.google.apphosting.base.protos.EmptyMessage>(
                      this, METHODID_DELETE_APP_VERSION)))
          .build();
    }
  }

  /**
   *
   *
   * <pre>
   * A service for evaluating HTTP requests. This service is implemented by
   * all the App Engine runtimes. Note that all our existing sandbox/VM
   * environments only support a single app version at a time, despite the
   * multi-app-version capability implied by this interface.
   * TODO: Consider changing the interface to not suggest that it can
   * support multiple app versions. This would probably make the code less
   * confusing. Related to that, there's no reason why the AppServer-side of
   * the runtime needs to inherit from this interface. To the extent that it
   * really does need similar methods, it can define its own local (non-RPC)
   * versions of those interfaces.
   * </pre>
   */
  public static final class EvaluationRuntimeStub
      extends io.grpc.stub.AbstractAsyncStub<EvaluationRuntimeStub> {
    private EvaluationRuntimeStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EvaluationRuntimeStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EvaluationRuntimeStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Given information an application and an HTTP request, execute the
     * request and prepare a response for the user.
     * </pre>
     */
    public void handleRequest(
        com.google.apphosting.base.protos.RuntimePb.UPRequest request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.RuntimePb.UPResponse>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHandleRequestMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Add an app version to the runtime.
     * </pre>
     */
    public void addAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAddAppVersionMethod(), getCallOptions()),
          request,
          responseObserver);
    }

    /**
     *
     *
     * <pre>
     * Delete an app version from the runtime.
     * NOTE: Here, AppInfo will be an AppInfo-lite.
     * </pre>
     */
    public void deleteAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request,
        io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>
            responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteAppVersionMethod(), getCallOptions()),
          request,
          responseObserver);
    }
  }

  /**
   *
   *
   * <pre>
   * A service for evaluating HTTP requests. This service is implemented by
   * all the App Engine runtimes. Note that all our existing sandbox/VM
   * environments only support a single app version at a time, despite the
   * multi-app-version capability implied by this interface.
   * TODO: Consider changing the interface to not suggest that it can
   * support multiple app versions. This would probably make the code less
   * confusing. Related to that, there's no reason why the AppServer-side of
   * the runtime needs to inherit from this interface. To the extent that it
   * really does need similar methods, it can define its own local (non-RPC)
   * versions of those interfaces.
   * </pre>
   */
  public static final class EvaluationRuntimeBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<EvaluationRuntimeBlockingStub> {
    private EvaluationRuntimeBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EvaluationRuntimeBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EvaluationRuntimeBlockingStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Given information an application and an HTTP request, execute the
     * request and prepare a response for the user.
     * </pre>
     */
    public com.google.apphosting.base.protos.RuntimePb.UPResponse handleRequest(
        com.google.apphosting.base.protos.RuntimePb.UPRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHandleRequestMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Add an app version to the runtime.
     * </pre>
     */
    public com.google.apphosting.base.protos.EmptyMessage addAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAddAppVersionMethod(), getCallOptions(), request);
    }

    /**
     *
     *
     * <pre>
     * Delete an app version from the runtime.
     * NOTE: Here, AppInfo will be an AppInfo-lite.
     * </pre>
     */
    public com.google.apphosting.base.protos.EmptyMessage deleteAppVersion(
        com.google.apphosting.base.protos.AppinfoPb.AppInfo request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteAppVersionMethod(), getCallOptions(), request);
    }
  }

  /**
   *
   *
   * <pre>
   * A service for evaluating HTTP requests. This service is implemented by
   * all the App Engine runtimes. Note that all our existing sandbox/VM
   * environments only support a single app version at a time, despite the
   * multi-app-version capability implied by this interface.
   * TODO: Consider changing the interface to not suggest that it can
   * support multiple app versions. This would probably make the code less
   * confusing. Related to that, there's no reason why the AppServer-side of
   * the runtime needs to inherit from this interface. To the extent that it
   * really does need similar methods, it can define its own local (non-RPC)
   * versions of those interfaces.
   * </pre>
   */
  public static final class EvaluationRuntimeFutureStub
      extends io.grpc.stub.AbstractFutureStub<EvaluationRuntimeFutureStub> {
    private EvaluationRuntimeFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected EvaluationRuntimeFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new EvaluationRuntimeFutureStub(channel, callOptions);
    }

    /**
     *
     *
     * <pre>
     * Given information an application and an HTTP request, execute the
     * request and prepare a response for the user.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.RuntimePb.UPResponse>
        handleRequest(com.google.apphosting.base.protos.RuntimePb.UPRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHandleRequestMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Add an app version to the runtime.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.EmptyMessage>
        addAppVersion(com.google.apphosting.base.protos.AppinfoPb.AppInfo request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAddAppVersionMethod(), getCallOptions()), request);
    }

    /**
     *
     *
     * <pre>
     * Delete an app version from the runtime.
     * NOTE: Here, AppInfo will be an AppInfo-lite.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<
            com.google.apphosting.base.protos.EmptyMessage>
        deleteAppVersion(com.google.apphosting.base.protos.AppinfoPb.AppInfo request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteAppVersionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HANDLE_REQUEST = 0;
  private static final int METHODID_ADD_APP_VERSION = 1;
  private static final int METHODID_DELETE_APP_VERSION = 2;

  private static final class MethodHandlers<Req, Resp>
      implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final EvaluationRuntimeImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(EvaluationRuntimeImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HANDLE_REQUEST:
          serviceImpl.handleRequest(
              (com.google.apphosting.base.protos.RuntimePb.UPRequest) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.RuntimePb.UPResponse>)
                  responseObserver);
          break;
        case METHODID_ADD_APP_VERSION:
          serviceImpl.addAppVersion(
              (com.google.apphosting.base.protos.AppinfoPb.AppInfo) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>)
                  responseObserver);
          break;
        case METHODID_DELETE_APP_VERSION:
          serviceImpl.deleteAppVersion(
              (com.google.apphosting.base.protos.AppinfoPb.AppInfo) request,
              (io.grpc.stub.StreamObserver<com.google.apphosting.base.protos.EmptyMessage>)
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

  private abstract static class EvaluationRuntimeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier,
          io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    EvaluationRuntimeBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.google.apphosting.base.protos.RuntimeRpc.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("EvaluationRuntime");
    }
  }

  private static final class EvaluationRuntimeFileDescriptorSupplier
      extends EvaluationRuntimeBaseDescriptorSupplier {
    EvaluationRuntimeFileDescriptorSupplier() {}
  }

  private static final class EvaluationRuntimeMethodDescriptorSupplier
      extends EvaluationRuntimeBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    EvaluationRuntimeMethodDescriptorSupplier(String methodName) {
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
      synchronized (EvaluationRuntimeGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor =
              result =
                  io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                      .setSchemaDescriptor(new EvaluationRuntimeFileDescriptorSupplier())
                      .addMethod(getHandleRequestMethod())
                      .addMethod(getAddAppVersionMethod())
                      .addMethod(getDeleteAppVersionMethod())
                      .build();
        }
      }
    }
    return result;
  }
}
