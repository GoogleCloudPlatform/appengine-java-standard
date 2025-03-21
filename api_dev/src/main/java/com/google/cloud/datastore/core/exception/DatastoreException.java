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

package com.google.cloud.datastore.core.exception;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.apphosting.datastore.DatastoreV3Pb.Error.ErrorCode;
import com.google.cloud.datastore.logs.ProblemCode;
import org.jspecify.annotations.Nullable;

// TODO: This class is used outside Datastore.
// Those uses should probably instead use some sort of client variant.
/**
 * Base class for exceptions thrown by {@code Datastore} implementations.
 *
 * <p>All of the datastore's exception handling is based on the assumption that all exceptions
 * reported by datastore code are {@code DatastoreException}s, and that those are sanitized and
 * appropriate to report to users in RPC application error codes and error detail messages.
 *
 * <p>That's pretty important, since many of the exceptions that the datastore handles internally -
 * {@code MegastoreException}, {@code RpcException}, and miscellaneous unchecked exceptions - have
 * internal details that we don't want to display to users. We need to be sure that we sanitize all
 * error messages that users could see.
 *
 * <p>We do that by writing all the user-visible error messages ourselves, here in {@code
 * DatastoreException}, and only reporting those messages to users.
 *
 * <p>In general, if you know the exact error code you want to return, use one of the
 * error-code-specific factory methods here. If you've caught an exception and you want to translate
 * it into something appropriate for developers, use {@code translateException()}, which includes a
 * number of heuristics that determine the right error code and message based on clues like the
 * exception type, message, and any wrapped exceptions.
 */
public class DatastoreException extends Exception implements Cloneable {

  /**
   * Retry options for a {@link DatastoreException}. Allows the default retry logic to be overridden
   * for a specific exception.
   */
  public enum RetryOptions {
    /* Use the default handling, configured in {@link RetryConfig}. */
    DEFAULT,
    /* This exception cannot be retried, regardless of retry logic. */
    NOT_RETRYABLE,
  }

  // Logically final, but modified after clone() in asNonRetryable.
  private RetryOptions retryOptions;

  private final ErrorCode errorCode;

  private final Problem problem;

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for DatastoreException.
   *
   * <p>The exceptions have default retry behavior unless forceNonRetryable is called. Callers must
   * specify values for message and errorCode.
   */
  public static class Builder {
    private RetryOptions retryOptions = RetryOptions.DEFAULT;
    private ErrorCode errorCode;
    private Problem problem;
    private String message;
    @Nullable private Throwable cause;

    public Builder forceNonRetryable() {
      checkArgument(retryOptions == RetryOptions.DEFAULT, "RetryOptions already set.");
      retryOptions = RetryOptions.NOT_RETRYABLE;
      return this;
    }

    public Builder retryOptions(RetryOptions retryOptions) {
      this.retryOptions = checkNotNull(retryOptions);
      return this;
    }

    public Builder errorCode(ErrorCode errorCode) {
      this.errorCode = checkNotNull(errorCode);
      return this;
    }

    public Builder problemCode(ProblemCode problemCode) {
      this.problem = checkNotNull(Problem.create(problemCode));
      return this;
    }

    public Builder problem(Problem problem) {
      this.problem = checkNotNull(problem);
      return this;
    }

    public Builder message(String message) {
      this.message = checkNotNull(message);
      return this;
    }

    public Builder cause(@Nullable Throwable cause) {
      this.cause = cause;
      return this;
    }

    public DatastoreException build() {
      if (problem == null) {
        problem = LegacyProblem.create(message);
      }

      return new DatastoreException(message, errorCode, problem, cause, retryOptions);
    }
  }

  /**
   * If true, then the operation that caused this exception should not be retried under any
   * circumstances. If false, then the operation may or may not be retryable. We should fall back to
   * the normal policies of checking error codes and timeouts to determine whether or not to retry.
   */
  public boolean isNeverRetryable() {
    return retryOptions() == RetryOptions.NOT_RETRYABLE
        || errorCode.equals(ErrorCode.INTERNAL_ERROR);
  }

  public RetryOptions retryOptions() {
    return retryOptions;
  }

  public DatastoreException(String message, ErrorCode errorCode, @Nullable Throwable cause) {
    this(message, errorCode, LegacyProblem.create(message), cause, RetryOptions.DEFAULT);
  }

  public DatastoreException(ProblemCode problemCode, String message, ErrorCode errorCode) {
    this(message, errorCode, Problem.create(checkNotNull(problemCode)), null, RetryOptions.DEFAULT);
  }

  public DatastoreException(
      ProblemCode problemCode, String message, ErrorCode errorCode, @Nullable Throwable cause) {
    this(
        message, errorCode, Problem.create(checkNotNull(problemCode)), cause, RetryOptions.DEFAULT);
  }

  protected DatastoreException(
      String message,
      ErrorCode errorCode,
      Problem problem,
      @Nullable Throwable cause,
      RetryOptions retryOptions) {
    super(checkNotNull(message), cause);
    this.errorCode = checkNotNull(errorCode);
    this.problem = problem;
    this.retryOptions = retryOptions;
  }

  /**
   * Returns the associated error code.
   *
   * @see ErrorCode
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Problem getProblem() {
    return problem;
  }

  /** Returns a new exception identical to this one, but with the given {@link ProblemCode}. */
  public DatastoreException withProblemCode(ProblemCode problemCode) {
    return withProblem(Problem.create(checkNotNull(problemCode)));
  }

  /** Returns a new exception identical to this one, but with the given {@link Problem}. */
  public DatastoreException withProblem(Problem problem) {
    return new DatastoreException(
        getMessage(), errorCode, checkNotNull(problem), getCause(), retryOptions);
  }

  /** Convert to a not-retryable exception (keeping everything else unchanged). */
  public DatastoreException asNonRetryable() {
    try {
      DatastoreException copy = (DatastoreException) clone();
      copy.retryOptions = RetryOptions.NOT_RETRYABLE;
      return copy;
    } catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }

  public static boolean sameErrorAndMessage(
      @Nullable DatastoreException current, @Nullable DatastoreException other) {
    if (current != null && other != null) {
      return current.sameErrorAndProblemCode(other)
          && current.getMessage().equals(other.getMessage());
    }
    return current == null && other == null;
  }

  public boolean sameErrorAndProblemCode(DatastoreException other) {
    return getErrorCode().equals(other.getErrorCode())
        && getProblem().getProblemCode().equals(other.getProblem().getProblemCode());
  }
}
