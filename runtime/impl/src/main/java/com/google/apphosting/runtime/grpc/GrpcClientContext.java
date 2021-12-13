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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.runtime.anyrpc.AnyRpcClientContext;
import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import java.time.Clock;
import java.util.Optional;

/**
 * An {@link AnyRpcClientContext} that will record the details of a gRPC call.
 *
 */
public class GrpcClientContext implements AnyRpcClientContext {
  private final Clock clock;

  private Optional<Long> deadlineNanos = Optional.empty();
  private int applicationError;
  private String errorDetail;
  private StatusProto status = StatusProto.getDefaultInstance();
  private Throwable exception;
  private ClientCall<?, ?> currentCall;
  private long currentCallStartTimeMillis;

  public GrpcClientContext(Clock clock) {
    this.clock = clock;
  }

  public <ReqT, RespT> void call(
      Channel channel,
      MethodDescriptor<ReqT, RespT> method,
      ReqT request,
      StreamObserver<RespT> responseObserver) {
    Preconditions.checkState(currentCall == null);
    ClientCall<ReqT, RespT> clientCall = channel.newCall(method, getCallOptions());
    currentCall = clientCall;
    currentCallStartTimeMillis = clock.millis();
    ClientCalls.asyncUnaryCall(clientCall, request, responseObserver);
  }

  private CallOptions getCallOptions() {
    CallOptions callOptions = CallOptions.DEFAULT;
    if (deadlineNanos.isPresent()) {
      callOptions = callOptions.withDeadlineAfter(deadlineNanos.get(), NANOSECONDS);
    }
    return callOptions;
  }

  @Override
  public long getStartTimeMillis() {
    return currentCallStartTimeMillis;
  }

  // TODO: figure out how to make this work properly.
  private static final int UNKNOWN_ERROR_CODE = 1;
  private static final int INTERNAL_CANONICAL_CODE = 13;
  private static final int INTERNAL_CODE = 3;
  private static final int DEADLINE_EXCEEDED_CODE = 4;
  private static final int CANCELLED_CODE = 6;

  @Override
  public Throwable getException() {
    return exception;
  }

  void setException(Throwable exception) {
    io.grpc.Status grpcStatus = io.grpc.Status.fromThrowable(exception);
    Optional<GrpcApplicationError> maybeAppError = GrpcApplicationError.decode(grpcStatus);
    if (maybeAppError.isPresent()) {
      GrpcApplicationError appError = maybeAppError.get();
      applicationError = appError.appErrorCode;
      errorDetail = appError.errorDetail;
      status = StatusProto.newBuilder()
          .setSpace(appError.namespace)
          .setCode(appError.appErrorCode)
          .setCanonicalCode(appError.appErrorCode)
          .setMessage(appError.errorDetail)
          .build();
    } else {
      int code;
      int canonicalCode;
      switch (grpcStatus.getCode()) {
        case DEADLINE_EXCEEDED:
          canonicalCode = code = DEADLINE_EXCEEDED_CODE;
          break;
        case CANCELLED:
          canonicalCode = code = CANCELLED_CODE;
          break;
        case INTERNAL:
          code = INTERNAL_CODE;
          canonicalCode = INTERNAL_CANONICAL_CODE;
          break;
        default:
          canonicalCode = code = UNKNOWN_ERROR_CODE;
          break;
      }
      applicationError = 0;
      errorDetail = exception.toString();
      status = StatusProto.newBuilder()
          .setSpace("RPC")
          .setCode(code)
          .setCanonicalCode(canonicalCode)
          .setMessage(errorDetail)
          .build();
      this.exception = exception;
    }
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
  public void setDeadline(double seconds) {
    Preconditions.checkArgument(seconds >= 0);
    double nanos = 1_000_000_000 * seconds;
    Preconditions.checkArgument(nanos <= Long.MAX_VALUE);
    // If the nanos value is more than this, it means that the deadline was more than 292 years,
    // so we are justified in throwing an exception.
    this.deadlineNanos = Optional.of((long) nanos);
  }

  @Override
  public void startCancel() {
    currentCall.cancel("GrpcClientContext.cancel() called", null);
  }
}
