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

package com.google.appengine.api.log;

import static java.util.Objects.requireNonNull;

import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.logservice.LogServicePb.LogModuleVersion;
import com.google.apphosting.api.logservice.LogServicePb.LogReadRequest;
import com.google.apphosting.api.logservice.LogServicePb.LogReadResponse;
import com.google.apphosting.api.logservice.LogServicePb.LogServiceError;
import com.google.apphosting.api.logservice.LogServicePb.LogServiceError.ErrorCode;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UninitializedMessageException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code LogServiceImpl} is an implementation of {@link LogService} that makes API calls to {@link
 * ApiProxy}.
 *
 */
final class LogServiceImpl implements LogService {
  static final String PACKAGE = "logservice";
  static final String READ_RPC_NAME = "Read";

  @Override
  public LogQueryResult fetch(LogQuery query) {
    try {
      // TODO: Update LogQueryResult to accept the Future so we can
      // return immediately.
      return fetchAsync(query).get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof LogServiceException) {
        throw (LogServiceException) e.getCause();
      } else if (e.getCause() instanceof InvalidRequestException) {
        throw (InvalidRequestException) e.getCause();
      } else {
        throw new LogServiceException(e.getMessage());
      }
    } catch (InterruptedException e) {
      throw new LogServiceException(e.getMessage());
    }
  }

  Future<LogQueryResult> fetchAsync(LogQuery query) {
    LogReadRequest.Builder request =
        LogReadRequest.newBuilder().setAppId(getCurrentEnvironmentOrThrow().getAppId());

    Long startTimeUs = query.getStartTimeUsec();
    if (startTimeUs != null) {
      request.setStartTime(startTimeUs);
    }

    Long endTimeUs = query.getEndTimeUsec();
    if (endTimeUs != null) {
      request.setEndTime(endTimeUs);
    }

    int batchSize = requireNonNull(query.getBatchSize(), "Null batch size");
    request.setCount(batchSize);

    LogLevel minLogLevel = query.getMinLogLevel();
    if (minLogLevel != null) {
      request.setMinimumLogLevel(minLogLevel.ordinal());
    }

    request
        .setIncludeIncomplete(query.getIncludeIncomplete())
        .setIncludeAppLogs(query.getIncludeAppLogs());

    // Use a set to de-dupe entries.
    Set<LogQuery.Version> convertedModuleInfos = Sets.newTreeSet(LogQuery.VERSION_COMPARATOR);

    // NOTE: LogQuery enforces that at most one of these lists is populated.
    if (!query.getMajorVersionIds().isEmpty()) {
      for (String versionId : query.getMajorVersionIds()) {
        convertedModuleInfos.add(new LogQuery.Version("default", versionId));
      }
    } else if (!query.getVersions().isEmpty()) {
      convertedModuleInfos.addAll(query.getVersions());
    } else {
      String currentVersionId = getCurrentEnvironmentOrThrow().getVersionId();
      // Get just the major version id - for 1.2332 it is just '1'.
      String versionId = currentVersionId.split("\\.")[0];
      convertedModuleInfos.add(
          new LogQuery.Version(getCurrentEnvironmentOrThrow().getModuleId(), versionId));
    }

    for (LogQuery.Version moduleInfo : convertedModuleInfos) {
      LogModuleVersion.Builder requestModuleVersion = request.addModuleVersionBuilder();
      if (!moduleInfo.getModuleId().equals("default")) {
        requestModuleVersion.setModuleId(moduleInfo.getModuleId());
      }
      requestModuleVersion.setVersionId(moduleInfo.getVersionId());
    }

    for (String requestId : query.getRequestIds()) {
      request.addRequestId(ByteString.copyFromUtf8(requestId));
    }

    String offset = query.getOffset();
    if (offset != null) {
      request.setOffset(LogQueryResult.parseOffset(offset));
    }

    final LogQuery finalizedQuery = query;
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Future<byte[]> responseBytes =
        ApiProxy.makeAsyncCall(PACKAGE, READ_RPC_NAME, request.build().toByteArray(), apiConfig);
    return new FutureWrapper<byte[], LogQueryResult>(responseBytes) {
      @Override
      protected LogQueryResult wrap(byte @Nullable[] responseBytes) {
        try {
          LogReadResponse response =
              LogReadResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
          return new LogQueryResult(response, finalizedQuery);
        } catch (InvalidProtocolBufferException | UninitializedMessageException e) {
          throw new LogServiceException("Could not parse LogReadResponse", e);
        }
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException) {
          ApiProxy.ApplicationException e = (ApiProxy.ApplicationException) cause;
          ErrorCode errorCode = LogServiceError.ErrorCode.forNumber(e.getApplicationError());
          if (errorCode == LogServiceError.ErrorCode.INVALID_REQUEST) {
            return new InvalidRequestException(e.getErrorDetail());
          }
          return new LogServiceException(e.getErrorDetail());
        }
        return cause;
      }
    };
  }

  private static ApiProxy.Environment getCurrentEnvironmentOrThrow() {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      throw new IllegalStateException(
          "Operation not allowed in a thread that is neither the original request thread "
              + "nor a thread created by ThreadManager");
    }
    return environment;
  }
}
