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

package com.google.appengine.tools.development;

import com.google.apphosting.api.ApiProxy;

/**
 * Factory class for an {@link ApiProxy.Delegate} object configured to use local
 * services.
 *
 */
public class ApiProxyLocalFactory {
  LocalServerEnvironment localServerEnvironment;

  public ApiProxyLocalFactory() {
    // nothing special
  }

  /**
   * Creates a new local proxy.
   * @param localServerEnvironment the local server env
   *
   * @return a new local proxy object
   */
  public ApiProxyLocal create(LocalServerEnvironment localServerEnvironment) {
    return new ApiProxyLocalImpl(localServerEnvironment);
  }

  /**
   * Creates a new local proxy that delegates some calls to a Python API server.
   *
   * @param localServerEnvironment the local server env
   * @param applicationName the application name to pass to the ApiServer binary
   * @return a new local proxy object
   */
  public ApiProxyLocal create(
      LocalServerEnvironment localServerEnvironment, String applicationName) {
    return ApiProxyLocalImpl.getApiProxyLocal(localServerEnvironment, applicationName);
  }
}
