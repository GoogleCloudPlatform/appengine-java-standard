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

package com.google.apphosting.utils.runtime;

import com.google.appengine.api.memcache.MemcacheServicePb;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.base.protos.Codes.Code;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.base.protos.RuntimePb.APIResponse.ERROR;
import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.apphosting.base.protos.api.RemoteApiPb.Response;
import com.google.apphosting.base.protos.api.RemoteApiPb.RpcError.ErrorCode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.GoogleLogger;
import java.util.Optional;

/**
 * {@code ApiProxyUtils} is a utility class with functions shared by ApiProxy delegates, e.g.
 * {@code ApiProxyImpl}, {@code VmApiProxyDelegate}.
 */
public final class ApiProxyUtils {
  private ApiProxyUtils() {}

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Convert APIResponse.getError() to the appropriate exception.
   *
   * @param apiResponse the APIResponse
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param logger the Logger used to create log messages.
   * @return ApiProxyException
   */
  public static ApiProxyException convertApiError(APIResponse apiResponse,
      String packageName, String methodName, GoogleLogger logger) {
    APIResponse.ERROR error = APIResponse.ERROR.forNumber(apiResponse.getError());

    switch (error) {
      case CALL_NOT_FOUND:
        return new ApiProxy.CallNotFoundException(packageName, methodName);
      case SECURITY_VIOLATION:
        logger.atSevere().log("Security violation: invalid request id used!");
        return new ApiProxy.UnknownException(packageName, methodName);
      case CAPABILITY_DISABLED:
        return new ApiProxy.CapabilityDisabledException(
            apiResponse.getErrorMessage(), packageName, methodName);
      case OVER_QUOTA:
        return new ApiProxy.OverQuotaException(
            apiResponse.getErrorMessage(), packageName, methodName);
      case REQUEST_TOO_LARGE:
        return new ApiProxy.RequestTooLargeException(packageName, methodName);
      case RESPONSE_TOO_LARGE:
        return new ApiProxy.ResponseTooLargeException(packageName, methodName);
      case PARSE_ERROR:
      case BAD_REQUEST:
        return new ApiProxy.ArgumentException(packageName, methodName);
      case CANCELLED:
        return new ApiProxy.CancelledException(packageName, methodName);
      case BUFFER_ERROR:  // Deprecated
        logger.atSevere().log("API returned BUFFER_ERROR, but shared buffers no longer supported.");
        return new ApiProxy.ArgumentException(packageName, methodName);
      case FEATURE_DISABLED:
        return new ApiProxy.FeatureNotEnabledException(
            "%s.%s " + apiResponse.getErrorMessage(), packageName, methodName);
      case RPC_ERROR:
        return convertApiResponseRpcErrorToException(
            apiResponse.getRpcError(),
            packageName,
            methodName,
            apiResponse.getRpcApplicationError(),
            apiResponse.getErrorMessage(),
            logger);
      default:
        return new ApiProxy.UnknownException(packageName, methodName);
    }
  }

  /**
   * Convert the APIResponse.RpcError to the appropriate exception.
   *
   * @param rpcError the APIResponse.RpcError.
   * @param packageName the name of the API package.
   * @param methodName the name of the method within the API package.
   * @param applicationError the application error from APIResponse.getRpcApplicationError(), which
   *        is used only when the status is APPLICATION_ERROR.
   * @param errorDetail the detail message of the application error, which is used only when the
   *        status is APPLICATION_ERROR.
   * @param logger the Logger used to create log messages.
   * @return ApiProxyException
   */
  private static ApiProxyException convertApiResponseRpcErrorToException(
      APIResponse.RpcError rpcError, String packageName, String methodName, int applicationError,
      String errorDetail, GoogleLogger logger) {
    logger.atWarning().log("RPC failed : %s : %s", rpcError, errorDetail);
    
    if (rpcError == null) {
      return new ApiProxy.UnknownException(packageName, methodName);
    }
    
    switch (rpcError) {
      case DEADLINE_EXCEEDED:
        return new ApiProxy.ApiDeadlineExceededException(packageName, methodName);
      case APPLICATION_ERROR:
        return new ApiProxy.ApplicationException(applicationError, errorDetail);
      default:
        return new ApiProxy.UnknownException(packageName, methodName);
    }
  }

  /**
   * Provides a throwable exception for HTTP API RPC user-application errors.
   *
   * @param packageName the package of the API being called, eg datastore_v3.
   * @param methodName the name of the method in the API being called, eg RunQuery.
   * @param status a status proto representing the response status.
   * @param applicationError error code representing the error.
   * @param errorDetail detailed message for the error.
   * @param cause exception to use as the cause (may be null).
   * @return Exception, CapabilityDisabledException, or an ApplicationException
   */
  public static ApiProxyException getRpcError(
      String packageName,
      String methodName,
      StatusProto status,
      int applicationError,
      String errorDetail,
      Throwable cause) {
    Optional<ApiProxyException> statusException =
        statusException(status, packageName, methodName, cause);
    if (statusException.isPresent()) {
      return statusException.get();
    } else {
      if (applicationError == MemcacheServicePb.MemcacheServiceError.ErrorCode.UNAVAILABLE_VALUE
          && "memcache".equals(packageName)) {
        // Special-case mapping for memcache to ensure strict backward compatibility even for
        // undocumented behaviors.
        return new ApiProxy.CapabilityDisabledException(errorDetail, packageName, methodName);
      } else {
        // Normal case for application errors
        return new ApiProxy.ApplicationException(applicationError, errorDetail);
      }
    }
  }

  /**
   * Provides a throwable exception for HTTP API transport-level RPC errors.
   *
   * @param packageName the package of the API being called, eg datastore_v3.
   * @param methodName the name of the method in the API being called, eg RunQuery.
   * @param response the response from the API server call.
   * @param logger the logger to use for logging warning messages.
   * @return ApiProxyException
   */
  public static ApiProxyException getApiError(
      String packageName, String methodName, Response response, GoogleLogger logger) {
    APIResponse apiResponse =
        APIResponse.newBuilder()
            .setError(ApiProxyUtils.remoteApiErrorToApiResponseError(response).getNumber())
            .setPb(response.getResponse())
            .build();

    return getApiError(packageName, methodName, apiResponse, logger);
  }

  /**
   * Provides a throwable exception for HTTP API transport-level RPC errors.
   *
   * @param packageName the package of the API being called, eg datastore_v3.
   * @param methodName the name of the method in the API being called, eg RunQuery.
   * @param apiResponse the response from the API server call.
   * @param logger the logger to use for logging warning messages.
   * @return ApiProxyException
   */
  public static ApiProxyException getApiError(
      String packageName, String methodName, APIResponse apiResponse, GoogleLogger logger) {
    logger.atWarning().log(
        "Received error from APIHost : %s : %s",
        apiResponse.getError(),
        apiResponse.getErrorMessage());

    return convertApiError(apiResponse, packageName, methodName, logger);
  }

  /**
   * Provides errors based on the status of the HTTP response.
   *
   * @param status a status proto representing the response status.
   * @param packageName the package of the API being called, eg datastore_v3.
   * @param methodName the name of the method in the API being called, eg RunQuery.
   * @param cause the exception to chain as the cause (may be null).
   * @return an optional Exception
   */
  @VisibleForTesting
  public static Optional<ApiProxyException> statusException(
      StatusProto status, String packageName, String methodName, Throwable cause) {
    switch (status.getSpace()) {
      case "generic":
        if (status.getCode() == Code.CANCELLED_VALUE) {
          return Optional.<ApiProxyException>of(
              new ApiProxy.CancelledException(packageName, methodName));
        } else {
          return Optional.empty();
        }
      case "RPC":
        if (status.getCode() == Code.DEADLINE_EXCEEDED_VALUE) {
          return Optional.<ApiProxyException>of(
              new ApiProxy.ApiDeadlineExceededException(packageName, methodName));
        } else {
          return Optional.<ApiProxyException>of(
              new ApiProxy.UnknownException(packageName, methodName, cause));
        }
      default:
        return Optional.empty();
    }
  }

  /**
   * Converts a RemoteApiPb RPC error code into an APIResponse error code.
   *
   * @param responsePb the response containing an error code to be converted.
   * @return An APIResponse.ERROR that represents the original RemoteApiPb error code.
   */
  public static ERROR remoteApiErrorToApiResponseError(RemoteApiPb.Response responsePb) {
    if (!responsePb.hasRpcError()) {
      return ERROR.OK;
    }
    int code = responsePb.getRpcError().getCode();
    ErrorCode[] errorCodes = ErrorCode.values();
    if (code >= 0 && code < errorCodes.length) {
      ErrorCode errorCode = errorCodes[code];
      try {
        return ERROR.valueOf(errorCode.name());
      } catch (IllegalArgumentException e) {
        // This is not a member of the ERROR enum, log it as unknown.
      }
    }
    logger.atWarning().log("Unknown error code %s", code);
    return ERROR.RPC_ERROR;
  }
}
