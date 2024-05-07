/*
 * Copyright 2022 Google LLC
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
package com.google.appengine.setup;

import com.google.appengine.setup.utils.http.HttpRequest;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.HashMap;

public class ApiProxySetupUtil {
  public static void registerAPIProxy(HttpRequest request) {
    ApiProxy.setDelegate(new ApiProxyDelegate());
    // "GAE_DEPLOYMENT_ID" environment property only exists in production, when AppServer forwards
    // request to appengine clone.
    boolean isProduction = System.getenv().containsKey("GAE_DEPLOYMENT_ID");
    ApiProxy.setEnvironmentForCurrentThread(isProduction ?
        getProdEnvironment(request) : getLocalEnvironment());
  }

  private static Environment getLocalEnvironment() {
    return new LazyApiProxyEnvironment(
        () -> new ApiProxyEnvironment(
            "localhost:8089", // server
            null, // ticket
            "dummy-appId",
            "dummy-module",
            "dummy-majorVersion",
            "dummy-instance",
            "dummy-email",
            true, // admin
            "dummy-authDomain",
            new TimerImpl(),
            1000l, // millisUntilSoftDeadline
            new HashMap<>()
        ));
  }

  private static Environment getProdEnvironment(HttpRequest request) {
    return new LazyApiProxyEnvironment(
        () -> ApiProxyEnvironment.createFromHeaders(
            System.getenv(),
            request,
            "169.254.169.253:10001", // server
            new TimerImpl(),
            1000l // millisUntilSoftDeadline
        ));
  }
}
