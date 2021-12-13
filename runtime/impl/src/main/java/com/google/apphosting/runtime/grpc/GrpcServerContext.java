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

import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.protobuf.MessageLite;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import java.time.Duration;

/**
 * Server context for gRPC calls using the {@link com.google.apphosting.runtime.anyrpc.AnyRpcPlugin}
 * framework. An implementation of, for example, {@link
 * com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface} will receive an instance
 * of this object on each received RPC call, which it will use to inform the particular RPC
 * implementation (here, gRPC) of the result of the requested operation.
 *
 */
class GrpcServerContext<ResponseT extends MessageLite> implements AnyRpcServerContext {
  private final Class<ResponseT> responseClass;
  private final StreamObserver<ResponseT> streamObserver;
  private final long startTimeMillis;
  private final long globalId;
  private final Context context;

  GrpcServerContext(Class<ResponseT> responseClass, StreamObserver<ResponseT> streamObserver) {
    this.responseClass = responseClass;
    this.streamObserver = streamObserver;
    this.startTimeMillis = System.currentTimeMillis();
    this.globalId = idGenerator.nextId();
    this.context = Context.current();
    // Grab the Context now, because it will not be available in other threads
    // that might call getDeadline() later.
  }

  @Override
  public void finishWithResponse(MessageLite response) {
    ResponseT typedResponse = responseClass.cast(response);
    streamObserver.onNext(typedResponse);
    streamObserver.onCompleted();
  }

  @Override
  public void finishWithAppError(int appErrorCode, String errorDetail) {
    GrpcApplicationError appError = new GrpcApplicationError("AppError", appErrorCode, errorDetail);
    Status status = appError.encode();
    streamObserver.onError(new StatusException(status));
  }

  @Override
  public Duration getTimeRemaining() {
    Deadline deadline = context.getDeadline();
    if (deadline == null) {
      return Duration.ofNanos(Long.MAX_VALUE);
    } else {
      return Duration.ofNanos(deadline.timeRemaining(NANOSECONDS));
    }
  }

  @Override
  public long getStartTimeMillis() {
    return startTimeMillis;
  }

  @Override
  public long getGlobalId() {
    // TODO: figure out if we can propagate this from the client.
    return globalId;
  }

  private static final IdGenerator idGenerator = new IdGenerator();

  private static class IdGenerator {
    private long lastId;

    /**
     * Returns an id that is unique to this JVM. It will usually equal the current timestamp, but
     * it is guaranteed to be monotonically increasing.
     */
    synchronized long nextId() {
      long id = System.currentTimeMillis();
      if (id <= lastId) {
        id = lastId + 1;
      }
      lastId = id;
      return id;
    }
  }
}
