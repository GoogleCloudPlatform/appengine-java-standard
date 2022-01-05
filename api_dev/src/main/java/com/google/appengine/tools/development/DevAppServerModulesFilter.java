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
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
public class DevAppServerModulesFilter implements Filter {

  static final String BACKEND_REDIRECT_ATTRIBUTE = "com.google.appengine.backend.BackendName";
  static final String BACKEND_INSTANCE_REDIRECT_ATTRIBUTE =
      "com.google.appengine.backend.BackendInstance";
  @VisibleForTesting
  static final String MODULE_INSTANCE_REDIRECT_ATTRIBUTE =
      "com.google.appengine.module.ModuleInstance";

  // In prod instances return 500 (Internal Server Error) when busy
  static final int INSTANCE_BUSY_ERROR_CODE = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

  // In prod modules return 404 (Not found) when stopped
  static final int MODULE_STOPPED_ERROR_CODE = HttpServletResponse.SC_NOT_FOUND;

  static final int MODULE_MISSING_ERROR_CODE = HttpServletResponse.SC_BAD_GATEWAY;

  private final AbstractBackendServers backendServersManager;
  private final ModulesService modulesService;

  private final Logger logger = Logger.getLogger(DevAppServerModulesFilter.class.getName());

  @VisibleForTesting
  DevAppServerModulesFilter(AbstractBackendServers backendServers, ModulesService modulesService) {
    this.backendServersManager = backendServers;
    this.modulesService = modulesService;
  }

  public DevAppServerModulesFilter() {
    this(BackendServers.getInstance(), ModulesServiceFactory.getModulesService());
  }

  @Override
  public void destroy() {
  }

  /**
   * Main filter method. All request to the dev-appserver pass this method.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest hrequest = (HttpServletRequest) request;
    HttpServletResponse hresponse = (HttpServletResponse) response;
    RequestType requestType = getRequestType(hrequest);
    switch (requestType) {
      case DIRECT_MODULE_REQUEST:
        doDirectModuleRequest(hrequest, hresponse, chain);
        break;
      case REDIRECT_REQUESTED:
        doRedirect(hrequest, hresponse);
        break;
      case DIRECT_BACKEND_REQUEST:
        doDirectBackendRequest(hrequest, hresponse, chain);
        break;
      case REDIRECTED_BACKEND_REQUEST:
        doRedirectedBackendRequest(hrequest, hresponse, chain);
        break;
      case REDIRECTED_MODULE_REQUEST:
        doRedirectedModuleRequest(hrequest, hresponse, chain);
        break;
      case STARTUP_REQUEST:
        doStartupRequest(hrequest, hresponse, chain);
        break;
    }
  }

  /**
   * Determine the request type for a given request.
   *
   * @param hrequest The Request to categorize
   * @return The RequestType of the request
   */
  @VisibleForTesting
  RequestType getRequestType(HttpServletRequest hrequest) {
    int instancePort = hrequest.getServerPort();
    String backendServerName = backendServersManager.getServerNameFromPort(instancePort);
    // Note that for redirected requests instancePort and hence backendServerName
    // applies to the redirecting port and must be used with care. Also
    // note that the order of evaluation is important here. In particular it is key
    // we check for redirected requests prior to checking for redirect requests as
    // a forwarded redirected request will have both sets of headers.
    if (hrequest.getRequestURI().equals("/_ah/start") &&
        expectsGeneratedStartRequests(backendServerName, instancePort)) {
      return RequestType.STARTUP_REQUEST;
    } else if (hrequest.getAttribute(BACKEND_REDIRECT_ATTRIBUTE) instanceof String) {
      // this request was redirected here from a different instance
      return RequestType.REDIRECTED_BACKEND_REQUEST;
    } else if (hrequest.getAttribute(MODULE_INSTANCE_REDIRECT_ATTRIBUTE) instanceof Integer) {
      // this request was redirected here from a different instance
      return RequestType.REDIRECTED_MODULE_REQUEST;
    } else if (backendServerName != null) {
      // request was to a backend server, check out if replica was specified
      int backendInstance = backendServersManager.getServerInstanceFromPort(instancePort);
      if (backendInstance == -1) {
        // no replica specified, redirect needed
        return RequestType.REDIRECT_REQUESTED;
      } else {
        return RequestType.DIRECT_BACKEND_REQUEST;
      }
    } else {
      // request to a non-backend instance, check if the user want us to redirect
      String serverRedirectHeader =
          getHeaderOrParameter(hrequest, BackendService.REQUEST_HEADER_BACKEND_REDIRECT);
      if (serverRedirectHeader == null && !isLoadBalancingRequest()) {
        return RequestType.DIRECT_MODULE_REQUEST;
      } else {
        return RequestType.REDIRECT_REQUESTED;
      }
    }
  }

  private boolean isLoadBalancingRequest() {
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    String module = modulesService.getCurrentModule();
    int instance = getCurrentModuleInstance();
    return modulesFilterHelper.isLoadBalancingInstance(module, instance);
  }

  private boolean expectsGeneratedStartRequests(String backendName,
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
  private int getCurrentModuleInstance() {
    String instance = "-1";
    try {
      instance = modulesService.getCurrentInstanceId();
    } catch (ModulesException me) {
      logger.log(Level.FINEST, "Ignoring Exception getting module instance and continuing", me);
    }
    return Integer.parseInt(instance);
  }

  private ModulesFilterHelper getModulesFilterHelper() {
    Map<String, Object> attributes = ApiProxy.getCurrentEnvironment().getAttributes();
    return (ModulesFilterHelper) attributes.get(DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY);
  }

  private boolean tryToAcquireServingPermit(
      String moduleOrBackendName, int instance, HttpServletResponse hresponse) throws IOException {
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    // Instance specified, check if exists.
    if (!modulesFilterHelper.checkInstanceExists(moduleOrBackendName, instance)) {
      String msg =
          String.format("Got request to non-configured instance: %d.%s", instance,
              moduleOrBackendName);
      logger.warning(msg);
      hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, msg);
      return false;
    }
    // Check if this specific instance is stopped.
    if (modulesFilterHelper.checkInstanceStopped(moduleOrBackendName, instance)) {
      String msg =
          String.format("Got request to stopped instance: %d.%s", instance, moduleOrBackendName);
      logger.warning(msg);
      hresponse.sendError(MODULE_STOPPED_ERROR_CODE, msg);
      return false;
    }

    // Check if this specific instance is busy.
    if (!modulesFilterHelper.acquireServingPermit(moduleOrBackendName, instance, true)) {
      String msg = String.format(
          "Got request to module %d.%s but the instance is busy.", instance, moduleOrBackendName);
      logger.finer(msg);
      hresponse.sendError(INSTANCE_BUSY_ERROR_CODE, msg);
      return false;
    }

    return true;
  }

  /**
   * Request that contains either headers or parameters specifying that it
   * should be forwarded either to a specific module or backend instance,
   * or to a free instance.
   */
  private void doRedirect(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException, ServletException {
    String moduleOrBackendName =
        backendServersManager.getServerNameFromPort(hrequest.getServerPort());
    if (moduleOrBackendName == null) {
      moduleOrBackendName =
          getHeaderOrParameter(hrequest, BackendService.REQUEST_HEADER_BACKEND_REDIRECT);
    }

    // We get sent here in 3 cases
    // 1) We are instance -1 of a backendserver so moduleOrBackendName != null
    // 2) Our caller set BackendService.REQUEST_HEADER_BACKEND_REDIRECT (and
    //    possibly BackendService.REQUEST_HEADER_INSTANCE_REDIRECT so
    //    moduleOrBackendName != null
    // 3) We are a load balancing instance of a module moduleOrBackendName == null
    boolean isLoadBalancingModuleInstance = false;
    if (moduleOrBackendName == null) {
      ModulesService modulesService = ModulesServiceFactory.getModulesService();
      moduleOrBackendName = modulesService.getCurrentModule();
      isLoadBalancingModuleInstance = true;
    }
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    int instance = getInstanceIdFromRequest(hrequest);
    logger.finest(String.format("redirect request to module: %d.%s", instance,
        moduleOrBackendName));
    if (instance != -1) {
      if (!tryToAcquireServingPermit(moduleOrBackendName, instance, hresponse)) {
        // instanceAcceptsConnections acquired a permit when it returned true.
        return;
      }
    } else {
      // Backend or module specified, check if exists
      if (!modulesFilterHelper.checkModuleExists(moduleOrBackendName)) {
        String msg = String.format("Got request to non-configured module: %s", moduleOrBackendName);
        logger.warning(msg);
        hresponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, msg);
        return;
      }
      // check if this Backend or module is stopped
      if (modulesFilterHelper.checkModuleStopped(moduleOrBackendName)) {
        String msg = String.format("Got request to stopped module: %s", moduleOrBackendName);
        logger.warning(msg);
        hresponse.sendError(MODULE_STOPPED_ERROR_CODE, msg);
        return;
      }
      // no instance specified, try to find and reserve a free instance
      instance = modulesFilterHelper.getAndReserveFreeInstance(moduleOrBackendName);
      if (instance == -1) {
        String msg = String.format("all instances of module %s are busy", moduleOrBackendName);
        logger.finest(msg);
        hresponse.sendError(INSTANCE_BUSY_ERROR_CODE, msg);
        return;
      }
    }

    // if we make it down here we have a module or backend name and a reserved instance
    try {
      if (isLoadBalancingModuleInstance) {
        logger.finer(String.format("forwarding request to module: %d.%s", instance,
            moduleOrBackendName));
        hrequest.setAttribute(MODULE_INSTANCE_REDIRECT_ATTRIBUTE, Integer.valueOf(instance));
      } else {
        logger.finer(String.format("forwarding request to backend: %d.%s", instance,
            moduleOrBackendName));
        hrequest.setAttribute(BACKEND_REDIRECT_ATTRIBUTE, moduleOrBackendName);
        hrequest.setAttribute(BACKEND_INSTANCE_REDIRECT_ATTRIBUTE, Integer.valueOf(instance));
      }
      // forward the request
      modulesFilterHelper.forwardToInstance(moduleOrBackendName, instance, hrequest, hresponse);
    } finally {
      // return the serving reservation
      modulesFilterHelper.returnServingPermit(moduleOrBackendName, instance);
    }
  }

  private void doDirectBackendRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    int instancePort = hrequest.getServerPort();
    String requestedBackend = backendServersManager.getServerNameFromPort(instancePort);
    int requestedInstance = backendServersManager.getServerInstanceFromPort(instancePort);
    injectApiInfo(requestedBackend, requestedInstance);
    doDirectRequest(requestedBackend, requestedInstance, hrequest, hresponse, chain);
  }

  private void doDirectModuleRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    String requestedModule =  modulesService.getCurrentModule();
    int requestedInstance = getCurrentModuleInstance();
    injectApiInfo(null, -1);
    doDirectRequest(requestedModule, requestedInstance, hrequest, hresponse, chain);
  }

  private void doDirectRequest(String moduleOrBackendName, int instance,
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    logger.finest("request to specific module instance: " + instance
        + "." + moduleOrBackendName);

    if (!tryToAcquireServingPermit(moduleOrBackendName, instance, hresponse)) {
      return;
    }
    try {
      logger.finest("Acquired serving permit for: " + instance + "."
          + moduleOrBackendName);
      // add thread local information required for the ModulesService
      injectApiInfo(null, -1);
      chain.doFilter(hrequest, hresponse);
    } finally {
      // we got the lock, release it when the request is done
      ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
      modulesFilterHelper.returnServingPermit(moduleOrBackendName, instance);
    }
  }

  /**
   * A request forwarded from a different instance. The forwarding instance is
   * responsible for acquiring the serving permit. All we need to do is to add
   * the ServerApiInfo and forward the request along the chain.
   */
  private void doRedirectedBackendRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    // N.B.: See bug http://b/4442244 happened if you see class cast
    // exceptions below.  removed some broken code to deal with them.
    String backendServer = (String) hrequest.getAttribute(BACKEND_REDIRECT_ATTRIBUTE);
    Integer instance = (Integer) hrequest.getAttribute(BACKEND_INSTANCE_REDIRECT_ATTRIBUTE);
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    int port = modulesFilterHelper.getPort(backendServer, instance);
    LocalEnvironment.setPort(ApiProxy.getCurrentEnvironment().getAttributes(), port);
    injectApiInfo(backendServer, instance);
    logger.finest("redirected request to backend server instance: " + instance + "."
        + backendServer);
    chain.doFilter(hrequest, hresponse);
  }

  /**
   * A request forwarded from a different instance. The forwarding instance is
   * responsible for acquiring the serving permit. All we need to do is to add
   * the ServerApiInfo and forward the request along the chain.
   */
  private void doRedirectedModuleRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    // N.B.: See bug http://b/4442244 happened if you see class cast
    // exceptions below.  removed some broken code to deal with them.
    Integer instance = (Integer) hrequest.getAttribute(MODULE_INSTANCE_REDIRECT_ATTRIBUTE);
    ModulesFilterHelper modulesFilterHelper = getModulesFilterHelper();
    String moduleName = modulesService.getCurrentModule();
    int port = modulesFilterHelper.getPort(moduleName, instance);
    LocalEnvironment.setInstance(ApiProxy.getCurrentEnvironment().getAttributes(), instance);
    LocalEnvironment.setPort(ApiProxy.getCurrentEnvironment().getAttributes(), port);
    injectApiInfo(null, -1);
    logger.finest("redirected request to module instance: " + instance + "." +
        ApiProxy.getCurrentEnvironment().getModuleId() + " " +
        ApiProxy.getCurrentEnvironment().getVersionId());
    chain.doFilter(hrequest, hresponse);
  }

  /**
   * Startup requests do not require any serving permits and can be forwarded
   * along the chain straight away.
   */
  private void doStartupRequest(
      HttpServletRequest hrequest, HttpServletResponse hresponse, FilterChain chain)
      throws IOException, ServletException {
    int instancePort = hrequest.getServerPort();
    String backendServer = backendServersManager.getServerNameFromPort(instancePort);
    int instance = backendServersManager.getServerInstanceFromPort(instancePort);
    logger.finest("startup request to: " + instance + "." + backendServer);
    injectApiInfo(backendServer, instance);
    chain.doFilter(hrequest, hresponse);
  }

  @SuppressWarnings("unused")
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  /**
   * Inject information about the current backend server setup so it is available
   * to the BackendService API. This information is stored in the threadLocalAttributes
   * in the current environment.
   *
   * @param backendName The server that is handling the request
   * @param instance The server instance that is handling the request
   */
  private void injectApiInfo(String backendName, int instance) {
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

  /**
   * Checks the request headers and request parameters for the specified key
   */
  @VisibleForTesting
  static String getHeaderOrParameter(HttpServletRequest request, String key) {
    String value = request.getHeader(key);
    if (value != null) {
      return value;
    }
    if ("GET".equals(request.getMethod())) {
      // Do not call this method for POST requests!  That will cause
      // Jetty to eat the input stream and parse it as parameters,
      // which may not be what later filters or the final servlet
      // expect to happen.
      return request.getParameter(key);
    }
    return null;
  }

  /**
   * Checks request headers and parameters to see if an instance id was
   * specified.
   */
  @VisibleForTesting
  static int getInstanceIdFromRequest(HttpServletRequest request) {
    try {
      return Integer.parseInt(
          getHeaderOrParameter(request, BackendService.REQUEST_HEADER_INSTANCE_REDIRECT));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  @VisibleForTesting
  static enum RequestType {
    DIRECT_MODULE_REQUEST, REDIRECT_REQUESTED, DIRECT_BACKEND_REQUEST, REDIRECTED_BACKEND_REQUEST,
    REDIRECTED_MODULE_REQUEST, STARTUP_REQUEST;
  }
}
