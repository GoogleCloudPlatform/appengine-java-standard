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

import com.google.apphosting.base.protos.AppinfoPb;
import com.google.apphosting.base.protos.CloneControllerGrpc.CloneControllerImplBase;
import com.google.apphosting.base.protos.ClonePb;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.EvaluationRuntimeGrpc.EvaluationRuntimeImplBase;
import com.google.apphosting.base.protos.ModelClonePb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.anyrpc.AnyRpcPlugin;
import com.google.apphosting.runtime.anyrpc.CloneControllerServerInterface;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;
import com.google.common.base.Preconditions;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Optional;

/**
 * RPC plugin for gRPC.
 *
 */
public class GrpcPlugin extends AnyRpcPlugin {
  private static final int MAX_REQUEST_BODY_SIZE = 50 * 1024 * 1024; // 50 MB

  private Optional<Integer> optionalServerPort = Optional.empty();
  private Server server;

  public GrpcPlugin() {}

  @Override
  public void initialize(int serverPort) {
    if (serverPort != 0) {
      Preconditions.checkArgument(serverPort > 0, "Server port cannot be negative: %s", serverPort);
      this.optionalServerPort = Optional.of(serverPort);
    }
  }

  @Override
  public void startServer(
      EvaluationRuntimeServerInterface evaluationRuntime,
      CloneControllerServerInterface cloneController) {
    if (!optionalServerPort.isPresent()) {
      throw new IllegalStateException("No server port has been specified");
    }
    EvaluationRuntimeImplBase evaluationRuntimeServer =
        new EvaluationRuntimeServer(evaluationRuntime);
    CloneControllerImplBase cloneControllerServer = new CloneControllerServer(cloneController);
    ServerInterceptor exceptionInterceptor = new ExceptionInterceptor();
    ServerServiceDefinition evaluationRuntimeService =
        ServerInterceptors.intercept(evaluationRuntimeServer, exceptionInterceptor);
    ServerServiceDefinition cloneControllerService =
        ServerInterceptors.intercept(cloneControllerServer, exceptionInterceptor);
    server =
        NettyServerBuilder.forPort(optionalServerPort.get())
            .maxInboundMessageSize(MAX_REQUEST_BODY_SIZE)
            .addService(evaluationRuntimeService)
            .addService(cloneControllerService)
            .build();
    try {
      server.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getServerPort() {
    return optionalServerPort.get();
  }

  @Override
  public boolean serverStarted() {
    return server != null && !server.isShutdown() && !server.isTerminated();
  }

  @Override
  public void blockUntilShutdown() {
    try {
      server.awaitTermination();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stopServer() {
    if (serverStarted()) {
      server.shutdown();
    }
  }

  @Override
  public void shutdown() {
    stopServer();
  }

  @Override
  public Runnable traceContextPropagating(Runnable runnable) {
    // TODO: Figure out how to do trace context propagation with gRPC.
    return runnable;
  }

  private static class EmptyGrpcServerContext extends GrpcServerContext<EmptyMessage> {
    EmptyGrpcServerContext(StreamObserver<EmptyMessage> streamObserver) {
      super(EmptyMessage.class, streamObserver);
    }
  }

  /**
   * Derive a {@link Status} from the given exception. If the exception is a
   * {@link StatusRuntimeException}, this method returns its contained
   * {@code Status}. Otherwise, it returns a {@link Status#INTERNAL} whose description includes
   * information about the exception. Currently this information is the exception's
   * {@code toString()} plus the first line of its stack trace.
   */
  private static Status statusFromException(RuntimeException e) {
    if (e instanceof StatusRuntimeException) {
      return ((StatusRuntimeException) e).getStatus();
    } else {
      String description = e.toString();
      StackTraceElement[] stack = e.getStackTrace();
      if (stack.length > 0) {
        description += ", at " + stack[0];
      }
      return Status.INTERNAL.withDescription(description).withCause(e);
    }
  }

  /**
   * Interceptor that catches exceptions while handling an operation. The exception causes the
   * call to be closed with an error status that includes information about the exception. This
   * interceptor is not designed to be used with streaming calls: in the simple request/response
   * calls that we currently have, the logic for handling an operation is triggered at the
   * "half-close" stage of the call, so catching the exception there is enough.
   *
   * <p>Interception is a little bit tricky. The original call handler can be wrapped by one or
   * more interceptors, making a chain. When a call arrives on the service, the first interceptor
   * in the chain (the outermost one in the wrapping) is asked to return a ServerCall.Listener that
   * will be informed of the various stages of the call. It is expected to call the next interceptor
   * in the chain and get back that interceptor's listener. It can then either return that listener
   * or wrap it in its own listener. So a chain of wrapped interceptors produces a chain of wrapped
   * listeners every time there is a call. Then the listeners are invoked as the stages of the call
   * proceed. Like the interceptors, each listener is expected to forward to its wrapped listener
   * in the usual case, and perform whatever extra logic it might need before and/or after that
   * forwarding.
   */
  private static class ExceptionInterceptor implements ServerInterceptor {
    @Override
    public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
        final ServerCall<RequestT, ResponseT> call,
        Metadata metadata,
        ServerCallHandler<RequestT, ResponseT> next) {
      ServerCall.Listener<RequestT> nextListener = next.startCall(call, metadata);
      return new SimpleForwardingServerCallListener<RequestT>(nextListener) {
        @Override
        public void onHalfClose() {
          try {
            super.onHalfClose();
          } catch (RuntimeException e) {
            call.close(statusFromException(e), new Metadata());
          }
        }
      };
    }
  }

  private static class EvaluationRuntimeServer extends EvaluationRuntimeImplBase {
    private final EvaluationRuntimeServerInterface evaluationRuntime;

    EvaluationRuntimeServer(EvaluationRuntimeServerInterface evaluationRuntime) {
      this.evaluationRuntime = evaluationRuntime;
    }

    @Override
    public void handleRequest(
        RuntimePb.UPRequest request,
        StreamObserver<RuntimePb.UPResponse> streamObserver) {
      GrpcServerContext<RuntimePb.UPResponse> serverContext =
          new GrpcServerContext<>(RuntimePb.UPResponse.class, streamObserver);
      evaluationRuntime.handleRequest(serverContext, request);
    }

    @Override
    public void addAppVersion(
        AppinfoPb.AppInfo appInfo,
        StreamObserver<EmptyMessage> streamObserver) {
      evaluationRuntime.addAppVersion(new EmptyGrpcServerContext(streamObserver), appInfo);
    }

    @Override
    public void deleteAppVersion(
        AppinfoPb.AppInfo appInfo,
        StreamObserver<EmptyMessage> streamObserver) {
      evaluationRuntime.deleteAppVersion(new EmptyGrpcServerContext(streamObserver), appInfo);
    }
  }

  private static class CloneControllerServer extends CloneControllerImplBase {
    private final CloneControllerServerInterface cloneController;

    CloneControllerServer(CloneControllerServerInterface cloneController) {
      this.cloneController = cloneController;
    }

    @Override
    public void waitForSandbox(
        EmptyMessage emptyMessage, StreamObserver<EmptyMessage> streamObserver) {
      cloneController.waitForSandbox(
          new EmptyGrpcServerContext(streamObserver), EmptyMessage.getDefaultInstance());
    }

    @Override
    public void applyCloneSettings(
        ClonePb.CloneSettings cloneSettings, StreamObserver<EmptyMessage> streamObserver) {
      cloneController.applyCloneSettings(
          new EmptyGrpcServerContext(streamObserver), cloneSettings);
    }

    @Override
    public void sendDeadline(
        ModelClonePb.DeadlineInfo deadlineInfo, StreamObserver<EmptyMessage> streamObserver) {
      cloneController.sendDeadline(new EmptyGrpcServerContext(streamObserver), deadlineInfo);
    }

    @Override
    public void getPerformanceData(
        ModelClonePb.PerformanceDataRequest request,
        StreamObserver<ClonePb.PerformanceData> streamObserver) {
      GrpcServerContext<ClonePb.PerformanceData> serverContext =
          new GrpcServerContext<>(ClonePb.PerformanceData.class, streamObserver);
      cloneController.getPerformanceData(serverContext, request);
    }
  }
}
