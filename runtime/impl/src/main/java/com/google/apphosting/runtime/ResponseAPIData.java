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

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.protobuf.ByteString;

import java.util.Collection;
import java.util.List;

/**
 * This interface defines a set of operations required for a Response to be used by the Java Runtime.
 */
public interface ResponseAPIData {
  void addAppLog(AppLogsPb.AppLogLine logLine);

  int getAppLogCount();

  List<AppLogsPb.AppLogLine> getAndClearAppLogList();

  void setSerializedTrace(ByteString byteString);

  void setTerminateClone(boolean terminateClone);

  void setCloneIsInUncleanState(boolean b);

  void setUserMcycles(long l);

  void addAllRuntimeLogLine(Collection<RuntimePb.UPResponse.RuntimeLogLine> logLines);

  void error(int error, String errorMessage);

  void finishWithResponse(AnyRpcServerContext rpc);

  void complete();

  int getError();
}
