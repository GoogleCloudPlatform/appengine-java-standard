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

package com.google.appengine.api.taskqueue;

import com.google.appengine.api.datastore.DatastoreApiHelper;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueServiceError.ErrorCode;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiConfig;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UninitializedMessageException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Provides translation of calls between userland and appserver land.
 *
 */
class QueueApiHelper {
  static final String PACKAGE = "taskqueue";

  // Makes calls to appserver.
  void makeSyncCall(String method, MessageLite request, MessageLite.Builder response) {
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, method, request.toByteArray());
      // This null check is mainly for the benefit of unit tests
      // (specifically ones using EasyMock, where the default behavior
      // is to return null).
      if (responseBytes != null) {
        try {
          response.mergeFrom(responseBytes, ExtensionRegistryLite.getEmptyRegistry());
        } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
          throw new IllegalArgumentException("Could not parse response", e);
        }
      }
    } catch (ApiProxy.ApplicationException exception) {
      throw translateError(exception);
    }
  }

  /**
   * Issue an async rpc against the taskqueue package with the given request and response pbs as
   * input and apply standard exception handling. Do not use this helper function if you need
   * non-standard exception handling.
   */
  <T extends MessageLite> Future<T> makeAsyncCall(
      String method, MessageLite request, final T responseTemplate, ApiConfig apiConfig) {
    Future<byte[]> response =
        ApiProxy.makeAsyncCall(PACKAGE, method, request.toByteArray(), apiConfig);
    return new FutureWrapper<byte[], T>(response) {
      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException) {
          return translateError((ApiProxy.ApplicationException) cause);
        }
        return cause;
      }

      @Override
      protected T wrap(byte[] responseBytes) {
        // This null check is mainly for the benefit of unit tests.
        if (responseBytes != null) {
          try {
            @SuppressWarnings("unchecked")
            T response =
                (T)
                    responseTemplate.toBuilder()
                        .mergeFrom(responseBytes, ExtensionRegistryLite.getEmptyRegistry())
                        .build();
            return response;
          } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
            throw new IllegalArgumentException("Could not parse response", e);
          }
        }
        return responseTemplate;
      }
    };
  }

  /***
   * Extract the future's result.
   */
  static <T> T getInternal(Future<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiProxy.ApplicationException(
          ErrorCode.TRANSIENT_ERROR_VALUE, "Interrupted while waiting for RPC response.");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        throw (Error) cause;
      } else {
        throw new RuntimeException(cause);
      }
    }
  }

  // Translates error codes and details to exceptions.
  // Corresponds to __TranslateError in taskqueue.py. Keep in sync!
  static RuntimeException translateError(int error, String detail) {
    ErrorCode errorCode = ErrorCode.forNumber(error);

    // Handles datastore exceptions which are tunneled via TaskQueue API by
    // shifting their error codes. For details see DATASTORE_ERROR description
    // in apphosting/api/taskqueue/taskqueue_service.proto
    int datastoreErrorCode = ErrorCode.DATASTORE_ERROR_VALUE;
    if (error >= datastoreErrorCode) {
      ApiProxy.ApplicationException datastoreApplicationException =
          new ApiProxy.ApplicationException(error - datastoreErrorCode, detail);
      TransactionalTaskException taskqueueException = new TransactionalTaskException();
      taskqueueException.initCause(
          DatastoreApiHelper.translateError(datastoreApplicationException));
      return taskqueueException;
    }

    switch (errorCode) {
      case UNKNOWN_QUEUE:
        return new IllegalStateException("The specified queue is unknown : " + detail);
      case TRANSIENT_ERROR:
        return new TransientFailureException(detail);
      case INTERNAL_ERROR:
        return new InternalFailureException(detail);
      case TASK_TOO_LARGE:
        return new IllegalArgumentException("Task size is too large : " + detail);
      case INVALID_TASK_NAME:
        return new IllegalArgumentException("Invalid task name : " + detail);
      case INVALID_QUEUE_NAME:
        return new IllegalArgumentException("Invalid queue name : " + detail);
      case INVALID_URL:
        return new IllegalArgumentException("Invalid URL : " + detail);
      case INVALID_QUEUE_RATE:
        return new IllegalArgumentException("Invalid queue rate : " + detail);
      case PERMISSION_DENIED:
        return new SecurityException("Permission for requested operation is denied : " + detail);
      case TASK_ALREADY_EXISTS:
        return new TaskAlreadyExistsException("Task name already exists : " + detail);
      case TOMBSTONED_TASK:
        // TODO It may be more interesting to throw a
        // "TombstonedTaskException" instead, however the semantics are currently
        // not useful. (i.e. Race condition between attempt to execute task
        // and when state of task is changed to TOMBSTONED_TASK means that
        // TASK_ALREADY_EXISTS does not indicate that the task will be
        // guaranteed to execute after the add request is initiated.  This guarantee
        // is required if TOMBSTONED_TASK is a useful state.)
        // It may be nice to have 3 states (TaskInRunQueue, TaskRunning, TaskRetired).
        // TaskInRunQueue and TaskRunning indicate the current TASK_ALREADY_EXISTS
        // state.
        return new TaskAlreadyExistsException("Task name is tombstoned : " + detail);
      case INVALID_ETA:
        return new IllegalArgumentException("ETA is invalid : " + detail);
      case INVALID_REQUEST:
        return new IllegalArgumentException("Invalid request : " + detail);
      case UNKNOWN_TASK:
        return new TaskNotFoundException("Task does not exist : " + detail);
      case TOMBSTONED_QUEUE:
        // TODO: Return a different exception when the Java API is able to provoke this
        // exception e.g. if the ability to delete queues is added.
        return new IllegalStateException(
            "The queue has been marked for deletion and is no longer usable : " + detail);
      case DUPLICATE_TASK_NAME:
        return new IllegalArgumentException("Identical task names in request : " + detail);
        // SKIPPED will never be translated into an exception.
      case TOO_MANY_TASKS:
        return new IllegalArgumentException("Request contains too many tasks : " + detail);
      case INVALID_QUEUE_MODE:
        return new InvalidQueueModeException(
            "Target queue mode does not support this operation : " + detail);
      case TASK_LEASE_EXPIRED:
        return new IllegalStateException("The task lease has expired : " + detail);
      case QUEUE_PAUSED:
        return new IllegalStateException(
            "The queue is paused and cannot process the request : " + detail);
      default:
        return new QueueFailureException("Unspecified error (" + errorCode + ") : " + detail);
    }
  }

  static RuntimeException translateError(ApiProxy.ApplicationException exception) {
    return translateError(exception.getApplicationError(), exception.getErrorDetail());
  }

  public static void validateQueueName(String queueName) {
    // Verify queue name matches RE specification.
    if (queueName == null
        || queueName.length() == 0
        || !QueueConstants.QUEUE_NAME_PATTERN.matcher(queueName).matches()) {
      throw new IllegalArgumentException(
          "Queue name does not match expression "
              + QueueConstants.QUEUE_NAME_REGEX
              + "; found '"
              + queueName
              + "'");
    }
  }
}
