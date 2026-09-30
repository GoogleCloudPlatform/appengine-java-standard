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

package com.google.apphosting.runtime.http;

import static com.google.apphosting.runtime.http.HttpApiHostClient.REQUEST_ENDPOINT;

import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.http.HttpApiHostClient.Config;
import com.google.common.flogger.GoogleLogger;
import com.google.common.net.HostAndPort;
import java.util.OptionalInt;

/** Makes instances of {@link HttpApiHostClient}. */
public class HttpApiHostClientFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private HttpApiHostClientFactory() {}

  /**
   * Creates a new HttpApiHostClient instance to talk to the HTTP-based API server on the given host
   * and port. This method is called reflectively from ApiHostClientFactory.
   *
   * <p>The maximum number of concurrent connections can be configured by setting the {@code
   * APPENGINE_API_MAX_CONNECTIONS} environment variable to a positive integer. If set, this
   * value overrides the {@code maxConcurrentRpcs} parameter.
   *
   * @param hostAndPort The host and port of the API server.
   * @param maxConcurrentRpcs The default maximum number of concurrent RPCs, used if the
   *     environment variable is not set.
   * @return A new {@link APIHostClientInterface} instance.
   */
  public static APIHostClientInterface create(
      HostAndPort hostAndPort, OptionalInt maxConcurrentRpcs) {
    String url = "http://" + hostAndPort + REQUEST_ENDPOINT;
    String maxConnectionsEnv = System.getenv("APPENGINE_API_MAX_CONNECTIONS");
    if (maxConnectionsEnv != null) {
      try {
        int maxConnections = Integer.parseInt(maxConnectionsEnv);
        if (maxConnections > 0) {
          maxConcurrentRpcs = OptionalInt.of(maxConnections);
        }
      } catch (NumberFormatException e) {
        logger.atWarning().withCause(e).log(
            "Failed to parse APPENGINE_API_MAX_CONNECTIONS: %s", maxConnectionsEnv);
      }
    }
    Config config = Config.builder().setMaxConnectionsPerDestination(maxConcurrentRpcs).build();
    return HttpApiHostClient.create(url, config);
  }
}
