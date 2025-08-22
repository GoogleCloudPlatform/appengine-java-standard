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

package com.google.appengine.api.mail;

import com.google.appengine.api.EnvironmentProvider;

/** A simple wrapper around {@link System} to allow for easier testing. */
class SystemEnvironmentProvider implements EnvironmentProvider {
  /**
   * Gets the value of the specified environment variable.
   *
   * @param name the name of the environment variable
   * @return the string value of the variable, or {@code null} if the variable is not defined
   */
  @Override
  public String getenv(String name) {
    return System.getenv(name);
  }

  /**
   * Gets the value of the specified environment variable, returning a default value if the variable
   * is not defined.
   *
   * @param name the name of the environment variable
   * @param defaultValue the default value to return
   * @return the string value of the variable, or the default value if the variable is not defined
   */
  @Override
  public String getenv(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null ? value : defaultValue;
  }
}
