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

package com.google.apphosting.runtime.jetty.http;

import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.MessageLite;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class LocalRpcContext<M extends MessageLite> implements AnyRpcServerContext {
  // We just dole out sequential ids here so we can tell requests apart in the logs.
  private static final AtomicLong globalIds = new AtomicLong();

  private final Class<M> responseMessageClass;
  private final long startTimeMillis;
  private final Duration timeRemaining;
  private final SettableFuture<M> futureResponse = SettableFuture.create();
  private final long globalId = globalIds.getAndIncrement();

  public LocalRpcContext(Class<M> responseMessageClass) {
    this(responseMessageClass, Duration.ofNanos((long) Double.MAX_VALUE));
  }

  public LocalRpcContext(Class<M> responseMessageClass, Duration timeRemaining) {
    this.responseMessageClass = responseMessageClass;
    this.startTimeMillis = System.currentTimeMillis();
    this.timeRemaining = timeRemaining;
  }

  @Override
  public void finishWithResponse(MessageLite response) {
    futureResponse.set(responseMessageClass.cast(response));
  }

  public M getResponse() throws ExecutionException, InterruptedException {
    return futureResponse.get();
  }

  @Override
  public void finishWithAppError(int appErrorCode, String errorDetail) {
    String message = "AppError: code " + appErrorCode + "; errorDetail " + errorDetail;
    futureResponse.setException(new RuntimeException(message));
  }

  @Override
  public Duration getTimeRemaining() {
    return timeRemaining;
  }

  @Override
  public long getGlobalId() {
    return globalId;
  }

  @Override
  public long getStartTimeMillis() {
    return startTimeMillis;
  }
}
