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

import com.google.apphosting.base.protos.ClonePb.CloneSettings;
import com.google.apphosting.base.protos.ClonePb.CloudDebuggerBreakpoints;
import com.google.apphosting.base.protos.ClonePb.DebuggeeInfoRequest;
import com.google.apphosting.base.protos.EmptyMessage;
import com.google.apphosting.base.protos.ModelClonePb.DeadlineInfo;
import com.google.apphosting.base.protos.ModelClonePb.PerformanceDataRequest;

/**
 * RPC-agnostic equivalent of CloneController.ServerInterface.
 *
 * <p>If you add methods to CloneController in apphosting/sandbox/clone.proto,
 * you need to remember to add them here too.
 */
public interface CloneControllerServerInterface {
  void waitForSandbox(AnyRpcServerContext ctx, EmptyMessage req);

  void applyCloneSettings(AnyRpcServerContext ctx, CloneSettings req);

  void sendDeadline(AnyRpcServerContext ctx, DeadlineInfo req);

  void getPerformanceData(AnyRpcServerContext ctx, PerformanceDataRequest req);

  void updateActiveBreakpoints(AnyRpcServerContext ctx, CloudDebuggerBreakpoints req);

  void getDebuggeeInfo(AnyRpcServerContext ctx, DebuggeeInfoRequest req);
}
