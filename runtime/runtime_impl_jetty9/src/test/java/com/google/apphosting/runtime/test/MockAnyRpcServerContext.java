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

package com.google.apphosting.runtime.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.MessageLite;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** A mock for {@link AnyRpcServerContext}. */
public class MockAnyRpcServerContext implements AnyRpcServerContext {
  private static final Random random = new Random();

  private final Duration timeRemaining;
  private final long startTimeMillis = System.currentTimeMillis();
  private final long globalId = random.nextLong();
  private final SettableFuture<Result> resultFuture = SettableFuture.create();

  public MockAnyRpcServerContext(Duration timeRemaining) {
    this.timeRemaining = timeRemaining;
  }

  @Override
  public void finishWithResponse(MessageLite response) {
    resultFuture.set(Result.ofSuccess(response));
  }

  @Override
  public void finishWithAppError(int appErrorCode, String errorDetail) {
    resultFuture.set(Result.ofFailure(appErrorCode, errorDetail));
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

  // The following methods are a subset of those in MockRpcServerContext.

  public void waitForCompletion() {
    try {
      resultFuture.get(60, SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  public MessageLite assertSuccess() {
    assertWithMessage("RPC should have completed").that(resultFuture.isDone()).isTrue();
    Result result;
    try {
      result = resultFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssertionError(e);
    }
    switch (result.getKind()) {
      case SUCCESS:
        return result.success();
      case FAILURE:
        throw new AssertionError(result.failure());
    }
    throw new AssertionError("Mystery kind of Result");
  }

  public void assertAppError(int errorCode) {
    assertWithMessage("RPC should have completed").that(resultFuture.isDone()).isTrue();
    Result result;
    try {
      result = resultFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssertionError(e);
    }
    switch (result.getKind()) {
      case SUCCESS:
        throw new AssertionError("RPC succeeded but should not have: " + result.success());
      case FAILURE:
        assertThat(result.failure().code()).isEqualTo(errorCode);
    }
  }

  @AutoValue
  abstract static class Failure {
    abstract int code();

    abstract String message();

    static Failure of(int code, String message) {
      return new AutoValue_MockAnyRpcServerContext_Failure(code, message);
    }
  }

  @AutoOneOf(Result.Kind.class)
  abstract static class Result {
    enum Kind {
      SUCCESS,
      FAILURE
    }

    abstract Kind getKind();

    abstract MessageLite success();

    abstract Failure failure();

    static Result ofSuccess(MessageLite message) {
      return AutoOneOf_MockAnyRpcServerContext_Result.success(message);
    }

    static Result ofFailure(int code, String message) {
      return AutoOneOf_MockAnyRpcServerContext_Result.failure(Failure.of(code, message));
    }
  }
}
