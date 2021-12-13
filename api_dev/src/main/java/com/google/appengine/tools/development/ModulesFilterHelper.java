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

package com.google.appengine.tools.development;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Support interface for {@link DevAppServerModulesFilter}.
 */
public interface ModulesFilterHelper {
  /**
   * This method guards access to module instances to limit the number of concurrent
   * requests. Each request running on a module instance must acquire a serving permit.
   * If no permits are available a 500 response should be sent.
   *
   * @param moduleOrBackendName The module or backend for which to acquire a permit.
   * @param instanceNumber The server instance for which to acquire a permit.
   * @param allowQueueOnBackends If set to false the method will return
   *        instantly, if set to true (and the specified server allows pending
   *        queues) this method can block for up to 10 s waiting for a serving
   *        permit to become available.
   * @return true if a permit was acquired, false otherwise
   */
  boolean acquireServingPermit(String moduleOrBackendName, int instanceNumber,
      boolean allowQueueOnBackends);
  /**
   * Acquires a serving permit for an instance with available capacity and returns the
   * instance id. If no instance has capacity this returns -1.
   * <p>
   * For backends which support queued requests this may block for a limited
   * time waiting for an instance to become available (see {@link
   * AbstractBackendServers#getAndReserveFreeInstance} for details).
   *
   * Supported for modules that support load balancing (currently {@link ManualModule}).
   * The client can check with {@link #isLoadBalancingInstance(String, int)}.
   *
   * @param requestedModuleOrBackendname Name of the requested module or backend.
   * @return the instance id of an available server instance, or -1 if no
   *         instance is available.
   */
  int getAndReserveFreeInstance(String requestedModuleOrBackendname);

  /**
   * Returns a serving permit after a request has completed.
   *
   * @param moduleOrBackendName The server name
   * @param instance The server instance
   */
  public void returnServingPermit(String moduleOrBackendName, int instance);

  /**
   * Verifies if a specific module instance is configured.
   *
   * @param moduleOrBackendName The module or backend name
   * @param instance The module instance
   * @return true if the module instance is configured and false otherwise.
   */
  boolean checkInstanceExists(String moduleOrBackendName, int instance);

  /**
   * Verifies if a specific module or backend is configured.
   *
   * @param moduleOrBackendName The module or backend name
   * @return true if the module is configured and false otherwise.
   */
  boolean checkModuleExists(String moduleOrBackendName);

  /**
   * Verifies if a specific existing module or backend is stopped.
   *
   * @param moduleOrBackendName The module or backend name
   * @return true if the module is stopped, false otherwise.
   */
  boolean checkModuleStopped(String moduleOrBackendName);

  /**
   * Verifies if a specific existing module or backend instance is stopped.
   *
   * @param moduleOrBackendName The module or backedn name
   * @param instance The module instance
   * @return true if the module instance is stopped and false otherwise.
   */
  boolean checkInstanceStopped(String moduleOrBackendName, int instance);

  /**
   * Forward a request to a specified module or backend instance. Calls the
   * request dispatcher for the requested instance with the instance
   * context. The caller must hold a serving permit for the requested
   * instance before calling this method.
   */
  void forwardToInstance(String requestedModuleOrBackendName, int instance,
      HttpServletRequest hrequest, HttpServletResponse hresponse)
          throws IOException, ServletException;

  /**
   * Returns true if the specified module or backend instance is a load balancing
   * instance which will forward requests to an available instance.
   *
   * @param moduleOrBackendName The module or backend name
   * @param instance The requested instance which can be -1.
   */
  boolean isLoadBalancingInstance(String moduleOrBackendName, int instance);

  /**
   * Returns true if internally generated "/_ah/start" requests are provided
   * for the specified module or backend instance.
   * <p>
   * Http "/_ah/start" requests for instances where this returns true are presumed to be
   * internally generated and receive special treatment by {@link DevAppServerModulesFilter}.
   * Requests to "/_ah/start" for other instances are treated as normal requests.
   *
   * @param moduleOrBackendName The module or backend name
   * @param instance The module instance which can be -1.
   */
  boolean expectsGeneratedStartRequests(String moduleOrBackendName, int instance);

  /**
   * Returns the port for the specified module of backend instance.
   *
   * @param moduleOrBackendName The module or backend name
   * @param instance The requested instance which can be -1.
   */
  int getPort(String moduleOrBackendName, int instance);
}
