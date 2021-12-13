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
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual {@link Module} implementation.
 */
public class ManualModule extends AbstractModule<ManualInstanceHolder> {
  // Counter for round robin load balancing.
  private final AtomicInteger instanceCounter = new AtomicInteger();

  ManualModule(ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      String address, DevAppServer devAppServer, AppEngineWebXml appEngineWebXml) {
    super(moduleConfigurationHandle, serverInfo, null, address, devAppServer,
        makeInstanceHolders(moduleConfigurationHandle, appEngineWebXml));
  }

  private static List<ManualInstanceHolder> makeInstanceHolders(
      ModuleConfigurationHandle moduleConfigurationHandle, AppEngineWebXml appEngineWebXml) {
    String instancesString = appEngineWebXml.getManualScaling().getInstances();
    int instances = instancesString == null ? 0 : Integer.parseInt(instancesString);
    if (instances < 0) {
      throw new AppEngineConfigException("Invalid instances " + instances + " in file "
        + moduleConfigurationHandle.getModule().getAppEngineWebXmlFile());
    }

    ImmutableList.Builder<ManualInstanceHolder> listBuilder = ImmutableList.builder();
    for (int ix = LocalEnvironment.MAIN_INSTANCE; ix < instances; ix++) {
      String moduleName = moduleConfigurationHandle.getModule().getModuleName();
      InstanceStateHolder stateHolder = new InstanceStateHolder(moduleName, ix);
      ContainerService containerService = ContainerUtils.loadContainer();
      InstanceHelper instanceHelper =
          new InstanceHelper(moduleName, ix, stateHolder, containerService);
      listBuilder.add(new ManualInstanceHolder(moduleName, containerService, ix, stateHolder,
          instanceHelper));
    }
    return listBuilder.build();
  }

  @Override
  public LocalServerEnvironment doConfigure(
      ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      File externalResourceDir, String address, Map<String, Object> containerConfigProperties,
      DevAppServer devAppServer) throws Exception {
    LocalServerEnvironment result = null;
    for (ManualInstanceHolder instanceHolder : getInstanceHolders()) {
      instanceHolder.setConfiguration(moduleConfigurationHandle, serverInfo, externalResourceDir,
          address, containerConfigProperties, devAppServer);
      LocalServerEnvironment thisEnvironment = instanceHolder.doConfigure();
      if (result == null) {
        result = thisEnvironment;
      }
    }
    return result;
  }

  @Override
  public int getInstanceCount() {
    return getInstanceHolders().size() - 1;
  }

  private ManualInstanceHolder getFirstMaybeAvailableInstanceHolder() {
    int instance = instanceCounter.getAndIncrement() % getInstanceCount();
    return getInstanceHolder(instance);
  }

  @Override
  public ManualInstanceHolder getAndReserveAvailableInstanceHolder() {
    ManualInstanceHolder result = null;
    for (ManualInstanceHolder instanceHolder : new Iterable<ManualInstanceHolder>() {
          @Override
          public Iterator<ManualInstanceHolder> iterator() {
            return new FromFirstMaybeAvailableInstanceIterator();
          }
        }) {
      if (instanceHolder.acquireServingPermit()) {
        result = instanceHolder;
        break;
      }
    }
    return result;
  }

  @Override
  public void startServing() throws Exception {
    requireState("startServing", InstanceState.STOPPED);
    for (ManualInstanceHolder instanceHolder : getInstanceHolders()) {
      instanceHolder.startServing();
    }
  }

  @Override
  public void stopServing() throws Exception {
    // Causes stopServing to fail while module instances are starting up
    // but have not reached the RUNNING state. The production behavior is
    // also asynchronous and not explicitly specified in the ModulesService API.
    //
    // It is probably possible to support stopServing for module instances in SLEEPING
    // or RUNNING_START_REQUEST state. If you try please keep in mind:
    //
    // 1) Threads may be retrying _ah/start messages - see InstanceHelper.sendStartRequest()
    // 2) Threads may be blocked on ManualInstanceHolder.latch - waiting for the /_ah/start handler
    //    to succeed.
    // 3) ManualModuleInstanceHolder.stopServing depends on the port assignment
    //    having been completed.
    requireState("stopServing", InstanceState.RUNNING);
    for (ManualInstanceHolder instanceHolder : getInstanceHolders()) {
      instanceHolder.stopServing();
    }
  }

  private void requireState(String operation, InstanceState requiredState) {
    for (ManualInstanceHolder instanceHolder : getInstanceHolders()) {
      instanceHolder.requireState(operation, requiredState);
    }
  }

  /**
   * Iterator for iterating through {@link InstanceHolder} values starting with
   * {@link #getFirstMaybeAvailableInstanceHolder}.
   */
  private class FromFirstMaybeAvailableInstanceIterator
      extends AbstractIterator<ManualInstanceHolder> {
    private static final int INVALID_INSTANCE_ID = -1;
    private int startInstanceId = INVALID_INSTANCE_ID;
    private int currentInstanceId = INVALID_INSTANCE_ID;

    @Override
    protected ManualInstanceHolder computeNext() {
      if (getInstanceCount() == 0) {
        endOfData();
        return null;
      }

      if (startInstanceId == INVALID_INSTANCE_ID) {
        ManualInstanceHolder instanceHolder = getFirstMaybeAvailableInstanceHolder();
        startInstanceId = instanceHolder.getInstance();
        currentInstanceId = startInstanceId;
        return instanceHolder;
      }

      int nextInstanceId = (currentInstanceId + 1) % getInstanceCount();
      if (nextInstanceId == startInstanceId) {
        endOfData();
        return null;
      } else {
        currentInstanceId = nextInstanceId;
        return getInstanceHolder(currentInstanceId);
      }
    }
  }
}
