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

package com.google.appengine.api.testing;

import com.google.appengine.api.NamespaceManager;
import com.google.apphosting.api.ApiProxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Mock {@code Environment} for testing.
 */
public class MockEnvironment implements ApiProxy.Environment {
  private static final AtomicLong VERSION_ID_COUNTER = new AtomicLong();
  private static final String REQUEST_NAMESPACE = "";
  public static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  public static MockEnvironment fromTestClass(Class<?> testClass) {
    return new MockEnvironment(
        testClass.getName(), Long.toHexString(VERSION_ID_COUNTER.getAndIncrement()));
  }

  private final String appId;
  private final String moduleId;
  private final String versionId;
  private final Map<String, Object> attributes = new HashMap<>();

  public MockEnvironment(String appId, String moduleId, String versionId) {
    this.appId = appId;
    this.moduleId = moduleId;
    this.versionId = versionId;
    attributes.put(APPS_NAMESPACE_KEY, REQUEST_NAMESPACE);
  }

  public MockEnvironment(String appId, String versionId) {
    this(appId, "default", versionId);
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public String getModuleId() {
    return moduleId;
  }

  @Override
  public String getVersionId() {
    return versionId;
  }

  @Override
  public String getEmail() {
    return null;
  }

  @Override
  public boolean isLoggedIn() {
    return false;
  }

  @Override
  public boolean isAdmin() {
    return false;
  }

  @Override
  public String getAuthDomain() {
    return null;
  }

  @Deprecated
  public String getRequestNamespace() {
    // No tests should call this.
    throw new IllegalArgumentException(
        "Failure by calling to deprecated getRequestNamespace(). Try calling"
            + " NamespaceManager.getGoogleAppsNamespace().");
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public long getRemainingMillis() {
    return Long.MAX_VALUE;
  }
}
