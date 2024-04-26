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
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.util.Collection;

public class UpResponseAPIData implements ResponseAPIData {

  private final MutableUpResponse response;

  public UpResponseAPIData(MutableUpResponse response) {
    this.response = response;
  }

  @Override
  public void addAppLog(AppLogsPb.AppLogLine logLine) {
    response.addAppLog(logLine);
  }

  @Override
  public int getAppLogCount() {
    return response.getAppLogCount();
  }

  @Override
  public ImmutableList<AppLogsPb.AppLogLine> getAndClearAppLogList() {
    return response.getAndClearAppLogList();
  }

  @Override
  public void setSerializedTrace(ByteString byteString) {
    response.setSerializedTrace(byteString);
  }

  @Override
  public void setTerminateClone(boolean terminateClone) {
    response.setTerminateClone(terminateClone);
  }

  @Override
  public void setCloneIsInUncleanState(boolean b) {
    response.setCloneIsInUncleanState(b);
  }

  @Override
  public void setUserMcycles(long l) {
    response.setUserMcycles(l);
  }

  @Override
  public void addAllRuntimeLogLine(Collection<RuntimePb.UPResponse.RuntimeLogLine> logLines) {
    response.addAllRuntimeLogLine(logLines);
  }

  @Override
  public void error(int error, String errorMessage) {
    response.clearHttpResponse();
    response.setError(RuntimePb.UPResponse.ERROR.LOG_FATAL_DEATH_VALUE);
    if (errorMessage != null) {
      response.setErrorMessage(errorMessage);
    }
  }

  @Override
  public void finishWithResponse(AnyRpcServerContext rpc) {
    rpc.finishWithResponse(response.build());
  }

  @Override
  public void complete() {
    response.setError(RuntimePb.UPResponse.ERROR.OK_VALUE);
    response.setHttpResponseCodeAndResponse(200, "OK");
  }

  @Override
  public int getError() {
    return response.getError();
  }
}
