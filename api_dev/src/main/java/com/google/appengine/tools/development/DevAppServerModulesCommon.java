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

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.backends.dev.LocalServerController;
import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This filter intercepts all request sent to all module instances.
 *
 *  There are 6 different request types that this filter will see:
 *
 *  * DIRECT_BACKEND_REQUEST: a client request sent to a serving (non load balancing)
 *    backend instance.
 *
 *  * REDIRECT_REQUESTED: a request requesting a redirect in one of three ways
 *     1) The request contains a BackendService.REQUEST_HEADER_BACKEND_REDIRECT
 *        header or parameter
 *     2) The request is sent to a load balancing module instance.
 *     3) The request is sent to a load balancing backend instance.
 *
 *    If the request specifies an instance with the BackendService.REQUEST_HEADER_INSTANCE_REDIRECT
 *    request header or parameter the filter verifies that the instance is available,
 *    obtains a serving permit and forwards the requests. If the instance is not available
 *    the filter responds with a 500 error.
 *
 *    If the request does not specify an instance the filter picks one,
 *    obtains a serving permit, and and forwards the request. If no instance is
 *    available this filter responds with a 500 error.
 *
 *  * DIRECT_MODULE_REQUEST: a request sent directly to the listening port of a
 *    specific serving module instance. The filter verifies that the instance is
 *    available, obtains a serving permit and sends the request to the handler.
 *    If no instance is available this filter responds with a 500 error.
 *
 *  * REDIRECTED_BACKEND_REQUEST: a request redirected to a backend instance.
 *    The filter sends the request to the handler. The serving permit has
 *    already been obtained by this filter when performing the redirect.
 *
 *  * REDIRECTED_MODULE_REQUEST: a request redirected to a specific module instance.
 *    The filter sends the request to the handler. The serving permit has
 *    already been obtained when by filter performing the redirect.
 *
 * * STARTUP_REQUEST: Internally generated startup request. The filter
 *   passes the request to the handler without obtaining a serving permit.
 *
 *
 */
public class DevAppServerModulesCommon {

  protected static final String BACKEND_REDIRECT_ATTRIBUTE = "com.google.appengine.backend.BackendName";
  protected static final String BACKEND_INSTANCE_REDIRECT_ATTRIBUTE =
      "com.google.appengine.backend.BackendInstance";
  @VisibleForTesting
  protected static final String MODULE_INSTANCE_REDIRECT_ATTRIBUTE =
      "com.google.appengine.module.ModuleInstance";

  protected final BackendServers backendServersManager;
  protected final ModulesService modulesService;

  protected final Logger logger = Logger.getLogger(DevAppServerModulesFilter.class.getName());

  @VisibleForTesting
  protected DevAppServerModulesCommon(BackendServers backendServers, ModulesService modulesService) {
    this.backendServersManager = backendServers;
    this.modulesService = modulesService;
  }

  public DevAppServerModulesCommon() {
    this(BackendServers.getInstance(), ModulesServiceFactory.getModulesService());
  }

  protected boolean isLoadBalancingRequest() {
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    String module = modulesService.getCurrentModule();
    int instance = getCurrentModuleInstance();
    return modulesFilterHelper.isLoadBalancingInstance(module, instance);
  }

  protected boolean expectsGeneratedStartRequests(String backendName,
      int requestPort) {
    String moduleOrBackendName = backendName;
    if (moduleOrBackendName == null) {
      moduleOrBackendName = modulesService.getCurrentModule();
    }

    int instance = backendName == null ? getCurrentModuleInstance() :
      backendServersManager.getServerInstanceFromPort(requestPort);
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    return modulesFilterHelper.expectsGeneratedStartRequests(moduleOrBackendName, instance);
  }

  /**
   * Returns the instance id for the module instance handling the current request or -1
   * if a back end server or load balancing server is handling the request.
   */
  protected int getCurrentModuleInstance() {
    String instance = "-1";
    try {
      instance = modulesService.getCurrentInstanceId();
    } catch (ModulesException me) {
      logger.log(Level.FINEST, "Ignoring Exception getting module instance and continuing", me);
    }
    return Integer.parseInt(instance);
  }

  protected ModulesFilterHelper getModulesFilterHelper() {
    Map<String, Object> attributes = ApiProxy.getCurrentEnvironment().getAttributes();
    return (ModulesFilterHelper) attributes.get(DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY);
  }

  /**
   * Inject information about the current backend server setup so it is available
   * to the BackendService API. This information is stored in the threadLocalAttributes
   * in the current environment.
   *
   * @param backendName The server that is handling the request
   * @param instance The server instance that is handling the request
   */
  protected void injectApiInfo(String backendName, int instance) {
    Map<String, String> portMapping = backendServersManager.getPortMapping();
    if (portMapping == null) {
      throw new IllegalStateException("backendServersManager.getPortMapping() is null");
    }
    injectBackendServiceCurrentApiInfo(backendName, instance, portMapping);

    Map<String, Object> threadLocalAttributes = ApiProxy.getCurrentEnvironment().getAttributes();

    // We inject backendServersManager which is not injected by
    // injectBackendServiceCurrentApiInfo as it is not needed by BackendsService
    // but is needed by the admin console for handling HTTP requests.
    if (!portMapping.isEmpty()) {
      threadLocalAttributes.put(
          LocalServerController.BACKEND_CONTROLLER_ATTRIBUTE_KEY, backendServersManager);
    }

    threadLocalAttributes.put(
        ModulesController.MODULES_CONTROLLER_ATTRIBUTE_KEY,
        Modules.getInstance());
  }

  /**
   * Sets up {@link ApiProxy} attributes needed {@link BackendService}.
   */
  public static void injectBackendServiceCurrentApiInfo(
      String backendName, int backendInstance, Map<String, String> portMapping) {
    Map<String, Object> threadLocalAttributes = ApiProxy.getCurrentEnvironment().getAttributes();
    if (backendInstance != -1) {
      threadLocalAttributes.put(BackendService.INSTANCE_ID_ENV_ATTRIBUTE, backendInstance + "");
    }
    if (backendName != null) {
      threadLocalAttributes.put(BackendService.BACKEND_ID_ENV_ATTRIBUTE, backendName);
    }
    threadLocalAttributes.put(BackendService.DEVAPPSERVER_PORTMAPPING_KEY, portMapping);
  }

  @VisibleForTesting
  public static enum RequestType {
    DIRECT_MODULE_REQUEST, REDIRECT_REQUESTED, DIRECT_BACKEND_REQUEST, REDIRECTED_BACKEND_REQUEST,
    REDIRECTED_MODULE_REQUEST, STARTUP_REQUEST;
  }
}
