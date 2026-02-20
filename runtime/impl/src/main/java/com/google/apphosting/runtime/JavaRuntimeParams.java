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

package com.google.apphosting.runtime;

import java.util.ArrayList;
import java.util.List;

/** Command line parameters for Java runtime, and its dependencies. */
record JavaRuntimeParams(
    String trustedHost,
    String fixedApplicationPath,
    int jettyHttpPort,
    int maxOutstandingApiRpcs,
    List<String> unknownParams) {

  /**
   * Creates {@code JavaRuntimeParams} from command line arguments.
   *
   * <p>This parser only supports arguments in the `--key=value` format. Arguments in the `--key
   * value` format will result in `--key` being treated as an unknown parameter and `value` being
   * silently ignored.
   *
   * <p>The following arguments are recognized:
   * <ul>
   *   <li>{@code --trusted_host=<value>}</li>
   *   <li>{@code --fixed_application_path=<value>}</li>
   *   <li>{@code --jetty_http_port=<value>}</li>
   *   <li>{@code --clone_max_outstanding_api_rpcs=<value>}</li>
   * </ul>
   *
   * @param args command line arguments
   * @return an instance of {@code JavaRuntimeParams} with parsed values from command line
   */
  public static JavaRuntimeParams parseArgs(String... args) {
    String trustedHost = "";
    String fixedApplicationPath = null;
    int jettyHttpPort = 8080;
    int maxOutstandingApiRpcs = AppEngineConstants.DEFAULT_MAX_OUTSTANDING_API_RPCS;
    List<String> unknownParams = new ArrayList<>();

    for (String arg : args) {
      String key = arg;
      String value = null;
      int eq = arg.indexOf('=');
      if (eq > 0) {
        key = arg.substring(0, eq);
        value = arg.substring(eq + 1);
      }
      switch (key) {
        case "--trusted_host" -> {
          if (value != null) {
            trustedHost = value;
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        case "--fixed_application_path" -> {
          if (value != null) {
            fixedApplicationPath = value;
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        case "--jetty_http_port" -> {
          if (value != null) {
            try {
              jettyHttpPort = Integer.parseInt(value);
            } catch (NumberFormatException e) {
              // If the value is not a valid integer throw an exception.
              throw new IllegalArgumentException("Invalid value for " + arg, e);
            }
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        case "--clone_max_outstanding_api_rpcs" -> {
          if (value != null) {
            try {
              maxOutstandingApiRpcs = Integer.parseInt(value);
            } catch (NumberFormatException e) {
              // If the value is not a valid integer throw an exception.
              throw new IllegalArgumentException("Invalid value for " + arg, e);
            }
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        default -> unknownParams.add(arg);
      }
    }
    return new JavaRuntimeParams(
        trustedHost, fixedApplicationPath, jettyHttpPort, maxOutstandingApiRpcs, unknownParams);
  }
}
