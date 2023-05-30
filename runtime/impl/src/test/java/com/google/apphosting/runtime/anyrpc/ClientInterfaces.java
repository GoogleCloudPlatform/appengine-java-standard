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

package com.google.apphosting.runtime.anyrpc;

import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.ClonePb.CloneSettings;
import com.google.apphosting.base.protos.ClonePb.PerformanceData;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo;
import com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;

/**
 * Abstract client interfaces for the EvaluationRuntime and CloneController RPCs. These are just
 * used as a convenient way to test RPCs. There is no connection to actual EvaluationRuntime or
 * CloneController functionality.
 *
 */
class ClientInterfaces {
  // There are no instances of this class.
  private ClientInterfaces() {}

  interface EvaluationRuntimeClient {
    void handleRequest(AnyRpcClientContext ctx, UPRequest req, AnyRpcCallback<UPResponse> cb);

    void addAppVersion(AnyRpcClientContext ctx, AppInfo req, AnyRpcCallback<EmptyMessage> cb);

    void deleteAppVersion(AnyRpcClientContext ctx, AppInfo req, AnyRpcCallback<EmptyMessage> cb);
  }

  interface CloneControllerClient {
    void waitForSandbox(AnyRpcClientContext ctx, EmptyMessage req, AnyRpcCallback<EmptyMessage> cb);

    void applyCloneSettings(
        AnyRpcClientContext ctx, CloneSettings req, AnyRpcCallback<EmptyMessage> cb);

    void sendDeadline(AnyRpcClientContext ctx, DeadlineInfo req, AnyRpcCallback<EmptyMessage> cb);

    void getPerformanceData(
        AnyRpcClientContext ctx, PerformanceDataRequest req, AnyRpcCallback<PerformanceData> cb);
  }
}
