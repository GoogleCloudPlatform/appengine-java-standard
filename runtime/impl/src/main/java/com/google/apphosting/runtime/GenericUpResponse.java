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
import com.google.common.collect.ImmutableList;

public class GenericUpResponse implements GenericResponse {

  private final MutableUpResponse response;

  public GenericUpResponse(MutableUpResponse response)
  {
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
}
