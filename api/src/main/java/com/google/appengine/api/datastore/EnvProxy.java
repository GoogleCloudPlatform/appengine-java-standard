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

package com.google.appengine.api.datastore;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Proxy around System.getenv() to enable testing and prevent errors in situations where the
 * security manager does not allow access to environment variables.
 */
// TODO: Remove reflective use of this class from
// javatests/com/google/developers/console/tools/wipeoutserver/WipeoutModuleServiceTest.java.
class EnvProxy {

  private static ImmutableMap<String, String> envOverride;

  private EnvProxy() {}

  /**
   * Updates to {@code envOverride} made after calling this method will not be reflected in calls to
   * {@link #getenv(String)}.
   */
  static synchronized void setEnvOverrideForTest(Map<String, String> envOverride) {
    EnvProxy.envOverride = ImmutableMap.copyOf(envOverride);
  }

  static synchronized void clearEnvOverrideForTest() {
    envOverride = null;
  }

  static String getenv(String name) {
    synchronized (EnvProxy.class) {
      if (envOverride != null) {
        return envOverride.get(name);
      }
    }
    try {
      return System.getenv(name);
    } catch (SecurityException e) {
      return null;
    }
  }
}
