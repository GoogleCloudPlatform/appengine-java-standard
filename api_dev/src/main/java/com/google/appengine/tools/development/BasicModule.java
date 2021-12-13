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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Basic {@link Module} implementation.  Very similar to ManualModule, but will create 1 or 2
 * instances based on maxInstances.  Does not attempt to implement autoscaling since we do not
 * do that in dynamic modules either.  Will create 2 instances and balance load between the two
 * if maxInstances is >= 2, this should help with testing shared state between instances.
 *
 */
public class BasicModule extends AbstractModule<ManualInstanceHolder> {

  private static final int MAX_INSTANCES_CAP = 2;

  BasicModule(ModuleConfigurationHandle moduleConfigurationHandle, String serverInfo,
      String address, DevAppServer devAppServer, AppEngineWebXml appEngineWebXml) {
    super(moduleConfigurationHandle, serverInfo, null, address, devAppServer,
        toInstanceHolders(appEngineWebXml, moduleConfigurationHandle));
  }

  private static List<ManualInstanceHolder> toInstanceHolders(
      AppEngineWebXml appEngineWebXml, ModuleConfigurationHandle moduleConfigurationHandle) {
    String instancesString = appEngineWebXml.getBasicScaling().getMaxInstances();
    int instances = instancesString == null ? 0 : Integer.parseInt(instancesString);
    if (instances <= 0) {
      throw new AppEngineConfigException("Invalid instances " + instances + " in file "
        + moduleConfigurationHandle.getModule().getAppEngineWebXmlFile());
    }
    ImmutableList.Builder<ManualInstanceHolder> listBuilder = ImmutableList.builder();
    for (
        int index = LocalEnvironment.MAIN_INSTANCE;
        index < MAX_INSTANCES_CAP && index < instances;
        index++) {
      listBuilder.add(toInstanceHolder(moduleConfigurationHandle, index));
    }
    return listBuilder.build();
  }

  private static ManualInstanceHolder toInstanceHolder(
      ModuleConfigurationHandle moduleConfigurationHandle, int index) {
    String moduleName = moduleConfigurationHandle.getModule().getModuleName();
    InstanceStateHolder stateHolder = new InstanceStateHolder(moduleName, index);
    ContainerService containerService = ContainerUtils.loadContainer();
    InstanceHelper instanceHelper =
        new InstanceHelper(moduleName, index, stateHolder, containerService);
    return new ManualInstanceHolder(moduleName, containerService, index, stateHolder,
          instanceHelper);
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

  private AtomicReference<ManualInstanceHolder> lastUsedInstance =
      new AtomicReference<ManualInstanceHolder>();

  @Override
  public ManualInstanceHolder getAndReserveAvailableInstanceHolder() {
    if (getInstanceCount() == 1) {
      return Iterables.getLast(getInstanceHolders(), null);
    }
    for (ManualInstanceHolder instance : getInstanceHolders()) {
      if (instance != lastUsedInstance.get() && instance.acquireServingPermit()) {
        lastUsedInstance.set(instance);
        return instance;
      }
    }
    return null;
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
}
