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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.ResponseAPIData;
import com.google.apphosting.runtime.anyrpc.AnyRpcServerContext;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Response;

public class JettyResponseAPIData implements ResponseAPIData {

  private final Response response;
  private final HttpServletResponse httpServletResponse;

  public JettyResponseAPIData(Response response, HttpServletResponse httpServletResponse) {
    this.response = response;
    this.httpServletResponse = httpServletResponse;
  }

  public Response getResponse() {
    return response;
  }

  public HttpServletResponse getHttpServletResponse() {
    return httpServletResponse;
  }

  @Override
  public void addAppLog(AppLogsPb.AppLogLine logLine) {}

  @Override
  public int getAppLogCount() {
    return 0;
  }

  @Override
  public List<AppLogsPb.AppLogLine> getAndClearAppLogList() {
    return ImmutableList.of();
  }

  @Override
  public void setSerializedTrace(ByteString byteString) {}

  @Override
  public void setTerminateClone(boolean terminateClone) {}

  @Override
  public void setCloneIsInUncleanState(boolean b) {}

  @Override
  public void setUserMcycles(long l) {}

  @Override
  public void addAllRuntimeLogLine(Collection<RuntimePb.UPResponse.RuntimeLogLine> logLines) {}

  @Override
  public void error(int error, String errorMessage) {}

  @Override
  public void finishWithResponse(AnyRpcServerContext rpc) {}

  @Override
  public void complete() {}

  @Override
  public int getError() {
    return 0;
  }
}
