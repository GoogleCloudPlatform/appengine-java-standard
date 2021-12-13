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

package com.google.apphosting.runtime.jetty94;

/** Class to serve static constants */
public final class Jetty94Constants {

  // As a util class, instantiation should not be allowed.
  private Jetty94Constants() {}

  public static final String APP_YAML_ATTRIBUTE_TARGET =
      "com.google.apphosting.runtime.jetty94.appYaml";
}
