/*
 * Copyright 2022 Google LLC
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

package com.google.appengine.setup;

import static com.google.appengine.repackaged.com.google.common.base.MoreObjects.firstNonNull;

public class RuntimeUtils {
    private static final String VM_API_PROXY_HOST = "appengine.googleapis.com";
    private static final int VM_API_PROXY_PORT = 10001;
    public static final long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
    public static final long MAX_USER_API_CALL_WAIT_MS = 60 * 1000;

    /**
     * Returns the host:port of the API server.
     *
     * @return If environment variables API_HOST or API_PORT port are set the host and/or port is
     * calculated from them. Otherwise the default host:port is used.
     */
    public static String getApiServerAddress() {
        String server = firstNonNull(System.getenv("API_HOST"), VM_API_PROXY_HOST);
        String port = firstNonNull(System.getenv("API_PORT"), "" + VM_API_PROXY_PORT);
        return server + ":" + port;
    }
}
