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
final class JavaRuntimeParams {

  /** Default trusted host is empty string. */
  private String trustedHost = "";

  /** Default application path is null. */
  private String fixedApplicationPath = null;

  /** Default Jetty HTTP port is 8080. */
  private int jettyHttpPort = 8080;

  /** Default value from {@link AppEngineConstants#DEFAULT_MAX_OUTSTANDING_API_RPCS}. */
  private int maxOutstandingApiRpcs = AppEngineConstants.DEFAULT_MAX_OUTSTANDING_API_RPCS;

  private List<String> unknownParams;

  private JavaRuntimeParams() {}

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
    JavaRuntimeParams params = new JavaRuntimeParams();
    params.unknownParams = new ArrayList<>();
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
            params.trustedHost = value;
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        case "--fixed_application_path" -> {
          if (value != null) {
            params.fixedApplicationPath = value;
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        case "--jetty_http_port" -> {
          if (value != null) {
            try {
              params.jettyHttpPort = Integer.parseInt(value);
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
              params.maxOutstandingApiRpcs = Integer.parseInt(value);
            } catch (NumberFormatException e) {
              // If the value is not a valid integer throw an exception.
              throw new IllegalArgumentException("Invalid value for " + arg, e);
            }
          } else {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
        }
        default -> params.unknownParams.add(arg);
      }
    }
    return params;
  }

  /** Specification used for connecting back to the appserver. */
  String getTrustedHost() {
    return trustedHost;
  }

  /** Jetty HTTP Port number to use for http access to the runtime. */
  int getJettyHttpPort() {
    return jettyHttpPort;
  }

  /** Maximum number of outstanding API RPCs. */
  int getMaxOutstandingApiRpcs() {
    return maxOutstandingApiRpcs;
  }

  /**
   * Fixed path to use for the application root directory, irrespective of the application id and
   * version. Ignored if empty.
   */
  String getFixedApplicationPath() {
    return fixedApplicationPath;
  }

  List<String> getUnknownParams() {
    return unknownParams;
  }
}
