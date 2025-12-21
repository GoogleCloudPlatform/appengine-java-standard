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

package com.google.appengine.tools.info;

final class SdkFactory {

  private SdkFactory() {}

  /** Returns an SDK implementation to use for access jar files and resources. */
  static AppengineSdk getSdk() {
    boolean useEE8 = Boolean.getBoolean("appengine.use.EE8");
    boolean useEE10 = Boolean.getBoolean("appengine.use.EE10");
    boolean useEE11 = Boolean.getBoolean("appengine.use.EE11");
    AppengineSdk currentSdk = null;
    if (useEE8) {
      currentSdk = new Jetty121EE8Sdk();
    } else if (useEE10) {
      // We stage with Jetty121 EE11 SDK.
      currentSdk = new Jetty121EE11Sdk();
    } else if (useEE11) {
      currentSdk = new Jetty121EE11Sdk();
    } else {
      // We keep Jetty9 legacy SDK for internal testing only.
      // For external usage, we default to Jetty12.1 EE8 SDK.
      currentSdk = new Jetty121EE8Sdk();
    }
    return currentSdk;
  }
}
