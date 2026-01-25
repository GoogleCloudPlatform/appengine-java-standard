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

import com.google.common.collect.ImmutableMap;

/**
 * Default deadline maps for API calls, distinguishing between online and offline requests.
 *
 * <p>In App Engine, requests are treated as either 'online' or 'offline', with different request
 * time limits and API call deadline limits applied to each.
 *
 * <p>An <b>online request</b> is a standard HTTP request from an end-user and must typically complete
 * within 60 seconds.
 *
 * <p>An <b>offline request</b> is one that is not directly serving an incoming, user-initiated HTTP
 * request and is allowed to run for a longer duration (e.g., 10 minutes or more). This typically
 * includes background tasks such as:
 *
 * <ul>
 *   <li>Task Queue tasks (from push or pull queues)
 *   <li>Cron jobs
 * </ul>
 *
 * <p>Because background tasks often perform long-running operations like data processing or batch
 * updates, they require longer deadlines. This class provides separate deadline maps for API calls
 * made during online vs. offline requests. For example, API calls like URL Fetch ({@code urlfetch})
 * or Cloud SQL ({@code rdbms}) have a much higher maximum deadline (e.g., 600 seconds) in the
 * offline maps, allowing background tasks to complete long-running API calls without timing out.
 */
public class ApiDeadlineMap {

  /** Map of package name to default API call deadline in seconds for online requests. */
  public static final ImmutableMap<String, Double> DEFAULT_DEADLINE_MAP =
      ImmutableMap.<String, Double>builder()
          .put("app_config_service", 60.0)
          .put("blobstore", 15.0)
          .put("datastore_v3", 60.0)
          .put("datastore_v4", 60.0)
          .put("file", 30.0)
          .put("images", 30.0)
          .put("logservice", 60.0)
          .put("mail", 30.0)
          .put("modules", 60.0)
          .put("rdbms", 60.0)
          .put("remote_socket", 60.0)
          .put("search", 10.0)
          .put("stubby", 10.0)
          .buildOrThrow();

  /** Map of package name to maximum API call deadline in seconds for online requests. */
  public static final ImmutableMap<String, Double> MAX_DEADLINE_MAP =
      ImmutableMap.<String, Double>builder()
          .put("app_config_service", 60.0)
          .put("blobstore", 30.0)
          .put("datastore_v3", 270.0)
          .put("datastore_v4", 270.0)
          .put("file", 60.0)
          .put("images", 30.0)
          .put("logservice", 60.0)
          .put("mail", 60.0)
          .put("modules", 60.0)
          .put("rdbms", 60.0)
          .put("remote_socket", 60.0)
          .put("search", 60.0)
          .put("stubby", 60.0)
          .put("taskqueue", 30.0)
          .put("urlfetch", 60.0)
          .buildOrThrow();

  /** Map of package name to default API call deadline in seconds for offline requests. */
  public static final ImmutableMap<String, Double> OFFLINE_DEFAULT_DEADLINE_MAP =
      ImmutableMap.<String, Double>builder()
          .put("app_config_service", 60.0)
          .put("blobstore", 15.0)
          .put("datastore_v3", 60.0)
          .put("datastore_v4", 60.0)
          .put("file", 30.0)
          .put("images", 30.0)
          .put("logservice", 60.0)
          .put("mail", 30.0)
          .put("modules", 60.0)
          .put("rdbms", 60.0)
          .put("remote_socket", 60.0)
          .put("search", 10.0)
          .put("stubby", 10.0)
          .buildOrThrow();

  /** Map of package name to maximum API call deadline in seconds for offline requests. */
  public static final ImmutableMap<String, Double> OFFLINE_MAX_DEADLINE_MAP =
      ImmutableMap.<String, Double>builder()
          .put("app_config_service", 60.0)
          .put("blobstore", 30.0)
          .put("datastore_v3", 270.0)
          .put("datastore_v4", 270.0)
          .put("file", 60.0)
          .put("images", 30.0)
          .put("logservice", 60.0)
          .put("mail", 60.0)
          .put("modules", 60.0)
          .put("rdbms", 600.0)
          .put("remote_socket", 60.0)
          .put("search", 60.0)
          .put("stubby", 600.0)
          .put("taskqueue", 30.0)
          .put("urlfetch", 600.0)
          .buildOrThrow();

  private ApiDeadlineMap() {}
}
