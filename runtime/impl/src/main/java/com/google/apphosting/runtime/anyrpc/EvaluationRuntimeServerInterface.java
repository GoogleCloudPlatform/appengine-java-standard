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
import com.google.apphosting.base.protos.RuntimePb.UPRequest;

/**
 * RPC-agnostic version of EvaluationRuntime.ServerInterface.
 *
 * <p>If you add methods to EvaluationRuntime in apphosting/base/runtime.proto,
 * you need to remember to add them here too.
 */
public interface EvaluationRuntimeServerInterface {
  void handleRequest(AnyRpcServerContext ctx, UPRequest req);

  void addAppVersion(AnyRpcServerContext ctx, AppInfo req);

  void deleteAppVersion(AnyRpcServerContext ctx, AppInfo req);
}
