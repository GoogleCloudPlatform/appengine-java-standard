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

package com.google.apphosting.runtime.jetty;

import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.runtime.AppVersion;

/**
 * {@code JettyConstants} centralizes some constants that are specific to our use of Jetty.
 */
public class JettyConstants {
  /**
   * This context attribute contains the {@link AppVersion} for the current application.
   */
  public static final String APP_VERSION_CONTEXT_ATTR =
      "com.google.apphosting.runtime.jetty.APP_VERSION_CONTEXT_ATTR";

  /**
   * This {@code ServletRequest} attribute contains the {@link
   * AppVersionKey} identifying the current application.  identify
   * which application version to use.
   */
  public static final String APP_VERSION_KEY_REQUEST_ATTR =
      "com.google.apphosting.runtime.jetty.APP_VERSION_REQUEST_ATTR";

  public static final String APP_YAML_ATTRIBUTE_TARGET =
      "com.google.apphosting.runtime.jetty.appYaml";
}
