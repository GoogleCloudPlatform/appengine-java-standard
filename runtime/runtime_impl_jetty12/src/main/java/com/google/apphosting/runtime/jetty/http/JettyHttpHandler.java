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

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.JettyConstants;
import com.google.apphosting.runtime.ServletEngineAdapter;
import com.google.apphosting.runtime.jetty.AppInfoFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class JettyHttpHandler extends Handler.Wrapper {
  private final boolean passThroughPrivateHeaders;
  private final AppInfoFactory appInfoFactory;
  private final AppVersionKey appVersionKey;

  public JettyHttpHandler(ServletEngineAdapter.Config runtimeOptions, AppVersionKey appVersionKey, AppInfoFactory appInfoFactory)
  {
    this.passThroughPrivateHeaders = runtimeOptions.passThroughPrivateHeaders();
    this.appInfoFactory = appInfoFactory;
    this.appVersionKey = appVersionKey;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {

    // TODO: set the environment.
    request.setAttribute(JettyConstants.APP_VERSION_KEY_REQUEST_ATTR, appVersionKey);
    return super.handle(request, response, callback);
  }
}
