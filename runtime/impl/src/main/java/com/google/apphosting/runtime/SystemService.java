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

package com.google.apphosting.runtime;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestRequest;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestResponse;
import com.google.apphosting.base.protos.SystemServicePb.SystemServiceError;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Implements {@link SystemService}.
 *
 */
class SystemService {
  static final String PACKAGE = "system";

  String startBackgroundRequest() {
    StartBackgroundRequestRequest request = StartBackgroundRequestRequest.newBuilder().build();

    StartBackgroundRequestResponse.Builder response = StartBackgroundRequestResponse.newBuilder();
    try {
      response.mergeFrom(
          ApiProxy.makeSyncCall(PACKAGE, "StartBackgroundRequest", request.toByteArray()));
      return response.getRequestId();
    } catch (ApiProxy.ApplicationException ex) {
      throw translateException(ex);
    } catch (InvalidProtocolBufferException ex) {
      throw new ApiProxy.ArgumentException(PACKAGE, "StartBackgroundRequest");
    }
  }

  private static RuntimeException translateException(ApiProxy.ApplicationException ex) {
    SystemServiceError.ErrorCode error =
        SystemServiceError.ErrorCode.forNumber(ex.getApplicationError());
    if (error == SystemServiceError.ErrorCode.INTERNAL_ERROR) {
      return new IllegalStateException(
          "An internal error occurred: " + ex.getErrorDetail());
    } else if (error == SystemServiceError.ErrorCode.BACKEND_REQUIRED) {
      return new IllegalStateException(
          "This feature is only available to backend instances.");
    } else if (error == SystemServiceError.ErrorCode.LIMIT_REACHED) {
      return new IllegalStateException(
          "Limit on the number of active background requests was reached for this app version.");
    } else {
      return new ApiProxy.UnknownException("An unknown error occurred: " + ex.getErrorDetail());
    }
  }
}
