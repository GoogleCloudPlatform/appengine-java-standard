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

package com.google.appengine.tools.remoteapi;

import com.google.apphosting.api.ApiProxy;
import com.google.errorprone.annotations.DoNotCall;
import java.util.HashMap;
import java.util.Map;

/**
 * A fake environment to use in client-side tools that use App Engine APIs,
 * such the remote API, or any other code that uses a KeyFactory.
 */
class ToolEnvironment implements ApiProxy.Environment {

  private final String appId;
  private final String userEmail;
  private final Map<String, Object> attributes = new HashMap<>();

  public ToolEnvironment(String appId, String userEmail) {
    this.appId = appId;
    this.userEmail = userEmail;
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public String getAuthDomain() {
    return "gmail.com"; // from LocalEnvironment
  }

  @Override
  public String getEmail() {
    return userEmail;
  }

  @DoNotCall
  @Override
  @Deprecated
  public final String getRequestNamespace() {
    return "";
  }

  @Override
  public String getModuleId() {
    return "default";
  }

  @Override
  public String getVersionId() {
    return "1";
  }

  @Override
  public boolean isAdmin() {
    return true;
  }

  @Override
  public boolean isLoggedIn() {
    return true;
  }

  @Override
  public long getRemainingMillis() {
    return Long.MAX_VALUE;
  }
}
