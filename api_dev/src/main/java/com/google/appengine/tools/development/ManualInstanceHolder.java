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

import com.google.appengine.tools.development.ApplicationConfigurationManager.ModuleConfigurationHandle;
import com.google.appengine.tools.development.InstanceStateHolder.InstanceState;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link InstanceHolder} for a {@link ManualModule}.
 */
class ManualInstanceHolder extends AbstractInstanceHolder  {
  // maximum time a request can wait for a start request to complete
  private static final int MAX_START_QUEUE_TIME_MS = 30 * 1000;
  private static final GoogleLogger LOGGER = GoogleLogger.forEnclosingClass();

  private final String moduleName;
  private final InstanceStateHolder stateHolder;
  private final InstanceHelper instanceHelper;
  private volatile CountDownLatch startRequestLatch;

  // Set by setConfiguration
  private ModuleConfigurationHandle moduleConfigurationHandle;
  private String serverInfo;
  private File externalResourceDir;
  private String address;
  private Map<String, Object> containerConfigProperties;
  private DevAppServer devAppServer;

  // Set by createConnection after Jetty either accepts our
  // choice of port or selects a port. We remember this value
  // so we can retain the same port after stopServing.
  Integer port;

  /**
   * Construct an instance holder.
   * @param moduleName the module name or 'default'
   * @param containerService for the instance.
   * @param instance nonnegative instance number or
   *     {link {@link LocalEnvironment#MAIN_INSTANCE}.
   * @param stateHolder holder for the instance state.
   * @param instanceHelper helper for operating on the instance.
   */
  ManualInstanceHolder(String moduleName, ContainerService containerService, int instance,
      InstanceStateHolder stateHolder, InstanceHelper instanceHelper) {
    super(containerService, instance);
    this.moduleName = moduleName;
    this.stateHolder = stateHolder;
    this.instanceHelper = instanceHelper;
    this.startRequestLatch = new CountDownLatch(1);
  }

  @Override
  public boolean isLoadBalancingInstance() {
    return isMainInstance();
  }

  @Override
  public boolean expectsGeneratedStartRequest() {
    return !isMainInstance();
  }

  @Override
  public String toString() {
    return "ManualServerInstanceHolder: containerservice=" + getContainerService() + " instance="
        + getInstance();
  }

  @Override
  public void startUp() throws Exception {
    stateHolder.testAndSet(InstanceState.INITIALIZING, InstanceState.SHUTDOWN);
    getContainerService().startup();
    stateHolder.testAndSet(InstanceState.STOPPED, InstanceState.INITIALIZING);
    startServing();
  }

  @Override
  public void createConnection() throws Exception {
    super.createConnection();
    if (port != null && port.intValue() != getContainerService().getPort()) {
      throw new IllegalStateException("Port has been reassigned for"
          + " module=" + moduleName
          + " instance=" + getInstance()
          + " original port = " + port
          + " new port=" + getContainerService().getPort());
    }
    this.port = getContainerService().getPort();
  }

  void setConfiguration(ModuleConfigurationHandle moduleConfigurationHandle,
      String serverInfo, File externalResourceDir, String address,
      Map<String, Object> containerConfigProperties, DevAppServer devAppServer) {
    this.moduleConfigurationHandle = moduleConfigurationHandle;
    this.serverInfo = serverInfo;
    this.externalResourceDir = externalResourceDir;
    this.address = address;
    this.containerConfigProperties = containerConfigProperties;
    this.devAppServer = devAppServer;
  }

  LocalServerEnvironment doConfigure() {
    ContainerService containerService = getContainerService();
    LocalServerEnvironment result = containerService.configure(serverInfo, address,
        getPortForDoConfigure(), moduleConfigurationHandle, externalResourceDir,
        containerConfigProperties, getInstance(), devAppServer);
    return result;
  }

  /**
   * Returns the port to pass to doConfigure.
   * <p>
   * The port is chosen as follows
   * <ol>
   * <li> If a port is already assigned we use it. This indicates we are
   *      re starting the instance.
   * <li> Otherwise if the user specified port using service properties we use that.
   * <li> Otherwise we use the value 0 which causes the container service to
   *      select a port.
   * </li>
   * </ol>
   */
  private int getPortForDoConfigure() {
    if (port == null) {
      return DevAppServerPortPropertyHelper.getPort(moduleName,
          getInstance(), devAppServer.getServiceProperties());
    } else {
      return port;
    }
  }

  void stopServing() throws Exception {
    if (isMainInstance()) {
      stateHolder.testAndSet(InstanceState.STOPPED, InstanceState.RUNNING);
    } else {
      instanceHelper.shutdown();
      stateHolder.testAndSet(InstanceState.INITIALIZING, InstanceState.SHUTDOWN);
      startRequestLatch = new CountDownLatch(1);
      doConfigure();
      createConnection();
      getContainerService().startup();
      stateHolder.testAndSet(InstanceState.STOPPED, InstanceState.INITIALIZING);
    }
   }

  void startServing() throws Exception {
    if (!stateHolder.test(InstanceState.STOPPED)) {
      throw new IllegalStateException("stopServing state=" + stateHolder + " module=" + moduleName);
    }
    if (isMainInstance()) {
      stateHolder.testAndSet(InstanceState.RUNNING, InstanceState.STOPPED);
    } else {
      stateHolder.testAndSet(InstanceState.SLEEPING, InstanceState.STOPPED);
      sendStartRequest();
    }
  }

  void requireState(String operation, InstanceState requiredState) {
    stateHolder.requireState(operation, requiredState);
  }

  private void sendStartRequest() {
    instanceHelper.sendStartRequest(() -> startRequestLatch.countDown());
  }

  @Override
  public boolean acquireServingPermit() {
    LOGGER.atFinest().log("trying to get serving permit for %d.%s", getInstance(), moduleName);
    int maxWaitTime = 0;
    synchronized (stateHolder) {
      if (!stateHolder.acceptsConnections()) {
        LOGGER.atFinest().log("%s: got request but instance is not in a serving state", moduleName);
        return false;
      }

      if (stateHolder.test(InstanceState.SLEEPING)) {
        LOGGER.atFinest().log("%s: waking up sleeping instance", moduleName);
        sendStartRequest();
      }

      // Modeled after backends.
      if (stateHolder.test(InstanceState.RUNNING_START_REQUEST)) {
        maxWaitTime = MAX_START_QUEUE_TIME_MS;
      }
    }
    try {
      boolean gotPermit = startRequestLatch.await(maxWaitTime, TimeUnit.MILLISECONDS);
      LOGGER.atFinest().log(
          "%s.%s: tried to get serving permit, timeout=%d success=%s",
          getInstance(), moduleName, maxWaitTime, gotPermit);
      return gotPermit;
    } catch (InterruptedException e) {
      LOGGER.atFinest().log(
          "%s.%s: got interrupted while waiting for serving permit", getInstance(), moduleName);
      return false;
    }
  }

  @Override
  public boolean isStopped() {
    return stateHolder.test(InstanceState.STOPPED);
  }

}
