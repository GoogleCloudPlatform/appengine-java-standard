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

package com.google.appengine.api.backends;

/**
 * {@link BackendService} allows you to retrieve information about
 * backend servers. Backend servers are long running addressable
 * servers that can be used for applications that need to keep
 * persistent state in ram between requests.
 * <p>
 * This API is deprecated and has been replaced by
 * {@link com.google.appengine.api.modules.ModulesService}. Please update your code as soon as
 * possible. See the modules documentation for more information:
 * https://developers.google.com/appengine/docs/java/modules/converting
 * <p>
 * This API allows you to retrieve information about the backend
 * handling the current request. It also allows you to to get the
 * address of a specific backend instance in such a way that a local
 * server is used during development and a production server is used
 * in production.
 *
 */
@Deprecated
public interface BackendService {
  // keep these in sync with http://b/3176627
  public static final String REQUEST_HEADER_BACKEND_REDIRECT = "X-AppEngine-BackendName";
  public static final String REQUEST_HEADER_INSTANCE_REDIRECT = "X-AppEngine-BackendInstance";

  // Keep in sync with:
  // com.google.apphosting.runtime.ApiProxyImpl.INSTANCE_ID_KEY/BACKEND_ID_KEY
  /**
   * Environment attribute key where the instance id is stored.
   *
   * @see BackendService#getCurrentInstance()
   */
  public static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";

  /**
   * Environment attribute key where the backend name is stored.
   *
   * @see BackendService#getCurrentBackend()
   */
  public static final String BACKEND_ID_ENV_ATTRIBUTE = "com.google.appengine.backend.id";

  // environment variable where the dev-appserver publishes the current mapping
  // between background servers and local ports
  public static final String DEVAPPSERVER_PORTMAPPING_KEY =
      "com.google.appengine.devappserver.portmapping";

  /**
   * Get the name of the backend handling the current request.
   *
   * @return The name of the backend or null if the request is not handled by a
   *         backend.
   */
  public String getCurrentBackend();

  /**
   * Get the instance handling the current request.
   *
   * @return The instance id or -1 if the request is not handled by a backend.
   */
  public int getCurrentInstance();

  /**
   * Get the address of a specific backend in such a way that a local server is
   * used during development and a production server is used in production.
   *
   * @param backend The name of the backend
   * @return The address of the backend
   */
  public String getBackendAddress(String backend);

  /**
   * Get the address of a specific backend instance in such a way that a local
   * instance is used during development and a production server instance is
   * used in production.
   *
   * @param backend The name of the backend
   * @param instance The instance id
   * @return The address of the backend instance
   */
  public String getBackendAddress(String backend, int instance);
}
