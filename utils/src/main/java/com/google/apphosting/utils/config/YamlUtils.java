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

package com.google.apphosting.utils.config;

import java.util.regex.Pattern;

/**
 * Helper methods for parsing YAML files.
 *
 */
public class YamlUtils {


  // From http://yaml.org/type/bool.html
  static final Pattern TRUE_PATTERN =
      Pattern.compile("y|Y|yes|Yes|YES|true|True|TRUE|on|On|ON");
  static final Pattern FALSE_PATTERN =
      Pattern.compile("n|N|no|No|NO|false|False|FALSE|off|Off|OFF");

  private static final String RESERVED_URL =
    "The URL '%s' is reserved and cannot be used.";

  private YamlUtils() { }

  /**
   * Parse a YAML !!bool type. YamlBeans only supports "true" or "false".
   *
   * @throws AppEngineConfigException
   */
  static boolean parseBoolean(String value) {
    if (TRUE_PATTERN.matcher(value).matches()) {
      return true;
    } else if (FALSE_PATTERN.matcher(value).matches()) {
      return false;
    }
    throw new AppEngineConfigException("Invalid boolean value '" + value + "'.");
  }

  /**
   * Check that a URL is not one of the reserved URLs according to
   * http://code.google.com/appengine/docs/java/configyaml/appconfig_yaml.html#Reserved_URLs
   *
   * @throws AppEngineConfigException
   */
  static void validateUrl(String url) {
    if (url.equals("/form")) {
      throw new AppEngineConfigException(String.format(RESERVED_URL, url));
    }
  }
}
