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

package com.google.apphosting.runtime.jetty.http;

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.GenericResponse;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.protobuf.ByteString;
import org.eclipse.jetty.server.Response;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GenericJettyResponse implements GenericResponse {

  private final Response response;

  public GenericJettyResponse(Response response) {
    this.response = response;
  }

  public Response getWrappedResponse()
  {
    return response;
  }

  @Override
  public void addAppLog(AppLogsPb.AppLogLine logLine) {
  }

  @Override
  public int getAppLogCount() {
    return 0;
  }

  @Override
  public List<AppLogsPb.AppLogLine> getAndClearAppLogList() {
    return Collections.emptyList();
  }

  @Override
  public void setSerializedTrace(ByteString byteString) {

  }

  @Override
  public void setTerminateClone(boolean terminateClone) {

  }

  @Override
  public void setCloneIsInUncleanState(boolean b) {

  }

  @Override
  public void setUserMcycles(long l) {

  }

  @Override
  public void addAllRuntimeLogLine(Collection<RuntimePb.UPResponse.RuntimeLogLine> logLines) {

  }

  @Override
  public void error(int error, String errorMessage) {

  }

  @Override
  public void finishWithResponse(AnyRpcServerContext rpc) {

  }

  @Override
  public void complete() {

  }

  @Override
  public int getError() {
    return 0;
  }
}
