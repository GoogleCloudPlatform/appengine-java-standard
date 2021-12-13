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

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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

  /**
   * Parse the data into YAML.
   *
   * @param data The text to parse as YAML. 
   * @return The YAML data as a Map.
   * @throws AppEngineConfigException Throws when the underlying Yaml library
   * detects bad data of when the data does not parse to a Map.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> genericParse(String data)
      throws AppEngineConfigException {
    try {
      YamlReader reader = new YamlReader(data);
      return reader.read(Map.class);
    } catch (YamlException exc) {
      throw new AppEngineConfigException("Invalid YAML data: " + data, exc);
    }
  }

  /**
   * A utility class that encapsulates conversion between different
   * types of objects.
   */
  public static interface ObjectConverter<T> {
    T convert(Object obj) throws Exception;
  }

  /**
   * Parses the data into YAML and converts the map values.
   *
   * @param data The text to parse as YAML. 
   * @param converter A converter for updating the values of the Map from
   * the type returned from the underlying Yaml library to the expected type.
   * @return The YAML data as a Map.
   * @throws AppEngineConfigException Throws when the underlying Yaml library
   * detects bad data or when the data does not parse to a Map. Also all
   * Exceptions from ObjectConverted are encapsulated in an
   * AppEngineConfigException.
   */
  public static <T> Map<String, T> genericParse(String data, 
      ObjectConverter<T> converter) throws AppEngineConfigException {
    try {
      Map<String, T> yaml = new HashMap<String, T>();
      for (Entry<String, Object> entry : genericParse(data).entrySet()) {
        yaml.put(entry.getKey(), converter.convert(entry.getValue()));
      }
      return yaml;
    } catch (Exception exc) {
      throw new AppEngineConfigException(exc);
    }
  }
}
