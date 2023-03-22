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

package com.google.appengine.runtime.lite;

import com.google.apphosting.api.ApiProxy;
import java.util.concurrent.Callable;

/** A utility to install the thread-local App Engine API proxy context. */
public final class ApiProxyEnvironmentManager {

  private final ApiProxy.Environment environment;

  public static ApiProxyEnvironmentManager ofCurrentEnvironment() {
    return new ApiProxyEnvironmentManager(ApiProxy.getCurrentEnvironment());
  }

  private ApiProxyEnvironmentManager(ApiProxy.Environment environment) {
    this.environment = environment;
  }

  public void installEnvironmentAndCall(Callable<Void> callable) throws Exception {
    try (Installer installer = new Installer()) {
      callable.call();
    }
  }

  class Installer implements AutoCloseable {
    private final ApiProxy.Environment previousEnvironment;

    Installer() {
      this.previousEnvironment = ApiProxy.getCurrentEnvironment();
      ApiProxy.setEnvironmentForCurrentThread(environment);
    }

    @Override
    public void close() {
      ApiProxy.setEnvironmentForCurrentThread(previousEnvironment);
    }
  }
}
