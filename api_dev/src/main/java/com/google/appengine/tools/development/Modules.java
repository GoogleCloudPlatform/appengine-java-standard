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

import com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApplicationException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXml.ManualScaling;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manager for {@link DevAppServer} servers.
 *
 */
public class Modules implements ModulesController, ModulesFilterHelper {

  // TODO: Explore wiring this into ApiProxy.Environment
  private static final AtomicReference<Modules> instance = new AtomicReference<Modules>();
  private static final Logger LOGGER = Logger.getLogger(Modules.class.getName());

  private final List<Module> modules;
  private final Map<String, Module> moduleNameToModuleMap;

  // This lock must be held for operations that perform dynamic configuration changes to Modules
  // such as startModule, stopModule. To assure code consistency when implementing dynamic
  // configuration changes please wrap you operation in a Runnable and invoke it with
  // using doDynamicConfiguration. See doDynamicConfiguration for more information.
  private final Lock dynamicConfigurationLock = new ReentrantLock();
  private static final int DYNAMIC_CONFIGURATION_TIMEOUT_SECONDS = 2;

  public static Modules createModules(
      ApplicationConfigurationManager applicationConfigurationManager,
      String serverInfo, File externalResourceDir, String address, DevAppServer devAppServer) {
    ImmutableList.Builder<Module> builder = ImmutableList.builder();
    for (ApplicationConfigurationManager.ModuleConfigurationHandle moduleConfigurationHandle :
        applicationConfigurationManager.getModuleConfigurationHandles()) {
      AppEngineWebXml appEngineWebXml = moduleConfigurationHandle.getModule().getAppEngineWebXml();
      Module module = null;
      if (!appEngineWebXml.getBasicScaling().isEmpty()) {
        module =
            new BasicModule(
                moduleConfigurationHandle, serverInfo, address, devAppServer, appEngineWebXml);
      } else if (!appEngineWebXml.getManualScaling().isEmpty()) {
        module =
            new ManualModule(
                moduleConfigurationHandle, serverInfo, address, devAppServer, appEngineWebXml);
      } else {
        module =
            new AutomaticModule(
                moduleConfigurationHandle, serverInfo, externalResourceDir, address, devAppServer);
      }
      builder.add(module);

      // Clear values that apply to the primary container only
      externalResourceDir = null;
    }
    try {
      ImmutableList<Module> lm = builder.build();
      instance.set(
          Class.forName(AppengineSdk.getSdk().getModulesClassName())
              .asSubclass(Modules.class)
              .getDeclaredConstructor(List.class)
              .newInstance(lm));
      return instance.get();
    } catch (ClassNotFoundException
        | IllegalAccessException
        | IllegalArgumentException
        | InstantiationException
        | NoSuchMethodException
        | SecurityException
        | InvocationTargetException ex) {
      Logger.getLogger(Modules.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  public static Modules getInstance() {
    return instance.get();
  }

  public void shutdown() throws Exception {
    for (Module module : modules) {
      module.shutdown();
    }
  }

  public void configure(Map<String, Object>containerConfigProperties) throws Exception {
    for (Module module : modules) {
      module.configure(containerConfigProperties);
    }
  }

  public void setApiProxyDelegate(ApiProxy.Delegate<?> apiProxyDelegate) {
    for (Module module : modules) {
      module.setApiProxyDelegate(apiProxyDelegate);
    }
  }

  public void createConnections() throws Exception {
    for (Module module : modules) {
      module.createConnection();
    }
  }

  public void startup() throws Exception {
    for (Module module : modules) {
      module.startup();
    }
  }

  public Module getMainModule() {
    return modules.get(0);
  }

  public Modules(List<Module> modules) {
    if (modules.size() < 1) {
      throw new IllegalArgumentException("modules must not be empty.");
    }
    this.modules = modules;

    ImmutableMap.Builder<String, Module> mapBuilder = ImmutableMap.builder();
    for (Module module : this.modules) {
      mapBuilder.put(module.getModuleName(), module);
    }
    moduleNameToModuleMap = mapBuilder.buildOrThrow();
  }

  public LocalServerEnvironment getLocalServerEnvironment() {
    return modules.get(0).getLocalServerEnvironment();
  }

  public Module getModule(String moduleName) {
    return moduleNameToModuleMap.get(moduleName);
  }

  // Modules Controller Methods.
  // TODO: Include backends in ModulesController.
  @Override
  public Iterable<String> getModuleNames() {
    return moduleNameToModuleMap.keySet();
  }

  @Override
  public Iterable<String> getVersions(String moduleName) throws ApplicationException {
    return ImmutableList.of(getDefaultVersion(moduleName));
  }

  @Override
  public String getDefaultVersion(String moduleName) throws ApplicationException {
    Module module = getRequiredModule(moduleName);
    return module.getMainContainer().getAppEngineWebXmlConfig().getMajorVersionId();
  }

  @Override
  public int getNumInstances(String moduleName, String version) throws ApplicationException {
    Module module = getRequiredModule(moduleName);
    checkVersion(version, module);
    ManualScaling manualScaling = getRequiredManualScaling(module);
    return Integer.parseInt(manualScaling.getInstances());
  }

  @Override
  public void setNumInstances(String moduleName, String version, int numInstances)
      throws ApplicationException {
    // b/8321220
    throw new UnsupportedOperationException(
        "ModulesService.setNumInstances not currently supported by java dev appserver");
  }

  @Override
  public String getHostname(String moduleName, String version, int instance)
      throws ApplicationException {
    Module module = getRequiredModule(moduleName);
    if (instance != LocalEnvironment.MAIN_INSTANCE) {
      checkVersion(version, module);
      checkNotDynamicModule(module);
    }
    String hostAndPort = module.getHostAndPort(instance);
    if (hostAndPort == null) {
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_INSTANCES_VALUE,
          "Instance " + instance + " not found");
    }
    return hostAndPort;
  }

  @Override
  public ModuleState getModuleState(String moduleName) throws ApplicationException {
    return checkModuleStopped(moduleName) ? ModuleState.STOPPED : ModuleState.RUNNING;
  }

  @Override
  public String getScalingType(final String moduleName) throws ApplicationException {
    Module module = getModule(moduleName);
    if (module == null) {
      return null;
    }
    return module.getClass().getSimpleName();
  }

  @Override
  public void startModule(final String moduleName, final String version)
      throws ApplicationException {
    doDynamicConfiguration("startServing", () -> doStartModule(moduleName, version));
  }

  private void doStartModule(String moduleName, String version) {
    Module module = getRequiredModule(moduleName);
    checkVersion(version, module);
    checkNotDynamicModule(module);
    try {
      module.startServing();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "startServing failed", e);
      throw new ApplicationException(ModulesServiceError.ErrorCode.UNEXPECTED_STATE_VALUE,
          "startServing failed with error " + e.getMessage());
    }
  }

  @Override
  public void stopModule(final String moduleName, final String version)
      throws ApplicationException {
    doDynamicConfiguration("stopServing", () -> doStopModule(moduleName, version));
  }

  /**
   * Attempts to acquire the {@link #dynamicConfigurationLock} and run the
   * requested operation.
   * <p>
   * Currently only one dynamic configuration operation is allowed at a time. This
   * reduces complexity (e.g. we don't allow the user to start a module while we are
   * stopping it). One disadvantage of the approach is that some operations that may
   * work in production will not work in the development environment. In particular an
   * attempt to perform a dynamic configuration change in another thread during
   * a dynamic configuration change will time out. For example consider
   * {@link com.google.appengine.api.LifecycleManager#beginShutdown(long)}.
   *
   * @throws ApplicationException if the operation fails, we are unable to
   * acquire the lock in {@link #DYNAMIC_CONFIGURATION_TIMEOUT_SECONDS} seconds
   * or we are interrupted before we acquire the lock.
   */
  private void doDynamicConfiguration(String operation, Runnable runnable) {
    try {
      if (dynamicConfigurationLock.tryLock(DYNAMIC_CONFIGURATION_TIMEOUT_SECONDS,
          TimeUnit.SECONDS)) {
        try {
          runnable.run();
        } finally {
          dynamicConfigurationLock.unlock();
        }
      } else {
        LOGGER.log(Level.SEVERE, "stopServing timed out");
        throw new ApplicationException(ModulesServiceError.ErrorCode.UNEXPECTED_STATE_VALUE,
            operation + " timed out");
      }
    } catch (InterruptedException ie) {
      LOGGER.log(Level.SEVERE, "stopServing interrupted", ie);
      throw new ApplicationException(ModulesServiceError.ErrorCode.UNEXPECTED_STATE_VALUE,
          operation + " interrupted " + ie.getMessage());
    }
  }

  private void doStopModule(String moduleName, String version) {
    Module module = getRequiredModule(moduleName);
    checkVersion(version, module);
    checkNotDynamicModule(module);
    try {
      module.stopServing();
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "stopServing failed", e);
      throw new ApplicationException(ModulesServiceError.ErrorCode.UNEXPECTED_STATE_VALUE,
          "stopServing failed with error " + e.getMessage());
    }
  }

  private Module getRequiredModule(String moduleName) {
    Module module = moduleNameToModuleMap.get(moduleName);
    if (module == null) {
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_MODULE_VALUE,
          "Module not found");
    }
    return module;
  }

  private void checkNotDynamicModule(Module module) {
    if (module.getMainContainer().getAppEngineWebXmlConfig().getManualScaling().isEmpty() &&
        module.getMainContainer().getAppEngineWebXmlConfig().getBasicScaling().isEmpty()) {
      // Logged because this exception redacted by the ModulesService.
      LOGGER.warning("Module " + module.getModuleName() + " cannot be a dynamic module");
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "This operation is not supported on Dynamic modules.");
    }
  }

  private ManualScaling getRequiredManualScaling(Module module) {
    ManualScaling manualScaling =
        module.getMainContainer().getAppEngineWebXmlConfig().getManualScaling();
    if (manualScaling.isEmpty()) {
      // Logged because this exception redacted by the ModulesService.
      LOGGER.warning("Module " + module.getModuleName() + " must be a manual scaling module");
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "Manual scaling is required.");
    }
    return manualScaling;
  }

  private void checkVersion(String version, Module module) {
    String moduleVersion =
        module.getMainContainer().getAppEngineWebXmlConfig().getMajorVersionId();
    if (version == null || !version.equals(moduleVersion)) {
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "Version not found");
    }
  }

  // ModulesFilterHelper methods.
  @Override
  public boolean acquireServingPermit(
    String moduleName, int instanceNumber, boolean allowQueueOnBackends) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getInstanceHolder(instanceNumber);
    return instanceHolder.acquireServingPermit();
  }

  @Override
  public int getAndReserveFreeInstance(String moduleName) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getAndReserveAvailableInstanceHolder();
    return instanceHolder == null ? -1 : instanceHolder.getInstance();
  }

  @Override
  public void returnServingPermit(String moduleName, int instance) {
    // Currently a no-op for modules.
  }

  @Override
  public boolean checkInstanceExists(String moduleName, int instance) {
    Module module = getModule(moduleName);
    return module != null && module.getInstanceHolder(instance) != null;
  }

  @Override
  public boolean checkModuleExists(String moduleName) {
     return getModule(moduleName) != null;
  }

  @Override
  public boolean checkModuleStopped(String serverName) {
    return checkInstanceStopped(serverName, LocalEnvironment.MAIN_INSTANCE);
  }

  @Override
  public boolean checkInstanceStopped(String moduleName, int instance) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    return instanceHolder.isStopped();
  }

  @Override
  public boolean isLoadBalancingInstance(String moduleName, int instance) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    return instanceHolder.isLoadBalancingInstance();
  }

  @Override
  public boolean expectsGeneratedStartRequests(String moduleName,
      int instance) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    return instanceHolder.expectsGeneratedStartRequest();
  }

  @Override
  public int getPort(String moduleName, int instance) {
    Module module = getModule(moduleName);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    return instanceHolder.getContainerService().getPort();
  }
}
